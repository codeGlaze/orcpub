;; Update ns requires to include the extracted calishite data namespace.
(ns orcpub.dnd.e5.character.random
  (:require [clojure.string :as s]
            [orcpub.data.names.turami :as turami]
            [orcpub.data.names.calishite :as calishite]
            [orcpub.data.names.chondathan :as chondathan]
            [orcpub.data.names.damaran :as damaran]
            [orcpub.data.names.shou :as shou]
            [orcpub.data.names.illuskan :as illuskan]
            [orcpub.data.names.elf :as elf]
            [orcpub.data.names.dwarf :as dwarf]
            [orcpub.data.names.halfling :as halfling]))
            ; [orcpub.data.names.mulan :as mulan]))

;; calishite-names original block: start-line=5 end-line=236
;; TODO (move-first-verify): `calishite-names` moved to `src/cljc/orcpub/data/names/calishite.cljc`.
;; Keep the shim below until tests and Figwheel verification pass. After verification, remove this shim and update callers to use the new namespace directly.
 (def calishite-names
   (let [m calishite/calishite-names
         cur-ns (str (ns-name *ns*))]
     (reduce-kv (fn [acc k v]
                  (assoc acc (keyword cur-ns (name k)) v))
                {}
                m)))

;; chondathan-names original block: start-line=18 end-line=58
;; TODO (move-first-verify): `chondathan-names` moved to `src/cljc/orcpub/data/names/chondathan.cljc`.
;; Keep the shim below until tests and Figwheel verification pass. After verification, remove this shim and update callers to use the new namespace directly.
(def chondathan-names
  (let [m chondathan/chondathan-names
        cur-ns (str (ns-name *ns*))]
    (reduce-kv (fn [acc k v]
                 (assoc acc (keyword cur-ns (name k)) v))
               {}
               m)))

;; damaran-names original block: start-line=30 end-line=156
;; TODO (move-first-verify): `damaran-names` moved to `src/cljc/orcpub/data/names/damaran.cljc`.
;; Keep the shim below until tests and Figwheel verification pass. After verification, remove this shim and update callers to use the new namespace directly.
(def damaran-names
  (let [m damaran/damaran-names
        cur-ns (str (ns-name *ns*))]
    (reduce-kv (fn [acc k v]
                 (assoc acc (keyword cur-ns (name k)) v))
               {}
               m)))

;; turami data shim
(def turami-names
  ;; turami data lives in a separate namespace to keep this file small.
  ;; remap the keys from that namespace into this namespace so existing
  ;; callers that use unqualified auto-resolved keywords (e.g. ::male)
  ;; continue to work without changes.
  (let [m turami/turami-names
        cur-ns (str (ns-name *ns*))]
    (reduce-kv (fn [acc k v]
                 (assoc acc (keyword cur-ns (name k)) v))
               {}
               m)))

;; shou-names original block: start-line=38 end-line=90
;; TODO (move-first-verify): `shou-names` moved to `src/cljc/orcpub/data/names/shou.cljc`.
;; Keep the shim below until tests and Figwheel verification pass. After verification, remove this shim and update callers to use the new namespace directly.
(def shou-names
  (let [m shou/shou-names
        cur-ns (str (ns-name *ns*))]
    (reduce-kv (fn [acc k v]
                 (assoc acc (keyword cur-ns (name k)) v))
               {}
               m)))

;; illuskan-names original block: start-line=67 end-line=156
;; TODO (move-first-verify): `illuskan-names` moved to `src/cljc/orcpub/data/names/illuskan.cljc`.
;; Keep the shim below until tests and Figwheel verification pass. After verification, remove this shim and update callers to use the new namespace directly.
(def illuskan-names
  (let [m illuskan/illuskan-names
        cur-ns (str (ns-name *ns*))]
    (reduce-kv (fn [acc k v]
                 (assoc acc (keyword cur-ns (name k)) v))
               {}
               m)))

;; rashemi-names original block: start-line=79 end-line=156
;; TODO (move-first-verify): `rashemi-names` moved to `src/cljc/orcpub/data/names/rashemi.cljc`.
;; Keep the shim below until tests and Figwheel verification pass. After verification, remove this shim and update callers to use the new namespace directly.
(def rashemi-names
  (let [m rashemi/rashemi-names
        cur-ns (str (ns-name *ns*))]
    (reduce-kv (fn [acc k v]
                 (assoc acc (keyword cur-ns (name k)) v))
               {}
               m)))

