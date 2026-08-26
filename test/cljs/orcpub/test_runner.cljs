(ns orcpub.test-runner
  (:require [cljs.test :as cljs.test :refer-macros [run-tests]]
            ;; .cljc tests (run on both JVM and CLJS)
            [orcpub.common-test]
            [orcpub.dnd.e5.event-utils-test]
            [orcpub.dnd.e5.compute-test]
            [orcpub.dnd.e5.hunter-evasion-test]
            ;; CLJS-only re-frame integration tests (events-test now also holds
            ;; the toggle-corruption stress harness)
            [orcpub.dnd.e5.events-test]
            [orcpub.dnd.e5.subs-test]
            [orcpub.dnd.e5.item-classification-subs-test]
            [orcpub.dnd.e5.item-flow-test]
            [orcpub.dnd.e5.signin-fetch-test]
            [orcpub.dnd.e5.content-reconciliation-test]
            [orcpub.dnd.e5.views-test]
            ;; storage layer (resilient loader read path)
            [orcpub.dnd.e5.db-test]
            ;; orcbrew import/export validation
            [orcpub.dnd.e5.orcbrew-validation-test]))

;; Signal completion to the Node runner. cljs.test/async tests report long
;; after -main returns, so a runner that exits when the bundle finishes loading
;; sees neither their results nor anything after them — and exits 0. The
;; default :summary report still prints "Ran N tests..."; this only adds the
;; end-of-run signal.
(defmethod cljs.test/report [:cljs.test/default :end-run-tests] [m]
  (when (exists? js/globalThis)
    (set! (.-__cljsTestsFailed js/globalThis) (not (cljs.test/successful? m)))
    (set! (.-__cljsTestsDone js/globalThis) true)))

(defn -main []
  (run-tests 'orcpub.common-test
             'orcpub.dnd.e5.event-utils-test
             'orcpub.dnd.e5.compute-test
             'orcpub.dnd.e5.hunter-evasion-test
             'orcpub.dnd.e5.events-test
             'orcpub.dnd.e5.subs-test
             'orcpub.dnd.e5.item-classification-subs-test
             'orcpub.dnd.e5.item-flow-test
             'orcpub.dnd.e5.signin-fetch-test
             'orcpub.dnd.e5.content-reconciliation-test
             'orcpub.dnd.e5.views-test
             'orcpub.dnd.e5.db-test
             'orcpub.dnd.e5.orcbrew-validation-test))

;; Auto-run when figwheel reloads
(defn ^:after-load on-reload []
  (-main))

(-main)
