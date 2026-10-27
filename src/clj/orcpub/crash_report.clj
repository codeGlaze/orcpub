(ns orcpub.crash-report
  "At start, tells the admin when the previous run crashed. The JVM is started with flags that
   write a heap dump when it runs out of memory and a crash log when it dies (docker-compose.yaml,
   JDK_JAVA_OPTIONS); this finds those files, raises one admin alert per new file, and remembers
   which it reported. plan-email-and-alerts.md on agents/develop."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [com.stuartsierra.component :as component]
            [orcpub.alerts :as alerts])
  (:import [java.lang.management ManagementFactory]
           [java.util Date]))

(defn crash-paths
  "The heap dump file and crash-log pattern the JVM was started with, from `args` (its input
   arguments): {:heap-dump path-or-nil :error-file pattern-or-nil}."
  [args]
  (let [flag (fn [prefix] (some #(when (s/starts-with? % prefix) (subs % (count prefix))) args))]
    {:heap-dump (flag "-XX:HeapDumpPath=")
     :error-file (flag "-XX:ErrorFile=")}))

(defn- crash-dirs
  "The directories `crash-paths` point into, without duplicates."
  [{:keys [heap-dump error-file]}]
  (distinct (keep #(some-> % io/file .getParentFile) [heap-dump error-file])))

(defn crash-files
  "Heap dumps (*.hprof) and JVM crash logs (*.log naming a crash) in `dirs`, oldest first."
  [dirs]
  (->> dirs
       (mapcat #(seq (.listFiles (io/file %))))
       (filter #(and (.isFile %) (re-matches #".*\.hprof|.*crash.*\.log|hs_err.*\.log" (.getName %))))
       (sort-by #(.lastModified %))))

(defn- reported-file
  "The file in `dir` that records which crash files were already reported."
  [dir]
  (io/file dir "reported.edn"))

(defn- read-reported
  "Names of crash files already reported in `dir`, as a set."
  [dir]
  (let [f (reported-file dir)]
    (try (if (.exists f) (set (edn/read-string (slurp f))) #{})
         (catch Exception _ #{}))))

(defn summary-lines
  "The first lines of crash log `f` that say what happened (the JVM's '#' header), at most 12."
  [f]
  (try
    (with-open [r (io/reader f)]
      (->> (line-seq r) (take 40) (filter #(s/starts-with? % "#")) (remove #(re-matches #"#\s*" %))
           (take 12) doall))
    (catch Exception _ [])))

(defn alert-for
  "The admin alert for crash file `f`, as {:kind :subject :body}."
  [f]
  (let [heap? (s/ends-with? (.getName f) ".hprof")
        at (Date. (.lastModified f))
        mb (quot (.length f) 1048576)]
    {:kind (str "jvm-crash:" (.getName f))
     :subject (if heap? "The server ran out of memory and restarted" "The server's Java process crashed")
     :body (s/join "\n"
                   (concat
                    [(if heap?
                       (str "On " at " the server ran out of memory. It exited and was restarted.")
                       (str "On " at " the server's Java process died. It was restarted."))
                     ""
                     (str "Evidence kept on the server: " (.getPath f) " (" mb " MB).")]
                    (when heap?
                      ["It shows what filled the memory. It holds everything that was in memory,"
                       "player data included: keep it private, and delete it once read so the"
                       "next crash can write a new one."])
                    (when-not heap? (cons "" (summary-lines f)))))}))

(defn report!
  "Raises an admin alert for each crash file in `dirs` not reported before, and records them.
   Returns the alerts raised."
  [dirs]
  (vec
   (for [dir dirs
         :let [seen (read-reported dir)
               unreported (remove #(contains? seen (.getName %)) (crash-files [dir]))]
         :when (seq unreported)
         :let [alerts (mapv alert-for unreported)]]
     (do
       (doseq [{:keys [kind subject body]} alerts]
         (alerts/raise-current! kind subject body))
       (try (spit (reported-file dir) (pr-str (into seen (map #(.getName %) unreported))))
            (catch Exception e (println "crash-report: could not record what was reported:" (.getMessage e))))
       alerts))))

(defrecord CrashReport [alerts]
  component/Lifecycle
  (start [this]
    (try
      (let [dirs (crash-dirs (crash-paths (.getInputArguments (ManagementFactory/getRuntimeMXBean))))]
        (if (seq dirs)
          (let [raised (apply concat (report! dirs))]
            (println "crash-report:" (if (seq raised)
                                       (str (count raised) " crash file(s) from an earlier run reported to the admin")
                                       "no new crash files")
                     "in" (s/join ", " (map str dirs))))
          (println "crash-report: the JVM was not started with crash-file flags; crashes are not reported")))
      (catch Exception e (println "crash-report: could not check for earlier crashes:" (.getMessage e))))
    this)
  (stop [this] this))

(defn new-crash-report
  "The CrashReport component; needs :alerts so its alerts can be sent."
  []
  (map->CrashReport {}))
