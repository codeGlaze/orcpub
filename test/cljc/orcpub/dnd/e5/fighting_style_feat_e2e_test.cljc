(ns orcpub.dnd.e5.fighting-style-feat-e2e-test
  "BRIDGE EXPERIMENT (maintainer-approved 'fighting style feat'): does the draconic
   pool+grant pattern generalize to a DIFFERENT bucket — a feat granting a fighting
   style choice from a built-in ++ homebrew pool, expressed as DATA?

   The decisive question is the COMPILE: a homebrew feat whose data says
   `:fighting-style {:choose 1}` must produce a Fighting Style choice that offers
   both built-in styles AND a homebrew style. (entity/build applying a chosen
   option's modifier is already proven elsewhere — draconic/divine-soul tests — so
   the new thing to prove is the data->grant+pool compile.)

   JVM/clojure.test so it runs under the enforced `lein test` gate."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.entity :as entity]
            [orcpub.template :as t]
            [orcpub.dnd.e5.template :as t5e]
            [orcpub.dnd.e5.options :as opt5e]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.dnd.e5.spell-lists :as sl5e]
            [orcpub.dnd.e5.weapons :as weapons5e]
            [orcpub.common :as common]))

(def spell-lists sl5e/spell-lists)
(def spells-map spells5e/spell-map)
(def language-map (common/map-by-key [{:name "Common" :key :common}]))
(def race-map {})

;; A HOMEBREW fighting style authored as DATA: name + a :props mechanic (swimming-speed
;; is in the shared make-feat-modifiers vocabulary) + a description. Compiled the same way
;; a homebrew draconic ancestry is.
(def homebrew-style
  (opt5e/fighting-style-option
   {:name "Tidewalker" :key :tidewalker
    :description "You gain a swimming speed of 30 feet."
    :props {:swimming-speed 30}}))

;; The pool a feat grants from = built-in fighting styles ++ the homebrew one.
(def pool (concat opt5e/fighting-style-options [homebrew-style]))

;; A HOMEBREW feat whose DATA grants a fighting style choice.
(def feat-cfg
  {:name "Style Adept" :key :style-adept
   :description "You gain a fighting style of your choice."
   :fighting-style {:choose 1}})

(defn feat-option []
  (opt5e/feat-option-from-cfg
   language-map spells-map spell-lists weapons5e/weapons-map race-map pool feat-cfg))

(deftest feat-data-grants-a-fighting-style-choice-from-the-pool
  (testing "a homebrew feat's :fighting-style data compiles to a Fighting Style choice offering built-in AND homebrew styles"
    (let [opt (feat-option)
          fs-sel (first (filter #(= "Fighting Style" (::t/name %)) (::t/selections opt)))]
      (is (some? fs-sel)
          "the feat carries a Fighting Style selection (the grant compiled)")
      (let [offered (set (map ::t/name (::t/options fs-sel)))]
        (is (contains? offered "Archery")
            "a BUILT-IN fighting style is offered")
        (is (contains? offered "Tidewalker")
            "the HOMEBREW fighting style is offered too — the pool is open, cross-bucket as data")))))

(deftest a-feat-without-the-grant-has-no-fighting-style-selection
  (testing "control: no :fighting-style key → no fighting-style selection (the grant is opt-in data)"
    (let [opt (opt5e/feat-option-from-cfg
               language-map spells-map spell-lists weapons5e/weapons-map race-map pool
               (dissoc feat-cfg :fighting-style))]
      (is (not-any? #(= "Fighting Style" (::t/name %)) (::t/selections opt))
          "no grant key, no grant"))))

;; ---------------------------------------------------------------------------
;; The built-character mile: pick the feat, choose the HOMEBREW style, and read
;; its mechanic off the derived sheet. (This is where the divine-soul :ref bug
;; hid — so it's the assertion that actually matters.)
;; ---------------------------------------------------------------------------

(def test-template
  (t5e/template
   (concat
    (t5e/template-selections
     nil nil nil
     weapons5e/weapons-map weapons5e/weapons
     sl5e/spell-lists spells-map
     [] [] [] []                          ; no backgrounds/races/classes/feats here
     language-map)
    ;; inject the feat (compiled with the built-in ++ homebrew pool) as a direct selection
    [(t/selection-cfg {:name "Bonus Feat" :key :bonus-feat :tags #{:feats}
                       :options [(feat-option)] :min 1 :max 1})])))

(def char-entity
  {:orcpub.entity/options
   {:ability-scores
    {:orcpub.entity/key :standard-roll
     :orcpub.entity/value {:orcpub.dnd.e5.character/str 10 :orcpub.dnd.e5.character/dex 10
                           :orcpub.dnd.e5.character/con 10 :orcpub.dnd.e5.character/int 10
                           :orcpub.dnd.e5.character/wis 10 :orcpub.dnd.e5.character/cha 10}}
    :bonus-feat
    {:orcpub.entity/key :style-adept
     :orcpub.entity/options
     {:fighting-style {:orcpub.entity/key :tidewalker}}}}})

(deftest built-character-gets-the-homebrew-fighting-style-mechanic
  (testing "a character who takes the feat and picks the HOMEBREW Tidewalker style has its swimming speed on the derived sheet"
    (let [built (entity/build char-entity test-template)]
      (is (some? built) "build must not throw")
      (is (= 30 (char5e/base-swimming-speed built))
          "the homebrew fighting style's :props mechanic (swimming-speed 30) lands end-to-end"))))

(deftest control-built-in-style-has-no-swim-speed
  (testing "CONTROL: picking the built-in Archery style instead → swimming speed is NOT 30 (proves the 30 came from Tidewalker, not a default)"
    (let [built (entity/build
                 (assoc-in char-entity
                           [:orcpub.entity/options :bonus-feat :orcpub.entity/options :fighting-style :orcpub.entity/key]
                           :archery)
                 test-template)]
      (is (not= 30 (char5e/base-swimming-speed built))
          (str "without Tidewalker, swim speed should not be 30 — got " (char5e/base-swimming-speed built))))))
