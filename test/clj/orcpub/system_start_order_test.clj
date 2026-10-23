(ns orcpub.system-start-order-test
  "In production the web server's start blocks for as long as the server runs, so every other
   component has to start before it or it never starts at all."
  (:require [clojure.test :refer [deftest is]]
            [com.stuartsierra.component :as component]
            [com.stuartsierra.dependency :as dep]
            [orcpub.system :as system]))

(defn- start-order
  "The keys of system map `s` in the order component/start-system starts them."
  [s]
  (sort (dep/topo-comparator (component/dependency-graph s (keys s))) (keys s)))

(deftest every-component-starts-before-the-web-server
  (let [order (vec (start-order (system/system :prod)))
        after (subvec order (inc (.indexOf order :pedestal)))]
    (is (empty? after)
        (str "these start after :pedestal, whose start blocks in production, so they never start: "
             (pr-str after) " (add them to :pedestal's component/using in system.clj)"))))
