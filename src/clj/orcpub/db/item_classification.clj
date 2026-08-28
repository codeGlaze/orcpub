(ns orcpub.db.item-classification
  "Backfill of :orcpub.dnd.e5.magic-items/magical? across stored custom items.

   Custom items predate any way of saying whether they were magical, so all of
   them — homemade rope and vorpal swords alike — were filed as magic items.
   `orcpub.dnd.e5.magic-items/classify` works the answer out from what an item
   already contains; this namespace writes that answer down for the items it
   can be sure about, so the app stops re-deriving it on every page load.

   Three properties keep this safe to run against a decade of user content:

   * It is ADDITIVE. The only datoms it writes are assertions of a brand new
     attribute. Nothing is retracted, no existing attribute is read-modify-
     written, and an item that already carries the flag is never touched.
   * It is IDEMPOTENT. Only items with no value for the attribute are
     selected, so re-running it is a no-op and it can live on the boot path.
   * It DOES NOT GUESS. Items `classify` cannot place from evidence
     (:unreviewed) are deliberately left alone. They keep behaving exactly as
     they always have — as magic items — and the item builder asks their owner
     instead. Silence is a better outcome than a confident wrong answer that
     nobody knows to go back and check.

   A backfilled value is not separately traceable — ::magical? is :db/noHistory
   like every other item attribute — which is why the backfill refuses to guess
   in the first place rather than relying on being able to undo a bad guess."
  (:require [datomic.api :as d]
            [orcpub.dnd.e5.magic-items :as mi5e]))

(def ^:private batch-size
  "Datomic transactions are all-or-nothing, so a decade of items goes in
   chunks: a single bad datom fails one batch instead of the whole backfill."
  500)

(defn unclassified-items
  "Every stored custom item with no ::mi5e/magical? value yet.

   Ownership is the marker for a user-built item — routes/save-item stamps
   ::mi5e/owner on save, and the static SRD items live in code, not the db."
  [db]
  ;; Pull only what classify reads, not [*]. This query materialises every
  ;; unclassified item in one seq before anything is written, and [*] drags
  ;; each item's description and its nested modifier entities along with it --
  ;; a decade of content, in peer heap, inside component/start. The chunking
  ;; below is on the write side and does nothing about that.
  ;;
  ;; :unreviewed items never gain the attribute, so this runs on every boot
  ;; forever, not just the first.
  (map
   first
   (d/q '[:find (pull ?e [:db/id
                          :orcpub.dnd.e5.magic-items/type
                          :orcpub.dnd.e5.magic-items/rarity
                          :orcpub.dnd.e5.magic-items/owner
                          :orcpub.dnd.e5.magic-items/attunement
                          :orcpub.dnd.e5.magic-items/magical-attack-bonus
                          :orcpub.dnd.e5.magic-items/magical-damage-bonus
                          :orcpub.dnd.e5.magic-items/magical-ac-bonus
                          {:orcpub.dnd.e5.magic-items/modifiers [:db/id]}])
          :where
          [?e :orcpub.dnd.e5.magic-items/owner _]
          (not [?e :orcpub.dnd.e5.magic-items/magical? _])]
        db)))

(defn classification-tx
  "Pure. Given pulled items, the assertions that record their classification.

   Contributes nothing for an item that already carries the flag (its owner's
   answer is not up for review, and re-asserting it would churn history), nor
   for one classify calls :unreviewed, nor for one with no :db/id to assert
   against. Everything this returns is therefore a first-time assertion, which
   is what makes re-running the backfill a no-op."
  [items]
  (into
   []
   (keep
    (fn [{:keys [:db/id] :as item}]
      (when (and id (not (contains? item mi5e/magical-key)))
        (let [classified (mi5e/ensure-classified item)]
          (when (contains? classified mi5e/magical-key)
            {:db/id id
             mi5e/magical-key (get classified mi5e/magical-key)})))))
   items))

(defn backfill-report
  "Pure. What a backfill over `items` would do, without touching the db."
  [items]
  (let [tx (classification-tx items)
        magical (count (filter mi5e/magical-key tx))]
    {:examined (count items)
     :classified (count tx)
     :magical magical
     :mundane (- (count tx) magical)
     ;; Items nobody can classify from evidence. They keep behaving as magic
     ;; items and the item builder asks their owner — they are the residue the
     ;; backfill deliberately does not touch, not a failure.
     :left-unreviewed (count (filter mi5e/unreviewed? items))
     :tx tx}))

(defn backfill!
  "Record the classification of every stored custom item we can be sure about.

   Safe to call on every boot: it selects only items with no value yet, so the
   first run does the work and later runs transact nothing. Returns the report
   map. Never throws — a failed backfill is a cosmetic problem, and it must not
   be able to stop the server from starting."
  [conn]
  (try
    (let [{:keys [tx] :as report} (backfill-report (unclassified-items (d/db conn)))]
      (doseq [batch (partition-all batch-size tx)]
        @(d/transact conn (vec batch)))
      (when (pos? (:classified report))
        (println (format "Classified %d custom items (%d magical, %d mundane); %d left for their owners to confirm"
                         (:classified report)
                         (:magical report)
                         (:mundane report)
                         (:left-unreviewed report))))
      (dissoc report :tx))
    ;; Throwable, not Exception. This runs inside component/start, and the
    ;; failure most likely on a large corpus -- OutOfMemoryError from
    ;; materialising the query above -- is an Error. Catching only Exception
    ;; let it escape into datomic/start, which rethrows it as
    ;; :schema-initialization-failed, so a backfill that is meant to be
    ;; optional would stop the server booting at all.
    (catch Throwable t
      (println "WARNING: custom item classification backfill failed, items keep their existing behaviour:"
               (.getMessage t))
      {:error (.getMessage t)})))
