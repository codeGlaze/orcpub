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

   A share nobody uses for ORCPUB_SHARE_PRUNE_DAYS while the server runs expires: its token and share data
   are deleted, never the character, and a note of the date stays so the owner's page can say so until
   the owner shares again or dismisses it. Use is the link opened with the current token, the owner's
   page, or a party page loading it, recorded at most once a day; a request without the token records
   nothing, so it cannot keep a share alive. Only prune! deletes on its own, run hourly after
   orcpub.heartbeat's beat, and time the server was off does not count. Stop sharing deletes at once."
  (:require [clojure.edn :as edn]
            [datomic.api :as d]
            [orcpub.config :as config]
            [orcpub.heartbeat :as heartbeat]
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

(defn- expiry-of
  "When the character's last share link expired unused, while the owner has neither shared again nor
   dismissed the note."
  [db character-id]
  (d/q '[:find ?on . :in $ ?c :where [?e :orcpub.share-expiry/character ?c] [?e :orcpub.share-expiry/on ?on]]
       db character-id))

(defn- expiry-retractions [db character-id]
  (map (fn [e] [:db/retractEntity e])
       (d/q '[:find [?e ...] :in $ ?c :where [?e :orcpub.share-expiry/character ?c]] db character-id)))

(defn prune!
  "Expires the shares unused for longer than the prune window: each is deleted, leaving a note of its
   character and the date for the owner's page, and a note still there after another window is deleted
   too. A day more is allowed because use is recorded at most daily, a share with no recorded use counts
   from when its record was made, and time the server was off does not count. Only share records and
   notes go, never a character or a party's entry for it. Returns {:expired n :forgotten n}."
  [conn]
  (let [db        (d/db conn)
        at        (now)
        days      (config/get-share-prune-days)
        outages   (heartbeat/outages db)
        unused?   (fn [since] (> (heartbeat/running-ms outages since at) (* (inc days) day-ms)))
        stale     (when (pos? days)
                    (vec (for [[e c made] (d/q '[:find ?e ?c ?made
                                                 :where [?e :orcpub.share/character ?c ?tx] [?tx :db/txInstant ?made]]
                                               db)
                               :when (unused? (or (:orcpub.share/used (d/entity db e)) made))]
                           [e c])))
        expiring  (set (map second stale))
        forgotten (when (pos? days)
                    (vec (for [[e c on] (d/q '[:find ?e ?c ?on
                                               :where [?e :orcpub.share-expiry/character ?c] [?e :orcpub.share-expiry/on ?on]]
                                             db)
                               :when (and (unused? on) (not (expiring c)))]
                           e)))]
    (when (or (seq stale) (seq forgotten))
      @(d/transact conn (concat (mapcat (fn [[e c]] [[:db/retractEntity e]
                                                     {:orcpub.share-expiry/character c :orcpub.share-expiry/on at}])
                                        stale)
                                (map (fn [e] [:db/retractEntity e]) forgotten))))
    {:expired (count stale) :forgotten (count forgotten)}))

(defn prune-job
  "prune! for the heartbeat, saying in the log what it removed."
  [conn]
  (let [{:keys [expired forgotten]} (prune! conn)]
    (when (pos? (+ expired forgotten))
      (println "Share links:" expired "expired unused," forgotten "expiry notes nobody dismissed removed"))))

(defn- random-token []
  (let [b (byte-array 16)]
    (.nextBytes (SecureRandom.) b)
    (.encodeToString (.withoutPadding (Base64/getUrlEncoder)) b)))

(defn- text [s] {:status 200 :headers (merge {"Content-Type" "text/plain; charset=utf-8"} (cap-headers)) :body s})

(defn retractions-for-character
  "Transaction data that deletes a character's share and any note that its last link expired, for Stop
   sharing, New link, and when the character goes."
  [db character-id]
  (concat (map (fn [e] [:db/retractEntity e])
               (d/q '[:find [?e ...] :in $ ?c :where [?e :orcpub.share/character ?c]] db character-id))
          (expiry-retractions db character-id)))

(defn get-token
  "The character's share token, for its owner. Otherwise 404, or 410 with the date when the character's
   last share link expired unused and the owner has neither shared again nor dismissed the note. Nothing
   is made here: a character's page asks this on every view, and only Share link creates a share."
  [{:keys [db conn identity] {:keys [id]} :path-params}]
  (let [owner? (owns-character? db (:user identity) id)
        share  (when owner? (share-of db id))
        on     (when (and owner? (not share)) (expiry-of db id))]
    (cond
      share (do (touch! conn share)
                (text (:orcpub.share/token share)))
      on    {:status 410 :headers {"Content-Type" "text/plain; charset=utf-8"}
             :body (str (.toInstant ^java.util.Date on))}
      :else {:status 404})))

(defn create-token
  "Shares the character: makes its token if it has none, for its owner, and returns it. Share link does
   this; until then nothing about the character is stored. Sharing again clears the note that an earlier
   link expired."
  [{:keys [db conn identity] {:keys [id]} :path-params}]
  (let [username (:user identity)
        share    (share-of db id)]
    (cond
      (not (owns-character? db username id)) {:status 404}
      share (do (touch! conn share)
                (text (:orcpub.share/token share)))
      :else (do @(d/transact conn (concat [{:orcpub.share/character id :orcpub.share/owner username
                                            :orcpub.share/token (random-token) :orcpub.share/used (now)}]
                                          (expiry-retractions db id)))
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

(defn stop-sharing
  "Deletes the character's share, so every link made before loads nothing and nothing is stored until
   Share link is pressed again, and the note that an earlier link expired. For the owner only."
  [{:keys [db conn identity] {:keys [id]} :path-params}]
  (if-not (owns-character? db (:user identity) id)
    {:status 404}
    (do (when-let [tx (seq (retractions-for-character db id))]
          @(d/transact conn tx))
        {:status 204})))

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
