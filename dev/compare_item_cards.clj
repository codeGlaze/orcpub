;; Renders every card frame family across the five rarities, then the name
;; treatments, so the alternatives can be looked at instead of argued about.
;;
;;   lein run -m clojure.main dev/compare_item_cards.clj
;;   OUT=/tmp/styles.pdf lein run -m clojure.main dev/compare_item_cards.clj
;;
;; PDFBox's renderer cannot rasterise the embedded faces -- open the result in a
;; browser, not with PDFRenderer, or every card looks like an encoding bug.

(require '[orcpub.pdf :as pdf] '[orcpub.dnd.e5.magic-items :as mi])
(import '[org.apache.pdfbox.pdmodel PDDocument PDPage PDPageContentStream] '[java.io File])

(def rarities [:common :uncommon :rare :very-rare :legendary])

(defn sample
  "One short item per rarity, so the frames are compared and not the prose."
  [r]
  (let [base (->> (vals mi/magic-item-map)
                  (filter #(= r (:orcpub.dnd.e5.magic-items/rarity %)))
                  (filter :orcpub.dnd.e5.magic-items/description)
                  (sort-by #(count (:orcpub.dnd.e5.magic-items/description %)))
                  first)]
    (assoc base :orcpub.dnd.e5.magic-items/description
           (str "A " (name r) " item, shown to compare the frame it is given. "
                "The words are the same on every card so the decoration is what differs."))))

(defn page! [doc fonts img items opts]
  (let [page (PDPage.)]
    (.addPage doc page)
    (with-open [cs (PDPageContentStream. doc page)]
      (pdf/print-items cs doc fonts img 2.5 3.5 items 0 false false opts))))

(let [doc (PDDocument.)
      fonts (pdf/load-fonts doc)
      img (pdf/make-image-loader doc)
      ladder (mapv sample rarities)]
  ;; One page per family: the five ranks side by side.
  (doseq [family pdf/card-flourishes]
    (page! doc fonts img ladder {:flourish family}))
  ;; Name treatments, all on the winning-ish frame.
  (doseq [[face size track] [[:bold 12 0.15] [:bold 13.5 0.3]
                             [:bold-italic 12.5 0.2] [:plain 13 0.55]]]
    (page! doc fonts img (mapv sample [:rare :legendary :uncommon])
           {:flourish :diamonds :name-face face :name-size size :name-tracking track}))
  (.save doc (File. (or (System/getenv "OUT") "target/item-card-styles.pdf")))
  (.close doc)
  (println "families:" (pr-str pdf/card-flourishes) "pages:" (+ (count pdf/card-flourishes) 4)))
