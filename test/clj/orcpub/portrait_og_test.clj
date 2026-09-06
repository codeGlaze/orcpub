(ns orcpub.portrait-og-test
  "The og:image path for a composed portrait, end to end against a real
   in-memory Datomic: save a character carrying a portrait, then check the
   share card points at the rendered PNG and that the endpoint serves it.

   This is a server test rather than a browser one because that is where the
   whole path lives -- a share crawler never runs the app's JavaScript."
  (:require [clojure.test :refer [deftest is testing]]
            [datomic.api :as d]
            [datomock.core :as dm]
            [orcpub.routes :as routes]
            [orcpub.db.schema :as schema]
            [orcpub.entity.strict :as se]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.portrait-assets :as pa])
  (:import [java.util UUID]))

(defmacro with-conn [conn-binding & body]
  `(let [uri# (str "datomic:mem:orcpub-og-test-" (UUID/randomUUID))
         ~conn-binding (do (d/create-database uri#) (d/connect uri#))]
     (try ~@body (finally (d/delete-database uri#)))))

(defn- a-portrait []
  {:layers (into {}
                 (keep (fn [k]
                         (when-let [a (first (pa/assets-for-layer k))]
                           [k {:artist/id (pa/artist-for-asset k (:asset/id a))
                               :asset/id  (:asset/id a)}])))
                 [:head :shirt :eyes])
   :colors {:hair "#5c3a1e" :skin "#e8c69c"}
   :tweaks {:head {:shade 10}}})

(defn- character-with [values]
  {::se/selections []
   ::se/summary {::char5e/character-name "Sharey"
                 ::char5e/race-name "Halfling"}
   ::se/values values})

(defn- save! [conn character]
  (:body (routes/do-save-character (d/db conn) conn character {:user "testy"})))

(defn- setup [conn]
  (let [c (dm/fork-conn conn)]
    @(d/transact c schema/all-schemas)
    @(d/transact c [{:orcpub.user/username "testy"
                     :orcpub.user/email "test@test.com"}])
    c))

;; ---------- the attribute actually persists ----------

(deftest portrait-survives-a-real-save
  (testing "::char5e/portrait is a registered string attr, so the transaction
            that used to fail on an unregistered nested map now succeeds"
    (with-conn conn
      (let [c (setup conn)
            stored (pr-str (a-portrait))
            saved (save! c (character-with {::char5e/portrait stored}))
            id (:db/id saved)
            back (-> (d/pull (d/db c) '[{::se/values [::char5e/portrait]}] id)
                     ::se/values ::char5e/portrait)]
        (is (some? id) "character saved")
        (is (= stored back) "portrait round-tripped through Datomic verbatim")
        (is (= (a-portrait) (char5e/parse-portrait back)) "and parses back")))))

;; ---------- the endpoint ----------

(deftest endpoint-serves-a-png-for-a-composed-portrait
  (with-conn conn
    (let [c (setup conn)
          id (:db/id (save! c (character-with {::char5e/portrait (pr-str (a-portrait))})))
          resp (routes/character-portrait-png {:db (d/db c) :path-params {:id id}})]
      (is (= 200 (:status resp)))
      (is (= "image/png" (get-in resp [:headers "Content-Type"])))
      (is (re-find #"max-age" (get-in resp [:headers "Cache-Control"]))
          "cached -- a crawler may refetch often")
      (let [buf (byte-array 4)]
        (.read ^java.io.InputStream (:body resp) buf)
        (is (= [-119 80 78 71] (vec buf)) "body starts with the PNG magic number")))))

(deftest endpoint-404s-rather-than-500s-without-a-portrait
  (with-conn conn
    (let [c (setup conn)
          id (:db/id (save! c (character-with {::char5e/image-url "https://example.com/a.png"})))]
      (is (= 404 (:status (routes/character-portrait-png
                           {:db (d/db c) :path-params {:id id}})))
          "a pasted URL is not a composed portrait"))))

(deftest endpoint-404s-for-an-unknown-character
  (with-conn conn
    (let [c (setup conn)]
      (is (= 404 (:status (routes/character-portrait-png
                           {:db (d/db c) :path-params {:id 999999999}})))))))

;; ---------- the share card ----------

(defn- og-image-of
  "The og:image content of a character's share card. Attribute order is not
   assumed -- hiccup emits them alphabetically, so `content` precedes
   `property`."
  [c id]
  (let [html (:body (routes/character-page
                     {:db (d/db c) :conn c :headers {"host" "example.test"}
                      :uri "/" :path-params {:id id}}))
        tag (re-find #"<meta[^>]*og:image[^>]*>" html)]
    (second (some->> tag (re-find #"content=\"([^\"]*)\"")))))

(deftest share-card-points-at-the-rendered-portrait
  (with-conn conn
    (let [c (setup conn)
          id (:db/id (save! c (character-with {::char5e/portrait (pr-str (a-portrait))})))
          og (og-image-of c id)]
      (is (some? og) "og:image present")
      (is (re-find (re-pattern (str "/" id "/portrait\\.png")) og)
          (str "og:image should be the rendered portrait, got " og)))))

(deftest share-card-falls-back-to-image-url
  (with-conn conn
    (let [c (setup conn)
          id (:db/id (save! c (character-with {::char5e/image-url "https://example.com/pasted.png"})))
          og (og-image-of c id)]
      (is (= "https://example.com/pasted.png" og)
          "a character with no composed portrait keeps its pasted URL"))))

(deftest share-card-carries-the-character-name
  (testing "character-summary-for-id used to return only the ::se/summary
            submap while character-page destructured ::se/summary back out of
            it, so every shared link had an empty title"
    (with-conn conn
      (let [c (setup conn)
            id (:db/id (save! c (character-with {})))
            html (:body (routes/character-page
                         {:db (d/db c) :conn c :headers {"host" "example.test"}
                          :uri "/" :path-params {:id id}}))]
        (is (re-find #"Sharey" html) "the character's name reaches the share card")))))
