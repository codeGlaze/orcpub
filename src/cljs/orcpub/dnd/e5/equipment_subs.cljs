(ns orcpub.dnd.e5.equipment-subs
  (:require [re-frame.core :refer [reg-sub reg-sub-raw dispatch #_subscribe]]
            [orcpub.common :as common]
            [orcpub.template :as t]
            [orcpub.dnd.e5.spell-subs]
            [orcpub.dnd.e5.modifiers :as mod5e]
            [orcpub.dnd.e5.magic-items :as mi5e]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.backgrounds :as bg5e]
            [orcpub.dnd.e5.languages :as langs5e]
            [orcpub.dnd.e5.feats :as feats5e]
            [orcpub.dnd.e5.races :as races5e]
            [orcpub.dnd.e5.classes :as classes5e]
            [orcpub.dnd.e5.weapons :as weapon5e]
            [orcpub.dnd.e5.armor :as armor5e]
            [orcpub.dnd.e5.template :as t5e]
            [orcpub.dnd.e5.equipment :as equipment5e]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.route-map :as routes]
            [orcpub.dnd.e5.event-utils :as event-utils :refer [url-for-route auth-headers
                                                                    handle-api-response]]
            [reagent.ratom :as ra]
            [clojure.string :as s]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(def sorted-items
  (delay (sort-by mi5e/name-key mi5e/magic-items))
  )

(if js/window.location
  (reg-sub-raw
   ::mi5e/custom-items
   (fn [app-db [_ user-data]]
     (when (and (:user-data @app-db) (:token (:user-data @app-db)))
       (go (dispatch [:set-loading true])
           (let [response (<! (http/get (url-for-route routes/dnd-e5-items-route)
                                        {:headers (auth-headers @app-db)}))]
             (dispatch [:set-loading false])
             (handle-api-response response
               #(dispatch [::mi5e/set-custom-items (:body response)])
               :on-401 (fn [])
               :context "fetch custom items"))))
     (ra/make-reaction
      (fn [] (get @app-db ::mi5e/custom-items [])))))
  (reg-sub
   ::mi5e/custom-items
   (fn [_ _] []))
  )

(reg-sub
 ::mi5e/expanded-custom-items
 :<- [::mi5e/custom-items]
 (fn [custom-items _]
   (mi5e/expand-magic-items custom-items)))

;; The EDIT path. Deliberately reads the raw expansion, not the effective
;; one: the item builder must load an item exactly as stored, or unticking
;; Magic Item once would make the suppression permanent on the next save.
(reg-sub
 ::mi5e/custom-item-map
 :<- [::mi5e/expanded-custom-items]
 (fn [custom-items _]
   (common/map-by-id custom-items)))

;; Everything else. A mundane item's magical mechanics are suppressed here,
;; once, so no downstream consumer has to remember to do it.
(reg-sub
 ::mi5e/effective-custom-items
 :<- [::mi5e/expanded-custom-items]
 (fn [custom-items _]
   (map mi5e/effective-item custom-items)))

(reg-sub
 ::mi5e/custom-item
 :<- [::mi5e/custom-item-map]
 (fn [custom-item-map [_ id]]
   (get custom-item-map id)))

(reg-sub
 ::char5e/sorted-items
 :<- [::mi5e/effective-custom-items]
 (fn [custom-items _]
   (concat
    custom-items
    @sorted-items)))

(reg-sub
 ::mi5e/custom-weapons
 :<- [::mi5e/effective-custom-items]
 (fn [custom-items _]
   (sequence
    mi5e/magic-weapon-xform
    custom-items)))

(reg-sub
 ::mi5e/custom-and-standard-weapons
 :<- [::mi5e/custom-weapons]
 (fn [custom-weapons _]
   (concat
    (map
     (fn [{:keys [::mi5e/name] :as i}]
       (assoc i :name name))
     custom-weapons) weapon5e/weapons)))

(reg-sub
 ::mi5e/custom-and-standard-weapons-map
 :<- [::mi5e/custom-and-standard-weapons]
 (fn [custom-and-standard-weapons _]
   (common/map-by-key custom-and-standard-weapons)))

(reg-sub
 ::mi5e/magic-weapons
 :<- [::char5e/sorted-items]
 (fn [sorted-items _]
   (sequence
    mi5e/magic-weapon-xform
    sorted-items)))

(reg-sub
 ::mi5e/all-weapons
 :<- [::mi5e/magic-weapons]
 (fn [magic-weapons]
   (concat magic-weapons weapon5e/weapons)))

;; Unused — melee-only filter. UI filters inline. Restore if a melee-only view is added.
#_(reg-sub
   ::mi5e/all-melee-weapons
   :<- [::mi5e/all-weapons]
   (fn [all-weapons]
     (filter
      ::weapon5e/melee?
      all-weapons)))

(defn map-by-key-or-id [items]
  (reduce
   (fn [m {:keys [:db/id key] :as item}]
     (assoc m
            key item
            id item))
   {}
   items))

(reg-sub
 ::mi5e/magic-weapon-map
 :<- [::mi5e/magic-weapons]
 (fn [magic-weapons _]
   (map-by-key-or-id magic-weapons)))

(defn item-option-cfg
  "Build one inventory option from an item.

   legacy-only? options stay resolvable but are not offered in the picker —
   see orcpub.template/option-cfg."
  [modifier-fn legacy-only? {:keys [:db/id
                                    ::mi5e/name
                                    key
                                    ::mi5e/description
                                    ::mi5e/page
                                    ::mi5e/modifiers
                                    ::mi5e/source] :as item}]
  (let [item-key (or key (keyword (str "id-" id)))
        full-item (update item
                          ::mi5e/modifiers
                          mod5e/build-modifiers)]
    (t/option-cfg
     {:name (or (:name item) name)
      :key item-key
      :legacy-only? legacy-only?
      :help (when (or description
                      page)
              (t5e/inventory-help description page source))
      :modifiers [(modifier-fn
                   item-key
                   full-item)]})))

(defn magic-item-options
  "Options for one of the Magic Weapons / Magic Armor / Other Magic Items
   selections.

   Custom items the user has since marked mundane are STILL listed here, as
   legacy-only options. Dropping them would orphan every character that picked
   one back when every custom item was filed as magical: the character's saved
   reference would no longer match an option, the item's modifiers would stop
   applying, and it would vanish from the sheet. Keeping the option resolvable
   costs nothing and loses nobody's gear; it just stops being offered."
  [modifier-fn nm]
  (fn [items _]
    (map
     (fn [item]
       (item-option-cfg modifier-fn (mi5e/mundane? item) item))
     items)))

(defn mundane-item-options
  "Options contributed to the ordinary Weapons / Armor / Equipment selections
   by custom items their owner has marked mundane."
  [modifier-fn]
  (fn [items _]
    (map
     (fn [item]
       (item-option-cfg modifier-fn false item))
     items)))

(reg-sub
 ::mi5e/magic-weapon-options
 :<- [::mi5e/magic-weapons]
 (magic-item-options mod5e/deferred-magic-weapon "Magic Weapon"))

;; --- mundane custom items -------------------------------------------------
;; Custom items marked mundane also feed the ordinary equipment selections, so
;; a homemade sword can be added under Weapons instead of Magic Weapons. Note
;; these subscriptions ADD options; nothing is taken out of the magic maps, so
;; every existing lookup (attack table, AC, sheet) keeps resolving.

(reg-sub
 ::mi5e/mundane-custom-items
 :<- [::mi5e/effective-custom-items]
 (fn [custom-items _]
   (filter mi5e/mundane? custom-items)))

(reg-sub
 ::mi5e/mundane-custom-weapons
 :<- [::mi5e/mundane-custom-items]
 (fn [mundane-items _]
   ;; magic-weapon-xform / magic-armor-xform / other-magic-items-xform are
   ;; plain ::mi5e/type filters despite their names.
   (sequence mi5e/magic-weapon-xform mundane-items)))

(reg-sub
 ::mi5e/mundane-custom-armor
 :<- [::mi5e/mundane-custom-items]
 (fn [mundane-items _]
   (sequence mi5e/magic-armor-xform mundane-items)))

(reg-sub
 ::mi5e/mundane-custom-gear
 :<- [::mi5e/mundane-custom-items]
 (fn [mundane-items _]
   (sequence mi5e/other-magic-items-xform mundane-items)))

(reg-sub
 ::mi5e/mundane-weapon-options
 :<- [::mi5e/mundane-custom-weapons]
 (mundane-item-options mod5e/deferred-custom-weapon))

(reg-sub
 ::mi5e/mundane-armor-options
 :<- [::mi5e/mundane-custom-armor]
 (mundane-item-options mod5e/deferred-custom-armor))

(reg-sub
 ::mi5e/mundane-equipment-options
 :<- [::mi5e/mundane-custom-gear]
 (mundane-item-options mod5e/deferred-custom-equipment))

(reg-sub
 ::mi5e/magic-armor-options
 :<- [::mi5e/magic-armor]
 (magic-item-options mod5e/deferred-magic-armor "Magic Armor"))

(reg-sub
 ::mi5e/other-magic-item-options
 :<- [::mi5e/other-magic-items]
 (magic-item-options mod5e/deferred-magic-item "Magic Item"))

(reg-sub
 ::mi5e/magic-armor
 :<- [::char5e/sorted-items]
 (fn [sorted-items _]
   (sequence
    mi5e/magic-armor-xform
    sorted-items)))

(reg-sub
 ::mi5e/magic-armor-map
 :<- [::mi5e/magic-armor]
 (fn [magic-armor _]
   (map-by-key-or-id magic-armor)))

(reg-sub
 ::mi5e/other-magic-items
 :<- [::char5e/sorted-items] ;function relies on state of this sub
 (fn [sorted-items _]
   (sequence
    mi5e/other-magic-items-xform
    sorted-items)))

(reg-sub
 ::mi5e/all-armor-map
 :<- [::mi5e/magic-armor-map]
 (fn [magic-armor-map]
   (merge
    magic-armor-map
    armor5e/armor-map)))

(reg-sub
 ::mi5e/other-magic-items-map
 :<- [::mi5e/other-magic-items]
 (fn [magic-items _]
   (map-by-key-or-id magic-items)))

;; Standard (SRD) equipment lookup maps — used by character_builder's
;; inventory-selector for the non-magic equipment sections. These are
;; thin wrappers around static vars, kept as subscriptions because
;; inventory-selector receives the sub vector dynamically.
(reg-sub
 ::equipment5e/weapons-map
 (fn [_ _] weapon5e/weapons-map))

;; For homebrew-inclusive weapons, use ::mi5e/all-weapons-map instead.

(reg-sub
 ::mi5e/all-weapons-map
 :<- [::mi5e/magic-weapon-map]
 (fn [magic-weapons-map]
   (merge
    magic-weapons-map
    weapon5e/weapons-map)))

(reg-sub
 ::mi5e/all-magic-items-map
 :<- [::mi5e/magic-weapon-map]
 :<- [::mi5e/magic-armor-map]
 :<- [::mi5e/other-magic-items-map]
 (fn [maps _]
   (apply merge
          mi5e/all-magic-items-map
          maps)))

(reg-sub
 ::mi5e/remote-items
 (fn [db _]
   (::mi5e/remote-items db)))

(reg-sub-raw
 ::mi5e/remote-item
 (fn [app-db [_ id]]
   (when (and (:user @app-db) (:token (:user @app-db)))
    (go (dispatch [:set-loading true])
       (let [response (<! (http/get (url-for-route
                                      routes/dnd-e5-item-route
                                      :id id)
                                    {:headers (auth-headers @app-db)}))]
         (dispatch [:set-loading false])
         (handle-api-response response
           #(dispatch [::mi5e/add-remote-item (:body response)])
           :context "fetch item"))))
   (ra/make-reaction
    (fn [] (get-in @app-db [::mi5e/remote-items id] {})))))

;; Unused — item detail lookup with remote HTTP fetch for int keys.
;; Groundwork for a magic item detail page. Restore when needed.
#_(reg-sub-raw
   ::mi5e/item
   (fn [_app-db [_ key]]
     (if (int? key)
       (subscribe [::mi5e/remote-item key])
       (ra/make-reaction (fn [] (get mi5e/all-equipment-map key))))))

