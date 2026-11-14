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

;; ─── Checking operator settings ────────────────────────────────────
;; A bad operator setting falls back to its default with a warning naming the setting, so a typo
;; costs that value, not a page or the server.

(defonce ^{:doc "Branding values that fell back to their default, as {:var :value :why}."}
  ignored
  (atom []))

(defn warn!
  "Prints that `setting` (a keyword like :app-help-url) was ignored, and why, and adds
   {:var :value :why} to `ignored` for the boot report. Returns nil."
  [setting value why]
  (let [var-name (.toUpperCase ^String (str/replace (name setting) "-" "_") java.util.Locale/ROOT)]
    (swap! ignored conj {:var var-name :value (pr-str value) :why why})
    (println (str "branding: " var-name " ignored (" why "): " (pr-str value)))))

(defn checked-url
  "`value` of `setting` when it is blank, an http(s) address, or a path on this site (/image/x.svg);
   else `default`, with a warning."
  [setting value default]
  (cond
    (str/blank? value) value
    (or (re-matches #"https?://[A-Za-z0-9.-]+(:\d+)?([/?#]\S*)?" value)
        (re-matches #"/(?![/\\])\S*" value)) value
    :else (do (warn! setting value "not a web address or site path") default)))

(defn checked-email
  "`value` of `setting` when it is blank or looks like an email address; else `default`, warned."
  [setting value default]
  (if (or (str/blank? value) (re-matches #"[^@\s]+@[^@\s]+\.[^@\s]+" value))
    value
    (do (warn! setting value "not an email address") default)))

(defn checked-int
  "`value` of `setting` as a positive whole number; `default` when unset, or with a warning when it
   is anything else."
  [setting value default]
  (if (str/blank? value)
    default
    (let [n (try (Integer/parseInt (str/trim value)) (catch NumberFormatException _ nil))]
      (if (and n (pos? n)) n (do (warn! setting value "not a positive whole number") default)))))
