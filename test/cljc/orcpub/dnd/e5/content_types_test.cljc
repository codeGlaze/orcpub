(ns orcpub.dnd.e5.content-types-test
  "Phase 4a of the content-extensibility work (docs/kb/content-extensibility-plan.md).

   Validates the content-types registry against reality so the later wiring loops can
   trust it: every :spec must be a registered spec, every :plugin-key must satisfy the
   orcbrew `::e5/content-keyword` contract (the orcpub.dnd.e5 namespace requirement that
   existing .orcbrew imports depend on), and identity fields must be unique.

   Requiring the domain namespaces below loads their spec/defs so `spec/get-spec` can
   confirm each registry :spec actually exists."
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.spec.alpha :as spec]
            [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.content-types :as ct]
            ;; side effect: register the specs the registry references
            [orcpub.dnd.e5.spells]
            [orcpub.dnd.e5.monsters]
            [orcpub.dnd.e5.encounters]
            [orcpub.dnd.e5.backgrounds]
            [orcpub.dnd.e5.languages]
            [orcpub.dnd.e5.classes]
            [orcpub.dnd.e5.selections]
            [orcpub.dnd.e5.feats]
            [orcpub.dnd.e5.races]))

(deftest registry-is-internally-consistent
  (let [cts ct/content-types]
    (testing "covers the known plugin-based homebrew types"
      (is (= 13 (count cts))))
    (doseq [field [:id :type-name :builder-item :spec :plugin-key :route-kw
                   :route-seg :local-storage-key]]
      (testing (str "every descriptor has " field)
        (is (every? #(contains? % field) cts))))
    (doseq [field [:id :builder-item :plugin-key :route-kw :route-seg :local-storage-key]]
      (testing (str field " is unique across types")
        (let [vs (map field cts)]
          (is (= (count vs) (count (distinct vs)))))))
    (testing "by-id index is complete"
      (is (= (set (map :id cts)) (set (keys ct/by-id)))))))

(deftest plugin-keys-satisfy-orcbrew-contract
  (testing "every :plugin-key is a valid ::e5/content-keyword — orcbrew import requires
            the orcpub.dnd.e5 namespace, so this guards backward compatibility"
    (doseq [{:keys [id plugin-key]} ct/content-types]
      (is (spec/valid? ::e5/content-keyword plugin-key)
          (str id " plugin-key " plugin-key " must satisfy ::e5/content-keyword")))))

(deftest specs-resolve-and-keys-are-well-formed
  (testing "every :spec names a registered spec (catches a wrong/renamed spec keyword)"
    (doseq [{:keys [id spec]} ct/content-types]
      (is (some? (spec/get-spec spec))
          (str id " :spec " spec " must be a registered spec"))))
  (testing "key fields are qualified keywords; route-kw is a keyword"
    (doseq [{:keys [builder-item spec plugin-key route-kw]} ct/content-types]
      (is (qualified-keyword? builder-item))
      (is (qualified-keyword? spec))
      (is (qualified-keyword? plugin-key))
      (is (keyword? route-kw)))))
