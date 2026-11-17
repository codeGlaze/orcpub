(ns orcpub.dnd.e5.views.footer
  "The site footer under every app page: labelled rows of links (Help, Support, Legal), then a
   muted band with the copyright, the SRD notice, the credits and the version."
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

(defn- footer-row
  "One labelled row: `label` in small caps, then `links` (hiccup) inline. Returns hiccup, or nil
   when `links` is empty, so a group with nothing to show takes no space."
  [label links]
  (when (seq (remove nil? links))
    (into [:div.site-footer-group [:div.site-footer-label label]] (remove nil? links))))

(defn site-footer
  "The footer's link rows and legal band. Takes no args; reads branding and splash settings.
   Returns hiccup."
  []
  [:div.site-footer
   [:div.site-footer-groups
    (footer-row "Help"
                [(when (seq branding/help-url)
                   [:a.orange {:href branding/help-url :target :_blank} "Help"])
                 [:a.orange {:href "https://github.com/Orcpub/orcpub/issues" :target :_blank} "Feedback/Bug Reports"]
                 [whats-new-view/footer-link]])
    (footer-row "Support" [(views-2/support-pills :compact? true)])
    (footer-row "Legal"
                (for [{:keys [label href]} splash/legal-footer-links]
                  ^{:key href}
                  [:a.orange {:href href :target :_blank} label]))]
   [:div.legal-footer
    [:p "© " (current-year) " "
     (if (seq branding/copyright-url)
       [:a.orange {:href branding/copyright-url :target :_blank} branding/copyright-holder]
       branding/copyright-holder)]
    [:p "This site is based on " srd-link " - Wizards of the Coast, Dungeons & Dragons, D&D, and their logos are trademarks of Wizards of the Coast LLC in the United States and other countries. © 2025 Wizards. All Rights Reserved."]
    [:p branding/app-name " is not affiliated with, endorsed, sponsored, or specifically approved by Wizards of the Coast LLC."]
    [:p "Icons by Lorc, Delapouite, Caduceus, Caro Asercion, Faithtoken, Sbed, Skoll and Willdabeast ("
     [:a.orange {:href "http://game-icons.net"} "game-icons.net"]
     "). Artwork by Sandra ("
     [:a.orange {:href "https://www.deviantart.com/sandara" :target :_blank} "Deviantart"] ")."]
    [:p.pointer.underline
     {:on-click #(dispatch [:orcpub.dnd.e5/open-whats-new])}
     "Version " (v/version) " (" (v/date) ") " (v/description) " edition"]]])
