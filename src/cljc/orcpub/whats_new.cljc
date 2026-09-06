(ns orcpub.whats-new
  "Release highlights for the What's New panel.

   `releases` is newest-first. `:id` is the stamp the browser stores once someone
   has seen the panel, so a new `:id` is what makes the panel open again — one new
   entry per release, and never reuse an id. `:icon` values are Font Awesome 5
   solid names — the set index.clj serves — so a v4 name renders as a blank column.")

(def releases
  [{:id "summer-patch-2026"
    :title "Summer Patch"
    :subtitle "Homebrew you can manage, characters that recover, sheets that print."
    :items
    [{:icon "fa-folder-open"
      :headline "My Content is a real library"
      :detail "Move or copy content between sources, turn it off at four levels, search inside a source, and read one health card that names anything needing attention."}
     {:icon "fa-medkit"
      :headline "Broken homebrew is repaired, not dropped"
      :detail "One bad entry no longer sidelines the source it came in with. The good items keep working, and anything set aside gets a repair panel with Fix & Restore."}
     {:icon "fa-heartbeat"
      :headline "Characters that blanked the page come back"
      :detail "An unreadable character now opens a recovery panel instead of an empty screen, repairs what it can on load, and can be reported in one click."}
     {:icon "fa-exchange-alt"
      :headline "Imports settle conflicts up front"
      :detail "Safe defaults clear duplicate keys in one click, Rename all finishes in a single pass, and Skip this one actually skips."}
     {:icon "fa-image"
      :headline "Portraits from far more sites"
      :detail "Paste a picture's address and the sheet takes it, including hosts that used to refuse. When an address can't work, the field says why and what to try instead."}
     {:icon "fa-print"
      :headline "Spell cards print in black and white"
      :detail "Casting time, range, components and duration come out solid black on a home printer, with an optional logo for the card backs."}
     {:icon "fa-shield-alt"
      :headline "Starting equipment for homebrew classes"
      :detail "The full SRD form — fixed gear, choice groups, bundles, and nested weapon choices."}
     {:icon "fa-share-alt"
      :headline "Share a character with its homebrew"
      :detail "A view-only link carries the custom content the sheet needs, magic items included, and the recipient can keep it in their library."}]}])

(def current-release
  (first releases))

(def current-release-id
  (:id current-release))

(defn unseen?
  "Is there a release the browser has not shown yet? True for a blank stamp, so a
   first visit sees the current release once."
  [seen-id]
  (not= seen-id current-release-id))
