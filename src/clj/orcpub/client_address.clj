(ns orcpub.client-address
  "The player's address behind proxies. Forwarded headers are trusted only as far as proxies we
   know appended them: walking X-Forwarded-For from the right, our own proxies are skipped and the
   first address we do not trust is the player's. Trusted: private networks (the Docker network the
   proxy reaches us on), TRUSTED_PROXIES, and Cloudflare's published ranges when TRUST_CLOUDFLARE is
   true. client-ip-behind-proxies.md on agents/develop."
  (:require [clojure.string :as s]
            [io.pedestal.interceptor :as interceptor]
            [orcpub.config :as config])
  (:import [java.net InetAddress]))

(def private-ranges
  "Networks a proxy in front of the app sits on: loopback, the private IPv4 ranges Docker and Swarm
   use, and IPv6 loopback and unique-local."
  ["127.0.0.0/8" "10.0.0.0/8" "172.16.0.0/12" "192.168.0.0/16" "::1/128" "fc00::/7"])

(def cloudflare-ranges
  "Cloudflare's published edge ranges (cloudflare.com/ips, checked 2026-10-22). Trusted only when
   TRUST_CLOUDFLARE is true: anyone can send requests from them through Cloudflare Workers."
  ["173.245.48.0/20" "103.21.244.0/22" "103.22.200.0/22" "103.31.4.0/22" "141.101.64.0/18"
   "108.162.192.0/18" "190.93.240.0/20" "188.114.96.0/20" "197.234.240.0/22" "198.41.128.0/17"
   "162.158.0.0/15" "104.16.0.0/13" "104.24.0.0/14" "172.64.0.0/13" "131.0.72.0/22"
   "2400:cb00::/32" "2606:4700::/32" "2803:f800::/32" "2405:b500::/32" "2405:8100::/32"
   "2a06:98c0::/29" "2c0f:f248::/32"])

(defn- address-bytes
  "The bytes of IP address text `a`, or nil when it is not an IP address. Never looks up a name."
  [a]
  (let [a (some-> a s/trim (s/replace #"^\[|\]$" ""))]
    (when (and (seq a)
               (or (re-matches #"\d{1,3}(\.\d{1,3}){3}" a)
                   (and (s/includes? a ":") (re-matches #"[0-9A-Fa-f:.]+" a))))
      (try (.getAddress (InetAddress/getByName a)) (catch Exception _ nil)))))

(defn parse-cidr
  "CIDR text `c` (\"10.0.0.0/8\", or a single address) as {:bytes :bits}, or nil when it is not one."
  [c]
  (let [[addr bits] (s/split (s/trim (str c)) #"/" 2)
        b (address-bytes addr)]
    (when b
      (let [n (if bits (parse-long bits) (* 8 (count b)))]
        (when (and n (<= 0 n (* 8 (count b)))) {:bytes b :bits n})))))

(defn in-range?
  "Whether IP address text `a` falls inside parsed CIDR `r` (from `parse-cidr`)."
  [a {:keys [bytes bits]}]
  (let [b (address-bytes a)]
    (boolean
     (and b (= (count b) (count bytes))
          (every? (fn [i]
                    (let [kept (min 8 (max 0 (- bits (* 8 i))))
                          mask (bit-and 0xff (bit-shift-left 0xff (- 8 kept)))]
                      (= (bit-and (aget b i) mask) (bit-and (aget bytes i) mask))))
                  (range (count b)))))))

(defn trusted-ranges
  "The parsed ranges trusted as proxies: `private-ranges`, the comma-separated `extra` (from
   TRUSTED_PROXIES), and `cloudflare-ranges` when `cloudflare?`. Unparsable entries are skipped."
  [extra cloudflare?]
  (keep parse-cidr (concat private-ranges
                           (when extra (s/split extra #","))
                           (when cloudflare? cloudflare-ranges))))

(defn client-address
  "The player's address for request `request`, given the parsed `trusted` ranges: its connecting
   address when that is not a trusted proxy; else the first untrusted address in X-Forwarded-For
   read from the right; else X-Real-IP; else the leftmost forwarded or the connecting address."
  [request trusted]
  (let [peer (:remote-addr request)
        trusted? (fn [a] (some #(in-range? a %) trusted))]
    (if-not (trusted? peer)
      peer
      (let [chain (->> (s/split (str (get-in request [:headers "x-forwarded-for"])) #",")
                       (map s/trim) (remove s/blank?) vec)
            real-ip (some-> (get-in request [:headers "x-real-ip"]) s/trim not-empty)]
        (or (some #(when (and (address-bytes %) (not (trusted? %))) %) (rseq chain))
            (when (and real-ip (address-bytes real-ip)) real-ip)
            (first (filter address-bytes chain))
            peer)))))

(defn interceptor-for
  "An interceptor that sets :remote-addr to the player's address (`client-address` with `trusted`)
   and keeps the connecting address as :peer-addr, so every limit and log line sees the player."
  [trusted]
  (interceptor/interceptor
   {:name ::client-address
    :enter (fn [context]
             (update context :request
                     (fn [req]
                       (assoc req :peer-addr (:remote-addr req)
                                  :remote-addr (client-address req trusted)))))}))

(defn configured-interceptor
  "`interceptor-for` with the ranges this server is configured to trust."
  []
  (interceptor-for (trusted-ranges (config/get-trusted-proxies) (config/trust-cloudflare?))))
