(ns orcpub.dnd.e5.portrait-save-test
  "Saving a portrait is an edit to the character like any other."
  (:require [cljs.test :refer-macros [deftest is]]
            [re-frame.core :refer [dispatch-sync]]
            [re-frame.db :refer [app-db]]
            [orcpub.dnd.e5.events]))

(deftest saving-a-portrait-marks-the-character-changed
  ;; a character that is not :changed is replaced by the server's copy when a save answers,
  ;; so an unmarked portrait save could be overwritten by a late autosave without a word
  (let [before @app-db]
    (try
      (reset! app-db {:character {:db/id 1 :orcpub.entity/values {}}
                      :portrait/draft {:layers {} :colors {:hair "#123456"}}})
      (dispatch-sync [:portrait/save true])
      (is (some? (get-in @app-db [:character :orcpub.entity/values :orcpub.dnd.e5.character/portrait])))
      (is (true? (get-in @app-db [:character :changed])))
      (finally (reset! app-db before)))))
