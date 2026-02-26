(ns orcpub.user-data
  "User data enrichment hooks. Pass-through by default.
   Fork overrides: replace this file to add custom fields to the
   user API response and registration defaults.

   Routes.clj calls these hooks at two points:
   1. user-body       — enrich-response can add fields to the API response
   2. registration    — registration-defaults can set initial values

   Datomic queries pull [*], so all attributes are available on the
   user entity passed to enrich-response.")

;; ─── Response Enrichment ───────────────────────────────────────
;; No-op by default. Forks override to map additional Datomic
;; attributes into the API response sent to the client.

(defn enrich-response
  "Add fork-specific fields to the user API response map.
   `data` is the base response, `user` is the full Datomic entity."
  [data _user]
  data)

;; ─── Registration Defaults ─────────────────────────────────────
;; No-op by default. Forks override to set initial attribute values
;; for newly registered users.

(defn registration-defaults
  "Default Datomic attributes for newly registered users."
  []
  {})
