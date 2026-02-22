;; OrcPub Docker User Management Script
;;
;; Runs inside the orcpub container using the uberjar classpath:
;;   java -cp /orcpub.jar clojure.main /scripts/manage-user.clj <command> [args...]
;;
;; Commands:
;;   create <username> <email> <password>  — Create and auto-verify a user
;;   batch  <file>                         — Create users from a file (one per line)
;;   verify <username-or-email>            — Verify an existing unverified user
;;   check  <username-or-email>            — Check if a user exists and their status
;;   list                                  — List all users (username + email + verified)

(ns manage-user
  (:require [datomic.api :as d]
            [buddy.hashers :as hashers]
            [clojure.string :as s]))

(def datomic-url
  (or (System/getenv "DATOMIC_URL")
      "datomic:dev://datomic:4334/orcpub?password=datomic"))

(defn get-conn []
  (try
    (d/connect datomic-url)
    (catch Exception e
      (binding [*out* *err*]
        (println "ERROR: Cannot connect to Datomic at" datomic-url)
        (println "  Is the transactor running? Cause:" (.getMessage e)))
      (System/exit 1))))

(defn find-user [db username-or-email]
  (d/q '[:find (pull ?e [:orcpub.user/username
                         :orcpub.user/email
                         :orcpub.user/verified?
                         :orcpub.user/created
                         :db/id]) .
         :in $ ?needle
         :where
         (or [?e :orcpub.user/username ?needle]
             [?e :orcpub.user/email ?needle])]
       db
       username-or-email))

(defn try-create-user!
  "Creates a user. Returns {:ok true} on success, {:duplicate \"reason\"} if the
   user/email already exists, or {:error \"message\"} on unexpected failure."
  [conn username email password]
  (let [db       (d/db conn)
        email    (s/lower-case (s/trim email))
        username (s/trim username)]
    (cond
      (d/q '[:find ?e . :in $ ?email
             :where [?e :orcpub.user/email ?email]] db email)
      {:duplicate (str "Email already registered: " email)}

      (d/q '[:find ?e . :in $ ?username
             :where [?e :orcpub.user/username ?username]] db username)
      {:duplicate (str "Username already taken: " username)}

      :else
      (do
        @(d/transact conn
           [{:orcpub.user/email      email
             :orcpub.user/username   username
             :orcpub.user/password   (hashers/encrypt password)
             :orcpub.user/verified?  true
             :orcpub.user/send-updates? false
             :orcpub.user/created    (java.util.Date.)}])
        (println "OK: User created and verified —" username "<" email ">")
        {:ok true}))))

