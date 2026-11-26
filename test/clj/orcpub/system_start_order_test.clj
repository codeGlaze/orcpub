(ns orcpub.system-start-order-test
  "In production the web server's start blocks for as long as the server runs, so every other
   component has to start before it or it never starts at all."
  (:require [clojure.test :refer [deftest is]]
            [com.stuartsierra.component :as component]
            [com.stuartsierra.dependency :as dep]
            [orcpub.system :as system]))

(deftest every-component-starts-before-the-web-server
  (let [s (system/system :prod)
        graph (component/dependency-graph s (keys s))
        before-pedestal (dep/transitive-dependencies graph :pedestal)
        missing (sort (remove (conj before-pedestal :pedestal) (keys s)))]
    (is (empty? missing)
        (str "these are not started before :pedestal, whose start blocks in production, so they may "
             "never start: " (pr-str missing) " (add them to :pedestal's component/using in system.clj)"))))
