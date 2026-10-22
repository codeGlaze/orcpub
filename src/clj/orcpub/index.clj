(ns orcpub.index
  (:require [hiccup.page :refer [include-css]]
            [hiccup2.core :as h]
            [cheshire.core :as cheshire]
            [orcpub.oauth :as oauth]
            [orcpub.fork.branding :as branding]
            [orcpub.dnd.e5.views-2 :as views-2]
            [orcpub.favicon :as fi]
            [orcpub.fork.integrations :as integrations]
            [orcpub.loading-spinner :as spinner]
            [orcpub.route-map :as routes]
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

(defn script-json
  "JSON for an inline <script>: `<`, `>`, `&` and the line separators become
   \\u escapes, so a value (an artist's credit) cannot close the tag."
  [x]
  (-> (cheshire/generate-string x)
      (.replace "<" "\\u003c")
      (.replace ">" "\\u003e")
      (.replace "&" "\\u0026")
      (.replace "\u2028" "\\u2028")
      (.replace "\u2029" "\\u2029")))

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
    ;; raw: the page's own script; nothing in it comes from a request or a user
    (h/raw (str "
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
       window.orcpubBootOk = function () {
         el.style.display = 'none';
         window.orcpubBooted = true;
         var lr = document.getElementById('load-rescue');
         if (lr) { lr.style.display = 'none'; }
       };
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
    ")))))

(def ^:private load-rescue-delay-ms 8000)

(defn load-rescue
  "The notice under the loading picture on a character page or the builder: hidden, shown after
   8 seconds if the app has not rendered (`window.orcpubBooted`), with a link to Data View.
   Inline styles and script only, so it works when the app's files do not."
  [nonce]
  (let [char-path (routes/path-for routes/dnd-e5-char-page-route :id "ID")
        repair-path (routes/path-for routes/dnd-e5-char-repair-page-route :id "ID")
        builder-paths [(routes/path-for routes/dnd-e5-char-builder-route)
                       (routes/path-for routes/dnd-e5-newb-char-builder-route)]
        draft-path (routes/path-for routes/dnd-e5-draft-data-page-route)]
    (list
     [:div#load-rescue
      {:style (str "display:none;position:fixed;left:50%;top:404px;transform:translateX(-50%);"
                   "width:min(92vw,440px);box-sizing:border-box;z-index:2147482000;"
                   "flex-wrap:wrap;gap:6px 16px;align-items:center;justify-content:space-between;"
                   "padding:12px 16px;border-radius:6px;border:2px solid transparent;"
                   "background:linear-gradient(#2c3445,#2c3445) padding-box,"
                   "linear-gradient(90deg,#b8860b,#f0a100,#b8860b) border-box;"
                   "font-family:Open Sans,system-ui,sans-serif;font-size:14px;line-height:1.45;color:#e8ebf0")}
      [:span#load-rescue-note]
      [:a#load-rescue-link
       {:style (str "color:#f0a100;font-weight:600;text-decoration:underline;white-space:nowrap;"
                    "display:inline-flex;align-items:center;min-height:44px")}]]
     (script-tag
      {:nonce nonce}
      ;; raw: the page's own script; its paths come from the route map, not a request
      (h/raw (str "
     (function () {
       var el = document.getElementById('load-rescue');
       var note = document.getElementById('load-rescue-note');
       var link = document.getElementById('load-rescue-link');
       if (!el || !note || !link) { return; }
       var path = window.location.pathname.replace(/\\/+$/, '');
       var charPath = " (cheshire/generate-string char-path) ";
       var idAt = charPath.indexOf('ID');
       var prefix = charPath.slice(0, idAt), suffix = charPath.slice(idAt + 2);
       var builders = " (cheshire/generate-string builder-paths) ";
       var target = null;
       if (path.indexOf(prefix) === 0 && path.slice(path.length - suffix.length) === suffix) {
         var id = path.slice(prefix.length, path.length - suffix.length);
         if (/^\\d+$/.test(id)) {
           target = ['This character didn\u2019t load.', 'Repair this character',
                     " (cheshire/generate-string repair-path) ".replace('ID', id)];
         }
       } else if (builders.indexOf(path) !== -1) {
         target = ['The builder didn\u2019t load.', 'Repair this draft', " (cheshire/generate-string draft-path) "];
       }
       if (!target) { return; }
       setTimeout(function () {
         if (window.orcpubBooted || !document.getElementById('load-rescue')) { return; }
         note.textContent = target[0];
         link.textContent = target[1];
         link.href = target[2];
         el.style.display = 'flex';
       }, " load-rescue-delay-ms ");
     })();
    "))))))

(defn index-page [{:keys [url
                          title
                          description
                          image
                          fb-type
                          nonce
                          spinner-kind]}
                  & [splash?]]
  ;; hiccup2 escapes every string by default; html5 (hiccup 1) escaped none, so a character's
  ;; name went into <title> as markup. Only the page's own scripts and styles are raw, below.
  (str
   "<!DOCTYPE html>\n"
   (h/html
    {:mode :html}
    [:html {:lang "en"}
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
    ;; Opt out of generative-AI training and image reuse. This is the
    ;; DeviantArt-originated convention: a `robots` meta carrying only the
    ;; `noai`/`noimageai` tokens, which does NOT imply noindex. Voluntary --
    ;; honoured by some crawlers, ignored by others -- so it sits alongside
    ;; robots.txt and the art licence rather than replacing either.
    [:meta {:name "robots" :content "noai, noimageai"}]
    [:meta {:charset "UTF-8"}]
    [:meta {:name "viewport"
            :content "width=device-width, initial-scale=1.0, minimum-scale=1.0"}]
    ;; raw: a hiccup 1 helper that returns its markup as a string
    (h/raw (fi/install :png-prefix "favicon-"
                       :img "/favicon"
                       :xml "/favicon"
                       :ver "1"))
    (include-css "/css/cookiestyles.css")
    (script-tag {:nonce nonce}
     (h/raw "document.documentElement.style.setProperty('--innerHeight', `${window.innerHeight}px`);
     window.addEventListener('resize', () => document.documentElement.style.setProperty('--innerHeight', `${window.innerHeight}px`));"))
    [:style
     (h/raw "
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
}")]
    [:style (h/raw spinner/css)]
    [:title title]
    (integrations/head-tags nonce)
    (script-tag {:nonce nonce}
     ;; raw: operator settings as JSON, escaped for a script by script-json
     (h/raw (str "window.__BRANDING__=" (script-json (branding/client-config)) ";"
                 "window.__INTEGRATIONS__=" (script-json (integrations/client-config)) ";")))]
   [:body {:style "margin:0;line-height:1"}
    [:div#app
     (if splash?
       (views-2/splash-page)
       ;; raw: the spinner's own markup; its kind is one of three keywords (spinner/pick)
       (h/raw (spinner/markup (or spinner-kind (spinner/pick nil)))))]
    ;; Homebrew rescue, a dead-man's switch: present by default, removed by the app once it has
    ;; rendered, so any boot failure (broken bundle, CLJS error, crashing homebrew) leaves it up.
    ;; It depends on nothing the app owns: server-rendered markup, inline styles (styles.css may
    ;; not have loaded), plain localStorage, and a vanilla anchor download, not FileSaver.
    (boot-rescue nonce)
    (when-not splash? (load-rescue nonce))
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
    (script-tag {:nonce nonce} (h/raw " window.start.init({Palette:\"palette7\",Mode:\"banner bottom\",})"))
    (when homebrew-url
      (script-tag {:nonce nonce}
       ;; raw: the page's own script; homebrew-url is the operator's LOAD_HOMEBREW_URL setting
       (h/raw (str "
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
       "))))]])))
  