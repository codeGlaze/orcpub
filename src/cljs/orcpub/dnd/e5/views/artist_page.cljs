(ns orcpub.dnd.e5.views.artist-page
  "Views for the public artist profiles, /artists and /artists/<slug>. What they show is
   decided in artist-profile (cljc). The page wears .pl-root so the drawer's stylesheet,
   credit face and --lk-* theme variables apply; .ap-* styles only what the drawer lacks.
   Plan and look: docs/design/artist-profiles/PLAN.md."
  (:require [re-frame.core :refer [subscribe]]
            [orcpub.dnd.e5.artist-profile :as ap]
            [orcpub.dnd.e5.portrait :as portrait]
            [orcpub.dnd.e5.views :as views]))

(def styles "
.ap-root { font-family: 'Open Sans', system-ui, sans-serif; color: #ebeef4; padding: 24px 12px 40px; }
.ap-root.light-theme { color: #363636; }
.ap-card {
  max-width: 640px; margin: 0 auto; padding: 28px 28px 24px;
  background: #131924; border: 1px solid rgba(240,161,0,0.16); border-radius: 12px;
  box-shadow: 0 20px 50px -30px rgba(0,0,0,0.7);
  display: flex; flex-direction: column; gap: 30px;
}
.ap-root.light-theme .ap-card {
  background: #fff; border-color: rgba(0,0,0,0.10);
  box-shadow: 0 20px 50px -34px rgba(0,0,0,0.35);
}
.ap-head { display: flex; flex-direction: column; gap: 12px; text-align: center; }
.ap-name {
  margin: 0; font: italic 400 40px/1.1 'Vollkorn', Georgia, serif;
  color: var(--lk-name); white-space: normal; text-wrap: balance;
}
.ap-head .lk-row { gap: 16px; }
.ap-lede { margin: 0; font-size: 13px; line-height: 1.5; color: var(--lk-dim); }
.ap-links { display: flex; flex-wrap: wrap; justify-content: center; gap: 8px; margin-top: 4px; }
.ap-link {
  display: inline-flex; align-items: center; gap: 8px;
  padding: 9px 16px; border-radius: 5px;
  border: 1px solid rgba(255,255,255,0.10); background: rgba(255,255,255,0.02);
  color: #ebeef4; text-decoration: none;
  font: 600 12px/1 'Open Sans', system-ui, sans-serif; letter-spacing: 0.04em;
  transition: border-color 160ms ease, background-color 160ms ease;
}
.ap-link:hover, .ap-link:focus-visible { border-color: #f0a100; background: rgba(240,161,0,0.06); outline: none; }
.ap-link .lk-ico { width: 14px; height: 14px; }
.ap-root.light-theme .ap-link { border-color: rgba(0,0,0,0.14); background: #fff; color: #363636; }
.ap-root.light-theme .ap-link:hover,
.ap-root.light-theme .ap-link:focus-visible { border-color: #33658A; background: rgba(51,101,138,0.06); }
.ap-section { display: flex; flex-direction: column; gap: 14px; }
.ap-section > .lk-cap { margin: 0; }
.ap-examples { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; }
.ap-example { margin: 0; display: flex; flex-direction: column; gap: 7px; }
/* Dark in both themes, like the drawer's frame: it holds character colours,
   and a light frame would swallow pale skin and blonde hair. */
.ap-frame {
  width: 100%; aspect-ratio: 4 / 5; box-sizing: border-box;
  position: relative; overflow: hidden; border-radius: 10px;
  background: radial-gradient(circle at 50% 35%, #202939, #131924 60%, #0f141c);
  border: 1px solid rgba(240,161,0,0.16);
}
.ap-root.light-theme .ap-frame { border-color: rgba(0,0,0,0.12); }
.ap-example figcaption {
  font: italic 12px/1.4 'Vollkorn', Georgia, serif; color: var(--lk-dim); text-align: center;
}
.ap-groups { display: flex; flex-direction: column; gap: 14px; }
.ap-group { display: grid; grid-template-columns: 96px 1fr; gap: 12px; align-items: start; }
.ap-group-name { font: 600 12px/1.3 'Open Sans', system-ui, sans-serif; padding-top: 6px; }
.ap-group-count { display: block; font-weight: 400; font-size: 11px; color: var(--lk-dim); }
.ap-pieces { display: grid; grid-template-columns: repeat(auto-fill, minmax(52px, 1fr)); gap: 6px; }
.ap-piece {
  aspect-ratio: 1; border-radius: 6px; overflow: hidden;
  background: #131924; border: 1px solid rgba(255,255,255,0.05);
  display: grid; place-items: center;
}
.ap-root.light-theme .ap-piece { border-color: rgba(0,0,0,0.10); }
.ap-root:not(.light-theme) .ap-card .ap-piece { background: #0e131a; }
.ap-piece img { width: 100%; height: 100%; object-fit: contain; }
.ap-foot {
  margin: 0; padding-top: 16px; border-top: 1px solid rgba(255,255,255,0.06);
  font-size: 12px; line-height: 1.55; color: var(--lk-dim); text-align: center;
}
.ap-root.light-theme .ap-foot { border-top-color: rgba(0,0,0,0.08); }
.ap-foot a, .ap-missing a { color: inherit; text-decoration: underline dotted var(--lk-under); text-underline-offset: 3px; }
.ap-foot a:hover, .ap-missing a:hover { color: var(--lk-glow-text); }
.ap-missing { text-align: center; font-size: 13px; color: var(--lk-dim); }
.ap-index { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 16px; }
.ap-index-card { display: flex; flex-direction: column; gap: 8px; text-decoration: none; text-align: center; }
.ap-index-card .lk-name { font-size: 18px; white-space: normal; }
.ap-index-card:hover .lk-name, .ap-index-card:focus-visible .lk-name {
  color: var(--lk-glow-text); text-shadow: var(--lk-glow);
}
.ap-index-card:focus-visible { outline: none; }
.ap-index-count { font-size: 11px; color: var(--lk-dim); }
@media (max-width: 560px) {
  .ap-card { padding: 22px 16px 18px; gap: 26px; }
  .ap-name { font-size: 32px; }
  .ap-examples { gap: 8px; }
  .ap-group { grid-template-columns: 1fr; gap: 6px; }
  .ap-group-name { padding-top: 0; }
  .ap-group-count { display: inline; padding-left: 6px; }
}
")

(defn- shell
  "The app's page chrome around a .pl-root, so the drawer's theme variables
   and credit face apply here too."
  [title & body]
  (let [theme @(subscribe [:theme])]
    [views/content-page
     title
     []
     (into [:div.pl-root.ap-root {:class theme}
            [:style (str portrait/drawer-styles styles)]]
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

(defn- pieces [artist]
  [:div.ap-groups
   (for [{:keys [layer label] ps :pieces} (ap/pieces-by-layer artist)]
     ^{:key layer}
     [:div.ap-group
      [:div.ap-group-name label
       [:span.ap-group-count (count ps) (if (= 1 (count ps)) " piece" " pieces")]]
      [:div.ap-pieces
       (for [{:asset/keys [id url] piece-label :asset/label} ps]
         ^{:key id}
         [:div.ap-piece {:title piece-label}
          [:img {:src url :alt (or piece-label "") :loading "lazy"}]])]])])

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
     [:section.ap-section
      [:h2.lk-cap "Pieces"]
      [pieces artist]]
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
