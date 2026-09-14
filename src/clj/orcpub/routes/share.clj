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

   A share nobody uses for ORCPUB_SHARE_PRUNE_DAYS while the server runs is deleted: its token and this
   copy of the homebrew, never the character or the owner's own homebrew. Use is the link opened with the
   current token, the owner's page, or a party page loading it, recorded at most once a day; a request
   without the token records nothing, so it cannot keep a share alive. Only prune! deletes, run hourly by
   orcpub.share-pruner, and time the server was off does not count (record-beat!)."
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

(def ^:private hour-ms (* 60 60 1000))

(def ^:private day-ms (* 24 hour-ms))

(defn- touch!
  "Records use of a share, at most once a day."
  [conn share]
  (let [at   (now)
        used ^java.util.Date (:orcpub.share/used share)]
    (when (or (nil? used) (> (- (.getTime at) (.getTime used)) day-ms))
      @(d/transact conn [{:db/id (:db/id share) :orcpub.share/used at}]))))

(defn token-current?
  "Whether token is the character's current share token."
  [db character-id token]
  (let [share (share-of db character-id)]
    (boolean (and share (string? token) (= token (:orcpub.share/token share))))))

(def ^:private beat-gap-ms
  "Beats come hourly; a longer gap than this means the server was off or its clock jumped ahead."
  (+ hour-ms (* 10 60 1000)))

(defn record-beat!
  "Records that the server is running, and records the time since the last beat as an outage when the gap
   is longer than hourly beats explain. Pruning does not count an outage as disuse. A clock set back
   records no outage and so deletes nothing sooner."
  [conn]
  (let [at       (now)
        previous ^java.util.Date (d/q '[:find ?t . :where [?e :db/ident :orcpub.share/clock] [?e :orcpub.share/beat ?t]]
                                      (d/db conn))]
    @(d/transact conn (cond-> [{:db/ident :orcpub.share/clock :orcpub.share/beat at}]
                        (and previous (> (- (.getTime at) (.getTime previous)) beat-gap-ms))
                        (conj {:orcpub.share-outage/from previous :orcpub.share-outage/to at})))))

(defn- unused-ms
  "Time from a share's last use to `at`, less the outages in between."
  [^java.util.Date used ^java.util.Date at outages]
  (let [from (.getTime used)
        to   (.getTime at)]
    (- to from (reduce + (for [[^java.util.Date start ^java.util.Date end] outages]
                           (max 0 (- (min to (.getTime end)) (max from (.getTime start)))))))))

(defn prune!
  "Deletes the shares unused for longer than the prune window, and returns how many. A day more is
   allowed because use is recorded at most daily, and a share with no recorded use is kept. Only share
   records go: the character, a party's entry for it and the owner's homebrew stay."
  [conn]
  (let [db      (d/db conn)
        at      (now)
        days    (config/get-share-prune-days)
        outages (d/q '[:find ?from ?to :where [?o :orcpub.share-outage/from ?from] [?o :orcpub.share-outage/to ?to]] db)
        stale   (when (pos? days)
                  (for [[e used] (d/q '[:find ?e ?used :where [?e :orcpub.share/character] [?e :orcpub.share/used ?used]] db)
                        :when (> (unused-ms used at outages) (* (inc days) day-ms))]
                    e))]
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
  (if-let [share (and (owns-character? db (:user identity) id) (share-of db id))]
    (do (touch! conn share)
        (text (:orcpub.share/token share)))
    {:status 404}))

(defn create-token
  "Shares the character: makes its token if it has none, for its owner, and returns it. Share link does
   this; until then nothing about the character is stored."
  [{:keys [db conn identity] {:keys [id]} :path-params}]
  (let [username (:user identity)
        share    (share-of db id)]
    (cond
      (not (owns-character? db username id)) {:status 404}
      share (do (touch! conn share)
                (text (:orcpub.share/token share)))
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
  "The character's shared homebrew, compressed, to anyone whose link carries its current token. Only that
   request counts as use: one without the token writes nothing, so it cannot keep a share from expiring."
  [{:keys [db conn] {:keys [id token]} :path-params}]
  (let [share  (share-of db id)
        stored (:orcpub.share/bundle share)]
    (if (and stored (string? token) (= token (:orcpub.share/token share)))
      (do (touch! conn share)
          {:status 200 :headers (merge {"Content-Type" "application/octet-stream"} (cap-headers))
           :body (ByteArrayInputStream. ^bytes stored)})
      {:status 404})))

(defn put-share
  "Replaces the homebrew a character shares, for its owner, when the path carries its current token. The
   body is the compressed homebrew, stored exactly as sent once upload-problem finds nothing wrong. An
   upload identical to the stored one is not checked or written again."
  [{:keys [db conn identity body] {:keys [id token]} :path-params}]
  (let [username (:user identity)
        share    (when (owns-character? db username id) (share-of db id))
        current? (and share (= token (:orcpub.share/token share)))]
    (when current? (touch! conn share))
    (if-not current?
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
