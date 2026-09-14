;; Encrypted share snapshots: the homebrew a character's share link carries.
(ns orcpub.routes.share
  "The browser compresses a character's homebrew bundle, encrypts it, and uploads only the ciphertext.
   The key travels in the link after #, which browsers never send, so the server stores what it cannot
   read.

   The key is derived from the character's share salt, the character id and the bundle, and the
   snapshot id from the key. The same homebrew therefore gives the same link in any session or
   browser, and uploading it again stores nothing. The salt is random and kept here; replacing it
   (new-salt) deletes the character's snapshots, so every link made before stops loading the homebrew
   and the next Copy link is a different one.

   Anyone may fetch a snapshot, as anyone may read the character; without the key it is noise. Only
   the character's owner may store one or see or replace its salt. The server cannot check what is
   inside a snapshot, so its guards are size: max-blob-bytes a snapshot, the newest
   max-shares-per-character kept for each character, and max-bytes-per-owner across an account. A
   character's snapshots and salt are deleted with it."
  (:require [datomic.api :as d]
            [orcpub.entity.strict :as se])
  (:import [java.security SecureRandom]
           [java.util Arrays Base64]
           [java.io ByteArrayInputStream InputStream]))

;; Measured 2026-09-13 against the MegaPak (12 sources): compressed and encrypted, a packed level 20
;; character's snapshot is 12 KB (divine soul sorcerer) to 25 KB (wizard with 40 homebrew spells), and
;; a wizard holding every subclass and spell in the pack 40 KB. Stored as bytes, since base64 would add
;; a third. The browser refuses to build one over max-blob-bytes.
(def max-blob-bytes (* 128 1024))
(def max-shares-per-character 5)
(def max-bytes-per-owner (* 2560 1024))

;; The first byte of a snapshot. Version 1 is the payload embedded in a link.
(def ^:private format-version 2)

(def ^:private share-id-shape #"[A-Za-z0-9_-]{22}")

(defn- record-id [character-id share] (str character-id "/" share))

(defn- read-blob
  "The request body's bytes, or ::too-large once it passes max-blob-bytes. Reads at most one byte past
   the limit, so an oversized body is not buffered whole."
  [^InputStream body]
  (when body
    (let [buf (byte-array (inc max-blob-bytes))
          n   (loop [off 0]
                (if (= off (alength buf))
                  off
                  (let [got (.read body buf off (- (alength buf) off))]
                    (if (neg? got) off (recur (+ off got))))))]
      (if (> n max-blob-bytes) ::too-large (Arrays/copyOf buf (int n))))))

(defn- snapshot-shaped?
  "The version byte, then at least the 16-byte AES-GCM tag and one byte of ciphertext."
  [blob]
  (and (bytes? blob) (> (alength ^bytes blob) 17) (= format-version (aget ^bytes blob 0))))

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

(defn- snapshot-retractions [db character-id]
  (map (fn [e] [:db/retractEntity e])
       (d/q '[:find [?e ...] :in $ ?c :where [?e :orcpub.share/character ?c]] db character-id)))

(defn retractions-for-character
  "Transaction data that deletes a character's snapshots and share salt, for when the character goes."
  [db character-id]
  (concat (snapshot-retractions db character-id)
          (map (fn [e] [:db/retractEntity e])
               (d/q '[:find [?e ...] :in $ ?c :where [?e :orcpub.share-salt/character ?c]] db character-id))))

(defn- random-salt []
  (let [b (byte-array 32)]
    (.nextBytes (SecureRandom.) b)
    (.encodeToString (.withoutPadding (Base64/getUrlEncoder)) b)))

(defn- salt-of [db character-id]
  (d/q '[:find ?s . :in $ ?c :where [?e :orcpub.share-salt/character ?c] [?e :orcpub.share-salt/value ?s]]
       db character-id))

(defn- text [s] {:status 200 :headers {"Content-Type" "text/plain; charset=utf-8"} :body s})

(defn get-salt
  "The character's share salt, for its owner, made the first time it is asked for."
  [{:keys [db conn identity] {:keys [id]} :path-params}]
  (cond
    (not (owns-character? db (:user identity) id)) {:status 404}
    (salt-of db id) (text (salt-of db id))
    :else (do @(d/transact conn [{:orcpub.share-salt/character id :orcpub.share-salt/value (random-salt)}])
              ;; Read back: had two first requests raced, both callers get the value that stuck.
              (text (salt-of (d/db conn) id)))))

(defn new-salt
  "Replaces the character's share salt and deletes its snapshots, so every link made before stops
   loading the homebrew. For the owner only."
  [{:keys [db conn identity] {:keys [id]} :path-params}]
  (if-not (owns-character? db (:user identity) id)
    {:status 404}
    (let [salt (random-salt)]
      @(d/transact conn (concat (snapshot-retractions db id)
                                [{:orcpub.share-salt/character id :orcpub.share-salt/value salt}]))
      (text salt))))

(defn get-share
  "A snapshot's bytes, to anyone who asks."
  [{:keys [db] {:keys [id share]} :path-params}]
  (if-let [blob (d/q '[:find ?blob . :in $ ?rid
                       :where [?e :orcpub.share/id ?rid] [?e :orcpub.share/ciphertext ?blob]]
                     db (record-id id share))]
    {:status 200 :headers {"Content-Type" "application/octet-stream"} :body (ByteArrayInputStream. ^bytes blob)}
    {:status 404}))

(defn put-share
  "Stores a snapshot for a character the caller owns, under the id the path names. The browser derives
   that id from the same secret as the key, so the server cannot match it to the bytes; a wrong one
   only breaks its owner's own link. Stores nothing when that snapshot is already there. Anyone else
   gets the 404 a missing character gets."
  [{:keys [db conn identity body] {:keys [id share]} :path-params}]
  (let [username (:user identity)]
    (cond
      (not (owns-character? db username id)) {:status 404}
      (not (and (string? share) (re-matches share-id-shape share))) {:status 400 :body {:error :share-id-invalid}}
      :else
      (let [blob (read-blob body)]
        (cond
          (= ::too-large blob)
          {:status 413 :body {:error :share-too-large}}

          (not (snapshot-shaped? blob))
          {:status 400 :body {:error :share-blob-invalid}}

          (d/q '[:find ?e . :in $ ?rid :where [?e :orcpub.share/id ?rid]] db (record-id id share))
          {:status 200 :body {:share share}}

          :else
          (let [existing (sort (d/q '[:find [?e ...] :in $ ?c :where [?e :orcpub.share/character ?c]] db id))
                evicted  (take (max 0 (inc (- (count existing) max-shares-per-character))) existing)
                size-of  #(or (:orcpub.share/size (d/pull db [:orcpub.share/size] %)) 0)
                used     (or (ffirst (d/q '[:find (sum ?size) :with ?e :in $ ?owner
                                            :where [?e :orcpub.share/owner ?owner] [?e :orcpub.share/size ?size]]
                                          db username))
                             0)]
            (if (> (+ (- used (reduce + (map size-of evicted))) (alength ^bytes blob)) max-bytes-per-owner)
              {:status 413 :body {:error :share-quota}}
              (do @(d/transact conn (concat (map (fn [e] [:db/retractEntity e]) evicted)
                                            [{:orcpub.share/id         (record-id id share)
                                              :orcpub.share/character  id
                                              :orcpub.share/owner      username
                                              :orcpub.share/ciphertext blob
                                              :orcpub.share/size       (alength ^bytes blob)}]))
                  {:status 200 :body {:share share}}))))))))
