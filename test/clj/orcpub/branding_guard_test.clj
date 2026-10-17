(ns orcpub.branding-guard-test
  "Every value the operator sets for branding is checked before the site uses it: a bad one falls
   back to its default, with a warning, rather than reaching a page or stopping the server."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as s]
            [orcpub.fork.branding :as branding]))

(defn- quietly
  "Calls `f` with its warnings captured; returns [result warnings-text]."
  [f]
  (let [w (java.io.StringWriter.)
        r (binding [*out* w] (f))]
    [r (str w)]))

(deftest an-address-setting-takes-only-web-addresses-and-site-paths
  (is (= "https://example.org/help" (branding/checked-url :app-help-url "https://example.org/help" "")))
  (is (= "http://localhost:8890" (branding/checked-url :app-url "http://localhost:8890" "")))
  (is (= "/image/logo.svg" (branding/checked-url :app-logo-path "/image/logo.svg" "/image/orcpub-logo.svg")))
  (is (= "" (branding/checked-url :app-help-url "" "")) "unset stays unset")
  (let [[r warned] (quietly #(branding/checked-url :app-help-url "javascript:alert(1)" ""))]
    (is (= "" r) "a script address falls back to the default")
    (is (s/includes? warned "APP_HELP_URL") "and the warning names the setting"))
  (is (= "/image/orcpub-logo.svg"
         (first (quietly #(branding/checked-url :app-logo-path "//evil.test/x.svg" "/image/orcpub-logo.svg"))))
      "an address on another site written as a path is not a site path"))

(deftest an-email-setting-takes-only-email-addresses
  (is (= "help@example.org" (branding/checked-email :app-support-email "help@example.org" "")))
  (is (= "" (first (quietly #(branding/checked-email :app-support-email "not an address" ""))))))

(deftest a-number-setting-falls-back-instead-of-stopping-the-server
  (is (= 100 (branding/checked-int :app-field-limit-text "100" 255)))
  (is (= 255 (first (quietly #(branding/checked-int :app-field-limit-text "lots" 255)))) "not a number")
  (is (= 255 (first (quietly #(branding/checked-int :app-field-limit-text "-4" 255)))) "not a positive number")
  (is (= 255 (branding/checked-int :app-field-limit-text nil 255)) "unset"))

(deftest artist-links-from-settings-pass-the-same-check-as-artists-edits
  (let [[o _] (quietly #(branding/artist-override
                         {:name "Someone" :link "javascript:alert(1)"
                          :links [{:label "Shop" :url "https://shop.example.org" :icon "x\" onclick=\"y" :color "red;}"}
                                  {:label "Bad" :url "http://plain.example.org"}]}))]
    (is (= "Someone" (:artist/name o)))
    (is (nil? (:artist/link o)) "a script address is dropped")
    (is (= ["https://shop.example.org"] (map :link/url (:artist/links o))) "only https links stay")
    (is (= "site" (:link/icon (first (:artist/links o)))) "the icon comes from the address, not the setting")
    (is (not= "red;}" (:link/color (first (:artist/links o)))) "and so does the colour")))
