(ns orcpub.dnd.e5.share-url
  "Browser-side codec that turns a homebrew bundle (from share-bundle) into a
   compact, URL-safe fragment payload and back — the wire format for the
   \"embed the content in the shared link\" feature.

   Everything is Promise-based because gzip goes through the native
   CompressionStream API, which is async.

   SECURITY: a shared payload is fully untrusted (the attacker controls the URL;
   a hash would only prove it didn't corrupt in transit, not that it's safe — and
   with no server secret there is nothing to sign with). `decode-shared` therefore
   applies, fail-closed at every step:
     1. input cap        — reject an oversized fragment before any work
     3. bomb-capped gunzip — abort if the decompressed stream exceeds a hard cap
                             (a tiny gzip can inflate to gigabytes)
     4. safe EDN read    — cljs.reader/read-string (no #= eval); unknown reader
                           tags throw rather than construct
     5. structural whitelist — share-bundle/whitelist-bundle keeps only the exact
                               {source {known-type {letter-kw def}}} shape
   Layer 6 (content sanitize + per-type spec) is applied by the .orcbrew import
   path when the caller actually loads the returned bundle — sharing cannot bypass
   the gate a file upload goes through.

   The payload is version-prefixed (\"1\") so the format can evolve."
  (:require [clojure.string :as str]
            [cljs.reader :as reader]
            [goog.crypt.base64 :as b64]
            [orcpub.dnd.e5.share-bundle :as sb]))

(def ^:private version "1")

(def url-budget
  "A comfortable clean length (chars) for the fragment payload — a link this size
   pastes fine into WhatsApp, email, Signal, iMessage, etc. Longer than this still
   works but gets a 'long link' caveat (some apps like Discord/SMS truncate)."
  16000)

(def ^:private max-link-chars
  "Above this, a link isn't a viable transport — fall back to a downloadable file.
   Kept under max-fragment-chars so anything we PRODUCE can also be DECODED."
  150000)

(def ^:private max-fragment-chars
  "Hard reject for an incoming payload before any decode work (anti-DoS)."
  200000)

(def ^:private max-decompressed-bytes
  "Hard cap on gunzip OUTPUT — aborts decompression bombs."
  (* 4 1024 1024))

(defn supported?
  "True when the browser exposes the compression APIs this codec needs."
  []
  (and (exists? js/CompressionStream) (exists? js/DecompressionStream)))

;; ── bytes / base64url ────────────────────────────────────────────────────────

(defn- str->bytes [s] (.encode (js/TextEncoder.) s))
(defn- bytes->str [u8] (.decode (js/TextDecoder.) u8))

(defn- b64url-encode [u8]
  (-> (b64/encodeByteArray u8)
      (str/replace "+" "-") (str/replace "/" "_") (str/replace "=" "")))

(defn- b64url-decode [s]
  (b64/decodeStringToUint8Array (-> s (str/replace "-" "+") (str/replace "_" "/"))))

;; ── gzip / gunzip ────────────────────────────────────────────────────────────

(defn- gzip [u8]
  (let [stream (.pipeThrough (.stream (js/Blob. #js [u8])) (js/CompressionStream. "gzip"))]
    (-> (js/Response. stream) (.arrayBuffer) (.then #(js/Uint8Array. %)))))

(defn- concat-chunks [chunks total]
  (let [out (js/Uint8Array. total)]
    (loop [i 0 off 0]
      (if (< i (.-length chunks))
        (let [c (aget chunks i)]
          (.set out c off)
          (recur (inc i) (+ off (.-length c))))
        out))))

(defn- read-capped
  "Drain a ReadableStream of Uint8Array chunks into one Uint8Array, rejecting if
   the cumulative size exceeds `cap` (decompression-bomb guard)."
  [readable cap]
  (let [rdr (.getReader readable)
        chunks (array)
        total (atom 0)]
    (letfn [(pump []
              (-> (.read rdr)
                  (.then (fn [res]
                           (if (.-done res)
                             (concat-chunks chunks @total)
                             (let [piece (.-value res)]
                               (swap! total + (.-length piece))
                               (if (> @total cap)
                                 (do (.cancel rdr)
                                     (throw (ex-info "decompressed payload too large" {})))
                                 (do (.push chunks piece) (pump)))))))))]
      (pump))))

(defn- gunzip-capped [u8 cap]
  (let [stream (.pipeThrough (.stream (js/Blob. #js [u8])) (js/DecompressionStream. "gzip"))]
    (read-capped stream cap)))

;; ── safe EDN read ────────────────────────────────────────────────────────────

(defn- safe-read-edn
  "cljs.reader/read-string does not eval (#= is a no-op) and throws on unknown
   reader tags rather than constructing anything. Any failure => sentinel."
  [s]
  (try (reader/read-string s) (catch :default _ ::read-error)))

;; ── public: encode ───────────────────────────────────────────────────────────

(defn- encode-edn [edn-str]
  (-> (gzip (str->bytes edn-str))
      (.then (fn [gz] (str version (b64url-encode gz))))))

(defn build-share-payload
  "bundle -> Promise of {:tier :full|:long|:file :payload s|nil}.
   We NEVER drop the character's content to fit — a link that carries names but no
   descriptions is useless (a feat/trait IS its description). So: ship the full
   payload when it's a comfortable length (:full); still ship the full payload but
   flag that very long links can be truncated by some apps (:long); and only when
   it's too large for any link fall back to a downloadable file (:file). Resolves
   :file immediately when compression is unsupported."
  [bundle]
  (if-not (supported?)
    (js/Promise.resolve {:tier :file :payload nil})
    (-> (encode-edn (sb/bundle->edn bundle))
        (.then (fn [full]
                 (let [n (count full)]
                   (cond
                     (<= n url-budget)     {:tier :full :payload full}
                     (<= n max-link-chars) {:tier :long :payload full}
                     :else                 {:tier :file :payload nil})))))))

;; ── public: decode (untrusted) ───────────────────────────────────────────────

(defn- read-shared-edn
  "The last layers every incoming share passes: safe EDN read, then the structural whitelist."
  [edn-str]
  (let [data (safe-read-edn edn-str)]
    (if (= data ::read-error)
      {:error :parse}
      (sb/whitelist-shared data))))

(defn decode-shared
  "Decode + structurally validate an untrusted fragment payload. Returns a Promise
   resolving to {:plugins m :custom-items [...] :dropped n} on success, or
   {:error kw} on any failure (:empty :too-large :version :unsupported :parse
   :decode). Security layers 1-5; layer 6 (content sanitize/spec) is applied when
   the caller imports the result."
  [payload]
  (cond
    (or (nil? payload) (not (string? payload)) (str/blank? payload))
    (js/Promise.resolve {:error :empty})

    (> (count payload) max-fragment-chars)
    (js/Promise.resolve {:error :too-large})

    (not (str/starts-with? payload version))
    (js/Promise.resolve {:error :version})

    (not (supported?))
    (js/Promise.resolve {:error :unsupported})

    :else
    (-> (js/Promise.resolve (subs payload (count version)))
        (.then (fn [b64] (b64url-decode b64)))
        (.then (fn [bytes] (gunzip-capped bytes max-decompressed-bytes)))
        (.then (fn [out] (bytes->str out)))
        (.then read-shared-edn)
        (.catch (fn [_] {:error :decode})))))

;; ── encrypted snapshots: short links ─────────────────────────────────────────
;; A link can carry "#s=<share id>.<key>" instead of the bundle. The server holds the encrypted
;; bundle (orcpub.routes.share) and never sees the key, which stays after the #. The key is derived
;; from the content and the character id, so the same content gives the same blob and id and uploading
;; it again stores nothing new. Someone who already has the exact content could confirm a match;
;; nobody can read a snapshot without its link.

(def max-snapshot-edn-bytes
  "The most text a snapshot unpacks to, which also bounds what a viewer's browser decompresses. A
   packed level 20 character measured 61 to 110 KB."
  (* 1024 1024))

(def max-snapshot-blob-chars
  "The most a snapshot stores, as orcpub.routes.share/max-blob-chars allows. Measured snapshots of
   packed level 20 characters store 21 to 32 KB."
  (* 256 1024))

(def ^:private snapshot-version "2")

(defn encryption-supported?
  "True when the browser can compress and encrypt. Web Crypto exists only on https and localhost, so a
   plain-http LAN address embeds the bundle in the link instead."
  []
  (and (supported?) (exists? js/crypto) (some? (.-subtle js/crypto))))

(defn- subtle [] (.-subtle js/crypto))

(defn- sha-256 [u8]
  (-> (.digest (subtle) "SHA-256" u8) (.then #(js/Uint8Array. %))))

(defn- join-bytes [a b]
  (let [out (js/Uint8Array. (+ (.-length a) (.-length b)))]
    (.set out a 0)
    (.set out b (.-length a))
    out))

(defn- aes-key [raw usage]
  (.importKey (subtle) "raw" raw #js {:name "AES-GCM"} false #js [usage]))

(defn- iv-for
  "One IV per key. Safe here because a key only ever encrypts the one plaintext it was derived from."
  [raw-key]
  (-> (sha-256 (join-bytes raw-key (str->bytes "iv"))) (.then #(.slice % 0 12))))

(defn- snapshot-id
  "The id the server stores a blob under, computed as orcpub.routes.share/share-id computes it: the
   first 22 characters of the blob's SHA-256 in base64url."
  [blob]
  (-> (sha-256 (str->bytes blob)) (.then #(subs (b64url-encode %) 0 22))))

(defn build-snapshot
  "bundle + character id -> Promise of {:share id :key k :blob b}, or {:error :unsupported|:too-large}.
   Compressed, then encrypted with AES-GCM."
  [bundle character-id]
  (let [edn (str->bytes (sb/bundle->edn bundle))]
    (cond
      (not (encryption-supported?)) (js/Promise.resolve {:error :unsupported})
      (> (.-length edn) max-snapshot-edn-bytes) (js/Promise.resolve {:error :too-large})
      :else
      (-> (js/Promise.all #js [(sha-256 (join-bytes (str->bytes (str "orcpub share v1 " character-id "\n")) edn))
                               (gzip edn)])
          (.then (fn [derived]
                   (let [raw (aget derived 0)
                         gz  (aget derived 1)]
                     (-> (js/Promise.all #js [(aes-key raw "encrypt") (iv-for raw)])
                         (.then (fn [ki] (.encrypt (subtle) #js {:name "AES-GCM" :iv (aget ki 1)} (aget ki 0) gz)))
                         (.then (fn [ct]
                                  (let [blob (str snapshot-version (b64url-encode (js/Uint8Array. ct)))]
                                    (if (> (count blob) max-snapshot-blob-chars)
                                      {:error :too-large}
                                      (-> (snapshot-id blob)
                                          (.then (fn [id] {:share id :key (b64url-encode raw) :blob blob})))))))))))))))

(defn decode-snapshot
  "Decrypt a snapshot's blob with the key from its link, then apply decode-shared's last layers under a
   max-snapshot-edn-bytes cap. Promise of {:plugins m :custom-items [...] :dropped n} or {:error kw}; a
   wrong key or a tampered blob is {:error :decode}."
  [blob key-str]
  (cond
    (not (and (string? blob) (string? key-str) (seq blob) (seq key-str)))
    (js/Promise.resolve {:error :empty})

    (> (count blob) max-snapshot-blob-chars)
    (js/Promise.resolve {:error :too-large})

    (not (str/starts-with? blob snapshot-version))
    (js/Promise.resolve {:error :version})

    (not (encryption-supported?))
    (js/Promise.resolve {:error :unsupported})

    :else
    (-> (js/Promise.resolve key-str)
        (.then b64url-decode)
        (.then (fn [raw] (js/Promise.all #js [(aes-key raw "decrypt") (iv-for raw)])))
        (.then (fn [ki] (.decrypt (subtle) #js {:name "AES-GCM" :iv (aget ki 1)} (aget ki 0)
                                  (b64url-decode (subs blob (count snapshot-version))))))
        (.then (fn [plain] (gunzip-capped (js/Uint8Array. plain) max-snapshot-edn-bytes)))
        (.then bytes->str)
        (.then read-shared-edn)
        (.catch (fn [_] {:error :decode})))))
