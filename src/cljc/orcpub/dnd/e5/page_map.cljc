(ns orcpub.dnd.e5.page-map
  "Generates the route -> view-fn bindings for the homebrew builder pages from the content-types
  registry, at COMPILE TIME.

  The framework doc listed this layer as \"won't generate — a view fn can't be derived from data in
  cljs\". That conflates two things. Deriving a fn from data at RUNTIME is genuinely impossible in
  cljs (no `resolve`). Emitting the symbol at COMPILE TIME is not, and every one of the registry's
  route-segs already names its view by the same convention:

      :route-seg \"draconic-ancestry-builder\"  ->  views/draconic-ancestry-builder-page

  So the map is derivable, and a macro is the right tool. D22 in the decisions record says exactly
  this: \"irreducible\" is a claim to be proven against the code, not asserted.

  A registry entry whose view fn does not exist produces the cljs compiler WARNING
  'Use of undeclared Var orcpub.dnd.e5.views/<name>-page' — verified by adding a bogus entry and
  building. It is a WARNING, not an error: the build still succeeds. So the macro removes the
  hand-wiring but does NOT by itself guarantee the view exists. The hard guards are
  builder-pages-macro-covers-every-registered-type and every-registered-type-has-a-builder-page-view
  in content_types_routes_test.

  No cycle: this requires only content-types, itself a pure-data leaf (D7). The emitted symbols are
  fully qualified, so the calling namespace needs orcpub.dnd.e5.views required but needs no
  particular alias."
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
