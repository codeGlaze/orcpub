(ns orcpub.pdf-image-fetch-test
  "The transport half of the image guard, against a real HTTP server.

   pdf-image-test covers which URLs are allowed. These cover what happens once
   one is: the caps have to hold against a server that lies about the size, that
   redirects, or that simply is not serving an image.

   The server is on loopback, which safe-image-url? refuses by design, so these
   drive open-image-stream and read-bounded-bytes directly. That split is the
   point -- the address guard and the transport guard are separate defences and a
   test that could only reach them together would prove neither."
  ;; explicit :refer to avoid namespace pollution from :refer :all
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [orcpub.pdf :as pdf]
            [clj-http.client])
  (:import (com.sun.net.httpserver HttpServer HttpHandler)
           (java.net InetSocketAddress)
           (javax.imageio ImageIO)
           (java.awt.image BufferedImage)
           (java.io ByteArrayOutputStream)))

(def ^:private open-image-stream #'pdf/open-image-stream)
(def ^:private pinned-connection-manager #'pdf/pinned-connection-manager)
(def ^:private proxied? #'pdf/proxied?)

(def ^:private loopback
  "The fixture server's address, supplied explicitly.

   validated-addresses refuses loopback, which is correct and is why these tests
   hand open-image-stream the address list directly. That split is the point: the
   address guard decides WHETHER to fetch, the pin decides WHERE, and each is
   tested on its own."
  [(java.net.InetAddress/getByName "127.0.0.1")])

(defn- fetch
  "open-image-stream against the fixture server, applying `f` to the body."
  [url f]
  (let [[in cm] (open-image-stream url loopback)]
    (try (f in)
         (finally (.close ^java.io.InputStream in) (some-> cm .close)))))
(def ^:private read-bounded-bytes #'pdf/read-bounded-bytes)
(def ^:private within-pixel-budget? #'pdf/within-pixel-budget?)

(def ^:private cap (* 128 1024))
(def ^:private server (atom nil))
(def ^:dynamic *base* "Base URL of the fixture server." nil)

(defn- png-bytes [w h]
  (let [out (ByteArrayOutputStream.)]
    (ImageIO/write (BufferedImage. w h BufferedImage/TYPE_INT_RGB) "png" out)
    (.toByteArray out)))

(def ^:private chunked
  "sendResponseHeaders' code for chunked transfer, which sends no Content-Length.
   Its code for `no body at all` is -1, and passing that while writing bytes
   silently discards them -- which makes a cap test pass without ever sending
   anything to cap."
  0)

(defn- respond [exchange status ^bytes body-bytes content-length]
  (.sendResponseHeaders exchange status (long content-length))
  (with-open [os (.getResponseBody exchange)]
    (when (seq body-bytes) (.write os body-bytes)))
  (.close exchange))

(defn- handler [f] (reify HttpHandler (handle [_ exchange] (f exchange))))

(defn- start! []
  (let [s (HttpServer/create (InetSocketAddress. "127.0.0.1" 0) 0)
        big (byte-array (* 200 1024))]
    (doto s
      (.createContext "/ok" (let [png (png-bytes 40 40)]
                              (handler #(respond % 200 png (count png)))))
      ;; Declares its real, over-cap size: refused from the header, nothing read.
      (.createContext "/too-big" (handler #(respond % 200 big (count big))))
      ;; Sends 200 KB chunked, so there is no Content-Length to check. Only the
      ;; streaming cap stands between the export and whatever the server sends.
      (.createContext "/undeclared-length" (handler #(respond % 200 big chunked)))
      (.createContext "/redirect"
                      (handler (fn [e]
                                 (.set (.getResponseHeaders e) "Location" "http://169.254.169.254/")
                                 (respond e 302 nil -1))))
      (.createContext "/not-found" (handler #(respond % 404 (.getBytes "nope") chunked)))
      (.createContext "/html" (handler #(respond % 200 (.getBytes "<html>admin</html>") chunked)))
      ;; Sends a byte at a time, slowly, forever-ish. Every individual read
      ;; returns well inside the read timeout, so only a total deadline stops it.
      (.createContext "/trickle"
                      (handler (fn [e]
                                 (.sendResponseHeaders e 200 (long chunked))
                                 (try
                                   (with-open [os (.getResponseBody e)]
                                     (dotimes [_ 40]
                                       (.write os (int 65))
                                       (.flush os)
                                       (Thread/sleep 300)))
                                   (catch Exception _ nil)
                                   (finally (.close e))))))
      (.setExecutor nil)
      (.start))
    (reset! server s)
    (str "http://127.0.0.1:" (.getPort (.getAddress s)))))

(defn- with-server [f]
  (let [base (start!)]
    (try (binding [*base* base] (f))
         (finally (.stop ^HttpServer @server 0)))))

(use-fixtures :once with-server)

(deftest an-ordinary-image-is-fetched
  (testing "the guard does not break the normal case"
    (fetch (str *base* "/ok")
           (fn [in] (let [data (read-bounded-bytes in)]
                      (is (pos? (count data)))
                      (is (true? (within-pixel-budget? data))))))))

(deftest an-oversized-body-is-refused-from-its-header
  (testing "a truthful Content-Length over the cap is rejected before any read"
    (is (thrown? clojure.lang.ExceptionInfo
                 (open-image-stream (str *base* "/too-big") loopback)))))

(deftest a-body-with-no-declared-length-is-still-bounded
  ;; The header check is an optimisation, not the limit. A chunked response
  ;; carries no Content-Length at all, so only the streaming cap stands between
  ;; the export and however much the server feels like sending.
  (testing "200 KB sent chunked still stops at the cap"
    (fetch (str *base* "/undeclared-length")
           (fn [in]
             (let [e (try (read-bounded-bytes in) nil
                          (catch clojure.lang.ExceptionInfo e e))]
               (is (some? e) "the stream must not be read to completion")
               (is (= :image-too-large (:error (ex-data e))))
               (is (<= (:bytes (ex-data e)) (+ cap 8192))
                   "it stops at the cap, not after buffering everything"))))))

(deftest a-redirect-is-not-followed
  ;; Redirects defeat the address check: the host that was validated is not the
  ;; host that answers. This one points at instance metadata.
  (testing "a 302 is an error, not a hop"
    (let [e (try (open-image-stream (str *base* "/redirect") loopback) nil
                 (catch clojure.lang.ExceptionInfo e e))]
      (is (some? e))
      (is (= 302 (:status (ex-data e)))))))

(deftest an-error-status-is-refused
  (testing "a 404 body is not fed to the image decoder"
    (is (thrown? clojure.lang.ExceptionInfo
                 (open-image-stream (str *base* "/not-found") loopback)))))

(deftest a-slow-trickle-cannot-hold-the-connection-open
  ;; The byte cap does not bound TIME. Each read here lands well inside the read
  ;; timeout, so without a total deadline this holds an export slot for as long
  ;; as the server cares to keep dribbling.
  (testing "a total deadline ends it, even though no single read times out"
    (fetch (str *base* "/trickle")
           (fn [in]
             (let [started (System/currentTimeMillis)
                   e (try (read-bounded-bytes in 1000) nil
                          (catch clojure.lang.ExceptionInfo e e))
                   elapsed (- (System/currentTimeMillis) started)]
               (is (some? e) "the transfer must not be allowed to run on")
               (is (= :image-transfer-timeout (:error (ex-data e))))
               (is (< elapsed 6000)
                   (str "gave up after " elapsed "ms, before the server finished")))))))

(deftest a-page-served-with-200-is-not-an-image
  (testing "what a successful SSRF would actually return"
    (fetch (str *base* "/html")
           (fn [in] (is (false? (within-pixel-budget? (read-bounded-bytes in))))))))

(deftest these-tests-exercise-the-pinned-path
  ;; open-image-stream skips the pin when a proxy applies, because a proxy does
  ;; its own name resolution. If the fixture URL were ever proxied, every test
  ;; below would quietly be testing the UNPINNED path and still pass.
  (is (false? (proxied? (str *base* "/ok")))
      "loopback must not be proxied, or these tests prove nothing about the pin"))

(deftest the-connection-uses-the-addresses-that-were-checked
  ;; The whole point of the pin. safe-image-url? resolves and judges; without
  ;; handing that answer to the connection, the connection resolves AGAIN and a
  ;; hostile DNS server can answer differently the second time.
  (testing "the fetch goes to the address it was given, not to whatever DNS says"
    ;; validated-addresses refuses loopback outright, so a fetch that reaches the
    ;; fixture server at all proves the supplied address was used.
    (fetch (str *base* "/ok") (fn [in] (is (pos? (count (read-bounded-bytes in))))))))

(deftest the-pin-refuses-any-host-it-was-not-given
  (testing "a route to another host cannot be resolved on this client"
    ;; No DNS is consulted, so this holds with or without a network.
    (let [cm (pinned-connection-manager "pinned.invalid" loopback)]
      (try
        (is (thrown? Exception
                     (clj-http.client/get "http://somewhere-else.invalid/x.png"
                                          {:connection-manager cm
                                           :throw-exceptions false
                                           :socket-timeout 2000
                                           :connection-timeout 2000})))
        (finally (.close cm))))))
