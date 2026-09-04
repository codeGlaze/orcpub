(ns orcpub.dnd.e5.armor-class
  "Armor Class. Two things contribute, combined differently:
     FORMULA — a whole 'your AC = ...' calculation (worn armor, unarmored defense, natural armor,
       a Barkskin floor, homebrew). Mutually exclusive: the best one wins (max). One that doesn't
       apply in the situation returns 0.
     BONUS — a flat +N stacked on the winning formula (shield, Ring of Protection, Defense style).
   AC = best formula + sum of bonuses. Bonuses land on the winner, not on a particular formula.

   Formulas split by how they behave as you swap armor: the ARMOR formula's value depends on which
   armor is worn; the OTHER formulas (unarmored defense, natural armor, floors, homebrew) do not.
   Each is (fn [armor shield] -> number); armor/shield may be nil. Pure — template_base supplies the
   formulas and bonuses, and homebrew AC compiles to the same shape, so there's one reconciler.")

(defn reconcile-ac
  "AC for one equipped (armor, shield): best formula + sum of bonuses.
   config — :armor-formula (or nil), :other-formulas, :bonuses (see ns). Inapplicable formula/bonus → 0."
  [{:keys [armor-formula other-formulas bonuses]} armor shield]
  (let [formulas (cond->> other-formulas armor-formula (cons armor-formula))
        run      #(% armor shield)]
    (+ (reduce max 0 (map run formulas))
       (reduce +   0 (map run bonuses)))))

(defn best-ac
  "Best AC across the armor + shields the character owns (they wear whatever gives the most) — the
   number the sheet shows. Runs inside a memoized subscription, so it fires only when AC-relevant
   state changes, not per render.

   Only the armor formula's value depends on which armor is worn, so the other formulas' best is
   found once per (wearing-armor?, shield) instead of once per owned armor. config as reconcile-ac."
  [{:keys [armor-formula other-formulas bonuses]} armors shields]
  (let [armors  (cons nil armors)     ; include the "no armor" option
        shields (cons nil shields)    ; include the "no shield" option
        ;; ::worn stands for any armor — the other formulas only ask whether armor is worn, not which.
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
