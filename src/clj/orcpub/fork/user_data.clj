(ns orcpub.fork.user-data
  "User data enrichment hooks for the DMV production fork.

   Routes.clj calls these hooks at two points:
   1. user-body       — enrich-response adds tier fields to the API response
   2. registration    — registration-defaults sets initial values for new users

   Datomic queries already pull [*], so all attributes are available on the
   user entity passed to enrich-response. No extra pull configuration needed.

   The public repo stub passes data through unchanged. DMV overrides
   add patron tier fields so the client can gate features via :user-tier.

   To add a new user-data field:
   1. Add the Datomic attribute to schema.clj
   2. Add it to enrich-response (maps DB attr → API response key)
   3. Add it to registration-defaults (initial value for new users)")

;; ─── Response Enrichment ───────────────────────────────────────
;; Called from user-body in routes.clj. Maps Datomic user attributes
;; into the API response sent to the client.

(defn enrich-response
  "Add tier fields to the user API response map.
   `data` is the base response, `user` is the full Datomic entity."
  [data user]
  (assoc data
         :patron (:orcpub.user/patron user)
         :patron-tier (:orcpub.user/patron-tier user)))

;; ─── Registration Defaults ─────────────────────────────────────
;; Merged into the new-user entity map during registration.

(defn registration-defaults
  "Default Datomic attributes for newly registered users."
  []
  {:orcpub.user/patron false
   :orcpub.user/patron-tier " "})
