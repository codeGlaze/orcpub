(ns orcpub.user-agent-test
  "Phone width always gets the phone layout; otherwise the device decides."
  (:require [cljs.test :refer-macros [deftest testing is]]
            [re-frame.core :as rf]
            [re-frame.db :refer [app-db]]
            [orcpub.user-agent :as user-agent]
            [orcpub.dnd.e5.subs]
            [orcpub.dnd.e5.events]))

(deftest layout-type-follows-width
  (testing "phone width draws the phone layout whatever the device"
    (is (= :mobile (user-agent/layout-type :desktop true)))
    (is (= :mobile (user-agent/layout-type :tablet true)))
    (is (= :mobile (user-agent/layout-type :mobile true))))
  (testing "wider than a phone, the device decides"
    (is (= :desktop (user-agent/layout-type :desktop false)))
    (is (= :tablet (user-agent/layout-type :tablet false)))
    (is (= :mobile (user-agent/layout-type :mobile false)) "a phone sideways stays a phone"))
  (testing "width unknown: the device alone, as before"
    (is (= :mobile (user-agent/layout-type :mobile nil)))
    (is (= :desktop (user-agent/layout-type :desktop nil)))))

(deftest device-type-sub-tracks-resize
  (reset! app-db {:device-type :desktop :narrow-screen? false})
  (is (= :desktop @(rf/subscribe [:device-type])))
  (is (false? @(rf/subscribe [:mobile?])))
  (rf/dispatch-sync [:set-narrow-screen true])
  (is (= :mobile @(rf/subscribe [:device-type])) "narrowing the window re-lays it")
  (is (true? @(rf/subscribe [:mobile?])))
  (is (= :desktop @(rf/subscribe [:ua-device-type])) "the device itself is unchanged")
  (rf/dispatch-sync [:set-narrow-screen false])
  (is (= :desktop @(rf/subscribe [:device-type]))))
