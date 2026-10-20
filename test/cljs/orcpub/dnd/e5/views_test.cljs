(ns orcpub.dnd.e5.views-test
  "Tests for the pure helpers behind the character-display fail-soft logic.

   The UI components (error-boundary, render-guard, feature-render-error,
   character-health-warning) are React/Reagent class components and are not
   meaningfully unit-testable without a DOM + a mounted re-frame app, so they
   are not covered here. The one piece of pure, side-effect-free logic that
   drives the diagnostics — blank-feature-name? — is tested directly."
  (:require [cljs.test :refer-macros [deftest testing is async]]
            [cljs.reader :as reader]
            [re-frame.core :as rf]
            [orcpub.pdf-spec :as pdf-spec]
            [orcpub.dnd.e5.character :as char]
            [orcpub.dnd.e5.portrait :as portrait]
            [orcpub.dnd.e5.portrait-assets :as pa]
            [orcpub.artist-links]
            [orcpub.dnd.e5.views :as views]
            [orcpub.dnd.e5.views.notifications :as notifications]
            [orcpub.dnd.e5.views.conflict-resolution :as conflict-resolution]))

(deftest blank-link-rows-count-against-the-limit
  (let [rows (fn [urls] (mapv (fn [u] {:url u}) urls))
        max-links orcpub.artist-links/max-links]
    (is (views/room-for-a-link? (rows (repeat (dec max-links) "https://x.test"))))
    (is (not (views/room-for-a-link? (rows (repeat max-links ""))))
        "a full set of blank rows leaves no room for another")))

(deftest a-nameless-item-reads-as-its-placeholder
  (is (= {:title "Unnamed Spell" :subtitle ":no-name"}
         (conflict-resolution/item-heading :orcpub.dnd.e5/spells nil :no-name))
      "the dialog showed a bare :no-name")
  (is (= "Unnamed Monster"
         (:title (conflict-resolution/item-heading :orcpub.dnd.e5/monsters "  " :x))))
  (is (= {:title "Unnamed Item" :subtitle nil}
         (conflict-resolution/item-heading :some/new-type nil nil)))
  (is (= {:title "Fire Bolt"}
         (conflict-resolution/item-heading :orcpub.dnd.e5/spells "Fire Bolt" :fire-bolt))))

(deftest blank-feature-name?-test
  (testing "nil names are blank"
    (is (true? (boolean (views/blank-feature-name? nil)))))
  (testing "empty string is blank"
    (is (true? (boolean (views/blank-feature-name? "")))))
  (testing "whitespace-only string is blank"
    (is (true? (boolean (views/blank-feature-name? "   "))))
    (is (true? (boolean (views/blank-feature-name? "\t\n")))))
  (testing "a real name is not blank"
    (is (false? (boolean (views/blank-feature-name? "Name")))))
  (testing "a name padded with whitespace is not blank"
    (is (false? (boolean (views/blank-feature-name? "  Rage  "))))))

;; ---------------------------------------------------------------------------
;; Banner rendering
;;
;; `message` is a plain function returning hiccup, so what it does to its input
;; IS testable without a DOM — which is the part that broke. Collapsing blank
;; lines by running `str` over the input rendered hiccup messages as their own
;; source text, and the save banner's export link stopped being a link at all.
;; Nothing caught it because every message in the suite was a string.
;;
;; Asserted through the body slot rather than by walking indices from the top:
;; the tone icon and close button have moved once already, and a test that
;; breaks when they move is testing the wrong thing.
;; ---------------------------------------------------------------------------

(defn- message-body
  "The child of the banner's [:div.message-body …] node."
  [msg]
  (-> (notifications/message :warning msg identity) (nth 2) (nth 3) (nth 1)))

(deftest hiccup-messages-are-not-stringified
  (testing "a hiccup message reaches the body as hiccup, not as printed source"
    (let [msg [:div "Saved. " [:span.pointer.underline {:on-click identity} "Export"]]]
      (is (vector? (message-body msg))
          "stringifying renders markup as text and kills every control in it")
      (is (= msg (message-body msg)))))

  (testing "a clickable child keeps the handler that makes it do anything"
    (let [handler (fn [_] :clicked)
          msg [:div [:span.pointer {:on-click handler} "Export"]]
          link (nth (message-body msg) 1)]
      (is (vector? link))
      (is (= handler (:on-click (second link)))))))

