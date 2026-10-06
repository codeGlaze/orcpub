(ns orcpub.ledger-page-test
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.ledger-page :as ledger-page]
            [orcpub.route-map :as route-map]
            [orcpub.routes :as routes]))

(deftest the-page-loads-its-own-script-and-never-the-app
  (doseq [id [17 nil]]
    (let [html (ledger-page/page {:id id :nonce "n0nce"})]
      (is (re-find #"<script nonce=\"n0nce\" src=\"/js/compiled/ledger.js\"" html) (pr-str id))
      (is (not (re-find #"orcpub\.js" html)) "no app bundle")
      (is (not (re-find #"styles\.css" html)) "no app stylesheet")))
  (testing "a saved character names itself; the draft page has no id"
    (is (re-find #"data-id=\"17\" data-mode=\"saved\"|data-mode=\"saved\" data-id=\"17\""
                 (ledger-page/page {:id 17})))
    (is (re-find #"data-mode=\"draft\"" (ledger-page/page {:id nil})))
    (is (not (re-find #"data-id" (ledger-page/page {:id nil}))))))

(defn- get-route
  "The GET route at `path` in the server's routes, or nil."
  [path]
  (first (filter #(and (= :get (:method %)) (= path (:path %))) routes/routes)))

(deftest the-data-page-and-its-repair-alias-are-served
  (doseq [route-key [route-map/dnd-e5-char-data-page-route route-map/dnd-e5-char-repair-page-route]]
    (let [path (route-map/path-for route-key :id ":id")
          route (get-route path)]
      (is (some? route) (str "no GET route at " path))
      (is (some #{:parse-id} (map :name (:interceptors route))) "a bad id is refused, as on the character page")))
  (is (some? (get-route (route-map/path-for route-map/dnd-e5-draft-data-page-route))))
  (is (= "/pages/dnd/5e/characters/5/data" (route-map/path-for route-map/dnd-e5-char-data-page-route :id 5)))
  (is (= "/pages/dnd/5e/characters/5/repair" (route-map/path-for route-map/dnd-e5-char-repair-page-route :id 5)))
  (is (= :char-5e-page (:handler (route-map/match-route "/pages/dnd/5e/characters/5")))
      "the character page still matches its own address"))
