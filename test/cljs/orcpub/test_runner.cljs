(ns orcpub.test-runner
  (:require [cljs.test :refer-macros [run-tests]]
            ;; .cljc tests (run on both JVM and CLJS)
            [orcpub.dnd.e5.event-utils-test]
            [orcpub.dnd.e5.compute-test]
            ;; CLJS-only re-frame integration tests
            [orcpub.dnd.e5.events-test]))

(defn -main []
  (run-tests 'orcpub.dnd.e5.event-utils-test
             'orcpub.dnd.e5.compute-test
             'orcpub.dnd.e5.events-test))

;; Auto-run when figwheel reloads
(defn ^:after-load on-reload []
  (-main))

(-main)
