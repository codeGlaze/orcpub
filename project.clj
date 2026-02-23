; Allow http connection, as org.apache.pdfbox/pdfbox has http dependnecies
(require 'cemerick.pomegranate.aether)
(cemerick.pomegranate.aether/register-wagon-factory!
 "http" #(org.apache.maven.wagon.providers.http.HttpWagon.))

(defproject orcpub "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :main orcpub.server

  :min-lein-version "2.7.1"

  :repositories [["apache" "http://repository.apache.org/snapshots/"]
                 ; This allows us to seamlessly load jars from local disk.
                 ["local" {:url "file:lib"
                           :checksum :ignore
                           :releases {:checksum :ignore}}]
                 ]
  :mirrors {"apache" {:url "https://repository.apache.org/snapshots/"}}

  :dependencies [[org.clojure/clojure "1.12.4"]
                 [org.clojure/test.check "1.1.1"]
                 [org.clojure/clojurescript "1.12.134"]
                 [org.clojure/core.async "1.8.741"]
                 ;; React 18 + Reagent 2.0 (Concurrent Mode)
                 [cljsjs/react "18.3.1-1"]
                 [cljsjs/react-dom "18.3.1-1"]
                 [cljsjs/filesaverjs "1.3.3-0"]
                 [com.cognitect/transit-cljs "0.8.280"]
                 [cljs-http "0.1.49"]
                 [com.andrewmcveigh/cljs-time "0.5.2"]
                 [clojure.java-time "1.4.2"]
                 [clj-http "3.13.1"]
                 [com.yetanalytics/ring-etag-middleware "0.1.1"]

                 [org.clojure/core.match "1.1.1"]
                 [re-frame "1.4.4"]
                 [reagent "2.0.1"]
                 [com.lambdaisland/garden "1.9.606"]
                 [org.apache.pdfbox/pdfbox "3.0.6"]
                 ;; Pedestal 0.7.0 uses Jetty 11, which is compatible with figwheel-main's Ring adapter.
                 ;; Pedestal 0.7.1+ and 0.8.x use Jetty 12, causing NoClassDefFoundError: ScopedHandler
                 ;; (removed in Jetty 12). Upgrade blocked until figwheel-main supports Jetty 12.
                 [io.pedestal/pedestal.service "0.7.0"]
                 [io.pedestal/pedestal.route "0.7.0"]
                 [io.pedestal/pedestal.jetty "0.7.0"]
                 [io.pedestal/pedestal.error "0.7.0"]
                 [org.clojure/data.json "2.5.0"]
                 [org.slf4j/slf4j-simple "1.7.21"]
                 [buddy/buddy-auth "3.0.323"]
                 [buddy/buddy-hashers "2.0.167"]
                 [reloaded.repl "0.2.3"]
                 [bidi "2.1.6"]

                 [com.stuartsierra/component "1.2.0"]
                 [com.google.guava/guava "32.1.2-jre"]

                 [com.fasterxml.jackson.core/jackson-databind "2.15.2"]
                 [com.fasterxml.jackson.core/jackson-core "2.15.2"]
                 [com.fasterxml.jackson.core/jackson-annotations "2.15.2"]

                 ;; Required for Pedestal 0.7.x ring-middlewares
                 [commons-io/commons-io "2.15.1"]

                 [hiccup "2.0.0"]
                 [com.draines/postal "2.0.5"]
                 [environ "1.2.0"]

                 [pdfkit-clj "0.1.7"]
                 ;; datomock fork with Datomic Pro 1.0.6527+ compatibility (new transact signature)
                 ;; Original vvvvalvalval/datomock 0.2.0 causes AbstractMethodError with Datomic Pro
                 [org.clojars.favila/datomock "0.2.2-favila1"]
                 ;; Datomic Pro: Free under Apache 2.0, supports Java 11/17/21, actively maintained.
                 ;; Exclude slf4j-nop to avoid duplicate SLF4J binding warnings.
                 ;; Installed to lib/com/datomic/datomic-pro/1.0.7482/ during Docker build/postCreateCommand
                 ;; Uses existing file:lib repository pattern (same as pdfbox)
                 ;; Latest version: https://docs.datomic.com/releases-pro.html
                 ;[com.datomic/datomic-pro "1.0.7482" :exclusions [org.slf4j/slf4j-nop]]
                 [com.datomic/peer "1.0.7482" :exclusions [org.slf4j/slf4j-nop]]
                 ;; cuerdas 026.415: Latest release on Clojars... does not match GH release versioning.
                 [funcool/cuerdas "2026.415"]
                 [camel-snake-kebab "0.4.0"]
                 [org.webjars/font-awesome "5.13.1"]
                 ;; See docs/UPGRADE_DEPENDENCIES.md for why this is needed on Java 9+/21
                 [javax.servlet/javax.servlet-api "4.0.1"]
                 ;; figwheel-main for hot-reload dev (compatible with Pedestal 0.7.0's Jetty 11)
                 [com.bhauman/figwheel-main "0.2.20"]
                 [com.bhauman/rebel-readline-cljs "0.1.4"]
              ]
  :plugins [[lein-cljsbuild "1.1.8" :exclusions [[org.clojure/clojure]]]
            [lein-localrepo "0.5.4"]
            [lein-garden "0.3.0"]
            [lein-environ "1.2.0"]
            [dev.weavejester/lein-cljfmt "0.16.0"]
            [lein-kibit "0.1.11"]
            #_[lein-resource "16.9.1"]]

  :source-paths ["src/clj" "src/cljc" "src/cljs"]

  :test-paths ["test/clj" "test/cljc" "test/cljs"]

  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"]

  :resource-paths ["resources" "target" "resources/.ebextensions/"]

  :uberjar-name "orcpub.jar"

  :garden {:builds [{;; Optional name of the build:
                     :id "screen"
                     ;; Source paths where the stylesheet source code is
                     :source-paths ["src/clj" "src/cljc"]
                     ;; The var containing your stylesheet:
                     :stylesheet orcpub.styles.core/app
                     ;; Compiler flags passed to `garden.core/css`:
                     :compiler {;; Where to save the file:
                                :output-to "resources/public/css/compiled/styles.css"
                                ;; Compress the output?
                                :pretty-print? false}}]}

  ;; NOTE: Garden compilation removed from global :prep-tasks for faster REPL startup.
  ;; CSS is compiled via: ./menu start garden, lein garden once, or automatically in uberjar build.
  ;; The compiled CSS is checked into resources/public/css/compiled/styles.css

  :cljsbuild {:builds
              {:dev
               {:source-paths ["web/cljs" "src/cljc" "src/cljs"]

                ;; the presence of a :figwheel configuration here
                ;; will cause figwheel to inject the figwheel client
                ;; into your build
                :figwheel     {:on-jsload "orcpub.core/on-js-reload"
                               ;; :open-urls will pop open your application
                               ;; in the default browser once Figwheel has
                               ;; started and complied your application.
                               ;; Comment this out once it no longer serves you.
                               :open-urls ["http://localhost:8890"]}

                :compiler     {:main                 orcpub.core
                               :asset-path           "/js/compiled/out"
                               :output-to            "resources/public/js/compiled/orcpub.js"
                               :output-dir           "resources/public/js/compiled/out"
                               :source-map-timestamp true
                               :pretty-print         true
                               :closure-defines      {goog.DEBUG true}
                               :optimizations        :none}}}}

  :figwheel {;; :http-server-root "public" ;; default and assumes "resources"
             ;; :server-port 3449 ;; default
             ;; :server-ip "127.0.0.1"

             :css-dirs ["resources/public/css"] ;; watch and update CSS

             ;; Start an nREPL server into the running figwheel process
             ;; :nrepl-port 7888

             ;; Server Ring Handler (optional)
             ;; if you want to embed a ring handler into the figwheel http-kit
             ;; server, this is for simple ring servers, if this

             ;; doesn't work for you just run your own server :) (see lein-ring)

             ;; :ring-handler hello_world.server/handler

             ;; To be able to open files in your editor from the heads up display
             ;; you will need to put a script on your path.
             ;; that script will have to take a file path and a line number
             ;; ie. in  ~/bin/myfile-opener
             ;; #! /bin/sh
             ;; emacsclient -n +$2 $1
             ;;
             ;; :open-file-command "myfile-opener"

             ;; if you are using emacsclient you can just use
             ;; :open-file-command "emacsclient"

             ;; if you want to disable the REPL
             ;; :repl false

             ;; to configure a different figwheel logfile path
             ;; :server-logfile "tmp/logs/figwheel-logfile.log"
             }

  :repl-options {;; If nREPL takes too long to load it may timeout,
             ;; increase this to wait longer before timing out.
             ;; Defaults to 30000 (30 seconds)
                 :timeout 300000 ; 5 mins to wait
                 }

  ;; setting up nREPL for Figwheel and ClojureScript dev
  ;; Please see:
  ;; https://github.com/bhauman/lein-figwheel/wiki/Using-the-Figwheel-REPL-within-NRepl

  :uberjar-inclusions [#"^\.ebextensions"]
  :jar-inclusions [#"^\.ebextensions"]

  :aliases {"fig:dev" ["trampoline" "run" "-m" "figwheel.main" "--" "--build" "dev" "--repl"]
            "fig:watch" ["run" "-m" "figwheel.main" "--" "--build" "dev"]
            "fig:build" ["run" "-m" "figwheel.main" "--" "--build-once" "dev"]
            "fig:test" ["run" "-m" "figwheel.main" "--" "--build-once" "test"]
            "figwheel-native" ["with-profile" "native-dev" "run" "-m" "user" "--figwheel"]
            "externs" ["do" "clean"
                       ["run" "-m" "externs"]]
            "rebuild-modules" ["run" "-m" "user" "--rebuild-modules"]
            ;; --fail-level error: exit 0 on warnings, exit 1 only on errors.
            ;; trampoline re-execs in a fresh JVM so System/exit propagates
            ;; (without it, lein suppresses the exit code).
            ;; native/ is excluded — directory only exists for React Native builds.
            "lint" ["trampoline" "with-profile" "lint" "run" "-m" "clj-kondo.main" "--lint" "src" "test" "web" "--fail-level" "error"]
            "prod-build" ^{:doc "Recompile code with prod profile."}
            ["externs"
             ["with-profile" "prod" "cljsbuild" "once" "main"]]}
  :profiles {:dev          {:dependencies [[binaryage/devtools "1.0.7"]
                                           [cider/piggieback "0.5.3"]
                                           [day8.re-frame/re-frame-10x "1.11.0" :exclusions [zprint rewrite-clj]]
                                           ]
                            :env       {:dev-mode  "true"
                                        :signature "dev-secret-do-not-use-in-production"}
                            ;; need to add dev source path here to get user.clj loaded
                            :source-paths ["web/cljs" "src/clj" "src/cljc" "src/cljs" "dev"]
                            :cljsbuild    {:builds {:dev {:compiler {:closure-defines {"re_frame.trace.trace_enabled_QMARK_" true
                                                                                       goog.DEBUG                            true
                                                                                       }
                                                                     :optimizations    :none
                                                                     :pretty-print     true
                                                                     ;; To console.log CLJS data-structures make sure you enable devtools in Chrome
                                                                     ;; https://github.com/binaryage/cljs-devtools
                                                                     :preloads        [devtools.preload]}}}}
                            ;; for CIDER
                            ;; :plugins [[cider/cider-nrepl "0.12.0"]]
                            :repl-options {:init-ns          user
                                           :nrepl-middleware [cider.piggieback/wrap-cljs-repl]}}
             ;; NOTE: :native-dev was for React Native builds (legacy, may be unused)
             :native-dev   {:dependencies [[cider/piggieback "0.5.3"]]
                            :source-paths ["src/cljs" "native/cljs" "src/cljc" "env/dev"]
                            :cljsbuild    {:builds [{:id           "main"
                                                     :source-paths ["src/cljs" "native/cljs" "src/cljc" "env/dev"]
                                                     :figwheel     true
                                                     :compiler     {:output-to     "target/not-used.js"
                                                                    :main          "env.main"
                                                                    :output-dir    "target"
                                                                    :optimizations :none}}]}
                            :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}}
             ;; NOTE: :prod was for React Native builds (legacy, may be unused)
             ;; datomic-pro dependency removed - peer is already in main deps
             :prod         {:cljsbuild    {:builds [{:id           "main"
                                                     :source-paths ["src/cljs" "native/cljs" "src/cljc" "env/prod"]
                                                     :compiler     {:output-to          "main.js"
                                                                    :main               "env.main"
                                                                    :output-dir         "target"
                                                                    :static-fns         true
                                                                    :externs            ["js/externs.js"]
                                                                    :parallel-build     true
                                                                    :optimize-constants true
                                                                    :optimizations      :advanced}}]}}
             :uberjar      {:prep-tasks  ["clean" ["garden" "once"] "compile" ["cljsbuild" "once" "prod"]]
                            :env         {:production true}
                            :aot         :all
                            :omit-source true
                            :cljsbuild   {:builds
                                          {:prod
                                           {:source-paths ["web/cljs" "src/cljc" "src/cljs"]
                                            :compiler     {:main          orcpub.core
                                                           :asset-path    "/js/compiled/out"
                                                           :output-to     "resources/public/js/compiled/orcpub.js"
                                                           ;;:output-dir "resources/public/js/compiled/out"
                                                           :optimizations :advanced
                                                           :infer-externs true
                                                           :externs       ["externs.js"]
                                                           :pretty-print  false}}}}}
             ;; Docker build: CLJS is compiled separately (cljsbuild hangs in no-TTY).
             ;; uberjar-cljs    — CLJS only, no prep-tasks (run cljsbuild directly).
             ;; uberjar-package — garden + AOT + jar packaging, no cljsbuild.
             ;; See: docs/LEIN-UBERJAR-HANG.md
             :uberjar-cljs    {:prep-tasks ^:replace []}
             :uberjar-package {:prep-tasks ^:replace [["garden" "once"] "compile"]
                              ;; Wipe cljsbuild config so hooks find nothing to build.
                              ;; lein-cljsbuild hooks into compile/jar at plugin load —
                              ;; removing it from prep-tasks alone isn't enough.
                              :cljsbuild  ^:replace {}}
             ;; All lint config lives in .clj-kondo/config.edn so IDE and CLI agree.
             :lint         {:dependencies [[clj-kondo "2026.01.19"]]}
             ;; Minimal profile for init-db and dev CLI - no ClojureScript, no Garden
             ;; Use: lein with-profile init-db run -m user <command>
             ;; dev/ is included so user.clj is on the classpath.
             ;; This does NOT affect production — uberjar uses :uberjar profile only.
             :init-db      {:source-paths ["src/clj" "src/cljc" "dev"]
                            :prep-tasks   ^:replace []}
             ;; Use like: lein with-profile +start-server repl
             :start-server {:repl-options {:init-ns user
                                           :init    (start-server)}}})
