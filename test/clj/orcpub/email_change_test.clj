(ns orcpub.email-change-test
  "Tests for the email change flow (PR #644).
   Uses datomock for in-memory Datomic and with-redefs to stub email sending."
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [datomic.api :as d]
   [datomock.core :as dm]
   [buddy.hashers :as hashers]
   [orcpub.errors :as errors]
   [orcpub.routes :as routes]
   [orcpub.route-map :as route-map]
   [orcpub.db.schema :as schema])
  (:import [java.util UUID]))

;; Prefix error output so CI logs distinguish test-expected errors.
(use-fixtures :each
  (fn [f]
    (binding [errors/*error-prefix* "TEST_ERROR:"]
      (f))))

(defmacro with-conn [conn-binding & body]
  `(let [uri# (str "datomic:mem:email-change-test-" (UUID/randomUUID))
         ~conn-binding (do
                         (d/create-database uri#)
                         (d/connect uri#))]
     (try ~@body
          (finally (d/delete-database uri#)))))

(defn seed-users
  "Transact schema and seed two verified users for testing."
  [conn]
  @(d/transact conn schema/all-schemas)
  @(d/transact conn
    [{:orcpub.user/username   "alice"
      :orcpub.user/email      "alice@test.com"
      :orcpub.user/password   (hashers/encrypt "pass123")
      :orcpub.user/verified?  true}
     {:orcpub.user/username   "bob"
      :orcpub.user/email      "bob@test.com"
      :orcpub.user/password   (hashers/encrypt "pass456")
      :orcpub.user/verified?  true}]))

(defn make-request
  "Build a minimal request map for request-email-change."
  [conn new-email username]
  {:transit-params {:new-email new-email}
   :db             (d/db conn)
   :conn           conn
   :identity       {:user username}
   ;; send-email-change-verification reads scheme + headers for base-url
   :scheme         :https
   :headers        {"host" "localhost"}})

(defn find-user [db username]
  (d/q '[:find (pull ?e [:orcpub.user/email
                         :orcpub.user/pending-email
                         :orcpub.user/verification-key
                         :orcpub.user/verification-sent
                         :orcpub.user/verified?
                         :db/id]) .
         :in $ ?username
         :where [?e :orcpub.user/username ?username]]
       db username))

(def success-path (route-map/path-for route-map/verify-success-route))
(def failed-path (route-map/path-for route-map/verify-failed-route))

(defn redirect-location
  "Extract the Location header from a redirect response."
  [resp]
  (get-in resp [:headers "Location"]))

;; ---------- Tests ----------

(deftest test-email-change-happy-path
  (with-conn conn
    (let [mocked-conn (dm/fork-conn conn)]
      (seed-users mocked-conn)
      (testing "Request email change stores pending-email and returns 200"
        (with-redefs [routes/send-email-change-verification (fn [& _] nil)]
          (let [resp (routes/request-email-change
                       (make-request mocked-conn "newalice@test.com" "alice"))]
            (is (= 200 (:status resp)))
            (let [user (find-user (d/db mocked-conn) "alice")]
              (is (= "newalice@test.com" (:orcpub.user/pending-email user)))
              (is (some? (:orcpub.user/verification-key user)))
              ;; Original email unchanged until verified
              (is (= "alice@test.com" (:orcpub.user/email user)))

              (testing "Verify link swaps email and clears pending-email"
                (let [key   (:orcpub.user/verification-key user)
                      vresp (routes/verify {:query-params {:key key}
                                            :db           (d/db mocked-conn)
                                            :conn         mocked-conn})]
                  (is (= 302 (:status vresp)))
                  ;; Must redirect to success, not failure
                  (is (= success-path (redirect-location vresp)))
                  (let [updated (find-user (d/db mocked-conn) "alice")]
                    (is (= "newalice@test.com" (:orcpub.user/email updated)))
                    (is (nil? (:orcpub.user/pending-email updated)))
                    ;; Verification key invalidated — link can't be reused
                    (is (nil? (:orcpub.user/verification-key updated)))))))))))))

(deftest test-email-change-duplicate-rejected
  (with-conn conn
    (let [mocked-conn (dm/fork-conn conn)]
      (seed-users mocked-conn)
      (testing "Changing to an already-taken email returns 400 :email-taken"
        (with-redefs [routes/send-email-change-verification (fn [& _] nil)]
          (let [resp (routes/request-email-change
                       (make-request mocked-conn "bob@test.com" "alice"))]
            (is (= 400 (:status resp)))
            (is (= :email-taken (-> resp :body :error)))
            ;; DB unchanged
            (let [user (find-user (d/db mocked-conn) "alice")]
              (is (= "alice@test.com" (:orcpub.user/email user)))
              (is (nil? (:orcpub.user/pending-email user))))))))))

(deftest test-email-change-same-as-current
  (with-conn conn
    (let [mocked-conn (dm/fork-conn conn)]
      (seed-users mocked-conn)
      (testing "Changing to your own current email returns 400 :same-as-current"
        (with-redefs [routes/send-email-change-verification (fn [& _] nil)]
          (let [resp (routes/request-email-change
                       (make-request mocked-conn "alice@test.com" "alice"))]
            (is (= 400 (:status resp)))
            (is (= :same-as-current (-> resp :body :error)))
            ;; DB unchanged
            (let [user (find-user (d/db mocked-conn) "alice")]
              (is (= "alice@test.com" (:orcpub.user/email user)))
              (is (nil? (:orcpub.user/pending-email user))))))))))

(deftest test-email-change-invalid-format
  (with-conn conn
    (let [mocked-conn (dm/fork-conn conn)]
      (seed-users mocked-conn)
      (testing "Badly formatted email returns 400 :invalid-email"
        (with-redefs [routes/send-email-change-verification (fn [& _] nil)]
          (let [resp (routes/request-email-change
                       (make-request mocked-conn "not-an-email" "alice"))]
            (is (= 400 (:status resp)))
            (is (= :invalid-email (-> resp :body :error)))
            ;; DB unchanged
            (let [user (find-user (d/db mocked-conn) "alice")]
              (is (= "alice@test.com" (:orcpub.user/email user)))
              (is (nil? (:orcpub.user/pending-email user))))))))))

(deftest test-email-change-nil-email
  (with-conn conn
    (let [mocked-conn (dm/fork-conn conn)]
      (seed-users mocked-conn)
      (testing "Nil new-email returns 400 :invalid-email"
        (with-redefs [routes/send-email-change-verification (fn [& _] nil)]
          (let [resp (routes/request-email-change
                       (make-request mocked-conn nil "alice"))]
            (is (= 400 (:status resp)))
            (is (= :invalid-email (-> resp :body :error))))))
      (testing "Empty string new-email returns 400 :invalid-email"
        (with-redefs [routes/send-email-change-verification (fn [& _] nil)]
          (let [resp (routes/request-email-change
                       (make-request mocked-conn "" "alice"))]
            (is (= 400 (:status resp)))
            (is (= :invalid-email (-> resp :body :error)))))))))

(deftest test-email-change-no-auth
  (with-conn conn
    (let [mocked-conn (dm/fork-conn conn)]
      (seed-users mocked-conn)
      (testing "Request without identity returns 400 :user-not-found"
        (with-redefs [routes/send-email-change-verification (fn [& _] nil)]
          (let [resp (routes/request-email-change
                       {:transit-params {:new-email "x@test.com"}
                        :db (d/db mocked-conn)
                        :conn mocked-conn
                        :identity nil
                        :scheme :https
                        :headers {"host" "localhost"}})]
            (is (= 400 (:status resp)))
            (is (= :user-not-found (-> resp :body :error)))))))))

(deftest test-email-send-failure-rolls-back
  (with-conn conn
    (let [mocked-conn (dm/fork-conn conn)]
      (seed-users mocked-conn)
      (testing "When email send fails, pending-email is retracted and returns 500"
        (with-redefs [routes/send-email-change-verification
                      (fn [& _] (throw (Exception. "SMTP down")))]
          (let [resp (routes/request-email-change
                       (make-request mocked-conn "newalice@test.com" "alice"))]
            (is (= 500 (:status resp)))
            (is (= :email-send-failed (-> resp :body :error)))
            ;; Full rollback: pending-email, verification-key, and verification-sent all cleared
            (let [user (find-user (d/db mocked-conn) "alice")]
              (is (nil? (:orcpub.user/pending-email user)))
              (is (nil? (:orcpub.user/verification-key user)))
              (is (nil? (:orcpub.user/verification-sent user)))
              (is (= "alice@test.com" (:orcpub.user/email user))))))))))

(deftest test-expired-verification-cleans-pending
  (with-conn conn
    (let [mocked-conn (dm/fork-conn conn)]
      (seed-users mocked-conn)
      (testing "Expired verification link clears stale pending-email"
        (with-redefs [routes/send-email-change-verification (fn [& _] nil)]
          ;; Request a change so pending-email is set
          (routes/request-email-change
            (make-request mocked-conn "newalice@test.com" "alice"))
          (let [user (find-user (d/db mocked-conn) "alice")
                key  (:orcpub.user/verification-key user)]
            ;; Force-expire by backdating verification-sent past the 24h window
            @(d/transact mocked-conn
               [{:db/id (:db/id user)
                 :orcpub.user/verification-sent
                 (java.util.Date. (- (System/currentTimeMillis) (* 25 60 60 1000)))}])
            (let [vresp (routes/verify {:query-params {:key key}
                                        :db           (d/db mocked-conn)
                                        :conn         mocked-conn})]
              ;; Must redirect to failed, not success
              (is (= 302 (:status vresp)))
              (is (= failed-path (redirect-location vresp)))
              ;; All pending state cleaned up, original email unchanged
              (let [updated (find-user (d/db mocked-conn) "alice")]
                (is (nil? (:orcpub.user/pending-email updated)))
                (is (nil? (:orcpub.user/verification-key updated)))
                (is (= "alice@test.com" (:orcpub.user/email updated)))))))))))

(deftest test-race-condition-email-claimed-between-request-and-verify
  (with-conn conn
    (let [mocked-conn (dm/fork-conn conn)]
      (seed-users mocked-conn)
      (testing "If target email is claimed by another user before verify, swap is rejected"
        (with-redefs [routes/send-email-change-verification (fn [& _] nil)]
          ;; Alice requests change to unclaimed@test.com
          (routes/request-email-change
            (make-request mocked-conn "unclaimed@test.com" "alice"))
          (let [user (find-user (d/db mocked-conn) "alice")
                key  (:orcpub.user/verification-key user)]
            ;; Meanwhile, someone else claims that email
            @(d/transact mocked-conn
               [{:orcpub.user/username  "charlie"
                 :orcpub.user/email     "unclaimed@test.com"
                 :orcpub.user/password  (hashers/encrypt "pass789")
                 :orcpub.user/verified? true}])
            ;; Alice clicks verify — should be rejected
            (let [vresp (routes/verify {:query-params {:key key}
                                        :db           (d/db mocked-conn)
                                        :conn         mocked-conn})]
              (is (= 302 (:status vresp)))
              (is (= failed-path (redirect-location vresp)))
              ;; Alice's email unchanged, pending cleaned up
              (let [updated (find-user (d/db mocked-conn) "alice")]
                (is (= "alice@test.com" (:orcpub.user/email updated)))
                (is (nil? (:orcpub.user/pending-email updated)))
                (is (nil? (:orcpub.user/verification-key updated)))))))))))

(deftest test-rate-limiting
  (with-conn conn
    (let [mocked-conn (dm/fork-conn conn)]
      (seed-users mocked-conn)
      (testing "Immediate resend of same email is blocked (email still in transit)"
        (with-redefs [routes/send-email-change-verification (fn [& _] nil)]
          (let [resp1 (routes/request-email-change
                        (make-request mocked-conn "new1@test.com" "alice"))]
            (is (= 200 (:status resp1)))
            ;; Immediate resend — within 1-min transit zone, should be blocked
            (let [resp2 (routes/request-email-change
                          (make-request mocked-conn "new1@test.com" "alice"))]
              (is (= 429 (:status resp2)))
              ;; retry-after-secs counts down to 1-min mark (resend window)
              (is (pos? (-> resp2 :body :retry-after-secs)))
              (is (<= (-> resp2 :body :retry-after-secs) 60))))))

      (testing "Different email within 5 minutes is rate-limited"
        (with-redefs [routes/send-email-change-verification (fn [& _] nil)]
          (let [resp (routes/request-email-change
                       (make-request mocked-conn "new2@test.com" "alice"))]
            (is (= 429 (:status resp)))
            ;; retry-after-secs counts down to 5-min mark
            (is (pos? (-> resp :body :retry-after-secs)))
            ;; pending-email unchanged
            (let [user (find-user (d/db mocked-conn) "alice")]
              (is (= "new1@test.com" (:orcpub.user/pending-email user)))))))

      (testing "Resend of same email after 1 min is allowed (free resend)"
        (with-redefs [routes/send-email-change-verification (fn [& _] nil)]
          ;; Backdate verification-sent to 2 minutes ago (past 1-min transit, within 5-min cooldown)
          (let [user (find-user (d/db mocked-conn) "alice")]
            @(d/transact mocked-conn
               [{:db/id (:db/id user)
                 :orcpub.user/verification-sent
                 (java.util.Date. (- (System/currentTimeMillis) (* 2 60 1000)))}]))
          (let [resp (routes/request-email-change
                       (make-request mocked-conn "new1@test.com" "alice"))]
            (is (= 200 (:status resp)))))))))

(deftest test-second-change-replaces-pending
  (with-conn conn
    (let [mocked-conn (dm/fork-conn conn)]
      (seed-users mocked-conn)
      (testing "After rate-limit window, a new request overwrites old pending-email"
        (with-redefs [routes/send-email-change-verification (fn [& _] nil)]
          ;; First request
          (routes/request-email-change
            (make-request mocked-conn "old-pending@test.com" "alice"))
          (let [user (find-user (d/db mocked-conn) "alice")
                old-key (:orcpub.user/verification-key user)]
            ;; Backdate verification-sent past the 5-min rate limit window
            @(d/transact mocked-conn
               [{:db/id (:db/id user)
                 :orcpub.user/verification-sent
                 (java.util.Date. (- (System/currentTimeMillis) (* 6 60 1000)))}])
            ;; Second request should succeed and overwrite pending-email
            (let [resp (routes/request-email-change
                         (make-request mocked-conn "new-pending@test.com" "alice"))]
              (is (= 200 (:status resp)))
              (let [updated (find-user (d/db mocked-conn) "alice")]
                (is (= "new-pending@test.com" (:orcpub.user/pending-email updated)))
                ;; Verification key should be replaced so old link is dead
                (is (not= old-key (:orcpub.user/verification-key updated)))))))))))
