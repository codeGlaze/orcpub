(ns orcpub.portrait-png-test
  "decode-portrait-png is the composed-portrait counterpart to pdf/fetch-image:
   the client bakes its CSS-mask layers to a PNG and posts the bytes, so there
   is no URL to fetch. Like fetch-image it must return nil rather than throw --
   a picture that will not decode must not cost the character their sheet."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.routes :as routes]))

(def ^:private tiny-png-b64
  ;; 1x1 transparent PNG
  (str "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk"
       "YPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=="))

(deftest decodes-valid-base64-png
  (let [{:keys [data jpg?]} (routes/decode-portrait-png tiny-png-b64)]
    (is (bytes? data))
    (is (pos? (alength data)))
    (is (false? jpg?) "PNG bytes are re-encoded by PDFBox, never passed as JPEG")
    (testing "decoded bytes carry the PNG magic number"
      (is (= [-119 80 78 71] (take 4 (vec data)))))))

(deftest returns-nil-for-unusable-input
  (is (nil? (routes/decode-portrait-png nil)) "absent")
  (is (nil? (routes/decode-portrait-png "")) "blank")
  (is (nil? (routes/decode-portrait-png "   ")) "whitespace")
  (is (nil? (routes/decode-portrait-png "!!!not base64!!!")) "malformed")
  (is (nil? (routes/decode-portrait-png 42)) "non-string")
  (is (nil? (routes/decode-portrait-png (apply str (repeat 8 "="))))
      "decodes to zero bytes"))

(deftest refuses_oversize_payload
  (testing "a body pretending to be a picture is rejected before allocating it"
    (let [huge (apply str (repeat (* 4 1024 1024) "A"))]  ; ~3 MB decoded
      (is (nil? (routes/decode-portrait-png huge))))))
