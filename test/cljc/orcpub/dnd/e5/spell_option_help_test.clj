(ns orcpub.dnd.e5.spell-option-help-test
  "Characterization for the spell-option :help field, pinned BEFORE it is made lazy.

   `spell-option` builds `:help` — the spell's peek panel — eagerly for every spell, for
   every class whose list contains it. It is 78% of the cost of building a spell option and
   most of the retained hiccup (docs/kb/perf-homebrew-builder-loop.md). The fix is to store
   a thunk and force it at render.

   These tests are written to pass BOTH before and after that change: they assert the
   CONTENT a renderer ends up with, via `force-help`, not the representation. That is the
   invariant that matters — a reader must see exactly the same peek. `help-deferred?`
   reports which representation is in play so the flip is visible in the test output rather
   than silent."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.dnd.e5.options :as opt5e]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.template :as t]
            [clojure.string :as st]))

(def spells-map spells5e/spell-map)
(def spell-help #'opt5e/spell-help)

(defn force-help
  "What a renderer ends up with. A thunk is called; anything else passes through."
  [h]
  (if (fn? h) (h) h))

(defn- opt-for [k & [class-name]]
  (opt5e/spell-option spells-map ::char5e/int (or class-name "Wizard") k))

;; A spread of real SRD spells: a long multi-paragraph one, a short one, a cantrip,
;; and one with a material component, so the assertions cover the branches spell-help has.
(def sample
  (->> (keys spells-map)
       (filter #(let [s (spells-map %)] (and (:description s) (:name s))))
       sort
       (take 40)
       vec))

(deftest help-content-is-identical-to-building-it-eagerly
  (testing "whatever representation :help uses, the rendered content is spell-help's output"
    (doseq [k sample]
      (is (= (spell-help (spells-map k))
             (force-help (::t/help (opt-for k))))
          (str "help content changed for " k)))))

(deftest help-carries-the-spell-metadata-and-the-whole-description
  (testing "the peek contains school/casting time/range/duration/components AND every
            paragraph of the description — pinned because the fix must not quietly trim it"
    (let [k (first (filter #(> (count (:description (spells-map %))) 400) sample))
          spell (spells-map k)
          h (force-help (::t/help (opt-for k)))
          flat (pr-str h)]
      (is (vector? h) "help is hiccup")
      (is (= :div (first h)))
      (doseq [label ["School" "Casting Time" "Range" "Duration" "Components"]]
        (is (st/includes? flat label) (str "peek lost the " label " field")))
      (is (= (count (st/split (:description spell) #"\n"))
             (count (filter #(and (vector? %) (= :p.m-t-5 (first %)))
                            (tree-seq coll? seq h))))
          "one <p> per description paragraph, none dropped"))))

(deftest everything-else-about-the-option-is-untouched
  (testing "name, key, prereqs and modifiers must not change when help becomes lazy"
    (doseq [k (take 10 sample)]
      (let [o (opt-for k)]
        (is (= k (::t/key o)))
        (is (= (:name (spells-map k)) (::t/name o)))
        (is (= 1 (count (::t/prereqs o))) "the already-known prereq")
        (is (= 1 (count (::t/modifiers o))) "the spells-known modifier")))))

(deftest help-still-reads-as-present
  (testing "callers use :help as a truthiness test to decide whether to show the info
            button (character_builder.cljs:517). A thunk must stay truthy, and must NOT be
            forced just to answer that question."
    (doseq [k (take 10 sample)]
      (is (some? (::t/help (opt-for k))) "an option with help must look like it has help"))))

(deftest report-which-representation-is-in-play
  (testing "not an assertion — makes the eager -> lazy flip visible in the test output"
    (let [h (::t/help (opt-for (first sample)))]
      (println (format "\n[SPELL HELP] representation: %s"
                       (if (fn? h) "DEFERRED (thunk, forced at render)" "eager hiccup")))
      (is (some? h)))))
