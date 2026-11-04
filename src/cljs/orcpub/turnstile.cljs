(ns orcpub.turnstile
  "Cloudflare Turnstile widget for the sign-up form (managed mode).
   Loaded only when the server put a site key on the page (window.__TURNSTILE_SITE_KEY__),
   and only when the form mounts, so no other page fetches anything from Cloudflare.
   Fails open on the client: if the script never loads, nothing is blocked; the server decides."
  (:require [reagent.core :as r]
            [re-frame.core :refer [dispatch]]))

(def ^:private script-id "cf-turnstile-api")
(def ^:private script-src "https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit")

(defn site-key
  "The public site key, or nil when Turnstile is not configured."
  []
  (let [k (aget js/window "__TURNSTILE_SITE_KEY__")]
    (when (and (string? k) (seq k)) k)))

(defonce ^:private widget-id (atom nil))

(defn- api [] (aget js/window "turnstile"))

(defn- call-api
  "Calls window.turnstile[method] by string name, so advanced compilation cannot rename it."
  [method & args]
  (let [t (api)]
    (.apply (aget t method) t (to-array args))))

(defn- with-api
  "Calls `f` once the Turnstile API is available, loading its script once. Never throws."
  [f]
  (if (api)
    (f)
    (if-let [existing (.getElementById js/document script-id)]
      (.addEventListener existing "load" f)
      (let [el (.createElement js/document "script")]
        (set! (.-id el) script-id)
        (set! (.-src el) script-src)
        (set! (.-async el) true)
        (set! (.-defer el) true)
        (.addEventListener el "load" f)
        ;; Script failed to load: leave the form as it is; the server fails open.
        (.addEventListener el "error" (fn [_] (dispatch [:turnstile-token nil])))
        (.appendChild (.-head js/document) el)))))

(defn reset-widget!
  "Tokens are single use: asks the widget for a fresh one. No-op without a widget."
  []
  (dispatch [:turnstile-token nil])
  (when-let [id @widget-id]
    (try (call-api "reset" id) (catch :default _ nil))))

(defn- render! [el key]
  (when (and (api) (nil? @widget-id) (.-isConnected el))
    (reset! widget-id
            (try
              (call-api "render" el
                       #js {:sitekey key
                            :callback (fn [token] (dispatch [:turnstile-token token]))
                            :expired-callback (fn [] (dispatch [:turnstile-token nil]))
                            :error-callback (fn [] (dispatch [:turnstile-token nil]))})
              (catch :default _ nil)))))

(defn widget
  "The challenge container. Renders nothing when no site key is configured."
  []
  (when-let [key (site-key)]
    (let [el (atom nil)]
      (r/create-class
       {:display-name "turnstile-widget"
        :component-did-mount
        (fn [_] (with-api #(when @el (render! @el key))))
        :component-will-unmount
        (fn [_]
          (when-let [id @widget-id]
            (try (call-api "remove" id) (catch :default _ nil)))
          (reset! widget-id nil)
          (dispatch [:turnstile-token nil]))
        :reagent-render
        (fn [] [:div.m-t-10 {:ref #(reset! el %)}])}))))
