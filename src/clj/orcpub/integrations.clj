(ns orcpub.integrations
  "Third-party integrations (analytics, ads) gated on env vars.
   Defaults to DMV values; forks override via .env or disable with empty string."
  (:require [environ.core :refer [env]]))

;; Matomo analytics
(def matomo-url   (or (env :matomo-url)     "https://t.dungeonmastersvault.com/"))
(def matomo-site  (or (env :matomo-site-id) "7"))

;; Google AdSense
(def adsense-client (or (env :adsense-client) "ca-pub-3202063096003962"))

(defn- script-tag
  "Generate a script tag with optional nonce for CSP strict mode.
   Passes through all attributes to the tag."
  [{:keys [nonce] :as opts} & body]
  (let [attrs (cond-> (dissoc opts :nonce)
                nonce (assoc :nonce nonce))]
    (if (seq body)
      (into [:script attrs] body)
      [:script attrs])))

(defn matomo-tags
  "Matomo tracking: preconnect, inline config, noscript pixel."
  [nonce]
  (when (and (seq matomo-url) (seq matomo-site))
    (list
      [:link {:rel "preconnect" :href matomo-url :crossorigin ""}]
      (script-tag {:nonce nonce}
        (str "var _paq = window._paq = window._paq || [];"
             "_paq.push(['setCookieDomain', '*.'+location.hostname]);"
             "_paq.push(['setDomains', ['*.'+location.hostname]]);"
             "_paq.push(['setDoNotTrack', true]);"
             "(function(){var u='" matomo-url "';"
             "_paq.push(['setTrackerUrl', u+'matomo.php']);"
             "_paq.push(['setSiteId', '" matomo-site "']);"
             "var d=document,g=d.createElement('script'),s=d.getElementsByTagName('script')[0];"
             "g.async=true;g.src=u+'matomo.js';s.parentNode.insertBefore(g,s);"
             "})();"))
      [:noscript
        (str "<p><img src=\"" matomo-url "matomo.php?idsite=" matomo-site
             "&rec=1\" style=\"border:0\" alt=\"\"></p>")])))

(defn- adsense-tag
  "Google AdSense loader script."
  [nonce]
  (when (seq adsense-client)
    (script-tag {:nonce nonce :async ""
                 :src (str "https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js?client=" adsense-client)
                 :crossorigin "anonymous"})))

(defn head-tags
  "All third-party integration tags for <head>. Returns a seq of hiccup elements."
  [nonce]
  (keep identity
    [(adsense-tag nonce)
     (matomo-tags nonce)]))
