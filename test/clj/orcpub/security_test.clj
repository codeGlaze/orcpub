(ns orcpub.security-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [datomic.api :as d]
            [orcpub.config :as config]
            [orcpub.email :as email]
            [orcpub.errors :as errors]
            [orcpub.pedestal :as pedestal]
            [orcpub.pwned :as pwned]
            [orcpub.routes :as routes]
            [orcpub.routes.auth :as auth]
            [orcpub.time :as time :refer [seconds minutes hours millis ago now]]
            [orcpub.security :as s]
            [orcpub.db.schema :as schema]
            [clojure.set :as sets])
  (:import [java.util UUID]))

;; Convenience wrappers for tests - these take n and return an Instant n units ago
(defn seconds-ago [n] (-> n seconds ago))
(defn minutes-ago [n] (-> n minutes ago))
(defn hours-ago [n] (-> n hours ago))
(defn millis-ago [n] (-> n millis ago))

(defn attempts-set [attempts]
  (into
   (sorted-set-by s/compare-dates)
   attempts))

(defn attempts-for-dates [dates]
  (into
   (sorted-set-by s/compare-dates)
   (map
    (fn [date]
      {:date date})
    dates)))

(deftest test-compare-dates
  (is (= 1 (s/compare-dates
            {:date (seconds-ago 10)}
            {:date (seconds-ago 11)})))
  (is (= 1 (s/compare-dates
            {:date (seconds-ago 10)}
            {:date (minutes-ago 11)})))
  (is (= -1 (s/compare-dates
            {:date (seconds-ago 10)}
            {:date (seconds-ago 9)})))
  (is (= -1 (s/compare-dates
            {:date (minutes-ago 10)}
            {:date (seconds-ago 9)}))))

(deftest test-too-many-attempts-for-username?
  (let [attempts {"larry" (attempts-for-dates (map seconds-ago (range 5)))
                  "larry-2" (attempts-for-dates (map seconds-ago (range 6)))
                  "redorc" (attempts-for-dates (map seconds-ago (range 4)))
                  "redorc-2" (sets/union
                              (attempts-for-dates (map seconds-ago (range 1 5)))
                              (attempts-for-dates (map hours-ago (range 1 6))))}]
    (is (s/too-many-attempts-for-username-aux "larry" attempts))
    (is (s/too-many-attempts-for-username-aux "larry-2" attempts))
    (is (not (s/too-many-attempts-for-username-aux "redorc" attempts)))
    (is (= 5 (-> "redorc-2" attempts (subseq < {:date (minutes-ago 1)}) count)))
    (is (= 4 (-> "redorc-2" attempts (subseq > {:date (minutes-ago 1)}) count)))
    (is (not (s/too-many-attempts-for-username-aux "redorc-2" attempts)))
    (is (not (s/too-many-attempts-for-username-aux "larry-3" attempts)))))

(deftest test-multiple-account-access?
  (let [attempts {"1.2.3.4" (attempts-set
                             (map
                              (fn [i]
                                {:date (seconds-ago i)
                                 :user (str "user-" i)})
                              (range 5)))
                  "1.2.3.5" (attempts-set
                             (map
                              (fn [i]
                                {:date (seconds-ago i)
                                 :user (str "user-" i)})
                              (range 6)))
                  "1.2.3.6" (attempts-set
                             (map
                              (fn [i]
                                {:date (seconds-ago i)
                                 :user (str "user-" i)})
                              (range 4)))}
        attempts-2 (update
                    attempts
                    "1.2.3.4"
                    sets/union
                    (attempts-set
                     (map
                      (fn [i]
                        {:date (hours-ago i)
                         :user (str "user-" i)})
                      (range 1 10))))]
    (is (s/multiple-account-access-aux "1.2.3.4" attempts))
    (is (s/multiple-account-access-aux "1.2.3.5" attempts))
    (is (not (s/multiple-account-access-aux "1.2.3.6" attempts)))
    (is (not (s/multiple-account-access-aux "1.2.3.6" attempts-2)))
    (is (not (s/multiple-account-access-aux "1.2.3.8" attempts-2)))))

