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
            [orcpub.common :as common]
            [orcpub.dnd.e5.content-reconciliation :as reconcile]
            ;; Side effect: registers every event handler under test.
            [orcpub.dnd.e5.events]
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
  "Put `item` in the builder, the way every path into the form does."
  [item]
  (swap! app-db assoc ::langs5e/builder-item item))

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
  "What the control sends: the raw string an author typed, not a keyword."
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
  (change-key! :something-else)
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
;; Two ways a save can destroy something, both found in review
;; ---------------------------------------------------------------------------

(deftest retargeting-the-source-must-not-destroy-the-item-sitting-there
  ;; Option Source Name is a free-text field, so a save can land in a library the item was not in.
  (swap! app-db assoc :plugins
         {SRC {ct {(k "Tideward") (assoc (draft "Tideward") :key (k "Tideward"))}}
          "Someone Else's Pak" {ct {(k "Tideward") {:key (k "Tideward") :name "Somebody Else's"
                                                     :option-pack "Someone Else's Pak"}}}})
  ;; the author edits their item and changes Option Source Name to the other library
  (open! (assoc (get-in @app-db [:plugins SRC ct (k "Tideward")])
                :option-pack "Someone Else's Pak"))
  (save!)
  (is (= "Somebody Else's"
         (get-in @app-db [:plugins "Someone Else's Pak" ct (k "Tideward") :name]))
      "the item already in that source is not silently replaced"))

(deftest a-stored-item-with-no-key-keeps-its-address
  ;; :key is optional on a stored item and older libraries do not have it — the read path derives
  ;; it from the name. Minting a tagged key on such an item's next save would leave the original
  ;; behind holding the pre-edit data, with no :former-keys to heal it.
  (swap! app-db assoc :plugins {SRC {ct {:tideward {:name "Tideward" :option-pack SRC}}}})
  (open! (assoc (get-in @app-db [:plugins SRC ct :tideward]) :description "edited"))
  (save!)
  (is (= #{:tideward} (set (keys (stored)))) "one entry, still at the address it had")
  (is (= "edited" (get-in (stored) [:tideward :description]))))

(deftest a-key-the-author-types-has-to-start-with-a-letter
  ;; This is the only place in the app that sets a key by hand. A key that does not start with a
  ;; letter is a keyword trap: the import pipeline quarantines content carrying one.
  (open! (draft "Tideward"))
  (save!)
  (doseq [junk ["" "   " "@@@" "123"]]
    (change-key! junk)
    (is (= #{(k "Tideward")} (set (keys (stored)))) (str "refused: " (pr-str junk)))
    (is (= (k "Tideward") (:key (in-builder))))))
