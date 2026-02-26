(ns orcpub.user-tier
  "User tier abstraction for feature gating.
   No tier system on the public repo — all users are :free.
   Fork overrides: replace this file with real tier logic.

   UI code gates on :user-tier subscription. This keeps fork-specific
   vocabulary out of shared view code."
  (:require [re-frame.core :refer [reg-sub]]))

;; ─── Tier Subscription ───────────────────────────────────────────
;; Returns :free for all users. Forks override this file to derive
;; tier from their own user data model.

(reg-sub
 :user-tier
 (fn [_ _] :free))
