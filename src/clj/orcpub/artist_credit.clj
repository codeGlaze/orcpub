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
            [orcpub.dnd.e5.portrait-assets :as pa])
  (:import [java.net URI]))

;; ---------------------------------------------------------------------------
;; What an artist may set
;; ---------------------------------------------------------------------------

(def max-name-length 60)
(def max-url-length 200)
(def max-links
  "The credit lockup flanks the name with at most four marks."
  4)

(def services
  "The marks a link can wear, each with its brand colour and the hosts it
   belongs to. The icon is worked out from the link, never chosen, so it
   can't disagree with where the link goes -- a Twitch mark that opened some
   other site is exactly the trick a lookalike phishing link plays. Anything
   that isn't a known service gets `site`, a plain globe."
  (array-map
   "twitch"  {:label "Twitch"  :color "#9146ff" :hosts #{"twitch.tv"}}
   "bluesky" {:label "Bluesky" :color "#1185fe" :hosts #{"bsky.app"}}
   "kofi"    {:label "Ko-fi"   :color "#ff5e5b" :hosts #{"ko-fi.com"}}
   "site"    {:label "Site"    :color "#f0a100" :hosts nil}))

(defn- host-of [url]
  (try (some-> (URI. url) .getHost s/lower-case) (catch Exception _ nil)))

(defn- host-matches? [host allowed]
  (some #(or (= host %) (s/ends-with? host (str "." %))) allowed))

(defn icon-for-url
  "The mark for a link: the service whose host it points at, else `site`."
  [url]
  (let [host (or (host-of (some-> url s/trim)) "")]
    (or (some (fn [[icon {:keys [hosts]}]] (when (and hosts (host-matches? host hosts)) icon))
              services)
        "site")))

(defn url-problem
  "Why `url` can't be used as a credit link, or nil when it can. Only https:
   a credit link is followed by strangers, and an http one can be rewritten
   on the way."
  [url]
  (let [url (some-> url s/trim)
        uri (try (URI. url) (catch Exception _ nil))
        host (host-of url)]
    (cond
      (s/blank? url) "Add a link"
      (> (count url) max-url-length) (str "Links can be up to " max-url-length " characters")
      (or (nil? uri) (not= "https" (some-> uri .getScheme s/lower-case)))
      "Links must start with https://"
      (or (s/blank? host) (not (s/includes? host "."))) "That doesn't look like a web address"
      (some? (.getUserInfo uri)) "That doesn't look like a web address")))

(defn- link-entry [url]
  (let [icon (icon-for-url url)
        {:keys [label color]} (services icon)]
    {:link/label label :link/icon icon :link/color color :link/url url}))

(defn- clean-name [n]
  (let [n (some-> n str s/trim (s/replace #"\s+" " "))]
    (when-not (s/blank? n) n)))

(defn check
  "Validate an edit submitted from the settings page:
     {:name \"..\" :link \"..\" :links [{:url \"..\"}]}
   Each link's icon is worked out from its address.
   Returns {:credit <clean stored form>} or {:errors {field message}}.
   A blank name or link means \"use the default\"."
  [{:keys [name link links]}]
  (let [nm (clean-name name)
        link (some-> link s/trim not-empty)
        links (->> links
                   (map #(some-> (:url %) str s/trim))
                   (remove s/blank?))
        link-errors (into {}
                          (keep-indexed (fn [i url] (when-let [p (url-problem url)] [i p])))
                          links)
        errors (cond-> {}
                 (and nm (> (count nm) max-name-length))
                 (assoc :name (str "Names can be up to " max-name-length " characters"))
                 (and link (url-problem link)) (assoc :link (url-problem link))
                 (> (count links) max-links) (assoc :links (str "Up to " max-links " links"))
                 (seq link-errors) (assoc :link-errors link-errors))]
    (if (seq errors)
      {:errors errors}
      {:credit (cond-> {}
                 nm (assoc :artist/name nm)
                 link (assoc :artist/link link)
                 (seq links) (assoc :artist/links (mapv link-entry links)))})))

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
