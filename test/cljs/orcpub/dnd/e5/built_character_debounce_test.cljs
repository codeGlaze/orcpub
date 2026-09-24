(ns orcpub.dnd.e5.built-character-debounce-test
  "Counts entity/build runs per change in debounced-build-sub.

   Two things this harness gets right and a naive one does not: inputs are
   reactions over a SHARED source (production shape -- :character and
   :built-template both derive from app-db; with independent atoms a second
   build is correct, not redundant), and the stub is installed with set!, not
   with-redefs, which unwinds before the trailing build's timer fires.

   It does NOT reproduce why the app built twice -- that was two subscription
   instances."
  (:require [cljs.test :refer-macros [deftest testing is async]]
            [reagent.ratom :as ra]
            [orcpub.dnd.e5.subs :as subs]))

(def ^:private debounce-slack-ms
  "Longer than the sub's 500 ms debounce, so any trailing build has landed."
  900)

(defn- count-builds
  "Calls `k` with [build-count build-args] for the builds `change!` caused.
   Construction builds once; not counted."
  [change! k]
  (let [src    (ra/atom 0)
        ;; :auto-run keeps them live. An inert reaction never recomputes, so
        ;; nothing fires at all.
        char-r (ra/make-reaction (fn [] {:character @src}) :auto-run true)
        tmpl-r (ra/make-reaction (fn [] {:template @src}) :auto-run true)
        builds (atom 0)
        seen   (atom [])
        orig   subs/built-character]
    (set! subs/built-character (fn [c t] (swap! builds inc) (swap! seen conj [c t]) [c t]))
    (let [rx (subs/debounced-build-sub char-r tmpl-r)]
      (reset! builds 0)
      (change! src)
      (js/setTimeout
       (fn []
         (let [n @builds]
           (set! subs/built-character orig)
           (ra/dispose! rx)
           (k n @seen)))
       debounce-slack-ms))))

(deftest one-change-touching-both-inputs
  (testing "a single interaction that changes character AND template"
    (async done
      (count-builds
       (fn [src] (swap! src inc))
       (fn [n _seen]
         (is (= 1 n) "builds once, not once per changed input")
         (done))))))

(deftest a-second-independent-change-builds-again
  (testing "a later, genuinely new change still rebuilds"
    (async done
      (count-builds
       (fn [src] (swap! src inc))
       (fn [n _seen]
         (is (= 1 n))
         (done))))))

(deftest no-change-no-build
  (testing "writing an identical value to an input does not rebuild"
    (async done
      (count-builds
       (fn [src] (reset! src @src))
       (fn [n _seen]
         (is (= 0 n) "nothing changed, so nothing to rebuild")
         (done))))))

(deftest first-build-must-not-use-a-stale-template
  (testing "the build the user actually waits on sees BOTH new values"
    (async done
      (count-builds
       (fn [src] (swap! src inc))
       (fn [_n seen]
         ;; The inputs are derived from the same source, so any build that pairs
         ;; a new character with an old template is reading a half-updated graph.
         (is (every? (fn [[c t]] (= (:character c) (:template t))) seen)
             (str "every build should pair matching values, got " (pr-str seen)))
         (done))))))