(deftest string-messages-become-a-title-and-details
  (testing "the first line titles the banner, the rest are details"
    (let [body (message-body "one\n\ntwo\n\n\nthree")
          title (nth body 1)
          details (vec (nth body 2))]
      (is (= [:div.message-title "one"] title))
      (is (= ["two" "three"] (mapv #(nth % 1) details)))))

  (testing "a single-line string is all title, with no empty detail rows"
    (let [body (message-body "just this")]
      (is (= [:div.message-title "just this"] (nth body 1)))
      (is (empty? (vec (nth body 2)))))))

;; ---- PDF export: nothing optional may stop it ----

(defn- with-download-form
  "Runs `f` with the page's download form in the DOM; its submits are recorded, not sent. Returns
   an atom of the specs submitted, each read back from the form's field."
  [f]
  (let [form (.createElement js/document "form")
        field (.createElement js/document "input")
        sent (atom [])]
    (set! (.-id form) "download-form")
    (set! (.-id field) "fields-input")
    (.appendChild form field)
    (.appendChild (.-body js/document) form)
    (set! (.-submit form) #(swap! sent conj (reader/read-string (.-value field))))
    (let [open (.-open js/window)]
      (set! (.-open js/window) (constantly nil))
      (try (f sent) (finally (.remove form) (set! (.-open js/window) open))))
    sent))

(def ^:private undrawable {:layers {:head {:asset/id :not-on-this-site}}})

(defn- export!
  "Presses Create PDF for an empty character, as the export button's handler does. Returns nil."
  []
  ((views/export-pdf {} 1 {}) nil))

(deftest a-portrait-this-site-cannot-draw-does-not-stop-the-export
  (with-redefs [pdf-spec/make-spec (constantly {:name "Bree"})
                char/portrait (constantly undrawable)]
    (let [sent (with-download-form (fn [_] (export!)))]
      (is (= 1 (count @sent)) "the PDF is sent at once")
      (is (nil? (:portrait-png (first @sent))) "without a portrait"))))

(deftest a-bake-that-throws-does-not-stop-the-export
  (with-redefs [pdf-spec/make-spec (constantly {:name "Bree"})
                char/portrait (constantly {:layers {:head {}}})
                pa/drawable? (constantly true)
                portrait/baked-png (constantly nil)
                portrait/bake-ahead! (fn [_] (throw (js/Error. "canvas refused")))]
    (let [sent (with-download-form (fn [_] (export!)))]
      (is (= 1 (count @sent)) "the PDF is sent without the portrait"))))

(deftest a-bake-that-never-finishes-does-not-hold-the-export
  (async done
    (let [sent (atom [])
          open (.-open js/window)
          form (.createElement js/document "form")
          field (.createElement js/document "input")]
      (set! (.-id form) "download-form")
      (set! (.-id field) "fields-input")
      (.appendChild form field)
      (.appendChild (.-body js/document) form)
      (set! (.-submit form) #(swap! sent conj (.-value field)))
      (set! (.-open js/window) (constantly nil))
      (with-redefs [pdf-spec/make-spec (constantly {:name "Bree"})
                    char/portrait (constantly {:layers {:head {}}})
                    pa/drawable? (constantly true)
                    portrait/baked-png (constantly nil)
                    portrait/bake-ahead! (fn [_] (js/Promise. (fn [_ _])))
                    views/portrait-wait-ms 30]
        (export!))
      (js/setTimeout (fn []
                       (is (= 1 (count @sent)) "after the wait the PDF goes without the portrait")
                       (.remove form)
                       (set! (.-open js/window) open)
                       (done))
                     300))))

(deftest a-character-the-pdf-cannot-be-made-for-says-so
  (let [shown (atom nil)]
    (with-redefs [pdf-spec/make-spec (fn [& _] (throw (js/Error. "no class")))
                  rf/dispatch #(reset! shown %)]
      (let [sent (with-download-form (fn [_] (export!)))]
        (is (empty? @sent))
        (is (= :show-error-message (first @shown)) "the player is told, not left with nothing")))))

;; Imported homebrew is untrusted. The ability-increase and save forms, their notes and the feat
;; section are drawn with every field holding each malformed shape; none may throw.
(def ^:private malformed-grants
  [nil 42 "x" true {} {:a 1} #{} #{:str} #{42} [] [nil] [42] ["x"] [{}] [[]] [[nil]] [[1]]
   [[1 nil]] [[1 nil :save]] [[1 :foo]] [[1 #{}]] [[1 []]] [[1 [nil]]] [[1 #{2}]] [["1" :str]]
   [[nil :str]] [[1 :str :save :extra]] [[0 :any]] [[-1 :any]] [[1.5 :any]] [[4 :mental]]
   [[1 {:a 1}]] [[1 "str"]] [[1 #{:wis :con}]]])

(deftest grant-forms-draw-any-imported-shape
  (let [draw (fn [f item] (try (dorun (tree-seq coll? seq (f item))) nil
                               (catch :default e (.-message e))))
        forms {"ability-increase-choices" #(views/ability-increase-choices % identity)
               "save-proficiency-choices" #(views/save-proficiency-choices % identity)
               "ability-save-notes"       views/ability-save-notes
               "feat-ability-score-section" views/feat-ability-score-section}
        failures (for [field [:ability-increases :save-proficiencies]
                       v malformed-grants
                       [label f] forms
                       :let [msg (draw f {:name "Bad" field v})]
                       :when msg]
                   [label field v msg])]
    (is (empty? failures) (pr-str (take 5 failures)))))
