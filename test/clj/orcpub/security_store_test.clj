(ns orcpub.security-store-test
  "The limits' state behind `LimitStore`: one contract, run against the memory store always and
   against a real Redis when one is available. Redis is a server given by REDIS_TEST_URL (not
   REDIS_URL: that would also switch every other test's limits to Redis), else a throwaway
   `redis-server` started on a free port, else the Redis tests are SKIPPED and say so."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [orcpub.env :as env]
            [orcpub.security :as s]
            [orcpub.security.redis-store :as redis]
            [orcpub.time :as time]
            [taoensso.carmine :as car :refer [wcar]]))

;; ---- a Redis for the run, or none ----

(def ^:private redis-conn (atom nil))

(defn- free-port []
  (with-open [ss (java.net.ServerSocket. 0)] (.getLocalPort ss)))

(defn- reachable? [url]
  (try (= "PONG" (wcar {:pool {} :spec {:uri url :conn-timeout-ms 500 :read-timeout-ms 500}} (car/ping)))
       (catch Throwable _ false)))

(defn- start-redis!
  "A throwaway redis-server, as {:url :process}, or nil when there is none to run."
  []
  (try
    (let [port (free-port)
          pb (ProcessBuilder. ^java.util.List ["redis-server" "--port" (str port) "--bind" "127.0.0.1"
                                               "--save" "" "--appendonly" "no"])
          p (.start (.redirectErrorStream pb true))
          url (str "redis://127.0.0.1:" port)]
      (loop [tries 30]
        (cond (reachable? url) {:url url :process p}
              (zero? tries) (do (.destroy p) nil)
              :else (do (Thread/sleep 100) (recur (dec tries))))))
    (catch Throwable _ nil)))

(defn- with-redis [f]
  (let [given (env/value :redis-test-url)
        started (when-not (and given (reachable? given)) (start-redis!))
        url (or (when (and given (reachable? given)) given) (:url started))]
    (reset! redis-conn (when url (redis/redis-store url)))
    (when-not url
      (println "SKIPPED: Redis tests (no REDIS_TEST_URL and no redis-server to start)"))
    (try (f)
         (finally
           (reset! redis-conn nil)
           (some-> (:process started) .destroy)))))

(use-fixtures :once with-redis)

(defn- clear-redis! [store]
  (let [conn (:conn store)]
    (loop [cursor "0"]
      (let [[nxt ks] (wcar conn (car/scan cursor :match (str redis/prefix "*") :count 500))]
        (doseq [k ks] (wcar conn (car/del k)))
        (when-not (= "0" (str nxt)) (recur nxt))))))

