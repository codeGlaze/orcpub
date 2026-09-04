(ns orcpub.dnd.e5.orcbrew-composition-test
  "The two orcbrew on-disk transforms COMPOSED, which nothing else covers.

   Each half is tested alone elsewhere — orcbrew-format-test for the v2 stamp/unwrap envelope,
   starting-equipment-ledger-test for the class collapse/expand delta. They meet on the export and
   import paths in events.cljs, and the ORDER they run in is load-bearing:

     export   collapse-class  ->  stamp    ->  serialize
     import   read            ->  unwrap   ->  expand-class

   stamp wraps content as {:orcbrew/format-version N :orcbrew/content <the plugin map>}. The class
   walker iterates a plugin map's entries and skips any whose value is not a map, so run AFTER
   stamp it would walk :orcbrew/format-version -> 2, skip it, and collapse nothing. Silently: no
   error, just an uncollapsed file. That is what these tests pin."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.dnd.e5.orcbrew-format :as orcbrew-format]
            [orcpub.dnd.e5.starting-equipment-ledger :as ledger]
            [orcpub.dnd.e5.srd-starting-equipment :as srd]
            [orcpub.dnd.e5 :as e5]
            #?(:clj [clojure.edn :as reader] :cljs [cljs.reader :as reader])))

;; Mirrors map-plugin-classes in events.cljs. Duplicated because that fn is cljs-only while both
;; transforms it composes are cljc; moving it into starting-equipment-ledger would remove this copy
;; and let the test exercise the shipped function directly.
(defn- walk-classes [f data]
  (if (map? data)
    (reduce-kv
     (fn [m src cts]
       (assoc m src
              (if (and (map? cts) (map? (::e5/classes cts)))
                (update cts ::e5/classes
                        (fn [cs] (reduce-kv (fn [acc k c] (assoc acc k (if (map? c) (f c) c))) {} cs)))
                cts)))
     {} data)
    data))

(def collapsible-class
  (merge {:name "Battle Sage" :key :battle-sage :hit-die 10 :starting-equipment-base :fighter}
         (srd/builder-equipment :fighter)
         {:weapons (assoc (:weapons (srd/builder-equipment :fighter)) :dagger 1)}))

;; The :grant feat is what makes this content v2, so stamp actually produces an envelope.
(def v2-plugins
  {"Pack" {::e5/classes {:battle-sage collapsible-class}
           :orcpub.dnd.e5/feats {:g {:name "G" :key :g :option-pack "Pack"
                                     :grant {:from :fighting-styles :choose 1}}}}})

(defn- export [data] (orcbrew-format/serialize-orcbrew
                      (orcbrew-format/stamp (walk-classes ledger/collapse-class data))))
(defn- import- [text] (walk-classes ledger/expand-class
                                    (orcbrew-format/unwrap (reader/read-string text))))

(deftest export-import-round-trips-through-both-transforms
  (testing "a class survives collapse -> stamp -> serialize -> unwrap -> expand unchanged"
    (let [back (get-in (import- (export v2-plugins)) ["Pack" ::e5/classes :battle-sage])]
      (is (= (:name collapsible-class) (:name back)))
      (is (= :fighter (:starting-equipment-base back)) "base marker restored on import")
      (is (nil? (:starting-equipment back)) "delta key consumed by expand")
      (is (contains? (:weapons back) :dagger) "the edit survives the whole trip")
      (is (= (:equipment-selections collapsible-class) (:equipment-selections back))))))

(deftest the-collapse-really-happened-on-disk
  (testing "the serialized file carries the compact delta, not a full equipment copy"
    (let [on-disk (orcbrew-format/unwrap (reader/read-string (export v2-plugins)))
          cls     (get-in on-disk ["Pack" ::e5/classes :battle-sage])]
      (is (= :fighter (get-in cls [:starting-equipment :base])) "delta written")
      (is (nil? (:equipment-selections cls)) "full selections NOT written"))))

(deftest stamping-before-collapsing-would-silently-skip-the-collapse
  (testing "the ordering claim, stated as a falsifiable test rather than a comment"
    (let [stamped (orcbrew-format/stamp v2-plugins)]
      (is (orcbrew-format/envelope? stamped) "this fixture really is v2, so there IS an envelope")
      (let [wrong-order (walk-classes ledger/collapse-class stamped)]
        (is (= stamped wrong-order)
            "collapsing an already-stamped envelope is a NO-OP — the walker skips
             :orcbrew/format-version because its value is not a map, and never reaches the classes.
             No error, just an uncollapsed export. Hence collapse must run first.")))))
