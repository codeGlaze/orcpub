(ns measure-lookups
  "Measures what the site's lookups by value cost as the user count grows, and what an index on
   each looked-up field would change. Builds a throwaway in-memory database; never touches a real
   one. Run: JVM_OPTS=-Xmx2500m lein with-profile +tools run -m measure-lookups 10000"
  (:require [buddy.hashers :as hashers]
            [clojure.string :as s]
            [datomic.api :as d]
            [orcpub.db.schema :as schema]
            [orcpub.routes :as routes])
  (:import [java.lang.management ManagementFactory]
           [java.util UUID]))

(def ^:private characters-per-user
  "Characters each test user owns."
  3)

(defn- allocated-bytes
  "Bytes all live threads have allocated so far: Datomic does part of a query on its own threads."
  []
  (let [mx ^com.sun.management.ThreadMXBean (ManagementFactory/getThreadMXBean)]
    (reduce + (filter pos? (.getThreadAllocatedBytes mx (.getAllThreadIds mx))))))

(defn- user-tx
  "Transaction data for test user `i` with password hash `hash`: one in ten has a mixed-case email,
   as old accounts do; each owns `characters-per-user` characters, a party, a folder and an item."
  [i hash]
  (let [username (str "user" i)
        email (if (zero? (mod i 10)) (str "User" i "@Example.test") (str "user" i "@example.test"))]
    (concat
     [{:orcpub.user/username username :orcpub.user/email email :orcpub.user/password hash
       :orcpub.user/verified? true :orcpub.user/verification-key (str (UUID/randomUUID))}
      {:orcpub.dnd.e5.party/owner username :orcpub.dnd.e5.party/name (str "party" i)}
      {:orcpub.dnd.e5.folder/owner username :orcpub.dnd.e5.folder/name (str "folder" i)}
      {:orcpub.dnd.e5.magic-items/owner username :orcpub.dnd.e5.magic-items/name (str "item" i)}]
     (for [c (range characters-per-user)]
       {:orcpub.entity.strict/owner username :orcpub.entity.strict/game-version :e5
        :orcpub.entity.strict/type :character
        :db/doc (str "character " c " of " username)}))))

(defn- build!
  "A connection to a fresh in-memory database holding `n` test users and what they own."
  [n]
  (let [uri (str "datomic:mem://measure-" (UUID/randomUUID))
        hash (hashers/encrypt "a test passphrase")]
    (d/create-database uri)
    (let [conn (d/connect uri)]
      @(d/transact conn schema/all-schemas)
      (doseq [batch (partition-all 500 (range n))]
        @(d/transact conn (mapcat #(user-tx % hash) batch)))
      conn)))

(defn- measure
  "Runs `f` 5 times to warm up, then 15 times, and returns {:ms median-milliseconds :mb
   median-megabytes-allocated}."
  [f]
  (dotimes [_ 5] (f))
  (let [runs (for [_ (range 15)]
               (let [a (allocated-bytes) t (System/nanoTime)]
                 (f)
                 [(/ (- (System/nanoTime) t) 1e6) (/ (- (allocated-bytes) a) 1048576.0)]))
        median #(nth (sort %) (quot (count %) 2))]
    {:ms (median (map first runs)) :mb (median (map second runs))}))

(defn- lookups
  "The site's lookups, as {label function-of-db}, for test user `i`. Each calls the query the site
   runs (routes.clj), with the arguments a request would bring."
  [i]
  (let [username (str "user" i)
        email (str "user" i "@example.test")]
    [["page load: account lookup (get-user)" #(routes/find-user-by-username-or-email % username)]
     ["sign in by username (no password check)" #(routes/first-user-by % routes/username-query username)]
     ["sign in / reset / sign-up check by email" #(routes/first-user-by % routes/email-query email)]
     ["character list (by owner)"
      #(d/q '[:find ?e :in $ [?idents ...] :where [?e :orcpub.entity.strict/owner ?idents]]
            % [username email])]
     ["magic item list (by owner)"
      #(d/q '[:find ?e :in $ ?username :where [?e :orcpub.dnd.e5.magic-items/owner ?username]] % username)]
     ["email, exact match (what a fix would run)"
      #(d/q '[:find ?e . :in $ ?email :where [?e :orcpub.user/email ?email]] % email)]]))

(defn- report
  "Measures every lookup against `db` and prints one line each under `title`."
  [title db i]
  (println (str "\n  " title))
  (doseq [[label f] (lookups i)]
    (let [{:keys [ms mb]} (measure #(f db))]
      (println (format "    %-46s %9.2f ms  %9.2f MB allocated" label ms mb)))))

(defn -main
  "Builds a database of each user count in `args` and prints each lookup's cost before and after
   indexing the fields looked up by value."
  [& args]
  (doseq [n (map #(Long/parseLong %) (or (seq args) ["10000"]))]
    (let [t (System/nanoTime)
          conn (build! n)
          i (quot n 2)]
      (println (format "\n== %,d users, %,d characters (built in %.0f s)"
                       n (* n characters-per-user) (/ (- (System/nanoTime) t) 1e9)))
      (report "as the site runs today (no indexes)" (d/db conn) i)
      @(d/transact conn (for [a [:orcpub.user/username :orcpub.user/email :orcpub.entity.strict/owner
                                 :orcpub.dnd.e5.party/owner :orcpub.dnd.e5.folder/owner
                                 :orcpub.dnd.e5.magic-items/owner]]
                          {:db/id a :db/index true}))
      (d/request-index conn)
      @(d/sync-index conn (d/basis-t (d/db conn)))
      (report "with those fields indexed (same queries)" (d/db conn) i)
      (d/release conn)))
  (shutdown-agents)
  (System/exit 0))
