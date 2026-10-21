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

(defn- with-class
  "`node` with CSS class `cls` (the page's stylesheet, `orcpub.ledger-page`) set; returns `node`."
  [node cls]
  (set! (.-className node) cls)
  node)

(defn- button
  "A primary (orange) button labelled `text` that calls `on-click` with the button."
  [text on-click]
  (let [b (with-class (el "button" nil text) "dv-btn primary")]
    (.addEventListener b "click" #(on-click b))
    b))

(defn- small-button
  "A quiet outlined button labelled `text` that calls `on-click` with the button."
  [text on-click]
  (let [b (with-class (el "button" nil text) "dv-btn")]
    (.addEventListener b "click" #(on-click b))
    b))

(defn- ask!
  "A popup over the page: `title`, the paragraphs `lines` (strings or nodes; a vector
   `[:muted text]` is set small and muted), Cancel and a primary `confirm` button that closes it
   and calls `on-confirm`. Escape and Cancel close it without acting."
  [title lines confirm on-confirm]
  (let [veil (with-class (el "div" nil) "dv-veil")
        close! #(when (.-parentNode veil) (.removeChild (.-parentNode veil) veil))
        go (button confirm (fn [_] (close!) (on-confirm)))
        dialog (with-class (apply el "div" nil (el "h3" nil title)
                                  (concat (for [l lines]
                                            (if (vector? l)
                                              (with-class (el "p" nil (second l)) "dv-muted")
                                              (el "p" nil l)))
                                          [(with-class (el "div" nil (small-button "Cancel" (fn [_] (close!))) go)
                                             "dv-actions")]))
                 "dv-dialog")]
    (.setAttribute dialog "role" "dialog")
    (.setAttribute dialog "aria-modal" "true")
    (.appendChild veil dialog)
    (.addEventListener veil "keydown" (fn [e] (when (= "Escape" (.-key e)) (close!))))
    (.appendChild (.-body js/document) veil)
    (.focus go)))

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
         (with-class (el "div" nil (button "Download the data" #(download! text filename))) "dv-tools")
         (with-class (el "pre" nil text) "dv-raw")))

(defonce ^:private state
  ;; The open character: {:root :stored :base :character :pending :saved-id :note :conflict
  ;; :show-raw}. `:stored` is the map as read from storage and `:base` the character read from it,
  ;; which the rows are drawn from; `:character` is `:base` with the `:pending` `picks/remove-at`
  ;; records taken out; `:conflict` the newer text storage held when a save found it changed.
  (atom nil))

(declare render! load-saved! load-draft!)

(defn- remove!
  "Take the pick at `address` (its row is `row`) out of the open character, unsaved, after the
   player confirms `ledger/warning` in a popup when there is one."
  [address row]
  (let [{:keys [character removed]} (picks/remove-at (:character @state) address)
        caution (ledger/warning row)
        apply! #(do (swap! state (fn [st] (-> st (assoc :character character :note nil)
                                              (update :pending conj (assoc removed :line (:line row) :below (:below row)
                                                                                    :choice (:choice row) :section (:section row))))))
                    (render!))]
    (when removed
      (if caution
        (let [[question & more] (clojure.string/split caution #"(?<=\?) ")]
          (ask! question (concat more [[:muted "Nothing is saved until you press Save."]]) "Remove" apply!))
        (apply!)))))

