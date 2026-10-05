(ns orcpub.dnd.e5.emblems
  "The class emblems on the spellbook pages: which icon heads each class, and what a
   player may swap it for. The icons are game-icons.net glyphs (CC BY 3.0) vendored under
   resources/public/image/emblems, each author credited in resources/public/image/ATTRIBUTION.md.
   The publisher's own class symbols are not in the SRD, so they are not used."
  (:require [clojure.string]))

(def pools
  "Per class, the icons on offer as [file-name author], the default first. The druid pool
   is the widest on purpose: leaves, beasts, tracks, fungus, fire, water and moon all read
   as druid."
  {:bard [["harp" "delapouite"]
          ["lyre" "lorc"]
          ["music-spell" "lorc"]
          ["musical-notes" "delapouite"]
          ["pan-flute" "delapouite"]
          ["flute" "delapouite"]
          ["drama-masks" "lorc"]
          ["guitar" "lorc"]
          ["french-horn" "caro-asercion"]
          ["quill-ink" "lorc"]
          ["feather" "lorc"]]
   :cleric [["holy-symbol" "lorc"]
            ["heraldic-sun" "caro-asercion"]
            ["sunbeams" "lorc"]
            ["sun-priest" "delapouite"]
            ["ankh" "lorc"]
            ["spiked-halo" "lorc"]
            ["holy-water" "delapouite"]
            ["jeweled-chalice" "lorc"]
            ["rod-of-asclepius" "delapouite"]
            ["jerusalem-cross" "delapouite"]]
   :druid [["leaf-swirl" "lorc"]
           ["oak-leaf" "delapouite"]
           ["linden-leaf" "lorc"]
           ["paw-print" "lorc"]
           ["deer-track" "delapouite"]
           ["mushrooms" "delapouite"]
           ["acorn" "lorc"]
           ["stag-head" "lorc"]
           ["wolf-howl" "lorc"]
           ["raven" "lorc"]
           ["tree-roots" "delapouite"]
           ["water-drop" "sbed"]
           ["campfire" "lorc"]
           ["moon" "lorc"]
           ["sprout" "lorc"]]
   :paladin [["cross-shield" "delapouite"]
             ["shining-sword" "lorc"]
             ["swords-power" "delapouite"]
             ["sword-altar" "delapouite"]
             ["checked-shield" "lorc"]
             ["visored-helm" "lorc"]
             ["mounted-knight" "skoll"]
             ["sun-spear" "delapouite"]
             ["magic-shield" "lorc"]
             ["winged-sword" "lorc"]]
   :ranger [["arrow-flights" "lorc"]
            ["bow-arrow" "delapouite"]
            ["broadhead-arrow" "lorc"]
            ["crosshair-arrow" "lorc"]
            ["bowman" "lorc"]
            ["wolf-head" "lorc"]
            ["hunting-horn" "lorc"]
            ["pine-tree" "lorc"]
            ["falcon-moon" "delapouite"]
            ["fox-head" "lorc"]]
   :sorcerer [["fluffy-flame" "lorc"]
              ["dragon-orb" "delapouite"]
              ["crystal-shine" "lorc"]
              ["lightning-helix" "lorc"]
              ["fire-ray" "lorc"]
              ["dragon-head" "faithtoken"]
              ["magic-swirl" "lorc"]
              ["fire-spell-cast" "delapouite"]
              ["crystal-wand" "lorc"]
              ["dragon-spiral" "lorc"]]
   :warlock [["warlock-eye" "delapouite"]
             ["pentagram-rose" "lorc"]
             ["interlaced-tentacles" "lorc"]
             ["warlock-hood" "delapouite"]
             ["evil-moon" "lorc"]
             ["daemon-skull" "lorc"]
             ["burning-eye" "lorc"]
             ["all-seeing-eye" "delapouite"]
             ["moon-bats" "delapouite"]
             ["candle-skull" "lorc"]]
   :wizard [["spell-book" "delapouite"]
            ["pointy-hat" "lorc"]
            ["wizard-staff" "lorc"]
            ["crystal-ball" "lorc"]
            ["scroll-unfurled" "lorc"]
            ["magic-portal" "lorc"]
            ["wizard-face" "delapouite"]
            ["crystal-wand" "lorc"]
            ["sands-of-time" "lorc"]
            ["orb-wand" "willdabeast"]]})

(def all-icons
  "Every vendored emblem. The server draws only names in this set: the name becomes part
   of a resource path, so anything else is refused rather than looked up."
  (into #{} (comp (mapcat val) (map first)) pools))

(def authors
  "Icon name to its author, as named in the credit line."
  (into {}
        (for [[_ opts] pools [icon author] opts]
          [icon (->> (clojure.string/split author #"-")
                     (map clojure.string/capitalize)
                     (clojure.string/join " "))])))

(def fallback-icon
  "For a caster outside the eight spellcasting classes: a homebrew class, or spells from
   a race or a feat."
  "spell-book")

(defn default-icon
  "The default emblem for `class-kw`."
  [class-kw]
  (or (ffirst (get pools class-kw)) fallback-icon))

(defn options
  "What the picker offers for `class-kw`: its own pool, or every icon for a class
   without one."
  [class-kw]
  (or (get pools class-kw)
      (->> (vals pools) (apply concat) (distinct) (sort-by first) vec)))

(defn icon-for
  "The emblem to draw for `class-kw`: the player's `chosen` name when it is a vendored
   icon, else the class default."
  [class-kw chosen]
  (if (contains? all-icons chosen) chosen (default-icon class-kw)))
