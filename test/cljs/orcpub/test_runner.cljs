(ns orcpub.test-runner
  (:require [cljs.test :refer-macros [run-tests]]
            ;; .cljc tests (run on both JVM and CLJS)
            [orcpub.dnd.e5.event-utils-test]
            [orcpub.dnd.e5.compute-test]
            ;; CLJS-only re-frame integration tests
            [orcpub.dnd.e5.events-test]
            [orcpub.dnd.e5.subs-test]
            [orcpub.dnd.e5.content-reconciliation-test]
            [orcpub.dnd.e5.draconic-ancestry-test]
            [orcpub.dnd.e5.dragonborn-ancestry-e2e-test]
            [orcpub.dnd.e5.simple-content-builder-test]
            [orcpub.dnd.e5.import-validation-test]
            [orcpub.dnd.e5.grant-vocabulary-cljs-test]))

(defn -main []
  (run-tests 'orcpub.dnd.e5.event-utils-test
             'orcpub.dnd.e5.compute-test
             'orcpub.dnd.e5.events-test
             'orcpub.dnd.e5.subs-test
             'orcpub.dnd.e5.content-reconciliation-test
             'orcpub.dnd.e5.draconic-ancestry-test
             'orcpub.dnd.e5.dragonborn-ancestry-e2e-test
             'orcpub.dnd.e5.simple-content-builder-test
             'orcpub.dnd.e5.import-validation-test
             'orcpub.dnd.e5.grant-vocabulary-cljs-test))

;; Auto-run when figwheel reloads
(defn ^:after-load on-reload []
  (-main))

(-main)
