(ns orcpub.startup-email
  "Emails the admin what a start means for the site: problems first, then this release's settings,
   then those worth setting. Sent on a release's first start, again when that list changes, and
   once more after a week while a problem remains. It remembers what it sent as setting names and
   states in startup-email.edn beside the startup report, never values.
   plan-email-and-alerts.md on agents/develop."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [orcpub.alerts :as alerts]
            [orcpub.config :as config]
            [orcpub.fork.branding :as branding]))

(def reminder-ms
  "How long a problem may stand before the one reminder: 7 days."
  (* 7 24 60 60 1000))

(defn- shown
  "How a `config/report` row's value reads in the email: a secret only as set or not set."
  [{:keys [secret? set? value]}]
  (cond secret?        (if set? "set" "not set")
        set?           (str value)
        (some? value)  (str value " (default)")
        :else          "not set"))

(defn- missing?
  "True when a required row has no value at all: unset and no default (a secret: unset)."
  [{:keys [secret? set? value]}]
  (if secret? (not set?) (and (not set?) (nil? value))))

(defn summary
  "Sorts `rows` (from `config/report`) for the email. Opts: `:sender-ok?`, false when the From
   address cannot deliver; `:deployment-problems`, [{:key :text}] found outside the settings.
   Returns {:problems [{:key :text}] :new [row] :worth [row] :tuning-defaults n}."
  [rows {:keys [sender-ok? deployment-problems]}]
  (let [class   #(get config/setting-classes (:var %) :tuning)
        new?    #(= config/current-release (:since %))
        missing (for [r rows :when (and (= :required (class r)) (missing? r))]
                  {:key (str "missing:" (:var r))
                   :text (str (:var r) " is not set" (when (:note r) (str ": " (:note r))) ".")})
        sender  (when-not sender-ok?
                  [{:key "missing:EMAIL_FROM_ADDRESS"
                    :text "EMAIL_FROM_ADDRESS is not an address mail can be sent from; set it."}])
        ignored (for [r rows :when (:ignored? r)]
                  {:key (str "ignored:" (:var r))
                   :text (str (:var r) "=" (:raw r) " was ignored: not " (or (:expects r) "a valid value")
                              ". The default is in use.")})]
    {:problems        (vec (concat missing sender ignored deployment-problems))
     :new             (filterv new? rows)
     :worth           (filterv #(and (= :recommended (class %)) (not (:set? %)) (not (new? %))) rows)
     :tuning-defaults (count (filter #(and (= :tuning (class %)) (not (:set? %)) (not (new? %))) rows))}))

(defn memory
  "What is remembered of `summary` sent for `release` at `now-ms`: problem keys and the names of
   settings left unset, never values. Returns the map written to startup-email.edn."
  [{:keys [problems worth] fresh :new} release now-ms]
  {:release   release
   :sent-at   now-ms
   :problems  (sort (map :key problems))
   :unset     (sort (map :var (remove :set? (concat fresh worth))))
   :reminded? false})

(defn decide
  "Whether to send for summary `s` given what was last sent (`remembered`, from `memory`, or nil),
   `release` and `now-ms`; `s` is a `summary`. Returns :first (a new release), :changed (problems or unset settings
   differ), :reminder (a problem a week old, reminded once), or nil (nothing to send)."
  [remembered s release now-ms]
  (let [current (memory s release now-ms)]
    (cond
      (every? empty? ((juxt :problems :new :worth) s)) nil
      (not= release (:release remembered))                  :first
      (not= ((juxt :problems :unset) current)
            ((juxt :problems :unset) remembered))           :changed
      (and (seq (:problems current))
           (not (:reminded? remembered))
           (>= (- now-ms (:sent-at remembered 0)) reminder-ms)) :reminder)))

(defn- table
  "`rows` as aligned lines: name, value as `shown`, then the note."
  [rows]
  (let [w (apply max 0 (map (comp count :var) rows))
        v (apply max 0 (map (comp count shown) rows))]
    (for [r rows]
      (str/trimr (format (str "  %-" w "s   %-" v "s   %s") (:var r) (shown r) (or (:note r) ""))))))

(defn email
  "The email for `summary`, as {:subject :body}. Opts: `:app-name`, `:release`, and `:admin`, the
   report's admin-address line (or nil). Sections appear only when they have something in them."
  [{:keys [problems worth tuning-defaults] fresh :new} {:keys [app-name release admin]}]
  (let [n      (fn [k word] (str k " " word (when (not= 1 k) "s")))
        parts  (remove nil? [(when (seq problems) (n (count problems) "problem"))
                             (when (seq fresh) (n (count fresh) "new setting"))])
        lines  (concat
                (when (seq problems)
                  (concat ["NEEDS FIXING"] (map #(str "  " (:text %)) problems) [""]))
                (when (seq fresh)
                  (concat [(str "NEW IN THE " (str/upper-case release))] (table fresh) [""]))
                (when (seq worth)
                  (concat ["WORTH SETTING"] (table worth) [""]))
                (when (pos? tuning-defaults)
                  [(str (n tuning-defaults "tuning setting") " at " (if (= 1 tuning-defaults) "its" "their")
                        " default" (when (not= 1 tuning-defaults) "s") "; the startup report lists them.") ""])
                (when admin ["DEPLOYMENT" (str "  " admin) ""])
                ["The full startup report is in the server log, and in startup-report.txt beside the crash files."])]
    {:subject (str app-name " started on the " release
                   (when (seq parts) (str ": " (str/join ", " parts))))
     :body    (str/join "\n" lines)}))

(defn deployment-problems
  "Problems outside the settings, as [{:key :text}]: crash files or alerts kept inside the
   container (`dirs` from `config/crash-and-alert-dirs`)."
  [{:keys [crash-dir alert-dir]}]
  (when (some #(and % (str/starts-with? % "/var/lib/orcpub/")) [crash-dir alert-dir])
    [{:key "deploy:state-inside"
      :text "Crash files and unsent alerts are kept inside the container and go when it is replaced. Mount - /var/log:/host-log on the orcpub service."}]))

(defn- read-memory
  "The memory map in `file`, or nil when it is missing or unreadable."
  [file]
  (try (edn/read-string (slurp file)) (catch Exception _ nil)))

(defn check!
  "Decides and, when due, raises the startup email as an admin alert, then records it in
   startup-email.edn in the startup report's folder. Reads everything from the running config.
   Returns the decision (`decide`), or nil. Never throws: a failure is logged."
  []
  (try
    (let [dirs    (config/crash-and-alert-dirs)
          dir     (config/report-dir dirs)
          file    (when dir (io/file dir "startup-email.edn"))
          release config/current-release
          now     (System/currentTimeMillis)
          admin   (config/alert-address)
          s       (summary (config/report)
                           {:sender-ok? (branding/sender-configured? branding/email-from-address)
                            :deployment-problems (deployment-problems dirs)})
          before  (some-> file read-memory)
          what    (decide before s release now)]
      (when what
        (let [{:keys [subject body]} (email s {:app-name branding/app-name :release release
                                               :admin (when admin (str "Admin email: " (:address admin)
                                                                       " (from " (:from admin) ")"))})]
          (alerts/raise-current! (str "startup-report:" release ":" (hash (memory s release 0)) ":" (name what))
                                 (if (= :reminder what) (str "Reminder: " subject) subject)
                                 body)
          (when file
            (spit file (pr-str (if (= :reminder what)
                                 (assoc before :reminded? true)
                                 (memory s release now)))))))
      what)
    (catch Exception e
      (println "startup-email: not sent:" (.getMessage e))
      nil)))
