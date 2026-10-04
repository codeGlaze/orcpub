(ns orcpub.dnd.e5.artist-profile-test
  "Pure-fn tests for the artist profile pages. The registry has one artist, so
   anything about several splits Fusspot's layers across obvious stand-ins with
   with-redefs, as the credit-order tests do. None of them is a registry entry."
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.string :as s]
            [orcpub.route-map :as route-map]
            [orcpub.dnd.e5.artist-profile :as ap]
            [orcpub.dnd.e5.portrait-assets :as pa]))

(defn- stand-ins
  "Fusspot's layers split across placeholder artists: [[id name layer-keys & more]]."
  [owners]
  (let [layers (:artist/layers pa/house-pack)]
    (mapv (fn [[id nm ks extra]]
            (merge (-> pa/house-pack
                       (dissoc :artist/slug)
                       (assoc :artist/id id :artist/name nm
                              :artist/layers (select-keys layers ks)))
                   extra))
          owners)))

;; ---------- addresses ----------

(deftest fusspot-lives-at-a-readable-address
  (let [a (ap/artist-by-slug "fusspot")]
    (is (= :house-pack (:artist/id a))
        "the slug, not the id: saved portraits store :house-pack")
    (is (= "/artists/fusspot" (ap/profile-path a)))
    (is (= "/artists" (ap/index-path)))
    (testing "people retype these"
      (is (= :house-pack (:artist/id (ap/artist-by-slug "  FussPot ")))))
    (is (nil? (ap/artist-by-slug "house-pack"))
        "the internal id is not a second address")
    (is (nil? (ap/artist-by-slug "nobody")))
    (is (nil? (ap/artist-by-slug nil)))))

(deftest the-route-map-round-trips
  (is (= {:handler route-map/artist-page-route :route-params {:slug "fusspot"}}
         (route-map/match-route "/artists/fusspot")))
  (is (= route-map/artists-page-route (:handler (route-map/match-route "/artists"))))
  (is (= {:handler route-map/artist-example-route :route-params {:slug "fusspot" :n "1"}}
         (route-map/match-route "/artists/fusspot/examples/1"))))

