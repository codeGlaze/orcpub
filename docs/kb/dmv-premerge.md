# Pre-merging integration into DMV

Moved to the private DMV repository (Gitea, branch `dmv`, from the pre-merge PR):
`docs/ops/dmv-premerge.md`. It holds the DMV repository's address, access and history of pre-merges, which do not belong in a public repo.

Agents: read it in the pre-merge worktree (`git worktree list | grep -F '[premerge/integration-to-dmv]'`),
and write DMV server details there, never here.
