(ns orcpub.robots-txt-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.java.io :as io]
            [clojure.string :as s]))

(defn- groups
  "robots.txt as [[agents rules] ...]: consecutive User-agent lines share the rules after them."
  [text]
  (->> (s/split-lines text)
       (map #(s/trim (s/replace % #"#.*" "")))
       (remove s/blank?)
       (map #(let [[k v] (s/split % #":\s*" 2)] [(s/lower-case k) v]))
       (partition-by #(= "user-agent" (first %)))
       (partition 2)
       (map (fn [[agents rules]] [(set (map second agents)) (vec rules)]))))

(deftest only-link-previews-may-crawl
  (let [gs (groups (slurp (io/resource "public/robots.txt")))
        rules-for (fn [agent] (some (fn [[agents rules]] (when (agents agent) rules)) gs))]
    (is (= [["disallow" "/"]] (rules-for "*")))
    (doseq [bot ["Discordbot" "Twitterbot" "Slackbot-LinkExpanding" "TelegramBot" "Mastodon"]]
      (is (= [["allow" "/"]] (rules-for bot)) bot))
    (is (= 2 (count gs)) "nothing else is named")))
