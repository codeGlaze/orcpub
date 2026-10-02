(ns orcpub.portrait-pack-test
  "The per-piece calibration files and the Loom import."
  (:require [clojure.test :refer [deftest is testing]]
            [orcpub.dnd.e5.portrait-assets :as pa]
            [orcpub.dnd.e5.portrait-calibration :as cal]
            [orcpub.portrait-pack.import :as imp]))

(deftest every-calibrated-piece-exists
  (testing "a typo in loom.edn or pieces.edn must fail here, not quietly do nothing"
    (let [known (imp/registry-files)]
      (doseq [path cal/files
              [k fs] (cal/read-file path)
              [f fields] fs]
        (is (known [k f]) (str path ": no piece " (name k) "/" f " in the registry"))
        (doseq [field (keys fields)]
          (is (contains? pa/calibration-fields field)
              (str path ": " (name k) "/" f " has unknown field " field)))))))

(deftest the-registry-carries-the-calibration
  (let [by-id (fn [k id] (some #(when (= id (:asset/id %)) %) (pa/assets-for-layer k)))]
    (is (= 2 (count (:asset/iris (by-id :eyes :l6-eyes-01)))) "irises from loom.edn")
    (is (= 0.72166 (get-in (by-id :eyes :l6-eyes-01) [:asset/iris 1 :cx])) "the artist's own placement")
    (is (= :lips (:asset/slot (by-id :mouth :l8-mouth-02))) "lips from pieces.edn")
    (is (:asset/casts-shadow (by-id :bangs :l9-bangs-03)))
    (is (not (:asset/casts-shadow (by-id :bangs :l9-bangs-01))))))

(def eye {:cx 0.5 :cy 0.4 :rx 0.02 :ry 0.01 :rot 1.5
          :lid {:x0 0.4 :y0 0.4 :mx 0.5 :my 0.35 :x1 0.6 :y1 0.4}})

(defn- manifest [& assets]
  {:layers [{:key "eyes" :assets (vec assets)}
            {:key "nose" :assets [{:file "L7_nose_01.png"}]}]})

(def known #{[:eyes "l6_eyes_01.png"] [:eyes "l6_eyes_02.png"]})

(deftest import-takes-only-what-the-loom-places
  (let [{:keys [loom conflicts unknown]}
        (imp/manifest->loom (manifest {:file "L6_eyes_01.png" :iris [eye] :pupil 0.45 :bytes 999
                                       :tintVerdict "good"})
                            nil known)]
    (is (empty? conflicts))
    (is (empty? unknown))
    (is (= {:eyes {"l6_eyes_01.png" {:pupil 0.45 :iris [eye]}}} loom)
        "file names lower-cased; sizes and verdicts left out; pieces with nothing placed left out")))

(deftest import-stops-on-a-piece-listed-two-ways
  (let [a {:file "L6_eyes_01.png" :iris [eye] :pupil 0.45}
        b (assoc-in a [:iris 0 :cx] 0.52)
        m (manifest a b)]
    (testing "without a preference it reports and leaves the piece out"
      (let [{:keys [loom conflicts]} (imp/manifest->loom m nil known)]
        (is (= [[:eyes "l6_eyes_01.png"]] (map :piece conflicts)))
        (is (empty? loom))))
    (is (= 0.5 (get-in (:loom (imp/manifest->loom m :first known)) [:eyes "l6_eyes_01.png" :iris 0 :cx])))
    (is (= 0.52 (get-in (:loom (imp/manifest->loom m :last known)) [:eyes "l6_eyes_01.png" :iris 0 :cx])))
    (testing "a piece listed twice the SAME way is not a conflict"
      (is (empty? (:conflicts (imp/manifest->loom (manifest a a) nil known)))))))

(deftest import-names-pieces-the-registry-lacks
  (is (= #{[:eyes "l6_eyes_04.png"]}
         (:unknown (imp/manifest->loom (manifest {:file "L6_eyes_04.png" :iris [eye]}) nil known)))))

(deftest import-says-what-changed
  (let [old {:eyes {"a.png" {:pupil 0.4} "b.png" {:pupil 0.4}}}
        new {:eyes {"a.png" {:pupil 0.4} "b.png" {:pupil 0.5} "c.png" {:pupil 0.4}}}]
    (is (= {:added [[:eyes "c.png"]] :removed [] :changed [[:eyes "b.png"]]}
           (update-vals (imp/changes old new) vec)))))

(deftest the-server-says-which-hair-pieces-lack-strand-fields
  (require 'orcpub.portrait-pack.strands)
  (let [missing (resolve 'orcpub.portrait-pack.strands/missing)
        m (vec (missing))]
    ;; the repo ships silhouettes and no strand files, so every hair piece on
    ;; the classpath is listed -- and nothing that is not hair
    (is (seq m))
    (is (every? #(re-find #"/(hair-back|hair-bits|hair-front|bangs|scalp)/" %) m))))
