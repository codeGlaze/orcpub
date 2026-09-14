;; Encrypted share snapshots, in the browser that makes and opens them.
(ns orcpub.dnd.e5.share-url-test
  (:require [cljs.test :refer-macros [deftest is async]]
            [orcpub.dnd.e5.share-url :as share-url]))

(def ^:private bundle
  {"My Source" {:orcpub.dnd.e5/spells {:my-spell {:name "My Spell" :option-pack "My Source"
                                                  :description "Every word of it travels."}}}})

(def ^:private salt "a-characters-share-salt")

(defn- fail [e] (is false (str "rejected: " e)))

(defn- link-of [made] (select-keys made [:share :key]))

(deftest a-snapshot-opens-with-its-key-and-only-its-key
  (async done
    (-> (share-url/build-snapshot bundle 42 salt)
        (.then (fn [{:keys [share key blob]}]
                 (is (re-matches #"[A-Za-z0-9_-]{22}" share))
                 (is (re-matches #"[A-Za-z0-9_-]{43}" key))
                 (is (not (re-find #"My Spell" (.decode (js/TextDecoder.) blob))) "the stored bytes are not readable")
                 (-> (share-url/decode-snapshot blob key)
                     (.then (fn [opened]
                              (is (= bundle (:plugins opened)))
                              (share-url/decode-snapshot blob (apply str (repeat 43 "A")))))
                     (.then (fn [wrong] (is (= {:error :decode} wrong) "a wrong key opens nothing"))))))
        (.catch fail)
        (.finally done))))

(deftest the-same-homebrew-gives-the-same-link
  (async done
    (let [;; The same fields, added in the opposite order.
          reordered {"My Source" {:orcpub.dnd.e5/spells {:my-spell (array-map :description "Every word of it travels."
                                                                              :option-pack "My Source"
                                                                              :name "My Spell")}}}]
      (-> (js/Promise.all #js [(share-url/build-snapshot bundle 42 salt)
                               (share-url/build-snapshot bundle 42 salt)
                               (share-url/build-snapshot reordered 42 salt)
                               (share-url/build-snapshot bundle 43 salt)
                               (share-url/build-snapshot bundle 42 "a-new-salt")])
          (.then (fn [made]
                   (is (= (link-of (aget made 0)) (link-of (aget made 1))) "built again, the same link")
                   (is (= (link-of (aget made 0)) (link-of (aget made 2))) "field order does not matter")
                   (is (not= (:share (aget made 0)) (:share (aget made 3))) "another character has its own link")
                   (is (not= (link-of (aget made 0)) (link-of (aget made 4))) "a new salt, a new link")))
          (.catch fail)
          (.finally done)))))

(deftest a-bundle-over-the-cap-makes-no-snapshot
  (async done
    (-> (share-url/build-snapshot
         {"S" {:orcpub.dnd.e5/spells {:big {:name "Big" :option-pack "S"
                                            :description (apply str (repeat (inc share-url/max-snapshot-edn-bytes) "x"))}}}}
         42 salt)
        (.then (fn [made] (is (= {:error :too-large} made))))
        (.catch fail)
        (.finally done))))

(deftest content-that-stores-over-the-cap-makes-no-snapshot
  ;; Text that barely compresses: small enough to build, too big to store.
  (let [alphabet "abcdefghijklmnopqrstuvwxyz0123456789"
        noise (apply str (repeatedly 500000 #(nth alphabet (rand-int (count alphabet)))))]
    (async done
      (-> (share-url/build-snapshot {"S" {:orcpub.dnd.e5/spells {:noise {:name "Noise" :option-pack "S"
                                                                        :description noise}}}}
                                    42 salt)
          (.then (fn [made] (is (= {:error :too-large} made))))
          (.catch fail)
          (.finally done)))))