;; mulan-names original block: start-line=90 end-line=182
;; TODO (move-first-verify): `mulan-names` moved to `src/cljc/orcpub/data/names/mulan.cljc`.
;; Keep the shim below until tests and Figwheel verification pass. After verification, remove this shim and update callers to use the new namespace directly.
(def mulan-names
  (let [m mulan/mulan-names
        cur-ns (str (ns-name *ns*))]
    (reduce-kv (fn [acc k v]
                 (assoc acc (keyword cur-ns (name k)) v))
               {}
               m)))

;; elf-names original block: start-line=102 end-line=299
;; TODO (move-first-verify): `elf-names` moved to `src/cljc/orcpub/data/names/elf.cljc`.
;; Keep the shim below until tests and Figwheel verification pass. After verification, remove this shim and update callers to use the new namespace directly.
(def elf-names
  (let [m elf/elf-names
        cur-ns (str (ns-name *ns*))]
    (reduce-kv (fn [acc k v]
                 (assoc acc (keyword cur-ns (name k)) v))
               {}
               m)))

;; dwarf-names original block: start-line=114 end-line=220
;; TODO (move-first-verify): `dwarf-names` moved to `src/cljc/orcpub/data/names/dwarf.cljc`.
;; Keep the shim below until tests and Figwheel verification pass. After verification, remove this shim and update callers to use the new namespace directly.
(def dwarf-names
  (let [m dwarf/dwarf-names
        cur-ns (str (ns-name *ns*))]
    (reduce-kv (fn [acc k v]
                 (assoc acc (keyword cur-ns (name k)) v))
               {}
               m)))

;; halfling-names original block: start-line=126 end-line=360
;; TODO (move-first-verify): `halfling-names` moved to `src/cljc/orcpub/data/names/halfling.cljc`.
;; Keep the shim below until tests and Figwheel verification pass. After verification, remove this shim and update callers to use the new namespace directly.
(def halfling-names
  (let [m halfling/halfling-names
        cur-ns (str (ns-name *ns*))]
    (reduce-kv (fn [acc k v]
                 (assoc acc (keyword cur-ns (name k)) v))
               {}
               m)))

