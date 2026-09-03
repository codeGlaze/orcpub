(ns orcpub.dnd.e5.armor-class
  "Pure AC reconciliation core — the whole model in one function, no app plumbing.

   An AC is the BEST applicable METHOD ('your AC = ...') plus every applicable BONUS ('+N'):

   - method : (fn [armor shield] number) — a full base formula (worn armor, unarmored defense,
     natural armor, mage armor, set-AC/floor). Returns 0 when it does not apply — e.g. an
     unarmored method while armor is worn, or a shield-forbidding method (Monk) while a shield
     is held. Competing methods reconcile by MAX: you take the better, never stack. A floor
     (Barkskin, 'AC can't be less than 16') is just a constant method — max gives the floor free.

   - bonus  : (fn [armor shield] number) — shield, magic-item AC, Ring/Cloak of Protection,
     Defense style, Mariner, etc. Bonuses are SUMMED and applied to the WINNING method, so they
     reach whatever method wins. (The old engine buried the universal bonuses inside the base
     formula, so a method that beat the base dropped them — see ac_reconciliation_test B2.)

   This is the ONE reconciler (no parallel engine): template_base feeds it the channel contents,
   and the test file iterates it directly with synthetic method/bonus lists.")

(defn reconcile-ac
  "AC for a specific equipped (armor, shield): the best method + the sum of the bonuses.
   `config` = {:methods [(fn [armor shield] n) ...] :bonuses [(fn [armor shield] n) ...]}.
   Callers guarantee at least the SRD base method is present (so the result is never < 10)."
  [{:keys [methods bonuses]} armor shield]
  (+ (transduce (map #(% armor shield)) max 0 methods)     ; winning method (0 if the list is empty)
     (transduce (map #(% armor shield)) +   0 bonuses)))   ; every applicable bonus, on the winner
