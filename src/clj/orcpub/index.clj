(ns orcpub.index
  (:require [hiccup.page :refer [html5 include-css]]
            [cheshire.core :as cheshire]
            [clojure.string :as str]
            [orcpub.config :as config]
            [orcpub.oauth :as oauth]
            [orcpub.fork.branding :as branding]
            [orcpub.dnd.e5.views-2 :as views-2]
            [orcpub.favicon :as fi]
            [orcpub.fork.integrations :as integrations]
            [environ.core :refer [env]]))

(def site-homebrew
  "Host-provided default homebrew, read once at startup from SITE_HOMEBREW_DIR
   (see orcpub.config/read-site-homebrew). Injected into the page so the client
   can load it as a read-only content layer beneath the user's own homebrew.
   A `delay` because it does file I/O — computed on first render, then cached.
   Change the files and restart to refresh (the version hash busts client caches
   automatically)."
  (delay (config/read-site-homebrew)))

(defn json-for-script
  "JSON-encode `x` for safe embedding inside an inline <script> block. Escapes
   the characters that could break out of the script element or trip a JS parser
   (`<`, `>`, `&`, and the U+2028/U+2029 line separators)."
  [x]
  (-> (cheshire/generate-string x)
      (str/replace "<" "\\u003c")
      (str/replace ">" "\\u003e")
      (str/replace "&" "\\u0026")
      (str/replace " " "\\u2028")
      (str/replace " " "\\u2029")))

(defn meta-tag [property content]
  (when content
    [:meta
     {:property property
      :content content}]))

(defn script-tag
  "Generate a script tag with optional nonce for CSP strict mode.
   For external scripts, pass :src. For inline scripts, pass content as body.
   Extra attributes (e.g. :async, :crossorigin) are passed through to the tag."
  [{:keys [nonce] :as opts} & body]
  (let [attrs (cond-> (dissoc opts :nonce)
                nonce (assoc :nonce nonce))]
    (if (seq body)
      (into [:script attrs] body)
      [:script attrs])))

(defn index-page [{:keys [url
                          title
                          description
                          image
                          fb-type
                          nonce]}
                  & [splash?]]
  (html5
   {:lang :en}
   [:head
    (meta-tag "og:url" url)
    (meta-tag "og:type" fb-type)
    (meta-tag "og:title" title)
    (meta-tag "og:description" description)
    (meta-tag "og:image" image)
    (meta-tag "og:site_name" branding/app-name)
    (meta-tag "og:type" "website")
    (meta-tag "twitter:card" "summary_large_image")
    (meta-tag "twitter:site" branding/app-name)
    (meta-tag "twitter:title" title)
    (meta-tag "twitter:description" description)
    (meta-tag "twitter:image" image)
    [:meta {:charset "UTF-8"}]
    [:meta {:name "viewport"
            :content "width=device-width, initial-scale=1.0, minimum-scale=1.0"}]
    (fi/install :png-prefix "favicon-"
                :img "/favicon"
                :xml "/favicon"
                :ver "1")
    (include-css "/css/cookiestyles.css")
    (script-tag {:nonce nonce}
     "document.documentElement.style.setProperty('--innerHeight', `${window.innerHeight}px`);
     window.addEventListener('resize', () => document.documentElement.style.setProperty('--innerHeight', `${window.innerHeight}px`));")
    [:style
     "
.splash-page-content {}
.splash-button .splash-button-content {height: 120px; width: 120px}
.splash-button .svg-icon {height: 64px; width: 64px}

@media (max-width: 767px)
{.splash-button .svg-icon {height: 32px; width: 32px}
.splash-button-title-prefix {display: none}
.splash-button .splash-button-content {height: 60px; width: 60px; font-size: 10px}
.legal-footer-parent {display: none}}

body {background-color: #080A0D}

#app {background-image: linear-gradient(182deg, #313A4D, #080A0D);background-attachment: fixed}

.app {height:100%;font-family:Open Sans, sans-serif}

.h-full {height: 100vh;height: var(--innerHeight, 100vh)}

.min-h-full {min-height: 100vh;min-height: var(--innerHeight, 100vh)}

html, body, div, span, applet, object, iframe,
h1, h2, h3, h4, h5, h6, p, blockquote, pre,
a, abbr, acronym, address, big, cite, code,
del, dfn, em, img, ins, kbd, q, s, samp,
small, strike, strong, sub, sup, tt, var,
b, u, i, center,
dl, dt, dd, ol, ul, li,
fieldset, form, label, legend,
table, caption, tbody, tfoot, thead, tr, th, td,
article, aside, canvas, details, figcaption, figure,
footer, header, hgroup, menu, nav, section, summary,
time, mark, audio, video {
	margin: 0;
	padding: 0;
	border: 0;
	outline: 0;
	font-size: 100%;
	font: inherit;
	vertical-align: baseline;
}
/* HTML5 display-role reset for older browsers */
article, aside, details, figcaption, figure,
footer, header, hgroup, menu, nav, section {
	display: block;
}
body {
	line-height: 1;
}
ol, ul {
	list-style: none;
}
blockquote, q {
	quotes: none;
}
blockquote:before, blockquote:after,
q:before, q:after {
	content: '';
	content: none;
}
ins {
	text-decoration: none;
}
del {
	text-decoration: line-through;
}

table {
	border-collapse: collapse;
	border-spacing: 0;
}

html {
	min-height: 100%;
}"]
    [:title title]
    (integrations/head-tags nonce)
    (script-tag {:nonce nonce}
     (str "window.__BRANDING__=" (cheshire/generate-string (branding/client-config)) ";"
          "window.__INTEGRATIONS__=" (cheshire/generate-string (integrations/client-config)) ";"))]
   [:body {:style "margin:0;line-height:1"}
    [:div#app
     (if splash?
       (views-2/splash-page)
       [:div.h-full {:style "display:flex;justify-content:space-around"}
        [:img {:src "/image/spiral.gif"
               :style "height:200px;width:200px;margin-top:200px"}]])]
    (include-css "/css/compiled/styles.css")
    ;; Dev mode uses Report-Only CSP (logs violations but doesn't block)
    ;; Prod mode uses enforcing CSP with nonces
    (script-tag {:src "/js/compiled/orcpub.js" :nonce nonce})
    (script-tag {:src "/js/cookies.js" :nonce nonce})
    (include-css "/assets/font-awesome/5.13.1/css/all.min.css")
    (include-css "https://fonts.googleapis.com/css?family=Open+Sans")
    (script-tag {:nonce nonce} " window.start.init({Palette:\"palette7\",Mode:\"banner bottom\",})")
    ;; Host-provided default homebrew. The app reads deploy/homebrew/*.orcbrew
    ;; at startup (SITE_HOMEBREW_DIR) and injects the raw sources plus a version
    ;; hash here. The client (::e5/site-plugins cofx) validates them through the
    ;; normal import pipeline and merges them as a read-only layer BENEATH the
    ;; user's own homebrew — never into the user's stored library. Replaces the
    ;; old client-side fetch that dumped raw text straight into localStorage.
    (let [{:keys [version sources]} @site-homebrew]
      (when (seq sources)
        (script-tag {:nonce nonce}
                    (str "window.orcpub_site_content="
                         (json-for-script {:version version :sources sources})
                         ";"))))
   ]))
  