(ns orcpub.image-url
  "What can be told about a picture's address without asking anyone.

   Most of what goes wrong with a character portrait is visible in the string:
   the address of a PAGE rather than of the picture on it, a missing scheme, an
   http link the browser will refuse to display. Waiting for a fetch to fail
   before saying so costs a round trip and tells the person less than the string
   already did.

   Nothing here blocks anything: what must hold is enforced where it cannot be
   argued with, by address validation on the server and by CORS in the browser. So
   this is free to be occasionally wrong, and its weakest rule -- an unknown host
   with no file name -- is only a note."
  (:require [clojure.string :as s]))

(def ^:private page-not-picture
  "Addresses of pages that SHOW a picture, which people paste far more often than
   the picture's own address. Matched on the whole URL, first hit wins, so put the
   narrower patterns first."
  [[#"(?i)^https?://(?:[a-z0-9-]+\.)*pinterest\.[a-z.]+/pin/"
    "That's the Pinterest page -- right-click the pin and choose Copy image address."]

   [#"(?i)^https?://(?:www\.)?imgur\.com/(?!.*\.(?:png|jpe?g|gif|webp))"
    "That's the Imgur page -- open the image itself and copy its address."]

   [#"(?i)^https?://(?:www\.)?reddit\.com/r/.+/comments/"
    "That's the Reddit post -- open the image and copy its address."]

   [#"(?i)^https?://(?:www\.)?flickr\.com/photos/"
    "That's the Flickr page -- right-click the photo and Copy image address."]

   [#"(?i)^https?://(?:www\.)?deviantart\.com/.+/art/"
    "That's the DeviantArt page -- right-click the art and Copy image address."]

   [#"(?i)^https?://(?:www\.)?artstation\.com/artwork/"
    "That's the ArtStation page -- right-click the art and Copy image address."]

   [#"(?i)^https?://(?:www\.)?(?:instagram\.com|facebook\.com)/"
    "Instagram and Facebook need a login, so nobody can fetch the picture."]])

(def ^:private known-image-hosts
  "Hosts that serve pictures straight, and often with no file extension to go by.
   Their addresses are not worth a warning about looking like a page."
  #{"i.imgur.com" "i.pinimg.com" "cdn.discordapp.com" "media.discordapp.net"
    "upload.wikimedia.org" "static.wikia.nocookie.net" "i.redd.it"
    "images.unsplash.com" "cdn.pixabay.com" "picsum.photos" "fastly.picsum.photos"
    "raw.githubusercontent.com" "live.staticflickr.com" "i.postimg.cc" "i.ibb.co"
    "www.dndbeyond.com" "media.dndbeyond.com"})

(def ^:private known-image-host-suffixes
  [".googleusercontent.com" ".wixmp.com" ".media.tumblr.com" ".artstation.com"
   ".cloudfront.net" ".amazonaws.com" ".cdninstagram.com" ".fbcdn.net"])

(defn- host-of
  "The host part of an http(s) address, lower-cased, or nil."
  [url]
  (some-> (second (re-find #"(?i)^https?://([^/?#]+)" url))
          s/lower-case
          (s/replace #":\d+$" "")))

(defn- known-image-host? [url]
  (when-let [h (host-of url)]
    (or (contains? known-image-hosts h)
        (some #(s/ends-with? h %) known-image-host-suffixes))))

(defn- looks-like-a-file? [url]
  (re-find #"(?i)\.(png|jpe?g|gif|webp|bmp)(?:[?#]|$)" url))

(defn advise
  "What is worth saying about `url` before anyone tries to fetch it.

   Returns nil when there is nothing useful to say, or a map:

     :level   :error when it cannot work as written, :warning when it probably
              will not, :note when it merely might not
     :message one self-contained sentence, carrying its own fix where there is
              one to describe
     :fix     a corrected address, when one can be derived mechanically, else nil

   A :fix is only ever offered where the correction is mechanical. Nothing here
   guesses at a picture's address from a page's."
  [url]
  (let [raw (str url)
        trimmed (s/trim raw)]
    (cond
      (s/blank? trimmed) nil

      (not= raw trimmed)
      {:level :warning
       :message "That address has a space at one end."
       :fix trimmed}

      (re-find #"\s" trimmed)
      {:level :error
       :message "That address has a space in it -- part of it is probably missing."
       :fix nil}

      ;; A scheme that is not the web. file:// and ftp:// are refused outright by
      ;; the server, and data: and javascript: are not addresses of anything.
      (re-find #"(?i)^(?!https?://)[a-z][a-z0-9+.-]*:" trimmed)
      {:level :error
       :message "Only http and https addresses work here."
       :fix nil}

      ;; No scheme at all, but it does look like a host and path.
      (and (not (re-find #"(?i)^https?://" trimmed))
           (re-find #"(?i)^[a-z0-9-]+(\.[a-z0-9-]+)+/" trimmed))
      {:level :error
       :message "That address is missing the https:// at the front."
       :fix (str "https://" trimmed)}

      (not (re-find #"(?i)^https?://" trimmed))
      {:level :error
       :message "That doesn't look like a web address -- right-click the picture and Copy image address."
       :fix nil}

      ;; Dropbox share links serve a viewer page unless asked for the file.
      (and (re-find #"(?i)^https?://(www\.)?dropbox\.com/" trimmed)
           (re-find #"(?i)[?&]dl=0" trimmed))
      {:level :warning
       :message "That Dropbox link opens the viewer page, not the file."
       :fix (s/replace trimmed #"(?i)([?&])dl=0" "$1raw=1")}

      ;; Google Drive share links have a well-known direct form.
      (re-find #"(?i)^https?://drive\.google\.com/file/d/([^/]+)" trimmed)
      {:level :warning
       :message "That Drive link opens the viewer page, not the file."
       :fix (str "https://drive.google.com/uc?export=view&id="
                 (second (re-find #"(?i)^https?://drive\.google\.com/file/d/([^/]+)" trimmed)))}

      :else
      (or
       ;; Pages that show a picture, which is the commonest paste of all.
       (some (fn [[pattern message]]
               (when (re-find pattern trimmed)
                 {:level :error :message message :fix nil}))
             page-not-picture)

       ;; http works on the server but the browser will not display it: the
       ;; page's Content-Security-Policy allows images over https only, so an
       ;; http portrait shows as a broken thumbnail whatever the host does.
       (when (re-find #"(?i)^http://" trimmed)
         {:level :warning
          :message "This page can only display pictures over https."
          :fix (s/replace trimmed #"(?i)^http://" "https://")})

       ;; Weakest rule, so last, and only a note: plenty of hosts serve pictures
       ;; from addresses with no file name on the end.
       (when (and (not (looks-like-a-file? trimmed))
                  (not (known-image-host? trimmed)))
         {:level :note
          :message "That may be a page rather than the picture itself."
          :fix nil})))))
