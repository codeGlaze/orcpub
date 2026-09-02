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