;; Was disabled with "TODO: Fix / remove test" because the function it covers
;; never called its own aux -- it returned the attempts atom, which is truthy
;; even when empty, so every assertion expecting false failed. The function is
;; fixed, so the test is live again.
(deftest test-multiple-ip-attempts-to-same-account?
  (let [attempts {"user-1" (attempts-set
                            (map
                             (fn [i]
                               {:date (millis-ago i)
                                :ip (str i)})
                             (range 3)))
                  "user-2" (attempts-set
                            (map
                             (fn [i]
                               {:date (millis-ago i)
                                :ip (str i)})
                             (range 4)))
                  "user-3" (attempts-set
                            (map
                             (fn [i]
                               {:date (millis-ago i)
                                :ip (str i)})
                             (range 2)))}]
    (is (s/multiple-ip-attempts-to-same-account-aux
         "user-1"
         attempts))
    (is (s/multiple-ip-attempts-to-same-account-aux
         "user-1"
         (update
          attempts
          "user-1"
          (fn [as]
            (sets/union
             as
             (attempts-set
              (map
               (fn [s]
                 (assoc s :ip "0"))
               as)))))))
    (is (not
         (s/multiple-ip-attempts-to-same-account-aux
          "user-3"
          (update
           attempts
           "user-3"
           (fn [as]
            (sets/union
             as
             (attempts-set
              (map
               (fn [s]
                 (assoc s :ip "0"))
               as))))))))
    (is (s/multiple-ip-attempts-to-same-account-aux
         "user-2"
         attempts))
    (is (not (s/multiple-ip-attempts-to-same-account-aux
              "user-3"
              attempts)))
    (is (not (s/multiple-ip-attempts-to-same-account-aux
              "user-4"
              attempts)))))

(deftest test-add-and-remove-old
  (let [attempts {"user-1" (attempts-set
                            (map
                             (fn [i]
                               {:ip (str i)
                                :user "user-1"
                                :date (minutes-ago i)})
                             (range 1 10)))
                  "user-2" (attempts-set
                            (map
                             (fn [i]
                               {:ip (str i)
                                :user "user-2"
                                :date (minutes-ago (+ i 10))})
                             (range 1 10)))
                  "user-3" (attempts-set
                            (map
                             (fn [i]
                               {:ip (str i)
                                :user "user-3"
                                :date (minutes-ago i)})
                             (range 1 20)))}
        result (s/add-and-remove-old
                "user-1"
                {:ip "x"
                 :user "user-1"
                 :date (now)}
                attempts
                (minutes-ago 10))]
    (is (-> "user-2" result nil?))
    (is (= 10 (-> "user-1" result count)))
    (is (= 9 (-> "user-3" result count)))))

(deftest multiple-ip-attempts-answers-no-for-an-untouched-account
  ;; The precise shape of the bug: the function returned the attempts map
  ;; instead of calling its aux, and an empty map is truthy in Clojure, so it
  ;; answered "yes" for an account with no attempts at all.
  (is (not (s/multiple-ip-attempts-to-same-account-aux "nobody" {})))
  (is (not (s/multiple-ip-attempts-to-same-account? "nobody"))
      "and through the public predicate, which is what callers use"))

(deftest attempts-sharing-an-instant-collapse
  ;; A property of the production structure, not a test artifact: attempts live
  ;; in a sorted-set-by compare-dates, which orders on :date alone, so two
  ;; attempts recorded in the same instant are treated as one element and one
  ;; is dropped. Worth knowing before trusting these counts as exact.
  (let [instant (now)
        two-ips (into (sorted-set-by s/compare-dates)
                      [{:user "u" :ip "1.1.1.1" :date instant}
                       {:user "u" :ip "2.2.2.2" :date instant}])]
    (is (= 1 (count two-ips))
        "two attempts, one instant, one surviving entry")))


(deftest refusals-are-counted-so-the-numbers-can-be-tuned
  (s/take-refusals!)
  (is (nil? (s/refusal-summary {})) "a quiet hour says nothing")
  ;; Eleven signups from one host inside the hour: the first ten pass.
  (dotimes [_ 11] (s/registration-allowed? "9.9.9.9"))
  (let [counts (s/take-refusals!)]
    (is (= 1 (:n (:register counts))))
    (is (= "limits: register 1" (s/refusal-summary counts))))
  (is (= {} (s/take-refusals!)) "taking them resets, so each hour is its own period"))

