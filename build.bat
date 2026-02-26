rem Used to build the windows instance of DMV

CALL lein clean
rem # Step 1: Compile CLJS
CALL lein fig:prod
rem # Step 2: AOT compile (with uberjar-package to skip clean)
CALL lein with-profile uberjar,uberjar-package compile
rem # Step 3: Package jar (uberjar-package prevents re-cleaning)
CALL lein with-profile uberjar,uberjar-package uberjar