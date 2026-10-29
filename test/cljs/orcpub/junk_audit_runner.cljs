(ns orcpub.junk-audit-runner
  "Runs only orcpub.dnd.e5.junk-pack-test, for test/e2e/junk-pack-audit.js: the audit's pages load
   this build instead of the whole browser suite."
  (:require [cljs.test :refer-macros [run-tests]]
            [re-frame.core :as rf]
            [orcpub.dnd.e5.homebrew-shapes :as shapes]
            [orcpub.dnd.e5.junk-pack-test]))

;; as in orcpub.test-runner: building the template must not start the autosave watcher
(rf/reg-fx :orcpub.dnd.e5.autosave-fx/ensure-template-cache (fn [_]))

;; the audit reads subscriptions outside a component on purpose, thousands of times: without this
;; each read logs a warning, and carrying those to the runner took most of its time
(rf/set-loggers! {:warn (fn [& _])})

(defmethod cljs.test/report [:cljs.test/default :end-run-tests] [m]
  (js/console.log "JUNKDONE" (pr-str (select-keys m [:test :pass :fail :error]))))

;; ?unguarded: the shape check lets everything through, so the junk reaches our own code. What
;; fails then is what containment and the hardened helpers must catch (homebrew-guard.md, 5).
(when (re-find #"[?&]unguarded" (str (.-search js/location)))
  (set! shapes/clean-library (fn [library & _] {:library library :findings []})))

(run-tests 'orcpub.dnd.e5.junk-pack-test)