(reg-sub
 ::equipment5e/armor-map
 (fn [_ _] armor5e/armor-map))

(reg-sub
 ::equipment5e/equipment-map
 (fn [_ _] equipment5e/equipment-map))

(reg-sub
 ::mi5e/all-equipment-map
 :<- [::mi5e/other-magic-items-map]
 (fn [other-magic-items-map _]
   ;; Name lookup for the Equipment section. Includes custom items so a
   ;; homemade lantern shows its name there instead of a blank row; a superset
   ;; is harmless because this only resolves keys the character already holds.
   (merge equipment5e/equipment-map other-magic-items-map)))

(reg-sub
 ::equipment5e/treasure-map
 (fn [_ _] equipment5e/treasure-map))

;; For homebrew-inclusive armor, use ::mi5e/all-armor-map instead.

(reg-sub
 ::char5e/template-selections
 :<- [::mi5e/magic-weapon-options]
 :<- [::mi5e/magic-armor-options]
 :<- [::mi5e/other-magic-item-options]
 :<- [::mi5e/all-weapons-map]
 :<- [::mi5e/custom-and-standard-weapons]
 :<- [::mi5e/mundane-weapon-options]
 :<- [::mi5e/mundane-armor-options]
 :<- [::mi5e/mundane-equipment-options]
 :<- [::spells5e/spell-lists]
 :<- [::spells5e/spells-map]
 :<- [::bg5e/backgrounds]
 :<- [::races5e/races]
 :<- [::classes5e/classes]
 :<- [::feats5e/feats]
 :<- [::langs5e/language-map]
 (fn [[magic-weapon-options
       magic-armor-options
       other-magic-item-options
       weapons-map
       custom-and-standard-weapons
       mundane-weapon-options
       mundane-armor-options
       mundane-equipment-options
       spell-lists
       spells-map
       backgrounds
       races
       classes
       feats
       language-map] _]
   (t5e/template-selections magic-weapon-options
                            magic-armor-options
                            other-magic-item-options
                            weapons-map
                            custom-and-standard-weapons
                            spell-lists
                            spells-map
                            backgrounds
                            races
                            classes
                            feats
                            language-map
                            {:weapons mundane-weapon-options
                             :armor mundane-armor-options
                             :equipment mundane-equipment-options})))

(reg-sub
 ::char5e/template
 :<- [::char5e/template-selections]
 (fn [template-selections _]
   (t5e/template template-selections)))
