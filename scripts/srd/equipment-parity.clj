;; Field-by-field parity of the app's weapons and armor against the generated SRD file.
;; Run through equipment-parity.sh, which regenerates the SRD file and supplies the app classpath.
;; Usage: clojure.main equipment-parity.clj SRD_EQUIPMENT_EDN REVIEWED_EDN [--self-test]
;; Every difference must be listed in REVIEWED_EDN with a reason; an unlisted one fails (exit 1).
;; KB: docs/kb/equipment-parity.md
(require '[clojure.edn :as edn] '[clojure.set :as set] '[clojure.string :as str]
         '[orcpub.dnd.e5.weapons :as w] '[orcpub.dnd.e5.armor :as a])

(defn weight
  "A weight as a number: 3, 3.0 and \"3 lb.\" are all 3.0, \"1/4 lb.\" is 0.25; nil and 0 (the SRD's dash) are nil."
  [x]
  (let [n (cond (number? x) (double x)
                (string? x) (if-let [[_ num den] (re-find #"^\s*(\d+)/(\d+)" x)]
                              (/ (Double/parseDouble num) (Double/parseDouble den))
                              (let [[_ whole half] (re-find #"^\s*(\d+(?:\.\d+)?)?(½)?" x)]
                                (+ (if whole (Double/parseDouble whole) 0.0) (if half 0.5 0.0)))))]
    (when (and n (pos? n)) n)))

(defn dice
  "Damage dice as [count die]: \"1d8\" is [1 8], a flat \"1\" is [1 1] (the app's form), \"0\" is nil."
  [d]
  (if-let [[_ c s] (re-matches #"(\d+)d(\d+)" (or d ""))]
    [(Long/parseLong c) (Long/parseLong s)]
    (when-let [n (some-> (re-matches #"[1-9]\d*" (or d "")) Long/parseLong)] [n 1])))

(defn srd-range
  "A property detail's \"range 30/120\" as [30 120], else nil."
  [s]
  (when-let [[_ x y] (re-find #"range (\d+)/(\d+)" (or s ""))] [(Long/parseLong x) (Long/parseLong y)]))

(def weapon-props
  "SRD property key -> app boolean field, for the properties the app stores as flags."
  {:finesse ::w/finesse? :heavy ::w/heavy? :light ::w/light? :loading ::w/loading? :reach ::w/reach
   :special ::w/special? :two-handed ::w/two-handed? :thrown ::w/thrown :ammunition ::w/ammunition?})

(defn srd-weapon
  "The comparable view of an SRD weapon: {field value}, in the app's terms."
  [e]
  (let [props (into {} (map (juxt :key #(or (:detail %) true)) (:properties e)))
        cat (name (:category e))]
    (merge {:name (:name e) :cost (:cost e) :weight (weight (:weight e))
            :type (keyword (first (str/split cat #"-"))) :melee? (str/ends-with? cat "melee")
            :ranged? (str/ends-with? cat "ranged")
            :damage-type (when (dice (get-in e [:damage :dice])) (get-in e [:damage :type]))
            :dice (dice (get-in e [:damage :dice]))
            :range (srd-range (or (props :ammunition) (props :thrown) (props :range)))
            :versatile (some-> (props :versatile) dice)}
           (into {} (for [[p _] weapon-props] [p (contains? props p)])))))

(defn app-weapon
  "The comparable view of an app weapon, field for field with srd-weapon."
  [e]
  (merge {:name (:name e) :cost (:cost e) :weight (weight (:weight e))
          :type (::w/type e) :melee? (boolean (::w/melee? e)) :ranged? (boolean (::w/ranged? e))
          :damage-type (::w/damage-type e)
          :dice (when (::w/damage-die e) [(::w/damage-die-count e) (::w/damage-die e)])
          :range (some-> (::w/range e) ((juxt ::w/min ::w/max)))
          :versatile (some-> (::w/versatile e) ((juxt ::w/damage-die-count ::w/damage-die)))}
         (into {} (for [[p f] weapon-props] [p (boolean (get e f))]))))

(defn srd-armor
  "The comparable view of an SRD armor; max-dex only where Dexterity applies."
  [e]
  {:name (:name e) :cost (:cost e) :weight (weight (:weight e)) :type (:category e)
   :ac (or (get-in e [:ac :base]) (get-in e [:ac :bonus]))
   :max-dex (when (get-in e [:ac :dex?]) (get-in e [:ac :max-dex]))
   :min-str (:strength e) :stealth-disadvantage? (boolean (:stealth-disadvantage? e))})

(defn app-armor
  "The comparable view of an app armor; a shield's AC is the +2 the app applies by type."
  [e]
  {:name (:name e) :cost (:cost e) :weight (weight (:weight e)) :type (:type e)
   :ac (or (:base-ac e) (when (= :shield (:type e)) 2))
   :max-dex (when (#{:light :medium} (:type e)) (:max-dex-mod e))
   :min-str (:min-str e) :stealth-disadvantage? (boolean (:stealth-disadvantage? e))})

(def mapped-fields
  "App fields the comparison covers, per kind; any other app field must be listed as app-only."
  {:weapons (into #{:name :cost :weight :key ::w/type ::w/melee? ::w/ranged? ::w/damage-type ::w/damage-die
                    ::w/damage-die-count ::w/range ::w/versatile} (vals weapon-props))
   :armor #{:name :cost :weight :key :type :base-ac :max-dex-mod :min-str :stealth-disadvantage?}})

(defn findings
  "Every difference between the SRD file and the app, as maps {:kind :key :field :srd :app}.
  Args: srd, the parsed equipment file; app, {:weapons [...] :armor [...]}."
  [srd app]
  (apply concat
         (for [[kind sview aview] [[:weapons srd-weapon app-weapon] [:armor srd-armor app-armor]]]
           (let [sm (into {} (map (juxt :key identity) (kind srd)))
                 am (into {} (map (juxt :key identity) (kind app)))]
             (concat
              (for [k (sort (set/difference (set (keys sm)) (set (keys am))))]
                {:kind kind :key k :field :entry :srd :present :app nil})
              (for [k (sort (set/difference (set (keys am)) (set (keys sm))))]
                {:kind kind :key k :field :entry :srd nil :app (:name (am k))})
              (for [k (sort (set/intersection (set (keys sm)) (set (keys am))))
                    :let [s (sview (sm k)) v (aview (am k))]
                    f (sort (keys s)) :when (not= (s f) (v f))]
                {:kind kind :key k :field f :srd (s f) :app (v f)})
              (for [f (sort-by str (set/difference (set (mapcat keys (kind app))) (mapped-fields kind)))]
                {:kind kind :key :* :field f :srd nil :app :app-only-field}))))))

(defn match
  "The reviewed entry covering a finding, or nil. Entries match on kind, key and field; :srd and
  :app, when given, must equal the finding's values, so a changed value is a new finding."
  [reviewed f]
  (some (fn [r] (when (and (= (select-keys r [:kind :key :field]) (select-keys f [:kind :key :field]))
                           (every? #(or (not (contains? r %)) (= (r %) (f %))) [:srd :app]))
                  r))
        reviewed))

(defn run
  "Prints the report and returns the number of unreviewed findings."
  [srd app reviewed quiet?]
  (let [fs (findings srd app)
        sorted (group-by #(if-let [r (match reviewed %)] (:status r :settled) :open) fs)]
    (when-not quiet?
      (doseq [[status title] [[:open "OPEN (not in the reviewed file)"] [:pending "pending a decision"] [:settled "settled"]]
              :let [xs (sorted status)] :when (seq xs)]
        (println (str "== " title ": " (count xs)))
        (doseq [f xs]
          (println (str "  " (name (:kind f)) " " (pr-str (:key f)) " " (pr-str (:field f))
                        " srd=" (pr-str (:srd f)) " app=" (pr-str (:app f))
                        (when-let [r (match reviewed f)] (str "  -- " (:reason r))))))))
    (count (sorted :open))))

(let [[srd-path reviewed-path flag] *command-line-args*
      srd (edn/read-string (slurp srd-path))
      app {:weapons w/weapons :armor a/armor}
      reviewed (edn/read-string (slurp reviewed-path))]
  (if (= flag "--self-test")
    (let [plants {"a changed SRD cost" (update-in srd [:weapons 0 :cost :num] inc)
                  "a dropped SRD entry" (update srd :armor (comp vec rest))
                  "an SRD property the app lacks" (update-in srd [:weapons 0 :properties] conj {:key :reach})}
          blind (remove (fn [[_ s]] (pos? (- (run s app reviewed true) (run srd app reviewed true)))) plants)]
      (doseq [[nm _] plants] (println (if (some #{nm} (map first blind)) "BLIND" "ok   ") nm))
      (System/exit (if (seq blind) 1 0)))
    (let [n (run srd app reviewed false)]
      (println (str n " open finding(s)"))
      (System/exit (if (pos? n) 1 0)))))
