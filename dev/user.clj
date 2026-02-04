(ns user
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [com.stuartsierra.component :as component]
            [figwheel-sidecar.repl-api :as f]
            [datomic.api :as datomic]
            [buddy.hashers :as hashers]
            [orcpub.routes :as r]
            [orcpub.system :as s]
            [orcpub.db.schema :as schema]))

(alter-var-root #'*print-length* (constantly 50))

;; user is a namespace that the Clojure runtime looks for and
;; loads if its available

;; You can place helper functions in here. This is great for starting
;; and stopping your webserver and other development services

;; The definitions in here will be available if you run "lein repl" or launch a
;; Clojure repl some other way

;; You have to ensure that the libraries you :require are listed in your dependencies

;; Once you start down this path
;; you will probably want to look at
;; tools.namespace https://github.com/clojure/tools.namespace
;; and Component https://github.com/stuartsierra/component

(defonce -server (atom nil))

(def ^:private default-db-uri "datomic:free://localhost:4334/orcpub")

(defmacro with-db
  "Convenience util to get access to the datomic conn and/or db
   objects. Call as:
   (with-db [conn db]
     (do-stuff-to db)
   You can also just do (with-db [db]) or (with-db [conn])

   Works with or without the server running - connects directly to
   Datomic if the server isn't started."
  [init-vector & body]
  `(let [conn# (if-let [system-map# @-server]
                 ;; Server running: get conn from component system
                 (->> system-map# :conn :conn)
                 ;; No server: connect directly to Datomic
                 (datomic/connect default-db-uri))
         db# (datomic/db conn#)
         {:keys ~init-vector} {:conn conn# :db db#}]
     ~@body))

(defn- project-form
  []
  (with-open [r (java.io.PushbackReader. (io/reader "project.clj"))]
    (binding [*read-eval* false]
      (loop [form (read r)]
        (if (= (first form) 'defproject)
          form
          (recur (read r)))))))

(defn get-cljs-build
  [id]
  (let [project-config (->> (project-form)
                            (drop 1)
                            (apply hash-map))
        build (->> project-config
                   :cljsbuild
                   :builds
                   (filter #(= id (:id %)))
                   first)]
    (prn "BUILD" build)
    [build]))

(defn init-database
  ([]
   (init-database :free))
  ([mode]
   (when-not (contains? #{:free :dev :mem} mode)
     (throw (IllegalArgumentException. (str "Unknown db type " mode))))
   (let [db-uri (str "datomic" mode "://localhost:4334/orcpub")]
     (datomic/create-database db-uri)
     (let [conn (datomic/connect db-uri)]
       (datomic/transact conn schema/all-schemas)))))

(defn stop-server
  []
  (when-let [s @-server]
    (component/stop s)
    (reset! -server nil)))

(defn start-server
  []
  ; restart
  (stop-server)
  (reset! -server (component/start (s/system :dev))))

(defn verify-new-user
  "Automatically mark a user as `verified`. Useful for local testing
   since the email never gets sent."
  [username-or-email]
  (with-db [conn db]
    (let [user (r/find-user-by-username-or-email db username-or-email)
          verification-key (:orcpub.user/verification-key user)]
      (r/verify {:query-params {:key verification-key}
                 :conn conn
                 :db db}))))

;; -----------------------------------------------------------------------------
;; Test account helpers
;; -----------------------------------------------------------------------------

(def default-test-accounts-file "dev/test-accounts.edn")

(defn- load-test-accounts
  "Load test accounts from an EDN file. Defaults to dev/test-accounts.edn"
  ([] (load-test-accounts default-test-accounts-file))
  ([path]
   (let [f (clojure.java.io/file path)]
     (if (.exists f)
       (edn/read-string (slurp f))
       (throw (ex-info (str "Test accounts file not found: " path) {:path path}))))))

(defn- normalize-email [email]
  (some-> email str/trim str/lower-case))

(defn- test-account-exists?
  [db {:keys [username email]}]
  (or (r/find-user-by-username db username)
      (r/find-user-by-username-or-email db (normalize-email email))))

(defn ensure-test-accounts!
  "Idempotently ensure a set of test accounts exist and are verified.

  Call with no args to load from dev/test-accounts.edn, or pass a file path,
  or pass a collection of account maps directly. Maps should have :username
  :email :password and optional :first-and-last-name / :send-updates?.
  Passwords are hashed; users are created verified to skip email flow."
  ([] (ensure-test-accounts! (load-test-accounts)))
  ([path-or-accounts]
   (if (string? path-or-accounts)
     (ensure-test-accounts! (load-test-accounts path-or-accounts))
     (let [accounts path-or-accounts]
       (with-db [conn db]
         (doseq [{:keys [username email password first-and-last-name send-updates?] :as acct} accounts]
           (if (test-account-exists? db acct)
             (println "Already exists:" username "(" email ")")
             (let [now (java.util.Date.)
                   tx [{:db/id "tempid"
                        :orcpub.user/username username
                        :orcpub.user/email (normalize-email email)
                        :orcpub.user/first-and-last-name (or first-and-last-name username)
                        :orcpub.user/password (hashers/encrypt (str/trim password))
                        :orcpub.user/send-updates? (boolean send-updates?)
                        :orcpub.user/created now
                        :orcpub.user/verified? true
                        :orcpub.user/verification-sent now
                        :orcpub.user/verification-key (str (java.util.UUID/randomUUID))}]
                   result @(datomic/transact conn tx)
                   new-id (-> result :tempids (get "tempid"))]
               (println "Created test account" username "id" new-id)))))))))

(defn list-test-accounts
  "Return a summary of whether the provided (or default) test accounts exist."
  ([] (list-test-accounts (load-test-accounts)))
  ([path-or-accounts]
   (if (string? path-or-accounts)
     (list-test-accounts (load-test-accounts path-or-accounts))
     (let [accounts path-or-accounts]
       (with-db [db]
         (map (fn [{:keys [username email] :as acct}]
                {:username username
                 :email email
                 :exists? (boolean (test-account-exists? db acct))})
              accounts))))))

(defn fig-start
  "This starts the figwheel server and watch based auto-compiler.

  Afterwards, call (cljs-repl) to connect."
  ([]
   (fig-start "dev"))
  ([build-id]
   ;; this call will only work as long as your :cljsbuild and
   ;; :figwheel configurations are at the top level of your project.clj
   ;; and are not spread across different lein profiles

   ;; otherwise you can pass a configuration into start-figwheel! manually
   (f/start-figwheel!
     {:figwheel-options {}
      :build-ids [build-id]
      :all-builds (get-cljs-build build-id)})))

(defn fig-stop
  "Stop the figwheel server and watch based auto-compiler."
  []
  (f/stop-figwheel!))

;; if you are in an nREPL environment you will need to make sure you
;; have setup piggieback for this to work
(defn cljs-repl
  "Launch a ClojureScript REPL that is connected to your build and host environment.

  (NB: Call fig-start first.)"
  []
  (f/cljs-repl))
