(ns orcpub.compose-env-test
  "docker-compose passes the app an explicit list of settings, so one left off the list never
   reaches the server however it is set in .env."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]))

(defn- app-environment
  "The variable names in the app service's environment block of docker-compose.yaml."
  []
  (let [lines (str/split-lines (slurp "docker-compose.yaml"))
        block (->> lines
                   (drop-while #(not (re-find #"^  orcpub:" %)))
                   (drop-while #(not (re-find #"^    environment:" %)))
                   rest
                   (take-while #(re-find #"^      |^\s*$|^\s*#" %)))]
    (set (keep #(second (re-find #"^      ([A-Z_]+):" %)) block))))

(deftest the-portrait-settings-reach-the-container
  (let [env (app-environment)]
    (is (contains? env "SIGNATURE") "the block was found")
    (doseq [v ["PORTRAIT_ARTISTS" "PROFILE_ENCRYPTION_KEYS" "APP_URL" "EMAIL_ADMIN_TO"]]
      (is (contains? env v) (str v " is passed to the app")))))
