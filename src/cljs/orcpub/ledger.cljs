(ns orcpub.ledger
  "Script for the character data page (`orcpub.ledger-page`): reads a saved character or this
   browser's draft and lists its stored picks, with none of the app loaded. Plain DOM, so a
   character that breaks the app cannot break this page. character-rescue.md"
  (:require [cljs.reader :as reader]
            [orcpub.dnd.e5.ledger :as ledger]))

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

(defn- link
  "An anchor to `href` reading `text`."
  [href text]
  (let [a (el "a" (str "color:" (:accent colors)) text)]
    (set! (.-href a) href)
    a))

(defn- stored
  "The localStorage value at `k`, or nil when there is none or storage is blocked."
  [k]
  (try (.getItem js/localStorage k) (catch :default _ nil)))

(defn- login
  "The app's stored login ({:user-data {:username :email} :token}), or nil."
  []
  (try (some-> (stored "user") reader/read-string) (catch :default _ nil)))

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

(defn- row-node
  "One table row for ledger `row`."
  [{:keys [depth section choice key value below]}]
  (el "tr" nil
      (cell section depth)
      (cell choice)
      (cell (el "code" (str "font-size:12px;color:" (:muted colors)) (str key)))
      (cell (when value (el "code" (str "font-size:12px;color:" (:muted colors)) value)))
      (cell (when (pos? below) (str below " below")))))

(defn- table-view
  "The rows of `character` under `heading`, with a copy for support labelled `support-heading`."
  [root heading character support-heading]
  (let [rows (ledger/rows character)
        head (fn [t] (el "th" (str "text-align:left;padding:6px 10px;color:" (:muted colors)) t))]
    (show! root
           (el "h2" "margin:0 0 4px;font-size:18px" heading)
           (message (str (count rows) " stored choices"))
           (button "Copy for support" #(copy! (ledger/support-text support-heading rows) %))
           (el "div" "overflow-x:auto;margin-top:8px"
               (el "table" "border-collapse:collapse;width:100%;font-size:14px"
                   (el "thead" nil (el "tr" nil (head "Section") (head "Choice") (head "Stored key")
                                       (head "Value") (head "")))
                   (apply el "tbody" nil (map row-node rows)))))))

(defn- open-character
  "Show `text` read as a character: the table, or the raw view when it cannot be read.
   `saved-id` is the saved character's id, nil for the browser draft."
  [root text saved-id]
  (let [{:keys [character error]} (ledger/read-character text)
        filename (str "character-" (or saved-id "draft") ".edn")]
    (cond
      error (raw-view root error text filename)

      (and saved-id (not (ledger/owner? character (login))))
      (show! root (message "Only the owner can open this.")
             (when-not (login) (message (link "/pages/login-page" "Log in")))
             (message (link (str "/pages/dnd/5e/characters/" saved-id) "Back to the character")))

      :else
      (let [nm (ledger/character-name character)
            heading (or nm (if saved-id "Unnamed character" "This browser's draft"))]
        (table-view root heading character
                    (str (if saved-id (str "Character " saved-id) "Browser draft")
                         (when nm (str " (" nm ")"))))))))

(defn- load-saved!
  "Fetch saved character `id` and show it."
  [root id]
  (-> (js/fetch (str "/dnd/5e/characters/" id) #js {:headers #js {"Accept" "application/edn"}})
      (.then (fn [r] (if (.-ok r)
                       (.then (.text r) #(open-character root % id))
                       (show! root (message "There is no saved character with this number.")))))
      (.catch #(show! root (message "The character could not be fetched. Check the connection and reload.")))))

(defn- load-draft!
  "Show the character this browser is holding as its draft."
  [root]
  (if-let [text (stored "character")]
    (open-character root text nil)
    (show! root (message "This browser holds no character draft."))))

(defn ^:export init
  "Fill the page's #ledger element from its `data-mode` (\"saved\" or \"draft\") and `data-id`."
  []
  (when-let [root (.getElementById js/document "ledger")]
    (if (= "saved" (.. root -dataset -mode))
      (load-saved! root (.. root -dataset -id))
      (load-draft! root))))

(init)
