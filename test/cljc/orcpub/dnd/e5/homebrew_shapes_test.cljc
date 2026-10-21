(ns orcpub.dnd.e5.homebrew-shapes-test
  (:require #?(:clj  [clojure.test :refer [deftest testing is]]
               :cljs [cljs.test :refer-macros [deftest testing is]])
            [orcpub.dnd.e5.homebrew-shapes :as shapes]))

(def ^:private card [:map {:name :string :description :string}])
(def ^:private entry-shape
  [:map {:name :string
         :abilities [:map-of :keyword :number]
         :languages [:coll :string]
         :traits [:list card]
         :size [:or :keyword :string]}])

(defn- clean [v] (shapes/clean-value entry-shape v []))

(deftest a-sound-entry-comes-back-identical
  (let [e {:name "Elf" :abilities {:dex 2} :languages #{"Elvish"} :traits [{:name "Keen"}]
           :size :medium :unlisted 42}
        r (clean e)]
    (is (identical? e (:value r)) "the same object, so nothing downstream recomputes")
    (is (empty? (:findings r)))))

(deftest a-lone-item-where-a-list-belongs-is-wrapped
  (let [r (clean {:traits {:name "Keen"}})]
    (is (= [{:name "Keen"}] (get-in r [:value :traits])))
    (is (= [{:path [:traits] :action :fixed :found "a map" :wanted "a list of a map"}]
           (:findings r)))))

(deftest what-cannot-be-read-is-read-past-and-named
  (testing "a field"
    (let [r (clean {:name "Elf" :abilities 42})]
      (is (= {:name "Elf"} (:value r)))
      (is (= [{:path [:abilities] :action :unreadable :found "a number"
               :wanted "a map of a keyword to a number"}]
             (:findings r)))))
  (testing "an element of a list, keeping the rest"
    (let [r (clean {:traits [{:name "Keen"} 42 {:name "Trance"}]})]
      (is (= [{:name "Keen"} {:name "Trance"}] (get-in r [:value :traits])))
      (is (= [[:traits 1]] (map :path (:findings r))))))
  (testing "a value inside a map, keeping the rest"
    (let [r (clean {:abilities {:dex 2 :str "x"}})]
      (is (= {:dex 2} (get-in r [:value :abilities])))
      (is (= [[:abilities :str]] (map :path (:findings r))))))
  (testing "a field inside an element"
    (let [r (clean {:traits [{:name 42 :description "d"}]})]
      (is (= [{:description "d"}] (get-in r [:value :traits])))
      (is (= [[:traits 0 :name]] (map :path (:findings r)))))))

(deftest every-junk-value-is-handled-and-never-throws
  (doseq [v [nil 42 "x" true :kw [] [42] ["x"] [{}] {} {:a 1} #{42} (list 1) 1.5]
          field [:name :abilities :languages :traits :size]]
    (let [r (clean {field v})]
      (is (map? (:value r)) (pr-str field v))
      (is (shapes/fits? entry-shape (:value r)) (str "the result fits: " (pr-str field v))))))

(deftest a-set-stays-a-set
  (is (= #{"Elvish"} (get-in (clean {:languages #{"Elvish" 42}}) [:value :languages]))))
