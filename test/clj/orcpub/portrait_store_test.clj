(ns orcpub.portrait-store-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [datomic.api :as d]
            [datomock.core :as dm]
            [orcpub.config :as config]
            [orcpub.routes :as routes]
            [orcpub.db.schema :as schema]
            [orcpub.entity.strict :as se]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.portrait-assets :as pa]
            [orcpub.portrait-render :as portrait-render]
            [orcpub.portrait-store :as store])
  (:import [java.util UUID]
           [java.nio.file Files]))

(defmacro with-conn
  "Run `body` with `conn-binding` bound to a fresh in-memory database."
  [conn-binding & body]
  `(let [uri# (str "datomic:mem:portrait-store-test-" (UUID/randomUUID))
         ~conn-binding (do (d/create-database uri#) (d/connect uri#))]
     (try ~@body (finally (d/delete-database uri#)))))

(def ^:dynamic *dir* nil)

(use-fixtures :each
  (fn [t]
    (let [dir (.toFile (Files/createTempDirectory "portrait-store" (make-array java.nio.file.attribute.FileAttribute 0)))]
      (binding [*dir* dir]
        (with-redefs [config/get-portrait-store-dir (constantly (.getPath dir))
                      store/coalesce-ms 150]
          (store/await-idle!)
          (store/clear-memory!)
          (try (t) (finally (store/await-idle!)
                            (doseq [f (reverse (file-seq dir))] (.delete ^java.io.File f)))))))))

(defn- portrait
  "A portrait choosing each layer's first piece, with hair colour `hair`."
  [hair]
  {:layers (into {} (keep (fn [k] (when-let [a (first (pa/assets-for-layer k))]
                                    [k {:artist/id (pa/artist-for-asset k (:asset/id a)) :asset/id (:asset/id a)}])))
                 [:head :shirt :eyes])
   :colors {:hair hair :skin "#e8c69c"}})

(defn- setup
  "A fork of `conn` with the schema and a user, testy."
  [conn]
  (let [c (dm/fork-conn conn)]
    @(d/transact c schema/all-schemas)
    @(d/transact c [{:orcpub.user/username "testy" :orcpub.user/email "t@example.test"}])
    c))

(defn- save!
  "Save a character with `values` (and `id` when updating); returns its id."
  [conn values & [id]]
  (:db/id (:body (routes/do-save-character (d/db conn) conn
                                           (cond-> {::se/selections []
                                                    ::se/summary {::char5e/character-name "Storey"}
                                                    ::se/values values}
                                             id (assoc :db/id id))
                                           {:user "testy"}))))

(defn- files
  "The store's file names, all of them or character `id`'s."
  ([] (sort (map #(.getName ^java.io.File %) (filter #(.isFile ^java.io.File %) (file-seq *dir*)))))
  ([id] (filter #(str/starts-with? % (str id "-")) (files))))

(defn- settle
  "Wait for scheduled draws to finish: until `done?` holds, or 15 s; then a moment more, so a draw
   that should not happen would have shown up."
  ([] (settle (constantly true)))
  ([done?] (loop [n 0] (when (and (< n 150) (not (done?))) (Thread/sleep 100) (recur (inc n))))
   (Thread/sleep (+ store/coalesce-ms 200))
   (store/await-idle!)))

(defn- counting
  "A render-png that counts top-level draws in `draws` and draws for real."
  [draws real]
  (fn ([p] (swap! draws inc) (real p))
      ([p w h] (real p w h))))

(defn- served
  "The image route's response for character `id`."
  [conn id] (routes/character-portrait-png {:db (d/db conn) :path-params {:id id}}))

(deftest a-save-draws-only-when-the-portrait-changed
  (with-conn conn
    (let [c (setup conn) draws (atom 0)
          real portrait-render/render-png]
      (with-redefs [portrait-render/render-png (counting draws real)]
        (let [id (save! c {::char5e/portrait (pr-str (portrait "#5c3a1e"))})]
          (settle #(seq (files)))
          (is (= 1 @draws) "a new portrait is drawn after the save")
          (is (= 1 (count (files id))) (pr-str (files)))
          (testing "an autosave of an unrelated edit draws nothing"
            (is (= :kept (store/saved! id (pr-str (portrait "#5c3a1e"))))
                "nothing is even scheduled: the kept file already matches")
            (save! c {::char5e/portrait (pr-str (portrait "#5c3a1e")) ::char5e/notes "hp 12"} id)
            (save! c {::char5e/portrait (pr-str (portrait "#5c3a1e")) ::char5e/notes "hp 9"} id)
            (settle)
            (is (= 1 @draws)))
          (testing "a changed portrait is drawn again, and replaces the old file"
            (save! c {::char5e/portrait (pr-str (portrait "#123456"))} id)
            (settle #(= 2 @draws))
            (is (= 2 @draws))
            (is (= 1 (count (files id))) "and replaces the old file")))))))

(deftest a-burst-of-changes-is-drawn-once
  (with-conn conn
    (let [c (setup conn) draws (atom [])]
      (with-redefs [portrait-render/render-png (fn [p] (swap! draws conj (get-in p [:colors :hair])) (byte-array [1]))]
        (let [id (save! c {::char5e/portrait (pr-str (portrait "#000001"))})]
          (doseq [h ["#000002" "#000003" "#000004" "#000005"]]
            (save! c {::char5e/portrait (pr-str (portrait h))} id))
          (settle #(seq @draws))
          (is (= ["#000005"] @draws) "one draw, of what was saved last"))))))

(deftest a-kept-drawing-survives-a-restart
  (with-conn conn
    (let [c (setup conn) draws (atom 0) real portrait-render/render-png]
      (with-redefs [portrait-render/render-png (counting draws real)
                    store/saved! (fn [& _] :off)] ; a character saved before the store existed
        (let [id (save! c {::char5e/portrait (pr-str (portrait "#5c3a1e"))})
              first-bytes (vec (.readAllBytes ^java.io.InputStream (:body (served c id))))]
          (is (= 1 @draws) "drawn on first request")
          (is (= 1 (count (files))) "and kept")
          (store/clear-memory!)
          (is (= (#'store/art-digest*) @@#'store/art-digest) "the fingerprint does not depend on the process")
          (is (= first-bytes (vec (.readAllBytes ^java.io.InputStream (:body (served c id))))))
          (is (= 1 @draws) "after a restart the kept file is served, not drawn again"))))))

(deftest a-served-portrait-is-the-servers-own-drawing
  (with-conn conn
    (let [c (setup conn)
          stored (pr-str (portrait "#5c3a1e"))
          id (save! c {::char5e/portrait stored})]
      (settle #(seq (files)))
      (let [body (vec (.readAllBytes ^java.io.InputStream (:body (served c id))))
            kept (vec (Files/readAllBytes (.toPath (store/file-for id (store/tag stored)))))]
        (is (= kept body) "what is served is the kept file")
        (is (= (vec (portrait-render/render-png (char5e/parse-portrait stored))) body)
            "and that is the server's drawing of the saved selection")))))

(deftest a-crafted-portrait-cannot-break-the-character
  (let [nested (apply str (repeat 20000 "["))]
    (testing "deeply nested data overflowed the reader's stack with an Error, not an Exception"
      (is (nil? (char5e/parse-portrait nested)))
      (is (nil? (char5e/parse-portrait (apply str (repeat 5000 "[")))) "nested but short enough to read"))
    (with-conn conn
      (let [c (setup conn)
            id (save! c {::char5e/portrait nested ::char5e/notes "still saved"})]
        (is (some? id) "the save goes through")
        (is (= "still saved" (get-in (d/pull (d/db c) '[{::se/values [::char5e/notes]}] id) [::se/values ::char5e/notes])))
        (is (nil? (get-in (d/pull (d/db c) '[{::se/values [::char5e/portrait]}] id) [::se/values ::char5e/portrait]))
            "and the junk is not stored")
        (is (= 404 (:status (served c id))) "the image route answers, with no picture")))))
