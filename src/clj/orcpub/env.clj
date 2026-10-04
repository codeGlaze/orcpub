(ns orcpub.env
  "The one way to read an environment value.

   Exists because the same defect was found independently at five sites, which
   is what a missing abstraction looks like. The rule is one line -- A BLANK
   VALUE IS AN ABSENT VALUE -- and it is not the obvious thing to write:

     (or (env :app-name) \"OrcPub\")

   reads correctly and is wrong. Environ returns \"\" for a variable that is
   exported but empty, and \"\" is TRUTHY in Clojure, so it wins the `or` and the
   default never applies. .env.example ships nine keys with empty values, so
   this is the documented state of an unset optional setting, not an edge case.

   What that cost, measured before this namespace existed:

     SIGNATURE=        tokens signed and VERIFIED against the empty string. The
                       guard meant to catch it was (when-not jwt-secret ...), a
                       nil check, so a blank secret walked past it and forged
                       tokens were accepted. See routes.clj.
     DATOMIC_URL=      get-datomic-uri returned \"?password=\" -- not a URI.
     DATOMIC_PASSWORD= \"?password=\" appended to an otherwise valid URI.
     CSP_POLICY=       silently selected the permissive fallback rather than the
                       documented strict default.
     EMAIL_SERVER_PORT= (Integer/parseInt \"\") threw at send time.

   Several of those sites already guarded with not-empty -- on the System/getenv
   branch, while leaving the (env ...) branch bare. Environ reads environment
   variables itself and answers first, so the guard sat on the path that never
   runs. Being careful was not enough; the care went to the wrong line.

   A helper alone does not fix this. orcpub.config already had a `signature`
   accessor and routes.clj read (environ/env :signature) raw anyway, four times,
   which is how the token bug survived. So .clj-kondo/config.edn marks
   environ.core/env and System/getenv as discouraged everywhere except here, and
   `lein lint` fails the build on them. The rule is the enforcement; this
   namespace is only where the exception lives.

   Values are trimmed, matching config/read-secret: a value that is only
   whitespace is not one."
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
  "True when `k` is exactly \"true\", case-insensitively. Everything else -- \"yes\",
   \"1\", blank, a typo -- is false.

   Matches the server's own comparison so callers cannot invent their own
   truthiness. equalsIgnoreCase compares per character rather than by locale
   casing rules, so it is immune to the Turkish dotless-i that this branch
   exists to fix; the literal goes first so a nil value returns false rather
   than throwing."
  [k]
  (.equalsIgnoreCase "true" (or (value k) "")))
