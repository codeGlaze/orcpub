(ns orcpub.dnd.e5.views.footer
  "The site footer under every app page: credits, help and support links, legal links and the
   legal notices."
  (:require [re-frame.core :refer [dispatch]]
            [orcpub.dnd.e5.views-2 :as views-2]
            [orcpub.dnd.e5.views.whats-new :as whats-new-view]
            [orcpub.fork.branding :as branding]
            [orcpub.fork.splash :as splash]
            [orcpub.ver :as v]))

(def srd-link
  "Link to the bundled SRD 5.1 PDF, as hiccup."
  [:a.orange {:href "/dnld/SRD-OGL_V5.1.pdf" :target "_blank"} "the 5e SRD-OGL 5.1"])

(defn current-year
  "The current calendar year in the viewer's time zone. Returns a number."
  []
  (.getFullYear (js/Date.)))

(defn site-footer
  "The footer's credits, link columns and legal notices. Takes no args; reads branding and splash
   settings. Returns hiccup."
  []
  ;; Top-aligned, spaced by the row's gap: a column that wraps starts on the left edge.
  [:div.flex.justify-cont-s-b.align-items-t.flex-wrap.p-10.site-footer-row
   [:div
    [:div.m-b-5 "Icons made by Lorc, Delapouite, Caduceus, Caro Asercion, Faithtoken, Sbed, Skoll and Willdabeast. Available on " [:a.orange {:href "http://game-icons.net"} "http://game-icons.net"]]
    [:div.m-b-5 "Artwork provided by the talented Sandra. Available on " [:a.orange {:href "https://www.deviantart.com/sandara" :target :_blank} "Deviantart"]]]
   [:div.site-footer-links
    [:div.m-b-5.justify-cont-c
     (when (seq branding/help-url)
       [:a.orange.m-r-5 {:href branding/help-url :target :_blank} "Help"])
     [:a.orange.m-r-5 {:href "https://github.com/Orcpub/orcpub/issues" :target :_blank} "Feedback/Bug Reports"]
     [whats-new-view/footer-link]]
    [views-2/support-pills :compact? true]]
   [:div
    [:div.m-b-5
     (for [{:keys [label href]} splash/legal-footer-links]
       ^{:key href}
       [:a.orange.m-r-5 {:href href :target :_blank} label])]]
   [:div.legal-footer
    [:p "© " (current-year) " "
     (if (seq branding/copyright-url)
       [:a.orange {:href branding/copyright-url :target :_blank} branding/copyright-holder]
       branding/copyright-holder)]
    [:p "This site is based on " srd-link " - Wizards of the Coast, Dungeons & Dragons, D&D, and their logos are trademarks of Wizards of the Coast LLC in the United States and other countries. © 2025 Wizards. All Rights Reserved."]
    [:p branding/app-name " is not affiliated with, endorsed, sponsored, or specifically approved by Wizards of the Coast LLC."]
    [:p.pointer.underline
     {:on-click #(dispatch [:orcpub.dnd.e5/open-whats-new])}
     "Version " (v/version) " (" (v/date) ") " (v/description) " edition"]]])
