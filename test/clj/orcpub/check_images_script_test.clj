(ns orcpub.check-images-script-test
  "scripts/check-images.sh names a file carrying the licensed-art mark by its whole path, spaces
   included, so the annotation points at the right file. Runs the script in a throwaway repository."
  (:require [clojure.test :refer [deftest is]]
            [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]])
  (:import [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]))

(def ^:private script (.getCanonicalPath (io/file "scripts/check-images.sh")))

(deftest a-marked-file-with-a-space-in-its-name-is-named-whole
  (let [dir (str (Files/createTempDirectory "check-images" (make-array FileAttribute 0)))
        marked (io/file dir "docs" "my shot.png")]
    (sh "git" "init" "-q" :dir dir)
    (io/make-parents marked)
    (spit marked "an image carrying orcpub-art")
    (sh "git" "add" "-A" :dir dir)
    (let [{:keys [out]} (sh "bash" script :dir dir)]
      (is (re-find #"::error file=docs/my shot\.png::" out) out)
      (is (not (re-find #"::error file=docs/my::" out)) "not split at the space"))))
