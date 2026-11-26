(ns orcpub.dependencies.build-shape-test
  "Figwheel is a dev tool and must never reach the production jar: its own Jetty and Ring sit
   beside Pedestal's on the server's classpath and break it (docs/kb/figwheel-jetty12.md). These
   checks read project.clj and docker/Dockerfile as written."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn- project
  "project.clj's defproject form as a map of its keys (:dependencies, :aliases, :profiles ...)."
  []
  (with-open [r (java.io.PushbackReader. (io/reader "project.clj"))]
    (binding [*read-eval* false]
      (->> (repeatedly #(read r false ::eof))
           (take-while #(not= ::eof %))
           (some #(when (and (seq? %) (= 'defproject (first %))) %))
           (drop 3)
           (apply hash-map)))))

(defn- dep-names
  "The artifact symbols in a lein :dependencies vector `deps`."
  [deps]
  (set (map first deps)))

(defn- figwheel?
  "Whether artifact symbol `a` is one of figwheel's (com.bhauman/*)."
  [a]
  (= "com.bhauman" (namespace a)))

(deftest figwheel-is-a-dev-dependency-only
  (let [p (project)]
    (testing "not in the main :dependencies, which all go into the uberjar"
      (is (contains? (dep-names (:dependencies p)) 'org.clojure/clojurescript)
          "project.clj was read: its main :dependencies are there")
      (is (empty? (filter figwheel? (dep-names (:dependencies p))))))
    (testing "still in :dev-config, for hot reload and the fig:* builds"
      (is (contains? (dep-names (get-in p [:profiles :dev-config :dependencies]))
                     'com.bhauman/figwheel-main)))))

(deftest the-production-bundle-is-built-without-figwheel
  (let [aliases (:aliases (project))
        cljs-prod (get aliases "cljs:prod")]
    (testing "cljs:prod runs cljs.main without the :dev profile"
      (is (some #{"cljs.main"} cljs-prod))
      (is (some #(str/includes? (str %) "-dev") cljs-prod)))
    (testing "the build alias and fig:prod go through cljs:prod"
      (is (some #{"cljs:prod," "cljs:prod"} (get aliases "build")))
      (is (= ["cljs:prod"] (get aliases "fig:prod"))))
    (testing "the Docker image builds its bundle with cljs:prod, never figwheel.main"
      (let [runs (filter #(str/starts-with? (str/trim %) "RUN ")
                         (str/split-lines (slurp "docker/Dockerfile")))]
        (is (some #(str/includes? % "lein cljs:prod") runs))
        (is (not-any? #(str/includes? % "figwheel") runs))))))
