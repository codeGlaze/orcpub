(ns orcpub.artist-links
  "The rules for an artist's credit links, shared by the server and the page.

   The server enforces them on save (orcpub.artist-credit); the account page
   runs the very same functions on every keystroke, so what the form says
   while you type is what the server will say -- there is no second copy of
   the rules to drift.

   Plain string work, no java.net.URI, so it reads the same on both sides."
  (:require [clojure.string :as s]))

(def max-name-length 60)
(def max-url-length 200)
(def max-links
  "The credit lockup flanks the name with at most four marks."
  4)

(def services
  "The marks a link can wear, each with its brand colour and the hosts it
   belongs to. The icon is worked out from the link, never chosen, so it
   can't disagree with where the link goes -- a Twitch mark that opened some
   other site is exactly the trick a lookalike phishing link plays. Anything
   that isn't a known service gets `site`, a plain globe. Ordered: the first
   whose host matches wins, and `site` matches everything."
  (array-map
   "twitch"  {:label "Twitch"  :color "#9146ff" :hosts #{"twitch.tv"}}
   "bluesky" {:label "Bluesky" :color "#1185fe" :hosts #{"bsky.app"}}
   "kofi"    {:label "Ko-fi"   :color "#ff5e5b" :hosts #{"ko-fi.com"}}
   "site"    {:label "Site"    :color "#f0a100" :hosts nil}))

(defn- ascii-lower
  "Lower case for ASCII names and hosts. On the JVM the default locale decides the folding, and
   Turkish turns I into a dotless i; these strings are compared with fixed ASCII."
  [x]
  #?(:clj (.toLowerCase (str x) java.util.Locale/ROOT) :cljs (s/lower-case x)))

(defn parse
  "{:scheme :userinfo :host} for an absolute URL, or nil."
  [url]
  (when-let [[_ scheme authority] (re-find #"^([A-Za-z][A-Za-z0-9+.-]*)://([^/?#]*)" (s/trim (str url)))]
    (let [[_ userinfo hostport] (re-find #"^(?:(.*)@)?(.*)$" authority)]
      {:scheme (ascii-lower scheme)
       :userinfo userinfo
       :host (ascii-lower (s/replace hostport #":\d*$" ""))})))

(defn- host-matches? [host allowed]
  (some #(or (= host %) (s/ends-with? host (str "." %))) allowed))

(defn icon-for-url
  "The mark for a link: the service whose host it points at, else `site`."
  [url]
  (let [host (or (:host (parse url)) "")]
    (or (some (fn [[icon {:keys [hosts]}]] (when (and hosts (host-matches? host hosts)) icon))
              services)
        "site")))

(defn service-for-url
  "The icon, label and colour a link will be shown with."
  [url]
  (let [icon (icon-for-url url)]
    (assoc (services icon) :icon icon)))

(defn url-problem
  "Why `url` can't be used as a credit link, or nil when it can. Only https:
   a credit link is followed by strangers, and an http one can be rewritten
   on the way."
  [url]
  (let [url (some-> url str s/trim)
        {:keys [scheme userinfo host]} (parse url)]
    (cond
      (s/blank? url) "Add a link"
      (> (count url) max-url-length) (str "Links can be up to " max-url-length " characters")
      (not= "https" scheme) "Links must start with https://"
      (or (s/blank? host) (not (s/includes? host ".")) (re-find #"\s" host)
          (s/starts-with? host ".") (s/ends-with? host "."))
      "That doesn't look like a web address"
      (some? userinfo) "That doesn't look like a web address")))

(defn typing-problem
  "What to say about a link while it is still being typed: the same rules,
   minus the ones that only mean 'not finished yet'. Nothing for an empty box
   or for the start of https:// itself, so typing 'htt' is not an error; the
   scheme is flagged the moment it goes wrong, not at save. `done?` (the box
   has lost focus) turns the rest on, so 'https://fuss' is not called
   malformed halfway through the word."
  [url done?]
  (let [u (s/trim (str url))
        p (url-problem u)]
    (cond
      (s/blank? u) nil
      (s/starts-with? "https://" (ascii-lower u)) nil
      (= p "Links must start with https://") p
      done? p
      (and p (s/starts-with? p "Links can be up to")) p
      :else nil)))

(defn clean-name [n]
  (let [n (some-> n str s/trim (s/replace #"\s+" " "))]
    (when-not (s/blank? n) n)))

(defn name-problem [n]
  (let [n (clean-name n)]
    (when (and n (> (count n) max-name-length))
      (str "Names can be up to " max-name-length " characters"))))

(defn link-entry
  "A link as it is stored and drawn, its mark worked out from the address."
  [url]
  (let [{:keys [icon label color]} (service-for-url url)]
    {:link/label label :link/icon icon :link/color color :link/url url}))

(defn edit-errors
  "Every problem with an edit {:name :link :links [{:url}]}, keyed by field,
   with :link-errors indexed by position among the non-blank links. Empty
   when the edit can be saved."
  [{:keys [name link links]}]
  (let [link (some-> link str s/trim not-empty)
        urls (->> links (map #(some-> (:url %) str s/trim)) (remove s/blank?))
        link-errors (into {} (keep-indexed (fn [i u] (when-let [p (url-problem u)] [i p]))) urls)]
    (cond-> {}
      (name-problem name) (assoc :name (name-problem name))
      (and link (url-problem link)) (assoc :link (url-problem link))
      (> (count urls) max-links) (assoc :links (str "Up to " max-links " links"))
      (seq link-errors) (assoc :link-errors link-errors))))
