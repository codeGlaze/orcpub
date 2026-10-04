(ns orcpub.image-url-test
  "Advice given about a picture's address before anyone fetches it.

   Two things matter as much as the advice being right: a good address must draw
   no comment at all, and a :fix must only ever be a mechanical correction. A
   warning on a working link teaches people to ignore warnings."
  (:require [clojure.test :refer [deftest is testing]]
            [orcpub.image-url :as iu]))

(deftest a-good-address-draws-no-comment
  (testing "direct links to a picture"
    (is (nil? (iu/advise "https://i.imgur.com/aBcDeF.png")))
    (is (nil? (iu/advise "https://i.pinimg.com/originals/07/18/2b/07182b00.jpg")))
    (is (nil? (iu/advise "https://cdn.discordapp.com/embed/avatars/0.png")))
    (is (nil? (iu/advise "https://www.dndbeyond.com/avatars/25098/972/6378547.jpeg"))))
  (testing "hosts that serve pictures from addresses with no file name"
    (is (nil? (iu/advise "https://picsum.photos/400/600")))
    (is (nil? (iu/advise "https://lh3.googleusercontent.com/abc123")))
    (is (nil? (iu/advise "https://images-wixmp-ed30a86b.wixmp.com/f/abc"))))
  (testing "nothing typed yet"
    (is (nil? (iu/advise nil)))
    (is (nil? (iu/advise "")))
    (is (nil? (iu/advise "   ")))))

(deftest the-page-is-told-apart-from-the-picture
  ;; The commonest paste of all, and the one a fetch can only report as a puzzle.
  (testing "Pinterest"
    (let [{:keys [level message fix]} (iu/advise "https://www.pinterest.com/pin/1234567890/")]
      (is (= :error level))
      (is (re-find #"Pinterest page" message))
      (is (nil? fix) "a picture's address cannot be derived from a page's")))
  (testing "the picture on Pinterest is not the page"
    (is (nil? (iu/advise "https://i.pinimg.com/736x/aa/bb/cc.jpg"))))
  (testing "Imgur"
    (is (= :error (:level (iu/advise "https://imgur.com/gallery/abc123"))))
    (is (nil? (iu/advise "https://i.imgur.com/abc123.png"))))
  (testing "others that show rather than serve"
    (doseq [u ["https://www.reddit.com/r/DnD/comments/abc123/my_character/"
               "https://www.flickr.com/photos/someone/12345/"
               "https://www.deviantart.com/artist/art/A-Character-12345"
               "https://www.artstation.com/artwork/abcdef"
               "https://www.instagram.com/p/abc123/"]]
      (is (= :error (:level (iu/advise u))) (str "should be caught: " u)))))

(deftest a-malformed-address-says-what-is-missing
  (testing "no scheme, but plainly a host and path"
    (let [{:keys [level fix]} (iu/advise "i.imgur.com/aBcDeF.png")]
      (is (= :error level))
      (is (= "https://i.imgur.com/aBcDeF.png" fix))))
  (testing "not an address at all"
    (is (= :error (:level (iu/advise "my character picture"))))
    (is (nil? (:fix (iu/advise "my character picture")))))
  (testing "a scheme that is not the web"
    (doseq [u ["file:///etc/passwd" "ftp://example.com/x.png" "data:image/png;base64,AAAA"]]
      (is (= :error (:level (iu/advise u))) (str "should be refused: " u))
      (is (nil? (:fix (iu/advise u))))))
  (testing "whitespace"
    (is (= "https://i.imgur.com/a.png" (:fix (iu/advise "  https://i.imgur.com/a.png  "))))
    (is (= :error (:level (iu/advise "https://i.imgur.com/a b.png"))))))

(deftest a-mechanical-correction-is-offered-where-one-exists
  (testing "http cannot be displayed by the page, whatever the host does"
    (let [{:keys [level fix]} (iu/advise "http://i.imgur.com/a.png")]
      (is (= :warning level))
      (is (= "https://i.imgur.com/a.png" fix))))
  (testing "Dropbox share links serve a viewer page"
    (is (= "https://www.dropbox.com/s/abc/pic.png?raw=1"
           (:fix (iu/advise "https://www.dropbox.com/s/abc/pic.png?dl=0")))))
  (testing "Google Drive share links have a direct form"
    (is (= "https://drive.google.com/uc?export=view&id=1AbC_dEf"
           (:fix (iu/advise "https://drive.google.com/file/d/1AbC_dEf/view?usp=sharing"))))))

(deftest an-address-that-might-be-a-page-is-only-noted
  ;; Weakest rule in the set, so it must be the quietest: an unknown host with no
  ;; file name on the end is suspicious and nothing more.
  (let [{:keys [level fix]} (iu/advise "https://example.com/characters/mine")]
    (is (= :note level))
    (is (nil? fix)))
  (is (nil? (iu/advise "https://example.com/characters/mine.png"))
      "a file name is enough to settle it"))
