(ns orcpub.whats-new
  "Release highlights for the What's New panel, and a deployment's patron credits. `releases`
   is newest-first; `:id` is the stamp the browser keeps once the panel is seen, so each release
   gets a new id, never a reused one. `:icon` takes Font Awesome 5 solid names (the set index.clj
   serves): a v4 name renders as a blank column. Items sharing a `:group` render under one
   heading, in the order given. `:detail` and `:link` ({:href :label}, shown after the detail) are optional."
  (:require [clojure.string :as str]))

(def releases
  [{:id "long-rest-update-2026-r2"
    :title "Long Rest Update"
    :items
    [
     {:group "New"
      :icon "fa-user-circle"
      :headline "Character portraits by Fusspot"
      :detail "Build a portrait from Fusspot's layered art, in your own colours."
      :link {:href "/artists/fusspot" :label "See Fusspot's page"}}
     {:group "New"
      :icon "fa-arrow-up"
      :headline "Floating ability score increases for homebrew"
      :detail "Homebrew races, subraces, backgrounds and feats can let the player choose where the increase goes: +2 and +1, +1 to any ability, or a fixed bonus. They can grant saving throw proficiencies too."}
     {:group "New"
      :icon "fa-shield-alt"
      :headline "Starting equipment for homebrew classes"
      :detail "Fixed gear, choices and bundles, like the SRD classes. Start from an SRD class and change only what you need."}
     {:group "New"
      :icon "fa-book"
      :headline "Rules tab"
      :detail "The SRD's rules and conditions, by section. Hover or tap a link to preview a spell, condition or rule."}
     {:group "New"
      :icon "fa-share-alt"
      :headline "Share characters with their homebrew"
      :detail "Send a link and others see your character exactly as you do, custom content and all, and can add that homebrew to their own library. Make a new link or stop sharing whenever you like."}
     {:group "New"
      :icon "fa-book-open"
      :headline "Spellbook printing"
      :detail "Spellbook pages after your sheet, as full text, a one-line ledger or a prep sheet."}
     {:group "New"
      :icon "fa-scroll"
      :headline "Magic item cards"
      :detail "Your magic items print as cards alongside your spell cards."}
     {:group "New"
      :icon "fa-bolt"
      :headline "Charges and rests"
      :detail "Charged items track their charges, and a Rest box handles short and long rests and recharges."}
     {:group "New"
      :icon "fa-folder-open"
      :headline "Move and switch off homebrew"
      :detail "Move or copy content between sources, and turn an item or a whole source off without deleting it."}
     {:group "Improved"
      :icon "fa-fist-raised"
      :headline "Overhauled weapons and attack table"
      :detail "Attack and damage rolls in one table, with advantage and disadvantage."}
     {:group "Improved"
      :icon "fa-user-shield"
      :headline "Overhauled sign-in and account creation pages"
      :detail "With a password strength meter, sign out everywhere, and an email when your password or email changes."}
     {:group "Improved"
      :icon "fa-box-open"
      :headline "Inventory editing on the sheet"
      :detail "Change quantities and coins on the sheet. Pack contents are items you use up and restock."}
     {:group "Improved"
      :icon "fa-search"
      :headline "Equipment search"
      :detail "The Equipment tab's lists filter as you type."}
     {:group "Improved"
      :icon "fa-print"
      :headline "Spell cards"
      :detail "Black and white for home printers, in level order, with an optional logo on the back."}
     {:group "Improved"
      :icon "fa-file-pdf"
      :headline "A4 paper support"
      :detail "Sheets, cards and spellbooks print on A4 as well as US Letter."}
     {:group "Improved"
      :icon "fa-undo"
      :headline "Levelling down"
      :detail "Choices that stop applying are set aside and return when they apply again."}
     {:group "Improved"
      :icon "fa-tachometer-alt"
      :headline "Faster builder"
      :detail "The builder no longer stalls with a large homebrew library."}
     {:group "Improved"
      :icon "fa-adjust"
      :headline "Easier-to-read buttons"
      :detail "Find white text on yellow hard to read? Turn on Dark Button Text, beside Light Theme."}
     {:group "Fixed"
      :icon "fa-check"
      :headline "Spells missing from three sheet styles now print"}
     {:group "Fixed"
      :icon "fa-check"
      :headline "Two-class casters export in every sheet style"}
     {:group "Fixed"
      :icon "fa-check"
      :headline "A character that showed a blank page opens a recovery panel instead"}
     {:group "Fixed"
      :icon "fa-check"
      :headline "Homebrew saved in two tabs no longer overwrites itself"}
     {:group "Fixed"
      :icon "fa-check"
      :headline "One broken homebrew entry no longer disables its whole source"}
     {:group "Fixed"
      :icon "fa-check"
      :headline "The header and menus no longer overflow on a phone"}
     {:group "Fixed"
      :icon "fa-check"
      :headline "Bards regain Bardic Inspiration on a short rest from level 5"}]}])

(def current-release
  (first releases))

(def current-release-id
  (:id current-release))

(defn unseen?
  "Is there a release the browser has not shown yet? True for a blank stamp, so a
   first visit sees the current release once."
  [seen-id]
  (not= seen-id current-release-id))

(def credits-path
  "Where a deployment puts its patron credits: a static file under resources/public, read when the
   What's New panel opens. The public repo ships it empty (no names); a fork replaces it."
  "/patron-credits.md")

(defn parse-credits
  "The patron credits in `text` as [{:tier name-or-nil :names [...]}], in file order. A line
   starting with # names a tier; any other non-blank line is one patron, a leading - or * dropped;
   names before the first heading form a group with no tier. Groups with no names are left out."
  [text]
  (->> (re-seq #"[^\r\n]+" (str text))
       (map str/trim)
       (remove str/blank?)
       (reduce (fn [groups line]
                 (if-let [[_ tier] (re-matches #"#+\s*(.*)" line)]
                   (conj groups {:tier tier :names []})
                   (let [nm (str/replace line #"^[-*]\s*" "")]
                     (if (seq groups)
                       (update-in groups [(dec (count groups)) :names] conj nm)
                       [{:tier nil :names [nm]}]))))
               [])
       (filterv (comp seq :names))))

(def credits-sample-size
  "How many names of the last credits group show before \"Show all\"."
  24)

(defn credits-display
  "The credit groups as shown: every group but the last in full; the last, when it holds more than
   credits-sample-size names, as :sample (that many of its names, in `shuffle-fn` order) with
   :all (every name, alphabetical, case ignored) behind \"Show all\". `groups` as parse-credits
   returns; `shuffle-fn` takes and returns a seq (`shuffle`, or `identity` in tests)."
  [groups shuffle-fn]
  (let [lead (vec (butlast groups))
        {:keys [names] :as last-group} (last groups)]
    (cond-> lead
      last-group (conj (if (> (count names) credits-sample-size)
                         (assoc last-group
                                :sample (vec (take credits-sample-size (shuffle-fn names)))
                                :all (vec (sort-by str/lower-case names)))
                         last-group)))))

(defn grouped-items
  "The release's items as [group items] pairs, in source order. A group of nil
   means the items carry no heading."
  [release]
  (->> (:items release)
       (partition-by :group)
       (map (fn [items] [(:group (first items)) items]))))
