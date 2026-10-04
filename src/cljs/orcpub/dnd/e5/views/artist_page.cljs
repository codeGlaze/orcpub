(ns orcpub.dnd.e5.views.artist-page
  "Views for the public artist profiles, /artists and /artists/<slug>. What they show is
   decided in artist-profile (cljc). The page wears .pl-root so the drawer's stylesheet,
   credit face and --lk-* theme variables apply; what the drawer lacks is .ap-*, in
   styles/core.clj `artist-pages`.
   Plan and look: docs/design/artist-profiles/PLAN.md."
  (:require [re-frame.core :refer [subscribe]]
            [orcpub.dnd.e5.artist-profile :as ap]
            [orcpub.dnd.e5.portrait :as portrait]
            [orcpub.dnd.e5.views :as views]))

(defn- shell
  "The app's page chrome around a .pl-root, so the drawer's theme variables
   and credit face apply here too."
  [title & body]
  (let [theme @(subscribe [:theme])]
    [views/content-page
     title
     []
     (into [:div.pl-root.ap-root {:class theme}
            [:style portrait/drawer-styles]]
           body)]))

(defn- link-button [{:link/keys [label url icon color]}]
  [:a.ap-link {:href url :target "_blank" :rel "noopener"}
   (when icon
     [:span.lk-ico {:aria-hidden true
                    :style {:color color
                            :WebkitMaskImage (str "url(/image/social/" icon ".svg)")
                            :maskImage (str "url(/image/social/" icon ".svg)")}}])
   label])

(defn- names [artists]
  (let [n (count artists)]
    (for [[i a] (map-indexed vector artists)]
      ^{:key (:artist/id a)}
      [:<>
       (cond (zero? i) nil (= i (dec n)) " and " :else ", ")
       (:artist/name a)])))

(defn- example [artist-id i p]
  (let [others (ap/others-in p artist-id)]
    [:figure.ap-example
     [:div.ap-frame {:role "img" :aria-label (str "Example portrait " (inc i))}
      [portrait/composite p]]
     (when (seq others)
       [:figcaption "with pieces by " (names others)])]))

(defn- profile-card [{:keys [:artist/id :artist/name] :as artist}]
  (let [links (ap/listed-links artist)
        n (ap/piece-count artist)]
    [:article.ap-card
     [:header.ap-head
      [:div.lk-cap "Portrait artist"]
      [:div.lk-row
       [:span.lk-rule.l {:aria-hidden true}]
       [:h1.ap-name name]
       [:span.lk-rule.r {:aria-hidden true}]]
      [:p.ap-lede n " hand-drawn " (if (= 1 n) "piece" "pieces")
       " in the portrait maker."]
      (when (seq links)
        [:nav.ap-links {:aria-label (str "Find " name)}
         (for [l links] ^{:key (:link/url l)} [link-button l])])]
     [:section.ap-section
      [:h2.lk-cap "Portraits from these pieces"]
      [:div.ap-examples
       (map-indexed (fn [i p] ^{:key i} [example id i p])
                    (ap/example-portraits id))]]
     [:p.ap-foot
      "Every portrait made with these pieces credits " name
      ", on the character page, the share card and the PDF. "
      [:a {:href (ap/index-path)} "All portrait artists"]]]))

(defn artist-page
  "/artists/<slug>"
  [{:keys [slug]}]
  (if-let [artist (ap/artist-by-slug slug)]
    [shell "Portrait Artist" [profile-card artist]]
    [shell "Portrait Artist"
     [:article.ap-card
      [:p.ap-missing "There is no portrait artist at this address. "
       [:a {:href (ap/index-path)} "See all portrait artists"]]]]))

(defn artists-page
  "/artists: everyone with a profile, in registry order."
  [_]
  [shell "Portrait Artists"
   [:article.ap-card
    [:header.ap-head
     [:div.lk-cap "Art by"]
     [:p.ap-lede "The people who drew the pieces in the portrait maker."]]
    [:div.ap-index
     (for [{:keys [:artist/id :artist/name] :as a} (ap/all-profiles)
           :let [n (ap/piece-count a)]]
       ^{:key id}
       [:a.ap-index-card {:href (ap/profile-path a)}
        [:div.ap-frame [portrait/composite (first (ap/example-portraits id 1))]]
        [:span.lk-name name]
        [:span.ap-index-count n (if (= 1 n) " piece" " pieces")]])]]])
