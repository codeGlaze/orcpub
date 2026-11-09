# Garden strips the spaces around `+` in `calc()`

**Found 2026-11-09 on `fix/pdf-export`, measured in `resources/public/css/compiled/styles.css`.**

The stylesheet build (`lein garden`, `project.clj` `:garden` with `:pretty-print? false`) compresses
the CSS. It keeps the spaces around `-` inside `calc()` but removes them around `+`:

| Written in `styles/*.clj` | Compiled |
|---|---|
| `"calc(100% - 32px)"` | `calc(100% - 32px)` (valid) |
| `"calc(100% + 4px)"` | `calc(100%+4px)` (**invalid**) |
| `"calc(10px + env(safe-area-inset-bottom))"` | `calc(10px+env(...))` (**invalid**) |

CSS requires whitespace around `+` and `-` in `calc()`, so the browser drops the whole declaration
and the property falls back to its initial value (`auto` for `top`/`bottom`, `0` for padding).
Nothing warns: the build succeeds and the rule is simply ignored.

**How to write it instead:** subtract a negative (`calc(100% - -4px)`, which the codebase already
uses once), or use `max()`/`min()` (`max(10px, env(safe-area-inset-bottom, 0px))`), which need no
operator spaces. Give `env()` a fallback: without one, a browser that does not define the variable
invalidates the expression the same way.

**Still broken on `integration` (2026-11-09):** `styles/portrait.clj:837` and `styles/core.clj`
2562 and 3640, each a popover offset (`calc(100% + 4px)` / `calc(100% + 9px)`). Check with
`grep -o "calc([^)]*)" resources/public/css/compiled/styles.css | grep "+"` after `lein garden once`.
