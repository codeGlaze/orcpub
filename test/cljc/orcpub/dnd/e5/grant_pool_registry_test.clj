(ns orcpub.dnd.e5.grant-pool-registry-test
  "The pool registry and the grant hook, against the decided design
   (docs/kb/content-extensibility-direction.md, \"The spine\" + \"Maintainability\").

   The gating test is `registering-a-pool-is-one-entry`. The direction doc makes it the pass/fail
   criterion for the whole retooling:

     \"exposing a SECOND pool in a builder must be a ~1-line registration — shown in a commit. If it
      isn't trivially cheap, the retooling failed its own purpose; STOP and reassess.\"

   Everything else here pins the two disciplines that keep it from rotting: `grant` stays a thin
   compiler (no pool-kind branches), and each pool's own definition absorbs its own irregularity.
   JVM/clojure.test."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.template :as t]
            [orcpub.dnd.e5.options :as opt5e]
            [orcpub.dnd.e5.grant-pools :as gp]))

(def ^:private no-plugins [])

(deftest registering-a-pool-is-one-entry
  (testing "every registered pool assembles with no code outside its own entry"
    (let [assembled (gp/assemble no-plugins)]
      (is (= (set (keys gp/pools)) (set (keys assembled)))
          "assemble must be uniform — it never inspects or skips a pool")
      (doseq [[k {:keys [name options]}] assembled]
        (is (string? name) (str k " has no display name"))
        (is (seq options) (str k " assembled to no options"))
        (is (every? ::t/key options)
            (str k " has options with no ::t/key — :key and :filter address entries by it")))))
  (testing "and the three pools registered so far differ ONLY in their own :options-fn"
    (is (= #{:languages :fighting-styles :skills} (set (keys gp/pools))))
    (doseq [[k d] gp/pools]
      (is (fn? (:options-fn d)) (str k " must own its shape as a fn, not describe it with keys"))
      (is (set? (:offerable-by d)) (str k " is missing its scoping metadata")))))

(deftest each-pool-absorbs-its-own-irregularity
  (testing "the pools disagree about what a built-in is; nothing outside them can tell"
    (let [a (gp/assemble no-plugins)]
      ;; languages: built-ins are RAW data run through a constructor
      (is (contains? (set (map ::t/key (get-in a [:languages :options]))) :deep-speech))
      ;; fighting styles: built-ins are ALREADY option-cfgs, constructor applies to homebrew only
      (is (contains? (set (map ::t/name (get-in a [:fighting-styles :options]))) "Archery"))
      ;; skills: closed — no homebrew half at all, and it ignores plugin-vals.
      ;; Compared by ::t/key: an option carries closures (prereq-fn, modifier fns) that are fresh
      ;; objects on every call, so the cfgs themselves never compare equal.
      (is (= (map ::t/key (get-in a [:skills :options]))
             (map ::t/key (get-in (gp/assemble [{:orcpub.dnd.e5/spells {:x {}}}])
                                  [:skills :options])))
          "a closed pool must not vary with plugin content"))))

(defn- sel [grant]
  (opt5e/grant-selection grant (gp/assemble no-plugins)))

(deftest decided-vocabulary
  (testing ":pool / :count is the canonical spelling"
    (let [s (sel {:pool :languages :count 2})]
      (is (= 2 (::t/min s)))
      (is (= 2 (::t/max s)))))
  (testing ":key forces a single creator-chosen entry"
    (let [s (sel {:pool :languages :key :elvish})]
      (is (= 1 (::t/min s)))
      (is (= [:elvish] (map ::t/key (::t/options s))))))
  (testing ":filter offers a creator-chosen subset"
    (is (= #{:elvish :dwarvish}
           (set (map ::t/key (::t/options (sel {:pool :languages :count 1
                                                :filter #{:elvish :dwarvish}}))))))))

(deftest prototype-spelling-still-reads
  (testing ":from / :choose are aliases, so branch-local prototype data keeps working"
    (let [s (sel {:from :fighting-styles :choose 2})]
      (is (= 2 (::t/max s)))
      (is (= "Fighting Style" (::t/name s))))))

(deftest filtering-is-graceful-never-an-error
  (testing "a filter naming nothing present yields an empty offer, not a throw (the direction doc's
            'absent metadata -> simply isn't offered' rule)"
    (is (empty? (::t/options (sel {:pool :languages :filter #{:klingon}})))))
  (testing "an unregistered pool yields no selection at all"
    (is (nil? (sel {:pool :not-a-pool :count 1})))))

(deftest count-defaults-to-one
  (is (= 1 (::t/max (sel {:pool :skills})))))
