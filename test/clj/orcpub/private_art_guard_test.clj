(ns orcpub.private-art-guard-test
  "scripts/private-art.sh must refuse a branch whose pushes would reach GitHub, whichever setting
   sends them there. Each case runs the script in a throwaway repository with a fake pack, so it
   never gets past the guard into touching any art."
  (:require [clojure.test :refer [deftest is]]
            [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]])
  (:import [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]))

(def ^:private script (.getCanonicalPath (io/file "scripts/private-art.sh")))

(defn- refused?
  "Runs private-art.sh in a fresh repository on branch `art`, tracking a private remote, after
   running each of the git `settings` there. Returns whether the script refused for GitHub."
  [settings]
  (let [dir (str (Files/createTempDirectory "private-art" (make-array FileAttribute 0)))
        git #(apply sh "git" (concat % [:dir dir]))]
    (git ["init" "-q" "-b" "art"])
    (git ["-c" "user.name=t" "-c" "user.email=t@t" "commit" "-q" "--allow-empty" "-m" "start"])
    (git ["remote" "add" "gitea" "https://gitea.example/art.git"])
    (git ["remote" "add" "github" "https://github.com/example/art.git"])
    (git ["config" "branch.art.remote" "gitea"])
    (git ["config" "branch.art.merge" "refs/heads/art"])
    (doseq [s settings] (git s))
    (spit (io/file dir "pack.zip") "not a zip")
    (let [{:keys [err]} (sh "bash" script "update" "pack.zip" :dir dir)]
      (boolean (re-find #"REFUSED" err)))))

(deftest a-branch-that-pushes-to-github-is-refused
  (is (not (refused? [])) "tracking and pushing to the private remote: allowed past the guard")
  (is (refused? [["config" "branch.art.pushRemote" "github"]]) "a branch push setting naming GitHub")
  (is (refused? [["config" "remote.pushDefault" "github"]]) "a repository push default naming GitHub")
  (is (refused? [["config" "branch.art.remote" "github"]]) "tracking GitHub"))
