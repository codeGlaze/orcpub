# Adding a homebrew content type — BEFORE vs AFTER

Representative comparison using the **Pact Boon** type. "Before" = the original fully-scattered
pattern; "After" = the registry-driven state (events `d2e002b4`, db `af68061d`, forms `109b5dd0`).
This shows the *events*, *db*, and *builder-form* layers — the ones collapsed so far. (Routes and
spec are still per-type; see `content-extensibility-direction.md` §"Foundation".)

---

## 1. Event wiring

### BEFORE — ~10 registrations, scattered across ~4,000 lines of `events.cljs`
```clojure
;; ~line 177
(def boon->local-store-interceptor (after boon->local-store))
(def boon-interceptors [(path ::class5e/boon-builder-item) boon->local-store-interceptor])

;; ~line 621
(reg-save-homebrew "Boon" ::class5e/save-boon ::class5e/boon-builder-item
                   ::class5e/homebrew-boon ::e5/boons
                   "You must specify 'Name', 'Option Source Name'")
;; ~line 756
(reg-delete-homebrew ::class5e/delete-boon ::e5/boons)
;; ~line 2142
(reg-edit-homebrew ::class5e/edit-boon ::class5e/set-boon
                   routes/dnd-e5-boon-builder-page-route)
;; ~line 3033
(reg-event-db ::class5e/set-boon-prop boon-interceptors
              (fn [boon [_ k v]] (assoc boon k v)))
;; ~line 4136
(reg-event-db ::class5e/set-boon boon-interceptors (fn [_ [_ boon]] boon))
;; ~line 4227
(reg-event-fx ::class5e/reset-boon (fn [_ _] {:dispatch [::class5e/set-boon default-boon]}))
;; ~line 4491
(reg-new-homebrew ::class5e/new-boon ::class5e/set-boon default-boon
                  routes/dnd-e5-boon-builder-page-route)
```

### AFTER — nothing per type. One loop (written ONCE) wires every type:
```clojure
;; events.cljs — this is the WHOLE per-type events story now, for ALL homebrew types:
(doseq [{:keys [type-name builder-item spec plugin-key route-kw default save-error] :as ct-entry}
        (filter :homebrew-builder? ct/content-types)]
  (register-homebrew-content!
   (merge (homebrew-event-keys builder-item)          ; save-/delete-/edit-/new-/set-/reset-/set-prop
          {:type-name type-name  :save-error (or save-error "...")
           :builder-item builder-item  :spec spec  :plugin-key plugin-key
           :default (or default {})  :route route-kw
           :interceptors (homebrew-local-store-interceptor ct-entry)})))
```
Adding a boon-like type adds **0 lines** here.

---

## 2. DB draft state

### BEFORE — a def, a key, a fn, and a slot, in `db.cljs`
```clojure
(def default-boon {})
(def local-storage-boon-key "boon")
(defn boon->local-store [boon]
  (when js/window.localStorage (set-item local-storage-boon-key (str boon))))
;; ...and inside the default-value map:
::class5e/boon-builder-item default-boon
```

### AFTER — nothing per type. The slots generate from the registry:
```clojure
;; db.cljs — inside default-value:
(into {} (map (juxt :builder-item :default))
      (filter :homebrew-builder? ct/content-types))
```
Adding a boon-like type adds **0 lines** here.

---

## 3. The builder form

### BEFORE — a bespoke input-field wrapper + a hand-built form, in `views.cljs`
```clojure
(defn boon-input-field [title prop boon & [class-names]]
  (builder-input-field title prop boon ::classes/set-boon-prop class-names))

(defn boon-builder []
  (let [boon @(subscribe [::classes/boon-builder-item])]
    [:div.p-20.main-text-color
     [:div.flex.w-100-p.flex-wrap
      [boon-input-field "Name" :name boon "m-b-20"]
      [plugin-datalist option-source-name-label boon ::classes/set-boon-prop]]
     [:div.w-100-p
      [:div.f-s-24.f-w-b "Description"]
      [textarea-field {:value (get boon :description)
                       :on-change #(dispatch [::classes/set-boon-prop :description %])}]]]))
```

### AFTER — one line (the generic form is data):
```clojure
(defn boon-builder []
  (simple-content-builder ::classes/boon-builder-item ::classes/set-boon-prop))
```
A *richer* type passes one extra field, e.g. the draconic-ancestry damage-type dropdown:
```clojure
(defn draconic-ancestry-builder []
  (simple-content-builder ::races/draconic-ancestry-builder-item
                          ::races/set-draconic-ancestry-prop
                          [[labeled-dropdown "Breath Weapon Damage Type" {...}]]))
```

---

## 4. So what do you actually WRITE to add a type now?

```clojure
;; content_types.cljc — ONE registry entry. Events + db wiring follow automatically.
{:id :boon  :type-name "Pact Boon"
 :builder-item :orcpub.dnd.e5.classes/boon-builder-item
 :spec :orcpub.dnd.e5.classes/homebrew-boon
 :plugin-key :orcpub.dnd.e5/boons
 :route-kw route-map/dnd-e5-boon-builder-page-route
 :route-seg "boon-builder"  :local-storage-key "boon"
 :homebrew-builder? true  :default {}}
```
Plus the genuinely per-type bits: the **form** (a one-liner, or + an extra field), the **spec**
(one line), and — until the routes pass — the route plumbing. Everything that used to be
copy-pasted boilerplate is now generated from that one entry.

**Behavior-preserving:** the boon and draconic builders' tests (handlers registered, builder
produces spec-valid content, pool, character round-trip) pass *unchanged* through all of this —
the loops register identically to the old hand-written code.
