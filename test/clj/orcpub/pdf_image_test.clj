(ns orcpub.pdf-image-test
  "The PDF exporter fetches a URL the user supplied, from the server, on an
   unauthenticated endpoint. These pin the limits that make that safe."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.pdf :as pdf]
            [orcpub.routes]
            [clojure.java.io]))

(deftest only-http-and-https-are-fetchable
  (testing "schemes the route's own filter used to allow"
    ;; #"^(https?|ftp|file)://..." permitted both of these.
    (is (not (pdf/safe-image-url? "file:///etc/passwd")))
    (is (not (pdf/safe-image-url? "ftp://example.com/x.png")))
    (is (not (pdf/safe-image-url? "jar:file:///x.jar!/y.png")))
    (is (not (pdf/safe-image-url? "gopher://example.com/x")))))

(deftest private-and-metadata-addresses-are-refused
  (testing "the addresses an SSRF is actually aimed at"
    (is (not (pdf/safe-image-url? "http://169.254.169.254/latest/meta-data/"))
        "cloud instance metadata")
    (is (not (pdf/safe-image-url? "http://127.0.0.1:8890/")))
    (is (not (pdf/safe-image-url? "http://localhost:5432/")))
    (is (not (pdf/safe-image-url? "http://10.0.0.1/")))
    (is (not (pdf/safe-image-url? "http://192.168.1.1/")))
    (is (not (pdf/safe-image-url? "http://172.16.0.1/")))
    (is (not (pdf/safe-image-url? "http://[::1]/")))))

(deftest modern-private-ipv6-is-refused
  (testing "fc00::/7, the range an internal IPv6 network actually uses"
    ;; InetAddress.isSiteLocalAddress only knows fec0::/10, deprecated in 2004,
    ;; so relying on it alone let every real private IPv6 address through.
    (is (not (pdf/safe-image-url? "http://[fd00::1]/")))
    (is (not (pdf/safe-image-url? "http://[fc00::1]/")))
    (is (not (pdf/safe-image-url? "http://[fdff:ffff::1]/"))))
  (testing "and the ranges it does know still are"
    (is (not (pdf/safe-image-url? "http://[fe80::1]/")))
    (is (not (pdf/safe-image-url? "http://[fec0::1]/")))))

(deftest a-v4-address-wrapped-in-a-v6-one-is-unwrapped
  (testing "NAT64: on a network running it, 64:ff9b::7f00:1 IS 127.0.0.1"
    (is (not (pdf/safe-image-url? "http://[64:ff9b::7f00:1]/")))
    (is (not (pdf/safe-image-url? "http://[64:ff9b::a00:1]/"))))
  (testing "6to4: 2002:7f00:1:: carries the same address"
    (is (not (pdf/safe-image-url? "http://[2002:7f00:1::1]/"))))
  (testing "the mapped form java normalises for us"
    (is (not (pdf/safe-image-url? "http://[::ffff:127.0.0.1]/")))
    (is (not (pdf/safe-image-url? "http://[::ffff:10.0.0.1]/")))))

(deftest reserved-ipv4-blocks-are-refused
  (testing "carrier-grade NAT, where cloud providers put internal services"
    (is (not (pdf/safe-image-url? "http://100.64.0.1/")))
    (is (not (pdf/safe-image-url? "http://100.127.255.254/"))))
  (testing "the other blocks that are not routable public internet"
    (is (not (pdf/safe-image-url? "http://0.0.0.5/")) "0.0.0.0/8")
    (is (not (pdf/safe-image-url? "http://240.0.0.1/")) "240.0.0.0/4")
    (is (not (pdf/safe-image-url? "http://255.255.255.255/")) "broadcast")
    (is (not (pdf/safe-image-url? "http://192.0.0.1/")) "192.0.0.0/24")
    (is (not (pdf/safe-image-url? "http://198.18.0.1/")) "198.18.0.0/15")))

(deftest the-guard-does-not-over-block
  ;; A deny list that refuses ordinary addresses is a broken feature, not a safe
  ;; one. These are all public and must stay fetchable.
  (testing "public IPv4 and IPv6 literals"
    (doseq [h ["8.8.8.8" "1.1.1.1" "93.184.216.34"
               "[2606:4700:4700::1111]" "[2001:4860:4860::8888]"]]
      (is (pdf/safe-image-url? (str "http://" h "/portrait.png")) h)))
  (testing "addresses just outside a blocked range"
    (is (pdf/safe-image-url? "http://172.32.0.1/x.png") "just past 172.16/12")
    (is (pdf/safe-image-url? "http://100.128.0.1/x.png") "just past 100.64/10"))
  (testing "a transition wrapper carrying a PUBLIC address is fine"
    ;; The wrapper is not the problem; what is inside it is.
    (is (pdf/safe-image-url? "http://[64:ff9b::808:808]/x.png") "NAT64 of 8.8.8.8")
    (is (pdf/safe-image-url? "http://[2002:0808:0808::1]/x.png") "6to4 of 8.8.8.8")))

