(ns orcpub.image-capture
  "Reads a character's picture in the browser, so an export can carry the bytes
   instead of an address for the server to fetch.

   The route is a CORS-attributed <img> drawn to a canvas, and only that: the app's
   CSP is `connect-src 'self'` and `img-src 'self' data: https:`, so an image host
   is reachable by the image loader and not by the fetch stack, and a fetch would
   log a violation on every export. Reading the canvas is therefore always a
   re-encode, bounded by scaling to the printed size first.

   A host that sends no Access-Control-Allow-Origin refuses every read, and the
   browser logs a CORS error saying so -- that is the host's rule being reported
   and cannot be suppressed from here. The caller then falls back to the server,
   and past that to a copy or a file.

   There is no way round a refusal from here: a tab or iframe showing the picture
   is a different origin the opener cannot read, and a service worker fetching it
   no-cors gets an opaque response whose bytes it cannot read either."
  (:require [clojure.string :as s]))

(def ^:private max-bytes
  "Byte ceiling, matching the server's. Bytes past it would be refused on arrival,
   so the encode below shrinks the picture until it fits rather than sending them."
  (* 128 1024))

(def ^:private max-pixels
  "Pixel ceiling, matching the server's. Byte size does not bound dimensions --
   a small file can declare an enormous canvas -- so this is checked separately."
  (* 2000 2000))

(def ^:private print-edge
  "Longest edge the sheet can actually show, in pixels.

   The portrait box is 2.35 x 3.15 inches and 300dpi is the print target, so 945px
   on the long side. The in-app thumbnail is 200x100, far smaller, so the printed
   size is the one that decides. Pixels past this are thrown away by the
   rasteriser, which is why size is given up before quality: it costs nothing
   visible until this point, and quality costs something immediately."
  945)

(def ^:private quality-steps
  "Tried at the printed size, once size has been given up as far as it can be."
  [0.92 0.8 0.68 0.55 0.42])

(def ^:private fallback-edges
  "Below the printed size, and only once quality is spent. A picture that still
   will not fit at the lowest quality is mostly noise, and shrinking it further
   loses less than dropping it and sending the address instead."
  [700 500 350])

(def ^:private capture-deadline-ms
  "Wall clock allowed for one read before it is called unavailable.

   Every route out of here ends in an event handler -- onload, onerror, toBlob,
   FileReader -- and a browser that fires none of them would leave the read
   pending for good. The export button waits on :pending, so this is what
   guarantees it is never waiting on nothing."
  10000)

