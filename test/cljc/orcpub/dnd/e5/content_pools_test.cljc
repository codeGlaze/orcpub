(ns orcpub.dnd.e5.content-pools-test
  "Pure tests for the pool primitive. JVM-runnable (the logic is in .cljc precisely so it
   is). Also the falsifiable maintainability proof (direction doc D21): the SAME `pool` fn
   serves any content type in one expression — a second pool is a one-liner, not new
   plumbing."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.dnd.e5.content-pools :as pools]))

;; A plugin-vals shape mirrors the app's :orcpub.dnd.e5/plugin-vals — a seq of packs, each
;; a map of content-keyword -> {entry-key -> entry}.
(def plugin-vals
  [{:orcpub.dnd.e5/draconic-ancestries
    {:amethyst {:name "Amethyst" :key :amethyst :option-pack "Pack A"}}
    :orcpub.dnd.e5/feats
    {:lucky {:name "Lucky" :key :lucky :option-pack "Pack A"}}}
   {:orcpub.dnd.e5/draconic-ancestries
    {:obsidian {:name "Obsidian" :key :obsidian :option-pack "Pack B"}}}])

(def built-in-ancestries
  [{:name "Black" :key :black} {:name "Red" :key :red}])

(deftest homebrew-entries-collects-across-packs
  (testing "homebrew entries of one type are gathered from every loaded pack"
    (is (= #{"Amethyst" "Obsidian"}
           (set (map :name (pools/homebrew-entries plugin-vals
                                                   :orcpub.dnd.e5/draconic-ancestries)))))))

(deftest pool-is-built-in-then-homebrew
  (testing "a pool is built-in entries first, then homebrew, so existing positions hold"
    (let [result (pools/pool plugin-vals :orcpub.dnd.e5/draconic-ancestries built-in-ancestries)]
      (is (= ["Black" "Red"] (map :name (take 2 result)))
          "built-ins come first, in order")
      (is (= #{"Amethyst" "Obsidian"} (set (map :name (drop 2 result))))
          "homebrew entries follow"))))

(deftest same-primitive-serves-a-second-type-in-one-expression
  (testing "the maintainability gate: a different pool is the same call with a different key"
    ;; This is the whole point — adding the feat pool is one expression, no new plumbing.
    (let [feats (pools/pool plugin-vals :orcpub.dnd.e5/feats [])]
      (is (= ["Lucky"] (map :name feats))))))

(deftest empty-and-missing-degrade-gracefully
  (testing "no packs / a type absent from a pack never errors — just yields built-ins"
    (is (= ["Black" "Red"]
           (map :name (pools/pool [] :orcpub.dnd.e5/draconic-ancestries built-in-ancestries))))
    (is (empty? (pools/homebrew-entries [{}] :orcpub.dnd.e5/draconic-ancestries)))))
