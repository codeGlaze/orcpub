# Branch handoffs

One file per code branch, at `branches/<branch name>.md` (`branches/feature/character-rescue.md`),
kept here so it never reaches a PR. `agent-setup.sh` inlines the current branch's file into the
generated `CLAUDE.md`, after the project-wide `BRANCH.md`, so every agent on that branch reads both
at session start. Project-wide state and rules go in `BRANCH.md`, not here.

- **Start one** from `TEMPLATE.md` when the branch has state the next agent needs: work in
  progress, a decision the owner made, a trap. A one-session fix needs none.
- **Keep it current and short:** it is read every session. Update it at each milestone; move
  finished history to the branch's KB page and link it. Aim for under 80 lines.
- **Delete it** when the branch merges or is abandoned.
- **A code branch never tracks its own `BRANCH.md` or `.claude/`:** the instructions come in from
  agents/develop. A branch cut before this folder existed may still track them; copy its
  `BRANCH.md` here as its handoff, then remove both from that branch in a commit there (with the
  owner's yes: it is a push to that branch). Until then `agent-setup.sh` points to its `BRANCH.md`.
