(ns orcpub.dnd.e5.views.guards
  "What keeps one broken thing from taking a page down with it: React's error boundary, the
   per-item render-guard and its fallback, the feature list that traces a failure to the item
   that caused it, a builder's form with each section guarded, and the app-root fallback, the
   last resort. See docs/HOMEBREW_GUARD.md, 5."
  (:require [reagent.core :as r]
            [clojure.string :as s]
            [orcpub.common :as common]
            [orcpub.dnd.e5.homebrew-check :as homebrew-check]))

(defn error-boundary
  "React error boundary: if a child throws while rendering, renders
   (fallback error component-stack retry) instead of unmounting the whole app. Key it on its
   content (e.g. the selected tab) so navigating away clears the error.
   GOTCHA: only getDerivedStateFromError re-renders into the fallback; component-did-catch
   logs the component stack and stores it for the fallback."
  [_fallback _child]
  (r/create-class
   {:display-name "error-boundary"
    :constructor (fn [this _props]
                   (set! (.-state this) #js {:error nil :componentStack nil}))
    :get-derived-state-from-error (fn [error] #js {:error error})
    :component-did-catch (fn [this e info]
                           (let [stack (some-> info .-componentStack)]
                             (js/console.error "[character render error]" e stack)
                             ;; Stash the component stack so the recovery panel can show
                             ;; *which* component threw — the only location info we can
                             ;; offer for a failure we didn't anticipate.
                             (.setState this #js {:componentStack stack})))
    :reagent-render
    (fn [fallback child]
      (let [this (r/current-component)]
        (if-let [e (.. this -state -error)]
          (fallback e
                    (.. this -state -componentStack)
                    ;; Reagent re-renders on new props, not on React state alone, so
                    ;; clearing the error has to force the redraw as well.
                    #(.setState this #js {:error nil :componentStack nil}
                                (fn [] (.forceUpdate this))))
          child)))}))

(defn locate-hint
  "Best-effort human locator for a malformed feature, built only from fields that
   actually point at a place in the builder — which class/subclass or homebrew
   source it came from, and the level it's gained. Never its :name (usually the
   missing part), and never its :page: the page is a physical-rulebook reference,
   not a place to fix anything in the app. Returns nil when there's nothing
   builder-locating to go on (the raw data dump still shows the page etc.)."
  [{:keys [class-key source level]}]
  (let [where (cond
                (string? source) (str "the “" source "” source")
                source           (str "the “" (common/kw-to-name source) "” source")
                class-key        (str "your " (s/capitalize (common/kw-to-name class-key)))
                :else            nil)
        bits (cond-> []
               where (conj (str "from " where))
               level (conj (str "gained at level " level)))]
    (when (seq bits) (s/join ", " bits))))

(defn guard-fallback
  "Inline fallback for a single item that threw while rendering: say where it
   likely came from (so the user can find and fix it in the builder) and dump its
   raw data so the exact malformed value is visible. No per-bug detector required;
   works for whatever was being rendered."
  [data _error]
  (let [hint (try (locate-hint data) (catch :default _ nil))]
    [:div.m-t-10.p-5.f-s-12
     {:style {:border "1px dashed #e9a227" :border-radius "4px"}}
     [:div.f-w-b "⚠ A feature here couldn’t be displayed"]
     (when hint [:div.m-t-2 (str "Likely " hint " — open it in the builder to fix.")])
     [:pre.wsp-prw.f-s-11.m-t-5 (pr-str data)]]))

(defn render-guard
  "Wrap one piece of UI so a render exception is contained to just this item
   (showing its raw data via guard-fallback) instead of blanking the whole page.
   General — catches any error type, not a specific known failure mode."
  [data child]
  [error-boundary
   (fn [error _stack _retry] [guard-fallback data error])
   child])

(defn- without-nth
  "items with index i removed (nil-safe, unlike keep-indexed)."
  [items i]
  (into (subvec items 0 i) (subvec items (inc i))))

(defn isolate-culprit
  "Fault isolation by re-execution, the active counterpart to render-guard. `render-coll`
   processes the collection `items` and may throw. Returns the item(s) whose removal stops
   it throwing -- catching aggregate failures (a sort over a nil key) a per-item guard never
   sees -- or nil if it doesn't throw. GOTCHA: with two independent faulty items neither is
   necessary, so it falls back to the smallest failing subset found by bisection."
  [render-coll items]
  (letfn [(throws? [coll]
            (try (doall (render-coll coll)) false (catch :default _ true)))
          (self-bad [coll]
            ;; items that still break when paired with a copy of themselves — i.e.
            ;; that individually carry the bad value, distinct from an innocent
            ;; neighbour only implicated because the failure needs ≥2 elements.
            (filterv #(throws? [% %]) coll))
          (bisect [its]
            (if (<= (count its) 1)
              its
              (let [mid (quot (count its) 2)
                    l (subvec its 0 mid) r (subvec its mid)]
                (cond (throws? l) (recur l)
                      (throws? r) (recur r)
                      :else its))))]
    (let [items (vec items)]
      (when (throws? items)
        (let [necessary (mapv items (filterv #(not (throws? (without-nth items %)))
                                             (range (count items))))]
          (cond
            (= 1 (count necessary)) necessary
            ;; >1 "necessary" is the comparison-failure trap: removing ANY of a
            ;; small set drops below the ≥2 elements the comparator needs, so each
            ;; looks necessary. Keep only the ones that break paired with themselves.
            (seq necessary) (let [sb (self-bad necessary)]
                              (if (seq sb) sb necessary))
            ;; nothing individually necessary (independent multi-fault): the items
            ;; that break self-paired, else bisect to a smallest failing subset.
            :else (let [sb (self-bad items)]
                    (if (seq sb) sb (bisect items)))))))))

(defn guarded-feature-list
  "Render `items` via `render-coll`. If the whole render throws, LEAD with the
   problem — surface the item(s) the isolator traced the failure to, with where to
   fix them — then render everything that DID work below. This is the auditing
   version of render-guard: it handles failures in the collection-level logic, not
   just per-item render throws."
  [render-coll items]
  ;; Render once: the realized result is reused on the happy path, so the sort and
  ;; hiccup build don't run twice on every render — only the error path pays extra.
  (let [rendered (try (doall (render-coll items)) (catch :default _ ::failed))]
    (if-not (= rendered ::failed)
      (into [:<>] rendered)
      (let [culprits (isolate-culprit render-coll items)
            culprit? (set culprits)
            survivors (remove culprit? items)]
        [:div
         ;; the problem, first and prominent — that's the thing the user must act on
         [:div.p-10.m-b-10.f-s-14
          {:style {:border "1px solid #e9a227" :border-radius "5px"
                   :background "rgba(233,162,39,0.12)"}}
          [:div.f-w-b.f-s-16
           (if (= 1 (count culprits))
             "⚠ A feature in this section couldn’t be displayed"
             (str "⚠ " (count culprits) " features in this section couldn’t be displayed"))]
          (doall
           (map-indexed
            (fn [i c]
              (let [hint (try (locate-hint c) (catch :default _ nil))]
                ^{:key i}
                [:div.m-t-5
                 (when hint [:div.f-w-600 (str "Likely " hint " — open it in the builder to fix.")])
                 [:pre.wsp-prw.f-s-11.m-t-2 {:style {:user-select "text"}} (pr-str c)]]))
            culprits))]
         ;; everything that rendered fine, below
         (when (seq survivors) (render-coll survivors))]))))

(defn app-error-fallback
  "Top-level catch-all shown when ANY page throws below the app-root boundary —
   the guarantee that the app never black-screens (goal #1). Generic by design (it
   can be any page): say something broke, reassure the data is safe, offer recovery,
   and expose the error for a report. Finer boundaries below this one give better,
   more specific messages where they apply; this is the last line of defense."
  [_error _stack retry]
  ;; Bring the boot-shell rescue control back: the app rendered cleanly once and
  ;; took it away, then this page threw. Getting homebrew out matters most in
  ;; exactly the state where the app has stopped being able to export it.
  (when-let [rescue (aget js/window "orcpubBootRescue")]
    (rescue))
  ;; The page may have failed on homebrew that only breaks once it is drawn. Check every
  ;; entry deeply; if any are set aside, try the page again.
  (js/setTimeout #(when (seq (homebrew-check/check-and-set-aside! nil))
                    (js/setTimeout retry 500))
                 0)
  (let [show? (r/atom false)
        copied? (r/atom false)]
    (fn [error stack retry]
      (let [report (str (or (some-> error .-stack) (str error))
                        (when stack (str "\n\nComponent stack:" stack)))]
        [:div.p-20.f-s-14.main-text-color
         [:div.f-w-b.f-s-24.m-b-10 "Something went wrong on this page"]
         [:div.m-b-15.l-h-19
          "This page hit an unexpected error and couldn’t finish loading. Your saved "
          "data wasn’t affected. Head back to your characters, or reload to try again — "
          "if it keeps happening, copy the details below and send them to us."]
         [:div.flex.flex-wrap.align-items-c
          [:button.form-button.m-r-10.m-b-5
           {:on-click #(set! (.-href js/window.location) "/pages/dnd/5e/characters")}
           "Go to my characters"]
          [:button.form-button.m-b-5 {:on-click #(.reload js/window.location)} "Reload page"]]
         [:div.m-t-10
          [:span.orange.underline.pointer
           {:on-click #(swap! show? not)}
           (if @show? "Hide details" "Show details")]
          (when @show?
            [:div
             [:button.form-button.f-s-11.m-t-5
              {:on-click (fn []
                           (some-> js/navigator .-clipboard (.writeText report))
                           (reset! copied? true))}
              (if @copied? "Copied!" "Copy")]
             [:pre.f-s-12.m-t-5.wsp-prw {:style {:user-select "text"}} report]])]]))))

(defn guarded-form
  "`builder`'s form with each of its sections in a render-guard, so a field that throws while
   drawing costs that section and the rest of the form still edits and saves; one that throws
   before it has a form shows `item` in the fallback. Every builder returns its form directly
   (none keeps state in a render function), so calling it here draws the same thing."
  [item builder]
  (try
    (let [form (builder)]
      (if (and (vector? form) (keyword? (first form)))
        (let [[tag & more] form
              [attrs children] (if (map? (first more)) [(first more) (rest more)] [nil more])]
          (into (cond-> [tag] attrs (conj attrs))
                (map #(if (vector? %) [render-guard item %] %))
                children))
        form))
    (catch :default e
      (js/console.error "A builder could not draw its form:" e)
      [guard-fallback item e])))