(defn batch-create-users!
  "Reads a user file (one user per line: username email password) and creates
   all users in a single JVM session. Blank lines and #-comments are skipped.
   Duplicates are logged and skipped (not counted as failures).
   Returns exit code 0 if no hard failures, 1 otherwise."
  [conn path]
  (let [lines   (->> (s/split-lines (slurp path))
                     (map s/trim)
                     (remove #(or (s/blank? %) (s/starts-with? % "#"))))
        results (doall
                  (for [line lines]
                    (let [parts (s/split line #"\s+")]
                      (if (< (count parts) 3)
                        (do (binding [*out* *err*]
                              (println "SKIP: bad line (need: username email password):" line))
                            {:error "bad line"})
                        (let [[username email password] parts
                              result (try
                                       (try-create-user! conn username email password)
                                       (catch Exception e
                                         {:error (.getMessage e)}))]
                          (when (:duplicate result)
                            (println "SKIP:" username "—" (:duplicate result)))
                          (when (:error result)
                            (binding [*out* *err*]
                              (println "FAIL:" username "—" (:error result))))
                          result)))))
        total   (count results)
        created (count (filter :ok results))
        dupes   (count (filter :duplicate results))
        failed  (count (filter :error results))]
    (println)
    (println (format "Batch complete: %d created, %d skipped (duplicate), %d failed, %d total"
                     created dupes failed total))
    (if (pos? failed) 1 0)))

(defn verify-user! [conn username-or-email]
  (let [db   (d/db conn)
        user (find-user db username-or-email)]
    (if-not user
      (do (binding [*out* *err*]
            (println "ERROR: User not found:" username-or-email))
          (System/exit 1))
      (if (:orcpub.user/verified? user)
        (println "OK: User already verified —" (:orcpub.user/username user))
        (do
          @(d/transact conn
             [[:db/add (:db/id user) :orcpub.user/verified? true]])
          (println "OK: User verified —" (:orcpub.user/username user)))))))

(defn check-user [db username-or-email]
  (if-let [user (find-user db username-or-email)]
    (do
      (println "Found user:")
      (println "  Username:" (:orcpub.user/username user))
      (println "  Email:   " (:orcpub.user/email user))
      (println "  Verified:" (:orcpub.user/verified? user))
      (println "  Created: " (:orcpub.user/created user)))
    (do
      (println "User not found:" username-or-email)
      (System/exit 1))))

(defn list-users [db]
  (let [users (d/q '[:find [(pull ?e [:orcpub.user/username
                                      :orcpub.user/email
                                      :orcpub.user/verified?]) ...]
                     :where [?e :orcpub.user/username]]
                   db)]
    (if (empty? users)
      (println "No users found.")
      (do
        (println (format "%-20s %-30s %s" "USERNAME" "EMAIL" "VERIFIED"))
        (println (apply str (repeat 65 "-")))
        (doseq [u (sort-by :orcpub.user/username users)]
          (println (format "%-20s %-30s %s"
                           (:orcpub.user/username u)
                           (:orcpub.user/email u)
                           (:orcpub.user/verified? u))))))))

;; --- CLI dispatch ---

(let [args *command-line-args*
      cmd  (first args)]
  (case cmd
    "create" (let [[_ username email password] args]
               (when-not (and username email password)
                 (binding [*out* *err*]
                   (println "Usage: manage-user.clj create <username> <email> <password>"))
                 (System/exit 1))
               (let [conn   (get-conn)
                     result (try-create-user! conn username email password)]
                 (when-let [msg (or (:duplicate result) (:error result))]
                   (binding [*out* *err*]
                     (println "ERROR:" msg))
                   (System/exit 1))))

    "batch"  (let [[_ path] args]
               (when-not path
                 (binding [*out* *err*]
                   (println "Usage: manage-user.clj batch <file>")
                   (println "  File format: one user per line — username email password")
                   (println "  Lines starting with # and blank lines are skipped"))
                 (System/exit 1))
               (let [conn (get-conn)
                     exit (batch-create-users! conn path)]
                 (System/exit exit)))

    "verify" (let [[_ username-or-email] args]
               (when-not username-or-email
                 (binding [*out* *err*]
                   (println "Usage: manage-user.clj verify <username-or-email>"))
                 (System/exit 1))
               (let [conn (get-conn)]
                 (verify-user! conn username-or-email)))

    "check"  (let [[_ username-or-email] args]
               (when-not username-or-email
                 (binding [*out* *err*]
                   (println "Usage: manage-user.clj check <username-or-email>"))
                 (System/exit 1))
               (let [conn (get-conn)
                     db   (d/db conn)]
                 (check-user db username-or-email)))

    "list"   (let [conn (get-conn)
                   db   (d/db conn)]
               (list-users db))

    (do
      (println "OrcPub User Management")
      (println "")
      (println "Commands:")
      (println "  create <username> <email> <password>  Create and auto-verify a user")
      (println "  batch  <file>                         Create users from a file (one per line)")
      (println "  verify <username-or-email>            Verify an existing user")
      (println "  check  <username-or-email>            Check if a user exists")
      (println "  list                                  List all users")
      (when-not cmd
        (System/exit 1))))
  ;; Datomic peer threads are non-daemon and keep the JVM alive; force exit.
  (System/exit 0))
