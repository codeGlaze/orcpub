(ns orcpub.csp-test
  "Tests for CSP (Content Security Policy) nonce generation and header building."
  ;; explicit :refer to avoid namespace pollution from :refer :all
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.string :as str]
            [orcpub.csp :as csp]
            [orcpub.pedestal :as pedestal]))

(deftest nonce-generation
  (testing "Nonces are unique per call"
    (let [nonces (repeatedly 100 csp/generate-nonce)]
      (is (= 100 (count (set nonces)))
          "100 nonces should all be unique")))

  (testing "Nonces are 128-bit base64 encoded"
    (let [nonce (csp/generate-nonce)]
      ;; 16 bytes base64 encoded = 24 chars (with padding) or 22 (without)
      (is (<= 22 (count nonce) 24)
          (str "Nonce length should be 22-24 chars, got " (count nonce)))
      ;; Should be valid base64 characters
      (is (re-matches #"[A-Za-z0-9+/=]+" nonce)
          "Nonce should contain only base64 characters"))))

(deftest csp-header-format
  (testing "Header contains nonce"
    (let [nonce "abc123XYZ"
          header (csp/build-csp-header nonce)]
      (is (str/includes? header (str "'nonce-" nonce "'"))
          "Header should contain nonce in 'nonce-xxx' format")
      (is (str/includes? header "'strict-dynamic'")
          "Header should contain 'strict-dynamic'")))

  (testing "Header contains required security directives"
    (let [header (csp/build-csp-header "test-nonce")]
      (is (str/includes? header "default-src 'self'")
          "Should have default-src directive")
      (is (str/includes? header "object-src 'none'")
          "Should block object embeds")
      (is (str/includes? header "base-uri 'self'")
          "Should restrict base-uri")
      (is (str/includes? header "frame-ancestors 'self'")
          "Should restrict frame-ancestors")
      (is (str/includes? header "form-action 'self'")
          "Should restrict form-action")))

  (testing "Production mode does not include WebSocket"
    (let [header (csp/build-csp-header "test")]
      (is (not (str/includes? header "ws://"))
          "Production header should not include WebSocket")))

  (testing "Dev mode adds Figwheel WebSocket"
    (let [header (csp/build-csp-header "test" :dev-mode? true)]
      (is (str/includes? header "ws://localhost:3449")
          "Dev mode header should include Figwheel WebSocket"))))

(deftest csp-header-does-not-have-unsafe-inline-for-scripts
  (testing "Strict mode should NOT have unsafe-inline in script-src"
    (let [header (csp/build-csp-header "test-nonce")
          ;; Extract just the script-src directive
          script-src-match (re-find #"script-src[^;]+" header)]
      (is (not (str/includes? (or script-src-match "") "'unsafe-inline'"))
          "script-src should NOT contain 'unsafe-inline' in strict mode"))))

(deftest nonce-interceptor-header-type
  (testing "Dev mode interceptor is a no-op (no CSP header added)"
    (let [interceptor (pedestal/make-nonce-interceptor true)
          ctx {:request {}
               :response {:status 200 :body "test"}}
          ;; Run :enter phase — should NOT add a nonce in dev mode
          enter-fn (get-in interceptor [:enter])
          after-enter (enter-fn ctx)]
      (is (nil? (get-in after-enter [:request :csp-nonce]))
          "Dev mode :enter should not generate a nonce")
      ;; Run :leave phase — without a nonce, should not add any header
      (let [leave-fn (get-in interceptor [:leave])
            result (leave-fn after-enter)]
        (is (not (contains? (get-in result [:response :headers]) "Content-Security-Policy"))
            "Dev mode should NOT add Content-Security-Policy header")
        (is (not (contains? (get-in result [:response :headers]) "Content-Security-Policy-Report-Only"))
            "Dev mode should NOT add Content-Security-Policy-Report-Only header"))))

  (testing "Prod mode interceptor uses enforcing header"
    (let [interceptor (pedestal/make-nonce-interceptor false)
          ctx {:request {}
               :response {:status 200 :body "test"}}
          ctx-with-nonce (assoc-in ctx [:request :csp-nonce] "test-nonce")
          leave-fn (get-in interceptor [:leave])
          result (leave-fn ctx-with-nonce)]
      (is (contains? (get-in result [:response :headers]) "Content-Security-Policy")
          "Prod mode should use Content-Security-Policy header")
      (is (not (contains? (get-in result [:response :headers]) "Content-Security-Policy-Report-Only"))
          "Prod mode should NOT use Content-Security-Policy-Report-Only header"))))
