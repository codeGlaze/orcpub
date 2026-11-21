(ns orcpub.jetty-container-test
  "The production service map's Jetty context configurator, run on a real Jetty 12 servlet context:
   it must apply, compress responses, and keep the request-body caps."
  (:require [clojure.test :refer [deftest is testing]]
            [orcpub.system :as system])
  (:import (org.eclipse.jetty.ee10.servlet ServletContextHandler)
           (org.eclipse.jetty.server.handler.gzip GzipHandler)))

(deftest the-context-configurator-applies-to-a-jetty-12-context
  (let [configure (get-in system/prod-service-map [:container-options :context-configurator])
        c (configure (ServletContextHandler.))]
    (testing "responses are compressed"
      (is (some? (.getDescendant c GzipHandler))))
    (testing "request bodies stay capped"
      (is (= system/max-form-content-size (.getMaxFormContentSize c)))
      (is (= 200 (.getMaxFormKeys c))))))
