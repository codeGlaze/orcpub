(ns orcpub.index
  (:require [hiccup.page :refer [html5 include-css]]
            [cheshire.core :as cheshire]
            [orcpub.oauth :as oauth]
            [orcpub.fork.branding :as branding]
            [orcpub.dnd.e5.views-2 :as views-2]
            [orcpub.favicon :as fi]
            [orcpub.fork.integrations :as integrations]
            [orcpub.loading-spinner :as spinner]
            [orcpub.env :as env]))

(def homebrew-url
  "URL to fetch server-hosted .orcbrew plugins from on first load.
   Set LOAD_HOMEBREW_URL to enable (e.g. \"/homebrew.orcbrew\" or a full URL).
   When unset, no fetch is attempted — plugins come only from local imports."
  (env/value :load-homebrew-url))

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

(def ^:private rescue-filename "orcpub-homebrew-rescue.orcbrew")

(defn boot-rescue
  "The rescue control plus the inline script that arms it. Separate <script>
   tags are independent, so a throw inside the app bundle cannot stop this one
   from having run. `window.orcpubBootOk` is what the app calls to take it away."
  [nonce]
  (list
   [:div#boot-rescue
    {:style (str "position:fixed;left:0;right:0;bottom:0;z-index:2147483000;"
                 "display:none;gap:12px;align-items:center;justify-content:center;"
                 "flex-wrap:wrap;padding:10px 14px;background:#2c3445;"
                 "border-top:1px solid rgba(255,255,255,0.15);"
                 "font-family:Open Sans,system-ui,sans-serif;font-size:13px;"
                 "color:#e8ebf0;line-height:1.5")}
    [:span#boot-rescue-note]
    [:button#boot-rescue-btn
     {:type "button"
      :style (str "font:inherit;font-weight:600;letter-spacing:0.04em;"
                  "text-transform:uppercase;color:#080A0D;background:#f0a100;"
                  "border:0;border-radius:3px;padding:8px 16px;cursor:pointer")}
     "Download my homebrew"]]
   (script-tag
    {:nonce nonce}
    (str "
     (function () {
       var el = document.getElementById('boot-rescue');
       var btn = document.getElementById('boot-rescue-btn');
       var note = document.getElementById('boot-rescue-note');
       if (!el || !btn || !note) { return; }

       // Read at every decision point, never once at load. A snapshot taken when
       // the page opened is how a rescue hands someone an empty or hours-old
       // file: they had nothing when it loaded, or they built for an hour after,
       // and the copy it kept never moved.
       function readPlugins() {
         try {
           var v = localStorage.getItem('plugins');
           return (!v || v === '{}') ? null : v;
         } catch (e) { return null; }
       }

       function sizeOf(s) {
         var kb = s.length / 1024;
         return kb < 1 ? s.length + ' bytes'
              : kb < 10 ? kb.toFixed(1) + ' KB'
              : Math.round(kb) + ' KB';
       }

       // Shown only when there is something to save, and labelled with what is
       // there RIGHT NOW — the size is the only evidence the user has that the
       // file about to download is really their content.
       function show() {
         var raw = readPlugins();
         if (!raw) { el.style.display = 'none'; return; }
         note.textContent = 'Your homebrew is saved in this browser ('
           + sizeOf(raw) + '). Download a copy:';
         el.style.display = 'flex';
       }

       // Hidden, not removed: the app can crash AFTER a clean first render, and
       // then the error screen asks for it back — re-checked at that moment, so
       // work done during the session counts even if the page opened empty.
       window.orcpubBootOk = function () { el.style.display = 'none'; };
       window.orcpubBootRescue = show;

       btn.addEventListener('click', function () {
         var raw = readPlugins();
         if (!raw) {
           note.textContent = 'Nothing is saved in this browser to download.';
           return;
         }
         var url = URL.createObjectURL(
           new Blob([raw], { type: 'text/plain;charset=utf-8' }));
         var a = document.createElement('a');
         a.href = url;
         a.download = '" rescue-filename "';
         document.body.appendChild(a);
         a.click();
         document.body.removeChild(a);
         setTimeout(function () { URL.revokeObjectURL(url); }, 1000);
       });

       show();
     })();
    "))))

(defn index-page [{:keys [url
                          title
                          description
                          image
                          fb-type
                          nonce
                          spinner-kind]}
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
    [:style spinner/css]
    [:title title]
    (integrations/head-tags nonce)
    (script-tag {:nonce nonce}
     (str "window.__BRANDING__=" (cheshire/generate-string (branding/client-config)) ";"
          "window.__INTEGRATIONS__=" (cheshire/generate-string (integrations/client-config)) ";"))]
   [:body {:style "margin:0;line-height:1"}
    [:div#app
     (if splash?
       (views-2/splash-page)
       (spinner/markup (or spinner-kind (spinner/pick nil))))]
    ;; Homebrew rescue, a dead-man's switch: present by default, removed by the app once it has
    ;; rendered, so any boot failure (broken bundle, CLJS error, crashing homebrew) leaves it up.
    ;; It depends on nothing the app owns: server-rendered markup, inline styles (styles.css may
    ;; not have loaded), plain localStorage, and a vanilla anchor download, not FileSaver.
    (boot-rescue nonce)
    (include-css "/css/compiled/styles.css")
    ;; Every script tag carries the per-request nonce. It is nil in dev mode,
    ;; where no CSP header is set at all; enforcing otherwise.
    (script-tag {:src "/js/compiled/orcpub.js" :nonce nonce})
    (script-tag {:src "/js/cookies.js" :nonce nonce})
    (include-css "/assets/font-awesome/5.13.1/css/all.min.css")
    ;; 400, 600 and 700: the stylesheet asks for bold on tabs, titles and labels, and with only
    ;; 400 loaded Chrome rendered all of it at regular weight. One variable file serves all
    ;; three, so the extra weights cost nothing over 400 alone. See docs/design/style-guide.md.
    (include-css "https://fonts.googleapis.com/css2?family=Open+Sans:wght@400;600;700&display=swap")
    (script-tag {:nonce nonce} " window.start.init({Palette:\"palette7\",Mode:\"banner bottom\",})")
    (when homebrew-url
      (script-tag {:nonce nonce}
       (str "
        const noLibrary = () => {
          const p = localStorage.getItem('plugins');
          return p === null || p === '{}' || p === '{\"Default Option Source\" {}}';
        };
        if (noLibrary()) {
          fetch('" homebrew-url "')
            .then(resp => {
              if (!resp.ok) {
                throw new Error('Failed to fetch plugins: ' + resp.status);
              }
              return resp.text();
            })
            .then(text => {
              // only into a library that is still empty, and bumped like any write so other tabs reload
              if (!text.toUpperCase().includes('NOT FOUND') && noLibrary()) {
                localStorage.setItem('plugins', text);
                localStorage.setItem('plugins:rev', String(Number(localStorage.getItem('plugins:rev') || 0) + 1));
                window.location.reload(false);
              }
            })
            .catch(error => {
              console.error('Error fetching plugins:', error);
            });
        }
       ")))
   ]))
  