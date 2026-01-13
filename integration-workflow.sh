#!/usr/bin/env bash
# integration-workflow.sh


# Usage: ./integration-workflow.sh integration/my-feature [testing/develop] [feature/my-working-branch]

INTEGRATION_BRANCH="$1"
TESTING_BRANCH="$2"
WORKING_BRANCH="$3"

if [[ -z "$INTEGRATION_BRANCH" ]]; then
  echo "Usage: $0 integration/my-feature [testing/develop] [feature/my-working-branch]"
  exit 1
fi

# Default TESTING_BRANCH to testing/develop if not set
if [[ -z "$TESTING_BRANCH" ]]; then
  TESTING_BRANCH="testing/develop"
  echo "Defaulting testing branch to: $TESTING_BRANCH"
fi

# If working branch not provided, prompt user to select (default: most recent)
if [[ -z "$WORKING_BRANCH" ]]; then
  BRANCHES=($(git for-each-ref --sort=-committerdate refs/heads/ --format='%(refname:short)' | grep -vE '^(main|develop|testing/develop|agents/develop|integration/)'))
  if [[ ${#BRANCHES[@]} -eq 0 ]]; then
    echo "No working branch found. Please specify one."
  echo "Available working branches:"
  for i in "${!BRANCHES[@]}"; do
  done
  DEFAULT_BRANCH="${BRANCHES[0]}"
  read -p "Select working branch [default: $DEFAULT_BRANCH]: " BRANCH_NUM
    WORKING_BRANCH="$DEFAULT_BRANCH"
  else
    IDX=$((BRANCH_NUM-1))

    set -e

    if [[ $IDX -ge 0 && $IDX -lt ${#BRANCHES[@]} ]]; then
      WORKING_BRANCH="${BRANCHES[$IDX]}"
    else

    # Always merge in develop, agents/develop, testing/develop, and the working branch
    for BR in develop agents/develop "$TESTING_BRANCH" "$WORKING_BRANCH"; do
      if git show-ref --verify --quiet refs/remotes/origin/$BR; then
        echo "Merging latest from $BR..."
        git merge origin/$BR || {
          echo "Merge conflict with $BR. Please resolve manually.";
          exit 1;
        }
      else
        echo "Branch $BR not found on origin, skipping."
      fi
    done
      echo "Invalid selection. Aborting."
      exit 1
    fi
  fi
  echo "Selected working branch: $WORKING_BRANCH"
fi

set -e

echo "Checking out integration branch: $INTEGRATION_BRANCH"
git checkout "$INTEGRATION_BRANCH"

echo "Merging latest from $TESTING_BRANCH (favor devcontainer from testing/develop)..."
git fetch origin "$TESTING_BRANCH"
git merge origin/"$TESTING_BRANCH"

echo "Merging latest from $WORKING_BRANCH (favor agent docs from working branch)..."
git fetch origin "$WORKING_BRANCH"
git merge origin/"$WORKING_BRANCH"

# Example: Always keep devcontainer from testing/develop
if git ls-files -u | grep -q '.devcontainer/devcontainer.json'; then
  git checkout --ours .devcontainer/devcontainer.json
  git add .devcontainer/devcontainer.json
fi

# Example: Always keep AGENTS.md from working branch
if git ls-files -u | grep -q 'AGENTS.md'; then
  git checkout --theirs AGENTS.md
  git add AGENTS.md
fi

echo "Integration branch is up to date. Resolve any remaining conflicts as needed."
