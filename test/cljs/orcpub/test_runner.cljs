(ns orcpub.test-runner
  (:require [cljs.test :refer-macros [run-tests]]
            [re-frame.core :as rf]
            ;; .cljc tests (run on both JVM and CLJS)
            [orcpub.common-test]
            [orcpub.dnd.e5.homebrew-guard-test]
            [orcpub.dnd.e5.event-utils-test]
            [orcpub.dnd.e5.compute-test]
            [orcpub.dnd.e5.hunter-evasion-test]
            ;; The spell page packer and row annotations run in the browser --
            ;; the builder decides the layout -- so their tests run here too.
            [orcpub.dnd.e5.spell-packing-test]
            [orcpub.registration-test]
            [orcpub.image-url-test]
            [orcpub.whats-new-test]
            ;; layout follows the window width, not just the device
            [orcpub.user-agent-test]
            [orcpub.dnd.e5.spell-annotations-test]
            ;; CLJS-only re-frame integration tests (events-test now also holds
            ;; the toggle-corruption stress harness)
            [orcpub.dnd.e5.events-test]
            [orcpub.dnd.e5.subs-test]
            [orcpub.dnd.e5.equipment-subs-test]
            [orcpub.dnd.e5.filtered-list-reactivity-test]
            [orcpub.dnd.e5.built-character-debounce-test]
            [orcpub.dnd.e5.content-reconciliation-test]
            ;; the save/key lifecycle: save twice, rename, restore a draft, land on a taken key
            [orcpub.dnd.e5.homebrew-save-lifecycle-test]
            [orcpub.dnd.e5.reference-web-test]
            [orcpub.dnd.e5.views-test]
            [orcpub.character-builder-test]
            ;; storage layer (resilient loader read path)
            [orcpub.dnd.e5.db-test]
            ;; orcbrew import/export validation
            [orcpub.dnd.e5.orcbrew-validation-test]
            ;; encrypted share snapshots use Web Crypto, so they run only here
            [orcpub.dnd.e5.share-url-test]))


;; Building the character template starts a watcher that recomputes it on every app-db change and
;; dispatches into whichever test runs next. Tests that need the request capture it (events-test
;; effects-of), which overrides this.
(rf/reg-fx :orcpub.dnd.e5.autosave-fx/ensure-template-cache (fn [_]))

(defn -main []
  (run-tests 'orcpub.common-test
   'orcpub.dnd.e5.homebrew-guard-test
             'orcpub.dnd.e5.event-utils-test
             'orcpub.dnd.e5.compute-test
             'orcpub.dnd.e5.hunter-evasion-test
             'orcpub.registration-test
             'orcpub.dnd.e5.spell-packing-test
             'orcpub.image-url-test
             'orcpub.whats-new-test
             'orcpub.user-agent-test
             'orcpub.dnd.e5.spell-annotations-test
             'orcpub.dnd.e5.events-test
             'orcpub.dnd.e5.subs-test
             'orcpub.dnd.e5.equipment-subs-test
             'orcpub.dnd.e5.filtered-list-reactivity-test
             'orcpub.dnd.e5.built-character-debounce-test
             'orcpub.dnd.e5.content-reconciliation-test
             'orcpub.dnd.e5.homebrew-save-lifecycle-test
             'orcpub.dnd.e5.reference-web-test
             'orcpub.dnd.e5.views-test
             'orcpub.character-builder-test
             'orcpub.dnd.e5.db-test
             'orcpub.dnd.e5.orcbrew-validation-test
             'orcpub.dnd.e5.share-url-test))

;; Auto-run when figwheel reloads
(defn ^:after-load on-reload []
  (-main))

(-main)