(defn- undo!
  "Put the pending removal at `address` back, or say what has to come back first."
  [address]
  (let [{:keys [character pending]} @state
        i (first (keep-indexed (fn [i p] (when (= address (:address p)) i)) pending))
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
                   (cond
                     (= 409 (.-status r))
                     (.then (.text r)
                            (fn [text]
                              (let [current (ledger/refused-current text)]
                                (swap! state assoc :conflict current
                                       :note (if current
                                               "This character changed since you opened this page."
                                               "Not saved: the character could not be read again."))
                                (render!))))

                     (.-ok r)
                     (.then (.text r)
                            (fn [text]
                              (let [new-id (str (:db/id (ledger/read-stored text)))]
                                (if (and (seq new-id) (not= new-id (str saved-id)))
                                  (set! (.-location js/window) (str "/pages/dnd/5e/characters/" new-id "/data"))
                                  (saved! "Saved.")))))
                     :else
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

(defn- confirm-save!
  "Ask in a popup before saving, naming each pending removal and what goes with it; Save on yes."
  []
  (let [{:keys [pending saved-id]} @state
        n (reduce + (map #(inc (:below % 0)) pending))]
    (ask! (str "Remove " n (if (= 1 n) " choice" " choices") " from this " (if saved-id "character" "draft") "?")
          (concat (for [{:keys [choice section below]} pending]
                    (el "span" nil (el "b" nil choice)
                        (if (pos? below) (str ", and the " below (if (= 1 below) " choice" " choices") " under it")
                            (str " (" section ")"))))
                  [[:muted (if saved-id
                             "This changes the saved character."
                             "This changes this browser's draft.")]])
          "Remove and save" save!)))

(defn- reload-keeping!
  "Open the character as storage holds it now, with the pending removals applied again; name any
   that no longer apply."
  []
  (let [{:keys [conflict pending]} @state
        {fresh :character} (ledger/read-character conflict)
        {:keys [character lost] kept :pending} (ledger/reapply fresh pending)]
    (swap! state assoc :stored (ledger/read-stored conflict) :base fresh :character character :pending kept :conflict nil
           :note (if (seq lost)
                   (str "Reloaded. No longer stored: " (clojure.string/join ", " lost) ".")
                   "Reloaded with your removals. Check, then save."))
    (render!)))

(defn- row-node
  "One line for ledger `row`: its label, the stored key and readable value under it, and Remove,
   or Undo when it is a removal not saved yet. A row under such a removal is struck through with
   no button. `place` is :top (the card's own line), :kid or :inset; :top and :inset name the
   section too."
  [{:keys [address choice section key readable below] :as row} removed place]
  (let [mark (ledger/removal-mark address removed)
        top? (= :top place)
        label (if (= :kid place) choice (str section ": " choice))
        node (with-class
               (el "div" nil
                   (with-class
                     (el "div" nil
                         (with-class (el "div" nil label) "dv-label")
                         (when (= :removed mark) (with-class (el "div" nil "Removed, not saved") "dv-gone"))
                         (with-class (el "div" nil (el "code" nil (str key)) (when readable (str " · " readable))) "dv-meta"))
                     "dv-text")
                   (case mark
                     :removed (small-button "Undo" (fn [_] (undo! address)))
                     :under nil
                     (small-button (if (pos? below) (str "Remove (+" below ")") "Remove") (fn [_] (remove! address row)))))
               (str "dv-row" (when top? " dv-top") (when mark " dv-removed")))]
    (.setAttribute node "data-row" choice)
    (.setAttribute node "data-section" section)
    (when mark (.setAttribute node "data-removed" (name mark)))
    node))

(defn- card-node
  "A card for top-level row `top` and the rows under it (`kids`, parents first): a heading per
   run of one section, and anything deeper than that in an inset under its parent."
  [top kids removed]
  (let [nodes (loop [[k & more :as ks] kids last-section nil acc []]
                (cond
                  (empty? ks) acc
                  (= 1 (:depth k))
                  (let [[inner rest*] (split-with #(> (:depth %) 1) more)]
                    (recur rest* (:section k)
                           (cond-> acc
                             (not= (:section k) last-section) (conj (with-class (el "div" nil (:section k)) "dv-sub"))
                             true (conj (with-class (el "div" nil (row-node k removed :kid)) "dv-kid"))
                             (seq inner) (conj (with-class
                                                 (apply el "div" nil
                                                        (for [n inner]
                                                          (let [line (row-node n removed :inset)]
                                                            (set! (.. line -style -marginLeft) (str (* 16 (- (:depth n) 2)) "px"))
                                                            line)))
                                                 "dv-inset")))))
                  :else (recur more last-section (conj acc (with-class (el "div" nil (row-node k removed :inset)) "dv-kid")))))]
    (with-class (apply el "section" nil (row-node top removed :top) nodes) "dv-card")))

(defn- notice-node
  "The save notice: how many choices are removed and not saved, with Save, or with the reload
   when a save found the character changed; nil when nothing is pending."
  [removed-count]
  (let [{:keys [pending conflict]} @state]
    (when (seq pending)
      (with-class
        (el "div" nil
            (with-class
              (el "div" nil
                  (el "span" nil (el "b" nil (str removed-count " removed")) ", not saved")
                  (if conflict
                    (button "Reload and keep my removals" (fn [_] (reload-keeping!)))
                    (button "Save" (fn [_] (confirm-save!)))))
              "dv-notice-box"))
        "dv-notice"))))

(defn- render!
  "Draw the open character from `state`."
  []
  (let [{:keys [root base pending note heading support-heading stored show-raw]} @state
        rows (ledger/rows base)
        removed (mapv :address pending)
        removed-count (count (filter #(ledger/removal-mark (:address %) removed) rows))
        groups (reduce (fn [gs r] (if (zero? (:depth r)) (conj gs [r []]) (update-in gs [(dec (count gs)) 1] conj r)))
                       [] rows)]
    (show! root
           (with-class
             (apply el "div" nil
                    (with-class (el "h2" nil heading) "dv-name")
                    (with-class (el "p" nil (str (count rows) " stored choices")) "dv-count")
                    (when note (with-class (el "p" nil note) "dv-note"))
                    (with-class (el "div" nil
                                    (small-button "Copy for support" #(copy! (ledger/support-text support-heading (ledger/rows (:character @state))) %))
                                    (small-button (if show-raw "Hide stored data" "Show stored data")
                                                  (fn [_] (swap! state update :show-raw not) (render!))))
                      "dv-tools")
                    (when show-raw (with-class (el "pre" nil (binding [*print-namespace-maps* false] (pr-str stored))) "dv-raw"))
                    (notice-node removed-count)
                    (concat (map (fn [[top kids]] (card-node top kids removed)) groups)
                            [(when (seq pending) (with-class (el "div" nil) "dv-spacer"))]))
             "dv"))))

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
        (reset! state {:root root :stored (ledger/read-stored text) :base character :character character
                       :pending [] :saved-id saved-id :note note
                       :heading (or nm (if saved-id "Unnamed character" "This browser's draft"))
                       :support-heading (ledger/support-heading saved-id character)})
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
    (if-let [kept (storage-item "character:corrupt")]
      (raw-view root (str "This browser's draft could not be read, so the app set it aside. "
                          "Download it to keep it, or to send it to support.")
                kept "character-draft-set-aside.edn")
      (show! root (message "This browser holds no character draft.")))))

(defn ^:export init
  "Fill the page's #ledger element from its `data-mode` (\"saved\" or \"draft\") and `data-id`."
  []
  (when-let [root (.getElementById js/document "ledger")]
    (if (= "saved" (.. root -dataset -mode))
      (load-saved! root (.. root -dataset -id))
      (load-draft! root))))

(init)
