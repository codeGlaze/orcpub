(ns orcpub.image-capture
  "Reads a character's picture in the browser, so an export can carry the bytes
   instead of an address for the server to fetch.

   The server's fetch is refused by hosts that block hotlinking, which judge the
   Referer and the datacenter IP -- neither of which describes the browser's own
   request. What the browser may read is bounded by CORS instead: a cross-origin
   image whose host sends no Access-Control-Allow-Origin cannot be read by script.
   The two rules catch different hosts, so a picture the server cannot have is
   often one the browser can.

   The route to the bytes is a CORS-attributed <img> drawn to a canvas, and only
   that. fetch would return the file as served and save a re-encode, but the app's
   Content-Security-Policy is `connect-src 'self'` and `img-src 'self' data:
   https:` -- an image host is reachable by the image loader and not by the fetch
   stack, and an attempt anyway would log a CSP violation on every export. Widening
   connect-src to reach arbitrary hosts is the larger cost.

   Reading the canvas is therefore always a re-encode. That is bounded work: the
   picture is scaled to what the sheet prints before it is encoded at all.

   When the host allows no read, capture reports nil and the caller offers an
   upload, which needs no permission from anyone."
  (:require [clojure.string :as s]))

(def ^:private max-bytes
  "Byte ceiling, matching the server's. Bytes past it would be refused on arrival,
   so the encode below shrinks the picture until it fits rather than sending them."
  (* 128 1024))

(def ^:private max-pixels
  "Pixel ceiling, matching the server's. Byte size does not bound dimensions --
   a small file can declare an enormous canvas -- so this is checked separately."
  (* 2000 2000))

(def ^:private max-edge
  "Longest edge kept when an image has to be re-encoded. The portrait prints at
   2.35 x 3.15 inches, so this is past 300dpi on the long side and anything more
   is detail the rasteriser discards."
  1000)

(def ^:private jpeg-qualities
  "Tried in order until an encode fits max-bytes."
  [0.85 0.7 0.55 0.4])

(defn- dimensions
  "Width and height of an ImageBitmap or a loaded <img>. Only the latter has
   naturalWidth, and only it distinguishes the pixels from the layout box."
  [source]
  [(or (.-naturalWidth source) (.-width source))
   (or (.-naturalHeight source) (.-height source))])

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
  "JPEG-encodes `canvas`, dropping quality until the result fits max-bytes, and
   hands the blob to `k`. Calls `k` with nil once the list is spent, which takes a
   picture that is still mostly noise at max-edge."
  [canvas qualities k]
  (if-let [q (first qualities)]
    (.toBlob canvas
             (fn [blob]
               (cond
                 (nil? blob) (k nil)
                 (<= (.-size blob) max-bytes) (k blob)
                 :else (encode-under-cap canvas (rest qualities) k)))
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
                   (if (and (<= (.-size blob) max-bytes)
                            (<= (* w h) max-pixels))
                     (k blob)
                     (encode-under-cap (draw-scaled bitmap max-edge) jpeg-qualities k)))))
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
              (encode-under-cap (draw-scaled img max-edge) jpeg-qualities k)
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
  (if (s/blank? url)
    (k nil)
    (via-canvas url (fn [blob] (if blob (blob->payload blob k) (k nil))))))

(defn capture-file
  "The same ceilings for a File the user picked, which is already local and needs
   no network route.

   Falls back to the image loader when createImageBitmap will not decode the file:
   it refuses some files the loader renders happily, and a picture the user can
   see in the page must not be one the export cannot carry."
  [file k]
  (let [payload (fn [blob] (if blob (blob->payload blob k) (k nil)))]
    (normalize file (fn [blob]
                      (if blob
                        (payload blob)
                        (via-object-url file payload))))))
