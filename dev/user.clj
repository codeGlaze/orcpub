(ns user
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [com.stuartsierra.component :as component]
            [datomic.api :as datomic]
            [buddy.hashers :as hashers]
            [orcpub.routes :as r]
            [orcpub.system :as s]
            [orcpub.db.schema :as schema]
            [orcpub.config :as config])
  (:gen-class))

;; ---------------------------------------------------------------------------
;; Dev tooling hub — REPL helpers and CLI entrypoint.
;;
;; The `user` namespace is special in Clojure: the runtime automatically looks
;; for and loads it when starting a REPL. Any functions defined here are
;; immediately available when you run `lein repl`.
;;
;; This file lives in dev/ and is only on the classpath in :dev and :init-db
;; profiles. It is NOT included in the production uberjar — keeping dev
;; tooling (user creation, DB init, Figwheel) out of production builds.
;;
;; Two ways to use it:
;;   1. REPL: (start-server), (init-database), (create-user! (conn) {...}), etc.
;;   2. CLI:  lein with-profile init-db run -m user <command> [args]
;;            (see -main at bottom for available commands)
;;
;; The :init-db profile skips ClojureScript/Garden compilation for fast CLI
;; startup. It includes dev/ in source-paths so this file is loadable.
;; ---------------------------------------------------------------------------

