(ns orcpub.pwned
  "Screens a password against Have I Been Pwned without ever sending it.

   The password is hashed locally and only the first five hex characters of that
   hash leave this process. The range endpoint answers with every suffix sharing
   that prefix -- several hundred of them -- and the comparison happens here, so
   a listener who breaks TLS learns which of roughly eight hundred hashes the
   password might be, and nothing more.

   The check is advisory by construction. Every failure path returns :unknown
   rather than throwing or blocking: this service must not be able to take
   registration down when a third party is slow, rate-limiting, or gone."
  (:require [clj-http.client :as client]
            [clojure.string :as s]
            [orcpub.config :as config])
  (:import (java.security MessageDigest)))

;; Counted since boot so the check can be audited at all. Without these a
;; permanently broken lookup -- an unset variable, a firewall, a CDN that has
;; started refusing our User-Agent -- is indistinguishable from a working one:
;; registration succeeds either way and nobody is ever told their password is
;; common. Fail-open is the right call; fail-open and BLIND is not.
(def ^:private tally (atom {:asked 0 :common 0 :clear 0 :unavailable 0}))

(defn stats [] @tally)

(defn take-stats!
  "The counts since the last time anyone asked, and resets them. Used by the
   hourly summary, which reports a period rather than a running total."
  []
  (first (reset-vals! tally {:asked 0 :common 0 :clear 0 :unavailable 0})))

(defn summary-line
  "A line for the log, or nil when nothing happened and there is nothing to say."
  [{:keys [asked common clear unavailable]}]
  (when (pos? (or asked 0))
    (format "pwned: %d checked, %d common, %d clear, %d unavailable" asked common clear unavailable)))

(def ^:private endpoint "https://api.pwnedpasswords.com/range/")

;; Short enough that a stalled third party cannot hold a signup open. Registration
;; already does real work after this; seconds here are seconds a person waits.
(def ^:private timeout-ms 2500)

(defn- sha1-hex [^String value]
  (let [digest (.digest (MessageDigest/getInstance "SHA-1")
                        (.getBytes value "UTF-8"))]
    (s/upper-case (apply str (map #(format "%02x" %) digest)))))

(defn- parse-suffixes
  "The body is SUFFIX:COUNT per line. Returns a map of suffix to count."
  [body]
  (into {}
        (keep (fn [line]
                (let [[suffix count-str] (s/split (s/trim line) #":" 2)]
                  (when (and suffix count-str)
                    (when-let [n (try (Long/parseLong (s/trim count-str))
                                      (catch Exception _ nil))]
                      [(s/upper-case suffix) n])))))
        (s/split-lines (or body ""))))

;; A sustained outage would otherwise write a line per signup. One a minute is
;; enough to see it in a log without burying everything else.
(def ^:private log-window-ms 60000)
(def ^:private last-logged (atom 0))

(defn- note-unavailable! [reason]
  (swap! tally update :unavailable inc)
  (let [now (System/currentTimeMillis)
        [before _] (swap-vals! last-logged #(if (> (- now %) log-window-ms) now %))]
    (when (> (- now before) log-window-ms)
      ;; The reason carries no password material: not the password, not its hash,
      ;; not the prefix, not the account it belonged to.
      (println "pwned: lookup unavailable -" reason)))
  :unknown)

(defn check
  "How many known breaches this password appears in.

   Returns a count (0 means not found), or :unknown when the service could not
   answer. :unknown is not a failure to handle -- it means the caller learned
   nothing and should carry on."
  [password]
  (if (or (s/blank? password) (not (config/pwned-check-enabled?)))
    :unknown
    (try
      (swap! tally update :asked inc)
      (let [digest (sha1-hex password)
            prefix (subs digest 0 5)
            suffix (subs digest 5)
            ;; Add-Padding makes every response a similar size, so the length of
            ;; the reply does not hint at how common the password is.
            {:keys [status body]} (client/get (str endpoint prefix)
                                              {:headers {"Add-Padding" "true"
                                                         "User-Agent" "orcpub-password-check"}
                                               :socket-timeout timeout-ms
                                               :connection-timeout timeout-ms
                                               :throw-exceptions false
                                               ;; The CDN sets a cookie we neither want nor
                                               ;; can parse; without this every call logs a
                                               ;; warning about it on the way past.
                                               :cookie-policy :none
                                               ;; A redirect off this host would be
                                               ;; somewhere we never agreed to talk to.
                                               :redirect-strategy :none})]
        (if (= 200 status)
          (let [n (get (parse-suffixes body) suffix 0)]
            (swap! tally update (if (pos? n) :common :clear) inc)
            n)
          (note-unavailable! (str "HTTP " status))))
      (catch Exception e
        ;; The class and message describe the transport, never the password.
        (note-unavailable! (.. e getClass getSimpleName))))))

(defn breached?
  "True only when the service positively said this password is known. An
   unreachable service is not a breached password."
  [password]
  (let [result (check password)]
    (and (number? result) (pos? result))))

(defn summary-job
  "Heartbeat job: one line an hour, and only when something was checked."
  [_conn]
  (when-let [line (summary-line (take-stats!))]
    (println line)))
