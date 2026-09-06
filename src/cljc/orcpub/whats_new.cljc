(ns orcpub.whats-new
  "Release highlights for the What's New panel.

   `releases` is newest-first. `:id` is the stamp the browser stores once someone
   has seen the panel, so a new `:id` is what makes the panel open again — one new
   entry per release, and never reuse an id. `:icon` values are Font Awesome 5
   solid names — the set index.clj serves — so a v4 name renders as a blank column.
   Items carrying the same `:group` are rendered under one heading, in the order
   they appear here.")

(def releases
  [{:id "summer-patch-2026"
    :title "Summer Patch"
    :subtitle "Homebrew you can manage, characters that recover, sheets that print everything."
    :items
    [{:group "Your homebrew library"
      :icon "fa-folder-open"
      :headline "My Content is a real library"
      :detail "Move or copy content between sources, turn it off at four levels, search inside a source, and read one health card that names anything needing attention."}
     {:group "Your homebrew library"
      :icon "fa-medkit"
      :headline "Broken homebrew is repaired, not dropped"
      :detail "One bad entry no longer sidelines the source it came in with. The good items keep working, and anything set aside gets a repair panel with Fix & Restore."}
     {:group "Your homebrew library"
      :icon "fa-exchange-alt"
      :headline "Imports settle conflicts up front"
      :detail "Safe defaults clear duplicate keys in one click, Rename all finishes in a single pass, and Skip this one actually skips. Old wizard-possessive spell names (Leomund's, Tasha's) match their current ones."}
     {:group "Your homebrew library"
      :icon "fa-shield-alt"
      :headline "Starting equipment for homebrew classes"
      :detail "The full SRD form — fixed gear, choice groups, bundles, and nested weapon choices — and you can start from an SRD class and change only what you want."}

     {:group "Characters"
      :icon "fa-bolt"
      :headline "The builder doesn't freeze on a big library"
      :detail "Switching between Race and Class with a large homebrew library cost about a second of locked-up tab. It is roughly a tenth of that now, and an edit rebuilds your character once instead of twice."}
     {:group "Characters"
      :icon "fa-heartbeat"
      :headline "Characters that blanked the page come back"
      :detail "An unreadable character now opens a recovery panel instead of an empty screen, repairs what it can on load, and can be reported in one click."}
     {:group "Characters"
      :icon "fa-image"
      :headline "Portraits from far more sites"
      :detail "Paste a picture's address and the sheet takes it, including hosts that used to refuse. When an address can't work, the field says why and what to try instead."}
     {:group "Characters"
      :icon "fa-share-alt"
      :headline "Share a character with its homebrew"
      :detail "A view-only link carries the custom content the sheet needs, magic items included, and the recipient can keep it in their library."}

     {:group "Printing"
      :icon "fa-list-ol"
      :headline "Every spell you know prints"
      :detail "Three sheet styles numbered their spell rows with a gap in the middle, so spells such as Glyph of Warding, Continual Flame and Darkness silently never printed, and prepared ticks could land on the wrong row. Every style is renumbered, and the empty hit dice and second-page name boxes are filled in."}
     {:group "Printing"
      :icon "fa-columns"
      :headline "Multiclass casters fit on fewer pages"
      :detail "Spells can pack one class to a column instead of one page each, with a Warlock's Pact Magic kept as its own pool rather than added into the shared slots. Two of the four styles could not export a two-class caster at all; now they can."}
     {:group "Printing"
      :icon "fa-scroll"
      :headline "Spell rows say more, and items get cards"
      :detail "Rows mark concentration, casting time, reactions and costly materials, and your magic items print as cards alongside the spell cards."}
     {:group "Printing"
      :icon "fa-print"
      :headline "Cards print in black and white"
      :detail "Casting time, range, components and duration come out solid black on a home printer, with an optional logo for the card backs."}]}])

(def current-release
  (first releases))

(def current-release-id
  (:id current-release))

(defn unseen?
  "Is there a release the browser has not shown yet? True for a blank stamp, so a
   first visit sees the current release once."
  [seen-id]
  (not= seen-id current-release-id))

(defn grouped-items
  "The release's items as [group items] pairs, in source order. A group of nil
   means the items carry no heading."
  [release]
  (->> (:items release)
       (partition-by :group)
       (map (fn [items] [(:group (first items)) items]))))
