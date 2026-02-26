(ns orcpub.user-tier
  "User tier abstraction for feature gating.
   DMV: derives tier from Patreon patron data in user-data.
   Public repo override: always returns :free.

   Registers :user-tier (generic gate), plus :patron and :patron-tier
   (backward compat for Matomo integration and data layer)."
  (:require [re-frame.core :refer [reg-sub]]))

;; ─── Tier Subscription ───────────────────────────────────────────
;; UI code gates on :user-tier — returns :free or a tier keyword.
;; This keeps patron-specific vocabulary out of shared view code.

(reg-sub
 :user-tier
 (fn [db _]
   (let [patron? (-> db :user-data :user-data :patron)]
     (if patron?
       (or (some-> db :user-data :user-data :patron-tier keyword)
           :patron)
       :free))))

;; ─── Data Layer Subscriptions ────────────────────────────────────
;; Kept for backward compat with Matomo custom variables and
;; any direct data-layer access. UI code should use :user-tier.

(reg-sub
 :patron
 (fn [db _]
   (-> db :user-data :user-data :patron)))

(reg-sub
 :patron-tier
 (fn [db _]
   (-> db :user-data :user-data :patron-tier)))
