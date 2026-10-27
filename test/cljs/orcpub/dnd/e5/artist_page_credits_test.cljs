(ns orcpub.dnd.e5.artist-page-credits-test
  "Artist credits are fetched after the page loads, so an artist page already on screen has to
   redraw when they arrive: a new name, new links, or the page turned off."
  (:require [cljs.test :refer-macros [deftest is testing]]
            [re-frame.core :refer [dispatch-sync]]
            [reagent.core :as r]
            [reagent.dom :as rdom]
            [orcpub.dnd.e5.events]
            [orcpub.dnd.e5.subs]
            [orcpub.dnd.e5.views.artist-page :as artist-page]
            [orcpub.dnd.e5.views :as views]
            [orcpub.dnd.e5.portrait-assets :as pa]))

(defn- with-page
  "Renders `page` into a detached element, calls `f` with it, and cleans up, restoring the
   artist overrides."
  [page f]
  (let [before (pa/current-overrides)
        el (.createElement js/document "div")]
    (try
      (dispatch-sync [:portrait/artists-loaded {}])
      (.appendChild (.-body js/document) el)
      (rdom/render page el)
      (r/flush)
      (f el)
      (finally
        (rdom/unmount-component-at-node el)
        (.remove el)
        (pa/set-artist-overrides! before)))))

(deftest the-profile-redraws-when-the-credits-arrive
  (with-page [artist-page/artist-page {:slug "fusspot"}]
    (fn [el]
      (is (= "Fusspot" (some-> (.querySelector el "h1.ap-name") .-textContent))
          "the registry's public default until they arrive")
      (dispatch-sync [:portrait/artists-loaded {"house-pack" {:artist/name "Fuss <b>MARK</b>"}}])
      (r/flush)
      (testing "the arrival redraws the page already on screen"
        (is (= "Fuss <b>MARK</b>" (some-> (.querySelector el "h1.ap-name") .-textContent))
            "the new name, shown as text")
        (is (nil? (.querySelector el "h1.ap-name b")) "never as markup")))))

(deftest the-list-redraws-when-the-credits-arrive
  (with-page [artist-page/artists-page nil]
    (fn [el]
      (is (re-find #"Fusspot" (.-textContent el)))
      (dispatch-sync [:portrait/artists-loaded {"house-pack" {:artist/name "Someone Else"}}])
      (r/flush)
      (is (re-find #"Someone Else" (.-textContent el)) "the list shows the arrived name"))))

(deftest a-page-turned-off-in-the-credits-goes-away-on-arrival
  (with-page [artist-page/artist-page {:slug "fusspot"}]
    (fn [el]
      (is (some? (.querySelector el "h1.ap-name")))
      (dispatch-sync [:portrait/artists-loaded {"house-pack" {:artist/profile? false}}])
      (r/flush)
      (is (nil? (.querySelector el "h1.ap-name")) "the profile is gone")
      (is (some? (.querySelector el ".ap-missing")) "and the page says nobody is here"))))

(deftest every-link-on-the-page-is-https-marked-and-names-its-domain
  (with-page [artist-page/artist-page {:slug "fusspot"}]
    (fn [el]
      (dispatch-sync [:portrait/artists-loaded
                      {"house-pack" {:artist/links [{:link/label "Shop" :link/url "https://exаmple.test/shop"}
                                                    {:link/label "Old" :link/url "http://example.test/"}]}}])
      (r/flush)
      (let [links (array-seq (.querySelectorAll el "a.ap-link"))]
        (is (= ["https://xn--exmple-4nf.test/shop"] (map #(.getAttribute % "href") links))
            "the plain-http link is not drawn, the other goes to its encoded host")
        (is (every? #(= "noopener" (.getAttribute % "rel")) links))
        (is (= "xn--exmple-4nf.test" (some-> (.querySelector el "a.ap-link .ap-link-host") .-textContent))
            "the button shows the real domain")))))

(deftest link-marks-are-styled-by-the-stylesheet
  (testing "a mark's own icon and colour arrive as CSS variables; the rule that uses them is Garden's"
    (with-page [artist-page/artist-page {:slug "fusspot"}]
      (fn [el]
        (let [ico (.querySelector el "a.ap-link .lk-ico")
              props (map #(.item (.-style ico) %) (range (.-length (.-style ico))))]
          (is (seq props))
          (is (every? #(.startsWith % "--") props) (str "only variables inline: " (vec props))))))))

(deftest the-page-shows-only-the-parts-the-artist-keeps-on
  (with-page [artist-page/artist-page {:slug "fusspot"}]
    (fn [el]
      (dispatch-sync [:portrait/artists-loaded {"house-pack" {:artist/show-examples? false
                                                               :artist/show-links? false
                                                               :artist/show-count? false}}])
      (r/flush)
      (is (= "Fusspot" (some-> (.querySelector el "h1.ap-name") .-textContent)) "the name always shows")
      (is (nil? (.querySelector el ".ap-examples")) "no examples")
      (is (nil? (.querySelector el "a.ap-link")) "no links")
      (is (not (re-find #"hand-drawn" (.-textContent el))) "no piece count"))))

(deftest the-list-shows-only-the-parts-the-artist-keeps-on
  (with-page [artist-page/artists-page nil]
    (fn [el]
      (dispatch-sync [:portrait/artists-loaded {"house-pack" {:artist/show-examples? false
                                                               :artist/show-count? false}}])
      (r/flush)
      (is (re-find #"Fusspot" (.-textContent el)))
      (is (nil? (.querySelector el ".ap-index-card img")) "no example on the card")
      (is (nil? (.querySelector el ".ap-index-count")) "no count on the card"))))

(deftest the-artist-switches-their-page-on-my-account
  (let [sent (atom nil)]
    (with-redefs [re-frame.core/dispatch (fn [ev] (when (= :save-artist-credit (first ev)) (reset! sent (second ev))))]
      (with-page [views/artist-credit-form {:own {:artist/show-links? false} :default {} :current {} :locked #{}}]
        (fn [el]
          (let [box (fn [label] (some #(when (= label (.-textContent %)) %)
                                      (array-seq (.querySelectorAll el "[role=checkbox]"))))]
            (is (= "true" (some-> (box "Show my artist page") (.getAttribute "aria-checked"))))
            (is (= "false" (some-> (box "Links") (.getAttribute "aria-checked"))) "read back from the saved credit")
            (some-> (box "Example portraits") .click)
            (r/flush)
            (some-> (some #(when (= "Save credit" (.-textContent %)) %) (array-seq (.querySelectorAll el "button"))) .click)
            (is (= {:profile? true :show-examples? false :show-links? false :show-count? true}
                   (select-keys @sent [:profile? :show-examples? :show-links? :show-count?])))))))))

(deftest a-switch-the-site-fixes-shows-its-setting-and-is-locked
  (with-page [views/artist-credit-form {:own {} :default {} :current {:artist/profile? false}
                                        :locked #{:artist/profile?}}]
    (fn [el]
      (let [box (some #(when (= "Show my artist page" (.-textContent %)) %)
                      (array-seq (.querySelectorAll el "[role=checkbox]")))]
        (is (= "false" (some-> box (.getAttribute "aria-checked"))) "reads Off, as the site has it")
        (is (= "true" (some-> box (.getAttribute "aria-disabled"))) "and cannot be switched")
        (is (re-find #"Set by the site's admins" (.-textContent el)))))))
