(ns orcpub.art-mark
  "The licensed-art mark: a PNG iTXt chunk, keyword \"orcpub-art\", that the Silhouette Loom writes
   into every exported piece of the real art. Anything drawn from marked pieces is marked too, and
   the repository's guard refuses any file carrying it. Silhouettes have none.
   GOTCHA: provenance, not protection: a screenshot or any outside tool drops it.")

(def key-word "orcpub-art")

(defn- u8
  "Byte `i` of `b`, 0..255."
  [b i] #?(:clj (bit-and (aget ^bytes b (int i)) 0xff) :cljs (aget b i)))

(defn- u32-at
  "The big-endian unsigned 32-bit number at `i` in `b`."
  [b i]
  (+ (* (u8 b i) 16777216) (* (u8 b (+ i 1)) 65536) (* (u8 b (+ i 2)) 256) (u8 b (+ i 3))))

(defn- ascii
  "Bytes `from`..`to` of `b` as Latin-1 text."
  [b from to] (apply str (map #(char (u8 b %)) (range from to))))

(defn- utf8
  "Bytes `from`..`to` of `b` as UTF-8 text."
  [b from to]
  #?(:clj (String. ^bytes b (int from) (int (- to from)) "UTF-8")
     :cljs (.decode (js/TextDecoder. "utf-8") (.subarray b from to))))

(defn- png?
  "Whether `b` starts with the PNG signature and has room for IHDR."
  [b]
  (and (>= (alength b) 33) (= [137 80 78 71 13 10 26 10] (mapv #(u8 b %) (range 8)))))

(defn mark-of
  "The mark's text in PNG bytes `b` (byte[] or Uint8Array), or nil. Reads only the chunks before
   the image data, where the mark is written."
  [b]
  (when (png? b)
    (loop [i 8]
      (when (<= (+ i 8) (alength b))
        (let [len (u32-at b i) type (ascii b (+ i 4) (+ i 8)) start (+ i 8) end (+ start len)]
          (cond
            (#{"IDAT" "IEND"} type) nil
            (and (= "iTXt" type) (<= end (alength b))
                 (= (str key-word "\u0000") (ascii b start (min end (+ start (count key-word) 1)))))
            ;; keyword NUL, compression flag, method, language NUL, translated keyword NUL, text
            (let [after-kw (+ start (count key-word) 1 2)
                  lang-end (loop [j after-kw] (if (and (< j end) (pos? (u8 b j))) (recur (inc j)) j))
                  trans-end (loop [j (inc lang-end)] (if (and (< j end) (pos? (u8 b j))) (recur (inc j)) j))]
              (utf8 b (min end (inc trans-end)) end))
            :else (recur (+ end 4))))))))

(defn marked?
  "Whether PNG bytes `b` carry the mark."
  [b]
  (some? (mark-of b)))

#?(:cljs
   (def ^:private crc-table
  (let [t (js/Uint32Array. 256)]
             (dotimes [n 256]
               (aset t n (loop [c n k 0] (if (= k 8) (unsigned-bit-shift-right c 0)
                                                     (recur (if (odd? c) (bit-xor 0xEDB88320 (unsigned-bit-shift-right c 1))
                                                                (unsigned-bit-shift-right c 1))
                                                            (inc k))))))
             t)))

(defn- crc32
  "The CRC-32 PNG chunks use, over bytes `from`..`to` of `b`."
  [b from to]
  #?(:clj (let [c (java.util.zip.CRC32.)] (.update c ^bytes b (int from) (int (- to from))) (.getValue c))
     :cljs (let [c (loop [i from c 0xFFFFFFFF]
                     (if (< i to)
                       (recur (inc i) (bit-xor (aget crc-table (bit-and (bit-xor c (aget b i)) 0xff))
                                               (unsigned-bit-shift-right c 8)))
                       c))]
             (unsigned-bit-shift-right (bit-xor c 0xFFFFFFFF) 0))))

(defn- text-bytes
  "`s` as UTF-8 bytes."
  [s]
  #?(:clj (.getBytes ^String s "UTF-8") :cljs (.encode (js/TextEncoder.) s)))

(defn- new-bytes
  "A zeroed byte array of length `n`."
  [n] #?(:clj (byte-array n) :cljs (js/Uint8Array. n)))

(defn- put!
  "Set byte `i` of `b` to `v` (0..255)."
  [b i v] #?(:clj (aset-byte b (int i) (unchecked-byte v)) :cljs (aset b i v)))

(defn- copy!
  "Copy `n` bytes from `src` at `src-from` to `dst` at `dst-from`."
  [src src-from dst dst-from n]
  #?(:clj (System/arraycopy src (int src-from) dst (int dst-from) (int n))
     :cljs (.set dst (.subarray src src-from (+ src-from n)) dst-from)))

(defn with-mark
  "PNG bytes `b` with the mark `text` added as an iTXt chunk straight after IHDR, so every byte of
   the image data, and every pixel, is unchanged. Bytes already marked come back as they are."
  [b text]
  (if (or (not (png? b)) (marked? b))
    b
    (let [kw (text-bytes key-word) tx (text-bytes text)
          data-len (+ (alength kw) 5 (alength tx))
          chunk-bytes (new-bytes (+ 12 data-len))
          ihdr-end 33 ; signature, then IHDR: always first, always 13 bytes of data
          out (new-bytes (+ (alength b) (alength chunk-bytes)))]
      (doseq [[i v] (map-indexed vector [(bit-and (bit-shift-right data-len 24) 0xff) (bit-and (bit-shift-right data-len 16) 0xff)
                                         (bit-and (bit-shift-right data-len 8) 0xff) (bit-and data-len 0xff)
                                         105 84 88 116])] ; iTXt
        (put! chunk-bytes i v))
      (copy! kw 0 chunk-bytes 8 (alength kw))
      (doseq [k (range 5)] (put! chunk-bytes (+ 8 (alength kw) k) 0))
      (copy! tx 0 chunk-bytes (+ 8 (alength kw) 5) (alength tx))
      (let [crc (crc32 chunk-bytes 4 (+ 8 data-len))]
        (doseq [[k sh] [[0 24] [1 16] [2 8] [3 0]]]
          (put! chunk-bytes (+ 8 data-len k) (bit-and (unsigned-bit-shift-right crc sh) 0xff))))
      (copy! b 0 out 0 ihdr-end)
      (copy! chunk-bytes 0 out ihdr-end (alength chunk-bytes))
      (copy! b ihdr-end out (+ ihdr-end (alength chunk-bytes)) (- (alength b) ihdr-end))
      out)))

(defn composed
  "The mark for a picture drawn from pieces whose marks are `sources` (nils ignored), or nil when
   none was marked."
  [sources]
  (when-let [s (seq (distinct (remove nil? sources)))]
    (str "licensed; composed from " (apply str (interpose " | " s)))))
