(ns orcpub.turnstile-test
  "Cloudflare Turnstile on the sign-up form: off unless both keys are set, fails open when
   Cloudflare is unreachable, refuses only on an explicit failure, and a refused attempt does
   not use up the host's signup allowance. No test reaches the network."
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [datomic.api :as d]
   [orcpub.config :as config]
   [orcpub.email :as email]
   [orcpub.errors :as errors]
   [orcpub.pedestal :as pedestal]
   [orcpub.pwned :as pwned]
   [orcpub.routes :as routes]
   [orcpub.security :as security]
   [orcpub.turnstile :as turnstile]
   [orcpub.db.schema :as schema])
  (:import [java.util UUID]))

(def ^:private secret "s3cr3t-turnstile-value-9f2c")

(use-fixtures :each
  (fn [f]
    (reset! @#'security/sightings {})
    (with-redefs [email/configured? (constantly true)
                  pwned/check (constantly 0)]
      (binding [errors/*error-prefix* "TEST_ERROR:"]
        (f)))
    (reset! @#'security/sightings {})))

(defn- fresh-conn []
  (let [uri (str "datomic:mem:turnstile-test-" (UUID/randomUUID))]
    (d/create-database uri)
    (let [conn (d/connect uri)]
      @(d/transact conn schema/all-schemas)
      conn)))

(defn- request [conn token]
  {:conn conn :db (d/db conn) :scheme :https :headers {"host" "example.test"}
   :remote-addr "7.7.7.7"
   :json-params (cond-> {:username "newcomer" :email "newcomer@test.com"
                         :verify-email "newcomer@test.com"
                         :password "hunter2hunter2" :send-updates? false}
                  token (assoc :turnstile-token token))})

(defn- user-exists? [conn]
  (some? (d/q '[:find ?e . :where [?e :orcpub.user/username "newcomer"]] (d/db conn))))

(defn- host-count []
  (count (get @@#'security/sightings [:register "7.7.7.7"])))


(deftest disabled-register-works-and-never-calls-cloudflare
  (let [conn (fresh-conn) calls (atom 0)]
    (with-redefs [turnstile/enabled? (constantly false)
                  turnstile/verify (fn [& _] (swap! calls inc) :fail)
                  turnstile/post-siteverify (fn [& _] (swap! calls inc) (throw (ex-info "no" {})))]
      (is (= 200 (:status (routes/register (request conn nil)))))
      (is (user-exists? conn))
      (is (zero? @calls) "siteverify is never called"))))

(deftest enabled-and-passing-creates-the-account
  (let [conn (fresh-conn)]
    (with-redefs [turnstile/enabled? (constantly true)
                  turnstile/post-siteverify (fn [& _] {:success true})]
      (is (= 200 (:status (routes/register (request conn "good-token")))))
      (is (user-exists? conn)))))

(deftest enabled-and-failing-refuses-without-using-the-host-allowance
  (let [conn (fresh-conn)]
    (with-redefs [turnstile/enabled? (constantly true)
                  turnstile/post-siteverify (fn [& _] {:success false})]
      (let [resp (routes/register (request conn "bad-token"))]
        (is (= 400 (:status resp)))
        (is (= 1 (count (get-in resp [:body :general]))) "a :general error")
        (is (str/includes? (first (get-in resp [:body :general])) "not a bot"))
        (is (= [:general] (keys (:body resp))) "no field key")
        (is (not (user-exists? conn)) "nothing created")
        (is (zero? (host-count)) "the host's allowance is not consumed")))))

(deftest enabled-and-unreachable-fails-open
  (let [conn (fresh-conn)]
    (with-redefs [turnstile/enabled? (constantly true)
                  turnstile/post-siteverify (fn [& _] (throw (java.net.SocketTimeoutException. "slow")))]
      (is (= 200 (:status (routes/register (request conn "any-token")))))
      (is (user-exists? conn)))))

(deftest a-blank-token-fails-without-calling-cloudflare
  (let [calls (atom 0)]
    (with-redefs [turnstile/post-siteverify (fn [& _] (swap! calls inc) {:success true})]
      (doseq [t [nil "" "   "]]
        (is (= :fail (turnstile/verify t "1.2.3.4"))))
      (is (zero? @calls) "no HTTP call"))
    (testing "and through register, while enabled"
      (let [conn (fresh-conn)]
        (with-redefs [turnstile/enabled? (constantly true)
                      turnstile/post-siteverify (fn [& _] (swap! calls inc) {:success true})]
          (is (= 400 (:status (routes/register (request conn nil)))))
          (is (zero? @calls)))))))

(deftest verify-maps-cloudflare-answers
  (with-redefs [turnstile/post-siteverify (fn [& _] {:success true})]
    (is (= :pass (turnstile/verify "t" "1.2.3.4"))))
  (with-redefs [turnstile/post-siteverify (fn [& _] {:success false :error-codes ["x"]})]
    (is (= :fail (turnstile/verify "t" "1.2.3.4"))))
  (with-redefs [turnstile/post-siteverify (fn [& _] (throw (ex-info "siteverify non-2xx" {:status 500})))]
    (is (= :unreachable (turnstile/verify "t" "1.2.3.4")))))

(deftest only-both-keys-enable-it
  (doseq [[site secret-key expected] [[nil nil false] ["s" nil false] [nil "k" false] ["s" "k" true]]]
    (with-redefs [config/turnstile-site-key (constantly site)
                  config/turnstile-secret-key (constantly secret-key)]
      (is (= expected (turnstile/enabled?))))))

(defn- frame-src-of [csp-header]
  (some #(when (str/starts-with? % "frame-src") %) (str/split csp-header #"; ")))

(deftest csp-allows-cloudflare-frames-only-when-enabled
  (let [leave (:leave (pedestal/make-nonce-interceptor false))
        header (fn [] (get-in (leave {:request {:csp-nonce "n"} :response {:headers {}}})
                              [:response :headers "Content-Security-Policy"]))]
    (with-redefs [config/turnstile-enabled? (constantly true)]
      (is (str/includes? (or (frame-src-of (header)) "") "https://challenges.cloudflare.com")))
    (with-redefs [config/turnstile-enabled? (constantly false)]
      (is (not (str/includes? (header) "challenges.cloudflare.com"))))))

(deftest the-settings-report-never-contains-the-secret
  (let [env {"TURNSTILE_SITE_KEY" "1x00000000000000000000AA" "TURNSTILE_SECRET_KEY" secret}]
    (with-redefs [config/env-raw (fn [n] (get env n))]
      (let [rows (filter #(#{"TURNSTILE_SITE_KEY" "TURNSTILE_SECRET_KEY"} (:var %)) (config/report))
            printed (str/join "\n" (config/report-lines (vec rows)))]
        (is (= 2 (count rows)))
        (is (not (str/includes? (pr-str rows) secret)) "not in the rows")
        (is (not (str/includes? printed secret)) "not in the banner")
        (is (str/includes? printed "1x00000000000000000000AA") "the public site key may show")
        (is (:set? (first (filter #(= "TURNSTILE_SECRET_KEY" (:var %)) rows))))))))
