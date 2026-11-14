(ns orcpub.fork-contract-test
  "The fork files (src/**/fork/) hold what one deployment sets differently from another, and a
   fork replaces each one whole (docs/kb/fork-customization.md). Shared code may use only the
   vars listed in `contract`, and every fork file must define each var listed for its namespace,
   so a fork's copy that lacks a new setting or hook fails here instead of breaking quietly."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as str]))

(def contract
  "Each fork namespace's settings and hooks: everything shared code may use from it. A new entry
   is something every fork must supply; logic every fork needs alike belongs in shared code."
  {"orcpub.fork.auth" '#{record-last-login-at-registration? token-lifetime-hours track-last-login?
                         verification-display-name}
   "orcpub.fork.branding" '#{app-name app-tagline app-url default-page-title logo-path og-image-filename
                             copyright-holder copyright-year copyright-url email-sender-name
                             email-signoff email-from-address support-email report-recipient help-url
                             social-links registration-logo-class restrict-print-to-owner?
                             field-limits}
   "orcpub.fork.integrations" '#{head-tags csp-domains client-config track-page-view! on-app-mount!
                                 track-character-list! content-slot supporter-link support-banner
                                 sheet-styles pdf-options-slot share-url}
   "orcpub.fork.privacy-content" '#{privacy-policy-section}
   "orcpub.fork.splash" '#{edition-label generator-buttons header-generator-entries
                           legal-footer-links logo-width-class}
   "orcpub.fork.user-data" '#{enrich-response registration-defaults}})

(defn- source-files
  "Every Clojure source file under src/ and web/, as java.io.Files."
  []
  (->> ["src" "web"]
       (map io/file)
       (filter #(.exists ^java.io.File %))
       (mapcat file-seq)
       (filter #(re-find #"\.clj[sc]?$" (.getName ^java.io.File %)))))

(defn- fork-file?
  "Whether `f` is one of the fork files."
  [f]
  (str/includes? (str/replace (.getPath ^java.io.File f) "\\" "/") "/fork/"))

(defn- used-fork-vars
  "The fork vars `text` (one source file) uses, as {fork-ns #{var-symbol}}: through an alias
   (`branding/app-name`) or a :refer."
  [text]
  (merge-with
   set/union
   (apply merge-with set/union {}
          (for [[_ fork-ns al] (re-seq #"\[(orcpub\.fork\.[\w.-]+)\s+:as\s+([\w.-]+)" text)]
            {fork-ns (set (map (comp symbol second)
                               (re-seq (re-pattern (str "(?<![\\w.-])" (java.util.regex.Pattern/quote al)
                                                        "/([\\w?!*<>=-]+)"))
                                       text)))}))
   (apply merge-with set/union {}
          (for [[_ fork-ns refers] (re-seq #"\[(orcpub\.fork\.[\w.-]+)[^\]]*?:refer\s+\[([^\]]*)\]" text)]
            {fork-ns (set (map symbol (str/split (str/trim refers) #"\s+")))}))))

(defn- fork-ns-name
  "The namespace a fork file declares, or nil."
  [text]
  (some->> (re-find #"\(ns\s+(orcpub\.fork\.[\w.-]+)" text) second))

(defn- defined-vars
  "The vars `text` (one source file) defines with def, defn, defonce or a defn-like macro."
  [text]
  (set (map (comp symbol second) (re-seq #"\(def[\w-]*\s+(?:\^\{[^}]*\}\s+|\^:[\w-]+\s+)*([\w?!*<>=-]+)" text))))

(deftest shared-code-uses-only-the-fork-contract
  (let [used (apply merge-with set/union {}
                    (for [f (source-files) :when (not (fork-file? f))]
                      (used-fork-vars (slurp f))))]
    (doseq [[fork-ns vars] used
            :let [extra (set/difference vars (get contract fork-ns #{}))]]
      (is (empty? extra)
          (str fork-ns " is used for " (sort extra) ", which the fork contract does not list. "
               "If every fork must supply it, add it to `contract`; if it is logic every fork "
               "needs alike, move it into shared code.")))))

(deftest every-fork-file-supplies-its-contract
  (let [defined (apply merge-with set/union {}
                       (for [f (source-files) :when (fork-file? f)
                             :let [text (slurp f) n (fork-ns-name text)] :when n]
                         {n (defined-vars text)}))]
    (doseq [[fork-ns vars] contract
            :let [missing (set/difference vars (get defined fork-ns #{}))]]
      (testing fork-ns
        (is (empty? missing)
            (str fork-ns " lacks " (sort missing) ": a fork's copy must define every setting "
                 "and hook shared code uses."))))))
