(ns orcpub.dnd.e5.char-filter
  (:require [clojure.string :as s]
            [orcpub.dnd.e5.character :as char5e]))

(defn char-matches?
  "Returns true if `char` satisfies all active filter criteria.

   Filters:
     name-filter      - string; blank = no filter, otherwise case-insensitive substring
     level-filters    - set of ints; empty = no filter, any class level must be in set
     class-filters    - set of strings; empty = no filter, any class name must be in set
     has-portrait?    - nil = all, true = must have image-url, false = must not
     has-faction-pic? - nil = all, true = must have faction-image-url, false = must not"
  [char name-filter level-filters class-filters has-portrait? has-faction-pic?]
  (and
   (or (s/blank? name-filter)
       (s/includes? (s/lower-case (or (::char5e/character-name char) ""))
                    (s/lower-case name-filter)))
   (or (empty? level-filters)
       (some #(level-filters (::char5e/level %)) (::char5e/classes char)))
   (or (empty? class-filters)
       (some #(class-filters (::char5e/class-name %)) (::char5e/classes char)))
   (or (nil? has-portrait?)
       (= has-portrait? (boolean (::char5e/image-url char))))
   (or (nil? has-faction-pic?)
       (= has-faction-pic? (boolean (::char5e/faction-image-url char))))))

(defn filter-characters
  "Filter `characters` by all active filter criteria."
  [characters name-filter level-filters class-filters has-portrait? has-faction-pic?]
  (filter #(char-matches? % name-filter level-filters class-filters has-portrait? has-faction-pic?)
          characters))
