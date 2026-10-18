(ns orcpub.dnd.e5.portrait-random
  "Seeded randomness the portraits need to come out the same in the browser and on the server:
   Randomize's seed hash and generator, and the grid hash behind hair streaks and freckles.
   All 32-bit integer maths, exact on both platforms.
   GOTCHA: plain `*` and `(int c)` differ in ClojureScript (doubles; a character is a string), so
   everything here goes through `mul32` and `char-code`.")

(defn u32
  "`x` as an unsigned 32-bit integer."
  ^long [^long x]
  #?(:clj (bit-and 0xffffffff x) :cljs (unsigned-bit-shift-right x 0)))

(defn mul32
  "a * b mod 2^32, unsigned."
  ^long [^long a ^long b]
  #?(:clj (bit-and 0xffffffff (unchecked-multiply a b))
     :cljs (unsigned-bit-shift-right (js/Math.imul a b) 0)))

(defn add32
  "a + b mod 2^32, unsigned."
  ^long [^long a ^long b]
  #?(:clj (bit-and 0xffffffff (unchecked-add a b))
     :cljs (unsigned-bit-shift-right (+ a b) 0)))

(defn ushr32
  "`h` read as 32 bits, shifted right by `n` with zeros."
  ^long [^long h ^long n]
  (unsigned-bit-shift-right (u32 h) n))

(defn char-code
  "A character's code: a Character on the JVM, a one-character string in ClojureScript."
  [c]
  #?(:clj (int c) :cljs (.charCodeAt c 0)))

(defn seed->int
  "32-bit FNV-1a of a seed's characters."
  [seed]
  (reduce (fn [h c] (mul32 (bit-xor h (char-code c)) 16777619))
          2166136261
          (str seed)))

(defn mulberry32
  "The mulberry32 generator: `(mulberry32 seed-int)` returns a fn yielding numbers in [0,1)."
  [seed-int]
  (let [a (atom (u32 seed-int))]
    (fn []
      (swap! a add32 0x6D2B79F5)
      (let [s @a
            t1 (mul32 (bit-xor s (ushr32 s 15)) (bit-or s 1))
            t2 (u32 (bit-xor t1 (add32 t1 (mul32 (bit-xor t1 (ushr32 t1 7)) (bit-or t1 61)))))]
        (/ (u32 (bit-xor t2 (ushr32 t2 14))) 4294967296.0)))))

(defn hash01
  "An integer hash of grid point `a`,`b` under `seed`, as 0..1."
  ^double [^long a ^long b ^long seed]
  (let [h (bit-xor (mul32 a 374761393) (mul32 b 668265263) (mul32 seed 1103515245))
        h (mul32 (bit-xor h (ushr32 h 13)) 1274126177)
        h (bit-xor h (ushr32 h 16))]
    (/ (double (bit-and h 0xffff)) 65535.0)))
