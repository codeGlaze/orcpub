(ns orcpub.fixtures
  "Test fixture files, readable from JVM and browser tests alike."
  #?(:cljs (:require-macros [orcpub.fixtures])))

#?(:clj
   (defmacro fixture-text
     "The text of `path` (from the repository root), read when the test is compiled, so a browser
      test can use a fixture file too.
      GOTCHA: an edited fixture is read again only when a namespace using it recompiles."
     [path]
     (slurp path)))
