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

   The formulas split into two groups, because they behave differently as you swap armor:
     - the ARMOR formula: the AC you get from the armor you're wearing (base AC + capped Dex +
       the armor's own magic). This is the ONLY formula whose value changes with which armor.
     - the OTHER formulas: every other way to get AC (unarmored defense, natural armor, floors,
       homebrew). These give the same number no matter which armor is in your pack.

   A formula or bonus is a plain function (fn [armor shield] -> number); armor and shield may be
   nil. This namespace is pure — template_base feeds it the character's formulas and bonuses, and
   homebrew AC compiles down to the same shape, so there is ONE reconciler, not a path per source.")

(defn reconcile-ac
  "The AC for ONE specific equipped (armor, shield): the best formula, plus every bonus.

   config = {:armor-formula  (fn [armor shield] n)     ; AC from the worn armor (may be nil, e.g. unarmored builds)
             :other-formulas [(fn [armor shield] n) …] ; unarmored defense, natural armor, floors, homebrew
             :bonuses        [(fn [armor shield] n) …]} ; flat +N additions, summed onto the winner

   A formula/bonus that doesn't apply in this situation returns 0, so only the ones that fit compete."
  [{:keys [armor-formula other-formulas bonuses]} armor shield]
  (let [in-situation (fn [f] (f armor shield))          ; run one formula/bonus for this armor+shield
        formulas     (cond->> other-formulas
                       armor-formula (cons armor-formula)) ; the armor formula competes alongside the others
        best-formula (reduce max 0 (map in-situation formulas)) ; the highest AC any formula gives here
        total-bonus  (reduce +   0 (map in-situation bonuses))] ; every applicable bonus, added up
    (+ best-formula total-bonus)))

(defn best-ac
  "The BEST AC across every armor + shield the character owns — they'll wear whatever gives the
   most. This is what the character sheet shows.

   It is equivalent to running reconcile-ac for every (armor, shield) pair and taking the max, but
   it skips the wasted work: only the ARMOR formula changes with which armor you wear. The OTHER
   formulas ('unarmored 13 + Dex', a floor, a homebrew AC) give the same number no matter what's in
   your pack, so their best is worked out ONCE per (wearing-armor?, shield) situation instead of
   being re-run for every owned armor.

   config = {:armor-formula (fn [armor shield] n)  :other-formulas [(fn …) …]  :bonuses [(fn …) …]}

   Example — a monk who owns 8 armors but fights unarmored: '10 + Dex + Wis' is computed once, not
   eight times; only the armor formula is evaluated against each of the 8 armors."
  [{:keys [armor-formula other-formulas bonuses]} armors shields]
  (let [armor-options  (cons nil armors)     ; each owned armor, plus the "no armor" option
        shield-options (cons nil shields)     ; each owned shield, plus the "no shield" option

        ;; The other formulas only change with two things: are we wearing armor at all, and which
        ;; shield. Work out their best AC for each such situation ONCE. ::worn is a stand-in for "some
        ;; armor" — the other formulas only ask "am I wearing armor?", they never look at which one.
        best-of-others
        (into {} (for [wearing? [true false]
                       shield   shield-options]
                   [[wearing? shield]
                    (reduce max 0 (map #(% (when wearing? ::worn) shield) other-formulas))]))]

    ;; Try every real (armor, shield). For each: the better of {the best of the other formulas} and
    ;; {the armor formula for THIS specific armor}, plus the bonuses for this combination.
    (reduce max 0
            (for [armor  armor-options
                  shield shield-options]
              (let [others (best-of-others [(some? armor) shield])
                    worn   (if armor-formula (armor-formula armor shield) 0)
                    bonus  (reduce + 0 (map #(% armor shield) bonuses))]
                (+ (max others worn) bonus))))))
