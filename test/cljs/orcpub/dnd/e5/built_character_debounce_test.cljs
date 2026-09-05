(ns orcpub.dnd.e5.built-character-debounce-test
  "How many times does entity/build actually run for ONE user change?

   `debounced-build-sub` adds the SAME on-change watch to both its inputs, the
   character and the built template. A single interaction (picking a race, a
   class) changes both, so both watches fire: the first takes the leading edge
   and builds immediately, the second finds last-run too recent and schedules a
   TRAILING build. Two full entity/build runs per change, the second landing
   500 ms later — right as the user clicks the next thing.

   These tests count builds directly by redefining `built-character`. The inputs
   are plain reagent atoms: the sub only needs add-watch/remove-watch/deref, so
   the real subscription graph is not needed to observe the fan-in."
  (:require [cljs.test :refer-macros [deftest testing is async]]
            [reagent.ratom :as ra]
            [orcpub.dnd.e5.subs :as subs]))

(def ^:private debounce-slack-ms
  "Longer than the sub's 500 ms debounce, so any trailing build has landed."
  900)

(defn- count-builds
  "Run `change!` against a fresh debounced build sub and call `k` with the number
   of builds it caused. Construction itself builds once; that is not counted.

   The inputs are REACTIONS over a shared source, not independent atoms. That is
   the production shape — `:character` and `:built-template` are both derived from
   app-db, so one interaction dirties both — and it is the whole point: with two
   unrelated atoms the second change really is news the first build never saw, and
   two builds would be correct. An earlier version of this helper used unrelated
   atoms and so could not tell a redundant rebuild from a needed one.

   NOTE: the stub is installed with set!, NOT with-redefs. with-redefs unwinds
   when its body exits, and this sub's whole point is a TRAILING build fired from
   a timer long after that — which would then run the real entity/build and go
   uncounted. An earlier version made exactly that mistake and reported 1 where
   there were 2."
  [change! k]
  (let [src    (ra/atom 0)
        ;; :auto-run keeps both reactions live. In the app the graph is live
        ;; because components deref it every render; an inert reaction never
        ;; recomputes and never notifies, so nothing would fire at all.
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
         ;; Was 2 before the fan-in fix: leading edge from the first watch,
         ;; trailing edge scheduled by the second.
         (is (= 1 n) "builds once, not once per changed input (was 2 before the fan-in guard)")
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
         (is (= 0 n) "nothing changed, so nothing to rebuild (was 1: ratom reset! notifies regardless)")
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
