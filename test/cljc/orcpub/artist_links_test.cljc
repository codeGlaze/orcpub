(ns orcpub.artist-links-test
  (:require #?(:clj [clojure.test :refer [deftest is testing]]
               :cljs [cljs.test :refer-macros [deftest is testing]])
            [orcpub.artist-links :as al]))

(deftest the-rules
  (is (nil? (al/url-problem "https://fusspot.rip/")))
  (is (nil? (al/url-problem "HTTPS://Fusspot.RIP:443/path?q=1#x")))
  (doseq [bad ["http://fusspot.rip/" "fusspot.rip" "javascript:alert(1)" "ftp://x.test"]]
    (is (= "Links must start with https://" (al/url-problem bad)) bad))
  (doseq [bad ["https://" "https://localhost" "https://user@evil.test/" "https://a b.test" "https://.test"]]
    (is (= "That doesn't look like a web address" (al/url-problem bad)) bad))
  (is (= "Add a link" (al/url-problem "  "))))

(deftest icons-come-from-the-host
  (is (= "twitch" (al/icon-for-url "https://www.twitch.tv/fusspot")))
  (is (= "bluesky" (al/icon-for-url "https://bsky.app/profile/x")))
  (is (= "kofi" (al/icon-for-url "https://ko-fi.com/x")))
  (is (= "site" (al/icon-for-url "https://twitch.tv.evil.test/x")) "lookalikes get the globe")
  (is (= "site" (al/icon-for-url "https://user@twitch.tv.evil.test/x")))
  (is (= "site" (al/icon-for-url "not a link"))))

(deftest capitals-fold-to-ascii-whatever-the-locale
  ;; under a Turkish JVM the default lower case turns I into a dotless i, so TWITCH would
  ;; stop matching twitch; the locale CI run is what makes this one able to fail
  (is (= "www.twitch.tv" (:host (al/parse "HTTPS://WWW.TWITCH.TV/FUSSPOT"))))
  (is (= "twitch" (al/icon-for-url "HTTPS://WWW.TWITCH.TV/FUSSPOT"))))

(deftest while-typing
  (testing "nothing while the start of https:// is still being typed"
    (doseq [partial ["" "h" "htt" "https" "https:/" "https://" "HTTPS:"]]
      (is (nil? (al/typing-problem partial false)) partial)))
  (testing "a wrong scheme is flagged the moment it goes wrong, not at save"
    (is (= "Links must start with https://" (al/typing-problem "http://" false)))
    (is (= "Links must start with https://" (al/typing-problem "fusspot.rip" false)))
    (is (= "Links must start with https://" (al/typing-problem "www." false))))
  (testing "an unfinished address waits until the box is left"
    (is (nil? (al/typing-problem "https://fuss" false)))
    (is (= "That doesn't look like a web address" (al/typing-problem "https://fuss" true))))
  (is (nil? (al/typing-problem "https://fusspot.rip/" true))))

(deftest what-blocks-a-save
  (is (empty? (al/edit-errors {:name "Fuss" :link "" :links [{:url "https://twitch.tv/f"} {:url ""}]})))
  (is (= {0 "Links must start with https://"}
         (:link-errors (al/edit-errors {:links [{:url " "} {:url "http://x.test"}]})))
      "indexed among the non-blank rows, as the server reports them")
  (is (contains? (al/edit-errors {:name (apply str (repeat 61 "x"))}) :name))
  (is (contains? (al/edit-errors {:links (repeat 5 {:url "https://a.test/"})}) :links)))
