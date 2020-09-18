(ns user
  (:require [clojure.java.io :as io]
            [com.stuartsierra.component :as component]
            [figwheel-sidecar.repl-api :as f]
            [datomic.api :as d]
            [orcpub.routes :as r]
            [orcpub.system :as s]
            [orcpub.db.schema :as schema]
            [clojure.data.csv :as csv]
            ))

(alter-var-root #'*print-length* (constantly 100))

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
           db# (d/db conn#)

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
   (init-database :free))
  ([mode]
   (when-not (contains? #{:free :dev :mem} mode)
     (throw (IllegalArgumentException. (str "Unknown db type " mode))))
   (let [db-uri (str "datomic" mode "://localhost:4334/orcpub")]
     (d/create-database db-uri)
     (let [conn (d/connect db-uri)]
       (d/transact conn schema/all-schemas)))))

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

(defn verify-new-user [username-or-email]
  (with-db [conn db]
    (let [user (r/find-user-by-username-or-email db username-or-email)
          verification-key (:orcpub.user/verification-key user)]
      (r/verify {:query-params {:key verification-key}
                 :conn conn
                 :db db}))))

(defn update-patron-status [username b]
  (with-db [conn db]
    (d/transact conn [{:db/id 17592186045418
                              :orcpub.user/patron true}])))

(defn cleanup-images []
  (let [image-url (with-db [db] (d/q '[:find ?e ?doc
                                       :where
                                       [?e :orcpub.dnd.e5.character/image-url ?doc]] db))]
    (with-db [conn]
      (doseq [[k u] image-url]
        (if (re-matches #"^(https?|ftp)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]" u)
          (println k u)
          ((println k)
           (d/transact conn [[:db/retract k
                              :orcpub.dnd.e5.character/image-url u]]))))))
  (let [faction-image-url
        (with-db [db] (d/q '[:find ?e ?doc
                             :where
                             [?e :orcpub.dnd.e5.character/faction-image-url ?doc]] db))]
    (with-db [conn]
      (doseq [[k u] faction-image-url]
        (if (re-matches #"^(https?|ftp)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]" u)
          (println k u)
          ((println k u)
            (d/transact conn [[:db/retract k
                               :orcpub.dnd.e5.character/faction-image-url u]])))))))

(defn cleanup-age []
  (let [age
        (with-db [db] (d/q '[:find ?e ?doc
                             :where
                             [?e :orcpub.dnd.e5.character/age ?doc]] db))]
    (with-db [conn]
      (doseq [[k n] age]
        (if (> (count n) 1000)
          ((println k (count n))
           (d/transact conn [[:db/retract k
                              :orcpub.dnd.e5.character/age n]])))))))

(defn cleanup-bonds []
  (let [bonds
        (with-db [db] (d/q '[:find ?e ?doc
                             :where
                             [?e :orcpub.dnd.e5.character/bonds ?doc]] db))]
    (with-db [conn]
      (doseq [[k n] bonds]
        (if (> (count n) 50000)
          ((println k (count n))
           (d/transact conn [[:db/retract k
                              :orcpub.dnd.e5.character/bonds n]])))))))

(defn cleanup-character-name []
  (let [name
        (with-db [db] (d/q '[:find ?e ?doc
                             :where
                             [?e :orcpub.dnd.e5.character/character-name ?doc]] db))]
    (with-db [conn]
      (doseq [[k n] name]
        (if (> (count n) 255)
          ((println k (count n))
           (d/transact conn [[:db/retract k
                              :orcpub.dnd.e5.character/character-name n]])))))))

(defn cleanup-description []
  (let [description
        (with-db [db] (d/q '[:find ?e ?doc
                             :where
                             [?e :orcpub.dnd.e5.character/description ?doc]] db))]
    (with-db [conn]
      (doseq [[k n] description]
        (if (> (count n) 50000)
          ((println k (count n))
           (d/transact conn [[:db/retract k
                              :orcpub.dnd.e5.character/description n]]))
          (println k (count n)))))))

(defn cleanup-faction-name []
  (let [faction-name
        (with-db [db] (d/q '[:find ?e ?doc
                             :where
                             [?e :orcpub.dnd.e5.character/faction-name ?doc]] db))]
    (with-db [conn]
      (doseq [[k n] faction-name]
        (if (> (count n) 50000)
          ((println k (count n))
           (d/transact conn [[:db/retract k
                              :orcpub.dnd.e5.character/faction-name n]])))))))


