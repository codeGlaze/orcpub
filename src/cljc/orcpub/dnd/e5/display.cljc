(ns orcpub.dnd.e5.display
  (:require [clojure.string :as s]
            [orcpub.common :as common]
            [orcpub.dnd.e5.weapons :as weapons]
            [orcpub.dnd.e5.magic-items :as mi]
            [orcpub.dnd.e5.units :as units]
            [orcpub.dnd.e5.weapon-damage :as weapon-damage]
            [orcpub.dnd.e5.character.equipment :as char-equip]))

#_(def phb-url "https://www.amazon.com/gp/product/0786965606/ref=as_li_tl?ie=UTF8&tag=orcpub-20&camp=1789&creative=9325&linkCode=as2&creativeASIN=0786965606&linkId=9cd9647802c714f226bd591d61058143")

#_(def scag-url "https://www.amazon.com/gp/product/0786965800/ref=as_li_tl?ie=UTF8&tag=orcpub-20&camp=1789&creative=9325&linkCode=as2&creativeASIN=0786965800&linkId=9b93efa0fc7239ebbf005d0b17367233")

#_(def vgm-url "https://www.amazon.com/gp/product/0786966017/ref=as_li_tl?ie=UTF8&tag=orcpub-20&camp=1789&creative=9325&linkCode=as2&creativeASIN=0786966017&linkId=506a1b33174f884dcec5db8c6c07ad31")

#_(def dmg-url "https://www.amazon.com/gp/product/0786965622/ref=as_li_tl?ie=UTF8&camp=1789&creative=9325&creativeASIN=0786965622&linkCode=as2&tag=orcpub-20&linkId=7d3e39946045872d4da58bd9d14a7a31")

#_(def sources
  {:phb {:abbr "PHB"
         :url phb-url}
   :vgm {:abbr "VGM"
         :url vgm-url}
   :scag {:abbr "SCAG"
          :url scag-url}
   :dmg {:abbr "DMG"
         :url dmg-url}
   :ee {:abbr "EE"
        :url "https://media.wizards.com/2015/downloads/dnd/EE_PlayersCompanion.pdf"}
   :cos {:abbr "COS"
         :url "https://www.amazon.com/gp/product/0786965983/ref=as_li_qf_sp_asin_il_tl?ie=UTF8&tag=orcpub-20&camp=1789&creative=9325&linkCode=as2&creativeASIN=0786965983&linkId=80c8baab14ecdb5c31f29d9c5b594c83"}
   :ua-revised-ranger {:abbr "UA-Revised-Ranger"
                       :url "http://media.wizards.com/2016/dnd/downloads/UA_RevisedRanger.pdf"}
   :ua-cleric {:abbr "UA-Cleric"
               :url "http://media.wizards.com/2016/dnd/downloads/UA_Cleric.pdf"}
   :ua-artificer {:abbr "UA-Artificer"
                  :url "http://www.dmsguild.com/product/213032/Unearthed-Arcana-The-Artificer-Class-5e"}
   :ua-eberron {:abbr "UA-Eberron"
                :url "http://media.wizards.com/2015/downloads/dnd/UA_Eberron_v1.1.pdf"}
   :ua-race-feats {:abbr "UA-Race-Feats"
                   :url "http://media.wizards.com/2017/dnd/downloads/RJSJC2017_04UASkillFeats_24v10.pdf"}
   :ua-skill-feats {:abbr "UA-Skill-Feats"
                    :url "http://media.wizards.com/2017/dnd/downloads/UA-SkillFeats.pdf"}
   :ua-waterborne {:abbr "UA-Waterborne"
                   :url "http://media.wizards.com/2015/downloads/dnd/UA_Waterborne_v3.pdf"}
   :ua-mystic {:abbr "UA-Mystic"
               :url "http://media.wizards.com/2017/dnd/downloads/UAMystic3.pdf"}
   :ua-bard {:abbr "UA-Bard"
             :url "https://media.wizards.com/2016/dnd/downloads/UA_Bard.pdf"}
   :ua-revised-subclasses {:abbr "UA-Revised-Subclasses"
                           :url "http://media.wizards.com/2017/dnd/downloads/UA-RevisedSubclasses.pdf"}
   :ua-trio-of-subclasses {:abbr "UA-Trio-of-Subclasses"
                           :url "http://media.wizards.com/2017/dnd/downloads/UAThreeSubclasses.pdf"}
   :ua-revised-class-options {:abbr "UA-Revised-Class-Options"
                              :url "http://media.wizards.com/2017/dnd/downloads/June5UA_RevisedClassOptv1.pdf"}
   :ua-feats {:abbr "UA-Feats"
              :url "https://media.wizards.com/2016/downloads/DND/UA-Feats-V1.pdf"}
   :ua-warlock-and-wizard {:abbr "UA-Warlock-and-Wizard"
                           :url "http://media.wizards.com/2017/dnd/downloads/20170213_Wizrd_Wrlck_UAv2_i48nf.pdf"}
   :ua-fighter {:abbr "UA-Fighter"
                :url "http://media.wizards.com/2016/dnd/downloads/2016_Fighter_UA_1205_1.pdf"}
   :ua-sorcerer {:abbr "UA-Sorcerer"
                 :url "https://media.wizards.com/2017/dnd/downloads/26_UASorcererUA020617s.pdf"}
   :ua-gothic-heroes {:abbr "UA-Gothic-Heroes"
                      :url "https://dnd.wizards.com/sites/default/files/media/upload/articles/UA%20Gothic%20Characters.pdf"}
   :ua-starter-spells {:abbr "UA-Starter-Spells"
                       :url "http://media.wizards.com/2017/dnd/downloads/UA-Starter-Spells.pdf"}})

