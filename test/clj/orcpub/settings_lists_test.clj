(ns orcpub.settings-lists-test
  "A setting the server reads can be set wherever the server runs: it is in the settings registry,
   and in the three lists an operator's value travels through (.env.example, the Docker Compose app
   service, the Swarm stack generator). A name missing from Compose or Swarm is dropped on its way
   into the container, with nothing said."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [orcpub.config :as config]))

(def ^:private dev-only
  "Names the server reads that are not operator settings: development switches, and HOSTNAME,
   which Docker and Swarm set themselves."
  #{"ORCPUB_HTTP_HOST" "PORTRAIT_DEV_SOLID" "HOSTNAME"})

(defn- env-name
  "Keyword `k` (:email-ssl) as its environment name (EMAIL_SSL)."
  [k]
  (s/upper-case (s/replace (name k) "-" "_")))

(defn- names-read
  "Every environment name the server source reads through orcpub.env or config's readers."
  []
  (set (for [f (file-seq (io/file "src/clj"))
             :when (s/ends-with? (.getName f) ".clj")
             :let [src (s/replace (slurp f) #"(?m);.*$" "")]
             n (concat (map (comp env-name second) (re-seq #"env/(?:value|flag\?) :([a-z0-9-]+)" src))
                       (map second (re-seq #"env-raw \"([A-Z0-9_]+)\"" src))
                       (map second (re-seq #"positive-int-env \[\"([A-Z0-9_]+)\"" src)))]
         n)))

(def ^:private operator-settings
  "Every name the boot report covers: the registry's rows and the branding names."
  (into (set (map :var config/settings)) config/branding-vars))

(defn- compose-app-names
  "The names in docker-compose.yaml's orcpub service environment list."
  []
  (let [lines (s/split-lines (slurp "docker-compose.yaml"))
        service (drop-while #(not= "  orcpub:" %) lines)
        env (->> service (drop-while #(not (re-matches #"\s+environment:\s*" %))) rest
                 (take-while #(or (re-matches #"\s{6,}.*" %) (s/blank? %))))]
    (set (keep #(second (re-matches #"\s+([A-Z][A-Z0-9_]*):.*" %)) env))))

(defn- swarm-app-names
  "The names in scripts/swarm.sh's _a_env_defaults list."
  []
  (let [src (slurp "scripts/swarm.sh")
        block (second (re-find #"(?s)_a_env_defaults=\((.*?)\n\s*\)" src))]
    (set (map second (re-seq #"\"([A-Z][A-Z0-9_]*):" block)))))

(defn- env-example-names
  "The names .env.example sets or shows commented out (`NAME=` or `# NAME=`)."
  []
  (set (keep #(second (re-matches #"#?\s*([A-Z][A-Z0-9_]*)=.*" %))
             (s/split-lines (slurp ".env.example")))))

(deftest every-name-the-server-reads-is-a-reported-setting
  (let [missing (sort (remove (into operator-settings dev-only) (names-read)))]
    (is (empty? missing) (str "read by the server but not in config/settings or branding-vars: " (pr-str missing)))))

(deftest every-setting-reaches-a-compose-container
  (let [missing (sort (remove (compose-app-names) operator-settings))]
    (is (empty? missing) (str "not in docker-compose.yaml's orcpub environment: " (pr-str missing)))))

(deftest every-setting-reaches-a-swarm-container
  (let [missing (sort (remove (swarm-app-names) operator-settings))]
    (is (empty? missing) (str "not in scripts/swarm.sh _a_env_defaults: " (pr-str missing)))))

(deftest every-setting-is-in-the-env-example
  (testing "an operator reading .env.example sees every setting"
    (let [missing (sort (remove (env-example-names) operator-settings))]
      (is (empty? missing) (str "not in .env.example: " (pr-str missing))))))

(deftest the-list-readers-read-the-files
  (testing "each list parses to the names it is known to hold, so an empty parse cannot pass"
    (is (contains? (compose-app-names) "SIGNATURE"))
    (is (contains? (swarm-app-names) "SIGNATURE"))
    (is (contains? (env-example-names) "SIGNATURE"))
    (is (contains? (names-read) "EMAIL_SERVER_URL"))))
