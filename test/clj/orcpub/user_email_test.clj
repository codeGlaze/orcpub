(ns orcpub.user-email-test
  "Email lookups match an indexed lowercase key, never a scan that lowercases every stored address:
   every writer sets the key, the backfill gives it to older accounts, and those accounts sign in
   with any casing afterwards. plan-lookup-indexes.md on agents/develop."
  (:require [buddy.hashers :as hashers]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [clojure.test :refer [deftest is testing]]
            [datomic.api :as d]
            [orcpub.db.schema :as schema]
            [orcpub.email :as email]
            [orcpub.routes :as routes]
            [orcpub.security :as security]
            [orcpub.user-email :as ue])
  (:import [java.util Locale UUID]))

(defn- fresh-conn
  "A connection to a new in-memory database with the full schema."
  []
  (let [uri (str "datomic:mem://user-email-" (UUID/randomUUID))]
    (d/create-database uri)
    (doto (d/connect uri) (-> (d/transact schema/all-schemas) deref))))

(defn- account
  "The account named `username` in `conn`, pulled whole, or nil."
  [conn username]
  (when-let [e (d/q '[:find ?e . :in $ ?u :where [?e :orcpub.user/username ?u]] (d/db conn) username)]
    (d/pull (d/db conn) '[*] e)))

(deftest the-key-is-trimmed-and-lowercased-in-any-locale
  (is (= "kaylee@example.test" (ue/email-key "  Kaylee@Example.TEST ")))
  (is (nil? (ue/email-key " ")))
  (let [default (Locale/getDefault)]
    (try (Locale/setDefault (Locale. "tr" "TR"))
         (is (= "ian@example.test" (ue/email-key "IAN@example.test")) "no dotless i on a Turkish JVM")
         (finally (Locale/setDefault default)))))

(deftest an-older-account-signs-in-with-any-casing-after-the-backfill
  (let [conn (fresh-conn)]
    @(d/transact conn [{:orcpub.user/username "kaylee" :orcpub.user/email "Kaylee@Example.Test"
                        :orcpub.user/password (hashers/encrypt "shiny-hunter2") :orcpub.user/verified? true}])
    (is (= 1 (ue/backfill! conn 500)))
    (is (zero? (ue/backfill! conn 500)) "a second run finds nothing to do")
    (let [db (d/db conn)]
      (is (= "Kaylee@Example.Test" (:orcpub.user/email (account conn "kaylee"))) "the address as typed is kept")
      (is (= "kaylee" (:orcpub.user/username (routes/lookup-user db "KAYLEE@example.test" "shiny-hunter2")))
          "sign-in by email")
      (is (some? (:db/id (routes/user-for-email db "kaylee@EXAMPLE.test"))) "password reset")
      (is (seq (d/q routes/email-query db (ue/email-key "kaylee@example.TEST")))
          "sign-up and email change see the address as taken")
      (is (= "kaylee" (:orcpub.user/username (routes/find-user-by-username-or-email db "kaylee@example.test")))
          "an account named by its email"))))

(deftest an-account-with-a-blank-email-does-not-stop-the-backfill
  (let [conn (fresh-conn)]
    @(d/transact conn [{:orcpub.user/username "blank" :orcpub.user/email "  "}
                       {:orcpub.user/username "river" :orcpub.user/email "River@Example.Test"}])
    (is (= 1 (ue/backfill! conn 500)) "the account with an address gets its key")
    (is (= "river@example.test" (:orcpub.user/email-key (account conn "river"))))
    (is (nil? (:orcpub.user/email-key (account conn "blank"))) "the blank one has nothing to key")
    (is (zero? (ue/backfill! conn 500)) "and is not tried again")))

(deftest registration-sets-the-key
  (let [conn (fresh-conn)]
    (with-redefs [security/registration-allowed? (constantly true)
                  email/configured? (constantly false)
                  email/unverified-registration-allowed? (constantly true)]
      (is (= 200 (:status (routes/register {:conn conn :db (d/db conn) :scheme :https :headers {"host" "example.test"}
                                            :json-params {:username "newcomer" :email "NewComer@Test.com"
                                                          :verify-email "NewComer@Test.com" :password "hunter2hunter2"
                                                          :send-updates? false}})))))
    (is (= "newcomer@test.com" (:orcpub.user/email-key (account conn "newcomer"))))))

(defn- manage-user-fns
  "The functions of docker/scripts/manage-user.clj, loaded without running its command line:
   only the forms above its CLI dispatch. Returns the script's namespace."
  []
  (let [src (slurp (io/file "docker/scripts/manage-user.clj"))]
    (binding [*ns* *ns*]
      (load-string (subs src 0 (s/index-of src ";; --- CLI dispatch ---"))))
    (the-ns 'manage-user)))

(deftest the-docker-user-script-sets-the-key-and-finds-by-it
  (let [conn (fresh-conn)
        ns (manage-user-fns)
        create! @(ns-resolve ns 'try-create-user!)
        find-user @(ns-resolve ns 'find-user)]
    (is (:ok (create! conn "mal" "Mal@Serenity.Test" "pw")))
    (is (= "mal@serenity.test" (:orcpub.user/email-key (account conn "mal"))))
    (is (:duplicate (create! conn "mal2" "MAL@serenity.test" "pw")) "a second account for the address is refused")
    (is (= "mal" (:orcpub.user/username (find-user (d/db conn) "mal@SERENITY.test"))))))

(deftest no-lookup-lowercases-stored-addresses
  (testing "a query that lowercases every stored email reads every account"
    (let [files (concat (filter #(s/ends-with? (str %) ".clj") (file-seq (io/file "src/clj")))
                        [(io/file "docker/scripts/manage-user.clj")])
          scan #"(?s):orcpub\.user/email\s+\?[\w-]+\]\s*\[\((?:clojure\.string|s|str)/lower-case"]
      (is (= [] (for [f files :when (re-find scan (slurp f))] (str f)))))))
