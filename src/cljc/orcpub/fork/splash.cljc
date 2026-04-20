(ns orcpub.fork.splash
  "Fork-specific splash page and navigation configuration.
   Public/community edition: minimal splash, no generators.")

;; ─── Splash Page ────────────────────────────────────────────────────

(def logo-width-class
  "CSS class controlling splash page logo width."
  "w-30-p")

(def edition-label
  "Text shown below logo on splash page. nil = hidden."
  "Community edition")

;; ─── Legal Footer ───────────────────────────────────────────────────

(def legal-footer-links
  "Links rendered in the legal footer (splash + content pages)."
  [{:label "Terms of Use"    :href "/terms-of-use"}
   {:label "Privacy Policy"  :href "/privacy-policy"}])

;; ─── Generators ─────────────────────────────────────────────────────

(def generator-buttons
  "Splash page generator buttons. Empty = section hidden."
  [])

(def header-generator-entries
  "Header nav generator dropdown entries. Empty = tab hidden."
  [])
