(ns orcpub.dnd.e5.artist-profile
  "What an artist's public profile page (/artists/<slug>) shows, as data: name, pieces,
   example portraits and listed links. Reads only the portrait-assets registry and its
   overrides; nothing is stored or tied to a site account. Used by the server (share tags,
   og image) and the browser (the page). Entry points: `profile`, `artist-by-slug`,
   `example-portraits`, `listed-links`, `profile-link`.
   Plan and decisions: docs/design/artist-profiles/PLAN.md."
  (:require [clojure.string :as s]
            [orcpub.route-map :as route-map]
            [orcpub.dnd.e5.portrait-assets :as pa]))

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
  "Whether the artist wants a profile page. On unless :artist/profile? is
   explicitly false -- the switch an artist's account setting will flip. Off
   means no page at all, not a page saying it is off: the address 404s like a
   slug nobody has, they drop out of /artists, and the credit loses its
   'About the artist' line. The credit's name still goes to their own link."
  [artist]
  (not (false? (:artist/profile? artist))))

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

(defn link-host
  "The host of `url` without scheme, www. or trailing path: \"fusspot.rip\". nil if none."
  [url]
  (some->> url (re-find #"(?i)^https?://(?:www\.)?([^/?#]+)") second not-empty))

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
  "Whether `u` is an http(s) address with no userinfo. Everything else -- a
   mailto:, a javascript:, a bare address -- is not something this page links
   to. `https://example.test/@name` passes: the @ is in the path."
  [u]
  (boolean (and (string? u) (re-find #"(?i)^https?://[^\s@/?#]+(?:[/?#]\S*)?$" u))))

(defn listed-links
  "The links the artist listed, and only those: :artist/links, or their single
   :artist/link as a site link when they have not given a labelled set.
   Anything that is not an http(s) URL is dropped, so an email address cannot
   reach the page even if one is put in the registry. Duplicates are dropped,
   first one kept."
  [artist]
  (let [links (or (seq (:artist/links artist))
                  (when-let [l (:artist/link artist)]
                    [{:link/label "Site" :link/icon "site" :link/url l}]))]
    (->> links
         (filter (comp web-url? :link/url))
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
           offset (fn [k] (pa/seed->int (str base "/" (name k))))]
       (vec
        (for [i (range n)]
          (let [rnd (pa/mulberry32 (pa/seed->int (str base "/" i)))
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
  "The share-card description of `artist`'s profile, naming them and their piece count."
  [artist]
  (let [n (piece-count artist)]
    (str "Portrait art by " (:artist/name artist) ": "
         n " hand-drawn " (if (= 1 n) "piece" "pieces")
         ", a few portraits made from them, and where to find more.")))
