(ns orcpub.dnd.e5.artist-profile
  "What an artist's public profile page (/artists/<slug>) shows, as data: name, pieces,
   example portraits and listed links. Reads only the portrait-assets registry and its
   overrides; nothing is stored or tied to a site account. Used by the server (share tags,
   og image) and the browser (the page). Entry points: `profile`, `artist-by-slug`,
   `example-portraits`, `listed-links`, `profile-link`.
   Plan and decisions: docs/design/artist-profiles/PLAN.md."
  (:require [clojure.string :as s]
            [orcpub.artist-links :as artist-links]
            [orcpub.route-map :as route-map]
            [orcpub.dnd.e5.portrait-assets :as pa]
            [orcpub.dnd.e5.portrait-random :as random]))

;; ---------- addresses ----------

(defn slug
  "The artist's address segment: :artist/slug, or the id's name when there is
   none. Read from the registry entry, never from the overrides, so a URL does
   not move when a deployment restates a name."
  [artist]
  (or (:artist/slug artist)
      (some-> (:artist/id artist) name)))

(defn- registry-entry
  "The registry entry for `artist-id`, without deployment overrides, or nil."
  [artist-id]
  (some #(when (= artist-id (:artist/id %)) %) pa/registry))

(defn profile-enabled?
  "Whether `artist` has a profile page: on unless :artist/profile? is false, which the artist sets
   on My Account (or the deployment forces). Off means /artists/<slug> redirects to /artists, they
   drop out of the list, their example images 404 and the credit loses its 'About the artist'
   line; the credit itself, and its link to the artist's own site, stay."
  [artist]
  (not (false? (:artist/profile? artist))))

(defn shows?
  "Whether `artist`'s page shows `part` (:examples, :links or :count): on unless they switched it
   off (:artist/show-examples? and the like false). The name always shows."
  [artist part]
  (not (false? (get artist (keyword "artist" (str "show-" (name part) "?"))))))

(defn profile
  "The artist, overrides applied, if they have a profile: they are registered,
   have said how they want to be named, and have not turned the page off. An
   artist with no :artist/name is skipped the way the credit skips them,
   rather than given a page nobody chose."
  [artist-id]
  (when-let [a (pa/artist-info artist-id)]
    (when (and (not (s/blank? (:artist/name a))) (profile-enabled? a))
      (assoc a :artist/slug (slug (registry-entry artist-id))))))

(defn artist-by-slug
  "The profile for a URL segment, or nil. Case and surrounding space are
   ignored, since people retype these."
  [segment]
  (when (string? segment)
    (let [wanted (s/lower-case (s/trim segment))]
      (some #(when (= wanted (some-> (slug %) s/lower-case))
               (profile (:artist/id %)))
            pa/registry))))

(defn registered-by-slug
  "The registry entry for a URL segment whether or not its page is on, or nil: what tells a page
   switched off (a redirect) from an address nobody has (a 404)."
  [segment]
  (when (string? segment)
    (let [wanted (s/lower-case (s/trim segment))]
      (some #(when (= wanted (some-> (slug %) s/lower-case)) %) pa/registry))))

(defn all-profiles
  "Every artist with a profile, in registry order."
  []
  (into [] (keep (comp profile :artist/id)) pa/registry))

(defn profile-path
  "The address of `artist`'s profile page, /artists/<slug>."
  [artist]
  (route-map/path-for route-map/artist-page-route :slug (slug artist)))

(defn index-path
  "The address of the list of artists, /artists."
  []
  (route-map/path-for route-map/artists-page-route))

(defn example-path
  "The address of `artist`'s example image `n`, counted from 1."
  [artist n]
  (route-map/path-for route-map/artist-example-route :slug (slug artist) :n (str n)))

(defn ascii-url
  "`url` with an international host in its encoded (punycode) form, so lookalike letters show as
   what they are: https://ex\u0430mple.test/ is https://xn--exmple-4nf.test/. Anything else as given."
  [url]
  (let [u (some-> url str s/trim)]
    (if-let [[_ head host tail] (some->> u (re-find #"^(https?://)([^/?#:]+)(.*)$"))]
      (if (re-find #"[^\x00-\x7F]" host)
        (str head #?(:clj (java.net.IDN/toASCII host) :cljs (.-hostname (js/URL. (str "https://" host)))) tail)
        u)
      u)))

(defn link-host
  "The host of `url` without scheme, www. or trailing path, encoded: \"fusspot.rip\". nil if none."
  [url]
  (some->> (ascii-url url) (re-find #"(?i)^https?://(?:www\.)?([^/?#]+)") second not-empty))

(defn watermark-text
  "What is tiled across `artist`'s example images: the host of their own link (else their
   name), then `site` when given, joined by a middle dot."
  [artist site]
  (s/join " \u00b7 " (remove s/blank? [(or (link-host (:artist/link artist))
                                            (:artist/name artist))
                                        site])))

(defn profile-link
  "Where the credit's secondary link goes, for the named artists on a
   portrait: the one artist's profile, or the list when there are several.
   nil when nobody named is on canvas, so the credit can drop the line."
  [named-artists]
  (let [with-profiles (filter #(profile (:artist/id %)) named-artists)]
    (case (count with-profiles)
      0 nil
      1 {:href (profile-path (first with-profiles)) :label "About the artist"}
      {:href (index-path) :label "About the artists"})))

;; ---------- what they drew ----------

(defn pieces-by-layer
  "The artist's pieces grouped by layer, most of the picture first -- the
   order the credit weighs layers in -- then z-order. The hidden scalp is left
   out: it is a generated patch, not anyone's drawing (see ARTISTS.md).
   Each group is {:layer k :label \"Head\" :pieces [asset ...]}."
  [artist]
  (->> pa/pickable-layers
       (keep (fn [k]
               (when-let [pieces (seq (get-in artist [:artist/layers k]))]
                 {:layer k :label (pa/layer-labels k) :pieces (vec pieces)})))
       (sort-by #(- (get pa/layer-credit-weight (:layer %) 1)))
       vec))

(defn piece-count
  "How many pieces `artist` drew, the generated scalp not counted."
  [artist]
  (reduce + (map (comp count :pieces) (pieces-by-layer artist))))

;; ---------- links ----------

(defn web-url?
  "Whether `u` is a link this site will draw: the rule artist edits are saved under
   (orcpub.artist-links/url-problem), https only, hostname characters only, no userinfo, its host
   read encoded, no whitespace. A mailto:, a javascript:, plain http and a bare address are not."
  [u]
  (and (string? u) (not (re-find #"\s" (s/trim u))) (nil? (artist-links/url-problem (ascii-url u)))))

(defn external-link
  "Anchor attributes for a link to an artist's own site, or nil when `url` is not one this site
   draws: a new tab without access to this one (rel noopener). The referrer is sent, as the
   site-wide Referrer-Policy allows (the domain only), so the artist sees where visitors came from."
  [url]
  (when (web-url? url)
    {:href (ascii-url url) :target "_blank" :rel "noopener"}))

(defn listed-links
  "The links the artist listed, and only those: :artist/links, or their single
   :artist/link as a site link when they have not given a labelled set.
   Anything that is not an http(s) URL is dropped, so an email address cannot
   reach the page even if one is put in the registry. Duplicates are dropped,
   first one kept."
  [artist]
  (let [links (when (shows? artist :links)
                (or (seq (:artist/links artist))
                  (when-let [l (:artist/link artist)]
                    [{:link/label "Site" :link/icon "site" :link/url l}])))]
    (->> links
         (filter (comp web-url? :link/url))
         (map #(update % :link/url ascii-url))
         (reduce (fn [[seen out] l]
                   (if (seen (:link/url l))
                     [seen out]
                     [(conj seen (:link/url l)) (conj out l)]))
                 [#{} []])
         second)))

;; ---------- example portraits ----------

(def example-count 3)

(defn- pick
  "An element of `v` chosen by `rnd`, a fn returning numbers in [0,1)."
  [rnd v]
  (nth v (int (Math/floor (* (rnd) (count v))))))

(defn example-portraits
  "`n` (default example-count) portraits built from `artist-id`'s pieces, or nil when the
   profile is off or unknown. Each of their layers rotates through their pieces; other
   layers are filled from the registry; colours come from the presets. Seeded from the
   slug, so every render agrees."
  ([artist-id] (example-portraits artist-id example-count))
  ([artist-id n]
   (when-let [artist (profile artist-id)]
     (let [base (str "artist-" (slug artist))
           own (:artist/layers artist)
           offset (fn [k] (random/seed->int (str base "/" (name k))))]
       (vec
        (for [i (range n)]
          (let [rnd (random/mulberry32 (random/seed->int (str base "/" i)))
                layers (reduce
                        (fn [acc k]
                          (let [mine (get own k)
                                asset (if (seq mine)
                                        (nth mine (mod (+ i (offset k)) (count mine)))
                                        (let [any (pa/assets-for-layer k)]
                                          (when (seq any) (pick rnd any))))]
                            (if asset
                              (assoc acc k {:artist/id (pa/artist-for-asset k (:asset/id asset))
                                            :asset/id (:asset/id asset)})
                              acc)))
                        {} pa/layer-order)
                colors (into {} (for [slot pa/color-slot-order
                                      :let [presets (pa/color-presets slot)]
                                      :when (seq presets)]
                                  [slot (pick rnd presets)]))]
            {:layers layers :colors colors :tweaks {}})))))))

(defn others-in
  "The other named artists in `portrait`, in credit order -- who an example
   borrowed pieces from."
  [portrait artist-id]
  (into []
        (filter #(and (not= artist-id (:artist/id %)) (:artist/name %)))
        (pa/credit-order (:layers portrait))))

;; ---------- share tags ----------

(defn page-title
  "The share-card title of `artist`'s profile: \"<name> — portrait artist\"."
  [artist]
  (str (:artist/name artist) " — portrait artist"))

(defn share-description
  "The share-card description of `artist`'s profile: their name, and the piece count, examples and
   links where their page shows them."
  [artist]
  (let [n (piece-count artist)]
    (str "Portrait art by " (:artist/name artist)
         (when (shows? artist :count) (str ": " n " hand-drawn " (if (= 1 n) "piece" "pieces")))
         (when (shows? artist :examples) ", a few portraits made from them")
         (when (shows? artist :links) ", and where to find more")
         ".")))
