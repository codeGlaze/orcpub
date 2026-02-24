(ns orcpub.core
  (:require [orcpub.character-builder :as ch]
            [orcpub.dnd.e5.subs]
            [orcpub.dnd.e5.equipment-subs]
            [orcpub.dnd.e5.events :as events]
            [orcpub.dnd.e5.autosave-fx :as autosave-fx]
            [orcpub.dnd.e5.views :as views]
            [orcpub.dnd.e5.views-2 :as views-2]
            [orcpub.dnd.e5.views.conflict-resolution :as conflict-views]
            [orcpub.dnd.e5.views.header :as views-header]
            [orcpub.dnd.e5.views.auth :as views-auth]
            [orcpub.dnd.e5.views.builders :as views-builders]
            [orcpub.dnd.e5.views.content :as views-content]
            [orcpub.dnd.e5.views.combat :as views-combat]
            [orcpub.dnd.e5.views.lists :as views-lists]
            [orcpub.route-map :as routes]
            [cljs-http.client :as http]
            [clojure.string :as s]
            [re-frame.core :refer [dispatch dispatch-sync subscribe]]
            [reagent.core :as r]
            [reagent.ratom :as ratom]
            [reagent.dom.client :as rdc]
            [goog.events])
  (:import
   [goog.history Html5History EventType]))

(enable-console-print!)

(when (and js/window.location
           (not (or (s/starts-with? js/window.location.href "https")
                    (s/starts-with? js/window.location.href "http://localhost"))))
  (set! js/window.location.protocol "https"))

(dispatch-sync [:initialize-db])

;; Init template cache after all subscription handlers are registered.
;; Must be called here (not self-initializing) so equipment-subs has loaded.
(autosave-fx/init-template-cache!)

;; DEBUG: trace subscribe calls outside reactive context.
;; Prints a full stack trace so we can find the call site.
;; REMOVE after fixing the warnings.
(let [original-subscribe re-frame.core/subscribe]
  (set! re-frame.core/subscribe
        (fn [query-v & args]
          (when-not (ratom/reactive?)
            (js/console.warn "[TRACE] subscribe outside reactive context:"
                             (pr-str query-v))
            (js/console.trace))
          (apply original-subscribe query-v args))))

(def pages
  {;; Splash / default
   nil views-2/splash-page
   routes/default-route views-2/splash-page

   ;; Character builder (character_builder.cljs)
   routes/dnd-e5-char-builder-route ch/character-builder

   ;; Lists module — entity browsers + parties
   routes/dnd-e5-orcacle-page-route views-lists/orcacle-page
   routes/dnd-e5-char-list-page-route views-lists/character-list
   routes/dnd-e5-monster-list-page-route views-lists/monster-list
   routes/dnd-e5-spell-list-page-route views-lists/spell-list
   routes/dnd-e5-item-list-page-route views-lists/item-list
   routes/dnd-e5-char-parties-page-route views-lists/parties

   ;; Builders module — homebrew content editors
   routes/dnd-e5-newb-char-builder-route views-builders/newb-character-builder-page
   routes/dnd-e5-spell-builder-page-route views-builders/spell-builder-page
   routes/dnd-e5-monster-builder-page-route views-builders/monster-builder-page
   routes/dnd-e5-background-builder-page-route views-builders/background-builder-page
   routes/dnd-e5-race-builder-page-route views-builders/race-builder-page
   routes/dnd-e5-subrace-builder-page-route views-builders/subrace-builder-page
   routes/dnd-e5-subclass-builder-page-route views-builders/subclass-builder-page
   routes/dnd-e5-class-builder-page-route views-builders/class-builder-page
   routes/dnd-e5-feat-builder-page-route views-builders/feat-builder-page
   routes/dnd-e5-language-builder-page-route views-builders/language-builder-page
   routes/dnd-e5-invocation-builder-page-route views-builders/invocation-builder-page
   routes/dnd-e5-boon-builder-page-route views-builders/boon-builder-page
   routes/dnd-e5-selection-builder-page-route views-builders/selection-builder-page
   routes/dnd-e5-item-builder-page-route views-builders/item-builder-page

   ;; Combat module — encounter builder + tracker
   routes/dnd-e5-encounter-builder-page-route views-combat/encounter-builder-page
   routes/dnd-e5-combat-tracker-page-route views-combat/combat-tracker-page

   ;; Content module — user content + account
   routes/dnd-e5-my-content-route views-content/my-content-page
   routes/my-account-page-route views-content/my-account-page

   ;; Auth module — registration, login, password reset
   routes/register-page-route views-auth/register-form
   routes/verify-failed-route views-auth/verify-failed
   routes/verify-success-route views-auth/verify-success
   routes/verify-sent-route views-auth/verify-sent
   routes/login-page-route views-auth/login-page
   routes/send-password-reset-page-route views-auth/send-password-reset-page
   routes/password-reset-sent-route views-auth/password-reset-sent
   routes/reset-password-page-route views-auth/password-reset-page
   routes/password-reset-success-route views-auth/password-reset-success
   routes/password-reset-expired-route views-auth/password-reset-expired-page
   routes/password-reset-used-route views-auth/password-reset-used-page

   ;; Detail pages — stay in views/ (not yet extracted)
   routes/dnd-e5-char-page-route views/character-page
   routes/dnd-e5-monster-page-route views/monster-page
   routes/dnd-e5-spell-page-route views/spell-page
   routes/dnd-e5-item-page-route views/item-page})

(defn handle-url-change [_]
  (let [route (when js/window.location
                (routes/match-route js/window.location.pathname))
        config {:skip-path? true}]
    (dispatch [:route route (if (events/login-routes (:handler route))
                              (merge
                               config
                               {:no-return? true})
                              config)])))

(defn make-history []
  (doto (Html5History.)
    (.setPathPrefix (str js/window.location.protocol
                         "//"
                         js/window.location.host))
    (.setUseFragment false)))

(defonce history (doto (make-history)
                   (goog.events/listen EventType.NAVIGATE
                                       handle-url-change)
                   (.setEnabled true)))

(defn query-map [query-str]
  (into
   {}
   (map
    (fn [[_ _ k v]]
      [k v])
    (re-seq #"((\w+)=(\w+))+" query-str))))

(defn main-view []
  (let [{:keys [handler route-params] :as route} @(subscribe [:route])
        view (pages (or handler route))
        query-string js/window.location.search
        query-map (query-map query-string)]
    [:div
     [view (assoc route-params :query query-map)]
     [conflict-views/import-log-overlay]]))

;; Verify auth token on startup (replaces @(subscribe [:user false]) side-effect)
(dispatch-sync [:verify-user-session])

;; React 18 createRoot API (Reagent 2.0)
(defonce root (rdc/create-root (js/document.getElementById "app")))

(rdc/render root
            (if (let [doc-style js/document.documentElement.style]
                  (and js/window.localStorage
                       (or (aget doc-style "flexWrap")
                           (aget doc-style "WebkitFlexWrap")
                           (aget doc-style "msFlexWrap"))))
              [main-view]
              [:div
               [views-header/app-header]
               [:div.f-s-24.main-text-color.sans
                {:style {:padding "200px"}}
                "Sorry, we are unable to support your browser since it does not support important HTML5 features. Please try a modern browser such as " [:a {:href "https://www.google.com/chrome/browser/desktop/index.html"} "Google Chrome"] " or " [:a {:href "https://www.mozilla.org/en-US/firefox/products/?v=a"} "Mozilla Firefox"]]]))

