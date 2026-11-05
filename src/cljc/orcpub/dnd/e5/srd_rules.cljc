(ns orcpub.dnd.e5.srd-rules
  "Lookups over the SRD rules file, `resources/public/srd/2014/rules.edn`, as parsed from EDN.
   Shared by the Rules pages, the link previews and the Orcacle. The file is generated, and its
   block format is described in `resources/srd/README.md`."
  (:require [clojure.string :as s]
            [orcpub.common :as common]))

(def data-path
  "URL of the 2014 rules file, served from `resources/public`."
  "/srd/2014/rules.edn")

(def notes-path
  "URL of our own clarification notes on the rules, served from `resources/public`. Not SRD text."
  "/notes/rules-clarifications.edn")

(defn section
  "The section keyed `key` in the parsed rules file `data`, or nil."
  [data key]
  (some #(when (= key (:key %)) %) (:sections data)))

(defn headings
  "Every rule heading in `data`, in file order.

   Args: `data`, the parsed rules file or nil.
   Returns: a seq of {:section key :anchor key :name text :level n}, one per rule heading,
   preceded by one {:section key :anchor nil :name section-name :level 1} per section."
  [data]
  (mapcat (fn [{:keys [key name body]}]
            (cons {:section key :anchor nil :name name :level 1}
                  (keep (fn [[tag level text anchor]]
                          (when (and (= :h tag) anchor)
                            {:section key :anchor anchor :name text :level level}))
                        body)))
          (:sections data)))

(defn rule-blocks
  "The blocks of one rule: from its heading up to the next heading at the same level or above.

   Args: `data`, the parsed rules file; `section`, a section key; `anchor`, a rule anchor, or
   nil for the section's opening text (the blocks before its first heading).
   Returns: a vector of blocks, without the rule's own heading; empty when nothing matches."
  [data section-key anchor]
  (let [body (:body (section data section-key))]
    (if (nil? anchor)
      (vec (take-while #(not= :h (first %)) body))
      (let [[_ & after] (drop-while (fn [[tag _ _ a]] (not (and (= :h tag) (= anchor a)))) body)
            level (some (fn [[tag l _ a]] (when (and (= :h tag) (= anchor a)) l)) body)]
        (vec (take-while (fn [[tag l]] (not (and (= :h tag) (<= l level)))) after))))))

(defn find-rule
  "The rule or section named exactly `text`, ignoring case and surrounding space.

   Args: `data`, the parsed rules file or nil; `text`, a search string or nil.
   Returns: the matching entry of `headings`, or nil."
  [data text]
  (when-not (s/blank? text)
    (let [t (common/ascii-lower-case (s/trim text))]
      (some #(when (= t (common/ascii-lower-case (:name %))) %) (headings data)))))

(defn matching
  "Rules and sections whose names contain `text`, ignoring case, in file order, at most 12.

   Args: `data`, the parsed rules file or nil; `text`, a search string.
   Returns: a seq of `headings` entries; empty for text under three characters."
  [data text]
  (let [t (common/ascii-lower-case (s/trim (or text "")))]
    (when (>= (count t) 3)
      (take 12 (filter #(s/includes? (common/ascii-lower-case (:name %)) t) (headings data))))))

(defn with-notes
  "A section's body with each of `notes` placed after the rule it clarifies, as a [:note note]
   block: after the rule's last block, before the next heading at its level or above.

   Args: `body`, a section's blocks; `notes`, maps with `:anchor` (a rule anchor in this body).
   Returns: the body with the notes inserted; notes whose anchor is not here are left out."
  [body notes]
  (let [by-anchor (group-by :anchor notes)]
    (loop [[block & more] body
           open nil          ; [level notes] of the rule whose notes are still to place
           out []]
      (let [[tag level _ anchor] block
            closes? (and open (or (nil? block) (and (= :h tag) (<= level (first open)))))
            out (cond-> out closes? (into (map (fn [n] [:note n]) (second open))))
            open (if closes? nil open)]
        (if (nil? block)
          out
          (recur more
                 (if (and (= :h tag) (seq (by-anchor anchor))) [level (by-anchor anchor)] open)
                 (conj out block)))))))