(defn cleanup-flaws []
  (let [flaws
        (with-db [db] (d/q '[:find ?e ?doc
                             :where
                             [?e :orcpub.dnd.e5.character/flaws ?doc]] db))]
    (with-db [conn]
      (doseq [[k n] flaws]
        (if (> (count n) 50000)
          ((println k (count n))
           (d/transact conn [[:db/retract k
                              :orcpub.dnd.e5.character/flaws n]])))))))
(defn cleanup-hair []
  (let [hair
        (with-db [db] (d/q '[:find ?e ?doc
                             :where
                             [?e :orcpub.dnd.e5.character/hair ?doc]] db))]
    (with-db [conn]
      (doseq [[k n] hair]
        (if (> (count n) 255)
          ((println k (count n))
           (d/transact conn [[:db/retract k
                              :orcpub.dnd.e5.character/hair n]])))))))

(defn cleanup-height []
  (let [height
        (with-db [db] (d/q '[:find ?e ?doc
                             :where
                             [?e :orcpub.dnd.e5.character/height ?doc]] db))]
    (with-db [conn]
      (doseq [[k n] height]
        (if (> (count n) 255)
          ((println k (count n))
           (d/transact conn [[:db/retract k
                              :orcpub.dnd.e5.character/height n]])))))))

(defn cleanup-ideals []
  (let [ideals
        (with-db [db] (d/q '[:find ?e ?doc
                             :where
                             [?e :orcpub.dnd.e5.character/ideals ?doc]] db))]
    (with-db [conn]
      (doseq [[k n] ideals]
        (if (> (count n) 50000)
          ((println k (count n))
           (d/transact conn [[:db/retract k
                              :orcpub.dnd.e5.character/ideals n]])))))))

(defn cleanup-notes []
  (let [notes
        (with-db [db] (d/q '[:find ?e ?doc
                             :where
                             [?e :orcpub.dnd.e5.character/notes ?doc]] db))]
    (with-db [conn]
      (doseq [[k n] notes]
        (if (> (count n) 50000)
          ((println k (count n))
           #_(d/transact conn [{:db/id k
                                :orcpub.dnd.e5.character/notes (subs n 0 50000)}]))
          (println k (count n)))))))

