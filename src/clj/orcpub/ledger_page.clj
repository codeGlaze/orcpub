(ns orcpub.ledger-page
  "The character data page (the ledger): server-rendered, inline styles, and only its own
   script (`/js/compiled/ledger.js`, built from `ledger.cljs.edn`), never the app bundle, so it
   opens whatever state a character is in. character-rescue.md"
  (:require [hiccup.page :refer [html5]]
            [orcpub.index :as index]))

(def ^:private script-path "/js/compiled/ledger.js")

(defn page
  "HTML for the data page. `id` is a saved character's id, or nil for this browser's draft;
   `nonce` is the request's CSP nonce, or nil."
  [{:keys [id nonce]}]
  (html5
   {:lang :en}
   [:head
    [:meta {:charset "utf-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    [:meta {:name "robots" :content "noindex"}]
    [:title "Character data"]]
   [:body {:style (str "margin:0;padding:16px;background:#1f2533;color:#e8ebf0;"
                       "font-family:Open Sans,system-ui,sans-serif;font-size:14px")}
    [:h1 {:style "font-size:20px;margin:0 0 12px"} "Character data"]
    [:div#ledger {:data-mode (if id "saved" "draft") :data-id (some-> id str)}
     "Loading character data"]
    [:noscript "This page needs JavaScript."]
    (index/script-tag {:src script-path :nonce nonce})]))

(defn page-response
  "The data page as a Ring response, for the character at path-param :id (already parsed by
   `parse-id`) or, without one, for this browser's draft."
  [{:keys [csp-nonce] {:keys [id]} :path-params}]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (page {:id id :nonce csp-nonce})})
