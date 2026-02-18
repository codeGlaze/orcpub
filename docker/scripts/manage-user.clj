;; OrcPub Docker User Management Script
;;
;; Runs inside the orcpub container using the uberjar classpath:
;;   java -cp /orcpub.jar clojure.main /scripts/manage-user.clj <command> [args...]
;;
;; Commands:
;;   create <username> <email> <password>  — Create and auto-verify a user
;;   verify <username-or-email>            — Verify an existing unverified user
;;   check  <username-or-email>            — Check if a user exists and their status
;;   list                                  — List all users (username + email + verified)

(ns manage-user
  (:require [datomic.api :as d]
            [buddy.hashers :as hashers]
            [clojure.string :as s]))

(def datomic-url
  (or (System/getenv "DATOMIC_URL")
      "datomic:free://datomic:4334/orcpub?password=datomic"))

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

(defn create-user! [conn username email password]
  (let [db    (d/db conn)
        email (s/lower-case (s/trim email))
        username (s/trim username)]
    ;; Check for duplicates
    (when (d/q '[:find ?e . :in $ ?email
                 :where [?e :orcpub.user/email ?email]] db email)
      (binding [*out* *err*]
        (println "ERROR: Email already registered:" email))
      (System/exit 1))
    (when (d/q '[:find ?e . :in $ ?username
                 :where [?e :orcpub.user/username ?username]] db username)
      (binding [*out* *err*]
        (println "ERROR: Username already taken:" username))
      (System/exit 1))
    ;; Create user — already verified, no email step needed
    @(d/transact conn
       [{:orcpub.user/email      email
         :orcpub.user/username   username
         :orcpub.user/password   (hashers/encrypt password)
         :orcpub.user/verified?  true
         :orcpub.user/send-updates? false
         :orcpub.user/created    (java.util.Date.)}])
    (println "OK: User created and verified —" username "<" email ">")))

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
               (let [conn (get-conn)]
                 (create-user! conn username email password)))

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
      (println "  verify <username-or-email>            Verify an existing user")
      (println "  check  <username-or-email>            Check if a user exists")
      (println "  list                                  List all users")
      (when-not cmd
        (System/exit 1)))))
