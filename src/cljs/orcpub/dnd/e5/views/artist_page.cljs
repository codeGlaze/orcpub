(ns orcpub.dnd.e5.views.artist-page
  "Views for the public artist profiles, /artists and /artists/<slug>. What they show is
   decided in artist-profile (cljc). The page wears .pl-root so the drawer's stylesheet,
   credit face and --lk-* theme variables apply; what the drawer lacks is .ap-*, in
   styles/core.clj `artist-pages`.
   Plan and look: agents/develop docs/archive/design/artist-profiles/PLAN.md."
  (:require [cljs.core.async :refer [<!]]
            [clojure.string :as str]
            [re-frame.core :refer [dispatch reg-event-fx reg-sub-raw subscribe]]
            [reagent.ratom :as ra]
            [orcpub.artist-links :as al]
            [orcpub.dnd.e5.artist-profile :as ap]
            [orcpub.dnd.e5.event-utils :as event-utils]
            [orcpub.dnd.e5.http-safe :as http]
            [orcpub.dnd.e5.views :as views])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;; Whether an artist is streaming, asked of the server (GET /api/twitch/live) the first time a
;; Twitch button for that login is drawn, once per login per page load.
(reg-event-fx
 :twitch/load-live
 (fn [{:keys [db]} [_ login]]
   (if (contains? (:twitch/requested db) login)
     {}
     (do (go (let [{:keys [status body]}
                   (<! (http/get (event-utils/backend-url
                                  (str "/api/twitch/live?login=" (js/encodeURIComponent login)))))]
               (dispatch [:twitch/live-loaded login (and (= 200 status) (map? body) (true? (:live body)))])))
         {:db (update db :twitch/requested (fnil conj #{}) login)}))))

(reg-event-fx
 :twitch/live-loaded
 (fn [{:keys [db]} [_ login live?]]
   {:db (assoc-in db [:twitch/live login] live?)}))

(reg-sub-raw
 :twitch/live?
 ;; Starts the one fetch for `login` when first subscribed; false until the answer arrives.
 (fn [app-db [_ login]]
   (dispatch [:twitch/load-live login])
   (ra/make-reaction #(boolean (get-in @app-db [:twitch/live login])))))

(defn- twitch-login
  "The lower-case Twitch login a link's address names, or nil when it is not a Twitch profile."
  [url]
  (when (= "twitch" (al/icon-for-url url))
    (some-> (al/handle-for-url url) (subs 1) str/lower-case)))

(defn- live-badge
  "The red dot and the word Live for a Twitch `login`, once the server says they are streaming;
   nothing while it is not live or unknown."
  [login]
  (when @(subscribe [:twitch/live? login])
    [:span.ap-live
     [:span.ap-live-dot {:role "img" :aria-label "Live now on Twitch"}]
     "Live"]))

(defn- shell
  "The app's page chrome around a .pl-root, so the drawer's theme variables
   and credit face apply here too."
  [title & body]
  (let [theme @(subscribe [:theme])]
    [views/content-page
     title
     []
     (into [:div.pl-root.ap-root {:class theme}] body)]))

(defn- link-button
  "One of the artist's listed links as a button: its mark, if it has one, its label, and the
   account handle for a known service's profile (al/handle-for-url) or else the domain it really
   goes to. Opens in a new tab; nothing for a link this site does not draw."
  [{:link/keys [label url icon color]}]
  (when-let [attrs (ap/external-link url)]
    [:a.ap-link attrs
     (when icon
       ;; this link's own mark and colour, as the variables the credit's .lk-ico reads
       [:span.lk-ico {:aria-hidden true
                      :style (cond-> {"--lk-icon" (str "url(/image/social/" icon ".svg)")}
                               color (assoc "--lk-mark-color" color))}])
     label
     [:span.ap-link-host (or (al/handle-for-url url) (ap/link-host url))]
     (when-let [login (twitch-login url)]
       [live-badge login])]))

(defn- names
  "The names of `artists` as prose: A / A and B / A, B and C."
  [artists]
  (let [n (count artists)]
    (for [[i a] (map-indexed vector artists)]
      ^{:key (:artist/id a)}
      [:<>
       (cond (zero? i) nil (= i (dec n)) " and " :else ", ")
       (:artist/name a)])))

(defn- example-img
  "Example `n` of `artist` (from 1), the small watermarked image the server renders once."
  [artist n alt]
  [:img.ap-example-img {:src (ap/example-path artist n) :alt alt :width 240 :height 300}])

(defn- example
  "Example `i` (from 0) of `artist`: its server-made image and, when portrait `p` borrows
   pieces from other artists, a caption naming them."
  [artist i p]
  (let [others (ap/others-in p (:artist/id artist))]
    [:figure.ap-example
     [:div.ap-frame [example-img artist (inc i) (str "Example portrait " (inc i))]]
     (when (seq others)
       [:figcaption "with pieces by " (names others)])]))

(defn- profile-card
  "The profile of `artist`: the name, then the piece count, listed links and examples where the
   artist keeps them on (ap/shows?), and a link to all artists once there is more than one."
  [{:keys [:artist/id :artist/name] :as artist}]
  (let [links (ap/listed-links artist)
        n (ap/piece-count artist)]
    [:article.ap-card
     [:header.ap-head
      [:div.lk-cap "Portrait artist"]
      [:div.lk-row
       [:span.lk-rule.l {:aria-hidden true}]
       [:h1.ap-name name]
       [:span.lk-rule.r {:aria-hidden true}]]
      (when (ap/shows? artist :count)
        [:p.ap-lede n " hand-drawn " (if (= 1 n) "piece" "pieces")
         " in the portrait maker."])
      (when (seq links)
        [:nav.ap-links {:aria-label (str "Find " name)}
         (for [l links] ^{:key (:link/url l)} [link-button l])])]
     (when (ap/shows? artist :examples)
       [:section.ap-section
        [:h2.lk-cap (str "Examples made with " name "'s art")]
        [:div.ap-examples
         (map-indexed (fn [i p] ^{:key i} [example artist i p])
                      (ap/example-portraits id))]])
     (when (< 1 (count (ap/all-profiles)))
       [:p.ap-foot
        [:a {:href (ap/index-path)} "All portrait artists"]])]))

(defn artist-page
  "/artists/<slug>. Redraws when the artist credits arrive, which can rename the artist,
   change their links or turn the page off."
  [{:keys [slug]}]
  (let [_ @(subscribe [:portrait/artist-overrides])]
    (if-let [artist (ap/artist-by-slug slug)]
      [shell "Portrait Artist" [profile-card artist]]
      [shell "Portrait Artist"
       [:article.ap-card
        [:p.ap-missing "There is no portrait artist at this address. "
         [:a {:href (ap/index-path)} "See all portrait artists"]]]])))

(defn artists-page
  "/artists: everyone with a profile, in registry order. Redraws when the artist credits
   arrive."
  [_]
  (let [_ @(subscribe [:portrait/artist-overrides])]
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
          (when (ap/shows? a :examples)
            [:div.ap-frame [example-img a 1 (str "A portrait from " name "'s pieces")]])
          [:span.lk-name name]
          (when (ap/shows? a :count)
            [:span.ap-index-count n (if (= 1 n) " piece" " pieces")])])]]]))
