(ns orcpub.test-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [orcpub.dnd.e5.import-validation-test]))

(doo-tests 'orcpub.dnd.e5.import-validation-test)
