(ns orcpub.dnd.e5.portrait-save-test
  "Saving a portrait is an edit to the character like any other."
  (:require [cljs.test :refer-macros [deftest is async]]
            [re-frame.core :refer [dispatch-sync]]
            [re-frame.db :refer [app-db]]
            [orcpub.dnd.e5.events]
            [orcpub.dnd.e5.portrait :as portrait]
            [orcpub.dnd.e5.portrait-assets :as pa]
            [orcpub.dnd.e5.portrait-face :as face]
            [orcpub.dnd.e5.portrait-light :as light]))

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

(deftest randomize-rolls-everything-within-sane-ranges
  (doseq [seed ["aaaaaaaa" "bbbbbbbb" "k3mn8pq2" "zz99yyxx" "2b2b2b2b"]]
    (let [p (portrait/roll seed {} false)
          in01 #(and (number? %) (<= 0 % 1))]
      (is (seq (:layers p)) "every layer is rolled")
      (is (= #{:hair :skin :eyes :shirt :lips :whites} (set (keys (:colors p)))) "every slot gets a colour")
      (doseq [[slot hex] (:colors p)]
        (is (some #{hex} (get pa/color-presets slot)) (str slot " is one of its presets")))
      (is (some #{(get-in p [:face :pupil])} face/pupil-kinds) "a pupil kind the face knows")
      (is (every? in01 (vals (select-keys (:face p) [:blush :freckles :freckle-place :freckle-strength :shade]))))
      (when-let [l (:light p)]
        (is (contains? light/moods (:mood l)) "a light mood the light knows")
        (is (contains? light/sides (:from l))))
      (is (every? in01 (vals (select-keys (:ombre p) [:start :falloff :depth :clumps :shine])))))))

(deftest a-seed-rolls-the-same-portrait
  (is (= (portrait/roll "k3mn8pq2" {} false) (portrait/roll "k3mn8pq2" {} false))))

(deftest keep-colours-keeps-every-colour-and-rolls-the-rest
  (let [mine {:layers {} :colors {:hair "#123456" :skin "#654321"} :tweaks {:head {:shade 0.2}}
              :face {:second-eye "#00ff00" :blush-colour "#e0606a"} :ombre {:tip "#abcdef"}}
        p (portrait/roll "k3mn8pq2" mine true)]
    (is (= (:colors mine) (:colors p)) "slot colours kept")
    (is (= (:tweaks mine) (:tweaks p)) "piece tweaks kept")
    (is (= "#00ff00" (get-in p [:face :second-eye])) "second eye kept")
    (is (= "#abcdef" (get-in p [:ombre :tip])) "hair tips kept")
    (is (seq (:layers p)) "the pieces are still rolled")))

(defn- opened-mode
  "The mode the editor opens in for a character with entity `values`. Returns a keyword."
  [values]
  (let [before @app-db]
    (try
      (reset! app-db {:character {:db/id 1 :orcpub.entity/values values}})
      (dispatch-sync [:portrait/open])
      (:portrait/mode @app-db)
      (finally (reset! app-db before)))))

(deftest the-editor-opens-on-what-the-character-uses
  (is (= :compose (opened-mode {})) "neither: Compose")
  (is (= :link (opened-mode {:orcpub.dnd.e5.character/image-url "https://i.imgur.com/2O4oP3l.png"}))
      "a link and no portrait: Picture link")
  (is (= :compose (opened-mode {:orcpub.dnd.e5.character/portrait "{:layers {:head {:asset/id :h}}}"}))
      "a portrait: Compose"))

(deftest a-drafted-link-changes-nothing-until-it-is-used
  (async done
    (let [before @app-db
          portrait "{:layers {:head {:asset/id :h}}}"
          values #(get-in @app-db [:character :orcpub.entity/values])]
      (reset! app-db {:character {:db/id 1 :orcpub.entity/values {:orcpub.dnd.e5.character/portrait portrait}}})
      (dispatch-sync [:portrait/open :link])
      (dispatch-sync [:portrait/set-link-draft "https://i.imgur.com/2O4oP3l.png"])
      (is (= portrait (:orcpub.dnd.e5.character/portrait (values))) "typing a link keeps the portrait")
      (is (nil? (:orcpub.dnd.e5.character/image-url (values))) "and sets no link yet")
      (dispatch-sync [:portrait/use-link])
      (js/setTimeout
       (fn []
         (is (= "https://i.imgur.com/2O4oP3l.png" (:orcpub.dnd.e5.character/image-url (values))) "Use sets the link")
         (is (nil? (:orcpub.dnd.e5.character/portrait (values))) "and the portrait goes")
         (reset! app-db before)
         (done))
       100))))
