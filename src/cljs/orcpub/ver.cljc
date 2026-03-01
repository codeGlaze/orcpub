(ns orcpub.ver
  #?(:cljs (:require-macros [orcpub.ver :refer [build-date]])))

#?(:clj
   (defmacro build-date
     "Captures the current date at compile time (MM-dd-yyyy).
      In CLJS, the macro runs on the JVM during compilation.
      Uses TZ env var if set, falls back to JVM default timezone."
     []
     (let [tz (or (System/getenv "TZ") (str (java.time.ZoneId/systemDefault)))]
       (.format (java.time.LocalDate/now (java.time.ZoneId/of tz))
                (java.time.format.DateTimeFormatter/ofPattern "MM-dd-yyyy")))))

(defn version [] "2.4.0.28")
(defn date [] (build-date))
(defn description [] "Assault of the Last Stand")