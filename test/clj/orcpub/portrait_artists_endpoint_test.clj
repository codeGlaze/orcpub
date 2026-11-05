(ns orcpub.portrait-artists-endpoint-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [datomic.api :as d]
            [orcpub.artist-credit :as ac]
            [orcpub.db.schema :as schema]
            [orcpub.dnd.e5.portrait-assets :as pa]
            [orcpub.routes :as routes]
            [orcpub.fork.branding]
            [orcpub.index :as index]
            [orcpub.route-map]
            [cheshire.core])
  (:import [java.util UUID]))

(defmacro with-conn
  "Run `body` with `conn-binding` bound to a fresh in-memory database with the schema."
  [conn-binding & body]
  `(let [uri# (str "datomic:mem:portrait-artists-test-" (UUID/randomUUID))
         ~conn-binding (do (d/create-database uri#) (d/connect uri#))]
     (try @(d/transact ~conn-binding schema/all-schemas)
          ~@body
          (finally (d/delete-database uri#)))))

(use-fixtures :each
  (fn [t] (let [before (pa/current-overrides)] (try (t) (finally (pa/set-artist-overrides! before))))))

(def marker "Fuss</script><b>MARK</b>")

(defn- artist!
  "Make an account for :house-pack in `conn` and save the marker credit for it; returns its id."
  [conn]
  @(d/transact conn [{:orcpub.user/username "fusspot" :orcpub.user/email "fusspot@example.test"
                      :orcpub.user/artist :house-pack}])
  (let [id (d/q '[:find ?e . :where [?e :orcpub.user/username "fusspot"]] (d/db conn))]
    (is (:saved (ac/save! conn id {:name marker})) "the credit is saved")
    id))

(deftest an-artist-credit-is-never-written-into-the-page
  (with-conn conn
    (artist! conn)
    (let [html (:body (index/index-page-response {:db (d/db conn) :headers {"host" "example.test"} :uri "/"}
                                                  {:title "t" :description "d"}))]
      (is (string? html))
      (is (not (re-find #"MARK" html)) "artist-editable text reaches the browser as data, not page source"))))

(deftest the-og-image-url-keeps-an-absolute-setting-and-prefixes-a-site-path
  (with-redefs [orcpub.fork.branding/og-image-filename "https://cdn.example.test/card.png"]
    (is (= "https://cdn.example.test/card.png" (index/default-image-url "example.test"))))
  (with-redefs [orcpub.fork.branding/og-image-filename "http://cdn.example.test/card.png"]
    (is (= "http://cdn.example.test/card.png" (index/default-image-url "example.test"))))
  (with-redefs [orcpub.fork.branding/og-image-filename "/image/card.png"]
    (is (= "https://example.test/image/card.png" (index/default-image-url "example.test")))))

(deftest the-character-page-carries-the-worldanvil-frame-header
  (with-redefs [routes/character-summary-for-id (constantly {})]
    (let [resp (routes/character-page {:headers {"host" "example.test"} :uri "/" :path-params {:id "1"}})]
      (is (= "ALLOW-FROM https://www.worldanvil.com/" (get-in resp [:headers "X-Frame-Options"])))
      (is (= "text/html" (get-in resp [:headers "Content-Type"])) "the default header is kept")
      (is (not (contains? resp "X-Frame-Options")) "and no stray top-level key"))))

(deftest the-endpoint-serves-the-saved-credit-as-json
  (with-conn conn
    (artist! conn)
    (let [{:keys [status headers body]} (routes/portrait-artists {:db (d/db conn)})
          parsed (cheshire.core/parse-string body)]
      (is (= 200 status))
      (is (re-find #"application/json" (get headers "Content-Type")))
      (is (re-find #"max-age=60" (get headers "Cache-Control")) "an edit shows within a minute")
      (is (= marker (get-in parsed ["house-pack" "artist/name"])) "the text arrives as data, unaltered"))))

(deftest the-endpoint-is-a-public-route
  (is (= "/dnd/5e/portrait-artists"
         (orcpub.route-map/path-for orcpub.route-map/dnd-e5-portrait-artists-route))))
