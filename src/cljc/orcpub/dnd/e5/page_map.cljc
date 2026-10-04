(ns orcpub.dnd.e5.page-map
  "Compile-time map of each content-type :route-kw to its builder view fn, emitted as the symbol
  `orcpub.dnd.e5.views/<route-seg>-page` (a macro: cljs cannot resolve a fn from data at runtime).
  GOTCHA: a registry entry with no such view fn is only a cljs compiler WARNING, not an error; the
  hard guards are in content_types_routes_test. Requires only content-types, a pure-data leaf; the
  caller must require orcpub.dnd.e5.views, under any alias."
  #?(:cljs (:require-macros [orcpub.dnd.e5.page-map]))
  (:require [orcpub.dnd.e5.content-types :as ct]))

#?(:clj
   (defmacro builder-pages
     "A map of every registry :route-kw to its `orcpub.dnd.e5.views/<route-seg>-page` fn."
     []
     (into {}
           (map (fn [{:keys [route-kw route-seg]}]
                  [route-kw (symbol "orcpub.dnd.e5.views" (str route-seg "-page"))]))
           ct/content-types)))
