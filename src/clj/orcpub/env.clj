(ns orcpub.env
  "The one way to read an environment value. A BLANK VALUE IS AN ABSENT VALUE: environ returns
   \"\" for an exported-but-empty variable, and \"\" is truthy, so (or (env :k) d) never applies
   the default. Values are trimmed. .clj-kondo/config.edn fails `lein lint` on environ.core/env
   and System/getenv anywhere else. What each blank cost, measured: docs/kb/blank-env-values.md."
  (:require [clojure.string :as str]
            [environ.core :as environ]))

(defn value
  "The environment value for `k`, or nil when unset, empty or whitespace.

   With `default`, returns it in place of nil. Prefer this over (or (value k) d)
   so the blank rule is applied before the default, not after."
  ([k]
   (some-> (environ/env k) str/trim not-empty))
  ([k default]
   (or (value k) default)))

(defn flag?
  "True when `k` is exactly \"true\", case-insensitively; anything else (\"yes\", \"1\", blank,
   a typo) is false. equalsIgnoreCase compares per character, so the Turkish dotless i cannot
   affect it, and the literal goes first so nil returns false."
  [k]
  (.equalsIgnoreCase "true" (or (value k) "")))
