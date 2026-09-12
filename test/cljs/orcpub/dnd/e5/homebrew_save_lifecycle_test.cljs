(ns orcpub.dnd.e5.homebrew-save-lifecycle-test
  "The homebrew save path, one scenario per thing a person actually does: save, save again,
   rename, come back to a restored draft, edit a saved item, land on a taken key.

   These are CHARACTERIZATION tests — they pin what the app does today, including behaviour we
   have decided is wrong (marked GAP). A deliberate change flips the assertion in the same commit
   that makes it; a change nobody meant to make shows up here instead of in a review.

   Layer: re-frame events through `dispatch-sync`, not the DOM. The save path is event logic, and
   the browser adds nothing to it. The builder FORM is pinned separately by test/e2e/*.js."
  (:require [cljs.test :refer-macros [deftest testing is use-fixtures]]
            [re-frame.core :as rf]
            [re-frame.db :refer [app-db]]
            [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.languages :as langs5e]
            [orcpub.dnd.e5.content-reconciliation :as reconcile]
            ;; Side effect: registers every event handler under test.
            [orcpub.dnd.e5.events]))

(def ^:private SRC "Lifecycle Pak")
(def ^:private ct :orcpub.dnd.e5/languages)

(defn- reset-db! []
  (reset! app-db {:plugins {}}))

;; The save handler returns its work as :dispatch-n, and re-frame's dispatch is ASYNC -- so a
;; bare dispatch-sync returns before the save has been applied. Capture the queue instead and
;; drain it here, which runs the same handlers in the same order, synchronously.
(def ^:private queued (atom []))

(use-fixtures :each
  {:before (fn []
             (reset-db!)
             (reset! queued [])
             (rf/reg-fx :dispatch-n (fn [evs] (swap! queued into (remove nil? evs))))
             (rf/reg-fx :dispatch   (fn [ev] (swap! queued conj ev))))
   :after  (fn []
             (rf/reg-fx :dispatch-n (fn [evs] (doseq [ev evs] (when ev (rf/dispatch ev)))))
             (rf/reg-fx :dispatch   (fn [ev] (rf/dispatch ev))))})

(defn- open!
  "Put `item` in the builder, the way every path into the form does."
  [item]
  (swap! app-db assoc ::langs5e/builder-item item))

(defn- save! []
  (reset! queued [])
  (rf/dispatch-sync [::langs5e/save-language])
  (loop [n 0]                                   ; drain, bounded: this chain is two deep
    (let [evs @queued]
      (when (and (seq evs) (< n 10))
        (reset! queued [])
        (doseq [ev evs] (rf/dispatch-sync ev))
        (recur (inc n))))))

(defn- set-name! [n]
  (swap! app-db assoc-in [::langs5e/builder-item :name] n))

(defn- stored [] (get-in @app-db [:plugins SRC ct]))
(defn- in-builder [] (::langs5e/builder-item @app-db))
(defn- draft [n] {:name n :option-pack SRC})

;; ---------------------------------------------------------------------------
;; Authoring something new
;; ---------------------------------------------------------------------------

(deftest a-new-item-lands-under-a-key-derived-from-its-name
  (open! (draft "Tideward"))
  (save!)
  (is (= #{:tideward} (set (keys (stored)))))
  (is (= "Tideward" (get-in (stored) [:tideward :name]))))

(deftest the-form-keeps-the-key-it-just-wrote
  ;; `save-collision` reads this to tell an edit returning to its own slot from a name landing on
  ;; somebody else's. Without it the SECOND save below is refused as an overwrite of itself.
  (open! (draft "Tideward"))
  (save!)
  (is (= :tideward (:key (in-builder)))))

(deftest saving-the-same-item-twice-is-not-a-collision
  (open! (draft "Tideward"))
  (save!)
  (swap! app-db assoc-in [::langs5e/builder-item :description] "a tide-tongue")
  (save!)
  (is (= #{:tideward} (set (keys (stored)))) "one entry, not two")
  (is (= "a tide-tongue" (get-in (stored) [:tideward :description])) "the second save landed")
  (is (empty? (:builder-field-errors @app-db)) "and nothing was flagged"))

(deftest a-restored-draft-saves-back-into-its-own-slot
  ;; Save, leave, come back: the form is rebuilt from the persisted draft rather than from the
  ;; item in :plugins, so the draft has to carry the key too.
  (open! (draft "Tideward"))
  (save!)
  (let [persisted (in-builder)]
    (reset-db!)
    (swap! app-db assoc :plugins {SRC {ct {:tideward (assoc (draft "Tideward") :key :tideward)}}})
    (open! persisted)
    (save!)
    (is (= #{:tideward} (set (keys (stored)))))))

(deftest editing-a-saved-item-saves-back-into-its-own-slot
  (swap! app-db assoc :plugins {SRC {ct {:tideward (assoc (draft "Tideward") :key :tideward)}}})
  (open! (get-in @app-db [:plugins SRC ct :tideward]))       ; what the edit event hands the form
  (swap! app-db assoc-in [::langs5e/builder-item :description] "edited")
  (save!)
  (is (= #{:tideward} (set (keys (stored)))))
  (is (= "edited" (get-in (stored) [:tideward :description]))))

;; ---------------------------------------------------------------------------
;; Renaming
;; ---------------------------------------------------------------------------

(deftest renaming-moves-the-entry-rather-than-copying-it
  ;; The bug this pins: the old entry used to be left behind, so one item became two and the
  ;; abandoned one stayed in the library, in exports, and in the source list forever.
  (open! (draft "Tideward"))
  (save!)
  (set-name! "Tidewall")
  (save!)
  (is (= #{:tidewall} (set (keys (stored)))) "no orphan under the old key")
  (is (= [:tideward] (get-in (stored) [:tidewall :former-keys])) "and the move is recorded"))

(deftest a-character-on-the-old-key-heals-through-former-key
  (open! (draft "Tideward"))
  (save!)
  (set-name! "Tidewall")
  (save!)
  (let [character {:orcpub.entity/options {:languages [{:orcpub.entity/key :tideward}]}}
        index     (reconcile/former-key-index (:plugins @app-db))
        {:keys [character rewrote]} (reconcile/reconcile-former-keys character index)]
    (is (= {:tideward :tidewall} index))
    (is (= [{:from :tideward :to :tidewall}] rewrote))
    (is (= :tidewall (get-in character [:orcpub.entity/options :languages 0 :orcpub.entity/key])))))

(deftest a-chain-of-renames-heals-from-every-link
  ;; :former-keys, not :former-key: one slot kept only the last hop, so A->B->C healed B and
  ;; stranded anyone still on A.
  (open! (draft "Alpha"))
  (save!)
  (set-name! "Beta")
  (save!)
  (set-name! "Gamma")
  (save!)
  (let [index (reconcile/former-key-index (:plugins @app-db))]
    (is (= #{:gamma} (set (keys (stored)))))
    (is (= [:alpha :beta] (:former-keys (get (stored) :gamma))))
    (is (= {:alpha :gamma :beta :gamma} index) "every former key points at the live one")))

(deftest the-history-is-capped-and-the-original-key-is-never-dropped
  ;; The mint is what a character nobody has opened since then still points at, so overflow comes
  ;; out of the MIDDLE.
  (open! (draft "One"))
  (save!)
  (doseq [n ["Two" "Three" "Four" "Five" "Six"]]
    (set-name! n)
    (save!))
  (let [item (get (stored) :six)]
    (is (= reconcile/former-key-cap (count (:former-keys item))))
    (is (= :one (first (:former-keys item))) "the key it was minted under survives")
    (is (= [:one :three :four :five] (:former-keys item)) "and the middle is what gave way")
    (is (nil? (:former-key item)) "the singular is not written alongside the plural")))

(deftest an-item-saved-under-the-old-singular-key-still-heals
  ;; Everything already in a library carries :former-key. It keeps working, and picks up the
  ;; plural the next time it is renamed.
  (swap! app-db assoc :plugins {SRC {ct {:new-name {:key :new-name :name "New Name"
                                                    :option-pack SRC :former-key :old-name}}}})
  (is (= {:old-name :new-name} (reconcile/former-key-index (:plugins @app-db))))
  (open! (get-in @app-db [:plugins SRC ct :new-name]))
  (set-name! "Newer Name")
  (save!)
  (let [item (get (stored) :newer-name)]
    (is (= [:old-name :new-name] (:former-keys item)) "the old singular is carried into the list")
    (is (nil? (:former-key item)))))

;; ---------------------------------------------------------------------------
;; Landing on a key something else holds
;; ---------------------------------------------------------------------------

(deftest a-new-item-may-not-take-a-key-another-item-in-this-source-holds
  (swap! app-db assoc :plugins {SRC {ct {:tideward (assoc (draft "Tideward") :key :tideward)}}})
  (open! (draft "Tideward"))                                 ; a DIFFERENT item, same name
  (save!)
  (is (= 1 (count (stored))) "the sitting tenant is not replaced")
  (is (= :invalid (:name (:builder-field-errors @app-db))) "and the name field says why"))

(deftest renaming-onto-an-occupied-key-is-refused-and-loses-nothing
  (swap! app-db assoc :plugins {SRC {ct {:tidewall (assoc (draft "Tidewall") :key :tidewall)}}})
  (open! (draft "Tideward"))
  (save!)
  (is (= #{:tideward :tidewall} (set (keys (stored)))))
  (set-name! "Tidewall")                                     ; onto the other item's key
  (save!)
  (is (= #{:tideward :tidewall} (set (keys (stored)))) "both survive")
  (is (= "Tidewall" (get-in (stored) [:tidewall :name])) "the tenant is untouched"))

(deftest CURRENT-a-name-held-by-another-source-is-refused
  ;; DECISION OPEN. `save-collision`'s own docstring says the cross-source case "informs rather
  ;; than blocks" -- both copies survive and the disable hierarchy decides which is live, and
  ;; import deliberately offers "keep both". The caller blocks it anyway. Whichever way that is
  ;; settled, it gets settled HERE first.
  (swap! app-db assoc :plugins {"Someone Else's Pak" {ct {:tideward {:key :tideward
                                                                    :name "Tideward"
                                                                    :option-pack "Someone Else's Pak"}}}})
  (open! (draft "Tideward"))
  (save!)
  (is (nil? (stored)) "nothing saved into this source")
  (is (= :invalid (:name (:builder-field-errors @app-db)))))
