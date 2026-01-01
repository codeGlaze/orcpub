(ns orcpub.dev-tools
  "Small dev utilities for seeding test users and verifying them." 
  (:require [datomic.api :as d]
            [buddy.hashers :as hashers]
            [clojure.string :as s]
            [orcpub.routes :as r])
  (:gen-class))

(def default-uri "datomic:free://localhost:4334/orcpub")

(defn conn
  ([] (conn default-uri))
  ([uri]
   (d/connect uri)))

(defn email-exists? [db email]
  (boolean (d/q '[:find ?e . :in $ ?email :where [?e :orcpub.user/email ?email]] db (s/lower-case email))))

(defn username-exists? [db username]
  (boolean (d/q '[:find ?e . :in $ ?username :where [?e :orcpub.user/username ?username]] db username)))

(defn create-user!
  "Create a user. Returns the result of the transact.

  Usage:
    (create-user! conn {:username "bob" :email "bob@example.com" :password "pass" :verify? true})"
  [conn {:keys [username email password verify? send-updates?] :or {verify? false send-updates? false}}]
  (let [db (d/db conn)
        email (when email (s/lower-case (s/trim email)))
        username (when username (s/trim username))]
    (cond
      (and email (email-exists? db email)) (throw (ex-info "Email already exists" {:email email}))
      (and username (username-exists? db username)) (throw (ex-info "Username already exists" {:username username}))
      :else
      (let [now (java.util.Date.)
            pw (hashers/encrypt (or password "password"))
            tx {:orcpub.user/email email
                :orcpub.user/username username
                :orcpub.user/password pw
                :orcpub.user/created now
                :orcpub.user/send-updates? (boolean send-updates?)
                :orcpub.user/verified? (boolean verify?)}]
        @(d/transact conn [tx])))))

(defn verify-user!
  "Mark an existing user as verified (useful when emails are not sent in dev)." [conn username-or-email]
  (let [db (d/db conn)
        user (r/find-user-by-username-or-email db username-or-email)]
    (if-not user
      (throw (ex-info "User not found" {:username-or-email username-or-email}))
      (let [id (:db/id user)]
        @(d/transact conn [[:db/add id :orcpub.user/verified? true]])))))

(defn delete-user!
  "Remove a user entity by username or email (dev only)." [conn username-or-email]
  (let [db (d/db conn)
        user (r/find-user-by-username-or-email db username-or-email)]
    (if-not user
      (throw (ex-info "User not found" {:username-or-email username-or-email}))
      @(d/transact conn [[:db/retractEntity (:db/id user)]]))))

(defn -main
  "CLI: create a user.

  Usage: lein run -m orcpub.dev-tools <username> <email> <password> [verify]
  Example: lein run -m orcpub.dev-tools testuser test@example.com s3cret verify
" [& args]
  (let [[username email password & rest] args
        verify? (some #(= % "verify") rest)
        c (conn)]
    (println "Creating user:" username email "verified?" verify?)
    (try
      (create-user! c {:username username :email email :password password :verify? verify?})
      (println "User created.")
      (when verify?
        (println "User verified."))
      (catch Exception e
        (println "Error creating user:" (.getMessage e))
        (System/exit 1)))))