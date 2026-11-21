(ns orcpub.routes.twitch-test
  (:require [charred.api :as cheshire]
            [clojure.test :refer [deftest is use-fixtures]]
            [orcpub.config :as config]
            [orcpub.routes.twitch :as twitch]))

(def ^:private calls (atom []))

(defn- stub
  "A *http* stand-in: records each call in `calls` and answers per `streams`, a fn of the call
   number (from 1) of streams requests returning a response map; token requests always succeed."
  [streams]
  (let [n (atom 0)]
    (fn [method url _opts]
      (swap! calls conj [method url])
      (if (= :post method)
        {:status 200 :body (cheshire/write-json-str {:access_token "tok" :expires_in 3600})}
        (streams (swap! n inc))))))

(defn- live-body
  "A streams response with one live stream."
  [] {:status 200 :body (cheshire/write-json-str {:data [{:type "live"}]})})
(defn- offline-body
  "A streams response with no streams."
  [] {:status 200 :body (cheshire/write-json-str {:data []})})

(defn- reset-fixture
  "Clears the module's caches and the call log around each test, with the keys set."
  [f]
  (twitch/reset-caches!)
  (reset! calls [])
  (with-redefs [config/twitch-client-id (constantly "id")
                config/twitch-client-secret (constantly "secret")]
    (f)))

(use-fixtures :each reset-fixture)

(defn- streams-calls
  "How many requests went to the streams endpoint."
  []
  (count (filter #(= :get (first %)) @calls)))

(deftest live-and-not-live
  (binding [twitch/*http* (stub (fn [_] (live-body)))]
    (is (true? (twitch/live? "fusspot"))))
  (twitch/reset-caches!)
  (binding [twitch/*http* (stub (fn [_] (offline-body)))]
    (is (false? (twitch/live? "fusspot")))))

(deftest answers-are-cached-for-two-minutes
  (binding [twitch/*http* (stub (fn [_] (live-body)))]
    (twitch/live? "fusspot")
    (twitch/live? "FussPot")
    (is (= 1 (streams-calls)))))

(deftest an-unlisted-login-is-never-sent-to-twitch
  (binding [twitch/*http* (stub (fn [_] (live-body)))]
    (is (false? (twitch/live? "someone_else")))
    (is (false? (twitch/live? nil)))
    (is (empty? @calls))))

(deftest feature-off-makes-no-calls
  (with-redefs [config/twitch-client-secret (constantly nil)]
    (binding [twitch/*http* (stub (fn [_] (live-body)))]
      (is (false? (twitch/live? "fusspot")))
      (is (empty? @calls)))))

(deftest a-failure-answers-not-live-and-is-cached
  (binding [twitch/*http* (stub (fn [_] {:status 500 :body "oops"}))]
    (is (false? (twitch/live? "fusspot")))
    (is (false? (twitch/live? "fusspot")))
    (is (= 1 (streams-calls)))))

(deftest a-401-refreshes-the-token-once
  (binding [twitch/*http* (stub (fn [n] (if (= 1 n) {:status 401 :body ""} (live-body))))]
    (is (true? (twitch/live? "fusspot")))
    (is (= 2 (streams-calls)))
    (is (= 2 (count (filter #(= :post (first %)) @calls))))))

(deftest the-route-answers-json
  (binding [twitch/*http* (stub (fn [_] (live-body)))]
    (is (= {:live true}
           (cheshire/read-json (:body (twitch/live-route {:query-params {:login "fusspot"}})) :key-fn keyword)))
    (is (= {:live false}
           (cheshire/read-json (:body (twitch/live-route {:query-params {:login "nobody"}})) :key-fn keyword)))))