(deftest a-response-that-is-not-an-image-is-refused
  (testing "an SSRF that got through would return a page, not a picture"
    (let [within? #'orcpub.pdf/within-pixel-budget?]
      (is (false? (within? (.getBytes "<html><body>internal admin</body></html>"))))
      (is (false? (within? (byte-array 0)))))))

(deftest a-decimal-encoded-loopback-is-still-loopback
  (testing "127.0.0.1 written as an integer resolves the same"
    ;; A textual check on the host string would miss this; resolving does not.
    (is (not (pdf/safe-image-url? "http://2130706433/")))))

(deftest garbage-is-refused-rather-than-thrown
  (testing "a malformed URL is skipped like an image that failed to load"
    (is (not (pdf/safe-image-url? "not a url")))
    (is (not (pdf/safe-image-url? "")))
    (is (not (pdf/safe-image-url? nil)))))

(deftest an-ordinary-public-image-url-is-allowed
  (testing "the guard does not refuse the normal case"
    ;; Resolves a real public host; skipped when the sandbox has no DNS.
    (let [resolvable? (try (java.net.InetAddress/getByName "example.com") true
                           (catch Exception _ false))]
      (when resolvable?
        (is (pdf/safe-image-url? "https://example.com/portrait.png"))
        (is (pdf/safe-image-url? "http://example.com/portrait.png"))))))

(deftest an-image-bomb-is-rejected-from-its-header
  (testing "dimensions are checked before any pixel buffer is allocated"
    ;; A 69-byte PNG whose IHDR claims 25000x25000. ImageIO/read would honour
    ;; that and allocate 2.5GB; reading the header costs the 69 bytes.
    (let [bomb (byte-array
                (concat
                 [-119 80 78 71 13 10 26 10]                       ; PNG magic
                 ;; IHDR: 25000 x 25000, 8-bit RGBA
                 [0 0 0 13] (map int "IHDR")
                 [0 0 97 -88 0 0 97 -88 8 6 0 0 0]
                 [-25 -122 -103 -87]))                             ; IHDR CRC
          within? #'orcpub.pdf/within-pixel-budget?]
      (is (false? (within? bomb))
          "a header claiming 625 million pixels is refused"))))

(deftest an-ordinary-portrait-passes-the-pixel-budget
  (let [img (java.awt.image.BufferedImage. 400 600 java.awt.image.BufferedImage/TYPE_INT_RGB)
        out (java.io.ByteArrayOutputStream.)]
    (javax.imageio.ImageIO/write img "png" out)
    (let [within? #'orcpub.pdf/within-pixel-budget?]
      (is (true? (within? (.toByteArray out)))
          "400x600 is well inside the budget"))))

(deftest only-styles-with-a-template-are-accepted
  (testing "the ids that have a master on disk"
    ;; (2026-09) Each style ships one master, grown per character, rather than
    ;; seven pre-cut variants. pdf/sheet-masters is the list.
    (is (= #{1 2 3 4} orcpub.routes/valid-sheet-styles))
    (doseq [n orcpub.routes/valid-sheet-styles
            :let [{:keys [file without-casters]} (get orcpub.pdf/sheet-masters n)]]
      (is (some? file) (str "style " n " must name a master"))
      (is (some? (clojure.java.io/resource file))
          (str "style " n "'s master must be on disk: " file))
      (when without-casters
        (is (some? (clojure.java.io/resource without-casters))
            (str "style " n "'s no-caster variant must be on disk: " without-casters))))))

(deftest the-fallback-style-has-a-template
  (testing "so an absent or bogus style cannot produce a missing resource"
    ;; A missing field used to yield "fillable-char-sheetstyle--0-spells.pdf",
    ;; io/resource nil, and an NPE -- a 500 anyone could trigger by omitting a
    ;; field on an unauthenticated endpoint.
    (is (contains? orcpub.routes/valid-sheet-styles orcpub.routes/default-sheet-style))
    (is (some? (clojure.java.io/resource
                (:file (get orcpub.pdf/sheet-masters
                            orcpub.routes/default-sheet-style)))))))

;; ─── Fetching, and what a caller has to do first ─────────────────────────────

(deftest fetch-image-answers-nil-rather-than-throwing
  ;; A picture that will not load must not cost the character their sheet: the
  ;; export draws what it got and carries on. Every one of these is refused by
  ;; validated-addresses inside safe-image-bytes, which is the check the fetch is
  ;; then pinned to -- so a caller does not need safe-image-url? first, and one
  ;; that calls it resolves the host twice.
  (testing "a URL the guard refuses comes back as nil"
    (doseq [url ["file:///etc/passwd"
                 "ftp://example.com/x.png"
                 "http://169.254.169.254/latest/meta-data/"
                 "http://127.0.0.1:8890/portrait.png"
                 "http://10.0.0.1/x.png"]]
      (is (nil? (pdf/fetch-image url)) url))))

(deftest jpeg-urls-embed-without-re-encoding
  ;; JPEG bytes go into the file as they are; anything else is decoded and
  ;; re-encoded losslessly because PDFBox will not take it otherwise. This is the
  ;; flag that picks between them, so it decides whether a portrait is recompressed.
  (testing "the extension decides, case-insensitively"
    (is (pdf/jpeg-url? "https://example.com/a.jpg"))
    (is (pdf/jpeg-url? "https://example.com/a.JPEG"))
    (is (pdf/jpeg-url? "https://example.com/A.Jpg"))
    (is (not (pdf/jpeg-url? "https://example.com/a.png")))
    (is (not (pdf/jpeg-url? "https://example.com/a.gif")))
    (is (not (pdf/jpeg-url? "https://example.com/jpg/a.png"))
        "the path, not a directory that happens to be called jpg")
    (is (not (pdf/jpeg-url? nil)))))
