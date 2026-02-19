(ns orcpub.dnd.e5.folder
  (:require [clojure.spec.alpha :as spec]))

(spec/def ::owner string?)
(spec/def ::name string?)
(spec/def ::character-id int?)
(spec/def ::character-ids (spec/coll-of ::character-id))
(spec/def ::folder (spec/keys :req [::name]))
