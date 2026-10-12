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
    :subtitle "Homebrew you can manage, characters that recover and share, safer accounts, and sheets that print everything."
    :items
    [{:group "Your homebrew library"
      :icon "fa-folder-open"
      :headline "Move homebrew between your sources"
      :detail "Move or copy content from one source to another, turn a single item or a whole source off without deleting it, search inside a source, and read one health card that names anything needing attention."}
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
     {:group "Your homebrew library"
      :icon "fa-clone"
      :headline "Homebrew is safe in two tabs"
      :detail "Saving homebrew in one tab no longer erases homebrew you saved in another: the older tab's change is merged in, and a real clash is shown to you. When you rename an item, links to it in the same source follow, and you are asked about links in other sources."}

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
      :icon "fa-search"
      :headline "Find equipment by typing"
      :detail "The Equipment tab's long dropdowns filter as you type. Nothing is hidden behind a cap — scroll the whole list, or walk it with the arrow keys and press Enter to add."}


     {:group "Sharing"
      :icon "fa-share-alt"
      :headline "Share a character with its homebrew"
      :detail "A short view-only link opens the character with the custom content its sheet needs, magic items included, and the recipient can keep that content in their library. A party keeps a shared character's homebrew too."}
     {:group "Sharing"
      :icon "fa-link"
      :headline "Links you control"
      :detail "Make a new link or stop sharing at any time. A link nobody opens can expire, and the character page tells you when one has."}

     {:group "Accounts"
      :icon "fa-user-shield"
      :headline "New sign-in pages and stronger passwords"
      :detail "Signing up, signing in and recovering an account have new pages. Passwords are judged on length, a meter shows what to fix as you type, and passwords that turn up again and again in data breaches are turned away."}
     {:group "Accounts"
      :icon "fa-key"
      :headline "You stay in charge of your account"
      :detail "Sign out everywhere from your account page. You get an email when your password or email address changes, and changing your email needs your password."}

     {:group "Printing"
      :icon "fa-book-open"
      :headline "Print a spellbook"
      :detail "Add spellbook pages after your sheet: every spell you print, by class, with your save DC and attack on each page, as a full-text book, a one-line ledger or a prep sheet to tick off. Pick an emblem for each class."}
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
      :detail "Casting time, range, components and duration come out solid black on a home printer, with an optional logo for the card backs. Cards are in spell-list order: by level, then name."}

     {:group "Site"
      :icon "fa-mobile-alt"
      :headline "Fits your phone"
      :detail "On a phone the header, menus and ability buttons fit the screen, and a desktop window narrowed to phone width gets the same layout."}
     {:group "Site"
      :icon "fa-adjust"
      :headline "Easier-to-read buttons"
      :detail "Turn on Dark Button Text beside Light Theme for dark lettering on the yellow buttons."}]}])

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
