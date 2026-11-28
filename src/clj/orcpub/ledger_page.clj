(ns orcpub.ledger-page
  "The Data View page (the ledger): server-rendered, inline styles, and only its own
   script (`/js/compiled/ledger.js`, built from `ledger.cljs.edn`), never the app bundle, so it
   opens whatever state a character is in. character-rescue.md"
  (:require [hiccup.page :refer [html5]]
            [orcpub.index :as index]))

(def ^:private script-path "/js/compiled/ledger.js")

(def ^:private styles
  "The page's layout: a column of cards, 44px buttons, and the save notice, which sits at the top
   on wide screens and across the bottom on narrow ones."
  (str ".dv{max-width:720px;line-height:1.4}"
       ".dv-name{font-size:18px;font-weight:600;margin:0}.dv-count{color:#aab3c2;margin:2px 0 12px}"
       ".dv-tools{display:flex;flex-wrap:wrap;gap:8px;margin:0 0 10px}"
       ".dv-btn{font:inherit;font-weight:600;min-height:44px;padding:0 16px;border-radius:4px;cursor:pointer;"
       "background:transparent;color:#e8ebf0;border:1px solid #3d4659;white-space:nowrap}"
       ".dv-btn.primary{background:#f0a100;color:#080A0D;border:0}"
       ".dv-btn:focus-visible{outline:2px solid #f0a100;outline-offset:2px}"
       ".dv-card{background:#2c3445;border-radius:8px;padding:8px 14px 10px;margin:0 0 10px}"
       ".dv-row{display:flex;align-items:center;gap:12px;min-height:48px;padding:2px 0}"
       ".dv-text{flex:1;min-width:0}.dv-label{font-weight:600}.dv-top>.dv-text>.dv-label{font-weight:700}"
       ".dv-meta{font-size:12px;color:#aab3c2;overflow-wrap:anywhere}.dv-meta code{font-size:12px}"
       ".dv-sub{font-size:11px;font-weight:700;letter-spacing:.08em;text-transform:uppercase;color:#aab3c2;"
       "margin:10px 0 0 18px}"
       ".dv-kid{margin-left:18px}.dv-inset{background:#1f2533;border-radius:6px;padding:2px 12px;margin:2px 0 6px 36px}"
       ".dv-removed .dv-text{opacity:.5}.dv-removed .dv-label{text-decoration:line-through}"
       ".dv-gone{font-size:13px}"
       ".dv-notice{position:sticky;top:0;z-index:5;background:#1f2533;padding:8px 0;margin:0 0 4px}"
       ".dv-notice-box{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap;"
       "padding:8px 14px;border-radius:8px;border:2px solid transparent;"
       "background:linear-gradient(#2c3445,#2c3445) padding-box,linear-gradient(90deg,#b8860b,#f0a100,#b8860b) border-box}"
       "@media (max-width:640px){.dv-notice{position:fixed;top:auto;bottom:0;left:0;right:0;margin:0;"
       "padding:8px 16px 10px}.dv-spacer{height:96px}}"
       ".dv-note{color:#f0a100;font-weight:600;margin:6px 0}"
       ".dv-raw{white-space:pre-wrap;word-break:break-all;font-size:12px;color:#aab3c2;background:#2c3445;"
       "padding:10px;border-radius:6px;max-height:60vh;overflow:auto}"
       ".dv-veil{position:fixed;inset:0;z-index:20;background:rgba(8,10,13,.65);display:flex;align-items:center;"
       "justify-content:center;padding:16px}"
       ".dv-dialog{width:min(100%,420px);box-sizing:border-box;background:#2c3445;border:1px solid #3d4659;"
       "border-radius:10px;padding:18px 18px 14px;box-shadow:0 12px 32px rgba(0,0,0,.5)}"
       ".dv-dialog h3{font-size:18px;margin:0 0 8px}.dv-dialog p{margin:4px 0}.dv-dialog .dv-muted{color:#aab3c2;font-size:13px}"
       ".dv-actions{display:flex;gap:8px;justify-content:flex-end;flex-wrap:wrap;margin-top:16px}"))

(defn page
  "HTML for the Data View page. `id` is a saved character's id, or nil for this browser's draft;
   `nonce` is the request's CSP nonce, or nil."
  [{:keys [id nonce]}]
  (html5
   {:lang :en}
   [:head
    [:meta {:charset "utf-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    [:meta {:name "robots" :content "noindex"}]
    [:title "Data View"]
    [:style styles]]
   [:body {:style (str "margin:0;padding:16px;background:#1f2533;color:#e8ebf0;"
                       "font-family:Open Sans,system-ui,sans-serif;font-size:14px")}
    [:h1 {:style "font-size:20px;margin:0 0 12px"} "Data View"]
    [:div#ledger {:data-mode (if id "saved" "draft") :data-id (some-> id str)}
     "Loading character data"]
    [:noscript "This page needs JavaScript."]
    (index/script-tag {:src script-path :nonce nonce})]))

(defn page-response
  "The Data View page as a Ring response, for the character at path-param :id (already parsed by
   `parse-id`) or, without one, for this browser's draft."
  [{:keys [csp-nonce] {:keys [id]} :path-params}]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (page {:id id :nonce csp-nonce})})
