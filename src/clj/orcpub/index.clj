(ns orcpub.index
  (:require [hiccup.page :refer [html5 include-css include-js]]
            [orcpub.oauth :as oauth]
            [orcpub.dnd.e5.views-2 :as views-2]
            [orcpub.favicon :as fi]))

(defn meta-tag [property content]
  (if content
    [:meta
     {:property property
      :content content}]))

(defn index-page [{:keys [url
                          title
                          description
                          image
                          fb-type]}
                  & [splash?]]
  (html5
   {:lang :en}
   [:head
    (meta-tag "og:url" url)
    (meta-tag "og:type" fb-type)
    (meta-tag "og:title" title)
    (meta-tag "og:description" description)
    (meta-tag "og:image" image)
    (meta-tag "og:site_name" "Dungeon Master's Vault")
    (meta-tag "og:type" "website")
    (meta-tag "twitter:card" "summary_large_image")
    (meta-tag "twitter:site" "Dungeon Master's Vault")
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
    [:script
     "document.documentElement.style.setProperty('--innerHeight', `${window.innerHeight}px`);
     window.addEventListener('resize', () => document.documentElement.style.setProperty('--innerHeight', `${window.innerHeight}px`));"]
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
    [:link {:rel "preconnect" :href "https://t.dungeonmastersvault.com/" :crossorigin ""}]
    [:script
     " var _paq = window._paq = window._paq || [];
      _paq.push([\"setDocumentTitle\", document.domain + \"/\" + document.title]);
      _paq.push([\"setCookieDomain\", \"*.www.dungeonmastersvault.com\"]);
      _paq.push([\"setDomains\", [\"*.www.dungeonmastersvault.com\"]]);
      _paq.push([\"setDoNotTrack\", true]);
      _paq.push(['trackPageView']);
      _paq.push(['enableLinkTracking']);
      (function() {
      var u=\"//t.dungeonmastersvault.com/\";
      _paq.push(['setTrackerUrl', u+'matomo.php']);
      _paq.push(['setSiteId', '7']);
      var d=document, g=d.createElement('script'), s=d.getElementsByTagName('script')[0];
      g.type='text/javascript'; g.async=true; g.src=u+'matomo.js'; s.parentNode.insertBefore(g,s);
      })();"]
    [:noscript "<p><img src=\"//t.dungeonmastersvault.com/matomo.php?idsite=7&amp;rec=1\" style=\"border:0;\" alt=\"\" /></p>"]

;<!-- IMPORTANT: Place these lines as high as you can in <head>, ideally just after <title> tag -->
;<link rel="preconnect" href="https://tags.bkrtx.com/">
    [:link {:rel "preconnect" :href "https://tags.bkrtx.com/"}]
;<link rel="preconnect" href="https://securepubads.g.doubleclick.net/" crossorigin>
    [:link {:rel "preconnect" :href "https://securepubads.g.doubleclick.net/" :crossorigin ""}]
;<link rel="preconnect" href="https://cdn.consentmanager.mgr.consensu.org/" crossorigin>
    [:link {:rel "preconnect" :href "https://cdn.consentmanager.mgr.consensu.org/" :crossorigin ""}]


;<!-- Place the below code anywhere you like in the <head> (higher is better) -->
;<script>window.AdSlots = window.AdSlots || {cmd: [], disableScripts: ['gpt','bk']};</script>
    [:script "window.AdSlots = window.AdSlots || {cmd: [], disableScripts: ['gpt','bk']};"]
;<script async src="https://tags.bkrtx.com/js/bk-coretag.js"></script>
    [:script {:async "" :src "https://tags.bkrtx.com/js/bk-coretag.js"}]
;<script async src="https://securepubads.g.doubleclick.net/tag/js/gpt.js"></script>
    [:script {:async "" :src "https://securepubads.g.doubleclick.net/tag/js/gpt.js"}]
;<script async src="/dist/dungeonmastersvault.min.js"></script>
    [:script {:async "" :src "/js/dungeonmastersvault.min.js"}]]
   [:body {:style "margin:0;line-height:1"}
    [:div#app
     (if splash?
       (views-2/splash-page)
       [:div.h-full {:style "display:flex;justify-content:space-around"}
        [:img {:src "/image/spiral.gif"
               :style "height:200px;width:200px;margin-top:200px"}]])]
    (include-css "/css/compiled/styles.css")
    (include-js "/js/compiled/orcpub.js")
    (include-js "/js/cookies.js")
    (include-css "/assets/font-awesome/5.13.1/css/all.min.css")
    (include-css "https://fonts.googleapis.com/css?family=Open+Sans")
    [:script " window.start.init({Palette:\"palette7\",Mode:\"banner bottom\",})"]    
    [:script
     "let plugins = localStorage.getItem('plugins');
    if(plugins === null || plugins === '{}')
     {
       fetch('https://' + window.location.host + '/homebrew.orcbrew')
         .then(resp => resp.text())
         .then(text => {
           if(!text.toUpperCase().includes('NOT FOUND')){
             localStorage.setItem('plugins',text);
             window.location.reload(false);
           }
       });
     }
    "]]))
