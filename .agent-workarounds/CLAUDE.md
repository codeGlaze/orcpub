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
