# .agent-workarounds/

**This directory exists only on `agents/*` branches.**
If you see it on any other branch, delete it:

```bash
git rm -r .agent-workarounds/
git commit -m "Remove agent-only workarounds"
```

## maven-proxy/

If Maven or Gradle fails with **401 Unauthorized** or proxy authentication
errors, run the setup script to start a local auth-injection proxy:

```bash
bash .agent-workarounds/maven-proxy/setup-maven-proxy.sh
```

This configures `~/.m2/settings.xml` (Maven) and `~/.gradle/gradle.properties`
(Gradle) to route through `127.0.0.1:3128`, where a local Python proxy injects
the `Proxy-Authorization` header for the upstream egress proxy.

Only needed in Claude Code Web environments where `https_proxy` is set.

**Upstream issue:**
https://github.com/anthropics/claude-code/issues/13372#issuecomment-3685454645

## clojars-deps/

Most Clojure libraries are hosted on **Clojars** (`repo.clojars.org`), which is
blocked by the Claude Code Web egress proxy. This script installs test
dependencies without Clojars access:

```bash
# First: start the maven proxy (if not already running)
bash .agent-workarounds/maven-proxy/setup-maven-proxy.sh

# Then: install Clojure test dependencies
bash .agent-workarounds/clojars-deps/install-test-deps.sh
```

**What it does:**

1. Installs `lein` via apt (if the pre-installed version can't self-bootstrap)
2. Creates `profiles.clj` to disable Lein plugins, Garden prep-tasks, and
   exclude `dev/user.clj` (which requires Datomic, Figwheel, etc.)
3. Creates **stub JARs** for Clojars-only deps that aren't loaded during JVM
   tests (satisfies Maven dependency resolution without real code)
4. Downloads **real source** from GitHub for libraries that ARE loaded during
   JVM test compilation (re-frame, reagent, macrovich)
5. Adds Maven Central transitive deps (tools.logging) via `profiles.clj`

**Domain requirements:**

| Domain | Purpose | Status |
|--------|---------|--------|
| `repo1.maven.org` | Maven Central downloads | Works |
| `api.github.com` | GitHub API for tarball URLs | Works |
| `codeload.github.com` | GitHub archive downloads | Works |
| `repo.clojars.org` | Clojars (primary Clojure repo) | Blocked |
| `release-assets.githubusercontent.com` | GitHub release assets | Blocked |

**After setup, run tests with:**
```bash
/usr/bin/lein test                                  # all tests
/usr/bin/lein test :only orcpub.dnd.e5.ac-test      # AC stacking tests
```

**Troubleshooting:**

If tests fail with `Could not locate <namespace>` errors, a library that was
previously a stub now needs real source. The pattern is:

1. Find the library's GitHub repo
2. Download the tarball: `curl -sL -o /tmp/lib.tar.gz "https://api.github.com/repos/OWNER/REPO/tarball/TAG"`
3. Extract and build JAR: `cd /tmp && tar xzf lib.tar.gz && cd OWNER-REPO-*/src && jar cf ~/.m2/repository/GROUP/ARTIFACT/VERSION/ARTIFACT-VERSION.jar .`
4. Add the dependency to `install-test-deps.sh` in the `install_real_deps()` function
