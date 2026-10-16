(ns orcpub.dnd.e5.character-drafts
  "One builder draft per character, in localStorage under \"character-draft/<key>\": the character's
   id, or for one never saved a key made in this browser. Each tab keeps the key it shows in
   sessionStorage, and marks it live while open. Rules and plan: plan-character-drafts.md."
  (:require [clojure.string :as s]))

(def prefix "character-draft/")
(def legacy-key "character")
(def ^:private tab-key-name "builder-draft-key")
(def ^:private tab-id-name "builder-tab-id")
(def ^:private live-prefix "draft-live/")

;; A tab that has not written its live mark for this long is taken to be closed.
(def live-ms 60000)

(defn storage-key
  "The localStorage key holding the draft for draft key `k`."
  [k]
  (str prefix k))

(defn new-key
  "A fresh draft key for a character never saved: \"new-\" and a random uuid."
  []
  (str "new-" (random-uuid)))

(defn new-key?
  "Whether draft key `k` belongs to a character never saved."
  [k]
  (and (string? k) (s/starts-with? k "new-")))

(defn kept-key
  "The draft key that keeps a draft offered or set aside, so opening its character does not
   overwrite it: \"kept-\" and a random uuid."
  []
  (str "kept-" (random-uuid)))

(defn kept-key?
  "Whether draft key `k` holds a draft that was offered or set aside."
  [k]
  (and (string? k) (s/starts-with? k "kept-")))

(defn key-for
  "The draft key for `character`, given `current`, the key this tab holds: the character's id;
   for one never saved, its :draft/key when it was opened from one, `current` when that is a
   never-saved key too, else a fresh one."
  [character current]
  (if-let [id (:db/id character)]
    (str id)
    (or (:draft/key character)
        (if (new-key? current) current (new-key)))))

(defn- session-get
  "This tab's sessionStorage value under `k`, or nil when absent or storage is refused."
  [k]
  (try (.getItem js/sessionStorage k) (catch :default _ nil)))

(defn- session-set!
  "Stores `v` under `k` in this tab's sessionStorage. Returns whether it was stored."
  [k v]
  (try (.setItem js/sessionStorage k v) true (catch :default _ false)))

(defn tab-key
  "The draft key this tab shows, or nil for a tab that has shown none."
  []
  (session-get tab-key-name))

(defn set-tab-key!
  "Records `k` as the draft key this tab shows. Returns whether it was stored."
  [k]
  (session-set! tab-key-name k))

(defn tab-id
  "This tab's id, made on first use and kept for the tab's life."
  []
  (or (session-get tab-id-name)
      (let [id (str (random-uuid))] (session-set! tab-id-name id) id)))

(defn mark-live!
  "Marks draft key `k` as open in this tab now. Returns nil."
  [k]
  (try (.setItem js/localStorage (str live-prefix k) (str (tab-id) " " (.now js/Date)))
       (catch :default _ nil))
  nil)

(defn live-elsewhere?
  "Whether another tab has marked draft key `k` live within `live-ms` of `now`."
  [k now]
  (let [[tab at] (some-> (try (.getItem js/localStorage (str live-prefix k)) (catch :default _ nil))
                         (s/split #" "))]
    (boolean (and tab (not= tab (tab-id)) (< (- now (js/parseInt at)) live-ms)))))

(defn unmark-live!
  "Clears this tab's live mark on draft key `k`, if it is this tab's. Returns nil."
  [k]
  (try (when (s/starts-with? (or (.getItem js/localStorage (str live-prefix k)) "") (tab-id))
         (.removeItem js/localStorage (str live-prefix k)))
       (catch :default _ nil))
  nil)

(defonce ^:private heartbeat (atom nil))

(defn start-heartbeat!
  "Once per page: renews this tab's live mark every 20 s, and clears it when the page is closed.
   Returns nil."
  []
  (when-not @heartbeat
    (reset! heartbeat (js/setInterval #(some-> (tab-key) mark-live!) 20000))
    (.addEventListener js/window "pagehide" #(some-> (tab-key) unmark-live!)))
  nil)

(defn draft-keys
  "The draft keys stored in this browser."
  []
  (try (into [] (comp (filter #(s/starts-with? % prefix)) (map #(subs % (count prefix))))
             (js/Object.keys js/localStorage))
       (catch :default _ [])))

(defn remove-draft!
  "Deletes the draft stored under draft key `k`. Returns nil."
  [k]
  (try (.removeItem js/localStorage (storage-key k)) (catch :default _ nil))
  nil)

(defn latest
  "Of `touched`, a map of draft key -> last edit time, the most recently edited key that `live?`
   does not reject, or nil."
  [touched live?]
  (->> touched
       (remove (fn [[k _]] (live? k)))
       (sort-by val >)
       ffirst))
