;; Shared homebrew: what a character's short share link loads.
(ns orcpub.routes.share
  "A character's owner shares the homebrew it uses by link, /characters/<id>#s=<token>. The server keeps
   one copy per character and replaces it whenever the owner's share button sends the homebrew again,
   so the link stays the same and shows the current homebrew.

   Nothing is stored for a character until its owner presses Share link (create-token). After that the
   token is random, and only the owner can see or replace it. New link replaces it and deletes the stored
   homebrew, so every link made before loads nothing. The token stays after the # in the link,
   which is not part of the page request the server logs.

   An upload that differs from the last one is checked once: unpacked under a size cap, read as plain
   data, and required to be exactly homebrew, meaning share-bundle/whitelist-shared keeps all of it and
   changes nothing. The browser applies the same filter before sending, so an honest upload passes and
   is stored as sent; anything else is refused, so the store holds nothing but homebrew. An upload
   identical to the last one is recognised by its digest and costs nothing. Limits come from config
   (ORCPUB_SHARE_MAX_*_KB): compressed size, unpacked size, and an account's total, with one copy per
   character. The browser learns them from the X-Share-Max-* headers. Deleting a character deletes its
   copy.

   A share nobody uses for ORCPUB_SHARE_PRUNE_DAYS is deleted, token and homebrew together: opening the
   link, the owner's page refreshing it, or a party page loading it all count as use, recorded at most
   once a day. An expired share is deleted when it is next asked for, and prune! sweeps the rest daily
   (orcpub.share-pruner)."
  (:require [clojure.edn :as edn]
            [datomic.api :as d]
            [orcpub.config :as config]
            [orcpub.dnd.e5.share-bundle :as sb]
            [orcpub.entity.strict :as se])
  (:import [java.security MessageDigest SecureRandom]
           [java.util Arrays Base64]
           [java.util.zip GZIPInputStream]
           [java.io ByteArrayInputStream InputStream]))

;; The caps, from config; the measurements behind their defaults sit with the getters.
(defn max-upload-bytes [] (* 1024 (config/get-share-max-upload-kb)))
(defn max-text-bytes [] (* 1024 (config/get-share-max-text-kb)))
(defn max-bytes-per-owner [] (* 1024 (config/get-share-max-account-kb)))

(defn- cap-headers []
  {"X-Share-Max-Upload-Bytes" (str (max-upload-bytes))
   "X-Share-Max-Text-Bytes"   (str (max-text-bytes))})

(defn- read-capped
  "Up to cap bytes from in, or ::too-large when there are more. Reads at most one byte past the cap."
  [^InputStream in cap]
  (let [buf (byte-array (inc cap))
        n   (loop [off 0]
              (if (= off (alength buf))
                off
                (let [got (.read in buf off (- (alength buf) off))]
                  (if (neg? got) off (recur (+ off got))))))]
    (if (> n cap) ::too-large (Arrays/copyOf buf (int n)))))

(defn- digest ^String [^bytes b]
  (.encodeToString (.withoutPadding (Base64/getUrlEncoder)) (.digest (MessageDigest/getInstance "SHA-256") b)))

(defn- upload-problem
  "Why a compressed upload cannot be stored, or nil when it can: ::too-large unpacked, ::unreadable as
   data, ::empty of homebrew, or ::not-homebrew when the whitelist would drop or change any of it."
  [^bytes upload]
  (let [text (try (with-open [z (GZIPInputStream. (ByteArrayInputStream. upload))]
                    (read-capped z (max-text-bytes)))
                  (catch Exception _ ::unreadable))]
    (if (keyword? text)
      text
      (let [data (try (edn/read-string (String. ^bytes text "UTF-8")) (catch Exception _ ::unreadable))
            kept (when-not (= ::unreadable data) (:plugins (sb/whitelist-shared data)))]
        (cond
          (= ::unreadable data) ::unreadable
          (empty? kept)         ::empty
          (not= data kept)      ::not-homebrew
          :else                 nil)))))

(defn- owns-character?
  "Whether username owns the character. A character saved in May 2017 may name its owner by email."
  [db username character-id]
  (boolean
   (when username
     (let [owner (::se/owner (d/pull db [::se/owner] character-id))
           email (d/q '[:find ?email . :in $ ?username
                        :where [?u :orcpub.user/username ?username] [?u :orcpub.user/email ?email]]
                      db username)]
       (and owner (or (= owner username) (and email (= owner email))))))))

