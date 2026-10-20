(ns orcpub.dnd.e5.malformed-grants-test
  "Imported homebrew is untrusted: any field can hold any shape. Every function that reads
   :ability-increases or :save-proficiencies, and the export's format check, is fed a grid of
   malformed values in every field, and none may throw. Add a function here when it starts
   reading either field."
  (:require [clojure.test :refer [deftest is]]
            [orcpub.dnd.e5.options :as opt5e]
            [orcpub.dnd.e5.orcbrew-format :as orcbrew-format]))

(def malformed
  [nil 42 "x" true {} {:a 1} #{} #{:str} #{42} [] [nil] [42] ["x"] [{}] [[]] [[nil]] [[1]]
   [[1 nil]] [[1 nil :save]] [[1 :foo]] [[1 #{}]] [[1 []]] [[1 [nil]]] [[1 #{2}]] [["1" :str]]
   [[nil :str]] [[1 :str :save :extra]] [[0 :any]] [[-1 :any]] [[1.5 :any]] [[4 :mental]]
   [[1 {:a 1}]] [[1 "str"]] (list [1 :str])])

(def readers
  {"compile-ability-increases"  #(opt5e/compile-ability-increases (:ability-increases %))
   "compile-save-proficiencies" #(opt5e/compile-save-proficiencies (:save-proficiencies %))
   "compile-ability-grants"     #(opt5e/compile-ability-grants %)
   "... :subrace"               #(opt5e/compile-ability-grants % {:attribution :subrace})
   "... :general"               #(opt5e/compile-ability-grants % {:attribution :general})
   "ignored-entry-warnings"     opt5e/ignored-entry-warnings
   "save-coverage-warnings"     opt5e/save-coverage-warnings
   "detect-incompatible-features"
   #(orcbrew-format/detect-incompatible-features {"P" {:orcpub.dnd.e5/races {:r %}}})
   "stamp"                      #(orcbrew-format/stamp {"P" {:orcpub.dnd.e5/races {:r %}}})})

(defn- throws [f item]
  (try (dorun (tree-seq coll? seq (f item))) nil
       (catch Throwable e (.getMessage e))))

(deftest no-reader-throws-on-malformed-grants
  (let [failures (for [field [:ability-increases :save-proficiencies]
                       v malformed
                       [label f] readers
                       :let [item {:name "Bad" :key :bad field v}
                             msg (throws f item)]
                       :when msg]
                   [label field v msg])]
    (is (empty? failures) (with-out-str (doseq [f failures] (prn f))))))

(deftest a-feat-with-a-malformed-spread-never-throws
  (let [failures (for [v (filter vector? malformed)
                       :let [msg (throws #(opt5e/feat-option-from-cfg nil nil nil [] nil %)
                                         {:name "F" :key :f :ability-increases v
                                          :save-proficiencies v})]
                       :when msg]
                   [v msg])]
    (is (empty? failures) (with-out-str (doseq [f failures] (prn f))))))
