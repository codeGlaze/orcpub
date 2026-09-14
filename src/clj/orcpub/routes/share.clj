;; Encrypted share snapshots: the homebrew a character's share link carries.
(ns orcpub.routes.share
  "The browser compresses a character's homebrew bundle, encrypts it with a key derived from the
   content, and uploads only the ciphertext. The key travels in the link after #, which browsers never
   send, so the server stores what it cannot read. Identical content gives an identical blob, and a
   snapshot's id is the start of the blob's SHA-256, so uploading the same content again stores nothing
   new.

   Anyone may fetch a snapshot, as anyone may read the character; without the key it is noise. Only
   the character's owner may store one. The server cannot check what is inside, so its guards are size:
   max-blob-bytes a snapshot, the newest max-shares-per-character kept for each character, and
   max-bytes-per-owner across an account. A character's snapshots are deleted with it."
  (:require [datomic.api :as d]
            [orcpub.entity.strict :as se])
  (:import [java.security MessageDigest]
           [java.util Arrays Base64]
           [java.io ByteArrayInputStream InputStream]))

;; Measured 2026-09-13 against the MegaPak (12 sources): compressed and encrypted, a packed level 20
;; character's snapshot is 16 KB (artificer) to 25 KB (wizard with 40 homebrew spells), and a wizard
;; holding every subclass and spell in the pack 40 KB. Stored as bytes, since base64 would add a third.
;; The browser refuses to build one over max-blob-bytes.
(def max-blob-bytes (* 128 1024))
(def max-shares-per-character 5)
(def max-bytes-per-owner (* 2560 1024))

;; The first byte of a snapshot. Version 1 is the payload embedded in a link.
(def ^:private format-version 2)

(defn share-id
  "The id a snapshot is stored under: the first 22 characters of its SHA-256 in base64url. The browser
   computes the same, so the id in a link names exactly one snapshot."
  [^bytes blob]
  (let [digest (.digest (MessageDigest/getInstance "SHA-256") blob)]
    (subs (.encodeToString (.withoutPadding (Base64/getUrlEncoder)) digest) 0 22)))

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

(defn get-share
  "A snapshot's bytes, to anyone who asks."
  [{:keys [db] {:keys [id share]} :path-params}]
  (if-let [blob (d/q '[:find ?blob . :in $ ?rid
                       :where [?e :orcpub.share/id ?rid] [?e :orcpub.share/ciphertext ?blob]]
                     db (record-id id share))]
    {:status 200 :headers {"Content-Type" "application/octet-stream"} :body (ByteArrayInputStream. ^bytes blob)}
    {:status 404}))

(defn put-share
  "Stores a snapshot for a character the caller owns. The body is the snapshot's bytes, and the path
   names the id they must hash to. Stores nothing when that snapshot is already there. Anyone else gets the 404
   a missing character gets."
  [{:keys [db conn identity body] {:keys [id share]} :path-params}]
  (let [username (:user identity)]
    (if-not (owns-character? db username id)
      {:status 404}
      (let [blob (read-blob body)]
        (cond
          (= ::too-large blob)
          {:status 413 :body {:error :share-too-large}}

          (not (snapshot-shaped? blob))
          {:status 400 :body {:error :share-blob-invalid}}

          (not= share (share-id blob))
          {:status 400 :body {:error :share-id-mismatch}}

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
                                            [{:orcpub.share/id        (record-id id share)
                                              :orcpub.share/character id
                                              :orcpub.share/owner     username
                                              :orcpub.share/ciphertext blob
                                              :orcpub.share/size      (alength ^bytes blob)}]))
                  {:status 200 :body {:share share}}))))))))

(defn retractions-for-character
  "Transaction data that deletes every snapshot of a character, for when the character goes."
  [db character-id]
  (map (fn [e] [:db/retractEntity e])
       (d/q '[:find [?e ...] :in $ ?c :where [?e :orcpub.share/character ?c]] db character-id)))
