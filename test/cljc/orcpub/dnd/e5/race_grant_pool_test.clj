(ns orcpub.dnd.e5.race-grant-pool-test
  "The generic grant hook, second silo. `grant-selection` was written pool-agnostic and
   owner-agnostic but wired to ONE assembly fn (feats) with ONE pool registered
   (:fighting-styles). This pins the second wire: race-option takes the same registry, a race's
   `:grant` data produces a choice from any pool in it, and :languages is registered.

   Both verbs go through the one hook:
     {:from :languages :choose 2}          -> the SELECT verb (user picks 2 of the pool)
     {:from :languages :key :elvish}       -> the GRANT verb (creator picked; forced single option)

   Also pins that the pre-existing `:profs :language-options` path is untouched — the point of the
   wire is that one shape can replace several, not that any saved shape stops working (D9).
   See docs/kb/feat-builder-audit.md. JVM/clojure.test."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.template :as t]
            [orcpub.dnd.e5.options :as opt5e]))

;; The registry template-selections builds, reproduced from the built-in languages so the test
;; does not need the app's subs.
(def ^:private languages
  [{:name "Common" :key :common} {:name "Elvish" :key :elvish}
   {:name "Dwarvish" :key :dwarvish} {:name "Deep Speech" :key :deep-speech}])

(def ^:private language-map (into {} (map (juxt :key identity)) languages))

(def ^:private pools
  {:languages {:name "Language" :options (map opt5e/language-option languages)}})

(defn- race-with [extra]
  (opt5e/race-option nil nil language-map nil pools
                     (merge {:name "Testfolk" :key :testfolk} extra)))

(defn- selection-named [option nm]
  (first (filter #(= nm (::t/name %)) (::t/selections option))))

(deftest language-option-carries-its-own-key
  (testing "grant-selection addresses pool entries by ::t/key, so the option must carry the
            language's key rather than one derived from its display name"
    (is (= :deep-speech (::t/key (opt5e/language-option {:name "Deep Speech" :key :deep-speech}))))
    (testing "and every built-in's derived key already matched, so saved characters are unaffected"
      (doseq [{:keys [name key]} languages]
        (is (= key (orcpub.common/name-to-kw name))
            (str name " derives a different key than it stores"))))))

(deftest select-verb-through-the-hook
  (let [sel (selection-named (race-with {:grant {:from :languages :choose 2}}) "Language")]
    (testing "a race's :grant produces a choice from the registered pool"
      (is (some? sel) "no selection was produced")
      (is (= 2 (::t/min sel)))
      (is (= 2 (::t/max sel)))
      (is (= #{:common :elvish :dwarvish :deep-speech}
             (set (map ::t/key (::t/options sel))))
          "the whole pool should be on offer"))))

(deftest grant-verb-through-the-same-hook
  (let [sel (selection-named (race-with {:grant {:from :languages :key :elvish}}) "Language")]
    (testing ":key narrows the same hook to a creator-chosen entry"
      (is (some? sel))
      (is (= 1 (::t/min sel)) "a forced grant is a single-option choice")
      (is (= [:elvish] (map ::t/key (::t/options sel)))))))

(deftest filter-verb-through-the-same-hook
  (let [sel (selection-named (race-with {:grant {:from :languages :choose 1
                                                 :filter #{:elvish :dwarvish}}})
                             "Language")]
    (testing ":filter offers a creator-chosen subset"
      (is (= #{:elvish :dwarvish} (set (map ::t/key (::t/options sel))))))))

(deftest unknown-pool-is-inert-not-fatal
  (testing "a :grant naming a pool that is not registered yields no selection and does not throw"
    (is (nil? (selection-named (race-with {:grant {:from :nope :choose 1}}) "Language")))))

(deftest no-grant-means-no-selection
  (testing "races without :grant are unchanged"
    (is (empty? (::t/selections (race-with {}))))))

(deftest legacy-language-options-path-untouched
  (testing "the pre-existing :profs :language-options shape still compiles (D9)"
    (let [sel (selection-named (race-with {:profs {:language-options
                                                   {:choose 2 :options {:elvish true
                                                                        :dwarvish true}}}})
                               "Languages")]
      (is (some? sel) "the legacy path stopped producing a selection")
      (is (= 2 (::t/max sel)))
      (is (= #{:elvish :dwarvish} (set (map ::t/key (::t/options sel))))))))
