(ns orcpub.crypto
  "Encryption at rest for personal details that the site must be able to read
   back -- today, the name a person wants to be called in emails.

   Encrypted, not hashed. A hash is one-way: right for a password, which is
   only ever checked, and useless for a name, which has to be written into
   'Hey Fuss!'. With encryption the database holds ciphertext and the key lives
   outside it, so a stolen database or backup shows nothing readable while the
   running site can still address its email. It does NOT protect against a
   compromised app server, which must hold the key to do its job.

   Keys come from the Docker secret profile_encryption_keys, or the
   PROFILE_ENCRYPTION_KEYS environment variable, as a comma-separated list of
   id:base64 pairs, each key 32 random bytes (AES-256):

     k2:3q2+7w...=,k1:mZ9a...=

   The FIRST key encrypts; every listed key can decrypt. To rotate, put a new
   key first and keep the old ones after it until nothing uses them. Generate
   one with `openssl rand -base64 32`.

   Stored values look like `v1:<key id>:<base64 of 12-byte IV + ciphertext>`.
   AES-GCM, so tampering is detected. Each value is bound to a purpose string
   (e.g. \"user/preferred-name\") as associated data, so a ciphertext copied into
   a different field fails to decrypt rather than being read as that field."
  (:require [clojure.string :as str]
            [orcpub.config :as config])
  (:import [javax.crypto Cipher]
           [javax.crypto.spec SecretKeySpec GCMParameterSpec]
           [java.security SecureRandom]
           [java.util Base64]
           [java.nio.charset StandardCharsets]))

(def ^:private version "v1")
(def ^:private iv-bytes 12)
(def ^:private tag-bits 128)
(def ^:private key-id-re #"[A-Za-z0-9_-]{1,16}")

(defn parse-keys
  "Parse the id:base64 key list. Returns a vector of {:id :key}, first one
   current; throws ex-info naming the problem for anything malformed, because
   a key that silently fails to load would silently stop names being saved."
  [s]
  (when-not (str/blank? s)
    (let [entries (->> (str/split s #",") (map str/trim) (remove str/blank?))
          parsed (mapv (fn [entry]
                         (let [[id b64 & more] (str/split entry #":" 2)]
                           (when (or more (str/blank? b64) (not (re-matches key-id-re (str id))))
                             (throw (ex-info "Each profile encryption key must be id:base64, with an id of 1-16 letters, digits, - or _"
                                             {:error :bad-key-format})))
                           (let [k (try (.decode (Base64/getDecoder) ^String (str/trim b64))
                                        (catch IllegalArgumentException _
                                          (throw (ex-info (str "Profile encryption key " id " is not valid base64")
                                                          {:error :bad-key-base64 :id id}))))]
                             (when-not (= 32 (alength ^bytes k))
                               (throw (ex-info (str "Profile encryption key " id " must be 32 bytes (openssl rand -base64 32)")
                                               {:error :bad-key-length :id id})))
                             {:id id :key k})))
                       entries)]
      (when (not= (count parsed) (count (distinct (map :id parsed))))
        (throw (ex-info "Profile encryption key ids must be unique" {:error :duplicate-key-id})))
      (not-empty parsed))))

(def ^:private configured
  (delay (try (parse-keys (config/profile-encryption-keys))
              (catch clojure.lang.ExceptionInfo e
                (println "ERROR: PROFILE_ENCRYPTION_KEYS ignored --" (.getMessage e))
                nil))))

(defn configured-keys
  "The deployment's keys, or nil when none are set (or they failed to parse)."
  []
  @configured)

(defn enabled?
  "Whether this deployment can store encrypted details at all."
  []
  (boolean (seq (configured-keys))))

(defn- cipher ^Cipher [mode ^bytes k ^bytes iv ^String purpose]
  (doto (Cipher/getInstance "AES/GCM/NoPadding")
    (.init (int mode) (SecretKeySpec. k "AES") (GCMParameterSpec. (int tag-bits) iv))
    (.updateAAD (.getBytes purpose StandardCharsets/UTF_8))))

(defn encrypt
  "Encrypt `plaintext` for `purpose` with the first of `ks` (default: the
   deployment's). Returns the stored string, or nil when no key is configured."
  ([purpose plaintext] (encrypt (configured-keys) purpose plaintext))
  ([ks purpose ^String plaintext]
   (when-let [{:keys [id key]} (first ks)]
     (let [iv (byte-array iv-bytes)
           _ (.nextBytes (SecureRandom.) iv)
           ct (.doFinal (cipher Cipher/ENCRYPT_MODE key iv purpose)
                        (.getBytes plaintext StandardCharsets/UTF_8))
           out (byte-array (+ iv-bytes (alength ct)))]
       (System/arraycopy iv 0 out 0 iv-bytes)
       (System/arraycopy ct 0 out iv-bytes (alength ct))
       (str version ":" id ":" (.encodeToString (Base64/getEncoder) out))))))

(defn decrypt
  "Decrypt a stored string for `purpose`. Returns nil -- never throws -- when
   the value is missing, malformed, tampered with, bound to a different
   purpose, or encrypted with a key no longer configured: the callers are
   emails and settings pages, which should fall back to a generic greeting
   rather than fail."
  ([purpose stored] (decrypt (configured-keys) purpose stored))
  ([ks purpose stored]
   (when (and (string? stored) (seq ks))
     (let [[v id b64] (str/split stored #":" 3)]
       (when-let [{:keys [key]} (and (= v version) (first (filter #(= id (:id %)) ks)))]
         (try
           (let [raw (.decode (Base64/getDecoder) ^String b64)]
             (when (> (alength raw) iv-bytes)
               (let [iv (java.util.Arrays/copyOfRange raw 0 (int iv-bytes))
                     ct (java.util.Arrays/copyOfRange raw (int iv-bytes) (alength raw))]
                 (String. (.doFinal (cipher Cipher/DECRYPT_MODE key iv purpose) ct)
                          StandardCharsets/UTF_8))))
           (catch Exception _ nil)))))))
