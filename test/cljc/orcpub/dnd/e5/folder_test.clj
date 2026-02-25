(ns orcpub.dnd.e5.folder-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.spec.alpha :as spec]
            [orcpub.dnd.e5.folder :as folder5e]))

;; ---------------------------------------------------------------------------
;; ::folder5e/name spec

(deftest folder-name-valid
  (is (spec/valid? ::folder5e/name "Campaign 1")))

(deftest folder-name-empty-string-is-valid
  ;; spec only requires string? — emptiness is a UI concern, not a spec constraint
  (is (spec/valid? ::folder5e/name "")))

(deftest folder-name-requires-string
  (is (not (spec/valid? ::folder5e/name 42)))
  (is (not (spec/valid? ::folder5e/name nil)))
  (is (not (spec/valid? ::folder5e/name :keyword))))

;; ---------------------------------------------------------------------------
;; ::folder5e/character-id spec

(deftest character-id-valid-int
  (is (spec/valid? ::folder5e/character-id 1))
  (is (spec/valid? ::folder5e/character-id 987654321)))

(deftest character-id-requires-int
  (is (not (spec/valid? ::folder5e/character-id "1")))
  (is (not (spec/valid? ::folder5e/character-id nil))))

;; ---------------------------------------------------------------------------
;; ::folder5e/character-ids spec

(deftest character-ids-valid-empty-collection
  (is (spec/valid? ::folder5e/character-ids [])))

(deftest character-ids-valid-collection-of-ints
  (is (spec/valid? ::folder5e/character-ids [1 2 3])))

(deftest character-ids-invalid-contains-non-int
  (is (not (spec/valid? ::folder5e/character-ids ["abc"])))
  (is (not (spec/valid? ::folder5e/character-ids [1 "two"]))))

;; ---------------------------------------------------------------------------
;; ::folder5e/folder spec

(deftest folder-valid-with-required-name
  (is (spec/valid? ::folder5e/folder {::folder5e/name "My Folder"})))

(deftest folder-invalid-when-name-missing
  (is (not (spec/valid? ::folder5e/folder {}))))

(deftest folder-invalid-when-name-wrong-type
  (is (not (spec/valid? ::folder5e/folder {::folder5e/name 123}))))

(deftest folder-explain-data-nil-when-valid
  (is (nil? (spec/explain-data ::folder5e/folder {::folder5e/name "Adventure"}))))

(deftest folder-explain-data-non-nil-when-invalid
  (is (some? (spec/explain-data ::folder5e/folder {})))
  (is (some? (spec/explain-data ::folder5e/folder {::folder5e/name nil}))))
