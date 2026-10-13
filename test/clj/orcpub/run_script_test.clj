(ns orcpub.run-script-test
  "./run writes .env; settings that lock data in must survive a re-run."
  (:require [clojure.test :refer [deftest is]]
            [clojure.java.shell :refer [sh]]))

(deftest setup-keeps-an-existing-profile-key
  ;; `./run --force` sources the existing .env and then calls generate_env, so a key generated
  ;; unconditionally replaced the one every stored preferred name is encrypted with
  (let [script (str "set -e; source <(sed -n '/^generate_key_b64()/,/^}/p' run);"
                    " eval \"$(sed -n '/^generate_env()/,/^}/p' run | grep 'PROFILE_ENCRYPTION_KEYS=')\";"
                    " echo \"$PROFILE_ENCRYPTION_KEYS\"")
        kept (sh "bash" "-c" (str "PROFILE_ENCRYPTION_KEYS=k1:existing; " script))
        fresh (sh "bash" "-c" (str "unset PROFILE_ENCRYPTION_KEYS; " script))]
    (is (= "k1:existing\n" (:out kept)) "a key already set is kept")
    (is (re-matches #"k1:[A-Za-z0-9+/=]{40,}\n" (:out fresh)) "a new .env gets a fresh key")))
