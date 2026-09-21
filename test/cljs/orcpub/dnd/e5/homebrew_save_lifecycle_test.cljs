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
            [orcpub.dnd.e5.selections :as selections5e]
            [orcpub.dnd.e5.orcbrew-validation :as orcbrew-val]
            [orcpub.dnd.e5.spell-subs :as subs5e]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.common :as common]
            [orcpub.dnd.e5.content-reconciliation :as reconcile]
            ;; Side effect: registers every event handler under test.
            [orcpub.dnd.e5.events :as events]
            ;; …and every subscription: the collision the save refuses is the one pinned at the
            ;; bottom of this file.
            [orcpub.dnd.e5.spell-subs]))

(def ^:private SRC "Lifecycle Pak")
(def ^:private ct :orcpub.dnd.e5/languages)

(defn- k
  "The key `item-name` mints in SRC. A minted key carries its source's tag (D10b), so the tests
   below ask for it by the same rule rather than spelling `:tideward-lepk` out twenty times — the
   literal is pinned once, in `a-minted-key-carries-its-sources-tag`."
  [item-name]
  (common/source-tagged-key item-name SRC))

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
  "Put a NEW `item` in the builder -- what the New button does. It clears `:builder-origin`,
   because every real path into the form either records where the item came from or says there
   is nowhere; a stale record from a previous save is not a state the app can reach."
  [item]
  (swap! app-db #(-> % (assoc ::langs5e/builder-item item) (dissoc :builder-origin))))

(defn- dispatch! 
  "Dispatch `event` and run everything it queues, synchronously."
  [event]
  (reset! queued [])
  (rf/dispatch-sync event)
  (loop [n 0]                                   ; drain, bounded: these chains are two deep
    (let [evs @queued]
      (when (and (seq evs) (< n 10))
        (reset! queued [])
        (doseq [ev evs] (rf/dispatch-sync ev))
        (recur (inc n))))))

(defn- save! [] (dispatch! [::langs5e/save-language]))

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
  (is (= #{(k "Tideward")} (set (keys (stored)))))
  (is (= "Tideward" (get-in (stored) [(k "Tideward") :name]))))

(deftest the-form-keeps-the-key-it-just-wrote
  ;; `save-collision` reads this to tell an edit returning to its own slot from a name landing on
  ;; somebody else's. Without it the SECOND save below is refused as an overwrite of itself.
  (open! (draft "Tideward"))
  (save!)
  (is (= (k "Tideward") (:key (in-builder)))))

(deftest saving-the-same-item-twice-is-not-a-collision
  (open! (draft "Tideward"))
  (save!)
  (swap! app-db assoc-in [::langs5e/builder-item :description] "a tide-tongue")
  (save!)
  (is (= #{(k "Tideward")} (set (keys (stored)))) "one entry, not two")
  (is (= "a tide-tongue" (get-in (stored) [(k "Tideward") :description])) "the second save landed")
  (is (empty? (:builder-field-errors @app-db)) "and nothing was flagged"))

(deftest a-restored-draft-saves-back-into-its-own-slot
  ;; Save, leave, come back: the form is rebuilt from the persisted draft rather than from the
  ;; item in :plugins, so the draft has to carry the key too.
  (open! (draft "Tideward"))
  (save!)
  (let [persisted (in-builder)]
    (reset-db!)
    (swap! app-db assoc :plugins {SRC {ct {(k "Tideward") (assoc (draft "Tideward")
                                                                 :key (k "Tideward"))}}})
    (open! persisted)
    (save!)
    (is (= #{(k "Tideward")} (set (keys (stored)))))))

(deftest editing-a-saved-item-saves-back-into-its-own-slot
  (swap! app-db assoc :plugins {SRC {ct {(k "Tideward") (assoc (draft "Tideward")
                                                               :key (k "Tideward"))}}})
  (open! (get-in @app-db [:plugins SRC ct (k "Tideward")]))  ; what the edit event hands the form
  (swap! app-db assoc-in [::langs5e/builder-item :description] "edited")
  (save!)
  (is (= #{(k "Tideward")} (set (keys (stored)))))
  (is (= "edited" (get-in (stored) [(k "Tideward") :description]))))

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
  (is (= #{(k "Tideward")} (set (keys (stored)))) "one entry, still at the key it was minted under")
  (is (= "Tidewall" (get-in (stored) [(k "Tideward") :name])) "with the new name on it")
  (is (empty? (get-in (stored) [(k "Tideward") :former-keys])) "nothing moved, so nothing to record"))

(deftest a-character-keeps-resolving-across-a-rename
  (open! (draft "Tideward"))
  (save!)
  (set-name! "Tidewall")
  (save!)
  (is (contains? (stored) (k "Tideward")) "the key a character stored is still the live one")
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
  (swap! app-db assoc :plugins {SRC {ct {(k "Tideward") (assoc (draft "Tideward")
                                                               :key (k "Tideward"))}}})
  (open! (draft "Tideward"))                                 ; a DIFFERENT item, same name
  (save!)
  (is (= 1 (count (stored))) "the sitting tenant is not replaced")
  (is (= :invalid (:name (:builder-field-errors @app-db))) "and the name field says why"))

(deftest taking-another-items-name-does-not-take-its-key
  ;; Two items may share a display name. Only the key is unique, and a saved item keeps its own.
  (swap! app-db assoc :plugins {SRC {ct {(k "Tidewall") (assoc (draft "Tidewall")
                                                               :key (k "Tidewall"))}}})
  (open! (draft "Tideward"))
  (save!)
  (is (= #{(k "Tideward") (k "Tidewall")} (set (keys (stored)))))
  (set-name! "Tidewall")                                     ; the same NAME as the other item
  (save!)
  (is (= #{(k "Tideward") (k "Tidewall")} (set (keys (stored)))) "both survive")
  (is (= "Tidewall" (get-in (stored) [(k "Tidewall") :name])) "the other item is untouched")
  (is (= "Tidewall" (get-in (stored) [(k "Tideward") :name])) "and this one took the name, not the key"))

(deftest a-key-held-by-another-source-is-refused-too
  ;; A key is an address, and it is global. Two items answering to one key collide wherever they
  ;; live: the combines that dedupe pick a winner by the hash order of source names, and the ones
  ;; that do not show both copies. Wanting both is legitimate, and it arrives through IMPORT, where
  ;; "keep both" is something someone chose.
  ;;
  ;; Tagging (D10b) makes this rare rather than impossible: the twin here holds the key THIS source
  ;; mints, which is what an .orcbrew authored in "Lifecycle Pak" and imported under another source
  ;; name leaves behind.
  (swap! app-db assoc :plugins {"Someone Else's Pak" {ct {(k "Tideward") {:key (k "Tideward")
                                                                          :name "Tideward"
                                                                          :option-pack "Someone Else's Pak"}}}})
  (open! (draft "Tideward"))
  (save!)
  (is (nil? (stored)) "nothing saved into this source")
  (is (= :invalid (:name (:builder-field-errors @app-db))) "and the name field says why"))

(defn- change-key!
  "What the control sends: the raw string an author typed, not a keyword. The event needs blank
   and junk to stay distinguishable, and `name-to-kw` turns both into something keyword-shaped."
  [typed]
  (dispatch! [::e5/change-builder-item-key ::langs5e/save-language typed]))

(defn- set-abbr! [abbr]
  (dispatch! [::e5/set-source-abbreviation SRC abbr]))

;; ---------------------------------------------------------------------------
;; Changing a key on purpose
;; ---------------------------------------------------------------------------

(deftest changing-a-key-moves-the-item-and-records-the-move
  ;; The only way an author changes a key now that the name field does not. Same move import
  ;; conflict resolution makes, so characters rebind on load.
  (open! (draft "Tidewrad"))
  (save!)
  ;; the author TYPES this one, so it is exactly what they typed -- no tag appended. Deleting the
  ;; tag is how an SRD override is asked for, so the control must not put one back.
  (change-key! "tideward")
  (is (= #{:tideward} (set (keys (stored)))) "moved, not copied")
  (is (= [(k "Tidewrad")] (get-in (stored) [:tideward :former-keys])) "and the move is recorded")
  (is (= :tideward (:key (in-builder))) "the open form follows its own item")
  (is (= {(k "Tidewrad") :tideward} (reconcile/former-key-index (:plugins @app-db))))
  (is (= "Tidewrad" (get-in (stored) [:tideward :name])) "the NAME is untouched"))

(deftest a-key-change-is-refused-when-the-key-is-taken-anywhere
  (swap! app-db assoc :plugins
         {SRC {ct {(k "Tidewrad") (assoc (draft "Tidewrad") :key (k "Tidewrad"))}}
          "Someone Else's Pak" {ct {:tideward {:key :tideward :name "Tideward"
                                               :option-pack "Someone Else's Pak"}}}})
  (open! (get-in @app-db [:plugins SRC ct (k "Tidewrad")]))
  (change-key! "tideward")
  (is (= #{(k "Tidewrad")} (set (keys (stored)))) "nothing moved")
  (is (= (k "Tidewrad") (:key (in-builder)))))

(deftest an-unsaved-item-has-no-key-to-change
  (open! (draft "Tideward"))
  (change-key! "something else")
  (is (nil? (stored)))
  (is (nil? (:key (in-builder)))))

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

(deftest a-minted-key-carries-its-sources-tag
  ;; The literal, pinned once. Everything above asks for keys through `k` so a change to the
  ;; abbreviation rule fails HERE and not in thirty places.
  (is (= :tideward-lepk (common/source-tagged-key "Tideward" SRC)))
  (is (= :artificer-ksty (common/source-tagged-key "Artificer" "Kibbles Tasty"))
      "the same key an import conflict would give it")
  (is (= :stone-elf-dflt (common/source-tagged-key "Stone Elf" "Default Option Source"))
      "the placeholder source tags too — it is where most first homebrew lands")
  (is (= :stone-elf-ua (common/source-tagged-key "Stone Elf" "Unearthed Arcana"))
      "and a source with a real-world abbreviation uses it")
  (is (= :stone-elf (common/source-tagged-key "Stone Elf" ""))
      "a source with no name mints the plain key"))

;; ---------------------------------------------------------------------------
;; A source's own tag
;; ---------------------------------------------------------------------------

(deftest a-source-can-set-the-tag-its-keys-are-minted-with
  ;; The derivation is a guess, and it is a guess nobody can correct for an author's own source.
  (set-abbr! "twc")
  (is (= "TWC" (get-in @app-db [:plugins SRC :abbreviation])) "normalized on the way in")
  (open! (draft "Tideward"))
  (save!)
  (is (= #{:tideward-twc} (set (keys (stored)))) "and the next key is minted with it"))

(deftest clearing-the-tag-hands-the-source-back-to-the-derivation
  (set-abbr! "twc")
  (set-abbr! "  ")
  (is (nil? (get-in @app-db [:plugins SRC :abbreviation])) "no stored copy of a guess")
  (open! (draft "Tideward"))
  (save!)
  (is (= #{(k "Tideward")} (set (keys (stored))))))

(deftest keys-already-minted-do-not-move-when-the-tag-changes
  ;; D9. The tag decides what the NEXT key gets; a stored key is an address something may hold.
  (open! (draft "Tideward"))
  (save!)
  (set-abbr! "twc")
  (is (= #{(k "Tideward")} (set (keys (stored)))) "the existing key is untouched")
  (open! (draft "Tidewall"))
  (save!)
  (is (= #{(k "Tideward") :tidewall-twc} (set (keys (stored)))) "only the new one carries it"))

(deftest a-tag-that-normalizes-to-nothing-is-not-stored
  (doseq [junk ["" "   " "!!!" "9" "2022"]]
    (set-abbr! junk)
    (is (nil? (get-in @app-db [:plugins SRC :abbreviation])) (pr-str junk)))
  (testing "a digit may follow a letter, just not lead"
    (set-abbr! "ua2")
    (is (= "UA2" (get-in @app-db [:plugins SRC :abbreviation])))))

;; ---------------------------------------------------------------------------
;; Moving an item between sources — Option Source Name is the instruction
;; ---------------------------------------------------------------------------

(def ^:private OTHER "Other Pak")

(defn- open-from-library!
  "Open a stored item the way My Content's edit button does, so the builder records where it came
   from."
  [source k]
  ;; My Content passes the ROW's address alongside the item -- the source holding it and the key
  ;; it answers to -- because neither is reliably on the item itself.
  (dispatch! [::langs5e/edit-language (get-in @app-db [:plugins source ct k]) source k ct]))

(defn- retarget! [source]
  (swap! app-db assoc-in [::langs5e/builder-item :option-pack] source))

(deftest retyping-the-source-MOVES-the-item-rather-than-copying-it
  ;; The bug this exists for: the save could only ever add, because it did not know where the item
  ;; had been. You ended up with one key answering in two sources — which then made both copies
  ;; uneditable.
  (open! (draft "Tideward"))
  (save!)
  (open-from-library! SRC (k "Tideward"))
  (retarget! OTHER)
  (save!)
  (is (nil? (get-in @app-db [:plugins SRC ct (k "Tideward")])) "gone from the source it left")
  (is (= "Tideward" (get-in @app-db [:plugins OTHER ct (k "Tideward") :name])) "and arrived")
  (is (= OTHER (get-in @app-db [:plugins OTHER ct (k "Tideward") :option-pack]))
      "carrying its new home"))

(deftest a-move-keeps-the-key-so-characters-are-untouched
  ;; A source move changes which library holds the item, not its address. Characters store only the
  ;; address, so there is nothing to heal and no breadcrumb to leave.
  (open! (draft "Tideward"))
  (save!)
  (open-from-library! SRC (k "Tideward"))
  (retarget! OTHER)
  (save!)
  (let [moved (get-in @app-db [:plugins OTHER ct (k "Tideward")])]
    (is (= (k "Tideward") (:key moved)) "same key")
    (is (empty? (:former-keys moved)) "nothing to record")
    (is (= {} (reconcile/former-key-index (:plugins @app-db))) "and nothing to heal")))

(deftest a-move-onto-an-occupied-address-is-refused-and-loses-nothing
  (open! (draft "Tideward"))
  (save!)
  (swap! app-db assoc-in [:plugins OTHER ct (k "Tideward")]
         {:key (k "Tideward") :name "Somebody Else's" :option-pack OTHER})
  (open-from-library! SRC (k "Tideward"))
  (retarget! OTHER)
  (save!)
  (is (= "Somebody Else's" (get-in @app-db [:plugins OTHER ct (k "Tideward") :name]))
      "the item already there is untouched")
  (is (some? (get-in @app-db [:plugins SRC ct (k "Tideward")])) "and this one stayed put")
  (is (empty? (:builder-field-errors @app-db))
      "and the NAME is not flagged — this item has a key, so renaming cannot help (D10a)"))

(deftest editing-in-place-is-not-a-move
  (open! (draft "Tideward"))
  (save!)
  (open-from-library! SRC (k "Tideward"))
  (retarget! SRC)                                    ; retyped to the SAME source
  (swap! app-db assoc-in [::langs5e/builder-item :description] "edited")
  (save!)
  (is (= #{(k "Tideward")} (set (keys (stored)))))
  (is (= "edited" (get-in (stored) [(k "Tideward") :description]))))

(deftest a-standing-duplicate-no-longer-traps-the-item-that-owns-it
  ;; Two sources already answer to one key — an import where somebody chose "keep both". Editing
  ;; either copy in place is its own slot, and says so because the builder knows where it opened
  ;; the item from.
  (swap! app-db assoc :plugins
         {SRC   {ct {(k "Tideward") (assoc (draft "Tideward") :key (k "Tideward"))}}
          OTHER {ct {(k "Tideward") {:key (k "Tideward") :name "Tideward" :option-pack OTHER}}}})
  (open-from-library! SRC (k "Tideward"))
  (swap! app-db assoc-in [::langs5e/builder-item :description] "edited")
  (save!)
  (is (= "edited" (get-in (stored) [(k "Tideward") :description])) "the edit landed")
  (is (empty? (:builder-field-errors @app-db)) "nothing was flagged")
  (is (= "Tideward" (get-in @app-db [:plugins OTHER ct (k "Tideward") :name]))
      "and the other source is untouched"))

(deftest an-item-with-no-recorded-origin-is-not-moved-on-a-guess
  ;; The origin IS persisted now, so a refresh keeps it (pinned end-to-end by
  ;; test/e2e/move-between-sources.js, which reloads the page mid-edit). This is the case where
  ;; it is genuinely absent -- a Move/copy from My Content rearranging :plugins under an open
  ;; builder, which leaves the form holding an item the library has moved on from. One source
  ;; holds the key, which is enough to save back INTO it, but not enough to delete the entry
  ;; there. Refusing costs one click; moving on a guess costs the entry.
  (open! (draft "Tideward"))
  (save!)
  (swap! app-db dissoc :builder-origin)              ; a Move/copy under an open builder
  (retarget! OTHER)
  (save!)
  (is (some? (get-in @app-db [:plugins SRC ct (k "Tideward")])) "it stayed where it lives")
  (is (nil? (get-in @app-db [:plugins OTHER ct (k "Tideward")])) "and did not arrive")

  ;; ...but a save back into the source it lives in is still fine
  (retarget! SRC)
  (swap! app-db assoc-in [::langs5e/builder-item :description] "edited")
  (save!)
  (is (= "edited" (get-in (stored) [(k "Tideward") :description]))))

(deftest a-restored-draft-does-not-guess-when-two-sources-answer
  ;; No record, and two holders: there is no honest answer, so the item is treated as new — which
  ;; means the occupied target refuses rather than something being overwritten.
  (swap! app-db assoc :plugins
         {SRC   {ct {(k "Tideward") (assoc (draft "Tideward") :key (k "Tideward"))}}
          OTHER {ct {(k "Tideward") {:key (k "Tideward") :name "Somebody Else's"
                                     :option-pack OTHER}}}})
  (open! (assoc (get-in @app-db [:plugins SRC ct (k "Tideward")]) :option-pack OTHER))
  (save!)
  (is (= "Somebody Else's" (get-in @app-db [:plugins OTHER ct (k "Tideward") :name]))
      "nothing was overwritten")
  (is (some? (get-in @app-db [:plugins SRC ct (k "Tideward")])) "and nothing was moved"))

;; ---------------------------------------------------------------------------
;; save-destination — the whole decision, one table
;; ---------------------------------------------------------------------------

(def ^:private A "Pak A")
(def ^:private B "Pak B")

(defn- lib [& sources]
  (into {} (for [[src k nm] (partition 3 sources)]
             [src {ct {k {:key k :name nm :option-pack src}}}])))

(defn- dest [plugins recorded option-pack key item]
  (:action (events/save-destination plugins recorded ct option-pack key item)))

(deftest save-destination-decides-by-where-the-item-came-from
  (testing "minting"
    (is (= :create (dest {} nil A :stone-elf {:name "Stone Elf"})))
    (is (= :refuse (dest (lib A :stone-elf "Somebody's") nil A :stone-elf {:name "Stone Elf"}))
        "a key something else answers to")
    (is (= :refuse (dest (lib B :stone-elf "Somebody's") nil A :stone-elf {:name "Stone Elf"}))
        "including one that answers in another library"))

  (testing "an item that came from here"
    (let [plugins (lib A :stone-elf "Stone Elf")]
      (is (= :in-place (dest plugins {:source A :key :stone-elf} A :stone-elf
                             {:key :stone-elf :name "Stone Elf"})))
      (is (= :move (dest plugins {:source A :key :stone-elf} B :stone-elf
                         {:key :stone-elf :name "Stone Elf"}))
          "retyping the source is the instruction to move")))

  (testing "moving onto an address something else answers to"
    (is (= :refuse (dest (lib A :stone-elf "Mine" B :stone-elf "Theirs")
                         {:source A :key :stone-elf} B :stone-elf
                         {:key :stone-elf :name "Mine"})))))

(deftest save-destination-does-not-trust-a-record-the-library-contradicts
  (let [item {:key :stone-elf :name "Stone Elf"}]
    (testing "the recorded source no longer holds the key — it moved, or was deleted"
      ;; trusting it would dissoc from a source that has already let go, leaving the copy it
      ;; moved to in place: one key, two libraries
      (is (= :in-place (dest (lib B :stone-elf "Stone Elf") {:source A :key :stone-elf}
                             B :stone-elf item))))

    (testing "a record for the same key in ANOTHER builder cannot reach this one"
      ;; a source holds a race and a subrace under one key for one name. The record lives in a
      ;; slot per content type, so the race builder's cannot be read by the subrace save at all
      ;; -- it is not a guard inside save-destination, it is the shape of the storage.
      (is (nil? (get-in {:builder-origin {:orcpub.dnd.e5/races {:source A :key :stone-elf}}}
                        [:builder-origin ct]))))

    (testing "no record at all: enough to save back in, NOT enough to move out"
      ;; the single holder is a guess. It cannot lose anything when the save lands back in that
      ;; same library, but a move DELETES the entry there -- and the builder may be holding an
      ;; item the library has moved on from, e.g. after a Move/copy from My Content.
      (is (= :in-place (dest (lib A :stone-elf "Stone Elf") nil A :stone-elf item)))
      (is (= :refuse   (dest (lib A :stone-elf "Stone Elf") nil B :stone-elf item))))

    (testing "no record and SEVERAL holders: refused, because this one cannot be told from its twin"
      ;; reopening it from My Content records where it came from, which is the way out
      (let [messy (lib A :stone-elf "Mine" B :stone-elf "Theirs")]
        (is (= :refuse (dest messy nil A :stone-elf item)))
        (is (= :refuse (dest messy nil "Pak C" :stone-elf item)))))

    (testing "no record and NO holder: nothing to duplicate"
      (is (= :create (dest {} nil A :stone-elf item))))))

(deftest a-second-save-after-a-move-is-in-place-not-another-move
  ;; The origin is re-stamped on save. Left pointing at the source it came from, the next save reads
  ;; as a move off an entry that is already gone — which either refuses the item or copies it on.
  (open! (draft "Tideward"))
  (save!)
  (open-from-library! SRC (k "Tideward"))
  (retarget! OTHER)
  (save!)
  (swap! app-db assoc-in [::langs5e/builder-item :description] "edited after the move")
  (save!)
  (is (nil? (get-in @app-db [:plugins SRC ct (k "Tideward")])) "still gone from where it left")
  (is (= "edited after the move"
         (get-in @app-db [:plugins OTHER ct (k "Tideward") :description]))
      "and the second save landed where it now lives")
  (is (empty? (:builder-field-errors @app-db)) "with nothing flagged"))

(deftest a-move-onward-to-a-third-source-does-not-leave-the-second-behind
  (open! (draft "Tideward"))
  (save!)
  (open-from-library! SRC (k "Tideward"))
  (retarget! OTHER)
  (save!)
  (retarget! "Third Pak")
  (save!)
  (is (= [#{"Third Pak"}]
         [(set (for [[src plugin] (:plugins @app-db)
                     :when (get-in plugin [ct (k "Tideward")])]
                 src))])
      "one library holds it, the last one asked for"))

;; ---------------------------------------------------------------------------
;; Replacing something, on purpose
;; ---------------------------------------------------------------------------

(defn- save-replacing! [] (dispatch! [::langs5e/save-language {:replace? true}]))

(deftest consent-only-re-decides-the-refusal-that-named-what-would-be-lost
  ;; "Replace it" is consent to discard ONE named entry the author can see on screen. The other
  ;; two refusals have nothing in the way to replace, so a yes there would MAKE the duplicate
  ;; rather than resolve it -- and the banner does not offer one.
  (is (= {:action :move :from A}
         (events/replacing {:action :refuse :reason :occupied :occupant {} :origin A}))
      "an item that lives somewhere else moves onto the address")
  (is (= {:action :create}
         (events/replacing {:action :refuse :reason :occupied :occupant {}}))
      "a mint has nowhere to move FROM -- it just lands")
  (is (= {:action :refuse :reason :elsewhere :holders #{B}}
         (events/replacing {:action :refuse :reason :elsewhere :holders #{B}}))
      "another library's entry is not this author's to discard")
  (is (= {:action :refuse :reason :ambiguous :holders #{A B}}
         (events/replacing {:action :refuse :reason :ambiguous :holders #{A B}}))
      "and consent to an unnamed one of two is not consent")
  (is (= {:action :in-place} (events/replacing {:action :in-place}))
      "a save that was never refused is untouched"))

(deftest replacing-on-a-move-discards-the-occupant-and-empties-the-origin
  (open! (draft "Tideward"))
  (save!)
  (swap! app-db assoc-in [:plugins OTHER ct (k "Tideward")]
         {:key (k "Tideward") :name "Somebody Else's" :option-pack OTHER})
  (open-from-library! SRC (k "Tideward"))
  (retarget! OTHER)
  (save!)
  (is (= "Somebody Else's" (get-in @app-db [:plugins OTHER ct (k "Tideward") :name]))
      "refused first, with nothing touched")
  (save-replacing!)
  (is (= "Tideward" (get-in @app-db [:plugins OTHER ct (k "Tideward") :name]))
      "and on consent this one takes the address")
  (is (nil? (get-in @app-db [:plugins SRC ct (k "Tideward")]))
      "as a MOVE -- replacing is not a licence to leave a copy behind")
  (is (empty? (:builder-field-errors @app-db))))

(deftest replacing-on-a-mint-takes-over-the-address
  (swap! app-db assoc-in [:plugins SRC ct (k "Tideward")]
         {:key (k "Tideward") :name "Somebody Else's" :option-pack SRC})
  (open! (draft "Tideward"))                          ; a NEW item minting the same key
  (save!)
  (is (= "Somebody Else's" (get-in (stored) [(k "Tideward") :name])) "refused first")
  (save-replacing!)
  (is (= #{(k "Tideward")} (set (keys (stored)))) "one entry at that address")
  (is (= "Tideward" (get-in (stored) [(k "Tideward") :name])) "and it is the new one"))

(deftest consent-does-not-reach-a-key-answering-in-another-source
  (swap! app-db assoc-in [:plugins OTHER ct (k "Tideward")]
         {:key (k "Tideward") :name "Somebody Else's" :option-pack OTHER})
  (open! (draft "Tideward"))
  (save-replacing!)
  (is (empty? (stored)) "nothing written here")
  (is (= "Somebody Else's" (get-in @app-db [:plugins OTHER ct (k "Tideward") :name])) "or there")
  (is (= :invalid (:name (:builder-field-errors @app-db))) "still refused, with the reason on the form"))

(deftest consent-does-not-reach-an-ambiguous-save
  (swap! app-db assoc :plugins
         {SRC   {ct {(k "Tideward") {:key (k "Tideward") :name "Mine" :option-pack SRC}}}
          OTHER {ct {(k "Tideward") {:key (k "Tideward") :name "Theirs" :option-pack OTHER}}}})
  (open! {:key (k "Tideward") :name "Mine" :option-pack SRC})   ; opened from nowhere: no origin
  (save-replacing!)
  (is (= "Mine" (get-in @app-db [:plugins SRC ct (k "Tideward") :name])))
  (is (= "Theirs" (get-in @app-db [:plugins OTHER ct (k "Tideward") :name]))
      "neither twin is guessed at, consent or not"))

;; ---------------------------------------------------------------------------
;; Save anyway -- placeholders fill the FIELDS, not the address
;; ---------------------------------------------------------------------------

(defn- save-anyway! [] (dispatch! [::langs5e/save-language-anyway]))

(deftest save-anyway-moves-rather-than-copying
  (open! (draft "Tideward"))
  (save!)
  (open-from-library! SRC (k "Tideward"))
  (retarget! OTHER)
  (save-anyway!)
  (is (nil? (get-in @app-db [:plugins SRC ct (k "Tideward")])) "no copy left behind")
  (is (some? (get-in @app-db [:plugins OTHER ct (k "Tideward")])) "and it arrived"))

(deftest save-anyway-stamps-the-key-back-onto-the-form
  ;; Without the stamp the item in the builder still has no key, so the next save mints a second
  ;; one and lands on its own entry: the save-twice refusal, one path over.
  (open! (draft "Tideward"))
  (save-anyway!)
  (is (= (k "Tideward") (:key (in-builder))))
  (save!)
  (is (= #{(k "Tideward")} (set (keys (stored)))) "one entry after both saves")
  (is (empty? (:builder-field-errors @app-db))))

(deftest save-anyway-does-not-clobber-somebody-elses-entry
  (swap! app-db assoc-in [:plugins SRC ct (k "Tideward")]
         {:key (k "Tideward") :name "Somebody Else's" :option-pack SRC})
  (open! (draft "Tideward"))
  (save-anyway!)
  (is (= "Somebody Else's" (get-in (stored) [(k "Tideward") :name]))
      "placeholders do not buy an address")
  (dispatch! [::langs5e/save-language-anyway {:replace? true}])
  (is (= "Tideward" (get-in (stored) [(k "Tideward") :name])) "until the author says so"))

;; ---------------------------------------------------------------------------
;; Selections go through the same gate
;; ---------------------------------------------------------------------------

(def ^:private sct :orcpub.dnd.e5/selections)

(defn- selection [nm src]
  {:name nm :option-pack src :options [{:name "Archery"}]})

(deftest a-selection-may-not-silently-replace-another
  ;; The selection save is its own handler, for its duplicate-option-name checks. It wrote with a
  ;; bare assoc-in: no collision check of any kind, not even on the ordinary save.
  (swap! app-db assoc-in [:plugins SRC sct (k "Fighting Style")]
         {:key (k "Fighting Style") :name "Theirs" :option-pack SRC})
  (swap! app-db assoc ::selections5e/builder-item (selection "Fighting Style" SRC))
  (dispatch! [::selections5e/save-selection])
  (is (= "Theirs" (get-in @app-db [:plugins SRC sct (k "Fighting Style") :name])) "left alone")
  (dispatch! [::selections5e/save-selection {:replace? true}])
  (is (= "Fighting Style" (get-in @app-db [:plugins SRC sct (k "Fighting Style") :name]))
      "until the author says so"))

(deftest a-selection-moves-rather-than-copying
  (swap! app-db assoc ::selections5e/builder-item (selection "Fighting Style" SRC))
  (dispatch! [::selections5e/save-selection])
  (dispatch! [::selections5e/edit-selection
              (get-in @app-db [:plugins SRC sct (k "Fighting Style")])
              SRC (k "Fighting Style") sct])
  (swap! app-db assoc-in [::selections5e/builder-item :option-pack] OTHER)
  (dispatch! [::selections5e/save-selection])
  (is (nil? (get-in @app-db [:plugins SRC sct (k "Fighting Style")])) "no copy left behind")
  (is (some? (get-in @app-db [:plugins OTHER sct (k "Fighting Style")])) "and it arrived"))

(deftest a-selection-keeps-the-key-it-just-wrote
  (swap! app-db assoc ::selections5e/builder-item (selection "Fighting Style" SRC))
  (dispatch! [::selections5e/save-selection])
  (is (= (k "Fighting Style") (:key (::selections5e/builder-item @app-db))))
  (dispatch! [::selections5e/save-selection])
  (is (= #{(k "Fighting Style")} (set (keys (get-in @app-db [:plugins SRC sct]))))
      "one entry after both saves"))

;; ---------------------------------------------------------------------------
;; Libraries authored before keys were stored
;; ---------------------------------------------------------------------------

;; `:key` is OPTIONAL on a stored item. Older libraries do not carry one and the read path derives
;; it from the NAME -- untagged, because the tag is newer than they are. Minting a tagged key on
;; the next save wrote a SECOND entry and left the original holding the pre-edit data, with no
;; :former-keys to heal it and nothing on screen to say it had happened.
(def ^:private old-key (common/name-to-kw "Tideward"))

(defn- with-legacy-item! []
  (swap! app-db assoc :plugins {SRC {ct {old-key {:name "Tideward" :option-pack SRC}}}}))

(deftest an-old-item-keeps-the-address-it-is-already-stored-under
  (is (not= old-key (k "Tideward")) "the minted key today is tagged; the stored one is not")
  (with-legacy-item!)
  (open-from-library! SRC old-key)
  (swap! app-db assoc-in [::langs5e/builder-item :description] "edited")
  (save!)
  (is (= #{old-key} (set (keys (stored)))) "one entry, at the address it already had")
  (is (= "edited" (get-in (stored) [old-key :description])) "carrying the edit"))

(deftest an-old-item-is-its-own-slot-not-a-collision
  ;; The first fix for this landed the item; the second save then read as a NEW item minting a key
  ;; something already holds, and was refused.
  (with-legacy-item!)
  (open-from-library! SRC old-key)
  (save!)
  (save!)
  (is (= #{old-key} (set (keys (stored)))))
  (is (empty? (:builder-field-errors @app-db)) "and nothing flagged on either save"))

(deftest save-anyway-also-keeps-an-old-address
  (with-legacy-item!)
  (open-from-library! SRC old-key)
  (dispatch! [::langs5e/save-language-anyway])
  (is (= #{old-key} (set (keys (stored)))) "placeholders do not re-address it either"))

(deftest a-selection-also-keeps-an-old-address
  (let [old (common/name-to-kw "Fighting Style")]
    (swap! app-db assoc :plugins
           {SRC {sct {old {:name "Fighting Style" :option-pack SRC
                           :options [{:name "Archery"}]}}}})
    (dispatch! [::selections5e/edit-selection
                (get-in @app-db [:plugins SRC sct old]) SRC old sct])
    (dispatch! [::selections5e/save-selection])
    (is (= #{old} (set (keys (get-in @app-db [:plugins SRC sct]))))
        "one entry, at the address it already had")))

(deftest a-name-that-matches-nothing-still-mints-a-tagged-key
  ;; The guard is "this address is ALREADY answering", not "drop the tag whenever there is no key".
  (open! (draft "Tideward"))
  (save!)
  (is (= #{(k "Tideward")} (set (keys (stored)))) "a new item is tagged as usual"))

(deftest an-old-item-in-another-source-does-not-capture-a-new-one
  ;; The probe is scoped to the source being saved to. An untagged entry of the same name in a
  ;; DIFFERENT library must not pull a new item onto its address.
  (swap! app-db assoc-in [:plugins OTHER ct old-key]
         {:name "Tideward" :option-pack OTHER})
  (open! (draft "Tideward"))
  (save!)
  (is (= #{(k "Tideward")} (set (keys (stored)))) "the new one mints its own tagged key")
  (is (= "Tideward" (get-in @app-db [:plugins OTHER ct old-key :name])) "and the old one is untouched"))

;; ---------------------------------------------------------------------------
;; The typed key, from the cut branch
;; ---------------------------------------------------------------------------

(deftest a-key-the-author-types-has-to-start-with-a-letter
  ;; This is the only place in the app that sets a key by hand. A key that does not start with a
  ;; letter is a keyword trap: the import pipeline quarantines content carrying one.
  (open! (draft "Tideward"))
  (save!)
  (doseq [junk ["" "   " "@@@" "123"]]
    (change-key! junk)
    (is (= #{(k "Tideward")} (set (keys (stored)))) (str "refused: " (pr-str junk)))
    (is (= (k "Tideward") (:key (in-builder))))))

;; ---------------------------------------------------------------------------
;; The negative controls that were missing (review, 2026-09-19)
;; ---------------------------------------------------------------------------

(deftest a-new-item-does-not-capture-an-untagged-entry-of-the-same-name
  ;; The one that was never written. `a-new-item-may-not-take-a-key-another-item-in-this-source-
  ;; holds` files the tenant under the TAGGED key, which an untagged address never matches, so it
  ;; passed without exercising this at all.
  ;;
  ;; Untagged entries are ordinary: imported content keeps the keys it arrived with, and stripping
  ;; the tag is how an author asks to override an SRD item. Identifying a keyless item by NAME
  ;; handed this new item that entry's address, and the save then overwrote it in place -- no
  ;; banner, no Replace offer, an item the author never opened.
  (with-legacy-item!)
  (open! (assoc (draft "Tideward") :description "a different language, same name"))
  (save!)
  (is (nil? (get-in (stored) [old-key :description])) "the entry already there is untouched")
  (is (= "a different language, same name" (get-in (stored) [(k "Tideward") :description]))
      "and the new one minted its own tagged key beside it")
  (is (= #{old-key (k "Tideward")} (set (keys (stored)))) "both survive")
  (is (empty? (:builder-field-errors @app-db)) "with nothing flagged — they do not collide"))

(deftest save-anyway-with-a-blank-source-does-not-move-the-item-out-of-its-library
  ;; A blank Option Source Name is the field the banner is complaining about. Substituting the
  ;; placeholder source and handing THAT to the destination read as a retarget: the item was
  ;; deleted from the library it lived in and relocated to "Default Option Source", with the
  ;; banner mentioning only where it landed.
  (open! (draft "Tideward"))
  (save!)
  (open-from-library! SRC (k "Tideward"))
  (swap! app-db assoc-in [::langs5e/builder-item :option-pack] "")
  (dispatch! [::langs5e/save-language-anyway])
  (is (= #{(k "Tideward")} (set (keys (stored)))) "still in the source it came from")
  (is (nil? (get-in @app-db [:plugins orcbrew-val/default-option-source ct (k "Tideward")]))
      "and did not land in the placeholder source"))

(deftest a-keyless-item-with-a-twin-elsewhere-can-still-be-saved
  ;; It could not be. `reg-edit-homebrew` recorded `(:key item)`, which is nil for a library
  ;; authored before keys were stored, so the record never validated; two holders then meant
  ;; :ambiguous, and the banner said to reopen it from My Content -- which recorded nil again.
  ;; The key control refuses an item with no key, so there was no way out at all.
  (with-legacy-item!)
  (swap! app-db assoc-in [:plugins OTHER ct old-key]
         {:name "Somebody Else's" :option-pack OTHER})
  (open-from-library! SRC old-key)
  (swap! app-db assoc-in [::langs5e/builder-item :description] "edited")
  (save!)
  (is (= "edited" (get-in (stored) [old-key :description])) "the edit landed")
  (is (= "Somebody Else's" (get-in @app-db [:plugins OTHER ct old-key :name]))
      "and the twin is untouched"))

(deftest a-stale-option-pack-does-not-relocate-the-item
  ;; An import where the author renamed the source at the modal files the content under the name
  ;; they chose WITHOUT rewriting each item's :option-pack, so the item still declares the name it
  ;; arrived with. Reading the origin off that field made a no-op edit-and-save look like a move,
  ;; and the item was relocated out of the source the author had named.
  (swap! app-db assoc :plugins
         {SRC {ct {(k "Tideward") {:key (k "Tideward") :name "Tideward"
                                   :option-pack "Name It Declared"}}}})
  (open-from-library! SRC (k "Tideward"))
  (save!)
  (is (= #{(k "Tideward")} (set (keys (stored)))) "still in the source that holds it")
  (is (nil? (get-in @app-db [:plugins "Name It Declared" ct (k "Tideward")]))
      "and no source was conjured from the stale declaration"))

(deftest a-rename-does-not-fork-a-keyless-item
  ;; Identifying the item by its NAME meant renaming one re-addressed it: a new entry was minted
  ;; from the new name and the original was left behind holding the pre-edit data, with no
  ;; :former-keys to heal it -- exactly the GOTCHA address-for exists to prevent.
  (with-legacy-item!)
  (open-from-library! SRC old-key)
  (set-name! "Seawall")
  (save!)
  (is (= #{old-key} (set (keys (stored)))) "one entry, at the address it already had")
  (is (= "Seawall" (get-in (stored) [old-key :name])) "carrying the new name"))

(deftest a-move-is-refused-when-a-third-library-also-answers
  ;; Emptying the origin does not help when another source already holds the key: the save would
  ;; still leave two entries at one address, which is the state refused everywhere else.
  (open! (draft "Tideward"))
  (save!)
  (swap! app-db assoc-in [:plugins "Third Pak" ct (k "Tideward")]
         {:key (k "Tideward") :name "Somebody Else's" :option-pack "Third Pak"})
  (open-from-library! SRC (k "Tideward"))
  (retarget! OTHER)
  (save!)
  (is (some? (get-in @app-db [:plugins SRC ct (k "Tideward")])) "it stayed where it was")
  (is (nil? (get-in @app-db [:plugins OTHER ct (k "Tideward")])) "and did not arrive")
  (is (empty? (:builder-field-errors @app-db))
      "and the NAME is not flagged — it is the other library, not the name, in the way"))

;; ---------------------------------------------------------------------------
;; Round two of the review (2026-09-20)
;; ---------------------------------------------------------------------------

(deftest an-empty-source-is-a-missing-field-not-an-instruction-to-move
  ;; `::option-pack` is `string?`, so "" satisfies the save spec and never reached the
  ;; missing-field banner. Clearing the box to retype it and pressing Save read as a retarget:
  ;; the item was deleted from its library and re-homed under a source named "", the banner said
  ;; only "saved", and the next save was a clean in-place -- nothing ever flagged it.
  (open! (draft "Tideward"))
  (save!)
  (open-from-library! SRC (k "Tideward"))
  (retarget! "")
  (save!)
  (is (= #{(k "Tideward")} (set (keys (stored)))) "still in the source it came from")
  (is (empty? (get @app-db "")) "and no nameless library was created")
  (is (= :missing (:option-pack (:builder-field-errors @app-db))) "the field is flagged instead"))

(deftest a-source-name-is-trimmed-before-it-is-used-as-an-address
  ;; " Tide Pak" renders identically to "Tide Pak" in My Content, so treating them as different
  ;; libraries moves the item into a twin nobody can tell apart.
  (open! (draft "Tideward"))
  (save!)
  (open-from-library! SRC (k "Tideward"))
  (retarget! (str " " SRC " "))
  (save!)
  (is (= #{SRC} (set (keys (:plugins @app-db)))) "one library, not two")
  (is (= #{(k "Tideward")} (set (keys (stored))))))

(deftest replacing-is-not-offered-when-a-third-library-also-answers
  ;; Consent to discarding the occupant does not resolve a key a THIRD source holds, so offering
  ;; it there takes a decision about one entry and leaves the duplicate standing anyway.
  (open! (draft "Tideward"))
  (save!)
  (doseq [src [OTHER "Third Pak"]]
    (swap! app-db assoc-in [:plugins src ct (k "Tideward")]
           {:key (k "Tideward") :name (str "In " src) :option-pack src}))
  (open-from-library! SRC (k "Tideward"))
  (retarget! OTHER)
  (save!)
  (is (= :elsewhere (:reason (events/save-destination (:plugins @app-db)
                                                      (get-in @app-db [:builder-origin ct])
                                                      ct OTHER (k "Tideward") (in-builder))))
      "refused for the third library, not offered as a replace")
  ;; and consent cannot reach it
  (dispatch! [::langs5e/save-language {:replace? true}])
  (is (= "In Other Pak" (get-in @app-db [:plugins OTHER ct (k "Tideward") :name]))
      "the occupant is untouched")
  (is (some? (get-in (stored) [(k "Tideward")])) "and this one stayed put"))

(deftest working-in-another-builder-does-not-disarm-this-one
  ;; `:builder-origin` was ONE slot shared by all thirteen builders, whose drafts are all live at
  ;; once. New in any builder cleared it globally and edit in any builder overwrote it -- so
  ;; deleting an item, clicking "add feat", and coming back resurrected the deleted item, and a
  ;; move was available only to whichever builder wrote the slot last. One slot per content type.
  (open! (draft "Tideward"))
  (save!)
  (open-from-library! SRC (k "Tideward"))
  ;; ...meanwhile, in the selections builder
  (dispatch! [::selections5e/new-selection OTHER])
  (is (= {:source SRC :key (k "Tideward") :name "Tideward"}
         (get-in @app-db [:builder-origin ct]))
      "the language builder's record is untouched")
  (is (nil? (get-in @app-db [:builder-origin sct])) "and the selection builder's is cleared")
  ;; the move is still available here
  (retarget! OTHER)
  (save!)
  (is (nil? (get-in @app-db [:plugins SRC ct (k "Tideward")])) "it moved, not refused")
  (is (some? (get-in @app-db [:plugins OTHER ct (k "Tideward")]))))

(deftest a-deleted-entry-is-not-resurrected-after-a-detour-through-another-builder
  (open! (draft "Tideward"))
  (save!)
  (open-from-library! SRC (k "Tideward"))
  (dispatch! [::langs5e/delete-language (get-in (stored) [(k "Tideward")]) SRC (k "Tideward")])
  (dispatch! [::selections5e/new-selection OTHER])        ; the detour that used to clear it
  (save!)
  (is (empty? (stored)) "still gone -- the record survived the detour and refused"))

(deftest a-key-change-re-stamps-where-the-item-now-lives
  ;; Left pointing at the old key the record can never verify again, and the next save falls back
  ;; to guessing from the library.
  (open! (draft "Tidewrad"))
  (save!)
  (change-key! "tideward")
  ;; the record carries WHICH item as well as where: the address alone only says something
  ;; still answers there (see `origin-of`)
  (is (= {:source SRC :key :tideward :name "Tidewrad"} (get-in @app-db [:builder-origin ct])))
  (swap! app-db assoc-in [::langs5e/builder-item :description] "edited after the key change")
  (save!)
  (is (= #{:tideward} (set (keys (stored)))) "one entry")
  (is (= "edited after the key change" (get-in (stored) [:tideward :description]))))

;; ---------------------------------------------------------------------------
;; Round three of the review (2026-09-20)
;; ---------------------------------------------------------------------------

(deftest an-item-edited-from-anywhere-but-my-content-still-knows-its-address
  ;; My Content's edit button passes the row's address; the other TEN doors -- the Spells and
  ;; Monsters list pages, and the character-builder pencil for background, race, subrace,
  ;; subclass, feat, invocation, boon and spell -- pass the item alone. They can only do that
  ;; because the read path stamps the address on the way out of :plugins. Without it a key-less
  ;; item edited from any of them minted a fresh key and forked into two entries: the original
  ;; left holding the pre-edit data, no :former-keys, nothing on screen.
  (with-legacy-item!)
  (let [as-read (-> (subs5e/process-plugin-vals (:plugins @app-db)) first (get ct) (get old-key))]
    (is (= old-key (:key as-read)) "the reader stamps the key it lives under")
    (is (= SRC (:option-pack as-read)) "and the source that really holds it")
    ;; what those call sites dispatch: the item, and nothing else. The content type still
    ;; reaches the record, because it is bound at REGISTRATION rather than passed here.
    (dispatch! [::langs5e/edit-language as-read])
    (is (= {:source SRC :key old-key :name "Tideward"} (get-in @app-db [:builder-origin ct]))
        "a door that passes only the item still records a complete address")
    (swap! app-db assoc-in [::langs5e/builder-item :description] "edited from the pencil")
    (save!)
    (is (= #{old-key} (set (keys (stored)))) "one entry, not a fork")
    (is (= "edited from the pencil" (get-in (stored) [old-key :description])))))

(deftest a-stale-declared-source-is-corrected-by-the-reader
  ;; An import that renames a source leaves each item declaring the name it arrived with. Read
  ;; through the same path, the item reports the library that actually holds it.
  (swap! app-db assoc :plugins
         {SRC {ct {(k "Tideward") {:key (k "Tideward") :name "Tideward"
                                   :option-pack "Name It Declared"}}}})
  (let [as-read (-> (subs5e/process-plugin-vals (:plugins @app-db)) first (get ct)
                    (get (k "Tideward")))]
    (is (= SRC (:option-pack as-read)))))

(deftest minting-onto-a-key-another-library-holds-is-not-offered-as-a-replace
  ;; The move branch already refused this; the mint branch had the opposite order and offered
  ;; "Replace it" -- which destroys the entry here and leaves the other library answering anyway,
  ;; so the consent buys nothing it promised.
  (swap! app-db assoc :plugins
         {SRC   {ct {(k "Tideward") {:key (k "Tideward") :name "Mine" :option-pack SRC}}}
          OTHER {ct {(k "Tideward") {:key (k "Tideward") :name "Theirs" :option-pack OTHER}}}})
  (open! (draft "Tideward"))                          ; a NEW item minting that same key
  (save!)
  (is (= :elsewhere (:reason (events/save-destination (:plugins @app-db) nil ct SRC
                                                      (k "Tideward") {:name "Tideward"})))
      "refused for the other library, not offered as a replace")
  (dispatch! [::langs5e/save-language {:replace? true}])
  (is (= "Mine" (get-in (stored) [(k "Tideward") :name])) "and consent cannot reach it"))

;; ---------------------------------------------------------------------------
;; Round five of the review (2026-09-21)
;; ---------------------------------------------------------------------------

(deftest deleting-uses-the-rows-address-not-the-items-own-fields
  ;; `reg-delete-homebrew` had no test at all. Reading `:option-pack`/`:key` off the item deleted
  ;; nothing for a pre-keys library, and where another source answered to the same key it deleted
  ;; THAT entry and left the clicked one in place.
  (with-legacy-item!)                                   ; SRC holds old-key, item carries no :key
  (swap! app-db assoc-in [:plugins OTHER ct old-key]
         {:name "Somebody Else's" :option-pack OTHER})
  (dispatch! [::langs5e/delete-language (get-in @app-db [:plugins SRC ct old-key]) SRC old-key])
  (is (empty? (stored)) "the clicked row is gone")
  (is (= "Somebody Else's" (get-in @app-db [:plugins OTHER ct old-key :name]))
      "and the same-keyed entry in another library is untouched"))

(deftest deleting-something-that-is-not-there-says-so
  ;; The list pages pass only the item, so the fallback runs. A homebrew item that arrived through
  ;; a share link is not in :plugins at all -- that used to be a silent no-op.
  (dispatch! [::langs5e/delete-language {:name "Shared" :option-pack "Not Mine"
                                         :key :shared-nm}])
  (is (empty? (:plugins @app-db)) "nothing written")
  (is (some? (:message @app-db)) "and the author is told rather than left guessing"))

(deftest an-entry-deleted-under-an-open-builder-is-not-silently-resurrected
  ;; Three things write :plugins outside this gate -- delete, Move/copy, and import conflict
  ;; resolution -- and all of them can run while a builder holds the item. Saving afterwards used
  ;; to put it straight back, undoing what the author had just done somewhere else.
  (open! (draft "Tideward"))
  (save!)
  (open-from-library! SRC (k "Tideward"))
  (dispatch! [::langs5e/delete-language (get-in (stored) [(k "Tideward")]) SRC (k "Tideward")])
  (is (empty? (stored)) "gone")
  (save!)
  (is (empty? (stored)) "a plain save does not bring it back")
  ;; ...but the author can say so
  (dispatch! [::langs5e/save-language {:replace? true}])
  (is (= #{(k "Tideward")} (set (keys (stored)))) "on consent it is written again"))

(deftest a-move-says-what-it-removed
  ;; The move is what the author asked for by retyping the source, but deleting the entry they
  ;; came from is not something the banner used to mention at all.
  (open! (draft "Tideward"))
  (save!)
  (open-from-library! SRC (k "Tideward"))
  (retarget! OTHER)
  (save!)
  (let [{:keys [title details]} (:message @app-db)]
    (is (re-find #"moved to" (str title)) "the headline says it moved")
    (is (some #(re-find (re-pattern SRC) (str %)) details)
        "and the detail names the library it was removed from")))

(deftest every-read-path-stamps-the-address
  ;; Two helpers read items out of :plugins and both must stamp, or a door that reads through the
  ;; unstamped one hands the save an item that cannot say where it lives.
  (with-legacy-item!)
  (let [via-vals (-> (subs5e/process-plugin-vals (:plugins @app-db)) first (get ct) (get old-key))]
    (is (= [old-key SRC] [(:key via-vals) (:option-pack via-vals)])))
  (let [[src plugin] (first (subs5e/process-plugins-with-sources (:plugins @app-db)))
        via-sources  (get-in plugin [ct old-key])]
    (is (= [SRC old-key SRC] [src (:key via-sources) (:option-pack via-sources)]))))

;; ---------------------------------------------------------------------------
;; Round six of the review (2026-09-21)
;; ---------------------------------------------------------------------------

(deftest an-entry-REPLACED-under-an-open-builder-is-not-silently-overwritten
  ;; The third case, and the one that got away. :vanished covers an entry deleted or relocated out
  ;; from under an open builder; this is the entry being REPLACED. An import under the same source
  ;; name does not collide -- detect-duplicate-keys deliberately filters same-source collisions
  ;; out -- and does not notify, so `[source key]` still answers and the save read it as in-place
  ;; and wrote over what had just been imported.
  ;;
  ;; The address says something answers there. The NAME says whether it is still the same thing.
  (open! (draft "Tideward"))
  (save!)
  (open-from-library! SRC (k "Tideward"))
  (swap! app-db assoc-in [::langs5e/builder-item :description] "my unsaved edit")
  ;; an import lands a DIFFERENT item at that same address, under the same source name
  (swap! app-db assoc-in [:plugins SRC ct (k "Tideward")]
         {:key (k "Tideward") :name "Somebody Else's" :option-pack SRC})
  (save!)
  (is (= "Somebody Else's" (get-in (stored) [(k "Tideward") :name]))
      "the imported entry is not silently overwritten")
  (is (nil? (get-in (stored) [(k "Tideward") :description])) "and still holds its own data")
  ;; ...and the author can still say they meant it
  (dispatch! [::langs5e/save-language {:replace? true}])
  (is (= "my unsaved edit" (get-in (stored) [(k "Tideward") :description]))
      "on consent, their work lands"))

(deftest changing-a-key-re-keys-the-library-the-item-came-from
  ;; The handler located the entry through the typed Option Source Name field -- the one guess
  ;; this whole rework exists to remove. Retype the source to a library that answers to the same
  ;; key, then change the key, and it re-keyed THAT library's entry and loaded it over the open
  ;; draft, losing every unsaved edit from app-db and the persisted draft alike.
  (open! (draft "Tidewrad"))
  (save!)
  (swap! app-db assoc-in [:plugins OTHER ct (k "Tidewrad")]
         {:key (k "Tidewrad") :name "Somebody Else's" :option-pack OTHER})
  (open-from-library! SRC (k "Tidewrad"))
  (swap! app-db assoc-in [::langs5e/builder-item :description] "my unsaved edit")
  (retarget! OTHER)                                   ; typed, not yet saved
  (change-key! "tideward")
  (is (= "Somebody Else's" (get-in @app-db [:plugins OTHER ct (k "Tidewrad") :name]))
      "the other library's entry is untouched")
  (is (= #{:tideward} (set (keys (stored)))) "this one was re-keyed, in the library it came from")
  (is (= "my unsaved edit" (:description (in-builder))) "and the open draft is still the author's"))