(defn- share-of [db character-id]
  (when-let [e (d/q '[:find ?e . :in $ ?c :where [?e :orcpub.share/character ?c]] db character-id)]
    (d/pull db [:db/id :orcpub.share/token :orcpub.share/bundle :orcpub.share/digest :orcpub.share/size
                :orcpub.share/used] e)))

(defn now [] (java.util.Date.))

(def ^:private day-ms (* 24 60 60 1000))

(defn- stale?
  "Whether a share has gone unused past the prune window at the moment `at`."
  [share ^java.util.Date at]
  (let [days (config/get-share-prune-days)
        used ^java.util.Date (:orcpub.share/used share)]
    (boolean (and (pos? days) used (> (- (.getTime at) (.getTime used)) (* days day-ms))))))

(defn- touch
  "Transaction data recording use of a share at `at`, or nil when it was recorded within the last day."
  [share ^java.util.Date at]
  (let [used ^java.util.Date (:orcpub.share/used share)]
    (when (or (nil? used) (> (- (.getTime at) (.getTime used)) day-ms))
      [{:db/id (:db/id share) :orcpub.share/used at}])))

(defn- live-share
  "The character's share, recording this use, or nil. A share past the prune window is deleted here."
  [conn db character-id]
  (when-let [share (share-of db character-id)]
    (let [at (now)]
      (if (stale? share at)
        (do @(d/transact conn [[:db/retractEntity (:db/id share)]]) nil)
        (do (when-let [tx (touch share at)] @(d/transact conn tx))
            share)))))

(defn token-current?
  "Whether token is the character's current, unexpired share token."
  [db character-id token]
  (let [share (share-of db character-id)]
    (boolean (and share (string? token) (= token (:orcpub.share/token share)) (not (stale? share (now)))))))

(defn prune!
  "Deletes every share unused past the prune window. Returns how many."
  [conn]
  (let [db    (d/db conn)
        at    (now)
        stale (for [e (d/q '[:find [?e ...] :where [?e :orcpub.share/character]] db)
                    :let [share (d/pull db [:db/id :orcpub.share/used] e)]
                    :when (stale? share at)]
                (:db/id share))]
    (when (seq stale)
      @(d/transact conn (map (fn [e] [:db/retractEntity e]) stale)))
    (count stale)))

(defn- random-token []
  (let [b (byte-array 16)]
    (.nextBytes (SecureRandom.) b)
    (.encodeToString (.withoutPadding (Base64/getUrlEncoder)) b)))

(defn- text [s] {:status 200 :headers (merge {"Content-Type" "text/plain; charset=utf-8"} (cap-headers)) :body s})

(defn retractions-for-character
  "Transaction data that deletes a character's shared homebrew and token, for when the character goes."
  [db character-id]
  (map (fn [e] [:db/retractEntity e])
       (d/q '[:find [?e ...] :in $ ?c :where [?e :orcpub.share/character ?c]] db character-id)))

(defn get-token
  "The character's share token, for its owner, or the 404 a character never shared gets. Nothing is made
   here: a character's page asks this on every view, and only Share link creates a share."
  [{:keys [db conn identity] {:keys [id]} :path-params}]
  (if-let [token (and (owns-character? db (:user identity) id)
                      (:orcpub.share/token (live-share conn db id)))]
    (text token)
    {:status 404}))

(defn create-token
  "Shares the character: makes its token if it has none, for its owner, and returns it. Share link does
   this; until then nothing about the character is stored."
  [{:keys [db conn identity] {:keys [id]} :path-params}]
  (let [username (:user identity)]
    (cond
      (not (owns-character? db username id)) {:status 404}
      (:orcpub.share/token (live-share conn db id)) (text (:orcpub.share/token (share-of (d/db conn) id)))
      :else (do @(d/transact conn [{:orcpub.share/character id :orcpub.share/owner username
                                    :orcpub.share/token (random-token) :orcpub.share/used (now)}])
                ;; Read back: had two first requests raced, both callers get the token that stuck.
                (text (:orcpub.share/token (share-of (d/db conn) id)))))))

(defn new-token
  "Replaces the character's share token and deletes its shared homebrew, so every link made before loads
   nothing. For the owner only."
  [{:keys [db conn identity] {:keys [id]} :path-params}]
  (let [username (:user identity)]
    (if-not (owns-character? db username id)
      {:status 404}
      (let [token (random-token)]
        ;; Two transactions: deleting the record and making its replacement in one would name the same
        ;; unique character twice.
        @(d/transact conn (retractions-for-character db id))
        @(d/transact conn [{:orcpub.share/character id :orcpub.share/owner username :orcpub.share/token token
                            :orcpub.share/used (now)}])
        (text token)))))

(defn get-share
  "The character's shared homebrew, compressed, to anyone whose link carries its current token."
  [{:keys [db conn] {:keys [id token]} :path-params}]
  (let [{stored :orcpub.share/bundle current :orcpub.share/token} (live-share conn db id)]
    (if (and stored (string? token) (= token current))
      {:status 200 :headers (merge {"Content-Type" "application/octet-stream"} (cap-headers))
       :body (ByteArrayInputStream. ^bytes stored)}
      {:status 404})))

(defn put-share
  "Replaces the homebrew a character shares, for its owner, when the path carries its current token. The
   body is the compressed homebrew, stored exactly as sent once upload-problem finds nothing wrong. An
   upload identical to the stored one is not checked or written again."
  [{:keys [db conn identity body] {:keys [id token]} :path-params}]
  (let [username (:user identity)
        share    (when (owns-character? db username id) (live-share conn db id))]
    (if-not (and share (= token (:orcpub.share/token share)))
      {:status 404}
      (let [upload (when body (read-capped body (max-upload-bytes)))]
        (cond
          (= ::too-large upload)                          {:status 413 :body {:error :share-too-large}}
          (nil? upload)                                   {:status 400 :body {:error :share-unreadable}}
          (= (digest upload) (:orcpub.share/digest share)) {:status 200 :body {:token token}}
          :else
          (case (upload-problem upload)
            ::too-large    {:status 413 :body {:error :share-too-large}}
            ::unreadable   {:status 400 :body {:error :share-unreadable}}
            ::empty        {:status 400 :body {:error :share-empty}}
            ::not-homebrew {:status 400 :body {:error :share-not-homebrew}}
            nil
            (let [used (or (ffirst (d/q '[:find (sum ?size) :with ?e :in $ ?owner
                                          :where [?e :orcpub.share/owner ?owner] [?e :orcpub.share/size ?size]]
                                        db username))
                           0)]
              (if (> (+ (- used (or (:orcpub.share/size share) 0)) (alength ^bytes upload)) (max-bytes-per-owner))
                {:status 413 :body {:error :share-quota}}
                (do @(d/transact conn [{:db/id               (:db/id share)
                                        :orcpub.share/used   (now)
                                        :orcpub.share/bundle upload
                                        :orcpub.share/digest (digest upload)
                                        :orcpub.share/size   (alength ^bytes upload)}])
                    {:status 200 :body {:token token}})))))))))
