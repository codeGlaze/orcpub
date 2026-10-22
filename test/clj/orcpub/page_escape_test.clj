(ns orcpub.page-escape-test
  "The page template escapes what it writes; only the page's own scripts and styles are raw."
  (:require [clojure.test :refer [deftest is]]
            [datomic.api :as d]
            [datomock.core :as dm]
            [orcpub.routes :as routes]
            [orcpub.db.schema :as schema]
            [orcpub.entity.strict :as se]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.index :as index])
  (:import [java.util UUID]))

(defmacro with-conn
  "Run `body` with `conn-binding` bound to a fresh in-memory database."
  [conn-binding & body]
  `(let [uri# (str "datomic:mem:page-escape-test-" (UUID/randomUUID))
         ~conn-binding (do (d/create-database uri#) (d/connect uri#))]
     (try ~@body (finally (d/delete-database uri#)))))

(deftest a-character-name-is-escaped-on-its-public-page
  (with-conn conn
    (let [c (dm/fork-conn conn)
          _ @(d/transact c schema/all-schemas)
          _ @(d/transact c [{:orcpub.user/username "testy" :orcpub.user/email "t@example.test"}])
          id (:db/id (:body (routes/do-save-character
                             (d/db c) c {::se/selections []
                                         ::se/summary {::char5e/character-name "Zed <b>MARK</b>"}
                                         ::se/values {::char5e/notes "n"}}
                             {:user "testy"})))
          html (:body (routes/character-page {:db (d/db c) :conn c :headers {"host" "example.test"}
                                              :uri "/" :path-params {:id id}}))]
      (is (re-find #"&lt;b&gt;MARK" html) "the name is on the page, escaped")
      (is (not (re-find #"<b>MARK</b>" html)) "and never as markup"))))

(deftest the-title-and-meta-are-escaped
  (let [html (index/index-page {:title "Zed <b>MARK</b>" :description "<i>x</i>" :nonce "n"})]
    (is (re-find #"<title>Zed &lt;b&gt;MARK&lt;/b&gt;</title>" html))
    (is (not (re-find #"<b>MARK</b>|<i>x</i>" html)))))