(def plural-map
  {:feet :feet})

(defn equipment-name [equipment-map equipment-kw]
  (or (:name (equipment-map equipment-kw))
      (if (string? equipment-kw)
        equipment-kw
        (common/kw-to-name equipment-kw true))))

(defn unit-amount-description [{:keys [units amount singular plural] :or {amount 1}}]
  (let [plural (or plural (plural-map units))]
    (str amount " " (if (not= 1 amount)
                      (if plural
                        (common/safe-name plural)
                        (str (common/safe-name units) "s"))
                      (if singular
                        (common/safe-name singular)
                        (str (common/safe-name units)))))))

(defn duration-description [{:keys [concentration] :as duration}]
  (str (when concentration "conc. ") (unit-amount-description duration)))

#_(defn get-source [source]
  (sources (or source :phb)))

#_(defn source-description [source page]
  (str "see " (:abbr (get-source source)) " " page))

(defn frequency-description [{:keys [units amount] :or {amount 1}}]
  (str
   (case amount
     1 "once"
     2 "twice"
     (str amount " times"))
   "/"
   (s/replace (common/safe-name units) #"-" " ")))

(defn attack-description-short [{:keys [description summary attack-type area-type] :as attack}]
  (let [summary (or summary description)]
    (str
     (when summary (str summary ", "))
     (case attack-type
       :area (case area-type
               :line (str (:line-width attack) " x " (:line-length attack) " ft. line")
               :cone (str (:length attack) " ft. cone")
               nil)
       :ranged "ranged"
       "melee"))))

(defn attack-description [{:keys [description summary attack-type area-type damage-type damage-die damage-die-count damage-modifier attack-modifier save save-dc page source] :as attack}]
  (let [attack-mod-str (when attack-modifier (str (common/bonus-str attack-modifier) " to hit, "))]
    (str
     (attack-description-short attack)
     (case attack-type
       :area ", "
       :ranged (str ", " attack-mod-str)
       (str ", " attack-mod-str))
     (or damage-die-count
         (::weapons/damage-die-count attack))
     "d"
     (or damage-die
         (::weapons/damage-die attack))
     (when damage-modifier (common/mod-str damage-modifier))
     " "
     (when damage-type (common/safe-name damage-type))
     " damage"
     (when save (str ", DC" save-dc " " (common/safe-name save) " save"))
     #_(when page (str " (" (source-description source page) ")")))))

(defn action-description [{:keys [description summary source page duration range frequency qualifier]}]
  (str
   (or summary description)
   (when (or range duration frequency)
     (str
      " ("
      (s/join ", "
              (remove
               nil?
               [qualifier
                (when range (str "range " (unit-amount-description range)))
                (when duration (str "lasts " (duration-description duration)))
                (when frequency (str "use " (frequency-description frequency)))
                #_(if page (source-description source page))]))
      ")"))))

;; Weapon damage text, shared by the weapons table and the PDF: weapon-damage-model.md

(defn damage-dice-str
  "An entry's dice and flat bonus as text: \"1d8+3\", \"2d6\", or \"+2\" for a flat-only
  entry. Takes a damage entry (base or extra); returns a string."
  [{:keys [::weapons/damage-die ::weapons/damage-die-count ::mi/damage-bonus ::weapon-damage/bonus]}]
  (let [flat (+ (or damage-bonus 0) (or bonus 0))]
    (if damage-die
      (str damage-die-count "d" damage-die (when-not (zero? flat) (common/mod-str flat)))
      (common/mod-str flat))))

(defn damage-str
  "An entry's dice and damage type as text, such as \"1d8+3 slashing\". Takes a damage
  entry; returns a string, without a type when the entry has none."
  [entry]
  (let [type (::weapons/damage-type entry)]
    (str (damage-dice-str entry) (when type (str " " (common/safe-name type))))))

(defn creature-types-str
  "Creature types as a plural phrase: #{:fiend :undead} gives \"fiends and undead\".
  Takes a set of type keywords and the joining word; returns a string."
  [types joiner]
  (let [plural (fn [t] (if (= :undead t) "undead" (str (common/safe-name t) "s")))
        names (map plural (sort types))]
    (if (next names)
      (str (s/join ", " (butlast names)) " " joiner " " (last names))
      (first names))))

(def recharge-names
  "How each reset period reads after \"once per\"."
  {::units/turn "turn" ::units/round "round" ::units/short-rest "short rest"
   ::units/long-rest "long rest" ::units/rest "rest" ::units/dawn "dawn"})

(defn condition-phrases
  "The conditions an entry or effect names, as short phrases in a fixed order:
  trigger, targets, toggle, cost. Takes the entry and a map of toggle id to toggle
  name; returns a vector of strings, empty when it applies on every hit."
  [{:keys [::mi/on ::mi/min-roll ::mi/attack-type ::mi/against ::mi/not-against
           ::mi/charge-cost ::mi/recharge]
    toggle ::mi/while}
   names]
  (cond-> []
    (= :miss on) (conj "on a miss")
    min-roll (conj (str (if (#{8 11 18} min-roll) "on an " "on a ") min-roll
                        (when (< min-roll 20) "+")))
    (= :crit on) (conj "on a crit")
    attack-type (conj (str "on a " (common/safe-name attack-type) " hit"))
    against (conj (str "vs " (creature-types-str against "and")))
    not-against (conj (str "not vs " (creature-types-str not-against "or")))
    toggle (conj (str "while " (get names toggle (common/safe-name toggle))))
    charge-cost (conj (str charge-cost (if (= 1 charge-cost) " charge" " charges")))
    recharge (conj (str "once per " (recharge-names recharge (common/safe-name recharge))))))

(defn toggle-names
  "A weapon's toggles as a map of toggle id to its name. Takes the weapon map."
  [weapon]
  (into {} (map (juxt ::mi/toggle-id ::mi/toggle-name)) (::mi/toggles weapon)))

(defn effect-summary
  "A save effect's short summary, such as \"DC 15 Str or prone\"; nil for effects
  without a save, whose full text belongs in the opened row. Takes the effect."
  [{:keys [::mi/save-dc ::mi/save-ability ::mi/save-fail]}]
  (when save-dc
    (str "DC " save-dc " " (s/capitalize (common/safe-name save-ability))
         (if save-fail (str " or " (common/safe-name save-fail)) " save"))))

(defn extra-damage-str
  "One extra damage entry with its conditions: \"+2d6 fire while Ablaze\". Takes the
  entry (type resolved) and a map of toggle id to name; returns a string."
  [entry names]
  (let [dice (damage-str entry)
        phrases (condition-phrases entry names)]
    (str (if (s/starts-with? dice "+") dice (str "+" dice))
         (when (seq phrases) (str " " (s/join ", " phrases))))))

(defn weapon-extras-str
  "Everything a weapon adds beyond its base damage, as one line: each extra, then each
  save, joined by \"; \". Takes the weapon map; returns a string, or nil when it adds nothing."
  [weapon]
  (let [names (toggle-names weapon)
        extras (map #(extra-damage-str (weapon-damage/resolve-extra weapon %) names)
                    (::mi/extra-damage weapon))
        saves (keep (fn [effect]
                      (when-let [summary (effect-summary effect)]
                        (let [phrases (condition-phrases effect names)
                              dice (when (::weapons/damage-die effect) (damage-str effect))]
                          (s/join ", " (remove nil? (concat [summary dice] phrases))))))
                    (::mi/effects weapon))]
    (not-empty (s/join "; " (concat extras saves)))))


(defn off-row?
  "Whether an extra or effect is left off the row for the + menu or a charge counter: a
  crit or rolled-number trigger, a charge cost or a recharge. Takes the entry."
  [{:keys [::mi/on ::mi/min-roll ::mi/charge-cost ::mi/recharge]}]
  (boolean (or (= :crit on) min-roll charge-cost recharge)))

(defn- plus-str
  "An extra's dice and type with a leading plus: \"+2d6 fire\", \"+2 bludgeoning\"."
  [entry]
  (let [text (damage-str entry)]
    (if (s/starts-with? text "+") text (str "+" text))))

(defn weapon-row-lines
  "What the row shows besides its tiles. Takes the weapon map and the set of toggle ids
  that are on. Returns {:tile {:text :count :types} or nil, :lines [{:label :chips}]};
  a chip is {:text :type :style (:fill :tint :outline) :ringed?}. One every-hit extra goes
  on the tile; two or more get an Every hit line and a +N more chip."
  [weapon toggles-on]
  (let [names (toggle-names weapon)
        shown (fn [{toggle ::mi/while :as e}]
                (and (not (off-row? e)) (or (nil? toggle) (contains? toggles-on toggle))))
        label (fn [e default] (or (not-empty (s/join ", " (condition-phrases (dissoc e ::mi/while) names)))
                                  default))
        extras (->> (::mi/extra-damage weapon)
                    (filter shown)
                    (map (partial weapon-damage/resolve-extra weapon)))
        every-hit (filter #(= "Every hit" (label % "Every hit")) extras)
        chip (fn [style e] {:text (plus-str e) :type (::weapons/damage-type e) :style style
                            :ringed? (boolean (::mi/while e))})
        grouped (reduce (fn [acc [l c]] (update acc l (fnil conj []) c))
                        (array-map)
                        (concat
                         (when (next every-hit) (map #(vector "Every hit" (chip :fill %)) every-hit))
                         (for [e extras :when (not= "Every hit" (label e "Every hit"))]
                           [(label e nil) (chip :tint e)])
                         (for [e (::mi/effects weapon)
                               :when (and (shown e) (effect-summary e))]
                           [(label e "On hit") {:text (effect-summary e) :style :outline}])))
        order (fn [[l]] (case l "Every hit" 0 "On hit" 1 2))]
    {:tile (when (seq every-hit)
             {:count (count every-hit)
              :text (if (next every-hit)
                      (str "+" (count every-hit) " more")
                      (s/replace (plus-str (first every-hit)) #" .*" ""))
              :types (mapv ::weapons/damage-type every-hit)})
     :lines (mapv (fn [[l chips]] {:label l :chips chips}) (sort-by order grouped))}))
