(ns orcpub.security-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [datomic.api :as d]
            [clj-http.client :as client]
            [orcpub.config :as config]
            [orcpub.email :as email]
            [orcpub.errors :as errors]
            [orcpub.pedestal :as pedestal]
            [orcpub.pwned :as pwned]
            [orcpub.routes :as routes]
            [orcpub.routes.auth :as auth]
            [orcpub.time :as time]
            [orcpub.security :as s]
            [orcpub.db.schema :as schema]
            [clojure.set :as sets])
  (:import [java.util UUID]))

(deftest refusals-are-counted-so-the-numbers-can-be-tuned
  (s/take-refusals!)
  (is (nil? (s/refusal-summary {})) "a quiet hour says nothing")
  ;; Eleven signups from one host inside the hour: the first ten pass.
  (dotimes [_ 11] (s/guard :orcpub.routes.auth/register {:ip "9.9.9.9"}))
  (let [counts (s/take-refusals!)]
    (is (= 1 (:n (:register-per-host counts))))
    (is (= "limits: register-per-host 1" (s/refusal-summary counts))))
  (is (= {} (s/take-refusals!)) "taking them resets, so each hour is its own period"))

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
  (is (= "limits, busiest source: register-per-host 9/10" (s/busiest-summary {:register-per-host 9} 0.8)))
  (is (nil? (s/busiest-summary {:register-per-host 2} 0.8)) "an ordinary hour says nothing")
  (is (nil? (s/busiest-summary {:unknown-limit 99} 0.8)) "a limit with no cap is not guessed at")
  (testing "every hourly limit in the table has its cap, derived from it"
    (doseq [[route p] s/auth-guards, [scope n window] (:limits p) :when (= :hour window)]
      (is (str/ends-with? (s/busiest-summary {(s/limit-name route scope) n} 0.8)
                          (str (name (s/limit-name route scope)) " " n "/" n))))))

(deftest busiest-reads-the-live-window
  (let [host (str "7.7.7." (rand-int 250))]
    (dotimes [_ 9] (s/guard :orcpub.routes.auth/register {:ip host}))
    ;; Other tests share this atom, so the floor is what matters, not the value.
    (is (>= (:register-per-host (s/busiest)) 9))))

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

