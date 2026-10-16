(ns orcpub.dnd.e5.character-drafts-test
  "The decisions behind one draft per character: which key a character's draft goes under, which
   draft a tab with none of its own opens, and when a draft counts as open in another tab."
  (:require [cljs.test :refer-macros [deftest is]]
            [orcpub.dnd.e5.character-drafts :as drafts]))

(deftest a-character-is-drafted-under-its-id-or-one-browser-key
  (is (= "111" (drafts/key-for {:db/id 111} "new-abc")) "a saved character: its id")
  (is (= "new-abc" (drafts/key-for {} "new-abc"))
      "a never-saved one keeps the key this tab already gave it")
  (let [k (drafts/key-for {} "111")]
    (is (drafts/new-key? k) "one opened after a saved character gets a key of its own")
    (is (not= k (drafts/key-for {} "111")) "and each such key is fresh")))

(deftest a-tab-with-no-draft-opens-the-latest-one-nobody-else-has-open
  (let [touched {"111" 300 "222" 200 "new-x" 100}]
    (is (= "111" (drafts/latest touched (constantly false))))
    (is (= "222" (drafts/latest touched #{"111"})) "the most recent is open in another tab")
    (is (nil? (drafts/latest touched (constantly true))) "every draft is open elsewhere")))

(deftest a-draft-is-open-elsewhere-only-while-another-tab-keeps-it-fresh
  (let [k "test-live-key"
        mark! (fn [tab at] (.setItem js/localStorage (str "draft-live/" k) (str tab " " at)))
        now 1000000]
    (try
      (mark! "another-tab" (- now 5000))
      (is (drafts/live-elsewhere? k now) "another tab marked it 5 s ago")
      (mark! "another-tab" (- now (inc drafts/live-ms)))
      (is (not (drafts/live-elsewhere? k now)) "a mark older than live-ms is a closed tab")
      (mark! (drafts/tab-id) (- now 5000))
      (is (not (drafts/live-elsewhere? k now)) "this tab's own mark is not elsewhere")
      (finally (.removeItem js/localStorage (str "draft-live/" k))))))