(defn name-search-match [text]
  (re-matches #".*\bname\b.*" text))

(defn first-last [list sex]
  (str (-> list sex rand-nth)
       " "
       (-> list ::surname rand-nth)))

(defn join-names [first last]
  (str first " " last))

(defn random-item [list key]
  (-> list key rand-nth))

(def sexes
  [::male ::female])

(def sexes-set (set sexes))

(def human-subraces
  [::calishite
   ::chondathan
   ::shou
   ::turami
   ::illuskan
   ::damaran
   ::rashemi
   ::mulan])

(def subraces-set (set human-subraces))

(def races
  [::elf
   ::dwarf
   ::halfling
   ::human
   ::human
   ::human])

(def races-set (set races))

(defn random-sex []
  (rand-nth sexes))

(derive ::tethyrian ::chondathan)

(defmulti random-name (fn [{:keys [race subrace sex]}]
                        [race subrace sex]))

(defmethod random-name [::human ::calishite ::male] [_]
  (first-last calishite-names ::male))

(defmethod random-name [::human ::calishite ::female] [_]
  (first-last calishite-names ::female))

(defn chondathan-surname []
  (str (random-item chondathan-names ::surname-pre)
       (random-item chondathan-names ::surname-post)))

(defn chondathan-male-name []
  (str (random-item chondathan-names ::male-pre)
       (random-item chondathan-names ::male-post)))

(defmethod random-name [::human ::chondathan ::male] [_]
  (join-names
   (random-item chondathan-names ::male)
   (chondathan-surname)))

(defmethod random-name [::human ::chondathan ::female] [_]
  (join-names
   (random-item chondathan-names ::female)
   (chondathan-surname)))

(defn set-name [list type]
  (random-item list type))

(defn combined-name [list pre-type post-type]
  (str (random-item list pre-type)
       (random-item list post-type)))

(defn random-set-or-combined [list type pre-type post-type & [fraction-set]]
  (let [r (rand)]
    (if (< r (or fraction-set 0.2))
      (set-name list type)
      (combined-name list pre-type post-type))))

(defmethod random-name [::human ::turami ::male] [_]
  (join-names
   (random-set-or-combined turami-names ::male ::male-pre ::male-post)
   (random-set-or-combined turami-names ::surname ::surname-pre ::surname-post)))

(defmethod random-name [::human ::turami ::female] [_]
  (join-names
   (random-set-or-combined turami-names ::female ::female-pre ::female-post)
   (random-set-or-combined turami-names ::surname ::surname-pre ::surname-post)))

(defn shou-name [first]
  (join-names
   first
   (random-set-or-combined shou-names ::surname :pre :post)))

(defmethod random-name [::human ::shou ::male] [_]
  (shou-name
   (random-set-or-combined shou-names ::male :pre :post)))

(defmethod random-name [::human ::shou ::female] [_]
  (shou-name
   (random-set-or-combined shou-names ::female :pre :post)))

(defn damaran-name [first]
  (join-names
   first
   (random-set-or-combined damaran-names ::surname ::surname-pre ::surname-post)))

(defmethod random-name [::human ::damaran ::male] [_]
  (damaran-name
   (random-set-or-combined damaran-names ::male ::male-pre ::male-post)))

(defmethod random-name [::human ::damaran ::female] [_]
  (damaran-name
   (random-set-or-combined damaran-names ::female ::female-pre ::female-post)))

(defn illuskan-name [first]
  (join-names
   first
   (combined-name illuskan-names ::surname-pre ::surname-post)))

(defmethod random-name [::human ::illuskan ::male] [_]
  (illuskan-name
   (set-name illuskan-names ::male)))

(defmethod random-name [::human ::illuskan ::female] [_]
  (illuskan-name
   (set-name illuskan-names ::female)))

(defn rashemi-name [first]
  (join-names
   first
   (combined-name rashemi-names ::surname-pre ::surname-post)))

(defmethod random-name [::human ::rashemi ::male] [_]
  (rashemi-name
   (random-set-or-combined rashemi-names ::male ::pre ::male-post)))

(defmethod random-name [::human ::rashemi ::female] [_]
  (rashemi-name
   (random-set-or-combined rashemi-names ::female ::pre ::female-post)))

(defn mulan-name [first]
  (join-names
   first
   (set-name mulan-names ::surname)))

(defmethod random-name [::human ::mulan ::male] [_]
  (mulan-name
   (set-name mulan-names ::male)))

(defmethod random-name [::human ::mulan ::female] [_]
  (mulan-name
   (set-name mulan-names ::female)))

(def human-subraces-set
  (set human-subraces))

(def races-and-subraces
  (concat (vec human-subraces)
          [::elf
           ::dwarf
           ::halfling]))

(defn random-race []
  (rand-nth
   races))

(defn random-human-subrace []
  (rand-nth human-subraces))

(defn random-subrace [race]
  (case race
    ::human (random-human-subrace)
    nil))

(defn random-race-or-subrace []
  (rand-nth races-and-subraces))

(defn elf-name [first]
  (join-names
   first
   (random-set-or-combined elf-names ::surname ::surname-pre ::surname-post)))

(defmethod random-name [::elf nil ::male] [_]
  (elf-name
   (random-set-or-combined elf-names ::male ::male-pre ::male-post)))

(defmethod random-name [::elf nil ::female] [_]
  (elf-name
   (random-set-or-combined elf-names ::female ::female-pre ::female-post)))

(defn dwarf-name [first]
  (join-names
   first
   (apply random-set-or-combined dwarf-names (rand-nth [[::surname ::surname-pre-1 ::surname-post-1]
                                                         [::surname ::surname-pre-2 ::surname-post-2]]))))

(defmethod random-name [::dwarf nil ::male] [_]
  (dwarf-name
   (random-set-or-combined dwarf-names ::male ::male-pre ::male-post)))

(defmethod random-name [::dwarf nil ::female] [_]
  (dwarf-name
   (random-set-or-combined dwarf-names ::female ::female-pre ::female-post)))

(defn halfling-name [first]
  (join-names
   first
   (random-set-or-combined halfling-names ::surname ::surname-pre ::surname-post)))

(defmethod random-name [::halfling nil ::male] [_]
  (halfling-name
   (set-name halfling-names ::male)))

(defmethod random-name [::halfling nil ::female] [_]
  (halfling-name
   (set-name halfling-names ::female)))

(defmethod random-name :default [_]
  (random-name {:race ::human
                :subrace (random-human-subrace)
                :sex (random-sex)}))

(defn random-name-result [{:keys [sex race subrace]}]
  (let [final-race (if (human-subraces-set subrace)
                     ::human
                     (or (races-set race) (random-race)))
        final-subrace (or (subraces-set subrace) (random-subrace final-race))
        final-sex (or (sexes-set sex) (random-sex))
        cfg {:race final-race
             :subrace final-subrace
             :sex final-sex}]
    (assoc cfg :name (random-name cfg))))

(def tavern-names
  {::names ["The Devil's Due"
            "The Bearded Clam"
            "The Barking Spider"
            "The Leaky Flagon"
            "The Leaky Mug"
            "The Leaky Horn"
            "The Wing and the Prayer"
            "The Dry Mug"
            "The End of the Road"
            "One Last Caress"
            "Long Way Home"
            "Long Road Home"
            "Dust in the Wind"
            "Where Eagles Dare"
            "Black Sails on Sunset"
            "The Ghost in the Darkness"
            "The Eye of the Beholder"
            "The Quick and the Dead"
            "The Waking Nightmare"
            "The Beginning of the End"
            "The Burned Bridge"
            "The Naughty Nymph"
            "The Weakest Link"
            "The Fate Worse Than Death"
            "The Fools Paradise"
            "The Fool and His Money"
            "The Hair of the Dog"
            "The Hairy Eyeball"
            "The Powers That Be"
            "The Elephant and the Room"
            "The Crack of Doom"
            "The Birds and the Bees"
            "Three Sheets to the Wind"
            "Thick as Thieves"
            "The Gilded Gold"
            "The Painted Lily"
            "Without a Paddle"]
   ::pre-creature ["Fat"
                   "Prancing"
                   "Dancing"
                   "Salty"
                   "Faithful"
                   "Bearded"
                   "Surly"
                   "Brawling"
                   "Fighting"
                   "Shaven"
                   "Limping"
                   "Lisping"
                   "Smiling"
                   "Smirking"
                   "Limber"
                   "Mustachioed"
                   "Tattooed"
                   "Gelded"
                   "Fierce"
                   "Naked"
                   "Leaping"
                   "Gallivanting"
                   "Gregarious"
                   "Preening"
                   "Warty"
                   "Swashbuckling"
                   "Flatulent"
                   "Flirting"
                   "Bawdy"
                   "Flirtatious"
                   "Flippant"
                   "Cheeky"
                   "Waggish"
                   "Fearless"
                   "Starving"
                   "Trusty"
                   "Sleeping"
                   "Yawning"
                   "Drunken"
                   "Drunk"
                   "Corpulent"
                   "Cloaked"
                   "Swimming"
                   "Soggy"
                   "Sailing"
                   "Zombie"
                   "Petrified"
                   "Slumbering"
                   "Rotten"
                   "Vile"
                   "Dessicated"
                   "Mummy"
                   "Mummified"
                   "Foul"
                   "Winged"
                   "Debauched"
                   "Entranced"
                   "Sad"
                   "Sour"
                   "Undead"
                   "Rich"
                   "Poor"
                   "Vacuous"
                   "Vengeful"
                   "Wrathful"
                   "Wax"
                   "Swollen"
                   "Sullen"
                   "Generous"
                   "Masked"
                   "Belligerent"
                   "Bellicose"
                   "Savage"
                   "Riotous"
                   "Bloodthirsty"
                   "Murderous"
                   "Cruel"
                   "Brutal"
                   "Vicious"
                   "Aroused"
                   "Raging"
                   "Impassioned"
                   "Enraged"
                   "Headstrong"
                   "Triumphant"
                   "Scarred"
                   "Quavering"
                   "Hairy"]
   ::pre ["Red"
          "Green"
          "Blue"
          "Purple"
          "Yellow"
          "Gray"
          "Scarlet"
          "Violet"
          "Azure"
          "Indigo"
          "Garnet"
          "Black"
          "White"
          "Rusty"
          "Rusted"
          "Iron"
          "Copper"
          "Bronze"
          "Gold"
          "Golden"
          "Silver"
          "Platinum"
          "Gilded"
          "Giant"
          "Horned"
          "Sunken"
          "Quivering"
          "Silent"
          "Frosty"
          "Scorched"
          "Scorned"
          "Lucky"
          "Great"          
          "Noble"
          "Wooden"
          "Woolen"
          "Forged"
          "Barmy"          
          "Glamourous"
          "Resplendent"
          "Flaming"
          "Fiery"
          "Blazing"
          "Flaring"
          "Raging"
          "Glowing"
          "Bombastic"
          "Dazzling"
          "Dark"
          "Stygian"
          "Dusky"
          "Darkened"
          "Bulging"
          "Barking"
          "Bursting"          
          "Ancient"
          "Burned"
          "Unburned"
          "Olde"         
          "Bloated"          
          "Thirsty"
          "Shining"
          "Bright"
          "Gleaming"
          "Shimmering"
          "Glistening"
          "Radiant"
          "Luminous"
          "Flamboyant"
          "Pretentious"
          "Laughing"
          "Lightning"
          "Thundering"
          "Thunder"
          "Ghost"
          "Ghostly"
          "Haunting"
          "Haunted"
          "Warded"
          "Brick"
          "Broken"
          "Pale"
          "Shadow"
          "Swaying"
          "Epic"
          "Enchanted"
          "Ensorcelled"
          "Charming"
          "Illusory"
          "Forbidden"
          "Forboding"
          "Phantom"
          "Phantasmal"
          "Hearty"
          "Gallant"
          "Leaky"
          "Dirty"
          "Common"
          "Plain"
          "Illuminated"
          "Weeping"
          "Wailing"
          "Screaming"
          "Angry"
          "Lonely"
          "Lonesome"
          "Sodden"
          "Fleeting"
          "Invisible"
          "Unseen"
          "Cloven"
          "Secret"
          "Veiled"
          "Cloaked"
          "Hooded"
          "Hooked"
          "Shrouded"
          "Fuming"
          "Smoking"
          "Wounded"
          "Leprous"
          "Diseased"
          "Deceased"
          "Cowled"
          "Caged"
          "Chained"
          "Unchained"
          "Wicked"
          "Quiescent"]
   ::creature ["Goblin"
               "Cock"
               "Hippogriff"
               "Lion"
               "Cockatrice"
               "Gorgon"
               "Dragon"
               "Manticore"
               "Griffin"
               "Orc"
               "Hobgoblin"
               "Stallion"
               "Mule"
               "Dog"
               "Bitch"
               "Ass"
               "Bull"
               "Buck"
               "Monkey"
               "Clam"
               "Stag"
               "Goat"
               "Pig"
               "Whale"
               "Yeti"
               "Bear"
               "Bat"
               "Owlbear"
               "Dogfish"
               "Lobster"
               "Flumph"
               "Crab"
               "Wench"
               "Barmaid"
               "Banshee"
               "Hag"
               "Harpy"
               "Mermaid"
               "Medusa"
               "Minotaur"
               "Bugbear"
               "Golem"
               "Troll"
               "Sprite"
               "Pixie"
               "Ogre"
               "Pegasus"
               "Kobold"
               "Half-Dragon"
               "Gnome"
               "Dryad"
               "Centaur"
               "Rust Monster"
               "Fiend"
               "Imp"
               "Blackfish"
               "Swordfish"
               "Satyr"
               "Captain"
               "Bard"
               "Rogue"
               "Mage"
               "Enchantress"
               "Sorceress"
               "King"
               "Queen"
               "Wizard"
               "Avenger"
               "Hero"
               "Harlot"
               "Courtesan"
               "Hussy"
               "Floozy"
               "Bimbo"
               "Hustler"
               "Whore"
               "Whoremonger"
               "Lady"
               "Knight"
               "Lord"
               "Pony"
               "Horse"
               "Liar"
               "Drunk"
               "Fool"
               "Jester"
               "Raven"
               "Crow"
               "Alchemist"
               "Conjurer"
               "Minister"
               "Spider"
               "Cavalier"
               "Champion"
               "Guardian"
               "Paladin"
               "Lover"
               "Templar"
               "Boatman"
               "Bowman"
               "Swordsman"
               "Horseman"
               "Stranger"
               "Watcher"
               "Merchant"
               "Dead"
               "Empress"
               "Emperor"
               "Unicorn"
               "Orcicorn"
               "Assassin"
               "Sellsword"
               "Minstrel"
               "Courtier"
               "Courtesan"
               "Burglar"
               "Leper"
               "Monk"
               "Enemy"
               "Friend"
               "Defiler"
               "Pirate"
               "Smuggler"
               "Wight"
               "Wraith"
               "Nymph"]
   ::object ["Mug"
             "Sword"
             "Shield"
             "Spear"
             "Dagger"
             "Halberd"
             "Javelin"
             "Helm"
             "Mace"
             "Ship"
             "Sail"
             "Goblet"
             "Flagon"
             "Decanter"
             "Crown"
             "Mug"
             "Bow"
             "Quiver"
             "Gauntlet"
             "Hole"
             "Gem"
             "Diamond"
             "Emerald"
             "Ruby"
             "Tome"
             "Tomb"
             "Table"
             "Chair"
             "Throne"
             "Knob"
             "Door"
             "Keg"
             "Tap"
             "Portal"
             "Anchor"
             "Bodice"
             "Ring"
             "Staff"
             "Wand"
             "Chalice"
             "Pitcher"
             "Arrow"
             "Bolt"
             "Hammer"
             "Hilt"
             "Pommel"
             "Rod"
             "Mandolin"
             "Cittern"
             "Lute"
             "Harp"
             "Flute"
             "Lyre"
             "Censer"
             "Lantern"
             "Mirror"
             "Mantle"
             "Cloak"
             "Medallion"
             "Necklace"
             "Pipes"
             "Periapt"
             "Amulet"
             "Scarab"
             "Ring"
             "Eye"
             "Hand"
             "Toe"
             "Foot"
             "Head"
             "Skull"
             "Ear"
             "Stone"
             "Stones"
             "Vial"
             "Talisman"
             "Tentacles"
             "Fireball"
             "Trident"
             "Wand"
             "Boots"
             "Wings"
             "Blade"
             "Axe"
             "Orb"
             "Charm"
             "Letter"
             "Chest"
             "Box"
             "Bell"
             "Gong"
             "Wagon"
             "Wheel"
             "Tree"
             "Leaf"
             "Pipe"
             "Illusion"
             "Incantation"
             "Whisper"
             "Tryst"
             "Tattoo"
             "Rune"
             "Alchemy"
             "Spell"
             "Alehouse"
             "Saloon"
             "Scroll"
             "House"
             "Shack"
             "Turret"
             "Hall"
             "Spire"
             "Prayer"
             "Wing"
             "Wings"
             "Barrel"
             "Death"
             "Scythe"
             "Torch"
             "Column"
             "Sconce"
             "Bedpan"
             "Battle"
             "Conflict"
             "Failing"
             "Fail"
             "Fall"
             "Bane"
             "Mercy"
             "Wrath"
             "Ember"
             "Cistern"
             "Chandelier"
             "Claw"
             "Bone"
             "Bones"
             "Battlement"
             "Bridge"
             "Elixer"
             "Potion"
             "Draught"
             "Libation"
             "Tonic"
             "Philter"
             "Dram"
             "Cordial"
             "Ale"
             "Glance"
             "Glimpse"
             "Glove"
             "Sheath"
             "Illusion"
             "Spell"
             "Secret"
             "Veil"
             "Threat"
             "Mask"
             "Shroud"
             "Coin"
             "Scabbard"
             "Vessel"
             "Boat"
             "Hold"
             "Keep"
             "Barge"
             "Schooner"
             "Skiff"
             "Wall"
             "Quill"
             "Barnacle"
             "Paradise"
             "Game"
             "Card"]})

(defn random-tavern-name-1 []
  (str "The "
       (random-item tavern-names (rand-nth [::pre ::pre-creature]))
       " "
       (random-item tavern-names ::creature)))

(defn random-tavern-name-2 []
  (str "The "
       (random-item tavern-names ::pre)
       " "
       (random-item tavern-names ::object)))

(defn random-tavern-name-3 []
  (str "The "
       (random-item tavern-names ::creature)
       "'s "
       (random-item tavern-names ::object)))

(defn random-creature-or-object []
  (random-item tavern-names (rand-nth [::creature ::object])))

(defn random-tavern-name-4 []
  (str "The "
       (random-creature-or-object)
       " and the "
       (random-creature-or-object)))

(defn random-tavern-name-5 []
  (str (random-item tavern-names ::object)
       " of the "
       (random-item tavern-names ::creature)))

(defn random-tavern-name []
  ((rand-nth [random-tavern-name-1
              random-tavern-name-2
              random-tavern-name-3
              random-tavern-name-4
              random-tavern-name-5])))