(defn cleanup-personality-trait-1 []
  (let [personality-trait-1
        (with-db [db] (d/q '[:find ?e ?doc
                             :where
                             [?e :orcpub.dnd.e5.character/personality-trait-1 ?doc]] db))]
    (with-db [conn]
      (doseq [[k n] personality-trait-1]
        (if (> (count n) 50000)
          (d/transact conn [[:db/retract k
                             :orcpub.dnd.e5.character/personality-trait-1 n]])
          #_(println k (count n)))))))

(defn cleanup-personality-trait-2 []
  (let [personality-trait-2
        (with-db [db] (d/q '[:find ?e ?doc
                             :where
                             [?e :orcpub.dnd.e5.character/personality-trait-2 ?doc]] db))]
    (with-db [conn]
      (doseq [[k n] personality-trait-2]
        (if (> (count n) 50000)
          (d/transact conn [[:db/retract k
                             :orcpub.dnd.e5.character/personality-trait-2 n]])
          #_(println k (count n)))))))

(defn cleanup-sex []
  (let [sex
        (with-db [db] (d/q '[:find ?e ?doc
                             :where
                             [?e :orcpub.dnd.e5.character/sex ?doc]] db))]
    (with-db [conn]
      (doseq [[k n] sex]
        (if (> (count n) 255)
          (d/transact conn [[:db/retract k
                             :orcpub.dnd.e5.character/sex n]])
          #_(println k (count n)))))))

(defn cleanup-skin []
  (let [skin
        (with-db [db] (d/q '[:find ?e ?doc
                             :where
                             [?e :orcpub.dnd.e5.character/skin ?doc]] db))]
    (with-db [conn]
      (doseq [[k n] skin]
        (if (> (count n) 255)
          (d/transact conn [[:db/retract k
                             :orcpub.dnd.e5.character/skin n]])
          #_(println k (count n)))))))

(defn cleanup-weight []
  (let [weight
        (with-db [db] (d/q '[:find ?e ?doc
                             :where
                             [?e :orcpub.dnd.e5.character/weight ?doc]] db))]
    (with-db [conn]
      (doseq [[k n] weight]
        (if (> (count n) 255)
          (d/transact conn [[:db/retract k
                             :orcpub.dnd.e5.character/weight n]])
          #_(println k (count n)))))))


(defn cleanup-users []
  (let [userdata
        (with-db [db] (d/q '[:find ?e :where [?e :orcpub.user/verified? false]] db))]
    (with-db [conn]
      (doseq [[k] userdata]
        (d/transact conn [[:db/retractEntity k]]))))

  #_(let [userdata2
        (with-db [db] (d/q '[:find ?e ?doc ?created
                                   :where
                                   [?e :orcpub.user/email ?doc]
                                   [?e :orcpub.user/created ?created]
                                   [(missing? $ ?e :orcpub.user/last-login)]] db))]
    (with-db [conn]
      (doseq [[k] userdata2]
        (d/transact conn [[:db/retractEntity k]])))))

(defn dumpusers []
  (let [userdata
        (with-db [db] (d/q '[:find ?e ?username ?email ?verified ?sendupdates ?lastlogin
                                   :where
                                   [?e :orcpub.user/username ?username]
                                   [?e :orcpub.user/email ?email]
                                   [?e :orcpub.user/verified? ?verified]
                                   [?e :orcpub.user/send-updates? ?sendupdates]
                                   [?e :orcpub.user/last-login ?lastlogin]
                                   ] db))]
    (with-open [out-file (io/writer "users.csv")]
      (csv/write-csv out-file userdata))))

(defn dumpusers2 []
  (let [userdata
        (with-db [db] (d/q '[:find ?e ?doc ?created
                                   :where
                                   [?e :orcpub.user/email ?doc]
                                   [?e :orcpub.user/created ?created]
                                   [(missing? $ ?e :orcpub.user/last-login)]] db))]
    (with-open [out-file (io/writer "users.csv")]
      (csv/write-csv out-file userdata))))

(defn dump-unverifiedusers []
  (let [userdata
        (with-db [db] (d/q '[:find ?e :where [?e :orcpub.user/verified? false] ] db) )]
    (with-open [out-file (io/writer "users.csv")]
      (csv/write-csv out-file userdata))))

(defn fixsrd []
  (println "fix tashas-hideous-laughter")
  (let [u1
        (with-db [db] (d/q '[:find ?e :where [?e :orcpub.entity.strict/key :tashas-hideous-laughter]] db))]
    (with-db [conn]
             (doseq [[k] u1]
               (println k)
               (d/transact conn [{:db/id k
                                        :orcpub.entity.strict/key :hideous-laughter}])
               )
             )
    )

  (println "fix :bigbys-hand")
  (let [u1
        (with-db [db] (d/q '[:find ?e :where [?e :orcpub.entity.strict/key :bigbys-hand]] db))]
    (with-db [conn]
             (doseq [[k] u1]
               (println k)
               (d/transact conn [{:db/id k
                                        :orcpub.entity.strict/key :arcane-hand}])
               )
             )
    )

  (println "fix :drawmijs-instant-summons")
  (let [u1
        (with-db [db] (d/q '[:find ?e :where [?e :orcpub.entity.strict/key :drawmijs-instant-summons]] db))]
    (with-db [conn]
             (doseq [[k] u1]
               (println k)
               (d/transact conn [{:db/id k
                                        :orcpub.entity.strict/key :instant-summons}])
               )
             )
    )

  (println "fix :evards-black-tentacles")
  (let [u1
        (with-db [db] (d/q '[:find ?e :where [?e :orcpub.entity.strict/key :evards-black-tentacles]] db))]
    (with-db [conn]
             (doseq [[k] u1]
               (println k)
               (d/transact conn [{:db/id k
                                        :orcpub.entity.strict/key :black-tentacles}])
               )
             )
    )

  (println "fix :leomunds-tiny-hut")
  (let [u1
        (with-db [db] (d/q '[:find ?e :where [?e :orcpub.entity.strict/key :leomunds-tiny-hut]] db))]
    (with-db [conn]
             (doseq [[k] u1]
               (println k)
               (d/transact conn [{:db/id k
                                        :orcpub.entity.strict/key :tiny-hut}])
               )
             )
    )

  (println "fix :leomunds-secret-chest")
  (let [u1
        (with-db [db] (d/q '[:find ?e :where [?e :orcpub.entity.strict/key :leomunds-secret-chest]] db))]
    (with-db [conn]
             (doseq [[k] u1]
               (println k)
               (d/transact conn [{:db/id k
                                        :orcpub.entity.strict/key :secret-chest}])
               )
             )
    )

  (println "fix melfs-acid-arrow")
  (let [u
        (with-db [db] (d/q '[:find ?e :where [?e :orcpub.entity.strict/key :melfs-acid-arrow]] db))]
    (with-db [conn]
             (doseq [[k] u]
               (println k)
               (d/transact conn [{:db/id k
                                        :orcpub.entity.strict/key :acid-arrow}])
               )
             )
    )

  (println "fix :mordenkainens-faithful-hound")
  (let [u
        (with-db [db] (d/q '[:find ?e :where [?e :orcpub.entity.strict/key :mordenkainens-faithful-hound]] db))]
    (with-db [conn]
             (doseq [[k] u]
               (println k)
               (d/transact conn [{:db/id k
                                        :orcpub.entity.strict/key :faithful-hound}])
               )
             )
    )

  (println "fix :mordenkainens-magnificent-mansion")
  (let [u
        (with-db [db] (d/q '[:find ?e :where [?e :orcpub.entity.strict/key :mordenkainens-magnificent-mansion]] db))]
    (with-db [conn]
             (doseq [[k] u]
               (println k)
               (d/transact conn [{:db/id k
                                        :orcpub.entity.strict/key :magnificent-mansion}])
               )
             )
    )

  (println "fix :mordenkainens-private-sanctum")
  (let [u
        (with-db [db] (d/q '[:find ?e :where [?e :orcpub.entity.strict/key :mordenkainens-private-sanctum]] db))]
    (with-db [conn]
             (doseq [[k] u]
               (println k)
               (d/transact conn [{:db/id k
                                        :orcpub.entity.strict/key :private-sanctum}])
               )
             )
    )

  (println "fix :mordenkainens-sword")
  (let [u
        (with-db [db] (d/q '[:find ?e :where [?e :orcpub.entity.strict/key :mordenkainens-sword]] db))]
    (with-db [conn]
             (doseq [[k] u]
               (println k)
               (d/transact conn [{:db/id k
                                        :orcpub.entity.strict/key :arcane-sword}])
               )
             )
    )

  (println "fix :nystuls-magic-aura")
  (let [u
        (with-db [db] (d/q '[:find ?e :where [?e :orcpub.entity.strict/key :nystuls-magic-aura]] db))]
    (with-db [conn]
             (doseq [[k] u]
               (println k)
               (d/transact conn [{:db/id k
                                        :orcpub.entity.strict/key :magic-aura}])
               )
             )
    )

  (println "fix :otilukes-freezing-sphere")
  (let [u
        (with-db [db] (d/q '[:find ?e :where [?e :orcpub.entity.strict/key :otilukes-freezing-sphere]] db))]
    (with-db [conn]
             (doseq [[k] u]
               (println k)
               (d/transact conn [{:db/id k
                                        :orcpub.entity.strict/key :freezing-sphere}])
               )
             )
    )

  (println "fix :otilukes-resilient-sphere")
  (let [u
        (with-db [db] (d/q '[:find ?e :where [?e :orcpub.entity.strict/key :otilukes-resilient-sphere]] db))]
    (with-db [conn]
             (doseq [[k] u]
               (println k)
               (d/transact conn [{:db/id k
                                        :orcpub.entity.strict/key :resilient-sphere}])
               )
             )
    )

  (println "fix :ottos-irresistible-dance")
  (let [u
        (with-db [db] (d/q '[:find ?e :where [?e :orcpub.entity.strict/key :ottos-irresistible-dance]] db))]
    (with-db [conn]
             (doseq [[k] u]
               (println k)
               (d/transact conn [{:db/id k
                                        :orcpub.entity.strict/key :irresistible-dance}])
               )
             )
    )

  (println "fix :tashas-hideous-laughter")
  (let [u
        (with-db [db] (d/q '[:find ?e :where [?e :orcpub.entity.strict/key :tashas-hideous-laughter]] db))]
    (with-db [conn]
             (doseq [[k] u]
               (println k)
               (d/transact conn [{:db/id k
                                        :orcpub.entity.strict/key :hideous-laughter}])
               )
             )
    )

  (println "fix :tensers-floating-disk")
  (let [u
        (with-db [db] (d/q '[:find ?e :where [?e :orcpub.entity.strict/key :tensers-floating-disk]] db))]
    (with-db [conn]
             (doseq [[k] u]
               (println k)
               (d/transact conn [{:db/id k
                                        :orcpub.entity.strict/key :floating-disk}])
               )
             )
    )

  )



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
