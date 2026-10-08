;; Writes the app's spell names and keys as JSON, for emit-rules.py to resolve spell links
;; against the keys the app routes by. Run from a code worktree:
;;   java -cp "src/cljc:src/clj:$(lein classpath)" clojure.main link-targets.clj OUT.json
(require 'orcpub.dnd.e5.spells)
(let [[out] *command-line-args*
      spells (map (fn [s] (str "{\"name\":" (pr-str (:name s)) ",\"key\":" (pr-str (name (:key s))) "}"))
                  @(resolve 'orcpub.dnd.e5.spells/spells))]
  (spit out (str "{\"spells\":[" (clojure.string/join "," spells) "]}"))
  (println "  spells:" (count spells) "->" out))
