(ns orcpub.save-version-test
  "The character version (`::se/version`): every save bumps it, a save sent with an older one is
   refused with a 409, and concurrent saves never store a duplicate selection. Saves run through
   `routes/do-save-character`, each taking its own database value as a request does."
  (:require [clojure.spec.alpha :as spec]
            [clojure.test :refer [deftest testing is]]
            [datomic.api :as d]
            [orcpub.db.schema :as schema]
            [orcpub.entity.strict :as se]
            [orcpub.routes :as routes])
  (:import [java.util UUID]
           [java.util.concurrent CountDownLatch]))

(defn- fresh-conn
  "A connection to a new in-memory database with the app's schema and the user kaylee."
  []
  (let [uri (str "datomic:mem:save-version-" (UUID/randomUUID))]
    (d/create-database uri)
    (let [conn (d/connect uri)]
      @(d/transact conn schema/all-schemas)
      @(d/transact conn [{:orcpub.user/username "kaylee" :orcpub.user/email "kaylee@example.com"}])
      conn)))

(def beren
  "A strict character with a name and a race."
  {::se/values {:orcpub.dnd.e5.character/character-name "Beren"}
   ::se/selections [{::se/key :race ::se/option {::se/key :tidefolk}}]})

(defn- save!
  "Save `strict` as kaylee, reading the database now, as a request does."
  [conn strict]
  (routes/do-save-character (d/db conn) conn strict {:user "kaylee"}))

(defn- with-skill
  "`strict` with a skills selection of `skill` added."
  [strict skill]
  (update strict ::se/selections conj {::se/key :skills ::se/options [{::se/key skill}]}))

(defn- skills
  "How many skills selections the stored character `id` holds."
  [conn id]
  (count (filter #(= :skills (::se/key %)) (::se/selections (d/pull (d/db conn) '[*] id)))))

(defn- together!
  "Run saves `a` and `b` (functions of no arguments) at the same moment; their statuses, in order."
  [a b]
  (let [go (CountDownLatch. 1)
        fa (future (.await go) (:status (a)))
        fb (future (.await go) (:status (b)))]
    (.countDown go)
    [@fa @fb]))

(deftest every-save-bumps-the-version-and-returns-it
  (let [conn (fresh-conn)
        created (routes/create-new-character conn beren "kaylee")
        id (:db/id created)]
    (is (= 1 (::se/version created)))
    (let [{:keys [status body]} (save! conn (assoc-in created [::se/values :orcpub.dnd.e5.character/character-name] "Bree"))]
      (is (= 200 status))
      (is (= 2 (::se/version body)))
      (is (= 2 (::se/version (d/pull (d/db conn) '[*] id)))))
    (testing "a save sent with an older version is refused before anything is written"
      (let [before (d/pull (d/db conn) '[*] id)
            {:keys [status body]} (save! conn (assoc-in created [::se/values :orcpub.dnd.e5.character/character-name] "Old"))]
        (is (= 409 status))
        (is (= {:error :stale-character :current before} body))
        (is (= before (d/pull (d/db conn) '[*] id)))))))

(deftest a-character-saved-before-versions-gets-one-on-its-next-save
  (let [conn (fresh-conn)
        id (:db/id (routes/create-new-character conn beren "kaylee"))]
    @(d/transact conn [[:db/retract id ::se/version 1]])
    (let [legacy (d/pull (d/db conn) '[*] id)]
      (is (nil? (::se/version legacy)))
      (is (= 200 (:status (save! conn legacy))))
      (is (= 1 (::se/version (d/pull (d/db conn) '[*] id)))))))

(deftest concurrent-saves-of-one-copy-never-store-a-duplicate
  (testing "carrying the version: one is saved, the other refused"
    (dotimes [_ 50]
      (let [conn (fresh-conn)
            copy (routes/create-new-character conn beren "kaylee")
            statuses (together! #(save! conn (with-skill copy :stealth)) #(save! conn (with-skill copy :insight)))]
        (is (= #{200 409} (set statuses)) (pr-str statuses))
        (is (= 1 (skills conn (:db/id copy)))))))
  (testing "without a version (an app from before): both are saved, one after the other"
    (dotimes [_ 50]
      (let [conn (fresh-conn)
            copy (dissoc (routes/create-new-character conn beren "kaylee") ::se/version)
            statuses (together! #(save! conn (with-skill copy :stealth)) #(save! conn (with-skill copy :insight)))
            stored (d/pull (d/db conn) '[*] (:db/id copy))]
        (is (= [200 200] statuses))
        (is (= 1 (skills conn (:db/id copy))))
        (is (spec/valid? ::se/entity stored))))))
