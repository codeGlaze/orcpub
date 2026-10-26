(ns orcpub.dnd.e5.containment-test
  "A bug of ours, or homebrew the checks before it miss, costs the one item it is in, and is
   reported: docs/HOMEBREW_GUARD.md, 5."
  (:require [cljs.test :refer-macros [deftest testing is]]
            [orcpub.template :as t]
            [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.classes :as classes5e]
            [orcpub.dnd.e5.events :as events]
            [re-frame.core]
            [re-frame.interceptor]
            [re-frame.registrar]
            [orcpub.dnd.e5.options :as opt5e]
            [orcpub.dnd.e5.homebrew-guard :as guard]
            [orcpub.dnd.e5.spell-subs :as spell-subs]))

(deftest an-invocation-that-breaks-the-warlock-costs-only-itself
  (let [reports (atom [])
        bad {:key :bad :name "Bad" :option-pack "P"}
        good {:key :good :name "Good" :option-pack "P"}
        real classes5e/eldritch-invocation-options]
    (with-redefs [classes5e/eldritch-invocation-options
                  (fn [invocations & more]
                    (if (some #(= :bad (:key %)) invocations)
                      (throw (js/Error. "a bug in one invocation"))
                      (apply real invocations more)))]
      (let [classes (guard/call-with-reporter
                     #(swap! reports conj %)
                     #(spell-subs/base-class-options {} {} {} {} {} [bad good] []))]
        (testing "every built-in class is still offered, the warlock included"
          (is (= 12 (count classes)))
          (is (some #(= :warlock (::t/key %)) classes)))
        (testing "the invocation that broke it is reported, by itself"
          (is (= [[::e5/invocations :bad]] (map (juxt :content-type :key) @reports))))))))

(deftest a-subrace-that-fails-is-blamed-not-its-race
  (let [reports (atom [])
        race {:name "Hill" :key :hill :option-pack "P"
              :subraces [{:name "Bad" :key :bad :race :hill :option-pack "P"}
                         {:name "Good" :key :good :race :hill :option-pack "P"}]}
        real opt5e/subrace-option]
    (with-redefs [opt5e/subrace-option (fn [& args]
                                         (if (= :bad (:key (last args)))
                                           (throw (js/Error. "a bug in one subrace"))
                                           (apply real args)))]
      (let [option (guard/call-with-reporter #(swap! reports conj %)
                                             #(guard/realize (opt5e/race-option {} {} {} {} race)))]
        (is (= :hill (::t/key option)) "the race is still offered")
        (is (= [[::e5/subraces :bad]] (map (juxt :content-type :key) @reports)))))))

(deftest the-deep-check-covers-every-type-that-becomes-a-character-option
  ;; monsters and encounters become no option: their pages contain a bad one instead
  (is (= #{::e5/races ::e5/subraces ::e5/backgrounds ::e5/feats ::e5/classes ::e5/subclasses
           ::e5/invocations ::e5/boons ::e5/selections ::e5/spells ::e5/languages}
         (set (keys (guard/conversions))))))

(deftest an-event-that-throws-is-noticed-once-and-changes-nothing
  (let [dispatched (atom [])
        err #(ex-info "re-frame" {:event-v [::broken %] :interceptor :db-handler :direction :before})]
    (with-redefs [re-frame.core/dispatch #(swap! dispatched conj %)]
      (events/event-failed (js/Error. "a bug") (err 1))
      (events/event-failed (js/Error. "a bug") (err 2))
      (is (= [[:show-error-message events/event-failed-message]] @dispatched)
          "told once, however many times it fails in a row")
      (events/event-failed (js/Error. "a bug")
                           (ex-info "re-frame" {:event-v [:show-error-message "x"]}))
      (is (= 1 (count @dispatched)) "the notice failing does not notice itself"))))

(deftest the-tests-keep-re-frames-handler
  ;; so an event that throws in a test fails it; the app registers its own in core.cljs
  (is (= re-frame.interceptor/default-error-handler
         (re-frame.registrar/get-handler :error :event-handler))))
