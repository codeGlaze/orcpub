(ns orcpub.dnd.e5.armor-class
  "How a character's Armor Class is computed. Two kinds of thing contribute to AC, and they
   combine differently:

     FORMULA — a complete way to compute your AC: 'unarmored 10 + Dex + Con', 'natural 13 + Dex',
       'this plate is 18', a Barkskin floor of 16. Formulas are mutually exclusive — you use the
       BEST one, never several at once (reconciled by max). A formula returns 0 for a situation it
       doesn't apply to (an unarmored formula while armor is worn; a shield-forbidding formula while
       a shield is held), so it simply drops out of the max on its own.

     BONUS — a flat '+N' that stacks on top of whichever formula won: a shield's +2, a Ring of
       Protection, the Defense fighting style. Bonuses are summed and added to the winning formula,
       so they always land on the AC you actually use (the old engine buried them in the base
       formula, so a formula that beat the base dropped them — this doesn't).

   In one line:   AC = (best applicable FORMULA) + (sum of applicable BONUSES).

   A formula or bonus is a plain function (fn [armor shield] -> number), evaluated for a given
   equipped armor + shield (either may be nil). This namespace is pure and knows nothing about the
   app: template_base feeds it the character's formulas and bonuses, and homebrew AC compiles down
   to the same two lists — so there is ONE reconciler, not a special path per source.")

(defn reconcile-ac
  "The AC for ONE specific equipped (armor, shield): the best formula, plus every bonus.

   config = {:formulas [(fn [armor shield] n) ...]   ; the 'your AC = ...' calculations (max wins)
             :bonuses  [(fn [armor shield] n) ...]}  ; the '+N' additions (summed onto the winner)

   Callers always include the built-in worn-armor formula, so the result is never below 10. A
   formula/bonus that doesn't apply in this situation returns 0 and thus contributes nothing."
  [{:keys [formulas bonuses]} armor shield]
  (let [in-situation (fn [f] (f armor shield))                 ; run one formula/bonus for this armor+shield
        best-formula (reduce max 0 (map in-situation formulas)) ; the highest AC any formula gives here
        total-bonus  (reduce +   0 (map in-situation bonuses))] ; every applicable bonus, added up
    (+ best-formula total-bonus)))

(defn best-ac
  "The BEST AC across every armor + shield the character owns — they'll wear whatever gives the
   most. This is what the character sheet shows.

   It is equivalent to running reconcile-ac for every (armor, shield) pair and taking the max, but
   it skips the wasted work: most formulas ('unarmored 13 + Dex', a floor, a homebrew AC) don't
   care WHICH armor is in your pack, only whether you're wearing any at all. Those armor-blind
   formulas are worked out ONCE per (wearing-armor?, shield) situation, instead of re-run for every
   owned armor. Only formulas flagged :reads-armor? (in practice just the built-in worn-armor
   formula) are tried against each armor.

   config = {:formulas [{:reads-armor? bool  :fn (fn [armor shield] n)} ...]
             :bonuses  [(fn [armor shield] n) ...]}

   Example — a monk who owns 8 armors but fights unarmored: '10 + Dex + Wis' is computed once, not
   eight times; only the worn-armor formula is evaluated against each of the 8 armors."
  [{:keys [formulas bonuses]} armors shields]
  (let [armor-blind (remove :reads-armor? formulas)  ; formulas that ignore which armor is worn
        armor-aware (filter :reads-armor? formulas)  ; the worn-armor formula(s) that read the item
        armor-opts  (cons nil armors)                ; each owned armor, plus the "no armor" option
        shield-opts (cons nil shields)               ; each owned shield, plus the "no shield" option

        ;; The armor-blind formulas can only change value with two things: are we wearing armor at
        ;; all, and which shield. Pre-compute their best AC for each such situation ONCE. ::worn is a
        ;; stand-in armor — these formulas only test "is armor present", they never read its fields.
        best-armor-blind
        (into {}
              (for [wearing? [true false]
                    shield   shield-opts]
                (let [armor (when wearing? ::worn)]
                  [[wearing? shield]
                   (reduce max 0 (map #((:fn %) armor shield) armor-blind))])))]

    ;; Try every real (armor, shield). For each: the better of {the pre-computed armor-blind best}
    ;; and {the worn-armor formula for THIS specific armor}, plus the bonuses for this combination.
    (reduce max 0
            (for [armor  armor-opts
                  shield shield-opts]
              (let [blind (best-armor-blind [(some? armor) shield])
                    aware (reduce max 0 (map #((:fn %) armor shield) armor-aware))
                    bonus (reduce +   0 (map #(% armor shield) bonuses))]
                (+ (max blind aware) bonus))))))
