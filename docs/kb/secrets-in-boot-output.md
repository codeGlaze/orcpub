# Credentials leak through logs, and through ex-info

A production boot log was printing the database password. The line looked innocuous:

```clojure
(println "Creating/connecting to Datomic database:" uri)
```

A `datomic:sql` URI carries the password in a query parameter:

```
datomic:sql://datomic?jdbc:postgresql://host:5432/datomic?user=datomic&password=hunter2
```

So every start wrote the production credential into terminal scrollback, container logs, any
aggregator, and eventually a screenshot pasted into a chat — which is how it was found.

## It was in four places, not one

Grepping for the `println` finds one site. The URI was also in three `ex-info` maps:

```clojure
(throw (ex-info "Failed to connect to Datomic database..."
                {:error :db-connection-failed
                 :uri uri}          ; <- goes wherever the exception goes
                e))
```

**Exception data is log output.** It reaches the same places a `println` does, usually with
more of an audience: error reporters, alerting, crash dumps. When hunting for a leak, search
for the *value*, not for the logging call.

## Redact at the boundary, and only what is logged

`orcpub.config/redact-secrets` handles both shapes a credential arrives in — a
`password`/`passwd`/`pwd`/`secret`/`token`/`api-key` query parameter, and
`scheme://user:pass@host` userinfo — and leaves everything else alone, so host, port,
database and user still show and the line stays useful.

**The near-miss worth remembering:** the first pass applied it with a blanket
find-and-replace, which also caught the *constructor*:

```clojure
(defn new-datomic [uri]
  (map->DatomicComponent {:uri (config/redact-secrets uri)}))   ; every connection fails
```

That is the value handed to `d/connect`. Redaction belongs on the path to the log, never on
the path to the thing that uses it. A blanket replace over a token as common as `:uri uri`
cannot tell those apart — read every hit.

## The next leak goes in the startup banner

Boot output is where connection details naturally accumulate, so it is where the next
credential will appear. Two rules:

1. Anything that might carry one goes through `redact-secrets` first.
2. Redacting is not a licence to log more. Redact what must be shown; do not print a secret
   because it will be masked.

## Reporting configuration without lying about it

The same hotfix added a boot banner, and its first two designs were both wrong in ways worth
keeping:

- **`set` for a rejected value.** A variable that is present but unparseable falls back to
  the default. Labelling that row `set` answers "did my change take effect?" with exactly the
  wrong word — the number displayed is the default, not theirs.
- **`IGNORED` in the source column.** Correct about the supplied value, but sitting next to
  the default it reads as *the default was ignored*. Backwards.

What works: the source column always describes where the **shown value** came from, so it has
two states, `SET` and `DEFAULT`. A rejected setting reads `DEFAULT`, and a separate `(!)` line
below the table names what was thrown away.

Both errors were caught by *rendering it and reading it*, not by reasoning about the design.
Print the thing and look at it.

## Also

- Plain ASCII, no colour. An em dash in the first draft came back as `?` through a pipe, and
  escape codes are noise in an aggregator. Alignment does the work; a test asserts every
  character is 7-bit.
- Print after the components start, so `started` can be trusted.
- One list drives both the banner and a test that every documented variable appears in it, so
  a knob added without a banner entry fails.
- **A credential ever logged unredacted must be rotated.** The fix stops new exposure; it does
  nothing about logs already written.
