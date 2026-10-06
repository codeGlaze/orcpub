;; A share's homebrew, written out for the upload and read back from a link, in the browser.
(ns orcpub.dnd.e5.share-url-test
  (:require [cljs.test :refer-macros [deftest is async]]
            [orcpub.dnd.e5.share-url :as share-url]))

(def ^:private bundle
  {"My Source" {:orcpub.dnd.e5/spells {:my-spell {:name "My Spell" :option-pack "My Source"
                                                  :description "Every word of it travels."}}}})

(defn- fail [e] (is false (str "rejected: " e)))

(defn- as-vector [u8] (vec (js/Array.from u8)))

(deftest shared-homebrew-reads-back-as-it-went-in
  (async done
    (-> (share-url/encode-share bundle nil)
        (.then (fn [{:keys [bytes]}] (share-url/decode-share bytes nil)))
        (.then (fn [opened] (is (= bundle (:plugins opened)))))
        (.catch fail)
        (.finally done))))

(deftest the-same-homebrew-uploads-the-same-bytes
  (async done
    (let [;; The same fields, added in the opposite order.
          reordered {"My Source" {:orcpub.dnd.e5/spells {:my-spell (array-map :description "Every word of it travels."
                                                                              :option-pack "My Source"
                                                                              :name "My Spell")}}}]
      (-> (js/Promise.all #js [(share-url/encode-share bundle nil) (share-url/encode-share reordered nil)])
          (.then (fn [made]
                   (is (= (as-vector (:bytes (aget made 0))) (as-vector (:bytes (aget made 1))))
                       "field order does not change what is sent, so the server does nothing with it")))
          (.catch fail)
          (.finally done)))))

(deftest what-is-sent-is-already-what-the-server-requires
  (async done
    (-> (share-url/encode-share (assoc-in bundle ["My Source" :orcpub.dnd.e5/spells :my-spell :image]
                                          "data:image/png;base64,iVBORw0KGgo")
                                nil)
        (.then (fn [{:keys [bytes]}] (share-url/decode-share bytes nil)))
        (.then (fn [opened]
                 (is (= "" (get-in (:plugins opened) ["My Source" :orcpub.dnd.e5/spells :my-spell :image]))
                     "a pasted image is emptied before sending, so the server has nothing to refuse")))
        (.catch fail)
        (.finally done))))

(deftest the-server-sets-the-caps
  (let [resp (js/Response. "" #js {:headers #js {"X-Share-Max-Upload-Bytes" "1000"}})]
    (is (= {:upload 1000 :text (:text share-url/default-share-caps)} (share-url/share-caps-from resp)))
    (is (= share-url/default-share-caps (share-url/share-caps-from (js/Response. ""))) "or its defaults stand")))

(deftest what-a-link-loads-must-be-homebrew
  (async done
    (-> (share-url/decode-share (js/Uint8Array. #js [1 2 3 4]) nil)
        (.then (fn [opened] (is (= {:error :decode} opened))))
        (.catch fail)
        (.finally done))))

(deftest homebrew-over-a-cap-is-not-shared
  (let [alphabet "abcdefghijklmnopqrstuvwxyz0123456789"
        noise (fn [n] (apply str (repeatedly n #(nth alphabet (rand-int (count alphabet))))))]
    (async done
      (-> (js/Promise.all
           #js [(share-url/encode-share bundle {:text 50})
                ;; Barely compressible text: under the text cap, over the upload cap.
                (share-url/encode-share {"S" {:orcpub.dnd.e5/spells {:noise {:name "Noise" :option-pack "S"
                                                                            :description (noise 200000)}}}}
                                        nil)])
          (.then (fn [made]
                   (is (= {:error :too-large} (aget made 0)) "over the text cap")
                   (is (= {:error :too-large} (aget made 1)) "over the upload cap")))
          (.catch fail)
          (.finally done)))))
