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

(defn- change-key! [new-key]
  (dispatch! [::e5/change-builder-item-key ::langs5e/save-language new-key]))

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
  (change-key! :tideward)
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
  (change-key! :tideward)
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
;; Moving an item between sources — Option Source Name is the instruction
;; ---------------------------------------------------------------------------

(def ^:private OTHER "Other Pak")

(defn- open-from-library!
  "Open a stored item the way My Content's edit button does, so the builder records where it came
   from."
  [source k]
  (dispatch! [::langs5e/edit-language (get-in @app-db [:plugins source ct k])]))

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
  (is (= :invalid (:name (:builder-field-errors @app-db))) "with the reason on the form"))

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

(deftest a-restored-draft-still-moves-when-its-origin-is-unambiguous
  ;; After a refresh there is no recorded origin. One source holds the key, so that is where it
  ;; came from.
  (open! (draft "Tideward"))
  (save!)
  (swap! app-db dissoc :builder-origin)              ; what a page reload leaves behind
  (retarget! OTHER)
  (save!)
  (is (nil? (get-in @app-db [:plugins SRC ct (k "Tideward")])) "still a move, not a copy")
  (is (some? (get-in @app-db [:plugins OTHER ct (k "Tideward")]))))

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

    (testing "the record is for a different item that happens to share the key"
      ;; two content types in one source share a key for one name; the record must not send a save
      ;; off to delete an entry nobody opened
      (is (= :in-place (dest (lib B :stone-elf "Stone Elf") {:source A :key :stone-elf}
                             B :stone-elf item))))

    (testing "no record at all: the one source holding the key is where it came from"
      (is (= :move (dest (lib A :stone-elf "Stone Elf") nil B :stone-elf item))))

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
              (get-in @app-db [:plugins SRC sct (k "Fighting Style")])])
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
