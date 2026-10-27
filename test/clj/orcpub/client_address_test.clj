(ns orcpub.client-address-test
  "The player's address behind proxies: found through the proxies we trust, never taken from a
   header a client wrote."
  (:require [clojure.test :refer [deftest is testing]]
            [orcpub.client-address :as ca]))

(def ^:private with-cloudflare
  "Ranges a Cloudflare-fronted site trusts."
  (ca/trusted-ranges nil true))

(def ^:private without-cloudflare
  "Ranges a site with no Cloudflare trusts."
  (ca/trusted-ranges nil false))

(defn- addr
  "The address `client-address` finds for a request from `peer` with X-Forwarded-For `xff`,
   trusting `trusted`."
  [trusted peer xff & [real-ip]]
  (ca/client-address {:remote-addr peer
                      :headers (cond-> {}
                                 xff (assoc "x-forwarded-for" xff)
                                 real-ip (assoc "x-real-ip" real-ip))}
                     trusted))

(deftest the-live-chain-finds-the-player
  (testing "Cloudflare -> Traefik -> app, as in a live error report"
    (is (= "97.126.172.119" (addr with-cloudflare "10.0.38.49" "97.126.172.119, 104.23.160.222")))))

(deftest a-client-cannot-choose-its-address
  (testing "a header sent straight to the app, not through a proxy, is ignored"
    (is (= "203.0.113.50" (addr with-cloudflare "203.0.113.50" "1.2.3.4"))))
  (testing "a header sent through Cloudflare: entries left of the real ones are the client's own"
    (is (= "97.126.172.119"
           (addr with-cloudflare "10.0.38.49" "1.2.3.4, 97.126.172.119, 104.23.160.222")))))

(deftest cloudflare-is-trusted-only-when-turned-on
  (testing "without TRUST_CLOUDFLARE a Cloudflare edge is just another address"
    (is (= "104.23.160.222" (addr without-cloudflare "10.0.38.49" "97.126.172.119, 104.23.160.222")))))

(deftest other-setups
  (testing "no proxy at all: the connecting address"
    (is (= "198.51.100.7" (addr without-cloudflare "198.51.100.7" nil))))
  (testing "nginx in front, which sends X-Real-IP only"
    (is (= "198.51.100.7" (addr without-cloudflare "172.18.0.4" nil "198.51.100.7"))))
  (testing "IPv6 through Cloudflare"
    (is (= "2001:db8::1" (addr with-cloudflare "10.0.0.2" "2001:db8::1, 2606:4700::6810:1"))))
  (testing "a proxy named in TRUSTED_PROXIES is skipped"
    (is (= "198.51.100.7"
           (addr (ca/trusted-ranges "203.0.113.10" false) "10.0.0.2" "198.51.100.7, 203.0.113.10"))))
  (testing "every hop trusted: the leftmost forwarded address"
    (is (= "10.1.2.3" (addr without-cloudflare "10.0.0.2" "10.1.2.3, 10.0.0.9")))))

(deftest junk-is-never-looked-up
  (testing "a name in the header is not an address and is never resolved"
    (is (= "10.0.0.2" (addr without-cloudflare "10.0.0.2" "beef, example.com")))
    (is (nil? (ca/parse-cidr "beef")))
    (is (nil? (ca/parse-cidr "example.com/8")))))

(deftest ranges-match-as-written
  (is (ca/in-range? "104.23.160.222" (ca/parse-cidr "104.16.0.0/13")))
  (is (not (ca/in-range? "104.24.0.1" (ca/parse-cidr "104.16.0.0/13"))))
  (is (ca/in-range? "172.31.255.255" (ca/parse-cidr "172.16.0.0/12")))
  (is (not (ca/in-range? "172.32.0.1" (ca/parse-cidr "172.16.0.0/12"))))
  (is (not (ca/in-range? "::1" (ca/parse-cidr "10.0.0.0/8"))) "IPv4 ranges never match IPv6"))

(deftest the-interceptor-fixes-the-request
  (let [enter (:enter (ca/interceptor-for with-cloudflare))
        req (:request (enter {:request {:remote-addr "10.0.38.49"
                                        :headers {"x-forwarded-for" "97.126.172.119, 104.23.160.222"}}}))]
    (is (= "97.126.172.119" (:remote-addr req)) "every limit and log line now sees the player")
    (is (= "10.0.38.49" (:peer-addr req)) "and the proxy is kept")))