(defn- once
  "Wraps `k` so it runs at most once, and runs with nil if nothing has called it
   by the deadline."
  [k]
  (let [done? (volatile! false)
        fire (fn [v] (when-not @done? (vreset! done? true) (k v)))]
    (js/setTimeout #(fire nil) capture-deadline-ms)
    fire))

(defn- dimensions
  "Width and height of an ImageBitmap or a loaded <img>. Only the latter has
   naturalWidth, and only it distinguishes the pixels from the layout box."
  [source]
  [(or (.-naturalWidth source) (.-width source))
   (or (.-naturalHeight source) (.-height source))])

(defn- long-edge
  [source]
  (apply max (dimensions source)))

(defn- encode-attempts
  "Longest edge and JPEG quality to try, in order, for a picture whose natural
   long edge is `natural`.

   Size is spent first, but only down to what the sheet can show: a picture
   already smaller than the printed size is never scaled at all, and just gets the
   quality ladder. Going below the printed size comes last, when quality alone
   cannot reach the ceiling."
  [natural]
  (let [edge (min natural print-edge)]
    (concat (for [q quality-steps] [edge q])
            (for [e fallback-edges :when (< e edge)] [e (last quality-steps)]))))

(defn- draw-scaled
  "Draws `source` onto a fresh canvas with its longest edge at most `edge`,
   preserving aspect, and returns the canvas."
  [source edge]
  (let [[w h] (dimensions source)
        f (min 1 (/ edge (max w h)))
        canvas (js/document.createElement "canvas")]
    (set! (.-width canvas) (max 1 (js/Math.round (* w f))))
    (set! (.-height canvas) (max 1 (js/Math.round (* h f))))
    (.drawImage (.getContext canvas "2d") source 0 0 (.-width canvas) (.-height canvas))
    canvas))

(defn- encode-under-cap
  "JPEG-encodes `source` at each of `attempts` in turn and hands `k` the first blob
   that fits max-bytes, or nil once the list is spent. The canvas is redrawn per
   attempt so an attempt may shrink the picture as well as its quality."
  [source attempts k]
  (if-let [[edge q] (first attempts)]
    (.toBlob (draw-scaled source edge)
             (fn [blob]
               (cond
                 (nil? blob) (k nil)
                 (<= (.-size blob) max-bytes) (k blob)
                 :else (encode-under-cap source (rest attempts) k)))
             "image/jpeg" q)
    (k nil)))

(defn- normalize
  "Passes `blob` through untouched when it already fits both ceilings; otherwise
   scales and re-encodes it. createImageBitmap is what reads the dimensions, and
   it is also the decode: a blob that is not an image rejects here."
  [blob k]
  (try
    (-> (js/createImageBitmap blob)
        (.then (fn [bitmap]
                 (let [[w h] (dimensions bitmap)]
                   ;; Carried untouched only when it is inside every limit AND no
                   ;; bigger than the sheet can show. Anything larger in either
                   ;; sense goes through the ladder, so size is given up before
                   ;; quality here too.
                   (if (and (<= (.-size blob) max-bytes)
                            (<= (* w h) max-pixels)
                            (<= (max w h) print-edge))
                     (k blob)
                     (encode-under-cap bitmap (encode-attempts (long-edge bitmap)) k)))))
        (.catch (fn [_] (k nil))))
    (catch :default _ (k nil))))

(defn- read-drawn
  "Loads `url` into an <img> and hands `k` a JPEG blob drawn from it, or nil.

   `prepare!` is given the element before src is set, which is the only moment
   crossOrigin has any effect -- set after src, it is ignored and the canvas is
   tainted."
  [url prepare! k]
  (let [img (js/Image.)]
    (set! (.-onload img)
          (fn [_]
            (try
              (encode-under-cap img (encode-attempts (long-edge img)) k)
              (catch :default _ (k nil)))))
    (set! (.-onerror img) (fn [_] (k nil)))
    (prepare! img)
    (set! (.-src img) url)))

(defn- via-canvas
  "Reads a remote picture back off a canvas. Without crossOrigin the draw succeeds
   and the read does not: toBlob throws a SecurityError on a tainted canvas."
  [url k]
  (read-drawn url #(set! (.-crossOrigin %) "anonymous") k))

(defn- via-object-url
  "Reads a local file through the image loader rather than createImageBitmap,
   which is the stricter decoder of the two and refuses files the loader renders.
   A blob: URL is same-origin, so nothing taints and no header is needed."
  [file k]
  (let [url (js/URL.createObjectURL file)]
    (read-drawn url identity (fn [blob] (js/URL.revokeObjectURL url) (k blob)))))

(defn- blob->payload
  "Reads `blob` as a data: URL and splits it into the mime type and the base64
   payload the export sends, or nil if the read fails."
  [blob k]
  (let [reader (js/FileReader.)
        finish (fn [result]
                 (k (when-let [[_ mime data] (and (string? result)
                                                  (re-matches #"data:([^;,]+);base64,(.*)"
                                                              result))]
                      (when (seq data) {:mime mime :data data}))))]
    (set! (.-onload reader) (fn [_] (finish (.-result reader))))
    (set! (.-onerror reader) (fn [_] (finish nil)))
    (.readAsDataURL reader blob)))

(defn capture
  "Reads the image at `url` and calls `k` with {:mime string :data base64}, or
   with nil when no route to the bytes is allowed.

   Deliberately not on the export click path: the export is a synchronous form
   submit into a new tab, and any await between the click and .submit() spends the
   transient user activation that keeps that tab from being blocked."
  [url k]
  (let [k (once k)]
    (if (s/blank? url)
      (k nil)
      (via-canvas url (fn [blob] (if blob (blob->payload blob k) (k nil)))))))

(defn capture-file
  "The same ceilings for a File the user picked, which is already local and needs
   no network route.

   Falls back to the image loader when createImageBitmap will not decode the file:
   it refuses some files the loader renders happily, and a picture the user can
   see in the page must not be one the export cannot carry."
  [file k]
  (let [k (once k)
        payload (fn [blob] (if blob (blob->payload blob k) (k nil)))]
    (normalize file (fn [blob]
                      (if blob
                        (payload blob)
                        (via-object-url file payload))))))

(defn- image-item
  "The first clipboard item carrying a picture, with the type it carries, or nil."
  [items]
  (some (fn [item]
          (when-let [t (first (filter #(s/starts-with? % "image/")
                                      (array-seq (.-types item))))]
            [item t]))
        (array-seq items)))

(defn capture-clipboard
  "Reads a picture the viewer has already copied and hands it to `k` like any
   other local file, or nil when the clipboard holds no picture.

   The copy has to be the VIEWER's -- \"Copy image\" in the browser's own menu.
   A page-initiated copy of a cross-origin image puts its markup on the clipboard
   and not its pixels, by the same rule that taints the canvas: if a page could
   copy pixels it could read any image anywhere, and no host's rules would mean
   anything. Reading the clipboard needs a user gesture and, the first time, the
   viewer's permission."
  [k]
  (let [k (once k)]
    (if-not (and js/navigator.clipboard (.-read js/navigator.clipboard))
      (k nil)
      (-> (.read js/navigator.clipboard)
          (.then (fn [items]
                   (if-let [[item t] (image-item items)]
                     (-> (.getType item t)
                         (.then (fn [blob] (capture-file blob k)))
                         (.catch (fn [_] (k nil))))
                     (k nil))))
          (.catch (fn [_] (k nil)))))))

(defn displays?
  "Whether `url` loads as a picture in this page, answered by loading it.

   No crossOrigin and no canvas: the question is only whether the browser will
   show it, which is also the question the Content-Security-Policy answers. Costs
   nothing anyone was not already going to spend -- the thumbnail loads the same
   address a moment later, and the browser serves the second one from cache."
  [url k]
  (let [k (once k)]
    (if (s/blank? url)
      (k false)
      (let [img (js/Image.)]
        (set! (.-onload img) (fn [_] (k true)))
        (set! (.-onerror img) (fn [_] (k false)))
        (set! (.-src img) url)))))
