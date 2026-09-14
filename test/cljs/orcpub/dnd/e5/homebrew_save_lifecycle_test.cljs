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
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.dnd.e5.content-reconciliation :as reconcile]
            ;; Side effect: registers every event handler under test.
            [orcpub.dnd.e5.events]
            ;; …and every subscription: the collision the save refuses is the one pinned at the
            ;; bottom of this file.
            [orcpub.dnd.e5.spell-subs]))

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

(deftest renaming-is-a-name-edit-and-does-not-re-address-the-item
  ;; MINTED ONCE (D10). The key is derived at creation and then fixed, so a rename cannot orphan
  ;; the old entry, cannot collide with anything, and cannot strand a character that holds the key.
  (open! (draft "Tideward"))
  (save!)
  (set-name! "Tidewall")
  (save!)
  (is (= #{:tideward} (set (keys (stored)))) "one entry, still at the key it was minted under")
  (is (= "Tidewall" (get-in (stored) [:tideward :name])) "with the new name on it")
  (is (empty? (get-in (stored) [:tideward :former-keys])) "nothing moved, so nothing to record"))

(deftest a-character-keeps-resolving-across-a-rename
  (open! (draft "Tideward"))
  (save!)
  (set-name! "Tidewall")
  (save!)
  (is (contains? (stored) :tideward) "the key a character stored is still the live one")
  (is (= {} (reconcile/former-key-index (:plugins @app-db))) "and there is nothing to heal"))

(deftest a-deliberate-key-change-still-records-the-move
  ;; The rename path that remains: import conflict resolution and the manual relink go through
  ;; rename-key-in-plugin, which records :former-keys so characters rebind on load.
  (let [item  {:key :tideward :name "Tideward" :option-pack SRC}
        moved (reconcile/record-former-key (assoc item :key :tidewall) :tideward)]
    (swap! app-db assoc :plugins {SRC {ct {:tidewall moved}}})
    (let [character {:orcpub.entity/options {:languages [{:orcpub.entity/key :tideward}]}}
          index     (reconcile/former-key-index (:plugins @app-db))
          {:keys [character rewrote]} (reconcile/reconcile-former-keys character index)]
      (is (= {:tideward :tidewall} index))
      (is (= [{:from :tideward :to :tidewall}] rewrote))
      (is (= :tidewall (get-in character [:orcpub.entity/options :languages 0 :orcpub.entity/key]))))))

(defn- moved-through
  "An item carried through a chain of deliberate key changes, oldest first."
  [ks]
  (reduce (fn [item k] (reconcile/record-former-key (assoc item :key k) (:key item)))
          {:key (first ks) :option-pack SRC}
          (rest ks)))

(deftest a-chain-of-key-changes-heals-from-every-link
  ;; :former-keys, not :former-key: one slot kept only the last hop, so A->B->C healed B and
  ;; stranded anyone still on A.
  (let [item (moved-through [:alpha :beta :gamma])]
    (swap! app-db assoc :plugins {SRC {ct {:gamma item}}})
    (is (= [:alpha :beta] (:former-keys item)))
    (is (= {:alpha :gamma :beta :gamma} (reconcile/former-key-index (:plugins @app-db)))
        "every former key points at the live one")))

(deftest the-history-is-capped-and-the-prime-key-is-never-dropped
  (let [item (moved-through [:one :two :three :four :five :six])]
    (is (= reconcile/former-key-cap (count (:former-keys item))))
    (is (= [:one :three :four :five] (:former-keys item)) "entry 0 stays; the middle gives way")
    (is (nil? (:former-key item)) "the singular is not written alongside the plural")))

(deftest an-item-carrying-the-old-singular-key-still-heals
  ;; Everything already in a library carries :former-key. It keeps working, and folds into the
  ;; vector at its next key change.
  (swap! app-db assoc :plugins {SRC {ct {:new-name {:key :new-name :name "New Name"
                                                    :option-pack SRC :former-key :old-name}}}})
  (is (= {:old-name :new-name} (reconcile/former-key-index (:plugins @app-db))))
  (let [next-move (reconcile/record-former-key
                   (assoc (get-in @app-db [:plugins SRC ct :new-name]) :key :newer-name)
                   :new-name)]
    (is (= [:old-name :new-name] (:former-keys next-move)))
    (is (nil? (:former-key next-move)))))

;; ---------------------------------------------------------------------------
;; Landing on a key something else holds
;; ---------------------------------------------------------------------------

(deftest a-new-item-may-not-take-a-key-another-item-in-this-source-holds
  (swap! app-db assoc :plugins {SRC {ct {:tideward (assoc (draft "Tideward") :key :tideward)}}})
  (open! (draft "Tideward"))                                 ; a DIFFERENT item, same name
  (save!)
  (is (= 1 (count (stored))) "the sitting tenant is not replaced")
  (is (= :invalid (:name (:builder-field-errors @app-db))) "and the name field says why"))

(deftest taking-another-items-name-does-not-take-its-key
  ;; Two items may share a display name. Only the key is unique, and a saved item keeps its own.
  (swap! app-db assoc :plugins {SRC {ct {:tidewall (assoc (draft "Tidewall") :key :tidewall)}}})
  (open! (draft "Tideward"))
  (save!)
  (is (= #{:tideward :tidewall} (set (keys (stored)))))
  (set-name! "Tidewall")                                     ; the same NAME as the other item
  (save!)
  (is (= #{:tideward :tidewall} (set (keys (stored)))) "both survive")
  (is (= "Tidewall" (get-in (stored) [:tidewall :name])) "the other item is untouched")
  (is (= "Tidewall" (get-in (stored) [:tideward :name])) "and this one took the name, not the key"))

(deftest a-key-held-by-another-source-is-refused-too
  ;; A key is an address, and it is global. Two items answering to one key collide wherever they
  ;; live: the combines that dedupe pick a winner by the hash order of source names, and the ones
  ;; that do not show both copies. Wanting both is legitimate, and it arrives through IMPORT, where
  ;; "keep both" is something someone chose.
  (swap! app-db assoc :plugins {"Someone Else's Pak" {ct {:tideward {:key :tideward
                                                                    :name "Tideward"
                                                                    :option-pack "Someone Else's Pak"}}}})
  (open! (draft "Tideward"))
  (save!)
  (is (nil? (stored)) "nothing saved into this source")
  (is (= :invalid (:name (:builder-field-errors @app-db))) "and the name field says why"))

(deftest an-item-that-already-owns-its-key-saves-over-itself-in-any-library
  ;; The refusal is for MINTING a key something else holds. An item that already owns the key it
  ;; is saving to is returning to its own slot, however crowded the rest of the library is.
  (swap! app-db assoc :plugins {SRC {ct {:tideward (assoc (draft "Tideward") :key :tideward)}}
                                "Someone Else's Pak" {ct {:other {:key :other :name "Other"
                                                                  :option-pack "Someone Else's Pak"}}}})
  (open! (assoc (draft "Tideward") :key :tideward :description "edited"))
  (save!)
  (is (= "edited" (get-in (stored) [:tideward :description])))
  (is (empty? (:builder-field-errors @app-db))))

;; ---------------------------------------------------------------------------
;; Why a duplicate key is refused wherever it lives
;; ---------------------------------------------------------------------------

(deftest two-spells-sharing-a-key-resolve-inconsistently
  ;; The spell's DATA comes from a set deduped by key; its class membership is reduced over the
  ;; non-deduped seq. So the two disagree, and the disagreement is the reason the save refuses to
  ;; mint a key another source already holds rather than reporting it.
  (reset! app-db
          {:plugins {"A" {:orcpub.dnd.e5/spells
                          {:tideward {:key :tideward :name "Tideward" :level 3 :option-pack "A"
                                      :spell-lists {:wizard true}}}}
                     "B" {:orcpub.dnd.e5/spells
                          {:tideward {:key :tideward :name "Tideward" :level 3 :option-pack "B"
                                      :spell-lists {:wizard true :cleric true}}}}}})
  (let [lists @(rf/subscribe [::spells5e/plugin-spell-lists])]
    (is (= [:tideward :tideward] (get-in lists [:wizard 3]))
        "the key lands on the list once per copy")
    (is (= #{:wizard :cleric} (set (keys lists)))
        "and membership is the union, so an override can add a class but not remove one")))

(deftest editing-your-own-item-works-even-when-another-source-answers-to-its-key
  ;; The messy library: two sources already hold :tideward, from an import where someone chose
  ;; "keep both". The duplicate is real and the health card reports it — but it is not created by
  ;; THIS save, and refusing the save fixes nothing. It only traps the item: the key is minted
  ;; once, so renaming is not a way out either — a rename no longer moves the key.
  (swap! app-db assoc :plugins
         {SRC {ct {:tideward (assoc (draft "Tideward") :key :tideward)}}
          "Someone Else's Pak" {ct {:tideward {:key :tideward :name "Tideward"
                                               :option-pack "Someone Else's Pak"}}}})
  (open! (assoc (get-in @app-db [:plugins SRC ct :tideward]) :description "edited"))
  (save!)
  (is (= "edited" (get-in (stored) [:tideward :description])) "the edit landed")
  (is (empty? (:builder-field-errors @app-db)) "and nothing was flagged")
  (is (= "Tideward" (get-in @app-db [:plugins "Someone Else's Pak" ct :tideward :name]))
      "the other source is untouched"))
