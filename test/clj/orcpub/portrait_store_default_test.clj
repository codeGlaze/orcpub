(ns orcpub.portrait-store-default-test
  "Where the portrait store writes when nobody says (no fixture here: the store tests redirect it)."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [orcpub.config :as config]
            [orcpub.env :as env]))

(deftest the-default-store-is-writable-in-the-image
  ;; the image runs from / as a user that can write only /tmp, so a relative default lands in
  ;; an unwritable /data/portraits
  (with-redefs [env/value (fn [& _] nil)]
    (is (str/starts-with? (config/get-portrait-store-dir) (System/getProperty "java.io.tmpdir")))))