(use-fixtures :each
  (fn [f]
    (reset! @#'s/sightings {})
    (reset! @#'s/login-challenges {})
    (reset! @#'s/notice-state {})
    (with-redefs [email/configured? (constantly true)
                  pwned/check (constantly 0)]
      (binding [errors/*error-prefix* "TEST_ERROR:"]
        (f)))
    (reset! @#'s/sightings {})
    (reset! @#'s/login-challenges {})
    (reset! @#'s/notice-state {})))

(def ^:private secret "s3cr3t-turnstile-value-9f2c")

(defn- fresh-conn []
  (let [uri (str "datomic:mem:turnstile-test-" (UUID/randomUUID))]
    (d/create-database uri)
    (let [conn (d/connect uri)]
      @(d/transact conn schema/all-schemas)
      conn)))

(deftest a-blank-token-fails-without-calling-cloudflare
  (let [calls (atom 0)]
    (with-redefs [s/post-siteverify (fn [& _] (swap! calls inc) {:success true})]
      (doseq [t [nil "" "   "]]
        (is (= :fail (s/turnstile-verify t "1.2.3.4"))))
      (is (zero? @calls) "no HTTP call"))))

(deftest verify-maps-cloudflare-answers
  (with-redefs [s/post-siteverify (fn [& _] {:success true})]
    (is (= :pass (s/turnstile-verify "t" "1.2.3.4"))))
  (with-redefs [s/post-siteverify (fn [& _] {:success false :error-codes ["x"]})]
    (is (= :fail (s/turnstile-verify "t" "1.2.3.4"))))
  (with-redefs [s/post-siteverify (fn [& _] (throw (ex-info "siteverify unavailable" {:status 500})))]
    (is (= :unreachable (s/turnstile-verify "t" "1.2.3.4")))))

(defn- verify-with-http
  "turnstile-verify against a stubbed HTTP client answering `response` (a map, or a function that throws)."
  [response]
  (with-redefs [config/turnstile-secret-key (constantly secret)
                client/post (fn [& _] (if (fn? response) (response) response))]
    (s/turnstile-verify "t" "1.2.3.4")))

(deftest only-a-transport-failure-or-a-server-error-fails-open
  (is (= :fail (verify-with-http {:status 400 :body {:success false :error-codes ["invalid-input-response"]}}))
      "Cloudflare refusing the token is a refusal")
  (is (= :fail (verify-with-http {:status 400 :body "<html>bad</html>"})) "even with a body that is not JSON")
  (is (= :fail (verify-with-http {:status 403 :body nil})))
  (is (= :unreachable (verify-with-http {:status 500 :body {}})) "Cloudflare being down")
  (is (= :unreachable (verify-with-http {:status 503 :body nil})))
  (is (= :unreachable (verify-with-http #(throw (java.net.SocketTimeoutException. "slow")))))
  (is (= :pass (verify-with-http {:status 200 :body {:success true}}))))

(deftest a-token-that-is-not-text-is-a-missing-token
  (let [calls (atom 0)]
    (with-redefs [s/post-siteverify (fn [& _] (swap! calls inc) {:success true})]
      (doseq [t [42 {:a 1} ["x"] true]]
        (is (= :fail (s/turnstile-verify t "1.2.3.4")) (pr-str t)))
      (is (zero? @calls) "no HTTP call"))))

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
    (doseq [[route p] s/auth-guards]
      (testing (str route)
        (if (:none p)
          (is (string? (:none p)) "a reason for having none")
          (do (is (seq (:limits p)) "limits")
              (is (every? (fn [[scope n window]] (and (#{:per-address :per-host :per-name :names-per-host} scope)
                                                      (pos? n) (#{:minute :hour} window)))
                          (:limits p)))
              (is (#{:always :after-failure nil} (:turnstile p)))
              (when (:turnstile p)
                (let [[scope n window] (:challenge-limit p)]
                  (is (and (= :per-host scope) (pos? n) (#{:minute :hour} window))
                      "a challenge needs a bound on the checks it makes")))
              (is (#{:uniform :message :error} (:answer p)))
              (is (or (not= :message (:answer p)) (seq (:message p))) "a message to show")))))))

;; The table-driven tests read the table, so deleting a row would also delete its tests. This pins
;; what must stay in it: the forms that cost something to serve, and the limits each one has.
(deftest the-forms-that-cost-something-stay-guarded
  (doseq [[route [challenge scopes]]
          {:orcpub.routes.auth/login [:after-failure #{:names-per-host :per-name}]
           :orcpub.routes.auth/register [:always #{:per-host}]
           :orcpub.routes.auth/re-verify [:always #{:per-address :per-host}]
           :orcpub.routes.auth/send-password-reset [:always #{:per-address :per-host}]}
          :let [p (s/policy route)]]
    (is (= challenge (:turnstile p)) (str route " keeps its challenge"))
    (is (sets/subset? scopes (set (map first (:limits p)))) (str route " keeps its limits"))))

;; ---- the table-driven tests: one body for every form, read off s/auth-guards ----
;; A new row in the table is tested the moment it is added; a route with a policy and no driver
;; below fails here, so nothing is guarded without being tested.

(def ^:private worked
  "Counts the real work the stubbed handlers were asked to do (a mail, a verification, an account)."
  (atom 0))

(defn- reg-ctx-request [conn n ip token]
  {:conn conn :db (d/db conn) :scheme :https :headers {"host" "example.test"} :remote-addr ip
   :json-params (cond-> {:username (str "user" n) :email (str "user" n "@test.com")
                         :verify-email (str "user" n "@test.com")
                         :password "hunter2hunter2" :send-updates? false}
                  token (assoc :turnstile-token token))})

(defn- login [typed token ip]
  (auth/login-response {:json-params (cond-> {:username typed :password "pw"}
                                       token (assoc :turnstile-token token))
                        :db nil :conn nil :remote-addr ip :headers {}}))

(def ^:private drivers
  "route -> (fn [] call), where (call {:address :ip :token :typed}) runs the real handler."
  (letfn [(by-query [handler]
            (fn [] (fn [{:keys [address ip token]}]
                     (handler {:query-params (cond-> {:email address} token (assoc :turnstile-token token))
                               :db nil :conn nil :remote-addr ip}))))]
    {:orcpub.routes.auth/send-password-reset (by-query auth/send-password-reset)
     :orcpub.routes.auth/re-verify (by-query auth/re-verify)
     :orcpub.routes.auth/register (fn [] (let [conn (fresh-conn) n (atom 0)]
                                           (fn [{:keys [ip token]}]
                                             (auth/register (reg-ctx-request conn (swap! n inc) ip token)))))
     :orcpub.routes.auth/login (fn [] (fn [{:keys [typed ip token]}] (login typed token ip)))}))

(defmacro ^:private in-world
  "Runs `body` with Turnstile off, every address known and unverified, sends counted in `worked`, and
   every sign-in a failure."
  [& body]
  `(with-redefs [s/turnstile-enabled? (constantly false)
                 auth/user-for-email (constantly {:db/id 17})
                 auth/do-send-password-reset (fn [& _#] (swap! worked inc) {:status 200})
                 auth/do-verification (fn [& _#] (swap! worked inc) {:status 200})
                 auth/lookup-user (constantly {})
                 auth/find-user-by-username-or-email (constantly nil)]
     (reset! worked 0)
     ~@body))

(defn- guarded-routes [pred]
  (for [[route p] s/auth-guards :when (and (not (:none p)) (pred p))]
    [route p ((or (drivers route) (throw (ex-info (str "no driver for guarded route " route) {}))))]))

(defn- ctx-for
  "A request that varies in every way but `scope`'s subject, so only that limit can fill up."
  [scope i]
  {:address (if (= :per-address scope) "same@example.com" (str "u" i "@example.com"))
   :ip (if (#{:per-host :names-per-host} scope) "5.5.5.5" (str "10.0." i ".1"))
   :typed (if (= :per-name scope) "Same-Name" (str "name" i))})

(defn- refused? [p resp before]
  (case (:answer p)
    :uniform (= before @worked)
    :message (= [(:message p)] (get-in resp [:body :general]))
    :error (= errors/too-many-attempts (get-in resp [:body :error]))))

(deftest every-limit-refuses-when-its-number-is-reached
  (doseq [[route p call] (guarded-routes :limits)
          [scope n _ when-failing] (:limits p)]
    (testing (str route " " scope " " n)
      (reset! @#'s/sightings {})
      (in-world
       (let [outcomes (vec (for [i (range 1 (+ n 2))]
                             (let [before @worked, resp (call (ctx-for scope i))]
                               (refused? p resp before))))
             first-refused (inc (count (take-while false? outcomes)))]
         ;; a limit refuses the request after the n-th that counted; one that only refuses a
         ;; FAILING attempt refuses the n-th itself
         (is (= (if when-failing n (inc n)) first-refused))
         (is (every? true? (drop (dec first-refused) outcomes)) "and stays refused")
         (is (= (if (= :error (:answer p)) 0 n) @worked)
             "only the requests inside the limit did any work"))))))

(deftest a-uniform-answer-does-not-depend-on-the-address
  (doseq [[route p call] (guarded-routes #(= :uniform (:answer %)))]
    (testing (str route)
      (let [attempts (+ 2 (apply max (map second (:limits p))))
            run (fn [lookup]
                  (reset! @#'s/sightings {})
                  (in-world
                   (with-redefs [auth/user-for-email (constantly lookup)]
                     (let [resps (vec (for [_ (range attempts)] (call {:address "a@example.com" :ip "6.6.6.6"})))]
                       [(set resps) @worked]))))
            [known-responses known-work] (run {:db/id 17})
            [unknown-responses unknown-work] (run {})
            [verified-responses verified-work] (run {:db/id 17 :orcpub.user/verified? true})]
        (is (= known-responses unknown-responses) "one answer, limited or not, known or not")
        (is (= 1 (count known-responses)))
        (is (pos? known-work))
        (is (< known-work attempts) "and the known address was in fact limited")
        (is (zero? unknown-work) "nothing is done for an address with no account")
        (when (= :orcpub.routes.auth/re-verify route)
          (is (= unknown-responses verified-responses) "a verified account answers the same too")
          (is (zero? verified-work) "and sends nothing"))))))

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

(defn- challenge-ctx [route i]
  {:ip "5.5.5.5" :address (str "u" i "@example.com") :typed-name "same-name" :token "tok"})

(deftest the-checks-a-host-can-make-are-bounded-before-cloudflare-is-called
  (doseq [[route p] s/auth-guards
          :let [[_ n window] (:challenge-limit p)]
          :when n]
    (testing (str route " " n)
      (reset! @#'s/sightings {})
      (reset! @#'s/login-challenges {})
      (let [calls (atom 0)]
        (with-redefs [s/turnstile-enabled? (constantly true)
                      s/post-siteverify (fn [& _] (swap! calls inc) {:success true})]
          (when (= :after-failure (:turnstile p))
            (s/mark-login-challenge! "same-name" "5.5.5.5"))
          (let [results (vec (for [i (range (inc n))] (s/guard route (challenge-ctx route i))))]
            (is (= n @calls) "the N+1th attempt made no call")
            (is (= {:reason :limit :limit (keyword (str (name route) "-challenge"))} (peek results))
                "and is refused as a limit"))
          (testing "another host is unaffected"
            (is (not= :limit (:reason (s/guard route (assoc (challenge-ctx route 0) :ip "6.6.6.6")))))))))))

(deftest a-failed-challenge-spends-no-allowance-but-counts-as-a-check
  (reset! @#'s/sightings {})
  (with-redefs [s/turnstile-enabled? (constantly true)
                s/post-siteverify (fn [& _] {:success false})]
    (is (= :turnstile (:reason (s/guard :orcpub.routes.auth/register {:ip "9.9.9.9" :token "bad"}))))
    (is (nil? (get @@#'s/sightings [:register-per-host "9.9.9.9"])))
    (is (= 1 (count (get @@#'s/sightings [:register-challenge "9.9.9.9"]))))))

(defn- lookups-fail-the-test [& _] (throw (AssertionError. "looked the address up before the challenge")))

(deftest the-challenge-is-checked-before-any-allowance-or-lookup
  (doseq [[route p call] (guarded-routes #(= :always (:turnstile %)))]
    (let [ctx (ctx-for :per-host 1)]
      (testing (str route ": a failed token is told to retry, with nothing spent or looked up")
        (reset! @#'s/sightings {})
        (in-world
         (with-redefs [auth/user-for-email lookups-fail-the-test]
           (let [[resp calls] (verify-calls {:success false} #(call (assoc ctx :token "bad")))]
             (is (= 400 (:status resp)))
             (is (str/includes? (first (get-in resp [:body :general])) "not a bot"))
             (is (= [:general] (keys (:body resp))) "a :general error, no field key")
             (is (= 1 calls))
             (is (empty? (remove (fn [[k]] (str/ends-with? (name (first k)) "-challenge")) @@#'s/sightings))
                 "no allowance was spent (the check itself is counted apart)")
             (is (zero? @worked))))))
      (testing (str route ": a missing token says the script is blocked, without calling Cloudflare")
        (in-world
         (with-redefs [auth/user-for-email lookups-fail-the-test]
           (doseq [blank [nil "" "   "]]
             (let [[resp calls] (verify-calls {:success true} #(call (assoc ctx :token blank)))]
               (is (= [errors/turnstile-blocked-message] (get-in resp [:body :general])))
               (is (zero? calls)))))))
      (testing (str route ": a passing token goes on to the work")
        (reset! @#'s/sightings {})
        (in-world
         (let [[resp calls] (verify-calls {:success true} #(call (assoc ctx :token "good")))]
           (is (= 200 (:status resp)))
           (is (= 1 calls))
           (is (= 1 @worked)))))
      (testing (str route ": an unreachable Cloudflare fails open")
        (reset! @#'s/sightings {})
        (in-world
         (let [[resp _] (verify-calls :throw #(call (assoc ctx :token "any")))]
           (is (= 200 (:status resp)))
           (is (= 1 @worked)))))
      (testing (str route ": with Turnstile off nothing is checked")
        (reset! @#'s/sightings {})
        (in-world
         (let [calls (atom 0)]
           (with-redefs [s/post-siteverify (fn [& _] (swap! calls inc) {:success false})]
             (is (= 200 (:status (call ctx))))
             (is (zero? @calls))
             (is (= 1 @worked)))))))))

(defmacro ^:private with-login-stubs
  "Sign-in with Turnstile on: `known` is the one typed name whose password is right."
  [known & body]
  `(with-redefs [s/turnstile-enabled? (constantly true)
                 auth/lookup-user (fn [_# u# _#] (if (= ~known u#) {:db/id 1 :orcpub.user/verified? true} {}))
                 auth/find-user-by-username-or-email (constantly nil)
                 auth/create-login-response (fn [& _#] {:status 200 :body {:token "t"}})]
     ~@body))

(deftest a-route-that-challenges-after-failure-asks-only-after-one
  (doseq [[route _ call] (guarded-routes #(= :after-failure (:turnstile %)))]
    (reset! @#'s/sightings {})
    (let [ip "1.1.1.1"
          attempt (fn [typed token ip] (call {:typed typed :token token :ip ip}))]
      (testing (str route ": no challenge until a failure")
        (with-login-stubs "kaylee"
          (let [[resp calls] (verify-calls {:success false} #(attempt "kaylee" nil ip))]
            (is (= 200 (:status resp)))
            (is (zero? calls) "Cloudflare is not asked"))))
      (testing (str route ": a failure looks the same for an unknown name and a wrong password")
        (with-login-stubs "kaylee"
          (let [unknown (attempt "nobody" nil ip)
                wrong (with-redefs [auth/lookup-user (constantly {})
                                    auth/find-user-by-username-or-email
                                    (constantly {:db/id 1 :orcpub.user/username "kaylee"})]
                        (attempt "kaylee" nil ip))]
            (is (= errors/bad-credentials (-> unknown :body :error)))
            (is (true? (-> unknown :body :challenge?)))
            (is (= unknown wrong) "identical, including the flag"))))
      (testing (str route ": the flag is absent while Turnstile is off")
        (reset! @#'s/login-challenges {})
        (with-redefs [s/turnstile-enabled? (constantly false)
                      auth/lookup-user (constantly {})
                      auth/find-user-by-username-or-email (constantly nil)]
          (is (= {:status 401 :body {:error errors/bad-credentials}} (attempt "nobody" nil ip)))
          (is (= {:status 401 :body {:error errors/bad-credentials}} (attempt "nobody" nil ip)))
          (is (empty? @@#'s/login-challenges) "and nothing is marked")))
      (testing (str route ": after a failure the next attempt needs a passing token")
        (reset! @#'s/login-challenges {})
        (with-login-stubs "kaylee"
          (attempt "kaylee-typo" nil ip)
          (with-redefs [auth/lookup-user lookups-fail-the-test]
            (let [[resp calls] (verify-calls {:success true} #(attempt "kaylee-typo" nil ip))]
              (is (= errors/challenge-required (-> resp :body :error)) "credentials are not looked at")
              (is (true? (-> resp :body :challenge?)))
              (is (zero? calls)))
            (is (= errors/challenge-required
                   (-> (first (verify-calls {:success false} #(attempt "kaylee-typo" "bad" ip))) :body :error))))
          (is (= errors/bad-credentials
                 (-> (first (verify-calls {:success true} #(attempt "kaylee-typo" "good" ip))) :body :error)))
          (is (= errors/bad-credentials
                 (-> (first (verify-calls :throw #(attempt "kaylee-typo" "any" ip))) :body :error))
              "an unreachable Cloudflare fails open")))
      (testing (str route ": the mark is per typed name and connection, trimmed and lowercased")
        (reset! @#'s/login-challenges {})
        (with-login-stubs "kaylee"
          (attempt "  Wash " nil ip)
          (is (true? (s/login-challenge-required? "wash" ip)))
          (is (false? (s/login-challenge-required? "wash" "2.2.2.2")) "another connection")
          (is (false? (s/login-challenge-required? "zoe" ip)) "another name")))
      (testing (str route ": a successful sign-in clears the mark")
        (with-login-stubs "kaylee"
          (s/mark-login-challenge! "kaylee" ip)
          (is (= 200 (:status (first (verify-calls {:success true} #(attempt "kaylee" "good" ip))))))
          (is (false? (s/login-challenge-required? "kaylee" ip)))
          (is (= 200 (:status (attempt "kaylee" nil ip))) "so the next one needs no token")))
      (testing (str route ": the mark lapses")
        (with-login-stubs "kaylee"
          (with-redefs [s/login-challenge-ms -1]
            (s/mark-login-challenge! "zoe" ip)
            (is (false? (s/login-challenge-required? "zoe" ip))))))
      (testing (str route ": a refusal by a limit also marks the next attempt")
        (reset! @#'s/sightings {})
        (with-login-stubs "kaylee"
          (dotimes [i 5] (attempt (str "spray" i) nil "3.3.3.3"))
          (let [r (attempt "wash" nil "3.3.3.3")]
            (is (= errors/too-many-attempts (-> r :body :error)))
            (is (true? (-> r :body :challenge?)))
            (is (true? (s/login-challenge-required? "wash" "3.3.3.3")))))))))

(deftest a-blank-field-is-answered-before-the-guard-is-consulted
  (with-redefs [s/guard (fn [& _] (throw (AssertionError. "consulted too early")))]
    (is (= errors/username-required
           (-> (auth/login-response {:json-params {:username "" :password "x"} :remote-addr "1.2.3.4"})
               :body :error)))))

(deftest the-resend-token-does-not-reach-the-mail
  (let [seen (atom nil)]
    (with-redefs [s/turnstile-enabled? (constantly true)
                  s/post-siteverify (fn [& _] {:success true})
                  auth/user-for-email (constantly {:db/id 17})
                  auth/do-verification (fn [_ params & _] (reset! seen params) {:status 200})]
      (auth/re-verify {:query-params {:email "a@b.com" :turnstile-token "t"}
                       :db nil :conn nil :remote-addr "9.9.9.9"})
      (is (not (contains? @seen :turnstile-token))))))

;; ---- notices to an account's owner: first at once, then at most every 6 hours and 4 a day ----

(defn- clock-from [t0]
  (let [clock (atom t0)]
    [(fn [] @clock) (fn [minutes] (reset! clock (.plusSeconds t0 (* 60 minutes))))]))

(def ^:private t0 (.toInstant #inst "2026-01-01T00:00:00Z"))

(deftest every-notice-follows-its-cadence
  (doseq [[kind {:keys [min-gap-hours]}] s/notices]
    (testing (str kind)
      (reset! @#'s/notice-state {})
      (let [[now-fn at] (clock-from t0)
            gap (* 60 min-gap-hours)
            claim #(s/claim-notice! kind "someone")]
        (with-redefs [time/now now-fn]
          (is (= {} (claim)) "the first attempt is told at once")
          (is (nil? (claim)) "a second inside the gap is not")
          (at (dec gap))
          (is (nil? (claim)) "nor one just short of it")
          (at (inc gap))
          (is (= {:since-last 3} (claim)) "after the gap the next attempt is told, with how many came in since")
          (is (nil? (claim)) "and then the gap starts again")
          (dotimes [_ 4] (claim))
          (at (+ (* 2 (inc gap)) 2))
          (is (= {:since-last 6} (claim)) "attempts are counted while suppressed")
          (is (= {} (s/claim-notice! kind "someone-else")) "a different key has its own cadence"))))))

(deftest no-more-than-the-daily-maximum-goes-out-in-a-rolling-day
  (with-redefs [s/notices {:k {:first :immediate :min-gap-hours 1 :max-per-day 4}}]
    (let [[now-fn at] (clock-from t0)]
      (with-redefs [time/now now-fn]
        (is (= [true true true true false]
               (for [i (range 5)] (do (at (* i 61)) (some? (s/claim-notice! :k "x"))))))
        (at (+ (* 24 60) 1))
        (is (= {:since-last 2} (s/claim-notice! :k "x")) "the first notice has left the day")))))

;; ---- memory bounds ----

(defn- ts-ago [minutes] (.minus (time/now) (time/minutes minutes)))

(deftest each-key-is-pruned-by-its-own-window
  (let [fresh (atom {})]
    (reset! @#'s/sightings
            {[:short "a"] (with-meta [[(ts-ago 5) nil]] {:window (time/minutes 1)})
             [:long "b"] (with-meta [[(ts-ago 5) nil]] {:window (time/hours 1)})})
    (#'s/sight! [:other "c"] (time/minutes 1) nil)
    (is (nil? (get @@#'s/sightings [:short "a"])) "past its own minute, though inside any global horizon")
    (is (some? (get @@#'s/sightings [:long "b"])) "still inside its hour")
    (is (some? fresh))))

(deftest the-sightings-map-has-a-ceiling-on-keys
  (with-redefs-fn {#'s/sighting-key-cap 10}
    (fn []
      (dotimes [i 40] (#'s/sight! [:flood (str "k" i)] (time/hours 1) nil))
      (is (<= (count @@#'s/sightings) 10))
      (is (some? (get @@#'s/sightings [:flood "k39"])) "the key just recorded is always kept")
      (is (nil? (get @@#'s/sightings [:flood "k0"])) "the oldest went first")
      (testing "limiting still works for the current key at the ceiling"
        (dotimes [_ 3] (#'s/sight! [:flood "mine"] (time/hours 1) nil))
        (is (= 3 (count (#'s/sight! [:flood "mine"] (time/hours 1) nil))))))))

(deftest login-marks-are-bounded-without-clearing-live-ones
  (with-redefs [s/turnstile-enabled? (constantly true)]
    (with-redefs-fn {#'s/login-challenge-cap 3}
      (fn []
        (doseq [n ["a" "b" "c"]] (s/mark-login-challenge! n "1.1.1.1"))
        (s/mark-login-challenge! "d" "1.1.1.1")
        (is (= 3 (count @@#'s/login-challenges)) "full of live marks: no growth")
        (is (every? #(s/login-challenge-required? % "1.1.1.1") ["a" "b" "c"]) "and none cleared")
        (is (false? (s/login-challenge-required? "d" "1.1.1.1")) "the newcomer is simply unmarked")
        (testing "expired marks make room first"
          (swap! @#'s/login-challenges assoc (#'s/login-challenge-key "a" "1.1.1.1") 0)
          (s/mark-login-challenge! "d" "1.1.1.1")
          (is (true? (s/login-challenge-required? "d" "1.1.1.1")))
          (is (true? (s/login-challenge-required? "b" "1.1.1.1"))))
        (testing "a pair already marked is refreshed even when full"
          (s/mark-login-challenge! "b" "1.1.1.1")
          (is (= 3 (count @@#'s/login-challenges))))))))

(deftest busiest-counts-only-the-last-hour
  (reset! @#'s/sightings
          {[:x "1"] (with-meta (vec (repeat 5 [(ts-ago 90) nil])) {:window (time/hours 24)})
           [:y "1"] (with-meta (vec (repeat 2 [(ts-ago 5) nil])) {:window (time/hours 1)})})
  (is (= {:x 0 :y 2} (s/busiest))))

(deftest a-notice-whose-mail-could-not-be-queued-is-given-back
  (let [claim #(s/claim-notice! :taken-signup "x@y.z")]
    (let [first-notice (claim)]
      (is (= {} first-notice))
      (s/release-notice! :taken-signup "x@y.z" first-notice)
      (is (= {} (claim)) "due again at once, as a first notice"))
    (reset! @#'s/notice-state {})
    (let [[now-fn at] (clock-from t0)]
      (with-redefs [time/now now-fn]
        (claim)
        (at 1)
        (is (nil? (claim)) "one more attempt waits")
        (at (* 7 60))
        (let [later (claim)]
          (is (= {:since-last 2} later))
          (s/release-notice! :taken-signup "x@y.z" later)
          (is (= {:since-last 3} (claim)) "and the attempts that were waiting are not lost"))))))
