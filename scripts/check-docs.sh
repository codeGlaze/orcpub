#!/usr/bin/env bash
# Docs organization check for agents/develop. Enforces docs/DOC-CONVENTIONS.md:
#   1. No dangling relative *.md links in docs/ (targets resolve to a real file).
#   2. No orphaned KB docs — every docs/kb/*.md is referenced from at least one
#      other tracked doc (any index: docs/kb/README.md OR docs/README.md, etc.).
#   3. Superset invariant — every doc under docs/ that exists on origin/develop
#      (the released main flow) also exists here. Reads origin/develop via
#      git ls-tree without checking it out; only as fresh as your last fetch.
#
# Runs within one checkout; committed only to agents/develop. Exit 1 on any issue.
root="$(git rev-parse --show-toplevel)"; cd "$root"
status=0
mapfile -t DOCS < <(git ls-files 'docs/**/*.md' 'docs/*.md')

echo "== 1. dangling relative markdown links =="
for f in "${DOCS[@]}"; do
  dir="$(dirname "$f")"
  grep -oE '\]\(([^)#]+\.md)(#[^)]*)?\)' "$f" 2>/dev/null \
    | sed -E 's/\]\(([^)#]+\.md).*/\1/' | while IFS= read -r target; do
        case "$target" in http*|/*) continue;; esac
        [ -f "$dir/$target" ] || echo "  DANGLING  $f -> $target"
      done
done | tee /tmp/_ck_dl; [ -s /tmp/_ck_dl ] && status=1; rm -f /tmp/_ck_dl

echo "== 2. orphaned KB docs (not referenced from any other doc) =="
for f in $(git ls-files 'docs/kb/*.md'); do
  base="$(basename "$f")"
  [ "$base" = "README.md" ] && continue
  # referenced from any OTHER tracked doc by basename?
  if ! grep -rlF "$base" "${DOCS[@]}" 2>/dev/null | grep -qv "^$f$"; then
    echo "  ORPHAN    $f (linked from no other doc)"; status=1
  fi
done

echo "== 3. superset: docs on origin/develop missing here =="
if git rev-parse --verify -q origin/develop >/dev/null; then
  comm -23 \
    <(git ls-tree -r --name-only origin/develop -- docs/ | sort) \
    <(printf '%s\n' "${DOCS[@]}" | sort) \
  | while IFS= read -r missing; do
      [ -n "$missing" ] && echo "  MISSING   $missing (on origin/develop, not here)"
    done | tee /tmp/_ck_ss; [ -s /tmp/_ck_ss ] && status=1; rm -f /tmp/_ck_ss
else
  echo "  (origin/develop not fetched — skipping superset check)"
fi

echo "== result: $([ $status -eq 0 ] && echo CLEAN || echo ISSUES) =="
exit $status