(deftest a-limit-turns-away-only-what-is-past-it
  (s/take-refusals!)
  (let [host (str "10.0.0." (rand-int 250))]
    (is (every? true? (repeatedly 10 #(s/registration-allowed? host))))
    (is (false? (s/registration-allowed? host)))
    (is (true? (s/registration-allowed? (str host ".other")))
        "a different host has its own allowance")))


(deftest one-host-hammering-reads-differently-from-a-spread
  ;; The count alone cannot tell these apart, and they are not the same event:
  ;; twenty refusals from twenty addresses is a pool working through a list.
  (s/take-refusals!)
  (dotimes [_ 4] (s/note-refusal! :register "5.5.5.5"))
  (is (= "limits: register 4" (s/refusal-summary (s/take-refusals!)))
      "one source is not worth naming as a spread")
  (doseq [n (range 6)] (s/note-refusal! :register (str "5.5.5." n)))
  (is (= "limits: register 6 from 6 sources" (s/refusal-summary (s/take-refusals!)))))

(deftest a-host-that-stops-just-short-is-still-visible
  ;; The case refusals cannot see at all: a host taking nine of its ten signups
  ;; an hour, every hour, trips nothing and is never counted. busiest-summary
  ;; is pure, so it is tested on its own rather than through the shared atom.
  (is (= "limits, busiest source: register 9/10" (s/busiest-summary {:register 9} 0.8)))
  (is (= "limits, busiest source: register 10/10" (s/busiest-summary {:register 10} 0.8)))
  (is (nil? (s/busiest-summary {:register 2} 0.8)) "an ordinary hour says nothing")
  (is (nil? (s/busiest-summary {:unknown-limit 99} 0.8)) "a limit with no cap is not guessed at"))

(deftest busiest-reads-the-live-window
  (let [host (str "7.7.7." (rand-int 250))]
    (dotimes [_ 9] (s/registration-allowed? host))
    ;; Other tests share this atom, so the floor is what matters, not the value.
    (is (>= (:register (s/busiest)) 9))))


(deftest a-password-change-withdraws-the-tokens-that-came-before-it
  (s/restore-password-changes! {})
  (let [before 1000 after 3000]
    (s/note-password-changed! "kaylee" 2000)
    (testing "a token minted before the change is no longer good"
      (is (true? (s/token-withdrawn? "kaylee" before))))
    (testing "one minted after it is"
      (is (false? (s/token-withdrawn? "kaylee" after))))
    (testing "a token with no minted stamp predates the mechanism, so it goes"
      (is (true? (s/token-withdrawn? "kaylee" nil))))
    (testing "nobody else is signed out -- this is not a limit on logins"
      (is (false? (s/token-withdrawn? "wash" before)))
      (is (false? (s/token-withdrawn? "wash" nil))))))

(deftest the-register-forgets-what-it-can-no-longer-refuse
  (s/restore-password-changes! {})
  (s/note-password-changed! "old" 1000)
  (s/note-password-changed! "recent" 9000)
  (s/forget-password-changes-before! 5000)
  (is (= #{"recent"} (set (keys (s/password-changes-snapshot))))
      "an entry older than the token lifetime can only refuse tokens that have expired anyway")
  (is (false? (s/token-withdrawn? "old" 1))
      "and once forgotten it refuses nothing"))

(deftest the-register-can-be-restored-because-a-restart-empties-it
  ;; The failure this guards is silent: after a restart an unrestored register
  ;; refuses nothing and looks exactly like one that has nothing to refuse.
  (s/restore-password-changes! {})
  (is (false? (s/token-withdrawn? "kaylee" 1)) "empty register refuses nothing")
  (s/restore-password-changes! [["kaylee" 2000]])
  (is (true? (s/token-withdrawn? "kaylee" 1)) "restored register refuses again")
  (s/restore-password-changes! {}))

(deftest a-refresh-from-an-older-snapshot-keeps-what-was-noted-since
  ;; Greptile, PR #34: the hourly refresh read the database, a reset committed and
  ;; noted itself, then the refresh replaced the map with its older read.
  (s/restore-password-changes! {})
  (s/note-password-changed! "kaylee" 9000)
  (s/absorb-password-changes! {} 0)
  (is (true? (s/token-withdrawn? "kaylee" 8000)) "a snapshot that predates the note keeps it")
  (s/absorb-password-changes! {"kaylee" 3000} 0)
  (is (true? (s/token-withdrawn? "kaylee" 8000)) "an older instant from the database does not win")
  (s/absorb-password-changes! {"wash" 7000} 0)
  (is (true? (s/token-withdrawn? "wash" 6000)) "what the database knows is added")
  (s/absorb-password-changes! {} 8000)
  (is (false? (s/token-withdrawn? "wash" 6000)) "and expired entries still drop out")
  (s/restore-password-changes! {}))

;; ---- Cloudflare Turnstile on the sign-up form (moved from turnstile_test.clj) ----
;; Off unless both keys are set, fails open when Cloudflare is unreachable, refuses only on an
;; explicit failure, and a refused attempt does not use up the host's signup allowance.
;; No test reaches the network.

(use-fixtures :each
  (fn [f]
    (reset! @#'s/sightings {})
    (reset! @#'s/login-challenges {})
    (reset! s/failed-login-attempts-by-username {})
    (reset! s/failed-login-attempts-by-ip {})
    (with-redefs [email/configured? (constantly true)
                  pwned/check (constantly 0)]
      (binding [errors/*error-prefix* "TEST_ERROR:"]
        (f)))
    (reset! @#'s/sightings {})
    (reset! @#'s/login-challenges {})
    (reset! s/failed-login-attempts-by-username {})
    (reset! s/failed-login-attempts-by-ip {})))

(def ^:private secret "s3cr3t-turnstile-value-9f2c")

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
  (count (get @@#'s/sightings [:register "7.7.7.7"])))


(deftest disabled-register-works-and-never-calls-cloudflare
  (let [conn (fresh-conn) calls (atom 0)]
    (with-redefs [s/turnstile-enabled? (constantly false)
                  s/turnstile-verify (fn [& _] (swap! calls inc) :fail)
                  s/post-siteverify (fn [& _] (swap! calls inc) (throw (ex-info "no" {})))]
      (is (= 200 (:status (auth/register (request conn nil)))))
      (is (user-exists? conn))
      (is (zero? @calls) "siteverify is never called"))))

(deftest enabled-and-passing-creates-the-account
  (let [conn (fresh-conn)]
    (with-redefs [s/turnstile-enabled? (constantly true)
                  s/post-siteverify (fn [& _] {:success true})]
      (is (= 200 (:status (auth/register (request conn "good-token")))))
      (is (user-exists? conn)))))

(deftest enabled-and-failing-refuses-without-using-the-host-allowance
  (let [conn (fresh-conn)]
    (with-redefs [s/turnstile-enabled? (constantly true)
                  s/post-siteverify (fn [& _] {:success false})]
      (let [resp (auth/register (request conn "bad-token"))]
        (is (= 400 (:status resp)))
        (is (= 1 (count (get-in resp [:body :general]))) "a :general error")
        (is (str/includes? (first (get-in resp [:body :general])) "not a bot"))
        (is (= [:general] (keys (:body resp))) "no field key")
        (is (not (user-exists? conn)) "nothing created")
        (is (zero? (host-count)) "the host's allowance is not consumed")))))

(deftest enabled-and-unreachable-fails-open
  (let [conn (fresh-conn)]
    (with-redefs [s/turnstile-enabled? (constantly true)
                  s/post-siteverify (fn [& _] (throw (java.net.SocketTimeoutException. "slow")))]
      (is (= 200 (:status (auth/register (request conn "any-token")))))
      (is (user-exists? conn)))))

(deftest a-blank-token-fails-without-calling-cloudflare
  (let [calls (atom 0)]
    (with-redefs [s/post-siteverify (fn [& _] (swap! calls inc) {:success true})]
      (doseq [t [nil "" "   "]]
        (is (= :fail (s/turnstile-verify t "1.2.3.4"))))
      (is (zero? @calls) "no HTTP call"))
    (testing "and through register, while enabled"
      (let [conn (fresh-conn)]
        (with-redefs [s/turnstile-enabled? (constantly true)
                      s/post-siteverify (fn [& _] (swap! calls inc) {:success true})]
          (is (= 400 (:status (auth/register (request conn nil)))))
          (is (zero? @calls)))))))

(deftest verify-maps-cloudflare-answers
  (with-redefs [s/post-siteverify (fn [& _] {:success true})]
    (is (= :pass (s/turnstile-verify "t" "1.2.3.4"))))
  (with-redefs [s/post-siteverify (fn [& _] {:success false :error-codes ["x"]})]
    (is (= :fail (s/turnstile-verify "t" "1.2.3.4"))))
  (with-redefs [s/post-siteverify (fn [& _] (throw (ex-info "siteverify non-2xx" {:status 500})))]
    (is (= :unreachable (s/turnstile-verify "t" "1.2.3.4")))))

(deftest only-both-keys-enable-it
  (doseq [[site secret-key expected] [[nil nil false] ["s" nil false] [nil "k" false] ["s" "k" true]]]
    (with-redefs [config/turnstile-site-key (constantly site)
                  config/turnstile-secret-key (constantly secret-key)]
      (is (= expected (s/turnstile-enabled?))))))

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

;; ---- the guard table covers the real route table ----

(def ^:private extra-auth-routes
  "Account endpoints whose handlers live outside orcpub.routes.auth."
  #{:orcpub.routes/unsubscribe})

(defn- auth-route-names []
  (->> routes/routes
       (map :route-name)
       (filter #(or (= "orcpub.routes.auth" (namespace %))
                    (contains? extra-auth-routes %)))
       set))

(deftest every-auth-route-is-in-the-guard-table
  (let [actual (auth-route-names)
        listed (set (keys s/auth-guards))]
    (is (seq actual) "the route table was read")
    (is (= #{} (sets/difference actual listed))
        "auth routes with no entry in security/auth-guards")
    (is (= #{} (sets/difference listed actual))
        "auth-guards entries that name no route")
    (doseq [[route guard] s/auth-guards]
      (is (or (and (set? guard) (seq guard))
              (and (map? guard) (string? (:none guard)) (seq (:none guard))))
          (str route " needs guards or a reason for having none")))))

;; ---- Turnstile on password reset, resend verification and sign-in after a failure ----

(defn- verify-calls
  "Runs `f` with Turnstile on and siteverify answering `answer` (a map, or :throw); returns
   [result number-of-siteverify-calls]."
  [answer f]
  (let [calls (atom 0)]
    (with-redefs [s/turnstile-enabled? (constantly true)
                  s/post-siteverify (fn [& _]
                                      (swap! calls inc)
                                      (if (= :throw answer)
                                        (throw (java.net.SocketTimeoutException. "slow"))
                                        answer))]
      [(f) @calls])))

(defn- lookups-fail-the-test [& _] (throw (AssertionError. "looked the address up before the challenge")))

(deftest reset-and-resend-check-the-challenge-before-anything-else
  (doseq [[label handler route-name]
          [["send-password-reset" auth/send-password-reset :orcpub.routes.auth/send-password-reset]
           ["re-verify" auth/re-verify :orcpub.routes.auth/re-verify]]]
    (let [call (fn [token] (handler {:query-params (cond-> {:email "someone@example.com"}
                                                     token (assoc :turnstile-token token))
                                     :db nil :conn nil :remote-addr "9.9.9.9"}))]
      (testing (str label ": a failed challenge is told to retry, before any lookup or allowance")
        (with-redefs [auth/user-for-email lookups-fail-the-test]
          (let [[resp calls] (verify-calls {:success false} #(call "bad"))]
            (is (= 400 (:status resp)))
            (is (str/includes? (first (get-in resp [:body :general])) "not a bot"))
            (is (= 1 calls))
            (is (empty? @@#'s/sightings) "no allowance was spent"))
          (testing "a missing token is refused the same way, without calling Cloudflare"
            (let [[resp calls] (verify-calls {:success true} #(call nil))]
              (is (= 400 (:status resp)))
              (is (zero? calls))))))
      (testing (str label ": the refusal is the same whether or not the address has an account")
        (let [refused (fn [user]
                        (with-redefs [auth/user-for-email (constantly user)]
                          (first (verify-calls {:success false} #(call "bad")))))]
          (is (= (refused {:db/id 5}) (refused {})))))
      (testing (str label ": a passing challenge proceeds to the usual work")
        (let [looked-up (atom 0)]
          (with-redefs [auth/user-for-email (fn [& _] (swap! looked-up inc) {})]
            (let [[resp calls] (verify-calls {:success true} #(call "good"))]
              (is (= {:status 200} resp))
              (is (= 1 calls))
              (is (= 1 @looked-up))))))
      (testing (str label ": an unreachable Cloudflare fails open")
        (with-redefs [auth/user-for-email (constantly {})]
          (is (= {:status 200} (first (verify-calls :throw #(call "any")))))))
      (testing (str label ": with Turnstile off nothing is checked")
        (let [calls (atom 0)]
          (with-redefs [s/turnstile-enabled? (constantly false)
                        s/post-siteverify (fn [& _] (swap! calls inc) {:success false})
                        auth/user-for-email (constantly {})]
            (is (= {:status 200} (call nil)))
            (is (zero? @calls))))))))

(deftest the-resend-token-does-not-reach-the-mail
  (let [seen (atom nil)]
    (with-redefs [s/turnstile-enabled? (constantly true)
                  s/post-siteverify (fn [& _] {:success true})
                  auth/user-for-email (constantly {:db/id 17})
                  auth/do-verification (fn [_ params & _] (reset! seen params) {:status 200})]
      (auth/re-verify {:query-params {:email "a@b.com" :turnstile-token "t"}
                       :db nil :conn nil :remote-addr "9.9.9.9"})
      (is (not (contains? @seen :turnstile-token))))))

(defn- login [typed token ip]
  (auth/login-response {:json-params (cond-> {:username typed :password "pw"}
                                       token (assoc :turnstile-token token))
                        :db nil :conn nil :remote-addr ip :headers {}}))

(defmacro ^:private with-login-stubs
  "Sign-in with Turnstile on: `known` is the one typed name whose password is right."
  [known & body]
  `(with-redefs [s/turnstile-enabled? (constantly true)
                 auth/lookup-user (fn [_# u# _#] (if (= ~known u#) {:db/id 1 :orcpub.user/verified? true} {}))
                 auth/find-user-by-username-or-email (constantly nil)
                 auth/create-login-response (fn [& _#] {:status 200 :body {:token "t"}})]
     ~@body))

(deftest sign-in-needs-no-challenge-until-it-fails
  (with-login-stubs "kaylee"
    (let [[resp calls] (verify-calls {:success false} #(login "kaylee" nil "1.1.1.1"))]
      (is (= 200 (:status resp)))
      (is (zero? calls) "Cloudflare is not asked"))))

(deftest a-failure-looks-the-same-for-an-unknown-name-and-a-wrong-password
  (with-login-stubs "kaylee"
    (let [unknown (login "nobody" nil "1.1.1.1")
          ;; "kaylee" exists, but this lookup stub is told it is wrong
          wrong (with-redefs [auth/lookup-user (fn [& _] {})
                              auth/find-user-by-username-or-email (constantly {:db/id 1 :orcpub.user/username "kaylee"})]
                  (login "kaylee" nil "1.1.1.1"))]
      (is (= 401 (:status unknown)))
      (is (= errors/bad-credentials (-> unknown :body :error)))
      (is (true? (-> unknown :body :challenge?)))
      (is (= unknown wrong) "identical, including the flag"))))

(deftest the-flag-is-absent-while-turnstile-is-off
  (with-redefs [s/turnstile-enabled? (constantly false)
                auth/lookup-user (constantly {})
                auth/find-user-by-username-or-email (constantly nil)]
    (is (= {:status 401 :body {:error errors/bad-credentials}} (login "nobody" nil "1.1.1.1")))
    (is (= {:status 401 :body {:error errors/bad-credentials}} (login "nobody" nil "1.1.1.1"))
        "and a second failure still asks for nothing")))

(deftest after-a-failure-the-next-attempt-needs-a-passing-token
  (with-login-stubs "kaylee"
    (login "kaylee-typo" nil "1.1.1.1")
    (testing "no token: challenge required, and the credentials are not looked at"
      (with-redefs [auth/lookup-user lookups-fail-the-test]
        (let [[resp calls] (verify-calls {:success true} #(login "kaylee-typo" nil "1.1.1.1"))]
          (is (= 401 (:status resp)))
          (is (= errors/challenge-required (-> resp :body :error)))
          (is (true? (-> resp :body :challenge?)))
          (is (zero? calls)))))
    (testing "a failing token is the same"
      (is (= errors/challenge-required
             (-> (first (verify-calls {:success false} #(login "kaylee-typo" "bad" "1.1.1.1"))) :body :error))))
    (testing "a passing token reaches the credentials"
      (is (= errors/bad-credentials
             (-> (first (verify-calls {:success true} #(login "kaylee-typo" "good" "1.1.1.1"))) :body :error))))
    (testing "an unreachable Cloudflare fails open"
      (is (= errors/bad-credentials
             (-> (first (verify-calls :throw #(login "kaylee-typo" "any" "1.1.1.1"))) :body :error))))))

(deftest a-successful-sign-in-clears-the-mark
  (with-login-stubs "kaylee"
    (login "nobody" nil "1.1.1.1")
    (login "kaylee" nil "1.1.1.1")
    (is (true? (s/login-challenge-required? "nobody" "1.1.1.1")) "a different name keeps its own mark")
    (login "nobody" nil "2.2.2.2")
    (s/mark-login-challenge! "kaylee" "1.1.1.1")
    (is (= 200 (:status (first (verify-calls {:success true} #(login "kaylee" "good" "1.1.1.1"))))))
    (is (false? (s/login-challenge-required? "kaylee" "1.1.1.1")) "the success cleared it")
    (is (= 200 (:status (login "kaylee" nil "1.1.1.1"))) "so the next one needs no token")))

(deftest the-mark-is-per-typed-name-and-connection
  (with-login-stubs "kaylee"
    (login "  Wash " nil "1.1.1.1")
    (is (true? (s/login-challenge-required? "wash" "1.1.1.1")) "trimmed and lowercased as the lookup does")
    (is (false? (s/login-challenge-required? "wash" "2.2.2.2")) "another connection")
    (is (false? (s/login-challenge-required? "zoe" "1.1.1.1")) "another name")))

(deftest the-mark-lapses
  (with-login-stubs "kaylee"
    (s/mark-login-challenge! "wash" "1.1.1.1")
    (with-redefs [s/login-challenge-ms -1]
      (s/mark-login-challenge! "zoe" "1.1.1.1")
      (is (false? (s/login-challenge-required? "zoe" "1.1.1.1"))))
    (is (true? (s/login-challenge-required? "wash" "1.1.1.1")))))

(deftest nothing-is-marked-while-turnstile-is-off
  (with-redefs [s/turnstile-enabled? (constantly false)]
    (s/mark-login-challenge! "wash" "1.1.1.1"))
  (is (empty? @@#'s/login-challenges)))

(deftest a-sprayed-or-throttled-refusal-also-marks-the-next-attempt
  (with-login-stubs "kaylee"
    (with-redefs [s/multiple-account-access? (constantly true)]
      (let [r (login "wash" nil "3.3.3.3")]
        (is (= errors/too-many-attempts (-> r :body :error)))
        (is (true? (-> r :body :challenge?)))))
    (is (true? (s/login-challenge-required? "wash" "3.3.3.3")))))

;; ---- a blocked widget and a failed check are told apart ----

(deftest no-token-is-told-to-allow-the-script-and-a-bad-token-to-retry
  (let [messages (fn [resp] (get-in resp [:body :general]))
        blocked errors/turnstile-blocked-message]
    (is (str/includes? blocked "ad blocker"))
    (is (str/includes? blocked "challenges.cloudflare.com"))
    (doseq [[label call]
            [["register" (fn [token] (auth/register (request (fresh-conn) token)))]
             ["send-password-reset"
              (fn [token] (auth/send-password-reset
                           {:query-params (cond-> {:email "a@b.com"} token (assoc :turnstile-token token))
                            :db nil :conn nil :remote-addr "9.9.9.9"}))]
             ["re-verify"
              (fn [token] (auth/re-verify
                           {:query-params (cond-> {:email "a@b.com"} token (assoc :turnstile-token token))
                            :db nil :conn nil :remote-addr "9.9.9.9"}))]]]
      (testing label
        (with-redefs [auth/user-for-email lookups-fail-the-test]
          (doseq [blank [nil "" "   "]]
            (is (= [blocked] (messages (first (verify-calls {:success true} #(call blank)))))
                "a blank token gets the ad-blocker message"))
          (let [msgs (messages (first (verify-calls {:success false} #(call "bad"))))]
            (is (= 1 (count msgs)))
            (is (not= [blocked] msgs) "a failed token does not")
            (is (str/includes? (first msgs) "not a bot"))))))))
