(ns orcpub.db.lookup-indexes-test
  "Every attribute the server looks up by value is indexed, and a database made before the indexes
   gains them on the next start without its data changing. lookup-indexes.md on agents/develop."
  (:require [clojure.test :refer [deftest is testing]]
            [datomic.api :as d]
            [orcpub.db.schema :as schema])
  (:import [java.util UUID]))

(def looked-up-by-value
  "Attributes a query matches against a value it is given (a username, a key, an owner)."
  [:orcpub.user/username :orcpub.user/email :orcpub.user/verification-key
   :orcpub.user/password-reset-key :orcpub.entity.strict/owner :orcpub.dnd.e5.party/owner
   :orcpub.dnd.e5.folder/owner :orcpub.dnd.e5.magic-items/owner :orcpub.party-share/character])

(defn- fresh-conn
  "A connection to a new in-memory database with `tx-data` transacted."
  [tx-data]
  (let [uri (str "datomic:mem://lookup-indexes-" (UUID/randomUUID))]
    (d/create-database uri)
    (doto (d/connect uri) (-> (d/transact tx-data) deref))))

(defn- value-indexed?
  "Whether attribute `a` has a value index in `db`: :db/index or :db/unique."
  [db a]
  (let [attr (d/attribute db a)]
    (boolean (or (:indexed attr) (:unique attr)))))

(def ^:private old-schema
  "`schema/all-schemas` as it was before the indexes: the same, without :db/index on
   `looked-up-by-value`."
  (map #(if (some #{(:db/ident %)} looked-up-by-value) (dissoc % :db/index) %) schema/all-schemas))

(deftest every-value-lookup-is-indexed
  (let [db (d/db (fresh-conn schema/all-schemas))]
    (is (= [] (remove #(value-indexed? db %) looked-up-by-value)))))

(deftest an-old-database-gains-the-indexes-on-start
  (let [conn (fresh-conn old-schema)
        user {:orcpub.user/username "kaylee" :orcpub.user/email "Kaylee@Example.Test"
              :orcpub.user/verification-key "k1"}
        e (get (:tempids @(d/transact conn [(assoc user :db/id "u") {:orcpub.entity.strict/owner "kaylee"}])) "u")]
    (testing "the old schema had none of them"
      (is (not-any? #(value-indexed? (d/db conn) %) looked-up-by-value)))
    @(d/transact conn schema/all-schemas)
    (let [db (d/db conn)]
      (testing "the start transacts the schema, which adds them"
        (is (= [] (remove #(value-indexed? db %) looked-up-by-value))))
      (testing "and changes no data"
        (is (= user (d/pull db (keys user) e)))
        (is (= 1 (count (seq (d/datoms db :avet :orcpub.entity.strict/owner "kaylee"))))
            "the value index answers the owner lookup")))))
