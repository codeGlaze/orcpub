(ns orcpub.security.redis-store
  "The abuse limits' state in Redis, so every app container counts against the same numbers.
   Loaded only when REDIS_URL is set (see orcpub.security/build-store). Same contract as the
   memory store: sightings are a sorted set per key (score = millisecond), marks are keys that
   expire, notice cadences are one expiring value per key. Every key starts with `prefix`, so a
   Redis shared with something else is safe. Socket timeouts are short: a slow Redis must not slow
   sign-in; orcpub.security answers any call that throws from memory instead."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [orcpub.security :as security]
            [orcpub.time :as time]
            [taoensso.carmine :as car :refer [wcar]]))

(def prefix "orcpub:limits:")

(def timeout-ms 200)

(defn- short-id
  "`v` as text for a key: itself, or its SHA-256 when long, since typed names are the sender's to
   make as long as they like."
  [v]
  (let [text (str v)]
    (if (<= (count text) 100)
      text
      (->> (.digest (java.security.MessageDigest/getInstance "SHA-256") (.getBytes text "UTF-8"))
           (map #(format "%02x" %))
           (apply str)))))

(defn- limit-text [limit]
  (if (keyword? limit) (subs (str limit) 1) (str limit)))

(defn- sighting-key [k]
  (let [[limit subject] (if (vector? k) k [k nil])]
    (str prefix "s:" (limit-text limit) ":" (short-id subject))))

(defn- mark-key [k] (str prefix "m:" (short-id (pr-str k))))

(defn- notice-key [k] (str prefix "n:" (short-id (pr-str k))))

;; Drops what left the window, reads what is still inside, adds this sighting, renews the key's
;; expiry -- as one script, so two containers cannot both take the last slot.
;; ARGV: cutoff ms, now ms, ttl ms, member.
(def ^:private sight-script
  "redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', '(' .. ARGV[1])
   local before = redis.call('ZRANGE', KEYS[1], 0, -1, 'WITHSCORES')
   redis.call('ZADD', KEYS[1], ARGV[2], ARGV[4])
   redis.call('PEXPIRE', KEYS[1], ARGV[3])
   return before")

;; Writes ARGV[2] only if the key still holds what the caller read (ARGV[1], empty for none).
(def ^:private swap-script
  "local cur = redis.call('GET', KEYS[1])
   if (cur == false and ARGV[1] == '') or cur == ARGV[1] then
     redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3])
     return 1
   end
   return 0")

;; A sighting is [instant who]; `who` rides in the member after a nonce that keeps equal
;; sightings apart. "n" is nil, "s" a string.
(defn- member [who]
  (str (random-uuid) "|" (if (nil? who) "n" (str "s" who))))

(defn- entries
  "[instant who] pairs from a flat [member score member score ...] reply."
  [flat]
  (mapv (fn [[m score]]
          (let [tag (subs m (inc (str/index-of m "|")))]
            [(java.time.Instant/ofEpochMilli (long (Double/parseDouble score)))
             (when (= \s (first tag)) (subs tag 1))]))
        (partition 2 flat)))

(defn- millis [^java.time.Instant i] (.toEpochMilli i))

(defrecord RedisStore [conn]
  security/LimitStore
  (-recent [_ k window]
    (let [cutoff (- (millis (time/now)) (.toMillis ^java.time.Duration window))]
      (entries (wcar conn (car/zrangebyscore (sighting-key k) cutoff "+inf" "WITHSCORES")))))
  (-sight! [_ k window who]
    (let [at (millis (time/now))
          ttl (.toMillis ^java.time.Duration window)]
      (entries (wcar conn (car/eval* sight-script 1 (sighting-key k)
                                     (- at ttl) at ttl (member who))))))
  (-busiest [_ window]
    (let [cutoff (- (millis (time/now)) (.toMillis ^java.time.Duration window))]
      (loop [cursor "0" acc {}]
        (let [[next-cursor ks] (wcar conn (car/scan cursor :match (str prefix "s:*") :count 500))
              counts (when (seq ks)
                       (wcar conn :as-pipeline (doseq [k ks] (car/zcount k cutoff "+inf"))))
              acc (reduce (fn [acc [k n]]
                            (let [limit (keyword (first (str/split (subs k (count (str prefix "s:"))) #":" 2)))]
                              (update acc limit (fnil max 0) (long n))))
                          acc (map vector ks counts))]
          (if (= "0" (str next-cursor)) acc (recur next-cursor acc))))))
  (-mark! [_ k ttl-ms]
    (wcar conn (car/set (mark-key k) "1" "PX" ttl-ms))
    nil)
  (-marked? [_ k]
    (= 1 (wcar conn (car/exists (mark-key k)))))
  (-unmark! [_ k]
    (wcar conn (car/del (mark-key k)))
    nil)
  (-update-notice! [_ k ttl-ms f]
    (let [rk (notice-key k)]
      ;; Optimistic: read, compute, write only if nobody else wrote in between; try again if so (a notice is claimed rarely, so this seldom loops).
      (loop [attempts 20]
        (let [old (wcar conn (car/get rk))
              entry (f (some-> old edn/read-string))]
          (cond
            (nil? entry) nil
            (= 1 (wcar conn (car/eval* swap-script 1 rk (or old "") (pr-str entry) ttl-ms))) entry
            (pos? (dec attempts)) (recur (dec attempts))
            :else (throw (ex-info "notice entry kept changing" {}))))))))

(defn redis-store
  "A store on the Redis at `url` (redis://[:password@]host:port; rediss:// for TLS)."
  [url]
  (->RedisStore {:pool {}
                 :spec {:uri url :conn-timeout-ms timeout-ms :read-timeout-ms timeout-ms}}))
