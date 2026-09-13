(ns orcpub.dnd.e5.armor-class
  "The Armor Class engine. `template_base` declares the ?-attributes — they are entity-spec macros
  and only work inside es/make-entity — and delegates the arithmetic here.

  THE MODEL
    AC = max(worn-armor value, every registered calculation) + sum(every registered bonus)

  A CALCULATION is a whole \"your AC = ...\": Unarmored Defense, natural armor, a Barkskin floor,
  homebrew. They compete; the best one wins; they never stack. Content registers them with
  mod5e/ac-formula, which appends to ?ac-fns.

  A BONUS is a flat +N that applies to whichever calculation won: a shield, Ring of Protection,
  the Defense fighting style. Content registers them with mod5e/ac-bonus-fn, which appends to
  ?ac-bonus-fns.

  Both are (fn [armor shield] -> number), either may be nil, and a contributor that does not apply
  in the situation returns 0. That makes 0 the \"no contribution\" value for both max and +, which
  is safe because no real AC is zero or negative.

  Deciding which one a feature is: does it replace how AC is computed, or add to the result?
  \"Your AC equals 13 + Dex\" is a calculation. \"+1 to AC\" is a bonus.

  TWO KINDS OF MAGIC, which the names below keep apart:
    ITEM magic  — ::magical-ac-bonus ON a worn armor or shield. Part of that item's own value.
    CHARACTER magic — Ring/Cloak of Protection. A bonus on the winner, registered like any other.

  ONE LIST, deliberately. A bucketed variant that splits calculations into item-dependent and
  item-independent groups was measured and rejected — it is SLOWER at realistic character sizes and
  only pays off past ~8 armors with ~20 calculations, while requiring authors to group correctly or
  get a wrong number. See ac_outer_loop_analysis_test."
)

(def ^:private magical-ac-bonus :orcpub.dnd.e5.magic-items/magical-ac-bonus)

(defn dex-cap
  "The most Dex this armor allows, or nil for no limit. The MORE PERMISSIVE of what the armor TYPE
  allows and what the item itself declares in :max-dex-mod.

  `caps` is {armor-type -> cap-or-nil}; nil means uncapped, and a type absent from the map is
  treated as 0. Defaults are {:light nil :medium 2 :heavy 0}, and features raise entries in it —
  Medium Armor Master is `{:medium 3}`. Per-type rather than a lone medium scalar, so a
  heavy armor that allows 2 Dex is expressible too; nothing about the rule is specific to medium.

  Taking the max is load-bearing. Every shipped medium armor prints :max-dex-mod 2 and every heavy
  prints 0, so reading the item's field alone would cap scale mail at 2 and silently disable Medium
  Armor Master. Taking the max keeps the feat working and still lets a custom item be more generous
  than its type."
  [caps armor]
  (let [t        (:type armor)
        type-cap (if (contains? caps t) (get caps t) 0)
        own-cap  (:max-dex-mod armor)]
    (cond (nil? own-cap)  type-cap
          (nil? type-cap) own-cap
          :else           (max type-cap own-cap))))

(defn raise-dex-cap
  "Raise one armor type's cap, never lower it. An already-uncapped type (nil) stays uncapped."
  [caps armor-type cap]
  (update caps armor-type (fn [cur] (if (nil? cur) nil (max (or cur 0) cap)))))

(defn armor-dex-bonus
  "How much of `dex` this armor lets you add."
  [dex caps armor]
  (if-let [cap (dex-cap caps armor)] (min cap dex) dex))

(defn shield-bonus
  "A shield's contribution: its flat 2 plus its own ITEM magic."
  [shield]
  (+ 2 (or (magical-ac-bonus shield) 0)))

(defn worn-armor-ac
  "AC from the armor being worn — base, the Dex it allows, and its own ITEM magic. `nil` armor is
  the unarmored value, 10 + Dex."
  [dex caps armor]
  (if armor
    (+ (:base-ac armor) (armor-dex-bonus dex caps armor) (magical-ac-bonus armor))
    (+ 10 dex)))

(defn reconcile
  "AC for one specific equipped (armor, shield): the best calculation, plus every bonus.
  `armor-value` is the worn-armor number, which competes with `calculations` like any other."
  [armor-value calculations bonuses armor shield]
  (+ (reduce max armor-value (map #(% armor shield) calculations))
     (reduce +   0           (map #(% armor shield) bonuses))))
