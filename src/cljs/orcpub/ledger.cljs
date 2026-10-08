(ns orcpub.ledger
  "Script for the character data page (`orcpub.ledger-page`): reads a saved character or this
   browser's draft and lists its stored picks, with none of the app loaded. Plain DOM, so a
   character that breaks the app cannot break this page. character-rescue.md"
  (:require [cljs.reader :as reader]
            [clojure.string]
            [cognitect.transit :as transit]
            [orcpub.dnd.e5.ledger :as ledger]
            [orcpub.dnd.e5.picks :as picks]))

(def ^:private colors
  {:bg "#1f2533" :panel "#2c3445" :text "#e8ebf0" :muted "#aab3c2" :line "#3d4659" :accent "#f0a100"})

(defn- el
  "A new DOM element of `tag` with `style` (a CSS string or nil) and `children`, each a string
   or a node; nil children are skipped."
  [tag style & children]
  (let [e (.createElement js/document tag)]
    (when style (set! (.. e -style -cssText) style))
    (doseq [c children :when (some? c)]
      (.appendChild e (if (string? c) (.createTextNode js/document c) c)))
    e))

(defn- button
  "A button labelled `text` that calls `on-click` with the button."
  [text on-click]
  (let [b (el "button" (str "font:inherit;font-weight:600;color:#080A0D;background:" (:accent colors)
                            ";border:0;border-radius:3px;padding:8px 14px;cursor:pointer;margin:4px 8px 4px 0")
              text)]
    (.addEventListener b "click" #(on-click b))
    b))

(defn- small-button
  "A quiet button labelled `text` that calls `on-click` with the button."
  [text on-click]
  (let [b (el "button" (str "font:inherit;font-size:13px;color:" (:text colors) ";background:transparent;"
                            "border:1px solid " (:line colors) ";border-radius:3px;padding:3px 10px;cursor:pointer")
              text)]
    (.addEventListener b "click" #(on-click b))
    b))

(defn- link
  "An anchor to `href` reading `text`."
  [href text]
  (let [a (el "a" (str "color:" (:accent colors)) text)]
    (set! (.-href a) href)
    a))

(defn- storage-item
  "The localStorage value at `k`, or nil when there is none or storage is blocked."
  [k]
  (try (.getItem js/localStorage k) (catch :default _ nil)))

(defn- login
  "The app's stored login ({:user-data {:username :email} :token}), or nil."
  []
  (try (some-> (storage-item "user") reader/read-string) (catch :default _ nil)))

(defn- show!
  "Replace everything in `root` with `nodes`."
  [root & nodes]
  (set! (.-textContent root) "")
  (doseq [n nodes :when n] (.appendChild root n)))

(defn- download!
  "Offer `text` to the player as a file named `filename`."
  [text filename]
  (let [url (.createObjectURL js/URL (js/Blob. #js [text] #js {:type "text/plain;charset=utf-8"}))
        a (el "a" nil)]
    (set! (.-href a) url)
    (set! (.-download a) filename)
    (.appendChild (.-body js/document) a)
    (.click a)
    (.removeChild (.-body js/document) a)
    (js/setTimeout #(.revokeObjectURL js/URL url) 1000)))

(defn- copy!
  "Put `text` on the clipboard and say so on button `b`."
  [text b]
  (-> (.writeText (.-clipboard js/navigator) text)
      (.then #(set! (.-textContent b) "Copied"))
      (.catch #(set! (.-textContent b) "Copy failed"))))

(defn- message
  "A short paragraph of `parts` (strings or nodes)."
  [& parts]
  (apply el "p" (str "color:" (:text colors) ";line-height:1.5") parts))

(defn- raw-view
  "What the page shows when `text` cannot be read as a character: `error`, the text itself and
   a way to download it, so nothing is lost."
  [root error text filename]
  (show! root
         (message error)
         (button "Download the data" #(download! text filename))
         (el "pre" (str "white-space:pre-wrap;word-break:break-all;font-size:12px;color:" (:muted colors)
                        ";background:" (:panel colors) ";padding:10px;border-radius:3px;max-height:60vh;overflow:auto")
             text)))

(defn- cell
  "A table cell holding `content`, padded `indent` levels."
  [content & [indent]]
  (el "td" (str "padding:6px 10px;border-top:1px solid " (:line colors) ";vertical-align:top;"
                (when indent (str "padding-left:" (+ 10 (* 18 indent)) "px")))
      content))

(defonce ^:private state
  ;; The open character: {:root :stored :character :pending :saved-id :note :conflict}. `:stored`
  ;; is the map as read from storage, `:pending` the `picks/remove-at` records not saved yet,
  ;; `:conflict` the newer text storage held when a save found it changed.
  (atom nil))

(declare render! load-saved! load-draft!)

(defn- remove!
  "Take the pick at `address` (its row is `row`) out of the open character, unsaved, after the
   player confirms `ledger/warning` when there is one."
  [address row]
  (let [{:keys [character removed]} (picks/remove-at (:character @state) address)
        caution (ledger/warning row)]
    (when (and removed (or (nil? caution) (js/confirm caution)))
      (swap! state #(-> % (assoc :character character :note nil)
                        (update :pending conj (assoc removed :line (:line row) :below (:below row)))))
      (render!))))

(defn- undo!
  "Put pending removal `i` back, or say what has to come back first."
  [i]
  (let [{:keys [character pending]} @state
        back (picks/put-at character (nth pending i))]
    (if back
      (swap! state assoc :character back :note nil
             :pending (vec (concat (subvec pending 0 i) (subvec pending (inc i)))))
      (swap! state assoc :note "Put back the choice it sits under first."))
    (render!)))

(defn- saved!
  "After a save: show `note` over what storage now holds."
  [note]
  (let [{:keys [root saved-id]} @state]
    (if saved-id (load-saved! root saved-id note) (load-draft! root note))))

(defn- write!
  "Write the open character back: to the server through the app's own save route, or to this
   browser's draft. Keeps everything the page did not change."
  []
  (let [{:keys [stored character saved-id]} @state
        data (ledger/save-data stored character)]
    (if-not saved-id
      (if (try (.setItem js/localStorage "character" (str data)) true (catch :default _ false))
        (saved! "Saved.")
        (do (swap! state assoc :note "This browser would not save it.") (render!)))
      (-> (js/fetch "/dnd/5e/characters"
                    #js {:method "POST"
                         :headers #js {"Content-Type" "application/transit+json"
                                       "Accept" "application/edn"
                                       "Authorization" (str "Token " (:token (login)))}
                         :body (transit/write (transit/writer :json) data)})
          (.then (fn [r]
                   (if (.-ok r)
                     (.then (.text r)
                            (fn [text]
                              (let [new-id (str (:db/id (ledger/read-stored text)))]
                                (if (and (seq new-id) (not= new-id (str saved-id)))
                                  (set! (.-location js/window) (str "/pages/dnd/5e/characters/" new-id "/data"))
                                  (saved! "Saved.")))))
                     (do (swap! state assoc :note (if (= 401 (.-status r))
                                                    "Only the owner can save this. Log in again and retry."
                                                    "The server refused this change."))
                         (render!)))))
          (.catch (fn [_] (swap! state assoc :note "Not saved: check the connection and retry.") (render!)))))))

(defn- current-text
  "Call `f` with the character's text as storage holds it now, or with nil when it cannot be read."
  [f]
  (let [{:keys [saved-id]} @state]
    (if-not saved-id
      (f (storage-item "character"))
      (-> (js/fetch (str "/dnd/5e/characters/" saved-id) #js {:headers #js {"Accept" "application/edn"}})
          (.then (fn [r] (if (.-ok r) (.then (.text r) f) (f nil))))
          (.catch (fn [_] (f nil)))))))

(defn- save!
  "Save, but only when storage still holds what the page opened: otherwise say so and offer to
   reload with the pending removals kept."
  []
  (current-text
   (fn [text]
     (if (and text (ledger/same-stored? (:stored @state) (ledger/read-stored text)))
       (write!)
       (do (swap! state assoc :conflict text
                  :note (if text "This character changed since you opened this page." "Not saved: the character could not be read again."))
           (render!))))))

(defn- reload-keeping!
  "Open the character as storage holds it now, with the pending removals applied again; name any
   that no longer apply."
  []
  (let [{:keys [conflict pending]} @state
        {fresh :character} (ledger/read-character conflict)
        {:keys [character lost] kept :pending} (ledger/reapply fresh pending)]
    (swap! state assoc :stored (ledger/read-stored conflict) :character character :pending kept :conflict nil
           :note (if (seq lost)
                   (str "Reloaded. No longer stored: " (clojure.string/join ", " lost) ".")
                   "Reloaded with your removals. Check, then save."))
    (render!)))

(defn- row-node
  "One table row for ledger `row`, with its Remove button."
  [{:keys [address depth section choice key value below] :as row}]
  (el "tr" nil
      (cell section depth)
      (cell choice)
      (cell (el "code" (str "font-size:12px;color:" (:muted colors)) (str key)))
      (cell (when value (el "code" (str "font-size:12px;color:" (:muted colors)) value)))
      (cell (when (pos? below) (str below " below")))
      (cell (small-button (if (pos? below) (str "Remove with " below " below") "Remove")
                          (fn [_] (remove! address row))))))

(defn- pending-node
  "The removals not saved yet, each with Undo, and the Save button; nil when there are none."
  [pending]
  (when (seq pending)
    (el "div" (str "background:" (:panel colors) ";padding:10px 12px;border-radius:3px;margin:8px 0")
        (el "div" "font-weight:600;margin-bottom:6px" (str "Not saved yet: " (count pending) " removed"))
        (apply el "div" nil
               (map-indexed (fn [i {:keys [line below]}]
                              (el "div" "margin:4px 0"
                                  (small-button "Undo" (fn [_] (undo! i)))
                                  (str " " line (when (pos? below) (str ", with " below " below")))))
                            pending))
        (if (:conflict @state)
          (button "Reload and keep my removals" (fn [_] (reload-keeping!)))
          (button "Save" (fn [_] (save!))))
        (el "span" (str "color:" (:muted colors)) "Close this character in other tabs before saving."))))

(defn- render!
  "Draw the open character from `state`."
  []
  (let [{:keys [root character pending note heading support-heading]} @state
        rows (ledger/rows character)
        head (fn [t] (el "th" (str "text-align:left;padding:6px 10px;color:" (:muted colors)) t))]
    (show! root
           (el "h2" "margin:0 0 4px;font-size:18px" heading)
           (message (str (count rows) " stored choices"))
           (when note (el "p" (str "color:" (:accent colors) ";font-weight:600") note))
           (pending-node pending)
           (button "Copy for support" #(copy! (ledger/support-text support-heading rows) %))
           (el "div" "overflow-x:auto;margin-top:8px"
               (el "table" "border-collapse:collapse;width:100%;font-size:14px"
                   (el "thead" nil (el "tr" nil (head "Section") (head "Choice") (head "Stored key")
                                       (head "Value") (head "") (head "")))
                   (apply el "tbody" nil (map row-node rows)))))))

(defn- open-character
  "Show `text` read as a character: the table, or the raw view when it cannot be read.
   `saved-id` is the saved character's id, nil for the browser draft; `note` is shown on top."
  [root text saved-id note]
  (let [{:keys [character error]} (ledger/read-character text)
        filename (str "character-" (or saved-id "draft") ".edn")]
    (cond
      error (raw-view root error text filename)

      (and saved-id (not (ledger/owner? character (login))))
      (show! root (message "Only the owner can open this.")
             (when-not (login) (message (link "/pages/login-page" "Log in")))
             (message (link (str "/pages/dnd/5e/characters/" saved-id) "Back to the character")))

      :else
      (let [nm (ledger/character-name character)]
        (reset! state {:root root :stored (ledger/read-stored text) :character character
                       :pending [] :saved-id saved-id :note note
                       :heading (or nm (if saved-id "Unnamed character" "This browser's draft"))
                       :support-heading (str (if saved-id (str "Character " saved-id) "Browser draft")
                                             (when nm (str " (" nm ")")))})
        (render!)))))

(defn- load-saved!
  "Fetch saved character `id` and show it, with `note` on top when given."
  [root id & [note]]
  (-> (js/fetch (str "/dnd/5e/characters/" id) #js {:headers #js {"Accept" "application/edn"}})
      (.then (fn [r] (if (.-ok r)
                       (.then (.text r) #(open-character root % id note))
                       (show! root (message "There is no saved character with this number.")))))
      (.catch #(show! root (message "The character could not be fetched. Check the connection and reload.")))))

(defn- load-draft!
  "Show the character this browser is holding as its draft, with `note` on top when given."
  [root & [note]]
  (if-let [text (storage-item "character")]
    (open-character root text nil note)
    (show! root (message "This browser holds no character draft."))))

(defn ^:export init
  "Fill the page's #ledger element from its `data-mode` (\"saved\" or \"draft\") and `data-id`."
  []
  (when-let [root (.getElementById js/document "ledger")]
    (if (= "saved" (.. root -dataset -mode))
      (load-saved! root (.. root -dataset -id))
      (load-draft! root))))

(init)
