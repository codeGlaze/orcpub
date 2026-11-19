# DMV production changes

Moved to the private DMV repository (Gitea, branch `dmv`, from the pre-merge PR):
`docs/ops/dmv-production-changes.md`. It holds the analysis of the live DMV deployment, including its known security issues, which do not belong in a public repo.

Agents: read it in the pre-merge worktree (`git worktree list | grep -F '[premerge/integration-to-dmv]'`),
and write DMV server details there, never here.
