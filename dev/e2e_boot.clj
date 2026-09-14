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
            [orcpub.routes :as routes]
            [orcpub.entity.strict :as se]
            [orcpub.dnd.e5.character :as char5e]
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

(defn- fighter-carrying
  "A level 1 fighter with one of its owner's custom items equipped, so a browser check can open a
   character whose sheet needs an item from another account."
  [character-name item-key]
  {::se/summary    {::char5e/character-name character-name
                    ::char5e/classes        [{::char5e/class-name "Fighter" ::char5e/level 1}]}
   ::se/selections [{::se/key    :ability-scores
                     ::se/option {::se/key       :standard-scores
                                  ::se/map-value {::char5e/str 15 ::char5e/dex 14 ::char5e/con 13
                                                  ::char5e/int 12 ::char5e/wis 10 ::char5e/cha 8}}}
                    {::se/key     :class
                     ::se/options [{::se/key        :fighter
                                    ::se/selections [{::se/key     :levels
                                                      ::se/options [{::se/key :level-1}]}]}]}
                    {::se/key     :other-magic-items
                     ::se/options [{::se/key item-key}]}]})

(defn- knowing-language
  "The same fighter, also knowing a homebrew language. The browser suite puts that language in the
   owner's local library, which is where homebrew lives; the server never has it."
  [character-name item-key language-key]
  (update (fighter-carrying character-name item-key) ::se/selections conj
          {::se/key :languages ::se/options [{::se/key language-key}]}))

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
    ;; Printed so a check can open the character without logging in.
    (let [{:keys [status body]} (routes/do-save-character (d/db conn) conn
                                                          (fighter-carrying "Bree Tinker" :kaylee-seeded-alpha)
                                                          {:user "kaylee"})]
      (println "E2E-CHARACTER" status (:db/id body)))
    (let [{:keys [status body]} (routes/do-save-character (d/db conn) conn
                                                          (knowing-language "Wren Holloway" :kaylee-seeded-beta :e2e-cant)
                                                          {:user "kaylee"})]
      (println "E2E-HOMEBREW-CHARACTER" status (:db/id body)))
    (println "E2E-READY")
    (flush)
    @(promise)))
