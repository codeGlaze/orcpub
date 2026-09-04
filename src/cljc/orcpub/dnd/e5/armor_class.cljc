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

  ---------------------------------------------------------------------------------------------
  NOT WIRED: best-ac at the bottom of this file. It is an outer-loop optimisation for
  subs.cljs's \"best AC across every owned armor/shield\" search, and it needs a different config
  shape (calculations split into item-dependent and item-independent groups). That split buys real
  work-saving on stacked characters but carries a footgun — a calculation placed in the wrong group
  returns a wrong number or throws, which ac_experiments_test demonstrates. Left unadopted pending
  a decision; the wired engine above deliberately uses ONE list."
)

(def ^:private magical-ac-bonus :orcpub.dnd.e5.magic-items/magical-ac-bonus)

(defn dex-cap
  "The most Dex this armor allows, or nil for no limit. The MORE PERMISSIVE of what the armor TYPE
  allows and what the item itself declares in :max-dex-mod.

  Taking the max is load-bearing. Every shipped medium armor prints :max-dex-mod 2 and every heavy
  prints 0, so reading the item's field alone would cap scale mail at 2 and silently disable Medium
  Armor Master, which raises the medium cap to 3 via `max-medium`. Taking the max keeps the feat
  working and still lets a custom item be more generous than its type — heavy that allows a Dex
  bonus is expressible."
  [max-medium armor]
  (let [type-cap (case (:type armor) :light nil :medium max-medium 0)
        own-cap  (:max-dex-mod armor)]
    (cond (nil? own-cap)  type-cap
          (nil? type-cap) own-cap
          :else           (max type-cap own-cap))))

(defn armor-dex-bonus
  "How much of `dex` this armor lets you add."
  [dex max-medium armor]
  (if-let [cap (dex-cap max-medium armor)] (min cap dex) dex))

(defn shield-bonus
  "A shield's contribution: its flat 2 plus its own ITEM magic."
  [shield]
  (+ 2 (or (magical-ac-bonus shield) 0)))

(defn worn-armor-ac
  "AC from the armor being worn — base, the Dex it allows, and its own ITEM magic. `nil` armor is
  the unarmored value, 10 + Dex."
  [dex max-medium armor]
  (if armor
    (+ (:base-ac armor) (armor-dex-bonus dex max-medium armor) (magical-ac-bonus armor))
    (+ 10 dex)))

(defn reconcile
  "AC for one specific equipped (armor, shield): the best calculation, plus every bonus.
  `armor-value` is the worn-armor number, which competes with `calculations` like any other."
  [armor-value calculations bonuses armor shield]
  (+ (reduce max armor-value (map #(% armor shield) calculations))
     (reduce +   0           (map #(% armor shield) bonuses))))

;; ── NOT WIRED (see the ns docstring) ─────────────────────────────────────────

(defn reconcile-ac
  "AC for one specific equipped (armor, shield): best formula + sum of bonuses.
  config — {:armor-formula f-or-nil, :other-formulas [f ...], :bonuses [f ...]} (see ns)."
  [{:keys [armor-formula other-formulas bonuses]} armor shield]
  (let [formulas (cond->> other-formulas armor-formula (cons armor-formula))
        run      #(% armor shield)]
    (+ (reduce max 0 (map run formulas))
       (reduce +   0 (map run bonuses)))))

(defn best-ac
  "Highest AC the character can reach with the armor and shields they own, trying every
  combination (including wearing nothing). Same result as calling reconcile-ac on every
  combination and taking the max.

  It avoids repeated work: only :armor-formula changes from one armor to the next, so the best
  of :other-formulas is computed once per (is-armor-worn?, shield) pair rather than once per
  owned armor. That is why other-formulas may not read armor fields — they are evaluated with
  ::worn, a placeholder that is merely non-nil, standing in for 'some armor'."
  [{:keys [armor-formula other-formulas bonuses]} armors shields]
  (let [armors  (cons nil armors)     ; nil = the "wear nothing" option
        shields (cons nil shields)    ; nil = the "no shield" option
        best-other (into {} (for [worn?  [true false]
                                  shield shields]
                              [[worn? shield]
                               (reduce max 0 (map #(% (when worn? ::worn) shield) other-formulas))]))]
    (reduce max 0
            (for [armor  armors
                  shield shields]
              (+ (max (best-other [(some? armor) shield])
                      (if armor-formula (armor-formula armor shield) 0))
                 (reduce + 0 (map #(% armor shield) bonuses)))))))
