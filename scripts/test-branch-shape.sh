#!/usr/bin/env bash
# Proves the branch-shape hooks block and allow what they claim, in a throwaway repo.
set -u
hooks="$(cd "$(dirname "$0")/../.githooks" && pwd)"
t="$(mktemp -d)"; trap 'rm -rf "$t"' EXIT
pass=0; bad=0
check() { # name, expected exit (0 or 1), command...
  local name="$1" want="$2"; shift 2
  "$@" >"$t/out" 2>&1; local got=$?; [ "$got" -ne 0 ] && got=1
  if [ "$got" = "$want" ]; then pass=$((pass+1)); else bad=$((bad+1)); echo "FAIL $name: wanted $want, got $got"; sed 's/^/   /' "$t/out"; fi
}
many() { for i in $(seq "$2" "$3"); do echo x >"$1$i.clj"; done; git add -A; git commit -qm "files $2-$3"; }
git init -q --bare "$t/o.git"; git clone -q "$t/o.git" "$t/w" 2>/dev/null; cd "$t/w" || exit 1
git config user.email t@t; git config user.name t; git config commit.gpgsign false
git config core.hooksPath "$t/h"; mkdir "$t/h"; cp "$hooks"/{pre-commit,pre-push,branch-shape} "$t/h/"; chmod +x "$t"/h/*
git checkout -qb integration; echo a >a; git add a; git commit -qm a; git push -q origin integration
git checkout -qb feature/x
mkdir -p docs/design docs/issues dev src resources/public test
for f in docs/design/round3.html docs/issues/plan.md docs/report.txt dev/scan_numerals.clj dev/svg_proof.clj server.log x/design_handoff/a.md mock.html; do
  mkdir -p "$(dirname "$f")"; echo x >"$f"; git add "$f"
  check "stray $f refused" 1 git commit -qm s
  git rm -qf --cached "$f"; rm -f "$f"
done
for f in src/ok.clj docs/PAGE.md dev/inspect_export.clj resources/public/p.html test/p.html; do
  echo x >"$f"; git add "$f"; check "normal $f passes" 0 git commit -qm "ok $f"
done
echo x >docs/design/a.md; git add docs/design/a.md; check "allow flag" 0 env ALLOW_STRAY_FILES=1 git commit -qm allowed
git rm -q docs/design/a.md; git commit -qm gone
check "small push" 0 git push -q origin feature/x
many src/f 1 90
check "under 100 pushes" 0 git push -q origin feature/x
many src/g 1 10
check "crossing 100 refused" 1 git push -q origin feature/x
check "crossing with flag" 0 env ALLOW_LARGE_BRANCH=1 git push -q origin feature/x
echo y >>a; git add a; git commit -qm more
check "already over: warned, not blocked" 0 git push -q origin feature/x
git checkout -q integration; echo b >b; git add b; git commit -qm b; git push -q origin integration
git checkout -qb feature/y; echo y >y.clj; git add y.clj; git commit -qm y; git push -q origin feature/y
git checkout -q integration; mkdir -p src; many src/big 1 150; git push -q origin integration
git checkout -q feature/y; git merge -q --no-edit origin/integration
check "merging integration in adds no count" 0 git push -q origin feature/y
git checkout -qb dmv-work feature/x; git init -q --bare "$t/g.git"; git remote add gitea "$t/g.git"
check "a push to gitea is not counted" 0 git push -q gitea dmv-work
git checkout -q feature/y; mkdir -p src; for i in $(seq 1 85); do echo x >"src/w$i.clj"; done; git add -A
git commit -qm w 2>"$t/warn"; grep -q 'heads-up: this branch changes 8[0-9]' "$t/warn" && pass=$((pass+1)) || { bad=$((bad+1)); echo "FAIL no warning past 80"; }
git checkout -q -b agents/z; mkdir -p docs/design; echo x >docs/design/r.html; git add -A
check "agents branch free" 0 git commit -qm kb
echo "branch-shape: $pass passed, $bad failed"
[ "$bad" -eq 0 ]