;; Lazy-load figwheel-main only when needed (avoids loading it for server-only REPL)
(def ^:private fig-api
  (delay
    (try
      (require 'figwheel.main.api)
      (find-ns 'figwheel.main.api)
      (catch Exception e
        (println "figwheel.main.api not available:" (.getMessage e))
        nil))))

(alter-var-root #'*print-length* (constantly 50))

(defonce -server (atom nil))

(defmacro with-db
  "Convenience util to get access to the datomic conn and/or db
   objects. Call as:
   (with-db [conn db]
     (do-stuff-to db)
   You can also just do (with-db [db]) or (with-db [conn])"
  [init-vector & body]
  `(if-let [system-map# @-server]
     ; first :conn here is a DatomicComponent;
     ; the second is the actual connection object
     (let [conn# (->> system-map# :conn :conn)
           db# (datomic/db conn#)

           ; unpack the requested values:
           {:keys ~init-vector} {:conn conn#
                                 :db db#}]
       ~@body)

     ;; nothing in -server:
     (throw (IllegalStateException. "Call (start-server) first"))))

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
   (init-database nil))
  ([mode]
   (let [env-uri (config/datomic-env)
         db-uri (if (some-> env-uri not-empty)
                  env-uri
                  (let [m (or mode :dev)]
                    (when-not (contains? #{:free :dev :mem} m)
                      (throw (IllegalArgumentException. (str "Unknown db type " m))))
                    (str "datomic" m "://localhost:4334/orcpub")))]
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

(defn fig-start
  "This starts the figwheel server and watch based auto-compiler.
  Uses figwheel-main 0.2.20 with dev.cljs.edn build config.

  Afterwards, call (cljs-repl) to connect."
  ([]
   (fig-start "dev"))
  ([build-id]
   (if-let [api @fig-api]
     ((ns-resolve api 'start) build-id)
     (println "figwheel-main not available. Run 'lein fig:dev' instead."))))

(defn fig-stop
  "Stop the figwheel server and watch based auto-compiler."
  []
  (if-let [api @fig-api]
    ((ns-resolve api 'stop-all))
    (println "figwheel-main not available.")))

;; if you are in an nREPL environment you will need to make sure you
;; have setup piggieback for this to work
(defn cljs-repl
  "Launch a ClojureScript REPL that is connected to your build and host environment.

  (NB: Call fig-start first.)"
  ([]
   (cljs-repl "dev"))
  ([build-id]
   (if-let [api @fig-api]
     ((ns-resolve api 'cljs-repl) build-id)
     (println "figwheel-main not available."))))

(defn add-test-user
  "Creates a test user for development, already marked as verified. Only runs if ORCPUB_ENV=dev."
  []
  (if (= (System/getenv "ORCPUB_ENV") "dev")
    (let [username "test"
          email "test@example.com"
          password "testpass"]
      (r/register {:username username :email email :password password :verified true}))
    (println "add-test-user is disabled outside dev environment.")))

;; ---------------------------------------------------------------------------
;; Standalone DB connection (no running server required)
;; ---------------------------------------------------------------------------

(defn conn
  "Connect to Datomic without starting the full server.
   Uses DATOMIC_URL env or default."
  ([] (conn (config/get-datomic-uri)))
  ([uri] (datomic/connect uri)))

;; ---------------------------------------------------------------------------
;; User CRUD (for CLI and REPL use — does not require a running server)
;; ---------------------------------------------------------------------------

(defn email-exists? [db email]
  (boolean (datomic/q '[:find ?e . :in $ ?email :where [?e :orcpub.user/email ?email]]
                      db (str/lower-case email))))

(defn username-exists? [db username]
  (boolean (datomic/q '[:find ?e . :in $ ?username :where [?e :orcpub.user/username ?username]]
                      db username)))

(defn create-user!
  "Create a user directly in the database. Does not require a running server.

  Usage from REPL:
    (create-user! (conn) {:username \"bob\" :email \"bob@example.com\" :password \"pass\" :verify? true})

  Usage from CLI:
    lein with-profile init-db run -m user create-user bob bob@example.com pass verify"
  [conn {:keys [username email password verify? send-updates?] :or {verify? false send-updates? false}}]
  (let [db (datomic/db conn)
        email (when email (str/lower-case (str/trim email)))
        username (when username (str/trim username))]
    (cond
      (and email (email-exists? db email))
      (throw (ex-info "Email already exists" {:email email}))

      (and username (username-exists? db username))
      (throw (ex-info "Username already exists" {:username username}))

      :else
      (let [now (java.util.Date.)
            pw (hashers/encrypt (or password "password"))
            tx {:orcpub.user/email email
                :orcpub.user/username username
                :orcpub.user/password pw
                :orcpub.user/created now
                :orcpub.user/send-updates? (boolean send-updates?)
                :orcpub.user/verified? (boolean verify?)}]
        @(datomic/transact conn [tx])))))

(defn verify-user!
  "Mark an existing user as verified by username or email.
   Does not require a running server."
  [conn username-or-email]
  (let [db (datomic/db conn)
        user (r/find-user-by-username-or-email db username-or-email)]
    (if-not user
      (throw (ex-info "User not found" {:username-or-email username-or-email}))
      @(datomic/transact conn [[:db/add (:db/id user) :orcpub.user/verified? true]]))))

(defn delete-user!
  "Remove a user entity by username or email. Dev only."
  [conn username-or-email]
  (let [db (datomic/db conn)
        user (r/find-user-by-username-or-email db username-or-email)]
    (if-not user
      (throw (ex-info "User not found" {:username-or-email username-or-email}))
      @(datomic/transact conn [[:db/retractEntity (:db/id user)]]))))

;; ---------------------------------------------------------------------------
;; CLI entrypoint — used by scripts/start.sh, scripts/create_dummy_user.sh
;;
;; Usage:
;;   lein with-profile init-db run -m user init-db
;;   lein with-profile init-db run -m user init-db --add-test-user
;;   lein with-profile init-db run -m user create-user <name> <email> <pass> [verify]
;;   lein with-profile init-db run -m user verify-user <name-or-email>
;;   lein with-profile init-db run -m user delete-user <name-or-email>
;; ---------------------------------------------------------------------------

(defn -main [& [cmd & args]]
  (try
    (case cmd
      "init-db"
      (let [uri (config/get-datomic-uri)]
        (println "Ensuring database exists at" uri)
        (datomic/create-database uri)
        (let [c (datomic/connect uri)]
          (println "Applying schema...")
          (datomic/transact c schema/all-schemas)
          (println "DB init done.")
          (when (some #{"--add-test-user"} args)
            (println "Creating test user...")
            (add-test-user))))

      "create-user"
      (let [[username email password & flags] args
            verify? (some #{"verify"} flags)
            c (conn)]
        (println "Creating user:" username email "verified?" (boolean verify?))
        (create-user! c {:username username :email email :password password :verify? verify?})
        (println "User created."))

      "verify-user"
      (do (verify-user! (conn) (first args))
          (println "User verified:" (first args)))

      "delete-user"
      (do (delete-user! (conn) (first args))
          (println "User deleted:" (first args)))

      ;; default
      (do (println "Usage: lein with-profile init-db run -m user <command> [args]")
          (println "")
          (println "Commands:")
          (println "  init-db [--add-test-user]   Create database and apply schema")
          (println "  create-user <u> <e> <p> [verify]   Create a user")
          (println "  verify-user <name-or-email>        Mark user as verified")
          (println "  delete-user <name-or-email>        Delete a user")
          (System/exit 1)))
    (catch Exception e
      (binding [*out* *err*]
        (println "Error:" (.getMessage e)))
      (System/exit 1)))
  ;; Datomic peer metrics thread is non-daemon and prevents clean JVM exit.
  ;; Force exit after successful command completion.
  (System/exit 0))