(defn- on-each-store
  "Runs (f name) once against memory and, when there is one, against Redis, each from empty."
  [f]
  (doseq [[nm store] [["memory" s/memory-store] ["redis" @redis-conn]] :when store]
    (reset! @#'s/sightings {})
    (reset! @#'s/login-challenges {})
    (reset! @#'s/notice-state {})
    (when (= "redis" nm) (clear-redis! store))
    (s/use-store! store)
    (try (testing nm (f nm))
         (finally (s/use-store! nil)))))

(defn- clock-from [t0]
  (let [clock (atom t0)]
    [(fn [] @clock) (fn [minutes] (reset! clock (.plusSeconds t0 (* 60 minutes))))]))

(def ^:private t0 (.toInstant #inst "2026-01-01T00:00:00Z"))

;; ---- the guard table ----

(defn- ctx
  "Varies in every way but `scope`'s subject, so only that limit can fill up."
  [scope i]
  {:address (if (= :per-address scope) "same@example.com" (str "u" i "@example.com"))
   :ip (if (#{:per-host :names-per-host} scope) "5.5.5.5" (str "10.0." i ".1"))
   :typed-name (if (= :per-name scope) "Same-Name" (str "name" i))
   :actionable (delay true)})

(deftest every-limit-of-the-table-refuses-at-its-number
  (on-each-store
   (fn [_]
     (doseq [[route p] s/auth-guards
             :when (and (not (:none p)) (not= :failure (:counted-on p)))
             [scope n] (:limits p)]
       (testing (str route " " scope " " n)
         (let [results (vec (for [i (range 1 (+ n 2))] (some? (s/guard route (ctx scope i)))))]
           (is (= (concat (repeat n false) [true]) results) "n pass, the next is refused")))))))

(deftest a-failing-form-refuses-the-failing-attempt-and-names-the-spread
  (on-each-store
   (fn [_]
     (let [route :orcpub.routes.auth/login
           [_ n] (first (filter #(= :per-name (first %)) (:limits (s/policy route))))
           [_ distinct-n] [nil (get-in s/auth-guards [route :notify :distinct])]
           from (fn [i] {:ip (str "10.1." i ".1") :typed-name "Victim"})
           outcomes (vec (for [i (range 1 (inc n))] (s/record! route :failure (from i))))]
       (is (nil? (:refusal (first outcomes))))
       (is (= :limit (:reason (:refusal (peek outcomes)))) "the n-th failing attempt is refused")
       (is (= [nil :sign-in-attempts] [(:notify (first outcomes)) (:notify (nth outcomes (dec distinct-n)))])
           "and the owner is told once enough addresses have tried")))))

(deftest sightings-count-inside-their-window-only
  (on-each-store
   (fn [_]
     (let [k [:probe "x"]
           short-window (java.time.Duration/ofMillis 200)]
       (is (empty? (#'s/sight! k short-window "a")))
       (is (= ["a"] (map second (#'s/sight! k short-window nil))))
       (is (= 2 (count (#'s/recent k short-window))) "looking records nothing")
       (is (= 2 (count (#'s/recent k short-window))))
       (Thread/sleep 300)
       (is (empty? (#'s/recent k short-window)) "gone with the window")))))

(deftest busiest-is-the-heaviest-key-of-each-limit
  (on-each-store
   (fn [_]
     (dotimes [_ 3] (#'s/sight! [:a "1"] (time/hours 1) nil))
     (dotimes [_ 5] (#'s/sight! [:a "2"] (time/hours 1) nil))
     (#'s/sight! [:b "1"] (time/hours 1) nil)
     (is (= {:a 5 :b 1} (select-keys (s/busiest) [:a :b]))))))

;; ---- marks and notices ----

(deftest a-failed-sign-in-marks-the-next-attempt-until-cleared
  (with-redefs [s/turnstile-enabled? (constantly true)]
    (on-each-store
     (fn [_]
       (is (false? (s/login-challenge-required? "Ann " "1.1.1.1")))
       (s/mark-login-challenge! "ann" "1.1.1.1")
       (is (true? (s/login-challenge-required? "Ann " "1.1.1.1")) "the typed text is trimmed and lowercased")
       (is (false? (s/login-challenge-required? "ann" "2.2.2.2")) "another connection is unmarked")
       (s/clear-login-challenge! "ann" "1.1.1.1")
       (is (false? (s/login-challenge-required? "ann" "1.1.1.1")))))))

(deftest a-mark-runs-out
  (on-each-store
   (fn [_]
     (let [store (#'s/store)]
       (s/-mark! store ["short" "ip"] 100)
       (is (s/-marked? store ["short" "ip"]))
       (Thread/sleep 250)
       (is (not (s/-marked? store ["short" "ip"])))))))

(deftest notices-follow-their-cadence-and-can-be-given-back
  (on-each-store
   (fn [_]
     (let [[now-fn at] (clock-from t0)
           gap (* 60 (:min-gap-hours (s/notices :sign-in-attempts)))
           claim #(s/claim-notice! :sign-in-attempts 42)]
       (with-redefs [time/now now-fn]
         (is (= {} (claim)) "the first attempt is told at once")
         (is (nil? (claim)) "a second inside the gap is not")
         (at (inc gap))
         (is (= {:since-last 2} (claim)) "after the gap, with how many came in")
         (s/release-notice! :sign-in-attempts 42 {:since-last 2})
         (is (= {:since-last 3} (claim)) "a claim given back is due again, the attempt since counted"))))))

(deftest concurrent-claims-tell-the-owner-once
  (on-each-store
   (fn [_]
     (let [told (->> (repeatedly 16 #(future (s/claim-notice! :taken-signup "a@b.c")))
                     doall (map deref) (filter some?))]
       (is (= 1 (count told)))))))

(deftest simultaneous-sightings-cannot-both-take-the-last-slot
  (on-each-store
   (fn [_]
     (let [seen (->> (repeatedly 20 #(future (count (#'s/sight! [:race "k"] (time/hours 1) nil))))
                     doall (map deref))]
       (is (= (range 20) (sort seen)) "each caller saw exactly the ones before it")))))

(deftest keys-are-prefixed-and-long-subjects-are-bounded
  (when-let [store @redis-conn]
    (s/use-store! store)
    (try
      (clear-redis! store)
      (#'s/sight! [:img (apply str (repeat 5000 "x"))] (time/hours 1) nil)
      (let [ks (wcar (:conn store) (car/keys "*"))
            mine (filter #(str/starts-with? % redis/prefix) ks)]
        (is (seq mine))
        (is (every? #(< (count %) 200) mine) "a sender's long text cannot make a long key"))
      (finally (clear-redis! store) (s/use-store! nil)))))

;; ---- a failing shared store never switches limits off ----

(def ^:private broken
  (reify s/LimitStore
    (-recent [_ _ _] (throw (java.net.SocketTimeoutException. "slow")))
    (-sight! [_ _ _ _] (throw (java.net.SocketTimeoutException. "slow")))
    (-busiest [_ _] (throw (java.net.SocketTimeoutException. "slow")))
    (-mark! [_ _ _] (throw (java.net.SocketTimeoutException. "slow")))
    (-marked? [_ _] (throw (java.net.SocketTimeoutException. "slow")))
    (-unmark! [_ _] (throw (java.net.SocketTimeoutException. "slow")))
    (-update-notice! [_ _ _ _] (throw (java.net.SocketTimeoutException. "slow")))))

(deftest a-throwing-store-falls-back-to-memory-and-limits-still-hold
  (reset! @#'s/sightings {})
  (reset! @#'s/login-challenges {})
  (reset! @#'s/notice-state {})
  (reset! @#'s/store-last-warned 0)
  (s/use-store! (s/->FallbackStore broken))
  (try
    (let [out (java.io.StringWriter.)]
      (binding [*out* out]
        (let [route :orcpub.routes.auth/register
              [_ n] (first (:limits (s/policy route)))
              results (vec (for [_ (range (inc n))] (some? (s/guard route {:ip "9.9.9.9"}))))]
          (is (= (concat (repeat n false) [true]) results) "the limit still refuses at its number")
          (with-redefs [s/turnstile-enabled? (constantly true)]
            (s/mark-login-challenge! "ann" "1.1.1.1")
            (is (true? (s/login-challenge-required? "ann" "1.1.1.1"))))
          (is (= {} (s/claim-notice! :taken-signup "x@y.z")))
          (is (nil? (s/claim-notice! :taken-signup "x@y.z")))
          (is (map? (s/busiest)))))
      (is (= 1 (count (re-seq #"limits: shared store failed" (str out))))
          "one warning, however many calls failed"))
    (finally (s/use-store! nil))))

(deftest without-redis-url-the-memory-store-is-used
  (is (nil? (env/value :redis-url)) "this suite runs on the memory store")
  (is (identical? s/memory-store (#'s/store))))
