(ns e2e-boot
  "Boots the real server against an in-memory Datomic db and seeds a verified
   user, in ONE JVM -- a mem:// database only exists inside the process that
   created it, so seeding from a separate `lein run` would talk to a different,
   empty database.

   Seeds TWO verified users, each owning custom items, because isolation is not
   observable with one: a query that returned everybody's items would look exactly
   like a correct one. kaylee/serenity99 owns three, zoe/washburne7 owns one.

   Used by scripts/e2e/run.sh. Not part of the production uberjar."
  (:require [com.stuartsierra.component :as component]
            [datomic.api :as d]
            [orcpub.system :as s]
            [user :as dev]))

(defn- item
  "A minimal custom magic item owned by `owner`. Enough attributes to survive
   expand-magic-items and to show up in My Items; not a realistic item."
  [owner nm]
  {:orcpub.dnd.e5.magic-items/name        nm
   :orcpub.dnd.e5.magic-items/owner       owner
   :orcpub.dnd.e5.magic-items/type        :wondrous-item
   :orcpub.dnd.e5.magic-items/rarity      :rare
   :orcpub.dnd.e5.magic-items/description (str nm " — seeded by e2e-boot.")})

(defn -main [& _]
  ;; The :dev system pins port 8890; run.sh passes E2E_PORT through as PORT.
  (let [port (some-> (System/getenv "PORT") Integer/parseInt)
        sys  (component/start (cond-> (s/system :dev)
                                port (assoc-in [:service-map :io.pedestal.http/port] port)))
        conn (get-in sys [:conn :conn])]
    ;; TWO accounts, each with its OWN items. One account cannot show isolation:
    ;; a bug that returned every user's items would look identical to correct
    ;; behaviour. The names are deliberately distinct per owner so a leak between
    ;; them is visible in a list without cross-referencing ids.
    (dev/create-user! conn {:username "kaylee"
                            :email    "kaylee@example.com"
                            :password "serenity99"
                            :verify?  true})
    (dev/create-user! conn {:username "zoe"
                            :email    "zoe@example.com"
                            :password "washburne7"
                            :verify?  true})
    @(d/transact conn [(item "kaylee" "Kaylee Seeded Alpha")
                       (item "kaylee" "Kaylee Seeded Beta")
                       (item "kaylee" "Kaylee Seeded Gamma")
                       (item "zoe"    "Zoe Seeded Only")])
    (println "E2E-READY")
    (flush)
    @(promise)))
