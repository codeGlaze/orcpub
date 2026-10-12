(ns orcpub.secret-accessors-test
  "Two settings can come from a mounted file as well as the environment, and must
   only ever be read through the accessor that knows that.

     SIGNATURE         -> config/signature         (/run/secrets/signature first)
     DATOMIC_PASSWORD  -> config/datomic-password  (/run/secrets/datomic_password first)

   Reading the environment directly still compiles, still passes every other
   test, and still works on any deployment that uses environment variables --
   which is why it survived review twice. It breaks only for the deployment that
   followed the Docker-secrets instructions in docker-compose.yaml: the secret is
   mounted, the variable is deliberately absent, and the direct read returns nil.
   In a container there is no .lein-env to mask it, so check-auth returns 500 and
   every authenticated call fails with a good secret sitting on disk.

   That is exactly what routes.clj did until 969cf644. orcpub.config already had
   the right accessor; the caller reached past it. A correct accessor nobody is
   obliged to use is worth nothing -- the lesson this branch learned about
   environ.core/env and then repeated one layer up.

   clj-kondo cannot catch this the way it catches bare environ reads:
   :discouraged-var matches VARS, and (env/value :signature) is an approved var
   with a particular argument. Distinguishing that needs a custom hook, so this
   scans the source instead. `lein test` already runs in CI, which is the only
   place enforcement matters."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def ^:private secret-keys
  {":signature" "config/signature"
   ":datomic-password" "config/datomic-password"})

(def ^:private allowed-file
  "orcpub/config.clj is where both accessors live, so it reads the keys legitimately."
  "config.clj")

(defn- clj-sources []
  (->> (file-seq (io/file "src"))
       (filter #(and (.isFile %) (str/ends-with? (.getName %) ".clj")))
       (remove #(= allowed-file (.getName %)))))

(defn- offences
  "[{:file :line :text :key}] for every direct env read of a secret-backed key.
   Comments are skipped: these keys are named in prose all over the codebase,
   including in the docstring above."
  []
  (for [f (clj-sources)
        [n line] (map-indexed vector (str/split-lines (slurp f)))
        [k reader] secret-keys
        :let [code (str/replace line #";.*$" "")]
        :when (re-find (re-pattern (str "\\(env/value\\s+" k "\\b")) code)]
    {:file (.getPath f) :line (inc n) :key k :accessor reader :text (str/trim line)}))

(deftest secrets-are-not-read-straight-from-the-environment
  (testing "every secret-backed key goes through its accessor, not env/value"
    (let [bad (offences)]
      (is (empty? bad)
          (str "These read a secret-backed setting directly, so a deployment using "
               "Docker secrets gets nil and all authentication fails:\n"
               (str/join "\n"
                         (for [{:keys [file line key] :as o} bad]
                           (format "  %s:%d  reads %s — use %s\n      %s"
                                   file line key (:accessor o) (:text o)))))))))

(deftest the-check-can-still-find-something
  ;; A scan that cannot match is a scan that passes forever. Prove the pattern
  ;; fires on the exact text it exists to reject, without needing a real offence
  ;; in the tree.
  (testing "the pattern matches a direct read"
    (is (re-find #"\(env/value\s+:signature\b" "(def x (env/value :signature))"))
    (is (re-find #"\(env/value\s+:datomic-password\b" "(env/value :datomic-password)")))

  (testing "and does not match the accessor it points people at"
    (is (nil? (re-find #"\(env/value\s+:signature\b" "(config/signature)"))))

  (testing "comments are stripped BEFORE matching, not ignored by the pattern"
    ;; The pattern matches prose either way. It is the strip in `offences` that
    ;; spares it, so that is what gets asserted -- claiming the pattern ignores
    ;; comments would be asserting something false.
    (let [line ";; never write (env/value :signature) here"]
      (is (re-find #"\(env/value\s+:signature\b" line)
          "the raw pattern does match, which is why the strip exists")
      (is (nil? (re-find #"\(env/value\s+:signature\b"
                         (str/replace line #";.*$" "")))
          "with the comment removed there is nothing left to match"))))


(deftest both-accessors-still-exist
  ;; If one is renamed, the message above would name a function nobody can find.
  (testing "the accessors this test points people at are real"
    (require 'orcpub.config)
    (is (some? (resolve 'orcpub.config/signature)))
    (is (some? (resolve 'orcpub.config/datomic-password)))))
