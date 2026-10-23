(ns orcpub.drawn-fonts-test
  "Which fonts the export's own drawing uses, read from a real export's pages.

   A Standard 14 font is not embedded: the viewer substitutes its own, and it is
   WinAnsi-only, so a heading of Łukasz's class printed as ?ukasz."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [orcpub.routes :as routes]
            [orcpub.dnd.e5.spells :as spells]
            [orcpub.dnd.e5.magic-items :as magic-items])
  (:import (org.apache.pdfbox Loader)
           (org.apache.pdfbox.pdmodel.font PDType0Font)
           (org.apache.pdfbox.text PDFTextStripper)))

(def ^:private request
  "Every drawn path at once: spell annotations, a relabel, column headings, the site
   line, a notice (an unknown field), the spellbook, spell cards, and item cards
   with charges and attunement."
  {:character-name "Łukasz"
   :no-such-field "dropped, so the notice page prints"
   :print-character-sheet-style? 1
   :spellcasting-class-1 "Wizard"
   :spells-1-1-1 "Bless"
   :spells-1-2-1 "Identify"
   :print-spell-annotations? true
   :spell-relabels [{:section 1 :box 3 :label "4"}]
   :spell-headings [{:box 0 :section 1 :class "Czarodziej Łódź" :ability "INT" :dc "15" :attack "+7"}
                    {:box 2 :section 1 :class "Wizard" :cantrips? false}]
   :spells-known {:wizard (vec (for [k (take 6 (keys spells/spell-map))]
                                 {:key k :class "Wizard"}))}
   :spell-save-dcs {"Wizard" 15}
   :spell-attack-mods {"Wizard" 7}
   :print-spell-cards? true
   :spellbook {:classes [{:class "Wizard" :level 5 :ability "INT" :dc 15 :attack 7}]}
   ;; The fields pdf-spec/magic-item-card-info sends: full entries carry functions.
   :magic-items-known (vec (for [item (take 3 (filter magic-items/attunement-key magic-items/magic-items))]
                             (select-keys item [:name ::magic-items/name ::magic-items/type
                                                ::magic-items/subtype ::magic-items/rarity
                                                ::magic-items/attunement
                                                ::magic-items/attunement-details
                                                ::magic-items/description ::magic-items/summary
                                                ::magic-items/page ::magic-items/source])))
   :print-magic-item-cards? true})

(defn- export
  "`request` through generate-character-pdf, reloaded from the response bytes."
  []
  (let [resp (#'routes/generate-character-pdf {:form-params {:body (pr-str request)}})]
    (Loader/loadPDF (.readAllBytes (:body resp)))))

(defn- page-fonts
  "Base name of every font a page's own resources name, subset prefix stripped, with
   whether it is embedded. Page resources only: the AcroForm's /Helv is the form's
   fallback for non-text fields, not drawing."
  [doc]
  (set (for [page (.getPages doc)
             :let [res (.getResources page)]
             :when res
             n (.getFontNames res)
             :let [f (.getFont res n)]]
         [(str/replace (.getName f) #"^[A-Z]{6}\+" "")
          (or (instance? PDType0Font f) (.isEmbedded f))])))

(deftest the-exports-drawing-embeds-every-font-it-uses
  (with-open [doc (export)]
    (let [fonts (page-fonts doc)
          text (str/replace (.getText (PDFTextStripper.) doc) #"\s+" " ")]
      (is (= 3 (count (:magic-items-known request))) "the request asks for item cards")
      (is (some->> (first (:magic-items-known request)) magic-items/name-key (str/includes? text))
          "and the export draws them, so their fonts are among those checked")
      (is (= #{}
             (set (map first (remove second fonts))))
          "not embedded: the viewer's own Helvetica, WinAnsi-only"))))
