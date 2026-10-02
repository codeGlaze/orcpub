(ns orcpub.dnd.e5.share-url
  "Browser codec between a homebrew bundle (share-bundle) and a URL-safe, version-prefixed (\"1\")
   link-fragment payload. Promise-based: gzip goes through the async CompressionStream API.
   SECURITY: a payload is fully untrusted. `decode-shared` fails closed at each layer: input cap
   (1), bomb-capped gunzip (3), safe EDN read (4; no #= eval, unknown tags throw), structural
   whitelist (5; share-bundle/whitelist-shared). Layer 6, content sanitize and per-type spec, runs
   on the .orcbrew import path when the caller loads the bundle. See share-links.md."
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
  "bundle -> Promise of {:tier :full|:long|:file :payload s|nil}. Never drops content to fit:
   :full ships the payload at a comfortable length, :long ships it but flags that some apps
   truncate very long links, and :file (no payload) means too large for any link. Resolves :file
   immediately when compression is unsupported."
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
        (.then (fn [edn-str]
                 (let [data (safe-read-edn edn-str)]
                   (if (= data ::read-error)
                     {:error :parse}
                     (sb/whitelist-shared data)))))
        (.catch (fn [_] {:error :decode})))))