(deftest every-slug-is-a-clean-path-segment
  (doseq [a pa/registry]
    (is (re-matches #"[a-z0-9]+(?:-[a-z0-9]+)*" (ap/slug a))
        (str (:artist/id a) " has slug " (pr-str (ap/slug a))))))

(deftest an-artist-without-a-slug-falls-back-to-the-id
  (with-redefs [pa/registry (stand-ins [[:placeholder-one "Placeholder One" [:eyes]]])]
    (is (= "placeholder-one" (ap/slug (first pa/registry))))
    (is (= "/artists/placeholder-one"
           (ap/profile-path (ap/artist-by-slug "placeholder-one"))))))

(deftest a-restated-name-does-not-move-the-address
  (try
    (pa/set-artist-overrides! {:house-pack {:artist/name "Someone Else"
                                            :artist/slug "someone-else"}})
    (let [a (ap/artist-by-slug "fusspot")]
      (is (= "Someone Else" (:artist/name a)) "the page shows the name the deployment set")
      (is (= "fusspot" (:artist/slug a)) "but the slug is not overridable"))
    (is (nil? (ap/artist-by-slug "someone-else")))
    (finally (pa/set-artist-overrides! {}))))

(deftest an-unnamed-artist-gets-no-page
  (with-redefs [pa/registry (stand-ins [[:placeholder-named "Placeholder Named" [:head]]
                                        [:placeholder-unnamed nil [:eyes]]])]
    (is (nil? (ap/artist-by-slug "placeholder-unnamed"))
        "they have not said how they want to be named; a page would invent it")
    (is (= [:placeholder-named] (mapv :artist/id (ap/all-profiles))))))

;; ---------- the credit's secondary link ----------

(deftest the-credit-points-at-one-profile-or-the-list
  (with-redefs [pa/registry (stand-ins [[:placeholder-a "Placeholder A" [:head]]
                                        [:placeholder-b "Placeholder B" [:eyes]]])]
    (let [[a b] (ap/all-profiles)]
      (is (= {:href "/artists/placeholder-a" :label "About the artist"}
             (ap/profile-link [a])))
      (is (= {:href "/artists" :label "About the artists"}
             (ap/profile-link [a b])))
      (is (nil? (ap/profile-link [])) "nobody named, no line")
      (testing "an artist on canvas without a profile does not count"
        (is (= "/artists/placeholder-a"
               (:href (ap/profile-link [a {:artist/id :not-registered :artist/name "X"}]))))))))

(deftest the-credit-name-still-goes-where-the-artist-chose
  (testing "the profile is a second link; :artist/link is untouched by it"
    (is (= "https://fusspot.rip/" (:artist/link (ap/artist-by-slug "fusspot"))))))

;; ---------- what they drew ----------

(deftest pieces-are-grouped-most-of-the-picture-first
  (let [groups (ap/pieces-by-layer (ap/artist-by-slug "fusspot"))]
    (is (= :head (:layer (first groups))) "the head carries the most weight")
    (is (not-any? #{:scalp} (map :layer groups))
        "the generated scalp is nobody's drawing")
    (is (= (map :layer groups)
           (sort-by #(- (get pa/layer-credit-weight % 1)) (map :layer groups))))
    (is (every? (comp seq :pieces) groups) "no empty groups")
    (is (= "Head" (:label (first groups))))
    (is (= (ap/piece-count (ap/artist-by-slug "fusspot"))
           (- (reduce + (map count (vals pa/asset-inventory)))
              (count (:scalp pa/asset-inventory)))))))

(deftest a-partial-artist-shows-only-their-pieces
  (with-redefs [pa/registry (stand-ins [[:placeholder-a "Placeholder A" [:head :shirt :scalp]]
                                        [:placeholder-b "Placeholder B" [:eyes :nose]]])]
    (let [b (ap/artist-by-slug "placeholder-b")]
      (is (= #{:eyes :nose} (set (map :layer (ap/pieces-by-layer b)))))
      (is (= 6 (ap/piece-count b))))))

;; ---------- links ----------

(deftest only-web-links-reach-the-page
  (is (ap/web-url? "https://fusspot.rip/"))
  (is (ap/web-url? "https://www.twitch.tv/fusspot"))
  (is (ap/web-url? "https://mastodon.example/@placeholder") "an @ in the path is a handle")
  (is (not (ap/web-url? "mailto:someone@example.test")))
  (is (not (ap/web-url? "someone@example.test")))
  (is (not (ap/web-url? "https://someone@example.test/")) "userinfo is an address in disguise")
  (is (not (ap/web-url? "javascript:alert(1)")))
  (is (not (ap/web-url? "https://example.test/ spaced")))
  (is (not (ap/web-url? nil))))

(deftest the-links-are-the-ones-listed-and-nothing-else
  (let [fusspot (ap/artist-by-slug "fusspot")]
    (is (= ["https://fusspot.rip/" "https://www.twitch.tv/fusspot"]
           (mapv :link/url (ap/listed-links fusspot)))
        "the two she asked for, in her order"))
  (testing "an email address is dropped even if one is put in the registry"
    (is (= ["Site"]
           (mapv :link/label
                 (ap/listed-links
                  {:artist/links [{:link/label "Site" :link/url "https://example.test/"}
                                  {:link/label "Email" :link/url "mailto:placeholder@example.test"}]})))))
  (testing "a single :artist/link stands in when no labelled set is given"
    (is (= [{:link/label "Site" :link/icon "site" :link/url "https://example.test/"}]
           (ap/listed-links {:artist/link "https://example.test/"}))))
  (testing "duplicates collapse, first one kept"
    (is (= ["A"]
           (mapv :link/label
                 (ap/listed-links {:artist/links [{:link/label "A" :link/url "https://example.test/"}
                                                  {:link/label "B" :link/url "https://example.test/"}]})))))
  (is (= [] (ap/listed-links {})) "no links, no buttons"))

;; ---------- examples ----------

(deftest the-seeded-randomness-is-the-same-on-both-platforms
  (testing "the examples are drawn on the server and captioned in the browser, so the
            hash and the generator must agree; values from the reference JavaScript"
    (is (= 440920331 (pa/seed->int "abc")))
    (is (= 993024918 (pa/seed->int "artist-fusspot/0")))
    (let [r (pa/mulberry32 12345)]
      (is (= [0.9797282677609473 0.3067522644996643 0.484205421525985] [(r) (r) (r)])))))

(deftest examples-are-stable
  (is (= (ap/example-portraits :house-pack) (ap/example-portraits :house-pack))
      "the page, the og image and every reload show the same pictures")
  (is (= (first (ap/example-portraits :house-pack))
         (first (ap/example-portraits :house-pack 1)))
      "the first example is the same however many are asked for, so the og
       image is the picture the page and the list lead with")
  (is (= ap/example-count (count (ap/example-portraits :house-pack)))))

(deftest examples-are-whole-drawable-and-different
  (let [xs (ap/example-portraits :house-pack)]
    (is (every? pa/drawable? xs))
    (is (every? #(= (set pa/layer-order) (set (keys (:layers %)))) xs)
        "every layer that has art is filled")
    (is (apply distinct? (map :layers xs)) "no two examples are the same picture")
    (testing "a layer with at least as many pieces as examples never repeats one"
      (doseq [k pa/layer-order
              :when (>= (count (get pa/asset-inventory k)) ap/example-count)]
        (is (apply distinct? (map #(get-in % [:layers k :asset/id]) xs)) (str k))))
    (is (every? #(every? (fn [slot] (re-matches #"#[0-9a-f]{6}" (get-in % [:colors slot])))
                         [:hair :skin :eyes :shirt])
                xs)
        "coloured from the presets")))

(deftest examples-use-the-artists-own-pieces-and-credit-the-rest
  (with-redefs [pa/registry (stand-ins [[:placeholder-a "Placeholder A"
                                         [:hair-bits :hair-back :head :scalp :shirt :hair-front :ears :mouth :bangs]]
                                        [:placeholder-b "Placeholder B" [:eyes :nose]]])]
    (let [xs (ap/example-portraits :placeholder-b)]
      (is (every? #(= :placeholder-b (pa/artist-for-asset :eyes (get-in % [:layers :eyes :asset/id]))) xs))
      (is (every? #(= :placeholder-b (pa/artist-for-asset :nose (get-in % [:layers :nose :asset/id]))) xs))
      (is (every? pa/drawable? xs) "filled out from the others so the picture is whole")
      (is (= [["Placeholder A"]] (distinct (map #(mapv :artist/name (ap/others-in % :placeholder-b)) xs)))
          "and each example says whose pieces it borrowed")
      (is (= [["Placeholder B"]]
             (distinct (map #(mapv :artist/name (ap/others-in % :placeholder-a))
                            (ap/example-portraits :placeholder-a))))
          "the other way round too, and an artist is never listed as borrowing
           from themself"))))

(deftest between-them-the-examples-use-every-piece
  (let [drawn (for [{ps :pieces} (ap/pieces-by-layer (ap/artist-by-slug "fusspot"))
                    p ps]
                (:asset/id p))
        used (set (for [x (ap/example-portraits :house-pack)
                        [_ sel] (:layers x)]
                    (:asset/id sel)))]
    (is (= 28 (count drawn)))
    (is (every? used drawn) "every piece she drew is in at least one example")))

(deftest the-examples-are-watermarked-with-her-site-and-ours
  (let [artist (ap/artist-by-slug "fusspot")]
    (is (= "fusspot.rip \u00b7 dungeonmastersvault.com"
           (ap/watermark-text artist "dungeonmastersvault.com")))
    (is (= "fusspot.rip" (ap/watermark-text artist nil)) "no site configured, her host alone")
    (is (= "Someone" (ap/watermark-text {:artist/name "Someone"} "")) "no link, her name")
    (is (= "example.org" (ap/link-host "https://www.example.org/shop?x=1")))
    (is (nil? (ap/link-host "mailto:someone@example.org")))))

(deftest examples-have-their-own-addresses
  (is (= "/artists/fusspot/examples/2" (ap/example-path (ap/artist-by-slug "fusspot") 2))))

(deftest no-examples-for-nobody
  (is (nil? (ap/example-portraits :not-registered))))

;; ---------- share tags ----------

(deftest share-tags-say-who-and-how-much
  (let [a (ap/artist-by-slug "fusspot")]
    (is (= "Fusspot — portrait artist" (ap/page-title a)))
    (is (s/includes? (ap/share-description a) (str (ap/piece-count a) " hand-drawn pieces")))
    (is (not (s/includes? (ap/share-description a) "@")))))

;; ---------- the artist can turn the page off ----------

(deftest an-artist-can-turn-their-page-off
  (is (ap/profile-enabled? (ap/artist-by-slug "fusspot")) "on unless turned off")
  (try
    (pa/set-artist-overrides! {:house-pack {:artist/profile? false}})
    (testing "off means no page at all, not a page saying it is off"
      (is (nil? (ap/artist-by-slug "fusspot")))
      (is (= [] (ap/all-profiles)) "and they drop out of the list")
      (is (nil? (ap/example-portraits :house-pack)))
      (is (nil? (ap/profile-link [(pa/artist-info :house-pack)]))
          "the credit loses its 'About the artist' line"))
    (testing "but the credit itself is untouched: the name still goes where
              the artist chose"
      (is (= "https://fusspot.rip/" (:artist/link (pa/artist-info :house-pack))))
      (is (= "Art: Fusspot" (pa/credit-line {:layers (pa/compose-for-seed "off-check")}))))
    (finally (pa/set-artist-overrides! {})))
  (is (some? (ap/artist-by-slug "fusspot")) "and turning it back on restores it"))

(deftest one-artist-off-leaves-the-others
  (with-redefs [pa/registry (stand-ins [[:placeholder-a "Placeholder A" [:head]]
                                        [:placeholder-b "Placeholder B" [:eyes]
                                         {:artist/profile? false}]])]
    (is (= [:placeholder-a] (mapv :artist/id (ap/all-profiles))))
    (is (= "/artists/placeholder-a"
           (:href (ap/profile-link (map pa/artist-info [:placeholder-a :placeholder-b]))))
        "with only one profile left among two artists, the credit links that one")))
