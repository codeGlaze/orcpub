(ns orcpub.dnd.e55.spells
  "SRD 5.2.1 spell delta: the spells that are NEW or MECHANICALLY CHANGED versus
   SRD 5.1. Spells identical in both editions are NOT repeated here -- they are
   inherited from orcpub.dnd.e5.spells, which stays the base.

   GENERATED from resources/public/dnld/SRD-5.2.1.pdf by scripts/srd/emit-e55.py;
   do not hand-edit. Every entry is derived from the SRD text itself, not from a
   third-party aggregator, so nothing outside the SRD can reach this file.

   SRD 5.2.1 content (c) Wizards of the Coast LLC, licensed under CC BY 4.0.
   https://creativecommons.org/licenses/by/4.0/")

(def source
  "The version tag every entry carries, for the edition filter."
  :srd-2024)

(def spells
  [
   {
    :name "Acid Splash"
    :key :acid-splash
    :school "evocation"
    :level 0
    :source :srd-2024
    :casting-time "Action"
    :range "60 feet"
    :duration "instantaneous"
    :components {}
    :description "You create an acidic bubble at a point within range, where it explodes in a 5-foot-radius Sphere. Each creature in that Sphere must succeed on a Dexterity saving throw or take 1d6 Acid damage. Cantrip Upgrade. The damage increases by 1d6 when you reach levels 5 (2d6), 11 (3d6), and 17 (4d6)."}
   {
    :name "Alarm"
    :key :alarm
    :school "abjuration"
    :level 1
    :source :srd-2024
    :casting-time "1 Minute"
    :range "30 feet"
    :duration "8 hours"
    :components {}
    :ritual true
    :description "You set an alarm against intrusion. Choose a door, a window, or an area within range that is no larger than a 20-foot Cube. Until the spell ends, an alarm alerts you whenever a creature touches or enters the warded area. When you cast the spell, you can designate creatures that won’t set off the alarm. You also choose whether the alarm is audible or mental: Audible Alarm. The alarm produces the sound of a handbell for 10 seconds within 60 feet of the warded area. Mental Alarm. You are alerted by a mental ping if you are within 1 mile of the warded area. This ping awakens you if you’re asleep."}
   {
    :name "Animal Messenger"
    :key :animal-messenger
    :school "enchantment"
    :level 2
    :source :srd-2024
    :casting-time "Action"
    :range "30 feet"
    :duration "24 hours"
    :components {}
    :ritual true
    :description "A Tiny Beast of your choice that you can see within range must succeed on a Charisma saving throw, or it attempts to deliver a message for you (if the target’s Challenge Rating isn’t 0, it automatically succeeds). You specify a location you have visited and a recipient who matches a general descrip- tion, such as “a person dressed in the uniform of the town guard” or “a red-haired dwarf wearing a pointed hat.” You also communicate a message of up to twenty-five words. The Beast travels for the duration toward the specified location, covering about 25 miles per 24 hours or 50 miles if the Beast can fly. When the Beast arrives, it delivers your message to the creature that you described, mimicking your communication. If the Beast doesn’t reach its desti- nation before the spell ends, the message is lost, and the Beast returns to where you cast the spell. Using a Higher-Level Spell Slot. The spell’s dura- tion increases by 48 hours for each spell slot level above 2."}
   {
    :name "Animal Shapes"
    :key :animal-shapes
    :school "transmutation"
    :level 8
    :source :srd-2024
    :casting-time "Action"
    :range "30 feet"
    :duration "24 hours"
    :components {}
    :description "Choose any number of willing creatures that you can see within range. Each target shape-shifts into a Large or smaller Beast of your choice that has a Challenge Rating of 4 or lower. You can choose a dif- ferent form for each target. On later turns, you can take a Magic action to transform the targets again. A target’s game statistics are replaced by the chosen Beast’s statistics, but the target retains its creature type; Hit Points; Hit Point Dice; alignment; ability to communicate; and Intelligence, Wisdom, and Charisma scores. The target’s actions are lim- ited by the Beast form’s anatomy, and it can’t cast spells. The target’s equipment melds into the new form, and the target can’t use any of that equipment while in that form. The target gains a number of Temporary Hit Points equal to the Hit Points of the first form into which it shape-shifts. These Temporary Hit Points vanish if any remain when the spell ends. The trans- formation lasts for the duration or until the target ends it as a Bonus Action."}
   {
    :name "Arcane Sword"
    :key :arcane-sword
    :school "evocation"
    :level 7
    :source :srd-2024
    :casting-time "Action"
    :range "90 feet"
    :duration "concentration up to 1 minute"
    :components {}
    :description "You create a spectral sword that hovers within range. It lasts for the duration. When the sword appears, you make a melee spell attack against a target within 5 feet of the sword. On a hit, the target takes Force damage equal to 4d12 plus your spellcasting ability modifier. On your later turns, you can take a Bonus Action to move the sword up to 30 feet to a spot you can see and repeat the attack against the same target or a different one."}
   {
    :name "Augury"
    :key :augury
    :school "divination"
    :level 2
    :source :srd-2024
    :casting-time "1 Minute"
    :range "self"
    :duration "instantaneous"
    :components {}
    :ritual true
    :description "You receive an omen from an otherworldly entity about the results of a course of action that you plan to take within the next 30 minutes. The GM chooses the omen from the Omens table. Omens Omen For Results That Will Be … Weal Good Woe Bad Weal and woe Good and bad Indifference Neither good nor bad The spell doesn’t account for circumstances, such as other spells, that might change the results. If you cast the spell more than once before finish- ing a Long Rest, there is a cumulative 25 percent chance for each casting after the first that you get no answer."}
   {
    :name "Aura Of Life"
    :key :aura-of-life
    :school "abjuration"
    :level 4
    :source :srd-2024
    :casting-time "Action"
    :range "self"
    :duration "concentration up to 10 minutes"
    :components {}
    :description "An aura radiates from you in a 30-foot Emanation for the duration. While in the aura, you and your allies have Resistance to Necrotic damage, and your Hit Point maximums can’t be reduced. If an ally with 0 Hit Points starts its turn in the aura, that ally re- gains 1 Hit Point."}
   {
    :name "Banishment"
    :key :banishment
    :school "abjuration"
    :level 4
    :source :srd-2024
    :casting-time "Action"
    :range "30 feet"
    :duration "concentration up to 1 minute"
    :components {}
    :description "One creature that you can see within range must succeed on a Charisma saving throw or be trans- ported to a harmless demiplane for the duration. While there, the target has the Incapacitated con- dition. When the spell ends, the target reappears in the space it left or in the nearest unoccupied space if that space is occupied. If the target is an Aberration, a Celestial, an Ele- mental, a Fey, or a Fiend, the target doesn’t return if the spell lasts for 1 minute. The target is instead transported to a random location on a plane (GM’s choice) associated with its creature type. Using a Higher-Level Spell Slot. You can target one additional creature for each spell slot level above 4."}
   {
    :name "Barkskin"
    :key :barkskin
    :school "transmutation"
    :level 2
    :source :srd-2024
    :casting-time "Bonus Action"
    :range "touch"
    :duration "1 hour"
    :description "You touch a willing creature. Until the spell ends, the target’s skin assumes a bark-like appearance, and the target has an Armor Class of 17 if its AC is lower than that."}
   {
    :name "Befuddlement"
    :key :befuddlement
    :school "enchantment"
    :level 8
    :source :srd-2024
    :casting-time "Action"
    :range "150 feet"
    :duration "instantaneous"
    :components {}
    :description "You blast the mind of a creature that you can see within range. The target makes an Intelligence sav- ing throw. On a failed save, the target takes 10d12 Psychic damage and can’t cast spells or take the Magic ac- tion. At the end of every 30 days, the target repeats the save, ending the effect on a success. The effect can also be ended by the Greater Restoration, Heal, or Wish spell. On a successful save, the target takes half as much damage only."}
   {
    :name "Blindness/deafness"
    :key :blindness-deafness
    :school "transmutation"
    :level 2
    :source :srd-2024
    :casting-time "Action"
    :range "120 feet"
    :duration "1 minute"
    :components {}
    :description "One creature that you can see within range must succeed on a Constitution saving throw, or it has the Blinded or Deafened condition (your choice) for the duration. At the end of each of its turns, the target repeats the save, ending the spell on itself on a success. Using a Higher-Level Spell Slot. You can target one additional creature for each spell slot level above 2."}
   {
    :name "Charm Monster"
    :key :charm-monster
    :school "enchantment"
    :level 4
    :source :srd-2024
    :casting-time "Action"
    :range "30 feet"
    :duration "1 hour"
    :components {}
    :description "One creature you can see within range makes a Wisdom saving throw. It does so with Advantage if you or your allies are fighting it. On a failed save, the target has the Charmed condition until the spell ends or until you or your allies damage it. The Charmed creature is Friendly to you. When the spell ends, the target knows it was Charmed by you. Using a Higher-Level Spell Slot. You can target one additional creature for each spell slot level above 4."}
   {
    :name "Chill Touch"
    :key :chill-touch
    :school "necromancy"
    :level 0
    :source :srd-2024
    :casting-time "Action"
    :range "touch"
    :duration "instantaneous"
    :components {}
    :description "Channeling the chill of the grave, make a melee spell attack against a target within reach. On a hit, the target takes 1d10 Necrotic damage, and it can’t re- gain Hit Points until the end of your next turn. Cantrip Upgrade. The damage increases by 1d10 when you reach levels 5 (2d10), 11 (3d10), and 17 (4d10)."}
   {
    :name "Chromatic Orb"
    :key :chromatic-orb
    :school "evocation"
    :level 1
    :source :srd-2024
    :casting-time "Action"
    :range "90 feet"
    :duration "instantaneous"
    :components {}
    :description "You hurl an orb of energy at a target within range. Choose Acid, Cold, Fire, Lightning, Poison, or Thun- der for the type of orb you create, and then make a ranged spell attack against the target. On a hit, the target takes 3d8 damage of the chosen type. If you roll the same number on two or more of the d8s, the orb leaps to a different target of your choice within 30 feet of the target. Make an attack roll against the new target, and make a new damage roll. The orb can’t leap again unless you cast the spell with a level 2+ spell slot. Using a Higher-Level Spell Slot. The damage in- creases by 1d8 for each spell slot level above 1. The orb can leap a maximum number of times equal to the level of the slot expended, and a creature can be targeted only once by each casting of this spell."}
   {
    :name "Color Spray"
    :key :color-spray
    :school "illusion"
    :level 1
    :source :srd-2024
    :casting-time "Action"
    :range "self"
    :duration "instantaneous"
    :components {}
    :description "You launch a dazzling array of flashing, colorful light. Each creature in a 15-foot Cone originating from you must succeed on a Constitution saving throw or have the Blinded condition until the end of your next turn."}
   {
    :name "Command"
    :key :command
    :school "enchantment"
    :level 1
    :source :srd-2024
    :casting-time "Action"
    :range "60 feet"
    :duration "instantaneous"
    :components {}
    :description "You speak a one-word command to a creature you can see within range. The target must succeed on a Wisdom saving throw or follow the command on its next turn. Choose the command from these options: Approach. The target moves toward you by the shortest and most direct route, ending its turn if it moves within 5 feet of you. Drop. The target drops whatever it is holding and then ends its turn. Flee. The target spends its turn moving away from you by the fastest available means. Grovel. The target has the Prone condition and then ends its turn. Halt. On its turn, the target doesn’t move and takes no action or Bonus Action. Using a Higher-Level Spell Slot. You can affect one additional creature for each spell slot level above 1."}
   {
    :name "Commune"
    :key :commune
    :school "divination"
    :level 5
    :source :srd-2024
    :casting-time "1 Minute"
    :range "self"
    :duration "1 minute"
    :components {}
    :ritual true
    :description "You contact a deity or a divine proxy and ask up to three questions that can be answered with yes or no. You must ask your questions before the spell ends. You receive a correct answer for each question. Divine beings aren’t necessarily omniscient, so you might receive “unclear” as an answer if a ques- tion pertains to information that lies beyond the de- ity’s knowledge. In a case where a one-word answer could be misleading or contrary to the deity’s inter- ests, the GM might offer a short phrase as an answer instead. If you cast the spell more than once before finish- ing a Long Rest, there is a cumulative 25 percent chance for each casting after the first that you get no answer."}
   {
    :name "Commune With Nature"
    :key :commune-with-nature
    :school "divination"
    :level 5
    :source :srd-2024
    :casting-time "1 Minute"
    :range "self"
    :duration "instantaneous"
    :components {}
    :ritual true
    :description "You commune with nature spirits and gain knowl- edge of the surrounding area. In the outdoors, the spell gives you knowledge of the area within 3 miles of you. In caves and other natural underground settings, the radius is limited to 300 feet. The spell doesn’t function where nature has been replaced by construction, such as in castles and settlements. Choose three of the following facts; you learn those facts as they pertain to the spell’s area: • Locations of settlements • Locations of portals to other planes of existence • Location of one Challenge Rating 10+ creature (GM’s choice) that is a Celestial, an Elemental, a Fey, a Fiend, or an Undead • The most prevalent kind of plant, mineral, or Beast (you choose which to learn) • Locations of bodies of water For example, you could determine the location of a powerful monster in the area, the locations of bod- ies of water, and the locations of any towns."}
   {
    :name "Comprehend Languages"
    :key :comprehend-languages
    :school "divination"
    :level 1
    :source :srd-2024
    :casting-time "Action"
    :range "self"
    :duration "1 hour"
    :components {}
    :ritual true
    :description "For the duration, you understand the literal mean- ing of any language that you hear or see signed. You also understand any written language that you see, but you must be touching the surface on which the words are written. It takes about 1 minute to read one page of text. This spell doesn’t decode symbols or secret messages."}
   {
    :name "Conjure Animals"
    :key :conjure-animals
    :school "conjuration"
    :level 3
    :source :srd-2024
    :casting-time "Action"
    :range "60 feet"
    :duration "concentration up to 10 minutes"
    :components {}
    :description "You conjure nature spirits that appear as a Large pack of spectral, intangible animals in an unoccu- pied space you can see within range. The pack lasts for the duration, and you choose the spirits’ animal form, such as wolves, serpents, or birds. You have Advantage on Strength saving throws while you’re within 5 feet of the pack, and when you move on your turn, you can also move the pack up to 30 feet to an unoccupied space you can see. Whenever the pack moves within 10 feet of a crea- ture you can see and whenever a creature you can see enters a space within 10 feet of the pack or ends its turn there, you can force that creature to make a Dexterity saving throw. On a failed save, the crea- ture takes 3d10 Slashing damage. A creature makes this save only once per turn. Using a Higher-Level Spell Slot. The damage in- creases by 1d10 for each spell slot level above 3."}
   {
    :name "Conjure Celestial"
    :key :conjure-celestial
    :school "conjuration"
    :level 7
    :source :srd-2024
    :casting-time "Action"
    :range "90 feet"
    :duration "concentration up to 10 minutes"
    :components {}
    :description "You conjure a spirit from the Upper Planes, which manifests as a pillar of light in a 10-foot-radius, 40-foot-high Cylinder centered on a point within range. For each creature you can see in the Cylinder, choose which of these lights shines on it: Healing Light. The target regains Hit Points equal to 4d12 plus your spellcasting ability modifier. Searing Light. The target makes a Dexterity saving throw, taking 6d12 Radiant damage on a failed save or half as much damage on a successful one. Until the spell ends, Bright Light fills the Cylinder, and when you move on your turn, you can also move the Cylinder up to 30 feet. Whenever the Cylinder moves into the space of a creature you can see and whenever a creature you can see enters the Cylinder or ends its turn there, you can bathe it in one of the lights. A creature can be affected by this spell only once per turn. Using a Higher-Level Spell Slot. The healing and damage increase by 1d12 for each spell slot level above 7."}
   {
    :name "Conjure Elemental"
    :key :conjure-elemental
    :school "conjuration"
    :level 5
    :source :srd-2024
    :casting-time "Action"
    :range "60 feet"
    :duration "concentration up to 10 minutes"
    :components {}
    :description "You conjure a Large, intangible spirit from the Ele- mental Planes that appears in an unoccupied space within range. Choose the spirit’s element, which determines its damage type: air (Lightning), earth (Thunder), fire (Fire), or water (Cold). The spirit lasts for the duration. Whenever a creature you can see enters the spir- it’s space or starts its turn within 5 feet of the spirit, you can force that creature to make a Dexterity sav- ing throw if the spirit has no creature Restrained. On failed save, the target takes 8d8 damage of the spirit’s type, and the target has the Restrained condition until the spell ends. At the start of each of its turns, the Restrained target repeats the save. On a failed save, the target takes 4d8 damage of the spirit’s type. On a successful save, the target isn’t Restrained by the spirit. Using a Higher-Level Spell Slot. The damage in- creases by 1d8 for each spell slot level above 5."}
   {
    :name "Conjure Fey"
    :key :conjure-fey
    :school "conjuration"
    :level 6
    :source :srd-2024
    :casting-time "Action"
    :range "60 feet"
    :duration "concentration up to 10 minutes"
    :components {}
    :description "You conjure a Medium spirit from the Feywild in an unoccupied space you can see within range. The spirit lasts for the duration, and it looks like a Fey creature of your choice. When the spirit ap- pears, you can make one melee spell attack against a creature within 5 feet of it. On a hit, the target takes Psychic damage equal to 3d12 plus your spellcasting ability modifier, and the target has the Frightened condition until the start of your next turn, with both you and the spirit as the source of the fear. As a Bonus Action on your later turns, you can teleport the spirit to an unoccupied space you can see within 30 feet of the space it left and make the attack against a creature within 5 feet of it. Using a Higher-Level Spell Slot. The damage in- creases by 1d12 for each spell slot level above 6."}
   {
    :name "Conjure Minor Elementals"
    :key :conjure-minor-elementals
    :school "conjuration"
    :level 4
    :source :srd-2024
    :casting-time "Action"
    :range "self"
    :duration "concentration up to 10 minutes"
    :components {}
    :description "You conjure spirits from the Elemental Planes that flit around you in a 15-foot Emanation for the dura- tion. Until the spell ends, any attack you make deals an extra 2d8 damage when you hit a creature in the Emanation. This damage is Acid, Cold, Fire, or Light- ning (your choice when you make the attack). In addition, the ground in the Emanation is Diffi- cult Terrain for your enemies. Using a Higher-Level Spell Slot. The damage in- creases by 1d8 for each spell slot level above 4."}
   {
    :name "Conjure Woodland Beings"
    :key :conjure-woodland-beings
    :school "conjuration"
    :level 4
    :source :srd-2024
    :casting-time "Action"
    :range "self"
    :duration "concentration up to 10 minutes"
    :components {}
    :description "You conjure nature spirits that flit around you in a 10-foot Emanation for the duration. Whenever the Emanation enters the space of a creature you can see and whenever a creature you can see enters the Emanation or ends its turn there, you can force that creature to make a Wisdom saving throw. The creature takes 5d8 Force damage on a failed save or half as much damage on a successful one. A creature makes this save only once per turn. In addition, you can take the Disengage action as a Bonus Action for the spell’s duration. Using a Higher-Level Spell Slot. The damage in- creases by 1d8 for each spell slot level above 4."}
   {
    :name "Contact Other Plane"
    :key :contact-other-plane
    :school "divination"
    :level 5
    :source :srd-2024
    :casting-time "1 Minute"
    :range "self"
    :duration "1 minute"
    :components {}
    :ritual true
    :description "You mentally contact a demigod, the spirit of a long- dead sage, or some other knowledgeable entity from another plane. Contacting this otherworldly intelligence can break your mind. When you cast this spell, make a DC 15 Intelligence saving throw. On a successful save, you can ask the entity up to five questions. You must ask your questions before the spell ends. The GM answers each question with one word, such as “yes,” “no,” “maybe,” “never,” “ir- relevant,” or “unclear” (if the entity doesn’t know the answer to the question). If a one-word answer would be misleading, the GM might instead offer a short phrase as an answer. On a failed save, you take 6d6 Psychic damage and have the Incapacitated condition until you finish a Long Rest. A Greater Restoration spell cast on you ends this effect."}
   {
    :name "Contingency"
    :key :contingency
    :school "abjuration"
    :level 6
    :source :srd-2024
    :casting-time "10 Minutes"
    :range "self"
    :duration "10 days"
    :components {}
    :description "Choose a spell of level 5 or lower that you can cast, that has a casting time of an action, and that can target you. You cast that spell—called the contin- gent spell—as part of casting Contingency, expend- ing spell slots for both, but the contingent spell doesn’t come into effect. Instead, it takes effect when a certain trigger occurs. You describe that trigger when you cast the two spells. For example, a Contingency cast with Water Breathing might stip- ulate that Water Breathing comes into effect when you are engulfed in water or a similar liquid. The contingent spell takes effect immediately af- ter the trigger occurs for the first time, whether or not you want it to, and then Contingency ends. The contingent spell takes effect only on you, even if it can normally target others. You can use only one Contingency spell at a time. If you cast this spell again, the effect of another Contingency spell on you ends. Also, Contingency ends on you if its material component is ever not on your person."}
   {
    :name "Counterspell"
    :key :counterspell
    :school "abjuration"
    :level 3
    :source :srd-2024
    :casting-time "Reaction, Which You Take When You See A"
    :range "60 feet"
    :duration "instantaneous"
    :components {}
    :description "You attempt to interrupt a creature in the process of casting a spell. The creature makes a Constitution saving throw. On a failed save, the spell dissipates with no effect, and the action, Bonus Action, or Re- action used to cast it is wasted. If that spell was cast with a spell slot, the slot isn’t expended."}
   {
    :name "Cure Wounds"
    :key :cure-wounds
    :school "abjuration"
    :level 1
    :source :srd-2024
    :casting-time "Action"
    :range "touch"
    :duration "instantaneous"
    :components {}
    :description "A creature you touch regains a number of Hit Points equal to 2d8 plus your spellcasting ability modifier. Using a Higher-Level Spell Slot. The healing in- creases by 2d8 for each spell slot level above 1."}
   {
    :name "Dancing Lights"
    :key :dancing-lights
    :school "illusion"
    :level 0
    :source :srd-2024
    :casting-time "Action"
    :range "120 feet"
    :duration "concentration up to 1 minute"
    :components {}
    :description "You create up to four torch-size lights within range, making them appear as torches, lanterns, or glow- ing orbs that hover for the duration. Alternatively, you combine the four lights into one glowing Me- dium form that is vaguely humanlike. Whichever form you choose, each light sheds Dim Light in a 10- foot radius. As a Bonus Action, you can move the lights up to 60 feet to a space within range. A light must be within 20 feet of another light created by this spell, and a light vanishes if it exceeds the spell’s range."}
   {
    :name "Detect Magic"
    :key :detect-magic
    :school "divination"
    :level 1
    :source :srd-2024
    :casting-time "Action"
    :range "self"
    :duration "concentration up to 10 minutes"
    :components {}
    :ritual true
    :description "For the duration, you sense the presence of magical effects within 30 feet of yourself. If you sense such effects, you can take the Magic action to see a faint aura around any visible creature or object in the area that bears the magic, and if an effect was cre- ated by a spell, you learn the spell’s school of magic. The spell is blocked by 1 foot of stone, dirt, or wood; 1 inch of metal; or a thin sheet of lead."}
   {
    :name "Detect Poison And Disease"
    :key :detect-poison-and-disease
    :school "divination"
    :level 1
    :source :srd-2024
    :casting-time "Action"
    :range "self"
    :duration "concentration up to 10 minutes"
    :components {}
    :ritual true
    :description "For the duration, you sense the location of poisons, poisonous or venomous creatures, and magical contagions within 30 feet of yourself. You sense the kind of poison, creature, or contagion in each case. The spell is blocked by 1 foot of stone, dirt, or wood; 1 inch of metal; or a thin sheet of lead."}
   {
    :name "Dissonant Whispers"
    :key :dissonant-whispers
    :school "enchantment"
    :level 1
    :source :srd-2024
    :casting-time "Action"
    :range "60 feet"
    :duration "instantaneous"
    :components {}
    :description "One creature of your choice that you can see within range hears a discordant melody in its mind. The target makes a Wisdom saving throw. On a failed save, it takes 3d6 Psychic damage and must imme- diately use its Reaction, if available, to move as far away from you as it can, using the safest route. On a successful save, the target takes half as much dam- age only. Using a Higher-Level Spell Slot. The damage in- creases by 1d6 for each spell slot level above 1."}
   {
    :name "Divination"
    :key :divination
    :school "divination"
    :level 4
    :source :srd-2024
    :casting-time "Action"
    :range "self"
    :duration "instantaneous"
    :components {}
    :ritual true
    :description "This spell puts you in contact with a god or a god’s servants. You ask one question about a specific goal, event, or activity to occur within 7 days. The GM of- fers a truthful reply, which might be a short phrase or cryptic rhyme. The spell doesn’t account for cir- cumstances that might change the answer, such as the casting of other spells. If you cast the spell more than once before finish- ing a Long Rest, there is a cumulative 25 percent chance for each casting after the first that you get no answer."}
   {
    :name "Divine Favor"
    :key :divine-favor
    :school "transmutation"
    :level 1
    :source :srd-2024
    :casting-time "Bonus Action"
    :range "self"
    :duration "1 minute"
    :components {}
    :description "Until the spell ends, your attacks with weapons deal an extra 1d4 Radiant damage on a hit."}
   {
    :name "Divine Smite"
    :key :divine-smite
    :school "evocation"
    :level 1
    :source :srd-2024
    :casting-time "Bonus Action, Which You Take Immedi-"
    :range "self"
    :duration "instantaneous"
    :description "The target takes an extra 2d8 Radiant damage from the attack. The damage increases by 1d8 if the tar- get is a Fiend or an Undead. Using a Higher-Level Spell Slot. The damage in- creases by 1d8 for each spell slot level above 1."}
   {
    :name "Dragon’s Breath"
    :key :dragon-s-breath
    :school "transmutation"
    :level 2
    :source :srd-2024
    :casting-time "Bonus Action"
    :range "touch"
    :duration "concentration up to 1 minute"
    :components {}
    :description "You touch one willing creature, and choose Acid, Cold, Fire, Lightning, or Poison. Until the spell ends, the target can take a Magic action to exhale a 15-foot Cone. Each creature in that area makes a Dexterity saving throw, taking 3d6 damage of the chosen type on a failed save or half as much damage on a successful one. Using a Higher-Level Spell Slot. The damage in- creases by 1d6 for each spell slot level above 2."}
   {
    :name "Earthquake"
    :key :earthquake
    :school "transmutation"
    :level 8
    :source :srd-2024
    :casting-time "Action"
    :range "500 feet"
    :duration "concentration up to 1 minute"
    :components {}
    :description "Choose a point on the ground that you can see within range. For the duration, an intense tremor rips through the ground in a 100-foot-radius circle centered on that point. The ground there is Difficult Terrain. When you cast this spell and at the end of each of your turns for the duration, each creature on the ground in the area makes a Dexterity saving throw. On a failed save, a creature has the Prone condition, and its Concentration is broken. You can also cause the effects below. Fissures. A total of 1d6 fissures open in the spell’s area at the end of the turn you cast it. You choose the fissures’ locations, which can’t be under struc- tures. Each fissure is 1d10 × 10 feet deep and 10 feet wide, and it extends from one edge of the spell’s area to another edge. A creature in the same space as a fissure must succeed on a Dexterity saving throw or fall in. A creature that successfully saves moves with the fissure’s edge as it opens. Structures. The tremor deals 50 Bludgeoning damage to any structure in contact with the ground in the area when you cast the spell and at the end of each of your turns until the spell ends. If a structure drops to 0 Hit Points, it collapses. A creature within a distance from a collapsing structure equal to half the structure’s height makes a Dexterity saving throw. On a failed save, the crea- ture takes 12d6 Bludgeoning damage, has the Prone condition, and is buried in the rubble, requiring a DC 20 Strength (Athletics) check as an action to es- cape. On a successful save, the creature takes half as much damage only."}
   {
    :name "Elementalism"
    :key :elementalism
    :school "transmutation"
    :level 0
    :source :srd-2024
    :casting-time "Action"
    :range "30 feet"
    :duration "instantaneous"
    :components {}
    :description "You exert control over the elements, creating one of the following effects within range. Beckon Air. You create a breeze strong enough to ripple cloth, stir dust, rustle leaves, and close open doors and shutters, all in a 5-foot Cube. Doors and shutters being held open by someone or something aren’t affected. Beckon Earth. You create a thin shroud of dust or sand that covers surfaces in a 5-foot-square area, or you cause a single word to appear in your handwrit- ing in a patch of dirt or sand. Beckon Fire. You create a thin cloud of harmless embers and colored, scented smoke in a 5-foot Cube. You choose the color and scent, and the embers can light candles, torches, or lamps in that area. The smoke’s scent lingers for 1 minute. Beckon Water. You create a spray of cool mist that lightly dampens creatures and objects in a 5-foot Cube. Alternatively, you create 1 cup of clean water either in an open container or on a surface, and the water evaporates in 1 minute. Sculpt Element. You cause dirt, sand, fire, smoke, mist, or water that can fit in a 1-foot Cube to assume a crude shape (such as that of a creature) for 1 hour."}
   {
    :name "Ensnaring Strike"
    :key :ensnaring-strike
    :school "conjuration"
    :level 1
    :source :srd-2024
    :casting-time "Bonus Action, Which You Take Immedi-"
    :range "self"
    :duration "concentration up to 1 minute"
    :components {}
    :description "As you hit the target, grasping vines appear on it, and it makes a Strength saving throw. A Large or larger creature has Advantage on this save. On a failed save, the target has the Restrained condition until the spell ends. On a successful save, the vines shrivel away, and the spell ends. While Restrained, the target takes 1d6 Piercing damage at the start of each of its turns. The target or a creature within reach of it can take an action to make a Strength (Athletics) check against your spell save DC. On a success, the spell ends. Using a Higher-Level Spell Slot. The damage in- creases by 1d6 for each spell slot level above 1."}
   {
    :name "Enthrall"
    :key :enthrall
    :school "enchantment"
    :level 2
    :source :srd-2024
    :casting-time "Action"
    :range "60 feet"
    :duration "concentration up to 1 minute"
    :components {}
    :description "You weave a distracting string of words, causing creatures of your choice that you can see within range to make a Wisdom saving throw. Any crea- ture you or your companions are fighting automati- cally succeeds on this save. On a failed save, a target has a −10 penalty to Wisdom (Perception) checks and Passive Perception until the spell ends."}
   {
    :name "Etherealness"
    :key :etherealness
    :school "conjuration"
    :level 7
    :source :srd-2024
    :casting-time "Action"
    :range "self"
    :duration "up to 8 hours"
    :components {}
    :description "You step into the border regions of the Ethereal Plane, where it overlaps with your current plane. You remain in the Border Ethereal for the duration. During this time, you can move in any direction. If you move up or down, every foot of movement costs an extra foot. You can perceive the plane you left, which looks gray, and you can’t see anything there more than 60 feet away. While on the Ethereal Plane, you can affect and be affected only by creatures, objects, and effects on that plane. Creatures that aren’t on the Ethereal Plane can’t perceive or interact with you unless a feature gives them the ability to do so. When the spell ends, you return to the plane you left in the spot that corresponds to your space in the Border Ethereal. If you appear in an occupied space, you are shunted to the nearest unoccupied space and take Force damage equal to twice the number of feet you are moved. This spell ends instantly if you cast it while you are on the Ethereal Plane or a plane that doesn’t border it, such as one of the Outer Planes. Using a Higher-Level Spell Slot. You can target up to three willing creatures (including yourself) for each spell slot level above 7. The creatures must be within 10 feet of you when you cast the spell."}
   {
    :name "False Life"
    :key :false-life
    :school "necromancy"
    :level 1
    :source :srd-2024
    :casting-time "Action"
    :range "self"
    :duration "instantaneous"
    :components {}
    :description "You gain 2d4 + 4 Temporary Hit Points. Using a Higher-Level Spell Slot. You gain 5 addi- tional Temporary Hit Points for each spell slot level above 1."}
   {
    :name "Feather Fall"
    :key :feather-fall
    :school "transmutation"
    :level 1
    :source :srd-2024
    :casting-time "Reaction, Which You Take When You Or A"
    :range "60 feet"
    :duration "1 minute"
    :components {}
    :description "Choose up to five falling creatures within range. A falling creature’s rate of descent slows to 60 feet per round until the spell ends. If a creature lands before the spell ends, the creature takes no damage from the fall, and the spell ends for that creature."}
   {
    :name "Find Familiar"
    :key :find-familiar
    :school "conjuration"
    :level 1
    :source :srd-2024
    :casting-time "1 Hour"
    :range "10 feet"
    :duration "instantaneous"
    :components {}
    :ritual true
    :description "You gain the service of a familiar, a spirit that takes an animal form you choose: Bat, Cat, Frog, Hawk, Lizard, Octopus, Owl, Rat, Raven, Spider, Weasel, or another Beast that has a Challenge Rating of 0. Appearing in an unoccupied space within range, the familiar has the statistics of the chosen form (see “Monsters”), though it is a Celestial, Fey, or Fiend (your choice) instead of a Beast. Your familiar acts independently of you, but it obeys your commands. Telepathic Connection. While your familiar is within 100 feet of you, you can communicate with it telepathically. Additionally, as a Bonus Action, you can see through the familiar’s eyes and hear what it hears until the start of your next turn, gaining the benefits of any special senses it has. Finally, when you cast a spell with a range of touch, your familiar can deliver the touch. Your fa- miliar must be within 100 feet of you, and it must take a Reaction to deliver the touch when you cast the spell. Combat. The familiar is an ally to you and your allies. It rolls its own Initiative and acts on its own turn. A familiar can’t attack, but it can take other actions as normal. Disappearance of the Familiar. When the famil- iar drops to 0 Hit Points, it disappears. It reappears after you cast this spell again. As a Magic action, you can temporarily dismiss the familiar to a pocket dimension. Alternatively, you can dismiss it forever. As a Magic action while it is temporarily dismissed, you can cause it to reappear in an unoccupied space within 30 feet of you. Whenever the familiar drops to 0 Hit Points or disappears into the pocket dimen- sion, it leaves behind in its space anything it was wearing or carrying. One Familiar Only. You can’t have more than one familiar at a time. If you cast this spell while you have a familiar, you instead cause it to adopt a new eligible form."}
   {
    :name "Find Steed"
    :key :find-steed
    :school "conjuration"
    :level 2
    :source :srd-2024
    :casting-time "Action"
    :range "30 feet"
    :duration "instantaneous"
    :description "You summon an otherworldly being that appears as a loyal steed in an unoccupied space of your choice within range. This creature uses the Otherworldly Steed stat block. If you already have a steed from this spell, the steed is replaced by the new one. The steed resembles a Large, rideable animal of your choice, such as a horse, a camel, a dire wolf, or an elk. Whenever you cast the spell, choose the steed’s creature type—Celestial, Fey, or Fiend— which determines certain traits in the stat block. Combat. The steed is an ally to you and your al- lies. In combat, it shares your Initiative count, and it functions as a controlled mount while you ride it (as defined in the rules on mounted combat). If you have the Incapacitated condition, the steed takes its turn immediately after yours and acts inde- pendently, focusing on protecting you. Disappearance of the Steed. The steed disap- pears if it drops to 0 Hit Points or if you die. When it disappears, it leaves behind anything it was wearing or carrying. If you cast this spell again, you decide whether you summon the steed that disap- peared or a different one. Using a Higher-Level Spell Slot. Use the spell slot’s level for the spell’s level in the stat block. face a choice of paths along the way there, you know which path is the most direct."}
   {
    :name "Floating Disk"
    :key :floating-disk
    :school "conjuration"
    :level 1
    :source :srd-2024
    :casting-time "Action"
    :range "30 feet"
    :duration "1 hour"
    :components {}
    :ritual true
    :description "This spell creates a circular, horizontal plane of force, 3 feet in diameter and 1 inch thick, that floats 3 feet above the ground in an unoccupied space of your choice that you can see within range. The disk remains for the duration and can hold up to 500 pounds. If more weight is placed on it, the spell ends, and everything on the disk falls to the ground. The disk is immobile while you are within 20 feet of it. If you move more than 20 feet away from it, the disk follows you so that it remains within 20 feet of you. It can move across uneven terrain, up or down stairs, slopes and the like, but it can’t cross an ele- vation change of 10 feet or more. For example, the disk can’t move across a 10-foot-deep pit, nor could it leave such a pit if it was created at the bottom. If you move more than 100 feet from the disk (typ- ically because it can’t move around an obstacle to follow you), the spell ends."}
   {
    :name "Forbiddance"
    :key :forbiddance
    :school "abjuration"
    :level 6
    :source :srd-2024
    :casting-time "10 Minutes"
    :range "touch"
    :duration "1 day"
    :components {}
    :ritual true
    :description "You create a ward against magical travel that pro- tects up to 40,000 square feet of floor space to a height of 30 feet above the floor. For the duration, creatures can’t teleport into the area or use portals, such as those created by the Gate spell, to enter the area. The spell proofs the area against planar travel, and therefore prevents creatures from accessing the area by way of the Astral Plane, the Ethereal Plane, the Feywild, the Shadowfell, or the Plane Shift spell. In addition, the spell damages types of creatures that you choose when you cast it. Choose one or more of the following: Aberrations, Celestials, Ele- mentals, Fey, Fiends, and Undead. When a creature of a chosen type enters the spell’s area for the first time on a turn or ends its turn there, the creature takes 5d10 Radiant or Necrotic damage (your choice when you cast this spell). You can designate a password when you cast the spell. A creature that speaks the password as it en- ters the area takes no damage from the spell. The spell’s area can’t overlap with the area of another Forbiddance spell. If you cast Forbiddance every day for 30 days in the same location, the spell lasts until it is dispelled, and the Material compo- nents are consumed on the last casting."}
   {
    :name "Forcecage"
    :key :forcecage
    :school "evocation"
    :level 7
    :source :srd-2024
    :casting-time "Action"
    :range "100 feet"
    :duration "concentration up to 1 hour"
    :components {}
    :description "An immobile, Invisible, Cube-shaped prison com- posed of magical force springs into existence around an area you choose within range. The prison can be a cage or a solid box, as you choose. A prison in the shape of a cage can be up to 20 feet on a side and is made from 1/2-inch diameter bars spaced 1/2 inch apart. A prison in the shape of a box can be up to 10 feet on a side, creating a solid bar- rier that prevents any matter from passing through it and blocking any spells cast into or out from the area. When you cast the spell, any creature that is com- pletely inside the cage’s area is trapped. Creatures only partially within the area, or those too large to fit inside it, are pushed away from the center of the area until they are completely outside it. A creature inside the cage can’t leave it by non- magical means. If the creature tries to use telepor- tation or interplanar travel to leave, it must first make a Charisma saving throw. On a successful save, the creature can use that magic to exit the cage. On a failed save, the creature doesn’t exit the cage and wastes the spell or effect. The cage also extends into the Ethereal Plane, blocking ethereal travel. This spell can’t be dispelled by Dispel Magic."}
   {
    :name "Gentle Repose"
    :key :gentle-repose
    :school "necromancy"
    :level 2
    :source :srd-2024
    :casting-time "Action"
    :range "touch"
    :duration "10 days"
    :components {}
    :ritual true
    :description "You touch a corpse or other remains. For the dura- tion, the target is protected from decay and can’t become Undead. The spell also effectively extends the time limit on raising the target from the dead, since days spent under the influence of this spell don’t count against the time limit of spells such as Raise Dead."}
   {
    :name "Giant Insect"
    :key :giant-insect
    :school "conjuration"
    :level 4
    :source :srd-2024
    :casting-time "Action"
    :range "60 feet"
    :duration "concentration up to 10 minutes"
    :components {}
    :description "You summon a giant centipede, spider, or wasp (cho- sen when you cast the spell). It manifests in an un- occupied space you can see within range and uses the Giant Insect stat block. The form you choose determines certain details in its stat block. The creature disappears when it drops to 0 Hit Points or when the spell ends. The creature is an ally to you and your allies. In combat, the creature shares your Initiative count, but it takes its turn immediately after yours. It obeys your verbal commands (no action required by you). If you don’t issue any, it takes the Dodge action and uses its movement to avoid danger. Using a Higher-Level Spell Slot. Use the spell slot’s level for the spell’s level in the stat block. Giant Insect Large Beast, Unaligned AC 11 + the spell’s level HP 30 + 10 for each spell level above 4 Speed 40 ft., Climb 40 ft., Fly 40 ft. (Wasp only) MOD SAVE MOD SAVE MOD SAVE Str 17 +3 +3 Dex 13 +1 +1 Con 15 +2 +2 Int 4 −3 −3 Wis 14 +2 +2 Cha 3 −4 −4 Senses Darkvision 60 ft.; Passive Perception 12 Languages Understands the languages you know CR None (XP 0; PB equals your Proficiency Bonus) Traits Spider Climb. The insect can climb difficult surfaces, including along ceilings, without needing to make an ability check. Actions Multiattack. The insect makes a number of attacks equal to half this spell’s level (round down). Poison Jab. Melee Attack Roll: Bonus equals your spell attack modifier, reach 10 ft. Hit: 1d6 + 3 plus the spell’s level Piercing damage plus 1d4 Poison damage. Web Bolt (Spider Only). Ranged Attack Roll: Bonus equals your spell attack modifier, range 60 ft. Hit: 1d10 + 3 plus the spell’s level Bludgeoning damage, and the target’s Speed is reduced to 0 until the start of the in- sect’s next turn. Bonus Actions Venomous Spew (Centipede Only). Constitution Saving Throw: Your spell save DC, one creature the insect can see within 10 feet. Failure: The target has the Poisoned condition until the start of the insect’s next turn."}
   {
    :name "Glibness"
    :key :glibness
    :school "enchantment"
    :level 8
    :source :srd-2024
    :casting-time "Action"
    :range "self"
    :duration "1 hour"
    :components {}
    :description "Until the spell ends, when you make a Charisma check, you can replace the number you roll with a 15. Additionally, no matter what you say, magic that would determine if you are telling the truth indi- cates that you are being truthful."}
   {
    :name "Goodberry"
    :key :goodberry
    :school "conjuration"
    :level 1
    :source :srd-2024
    :casting-time "Action"
    :range "self"
    :duration "24 hours"
    :components {}
    :description "Ten berries appear in your hand and are infused with magic for the duration. A creature can take a Bonus Action to eat one berry. Eating a berry re- stores 1 Hit Point, and the berry provides enough nourishment to sustain a creature for one day. Uneaten berries disappear when the spell ends."}
   {
    :name "Guards And Wards"
    :key :guards-and-wards
    :school "abjuration"
    :level 6
    :source :srd-2024
    :casting-time "1 Hour"
    :range "touch"
    :duration "24 hours"
    :components {}
    :description "You create a ward that protects up to 2,500 square feet of floor space. The warded area can be up to 20 feet tall, and you shape it as one 50-foot square, one hundred 5-foot squares that are contiguous, or twenty-five 10-foot squares that are contiguous. When you cast this spell, you can specify individu- als that are unaffected by the spell’s effects. You can also specify a password that, when spoken aloud within 5 feet of the warded area, makes the speaker immune to its effects. The spell creates the effects below within the warded area. Dispel Magic has no effect on Guards and Wards itself, but each of the following effects can be dispelled. If all four are dispelled, Guards and Wards ends. If you cast the spell every day for 365 days on the same area, the spell thereafter lasts un- til all its effects are dispelled. Corridors. Fog fills all the warded corridors, making them Heavily Obscured. In addition, at each intersection or branching passage offering a choice of direction, there is a 50 percent chance that a creature other than you believes it is going in the opposite direction from the one it chooses. Doors. All doors in the warded area are magically locked, as if sealed by the Arcane Lock spell. In addi- tion, you can cover up to ten doors with an illusion to make them appear as plain sections of wall. Stairs. Webs fill all stairs in the warded area from top to bottom, as in the Web spell. These strands regrow in 10 minutes if they are destroyed while Guards and Wards lasts. Other Spell Effect. Place one of the following mag- ical effects within the warded area: • Dancing Lights in four corridors, with a simple program that the lights repeat as long as Guards and Wards lasts • Magic Mouth in two locations • Stinking Cloud in two locations (the vapors return within 10 minutes if dispersed while Guards and Wards lasts) • Gust of Wind in one corridor or room (the wind blows continuously while the spell lasts) • Suggestion in one 5-foot square; any creature that enters that square receives the suggestion mentally"}
   {
    :name "Hallow"
    :key :hallow
    :school "abjuration"
    :level 5
    :source :srd-2024
    :casting-time "24 Hours"
    :range "touch"
    :duration "until dispelled"
    :components {}
    :description "You touch a point and infuse an area around it with holy or unholy power. The area can have a radius up to 60 feet, and the spell fails if the radius includes an area already under the effect of Hallow. The af- fected area has the following effects. Hallowed Ward. Choose any of these creature types: Aberration, Celestial, Elemental, Fey, Fiend, or Undead. Creatures of the chosen types can’t willingly enter the area, and any creature that is possessed by or that has the Charmed or Fright- ened condition from such creatures isn’t possessed, Charmed, or Frightened by them while in the area. Extra Effect. You bind an extra effect to the area from the list below: Courage. Creatures of any types you choose can’t gain the Frightened condition while in the area. Darkness. Darkness fills the area. Normal light, as well as magical light created by spells of a level lower than this spell, can’t illuminate the area. Daylight. Bright light fills the area. Magical Dark- ness created by spells of a level lower than this spell can’t extinguish the light. Peaceful Rest. Dead bodies interred in the area can’t be turned into Undead. Extradimensional Interference. Creatures of any types you choose can’t enter or exit the area using teleportation or interplanar travel. Fear. Creatures of any types you choose have the Frightened condition while in the area. Resistance. Creatures of any types you choose have Resistance to one damage type of your choice while in the area. Silence. No sound can emanate from within the area, and no sound can reach into it. Tongues. Creatures of any types you choose can communicate with any other creature in the area even if they don’t share a common language. Vulnerability. Creatures of any types you choose have Vulnerability to one damage type of your choice while in the area."}
   {
    :name "Heal"
    :key :heal
    :school "abjuration"
    :level 6
    :source :srd-2024
    :casting-time "Action"
    :range "60 feet"
    :duration "instantaneous"
    :components {}
    :description "Choose a creature that you can see within range. Positive energy washes through the target, restor- ing 70 Hit Points. This spell also ends the Blinded, Deafened, and Poisoned conditions on the target. Using a Higher-Level Spell Slot. The healing in- creases by 10 for each spell slot level above 6."}
   {
    :name "Healing Word"
    :key :healing-word
    :school "abjuration"
    :level 1
    :source :srd-2024
    :casting-time "Bonus Action"
    :range "60 feet"
    :duration "instantaneous"
    :components {}
    :description "A creature of your choice that you can see within range regains Hit Points equal to 2d4 plus your spellcasting ability modifier. Using a Higher-Level Spell Slot. The healing in- creases by 2d4 for each spell slot level above 1."}
   {
    :name "Hellish Rebuke"
    :key :hellish-rebuke
    :school "evocation"
    :level 1
    :source :srd-2024
    :casting-time "Reaction, Which You Take In Response To"
    :range "60 feet"
    :duration "instantaneous"
    :components {}
    :description "The creature that damaged you is momentarily sur- rounded by green flames. It makes a Dexterity sav- ing throw, taking 2d10 Fire damage on a failed save or half as much damage on a successful one. Using a Higher-Level Spell Slot. The damage in- creases by 1d10 for each spell slot level above 1."}
   {
    :name "Heroes’ Feast"
    :key :heroes-feast
    :school "conjuration"
    :level 6
    :source :srd-2024
    :casting-time "10 Minutes"
    :range "self"
    :duration "instantaneous"
    :components {}
    :description "You conjure a feast that appears on a surface in an unoccupied 10-foot Cube next to you. The feast takes 1 hour to consume and disappears at the end of that time, and the beneficial effects don’t set in until this hour is over. Up to twelve creatures can partake of the feast. A creature that partakes gains several benefits, which last for 24 hours. The creature has Resis- tance to Poison damage, and it has Immunity to the Frightened and Poisoned conditions. Its Hit Point maximum also increases by 2d10, and it gains the same number of Hit Points."}
   {
    :name "Hex"
    :key :hex
    :school "enchantment"
    :level 1
    :source :srd-2024
    :casting-time "Bonus Action"
    :range "90 feet"
    :duration "concentration up to 1 hour"
    :components {}
    :description "You place a curse on a creature that you can see within range. Until the spell ends, you deal an extra 1d6 Necrotic damage to the target whenever you hit it with an attack roll. Also, choose one ability when you cast the spell. The target has Disadvantage on ability checks made with the chosen ability. If the target drops to 0 Hit Points before this spell ends, you can take a Bonus Action on a later turn to curse a new creature. Using a Higher-Level Spell Slot. Your Concentra- tion can last longer with a spell slot of level 2 (up to 4 hours), 3-4 (up to 8 hours), or 5+ (24 hours)."}
   {
    :name "Ice Knife"
    :key :ice-knife
    :school "conjuration"
    :level 1
    :source :srd-2024
    :casting-time "Action"
    :range "60 feet"
    :duration "instantaneous"
    :components {}
    :description "You create a shard of ice and fling it at one creature within range. Make a ranged spell attack against the target. On a hit, the target takes 1d10 Piercing damage. Hit or miss, the shard then explodes. The target and each creature within 5 feet of it must succeed on a Dexterity saving throw or take 2d6 Cold damage. Using a Higher-Level Spell Slot. The Cold damage increases by 1d6 for each spell slot level above 1."}
   {
    :name "Identify"
    :key :identify
    :school "divination"
    :level 1
    :source :srd-2024
    :casting-time "1 Minute"
    :range "touch"
    :duration "instantaneous"
    :components {}
    :ritual true
    :description "You touch an object throughout the spell’s casting. If the object is a magic item or some other magi- cal object, you learn its properties and how to use them, whether it requires Attunement, and how many charges it has, if any. You learn whether any ongoing spells are affecting the item and what they are. If the item was created by a spell, you learn that spell’s name. If you instead touch a creature throughout the casting, you learn which ongoing spells, if any, are currently affecting it."}
   {
    :name "Illusory Script"
    :key :illusory-script
    :school "illusion"
    :level 1
    :source :srd-2024
    :casting-time "1 Minute"
    :range "touch"
    :duration "10 days"
    :components {}
    :ritual true
    :description "You write on parchment, paper, or another suitable material and imbue it with an illusion that lasts for the duration. To you and any creatures you desig- nate when you cast the spell, the writing appears normal, seems to be written in your hand, and conveys whatever meaning you intended when you wrote the text. To all others, the writing appears as if it were written in an unknown or magical script that is unintelligible. Alternatively, the illusion can alter the meaning, handwriting, and language of the text, though the language must be one you know. If the spell is dispelled, the original script and the illusion both disappear. A creature that has Truesight can read the hidden message."}
   {
    :name "Instant Summons"
    :key :instant-summons
    :school "conjuration"
    :level 6
    :source :srd-2024
    :casting-time "1 Minute"
    :range "touch"
    :duration "until dispelled"
    :components {}
    :ritual true
    :description "You touch the sapphire used in the casting and an object weighing 10 pounds or less whose longest dimension is 6 feet or less. The spell leaves an Invis- ible mark on that object and invisibly inscribes the object’s name on the sapphire. Each time you cast this spell, you must use a different sapphire. Thereafter, you can take a Magic action to speak the object’s name and crush the sapphire. The ob- ject instantly appears in your hand regardless of physical or planar distances, and the spell ends. If another creature is holding or carrying the ob- ject, crushing the sapphire doesn’t transport it, but instead you learn who that creature is and where that creature is currently located."}
   {
    :name "Jump"
    :key :jump
    :school "transmutation"
    :level 1
    :source :srd-2024
    :casting-time "Bonus Action"
    :range "touch"
    :duration "1 minute"
    :description "You touch a willing creature. Once on each of its turns until the spell ends, that creature can jump up to 30 feet by spending 10 feet of movement. Using a Higher-Level Spell Slot. You can target one additional creature for each spell slot level above 1."}
   {
    :name "Lesser Restoration"
    :key :lesser-restoration
    :school "abjuration"
    :level 2
    :source :srd-2024
    :casting-time "Bonus Action"
    :range "touch"
    :duration "instantaneous"
    :components {}
    :description "You touch a creature and end one condition on it: Blinded, Deafened, Paralyzed, or Poisoned."}
   {
    :name "Locate Animals Or Plants"
    :key :locate-animals-or-plants
    :school "divination"
    :level 2
    :source :srd-2024
    :casting-time "Action"
    :range "self"
    :duration "instantaneous"
    :components {}
    :ritual true
    :description "Describe or name a specific kind of Beast, Plant creature, or nonmagical plant. You learn the direc- tion and distance to the closest creature or plant of that kind within 5 miles, if any are present."}
   {
    :name "Magic Mouth"
    :key :magic-mouth
    :school "illusion"
    :level 2
    :source :srd-2024
    :casting-time "1 Minute"
    :range "30 feet"
    :duration "until dispelled"
    :components {}
    :ritual true
    :description "You implant a message within an object in range—a message that is uttered when a trigger condition is met. Choose an object that you can see and that isn’t being worn or carried by another creature. Then speak the message, which must be 25 words or fewer, though it can be delivered over as long as 10 minutes. Finally, determine the circumstance that will trigger the spell to deliver your message. When that trigger occurs, a magical mouth ap- pears on the object and recites the message in your voice and at the same volume you spoke. If the ob- ject you chose has a mouth or something that looks like a mouth (for example, the mouth of a statue), the magical mouth appears there, so the words appear to come from the object’s mouth. When you cast this spell, you can have the spell end after it delivers its message, or it can remain and repeat its message whenever the trigger occurs. The trigger can be as general or as detailed as you like, though it must be based on visual or audible conditions that occur within 30 feet of the object. For example, you could instruct the mouth to speak when any creature moves within 30 feet of the ob- ject or when a silver bell rings within 30 feet of it."}
   {
    :name "Mass Cure Wounds"
    :key :mass-cure-wounds
    :school "abjuration"
    :level 5
    :source :srd-2024
    :casting-time "Action"
    :range "60 feet"
    :duration "instantaneous"
    :components {}
    :description "A wave of healing energy washes out from a point you can see within range. Choose up to six crea- tures in a 30-foot-radius Sphere centered on that point. Each target regains Hit Points equal to 5d8 plus your spellcasting ability modifier. Using a Higher-Level Spell Slot. The healing in- creases by 1d8 for each spell slot level above 5."}
   {
    :name "Mass Heal"
    :key :mass-heal
    :school "abjuration"
    :level 9
    :source :srd-2024
    :casting-time "Action"
    :range "60 feet"
    :duration "instantaneous"
    :components {}
    :description "A flood of healing energy flows from you into creatures around you. You restore up to 700 Hit Points, divided as you choose among any number of creatures that you can see within range. Creatures healed by this spell also have the Blinded, Deafened, and Poisoned conditions removed from them."}
   {
    :name "Mass Healing Word"
    :key :mass-healing-word
    :school "abjuration"
    :level 3
    :source :srd-2024
    :casting-time "Bonus Action"
    :range "60 feet"
    :duration "instantaneous"
    :components {}
    :description "Up to six creatures of your choice that you can see within range regain Hit Points equal to 2d4 plus your spellcasting ability modifier. Using a Higher-Level Spell Slot. The healing in- creases by 1d4 for each spell slot level above 3."}
   {
    :name "Meld Into Stone"
    :key :meld-into-stone
    :school "transmutation"
    :level 3
    :source :srd-2024
    :casting-time "Action"
    :range "touch"
    :duration "8 hours"
    :components {}
    :ritual true
    :description "You step into a stone object or surface large enough to fully contain your body, merging yourself and your equipment with the stone for the duration. You must touch the stone to do so. Nothing of your presence remains visible or otherwise detectable by nonmagical senses. While merged with the stone, you can’t see what occurs outside it, and any Wisdom (Perception) checks you make to hear sounds outside it are made with Disadvantage. You remain aware of the pas- sage of time and can cast spells on yourself while merged in the stone. You can use 5 feet of movement to leave the stone where you entered it, which ends the spell. You otherwise can’t move. Minor physical damage to the stone doesn’t harm you, but its partial destruction or a change in its shape (to the extent that you no longer fit within it) expels you and deals 6d6 Force damage to you. The stone’s complete destruction (or transmutation into a different substance) expels you and deals 50 Force damage to you. If expelled, you move into an unoc- cupied space closest to where you first entered and have the Prone condition."}
   {
    :name "Mind Spike"
    :key :mind-spike
    :school "divination"
    :level 2
    :source :srd-2024
    :casting-time "Action"
    :range "120 feet"
    :duration "concentration up to 1 hour"
    :components {}
    :description "You drive a spike of psionic energy into the mind of one creature you can see within range. The target makes a Wisdom saving throw, taking 3d8 Psychic damage on a failed save or half as much damage on a successful one. On a failed save, you also always know the target’s location until the spell ends, but only while the two of you are on the same plane of existence. While you have this knowledge, the target can’t become hidden from you, and if it has the Invisible condition, it gains no benefit from that condition against you. Using a Higher-Level Spell Slot. The damage in- creases by 1d8 for each spell slot level above 2."}
   {
    :name "Phantasmal Force"
    :key :phantasmal-force
    :school "illusion"
    :level 2
    :source :srd-2024
    :casting-time "Action"
    :range "60 feet"
    :duration "concentration up to 1 minute"
    :components {}
    :description "You attempt to craft an illusion in the mind of a creature you can see within range. The target makes an Intelligence saving throw. On a failed save, you create a phantasmal object, creature, or other phenomenon that is no larger than a 10-foot Cube and that is perceivable only to the target for the duration. The phantasm includes sound, tem- perature, and other stimuli. The target can take a Study action to examine the phantasm with an Intelligence (Investigation) check against your spell save DC. If the check succeeds, the target realizes that the phantasm is an illusion, and the spell ends. While affected by the spell, the target treats the phantasm as if it were real and rationalizes any il- logical outcomes from interacting with it. For exam- ple, if the target steps through a phantasmal bridge and survives the fall, it believes the bridge exists and something else caused it to fall. An affected target can even take damage from the illusion if the phantasm represents a dangerous creature or hazard. On each of your turns, such a phantasm can deal 2d8 Psychic damage to the tar- get if it is in the phantasm’s area or within 5 feet of the phantasm. The target perceives the damage as a type appropriate to the illusion."}
   {
    :name "Phantom Steed"
    :key :phantom-steed
    :school "illusion"
    :level 3
    :source :srd-2024
    :casting-time "1 Minute"
    :range "30 feet"
    :duration "1 hour"
    :components {}
    :ritual true
    :description "A Large, quasi-real, horselike creature appears on the ground in an unoccupied space of your choice within range. You decide the creature’s appearance, and it is equipped with a saddle, bit, and bridle. Any of the equipment created by the spell vanishes in a puff of smoke if it is carried more than 10 feet away from the steed. For the duration, you or a creature you choose can ride the steed. The steed uses the Riding Horse stat block (see “Monsters”), except it has a Speed of 100 feet and can travel 13 miles in an hour. When the spell ends, the steed gradually fades, giving the rider 1 minute to dismount. The spell ends early if the steed takes any damage."}
   {
    :name "Plant Growth"
    :key :plant-growth
    :school "transmutation"
    :level 3
    :source :srd-2024
    :casting-time "Action (Overgrowth) Or"
    :range "150 feet"
    :duration "instantaneous"
    :components {}
    :description "This spell channels vitality into plants. The casting time you use determines whether the spell has the Overgrowth or the Enrichment effect below. Overgrowth. Choose a point within range. All normal plants in a 100-foot-radius Sphere centered on that point become thick and overgrown. A crea- ture moving through that area must spend 4 feet of movement for every 1 foot it moves. You can exclude one or more areas of any size within the spell’s area from being affected. Enrichment. All plants in a half-mile radius cen- tered on a point within range become enriched for 365 days. The plants yield twice the normal amount of food when harvested. They can benefit from only one Plant Growth per year."}
   {
    :name "Poison Spray"
    :key :poison-spray
    :school "necromancy"
    :level 0
    :source :srd-2024
    :casting-time "Action"
    :range "30 feet"
    :duration "instantaneous"
    :components {}
    :description "You spray toxic mist at a creature within range. Make a ranged spell attack against the target. On a hit, the target takes 1d12 Poison damage. Cantrip Upgrade. The damage increases by 1d12 when you reach levels 5 (2d12), 11 (3d12), and 17 (4d12)."}
   {
    :name "Power Word Heal"
    :key :power-word-heal
    :school "enchantment"
    :level 9
    :source :srd-2024
    :casting-time "Action"
    :range "60 feet"
    :duration "instantaneous"
    :description "A wave of healing energy washes over one creature you can see within range. The target regains all its Hit Points. If the creature has the Charmed, Fright- ened, Paralyzed, Poisoned, or Stunned condition, the condition ends. If the creature has the Prone condition, it can use its Reaction to stand up."}
   {
    :name "Prayer Of Healing"
    :key :prayer-of-healing
    :school "abjuration"
    :level 2
    :source :srd-2024
    :casting-time "10 Minutes"
    :range "30 feet"
    :duration "instantaneous"
    :components {}
    :description "Up to five creatures of your choice who remain within range for the spell’s entire casting gain the benefits of a Short Rest and also regain 2d8 Hit Points. A creature can’t be affected by this spell again until that creature finishes a Long Rest. Using a Higher-Level Spell Slot. The healing in- creases by 1d8 for each spell slot level above 2."}
   {
    :name "Produce Flame"
    :key :produce-flame
    :school "conjuration"
    :level 0
    :source :srd-2024
    :casting-time "Bonus Action"
    :range "self"
    :duration "10 minutes"
    :components {}
    :description "A flickering flame appears in your hand and re- mains there for the duration. While there, the flame emits no heat and ignites nothing, and it sheds Bright Light in a 20-foot radius and Dim Light for an additional 20 feet. The spell ends if you cast it again. Until the spell ends, you can take a Magic action to hurl fire at a creature or an object within 60 feet of you. Make a ranged spell attack. On a hit, the target takes 1d8 Fire damage. Cantrip Upgrade. The damage increases by 1d8 when you reach levels 5 (2d8), 11 (3d8), and 17 (4d8)."}
   {
    :name "Purify Food And Drink"
    :key :purify-food-and-drink
    :school "transmutation"
    :level 1
    :source :srd-2024
    :casting-time "Action"
    :range "10 feet"
    :duration "instantaneous"
    :components {}
    :ritual true
    :description "You remove poison and rot from nonmagical food and drink in a 5-foot-radius Sphere centered on a point within range."}
   {
    :name "Ray Of Sickness"
    :key :ray-of-sickness
    :school "necromancy"
    :level 1
    :source :srd-2024
    :casting-time "Action"
    :range "60 feet"
    :duration "instantaneous"
    :components {}
    :description "You shoot a greenish ray at a creature within range. Make a ranged spell attack against the target. On a hit, the target takes 2d8 Poison damage and has the Poisoned condition until the end of your next turn. Using a Higher-Level Spell Slot. The damage in- creases by 1d8 for each spell slot level above 1."}
   {
    :name "Reincarnate"
    :key :reincarnate
    :school "necromancy"
    :level 5
    :source :srd-2024
    :casting-time "1 Hour"
    :range "touch"
    :duration "instantaneous"
    :components {}
    :description "You touch a dead Humanoid or a piece of one. If the creature has been dead no longer than 10 days, the spell forms a new body for it and calls the soul to enter that body. Roll 1d10 and consult the table below to determine the body’s species, or the GM chooses another playable species. 1d10 Species 1d10 Species 1 Roll again. 6 Goliath 2 Dragonborn 7 Halfling 3 Dwarf 8 Human 4 Elf 9 Orc 5 Gnome 10 Tiefling The reincarnated creature makes any choices that a species’ description offers, and the creature re- calls its former life. It retains the capabilities it had in its original form, except it loses the traits of its previous species and gains the traits of its new one."}
   {
    :name "Resilient Sphere"
    :key :resilient-sphere
    :school "abjuration"
    :level 4
    :source :srd-2024
    :casting-time "Action"
    :range "30 feet"
    :duration "concentration up to 1 minute"
    :components {}
    :description "A shimmering sphere encloses a Large or smaller creature or object within range. An unwilling crea- ture must succeed on a Dexterity saving throw or be enclosed for the duration. Nothing—not physical objects, energy, or other spell effects—can pass through the barrier, in or out, though a creature in the sphere can breathe there. The sphere is immune to all damage, and a creature or object inside can’t be damaged by at- tacks or effects originating from outside, nor can a creature inside the sphere damage anything outside it. The sphere is weightless and just large enough to contain the creature or object inside. An enclosed creature can take an action to push against the sphere’s walls and thus roll the sphere at up to half the creature’s Speed. Similarly, the globe can be picked up and moved by other creatures. A Disintegrate spell targeting the globe destroys it without harming anything inside."}
   {
    :name "Searing Smite"
    :key :searing-smite
    :school "evocation"
    :level 1
    :source :srd-2024
    :casting-time "Bonus Action, Which You Take Immedi-"
    :range "self"
    :duration "1 minute"
    :description "As you hit the target, it takes an extra 1d6 Fire dam- age from the attack. At the start of each of its turns until the spell ends, the target takes 1d6 Fire dam- age and then makes a Constitution saving throw. On a failed save, the spell continues. On a successful save, the spell ends. Using a Higher-Level Spell Slot. All the damage increases by 1d6 for each spell slot level above 1."}
   {
    :name "Sending"
    :key :sending
    :school "divination"
    :level 3
    :source :srd-2024
    :casting-time "Action"
    :range "unlimited"
    :duration "instantaneous"
    :components {}
    :description "You send a short message of 25 words or fewer to a creature you have met or a creature described to you by someone who has met it. The target hears the message in its mind, recognizes you as the sender if it knows you, and can answer in a like manner immediately. The spell enables targets to understand the meaning of your message. You can send the message across any distance and even to other planes of existence, but if the target is on a different plane than you, there is a 5 percent chance that the message doesn’t arrive. You know if the delivery fails. Upon receiving your message, a creature can block your ability to reach it again with this spell for 8 hours. If you try to send another message during that time, you learn that you are blocked, and the spell fails."}
   {
    :name "Shield"
    :key :shield
    :school "abjuration"
    :level 1
    :source :srd-2024
    :casting-time "Reaction, Which You Take When You Are"
    :range "self"
    :duration "1 round"
    :components {}
    :description "An imperceptible barrier of magical force protects you. Until the start of your next turn, you have a +5 bonus to AC, including against the triggering attack, and you take no damage from Magic Missile."}
   {
    :name "Shillelagh"
    :key :shillelagh
    :school "transmutation"
    :level 0
    :source :srd-2024
    :casting-time "Bonus Action"
    :range "self"
    :duration "1 minute"
    :components {}
    :description "A Club or Quarterstaff you are holding is imbued with nature’s power. For the duration, you can use your spellcasting ability instead of Strength for the attack and damage rolls of melee attacks using that weapon, and the weapon’s damage die becomes a d8. If the attack deals damage, it can be Force damage or the weapon’s normal damage type (your choice). The spell ends early if you cast it again or if you let go of the weapon. Cantrip Upgrade. The damage die changes when you reach levels 5 (d10), 11 (d12), and 17 (2d6)."}
   {
    :name "Shining Smite"
    :key :shining-smite
    :school "transmutation"
    :level 2
    :source :srd-2024
    :casting-time "Bonus Action, Which You Take Immedi-"
    :range "self"
    :duration "concentration up to 1 minute"
    :description "The target hit by the strike takes an extra 2d6 Radi- ant damage from the attack. Until the spell ends, the target sheds Bright Light in a 5-foot radius, attack rolls against it have Advantage, and it can’t benefit from the Invisible condition. Using a Higher-Level Spell Slot. The damage in- creases by 1d6 for each spell slot level above 2."}
   {
    :name "Silence"
    :key :silence
    :school "illusion"
    :level 2
    :source :srd-2024
    :casting-time "Action"
    :range "120 feet"
    :duration "concentration up to 10 minutes"
    :components {}
    :ritual true
    :description "For the duration, no sound can be created within or pass through a 20-foot-radius Sphere centered on a point you choose within range. Any creature or object entirely inside the Sphere has Immunity to Thunder damage, and creatures have the Deaf- ened condition while entirely inside it. Casting a spell that includes a Verbal component is impossible there."}
   {
    :name "Sleep"
    :key :sleep
    :school "enchantment"
    :level 1
    :source :srd-2024
    :casting-time "Action"
    :range "60 feet"
    :duration "concentration up to 1 minute"
    :components {}
    :description "Each creature of your choice in a 5-foot-radius Sphere centered on a point within range must suc- ceed on a Wisdom saving throw or have the Inca- pacitated condition until the end of its next turn, at which point it must repeat the save. If the target fails the second save, the target has the Unconscious condition for the duration. The spell ends on a tar- get if it takes damage or someone within 5 feet of it takes an action to shake it out of the spell’s effect. Creatures that don’t sleep, such as elves, or that have Immunity to the Exhaustion condition auto- matically succeed on saves against this spell."}
   {
    :name "Sorcerous Burst"
    :key :sorcerous-burst
    :school "evocation"
    :level 0
    :source :srd-2024
    :casting-time "Action"
    :range "120 feet"
    :duration "instantaneous"
    :description "You cast sorcerous energy at one creature or object within range. Make a ranged spell attack against the target. On a hit, the target takes 1d8 damage of a type you choose: Acid, Cold, Fire, Lightning, Poison, Psychic, or Thunder. If you roll an 8 on a d8 for this spell, you can roll another d8, and add it to the damage. When you cast this spell, the maximum number of these d8s you can add to the spell’s damage equals your spellcast- ing ability modifier. Cantrip Upgrade. The damage increases by 1d8 when you reach levels 5 (2d8), 11 (3d8), and 17 (4d8)."}
   {
    :name "Spare The Dying"
    :key :spare-the-dying
    :school "necromancy"
    :level 0
    :source :srd-2024
    :casting-time "Action"
    :range "15 feet"
    :duration "instantaneous"
    :components {}
    :description "Choose a creature within range that has 0 Hit Points and isn’t dead. The creature becomes Stable. Cantrip Upgrade. The range doubles when you reach levels 5 (30 feet), 11 (60 feet), and 17 (120 feet)."}
   {
    :name "Speak With Animals"
    :key :speak-with-animals
    :school "divination"
    :level 1
    :source :srd-2024
    :casting-time "Action"
    :range "self"
    :duration "10 minutes"
    :components {}
    :ritual true
    :description "For the duration, you can comprehend and verbally communicate with Beasts, and you can use any of the Influence action’s skill options with them. Most Beasts have little to say about topics that don’t pertain to survival or companionship, but at minimum, a Beast can give you information about nearby locations and monsters, including whatever it has perceived within the past day."}
   {
    :name "Spiritual Weapon"
    :key :spiritual-weapon
    :school "evocation"
    :level 2
    :source :srd-2024
    :casting-time "Bonus Action"
    :range "60 feet"
    :duration "concentration up to 1 minute"
    :components {}
    :description "You create a floating, spectral force that resembles a weapon of your choice and lasts for the duration. The force appears within range in a space of your choice, and you can immediately make one melee spell attack against one creature within 5 feet of the force. On a hit, the target takes Force damage equal to 1d8 plus your spellcasting ability modifier. As a Bonus Action on your later turns, you can move the force up to 20 feet and repeat the attack against a creature within 5 feet of it. Using a Higher-Level Spell Slot. The damage in- creases by 1d8 for every slot level above 2."}
   {
    :name "Starry Wisp"
    :key :starry-wisp
    :school "evocation"
    :level 0
    :source :srd-2024
    :casting-time "Action"
    :range "60 feet"
    :duration "instantaneous"
    :components {}
    :description "You launch a mote of light at one creature or object within range. Make a ranged spell attack against the target. On a hit, the target takes 1d8 Radiant dam- age, and until the end of your next turn, it emits Dim Light in a 10-foot radius and can’t benefit from the Invisible condition. Cantrip Upgrade. The damage increases by 1d8 when you reach levels 5 (2d8), 11 (3d8), and 17 (4d8)."}
   {
    :name "Stoneskin"
    :key :stoneskin
    :school "transmutation"
    :level 4
    :source :srd-2024
    :casting-time "Action"
    :range "touch"
    :duration "concentration up to 1 hour"
    :components {}
    :description "Until the spell ends, one willing creature you touch has Resistance to Bludgeoning, Piercing, and Slash- ing damage."}
   {
    :name "Storm Of Vengeance"
    :key :storm-of-vengeance
    :school "conjuration"
    :level 9
    :source :srd-2024
    :casting-time "Action"
    :range "1 mile"
    :duration "concentration up to 1 minute"
    :components {}
    :description "A churning storm cloud forms for the duration, centered on a point within range and spreading to a radius of 300 feet. Each creature under the cloud when it appears must succeed on a Constitution saving throw or take 2d6 Thunder damage and have the Deafened condition for the duration. At the start of each of your later turns, the storm produces different effects, as detailed below. Turn 2. Acidic rain falls. Each creature and object under the cloud takes 4d6 Acid damage. Turn 3. You call six bolts of lightning from the cloud to strike six different creatures or objects beneath it. Each target makes a Dexterity saving throw, taking 10d6 Lightning damage on a failed save or half as much damage on a successful one. Turn 4. Hailstones rain down. Each creature un- der the cloud takes 2d6 Bludgeoning damage. Turns 5-10. Gusts and freezing rain assail the area under the cloud. Each creature there takes 1d6 Cold damage. Until the spell ends, the area is Diffi- cult Terrain and Heavily Obscured, ranged attacks with weapons are impossible there, and strong wind blows through the area."}
   {
    :name "Summon Dragon"
    :key :summon-dragon
    :school "conjuration"
    :level 5
    :source :srd-2024
    :casting-time "Action"
    :range "60 feet"
    :duration "concentration up to 1 hour"
    :components {}
    :description "You call forth a Dragon spirit. It manifests in an un- occupied space that you can see within range and uses the Draconic Spirit stat block. The creature disappears when it drops to 0 Hit Points or when the spell ends. The creature is an ally to you and your allies. In combat, the creature shares your Initiative count, but it takes its turn immediately after yours. It obeys your verbal commands (no action required by you). If you don’t issue any, it takes the Dodge action and uses its movement to avoid danger. Using a Higher-Level Spell Slot. Use the spell slot’s level for the spell’s level in the stat block. Draconic Spirit Large Dragon, Neutral AC 14 + the spell’s level HP 50 + 10 for each spell level above 5 Speed 30 ft., Fly 60 ft., Swim 30 ft. MOD SAVE MOD SAVE MOD SAVE Str 19 +4 +4 Dex 14 +2 +2 Con 17 +3 +3 Int 10 +0 +0 Wis 14 +2 +2 Cha 14 +2 +2 Resistances Acid, Cold, Fire, Lightning, Poison Immunities Charmed, Frightened, Poisoned Senses Blindsight 30 ft., Darkvision 60 ft.; Passive Perception 12 Languages Draconic, understands the languages you know CR None (XP 0; PB equals your Proficiency Bonus) Traits Shared Resistances. When you summon the spirit, choose one of its Resistances. You have Resistance to the chosen damage type until the spell ends. Actions Multiattack. The spirit makes a number of Rend attacks equal to half the spell’s level (round down), and it uses Breath Weapon. Rend. Melee Attack Roll: Bonus equals your spell attack modifier, reach 10 feet. Hit: 1d6 + 4 + the spell’s level Piercing damage. Breath Weapon. Dexterity Saving Throw: DC equals your spell save DC, each creature in a 30-foot Cone. Failure: 2d6 damage of a type this spirit has Resistance to (your choice when you cast the spell). Success: Half damage."}
   {
    :name "Telepathic Bond"
    :key :telepathic-bond
    :school "divination"
    :level 5
    :source :srd-2024
    :casting-time "Action"
    :range "30 feet"
    :duration "1 hour"
    :components {}
    :ritual true
    :description "You forge a telepathic link among up to eight willing creatures of your choice within range, psychically linking each creature to all the others for the dura- tion. Creatures that can’t communicate in any lan- guages aren’t affected by this spell. Until the spell ends, the targets can communicate telepathically through the bond whether or not they share a language. The communication is possible over any distance, though it can’t extend to other planes of existence."}
   {
    :name "Tiny Hut"
    :key :tiny-hut
    :school "evocation"
    :level 3
    :source :srd-2024
    :casting-time "1 Minute"
    :range "self"
    :duration "8 hours"
    :components {}
    :ritual true
    :description "A 10-foot Emanation springs into existence around you and remains stationary for the duration. The spell fails when you cast it if the Emanation isn’t big enough to fully encapsulate all creatures in its area. Creatures and objects within the Emanation when you cast the spell can move through it freely. All other creatures and objects are barred from passing through it. Spells of level 3 or lower can’t be cast through it, and the effects of such spells can’t extend into it. The atmosphere inside the Emanation is comfort- able and dry, regardless of the weather outside. Until the spell ends, you can command the interior to have Dim Light or Darkness (no action required). The Em- anation is opaque from the outside and of any color you choose, but it’s transparent from the inside. The spell ends early if you leave the Emanation or if you cast it again."}
   {
    :name "Transport Via Plants"
    :key :transport-via-plants
    :school "conjuration"
    :level 6
    :source :srd-2024
    :casting-time "Action"
    :range "10 feet"
    :duration "1 minute"
    :components {}
    :description "This spell creates a magical link between a Large or larger inanimate plant within range and another plant, at any distance, on the same plane of exis- tence. You must have seen or touched the destina- tion plant at least once before. For the duration, any creature can step into the target plant and exit from the destination plant by using 5 feet of movement."}
   {
    :name "True Strike"
    :key :true-strike
    :school "divination"
    :level 0
    :source :srd-2024
    :casting-time "Action"
    :range "self"
    :duration "instantaneous"
    :components {}
    :description "Guided by a flash of magical insight, you make one attack with the weapon used in the spell’s casting. The attack uses your spellcasting ability for the at- tack and damage rolls instead of using Strength or Dexterity. If the attack deals damage, it can be Ra- diant damage or the weapon’s normal damage type (your choice). Cantrip Upgrade. Whether you deal Radiant dam- age or the weapon’s normal damage type, the attack deals extra Radiant damage when you reach levels 5 (1d6), 11 (2d6), and 17 (3d6)."}
   {
    :name "Tsunami"
    :key :tsunami
    :school "conjuration"
    :level 8
    :source :srd-2024
    :casting-time "1 Minute"
    :range "1 mile"
    :duration "concentration up to 6 rounds"
    :components {}
    :description "A wall of water springs into existence at a point you choose within range. You can make the wall up to 300 feet long, 300 feet high, and 50 feet thick. The wall lasts for the duration. When the wall appears, each creature in its area makes a Strength saving throw, taking 6d10 Blud- geoning damage on a failed save or half as much damage on a successful one. At the start of each of your turns after the wall appears, the wall, along with any creatures in it, moves 50 feet away from you. Any Huge or smaller creature inside the wall or whose space the wall enters when it moves must succeed on a Strength saving throw or take 5d10 Bludgeoning damage. A creature can take this damage only once per round. At the end of the turn, the wall’s height is reduced by 50 feet, and the damage the wall deals on later rounds is reduced by 1d10. When the wall reaches 0 feet in height, the spell ends. A creature caught in the wall can move by swim- ming. Because of the wave’s force, though, the crea- ture must succeed on a Strength (Athletics) check against your spell save DC to move at all. If it fails the check, it can’t move. A creature that moves out of the wall falls to the ground."}
   {
    :name "Unseen Servant"
    :key :unseen-servant
    :school "conjuration"
    :level 1
    :source :srd-2024
    :casting-time "Action"
    :range "60 feet"
    :duration "1 hour"
    :components {}
    :ritual true
    :description "This spell creates an Invisible, mindless, shapeless, Medium force that performs simple tasks at your command until the spell ends. The servant springs into existence in an unoccupied space on the ground within range. It has AC 10, 1 Hit Point, and a Strength of 2, and it can’t attack. If it drops to 0 Hit Points, the spell ends. Once on each of your turns as a Bonus Action, you can mentally command the servant to move up to 15 feet and interact with an object. The servant can perform simple tasks that a human could do, such as fetching things, cleaning, mending, folding clothes, lighting fires, serving food, and pouring drinks. Once you give the command, the servant performs the task to the best of its ability until it completes the task, then waits for your next command. If you command the servant to perform a task that would move it more than 60 feet away from you, the spell ends."}
   {
    :name "Vitriolic Sphere"
    :key :vitriolic-sphere
    :school "evocation"
    :level 4
    :source :srd-2024
    :casting-time "Action"
    :range "150 feet"
    :duration "instantaneous"
    :components {}
    :description "You point at a location within range, and a glow- ing, 1-foot-diameter ball of acid streaks there and explodes in a 20-foot-radius Sphere. Each creature in that area makes a Dexterity saving throw. On a failed save, a creature takes 10d4 Acid damage and another 5d4 Acid damage at the end of its next turn. On a successful save, a creature takes half the initial damage only. Using a Higher-Level Spell Slot. The initial dam- age increases by 2d4 for each spell slot level above 4."}
   {
    :name "Water Breathing"
    :key :water-breathing
    :school "transmutation"
    :level 3
    :source :srd-2024
    :casting-time "Action"
    :range "30 feet"
    :duration "24 hours"
    :components {}
    :ritual true
    :description "This spell grants up to ten willing creatures of your choice within range the ability to breathe under- water until the spell ends. Affected creatures also retain their normal mode of respiration."}
   {
    :name "Water Walk"
    :key :water-walk
    :school "transmutation"
    :level 3
    :source :srd-2024
    :casting-time "Action"
    :range "30 feet"
    :components {}
    :ritual true
    :description "This spell grants the ability to move across any liquid surface—such as water, acid, mud, snow, quicksand, or lava—as if it were harmless solid ground (crea- tures crossing molten lava can still take damage from the heat). Up to ten willing creatures of your choice within range gain this ability for the duration. An affected target must take a Bonus Action to pass from the liquid’s surface into the liquid itself and vice versa, but if the target falls into the liquid, the target passes through the surface into the liquid below."}
   {
    :name "Weird"
    :key :weird
    :school "illusion"
    :level 9
    :source :srd-2024
    :casting-time "Action"
    :range "120 feet"
    :duration "concentration up to 1 minute"
    :components {}
    :description "You try to create illusory terrors in others’ minds. Each creature of your choice in a 30-foot-radius Sphere centered on a point within range makes a Wisdom saving throw. On a failed save, a target takes 10d10 Psychic damage and has the Frightened condition for the duration. On a successful save, a target takes half as much damage only. A Frightened target makes a Wisdom saving throw at the end of each of its turns. On a failed save, it takes 5d10 Psychic damage. On a successful save, the spell ends on that target."}])
