(ns orcpub.artist-credit
  "How an artist is credited, and who gets the last word on it.

   Three layers, later ones winning field by field:

     1. the registry in portrait-assets.cljc   -- the public default
     2. the artist's own edits, from their account settings
     3. PORTRAIT_ARTISTS in the deployment config

   The config wins so the operators can always correct a credit -- a typo, a
   dead link, a compromised account -- without touching anyone's account. The
   artist's edits win over the code so they never need a release to change
   their own handle.

   Only presentation is editable: name, primary link and the icon links.
   Which art is whose stays in the registry, so no account can re-attribute
   anyone's work.

   Edits are stored on the account as EDN and cleaned on the way in AND on the
   way out, so a value written by some older or buggier version still cannot
   put a javascript: link or an arbitrary colour on the page."
  (:require [clojure.edn :as edn]
            [clojure.string :as s]
            [datomic.api :as d]
            [orcpub.fork.branding :as branding]
            [orcpub.artist-links :as links]
            [orcpub.dnd.e5.portrait-assets :as pa]))

;; ---------------------------------------------------------------------------
;; What an artist may set -- the rules live in orcpub.artist-links, which the
;; account page also runs as you type
;; ---------------------------------------------------------------------------

(def max-name-length links/max-name-length)
(def max-url-length links/max-url-length)
(def max-links links/max-links)
(def services links/services)
(def icon-for-url links/icon-for-url)
(def url-problem links/url-problem)
(def ^:private link-entry links/link-entry)
(def ^:private clean-name links/clean-name)

(defn check
  "Validate an edit submitted from the settings page:
     {:name \"..\" :link \"..\" :links [{:url \"..\"}]}
   Each link's icon is worked out from its address.
   Returns {:credit <clean stored form>} or {:errors {field message}}.
   A blank name or link means \"use the default\"."
  [{:keys [name link links] :as edit}]
  (let [errors (links/edit-errors edit)
        nm (clean-name name)
        link (some-> link s/trim not-empty)
        urls (->> links (map #(some-> (:url %) str s/trim)) (remove s/blank?))]
    (if (seq errors)
      {:errors errors}
      {:credit (cond-> {}
                 nm (assoc :artist/name nm)
                 link (assoc :artist/link link)
                 (seq urls) (assoc :artist/links (mapv link-entry urls)))})))

(defn read-stored
  "The stored EDN, re-checked. Anything that no longer passes is dropped
   field by field rather than taking the whole credit down."
  [stored]
  (let [m (try (edn/read-string {:readers {} :default (fn [_ _] nil)} stored)
               (catch Exception _ nil))]
    (when (map? m)
      (let [nm (clean-name (:artist/name m))
            link (:artist/link m)
            links (->> (when (sequential? (:artist/links m)) (:artist/links m))
                       (keep :link/url)
                       (filter #(and (string? %) (nil? (url-problem %))))
                       (take max-links))]
        (not-empty
         (cond-> {}
           (and nm (<= (count nm) max-name-length)) (assoc :artist/name nm)
           (and (string? link) (nil? (url-problem link))) (assoc :artist/link link)
           ;; icons re-derived from the links, whatever was stored
           (seq links) (assoc :artist/links (mapv link-entry links))))))))

;; ---------------------------------------------------------------------------
;; Layering
;; ---------------------------------------------------------------------------

(defn db-edits
  "{artist-id credit} from every linked account that has saved edits. If two
   accounts ever speak for one artist, neither's edits are trusted."
  [db]
  (let [rows (d/q '[:find ?a ?c
                    :where [?e :orcpub.user/artist ?a]
                           [?e :orcpub.user/artist-credit ?c]]
                  db)]
    (into {}
          (for [[a group] (group-by first rows)
                :when (= 1 (count group))
                :let [credit (read-stored (second (first group)))]
                :when credit]
            [a credit]))))

(defn effective
  "Artist edits under deployment config, field by field."
  [edits config]
  (merge-with merge edits config))

(defonce ^:private applied-t (atom nil))

(defn refresh!
  "Apply the current layering to portrait-assets, which both the server
   renders and the page's __BRANDING__ read from. Called per page render and
   per share-card render with the request's db, so every container catches up
   with an edit saved on any other one on its next request; cheap when nothing
   changed, because it only re-reads when the database has moved on."
  [db]
  (let [t [(:id db) (d/basis-t db)]]
    (when (not= t @applied-t)
      (pa/set-artist-overrides! (effective (db-edits db) branding/portrait-artists))
      (reset! applied-t t))))

;; ---------------------------------------------------------------------------
;; Saving
;; ---------------------------------------------------------------------------

(defn credit-for-account
  "What the settings page shows an artist: {:current <what the site shows>
   :own <their saved edits> :locked #{fields the config decides}}."
  [db user-id]
  (let [{:orcpub.user/keys [artist artist-credit]}
        (d/pull db [:orcpub.user/artist :orcpub.user/artist-credit] user-id)]
    (when artist
      (let [own (some-> artist-credit read-stored)
            config (get branding/portrait-artists artist)
            base (some #(when (= artist (:artist/id %)) %) pa/registry)]
        {:artist-id artist
         :default (select-keys base [:artist/name :artist/link :artist/links])
         :current (merge base own config)
         :own own
         :locked (set (keys config))}))))

(defn save!
  "Save `edit` as the credit for the artist `user-id` speaks for. Returns
   {:errors ..}, {:unchanged true}, or {:saved credit :before old :artist-id id}."
  [conn user-id edit]
  (let [db (d/db conn)
        {:orcpub.user/keys [artist artist-credit]}
        (d/pull db [:orcpub.user/artist :orcpub.user/artist-credit] user-id)]
    (if-not artist
      {:errors {:account "This account isn't an artist account"}}
      (let [{:keys [errors credit]} (check edit)
            before (some-> artist-credit read-stored)]
        (cond
          errors {:errors errors}
          (= (not-empty credit) before) {:unchanged true}
          :else
          (do @(d/transact conn [(if (seq credit)
                                   {:db/id user-id :orcpub.user/artist-credit (pr-str credit)}
                                   [:db/retract user-id :orcpub.user/artist-credit artist-credit])])
              (refresh! (d/db conn))
              {:saved credit :before before :artist-id artist}))))))
