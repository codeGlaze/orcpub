(ns orcpub.dnd.e5.armor-class
  "Armor Class calculation. NOT YET WIRED INTO THE APP — the live AC code is still
  template_base.cljc (?armor-class-with-armor). Nothing in src/ requires this namespace; it is
  currently exercised only by ac_reconciliation_test and ac_experiments_test.

  Two things contribute to AC, combined differently:
    FORMULA — a whole 'your AC = ...' calculation: worn armor, unarmored defense, natural armor,
      a Barkskin floor, homebrew. Mutually exclusive — the best one wins (max).
    BONUS — a flat +N added on top of whichever formula won: shield, Ring of Protection,
      Defense fighting style.
  So: AC = best formula + sum of bonuses.

  Both are (fn [armor shield] -> number); armor and shield may be nil. A formula or bonus that
  does not apply in the situation returns 0 (an unarmored formula while armor is worn, a
  shield-forbidding formula while a shield is held). 0 is therefore the 'no contribution' value,
  and it works as the seed for both max and +, since no real AC is zero or negative.

  Formulas are supplied in two groups, because they behave differently as you change armor:
    :armor-formula  — AC from the armor being worn. Its value depends on WHICH armor.
    :other-formulas — everything else. Their value must NOT depend on which armor is worn; they
                      may check whether armor is present, but must not read its fields. best-ac
                      relies on this (see below) and a formula that breaks the rule will return a
                      wrong number or throw.

  INTENDED WIRING (none of this is built yet — recorded so the shape is clear):
    - template_base would supply :armor-formula, :other-formulas and :bonuses, replacing the
      scalar accumulators it uses today.
    - best-ac would replace the ::best-armor-combo subscription (subs.cljs:801). That sub is
      memoized, so once wired, AC would recompute when AC-relevant state changes rather than on
      every render.
    - homebrew AC would compile down to these same two groups, so homebrew and built-in content
      would go through one reconciler instead of the several the app has now.")

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
