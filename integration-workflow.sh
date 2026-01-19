#!/usr/bin/env bash
# integration-workflow.sh
#
# Usage: ./integration-workflow.sh [testing-branch] [working-branch]
#
# This script automates the integration workflow:
# 1. Selects an integration branch (current or user-specified)
# 2. Optionally selects a testing branch (default: testing/develop)
# 3. Optionally selects a working branch (interactive menu if not provided)
# 4. Merges testing/develop, agents/develop, and the working branch into integration

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
STATE_FILE="${STATE_FILE:-$SCRIPT_DIR/.integration-workflow-state}"

# Persisted selections between runs
LAST_INTEGRATION_BRANCH=""
LAST_TESTING_BRANCH=""
LAST_WORKING_BRANCH=""

load_state() {
  if [[ -f "$STATE_FILE" ]]; then
    # shellcheck disable=SC1090
    source "$STATE_FILE"
  fi
}

save_state() {
  cat > "$STATE_FILE" <<EOF
LAST_INTEGRATION_BRANCH="$INTEGRATION_BRANCH"
LAST_TESTING_BRANCH="$TESTING_BRANCH"
LAST_WORKING_BRANCH="$WORKING_BRANCH"
EOF
}

branch_exists_local_or_remote() {
  local name="$1"
  if git show-ref --verify --quiet "refs/heads/$name"; then
    return 0
  fi
  if git show-ref --verify --quiet "refs/remotes/origin/$name"; then
    return 0
  fi
  if git show-ref --verify --quiet "refs/$name"; then
    return 0
  fi
  return 1
}

# Formatting helpers
BOLD="\033[1m"
RESET="\033[0m"
CYAN="\033[36m"
YELLOW="\033[33m"
GREEN="\033[32m"
SEPARATOR="${CYAN}--------------------------------------------------${RESET}"

load_state

# Get current branch
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)

# Integration branch selection
echo -e "${SEPARATOR}"
echo -e "${BOLD}Integration branch selection${RESET}"
echo -e "${SEPARATOR}"
if [[ "$CURRENT_BRANCH" =~ ^integration/ || "$CURRENT_BRANCH" =~ ^testing/ ]]; then
  echo -e "${BOLD}Current branch:${RESET} $CURRENT_BRANCH"
  read -p "Use current branch as integration branch? [Y/n]: " USE_CURR
  if [[ -z "$USE_CURR" || "$USE_CURR" =~ ^[Yy]$ ]]; then
    INTEGRATION_BRANCH="$CURRENT_BRANCH"
  else
    read -p "Enter integration branch name: " INTEGRATION_BRANCH
  fi
else
  PROMPT="Enter integration branch name"
  if [[ -n "$LAST_INTEGRATION_BRANCH" ]]; then
    PROMPT+=" [default: $LAST_INTEGRATION_BRANCH]"
  fi
  PROMPT+=": "
  read -p "$PROMPT" INTEGRATION_BRANCH
  if [[ -z "$INTEGRATION_BRANCH" && -n "$LAST_INTEGRATION_BRANCH" ]]; then
    INTEGRATION_BRANCH="$LAST_INTEGRATION_BRANCH"
  fi
  if [[ -z "$INTEGRATION_BRANCH" ]]; then
    echo "Integration branch is required."
    exit 1
  fi
fi

# Handle arguments
DEFAULT_TESTING_BRANCH="${LAST_TESTING_BRANCH:-testing/develop}"
TESTING_BRANCH="${1:-$DEFAULT_TESTING_BRANCH}"
WORKING_BRANCH="$2"

echo -e "${YELLOW}Using testing branch: $TESTING_BRANCH${RESET}"

# Working branch selection menu (only if not provided)
if [[ -z "$WORKING_BRANCH" ]]; then
  if [[ -n "$LAST_WORKING_BRANCH" ]] && branch_exists_local_or_remote "$LAST_WORKING_BRANCH"; then
    read -p "Reuse last working branch ($LAST_WORKING_BRANCH)? [Y/n]: " REUSE_LAST
    if [[ -z "$REUSE_LAST" || "$REUSE_LAST" =~ ^[Yy]$ ]]; then
      WORKING_BRANCH="$LAST_WORKING_BRANCH"
    fi
  fi

  if [[ -n "$WORKING_BRANCH" ]]; then
    echo -e "${GREEN}Selected working branch: $WORKING_BRANCH${RESET}"
  fi
fi

if [[ -z "$WORKING_BRANCH" ]]; then
  # Build branch list
  mapfile -t BRANCHES < <(git branch -a | grep -v 'HEAD' | sed 's/^..//' | grep -vE '^(main|develop|testing/develop|agents/develop|integration/|remotes/origin/HEAD)' | awk '!x[$0]++' | sed 's/^ *//;s/ *$//')

  if [[ ${#BRANCHES[@]} -eq 0 ]]; then
    echo "No working branches found. Please specify one."
    exit 1
  fi

  # Filter menu
  FILTER_PREFIX=""
  while true; do
    clear
    echo -e "${SEPARATOR}"
    echo -e "${BOLD}   Working Branch Selection${RESET}"
    echo -e "${SEPARATOR}"
    echo ""
    echo "Filter by prefix:"
    echo "   1) feature/"
    echo "   2) update/"
    echo "   3) bugfix/"
    echo "   4) temp/"
    echo "   5) claude/"
    echo "   6) Show all"
    echo "   0) Cancel"
    read -p "Select filter [default: all]: " FILTER_CHOICE
    case "$FILTER_CHOICE" in
      1) FILTER_PREFIX="feature/" ;;
      2) FILTER_PREFIX="update/" ;;
      3) FILTER_PREFIX="bugfix/" ;;
      4) FILTER_PREFIX="temp/" ;;
      5) FILTER_PREFIX="claude/" ;;
      0) echo "Cancelled by user."; exit 0 ;;
      *) FILTER_PREFIX="" ;;
    esac

    if [[ -n "$FILTER_PREFIX" ]]; then
      mapfile -t FILTERED_BRANCHES < <(printf "%s\n" "${BRANCHES[@]}" | grep -F "$FILTER_PREFIX")
    else
      FILTERED_BRANCHES=("${BRANCHES[@]}")
    fi

    if [[ ${#FILTERED_BRANCHES[@]} -eq 0 ]]; then
      echo "No branches found for that filter. Press Enter to try again."
      read
      continue
    fi
    break
  done

  BRANCHES=("${FILTERED_BRANCHES[@]}")
  TOTAL_BRANCHES=${#BRANCHES[@]}
  PAGE_SIZE=10

  # Branch selection with paging
  if (( TOTAL_BRANCHES > PAGE_SIZE )); then
    PAGE_START=0
    PAGE_END=$PAGE_SIZE
    while true; do
      clear
      echo -e "${SEPARATOR}"
      echo -e "${BOLD}   Working Branch Selection${RESET}"
      echo -e "${SEPARATOR}"
      echo ""
      echo "Available working branches:"
      for ((i=PAGE_START; i<PAGE_END && i<TOTAL_BRANCHES; i++)); do
        printf "  %2d) %s\n" $((i+1)) "${BRANCHES[$i]}"
      done
      echo "-------------------------------"
      echo "   0) Cancel"
      if (( PAGE_END < TOTAL_BRANCHES )); then
        echo "   n) Next page"
      fi
      if (( PAGE_START > 0 )); then
        echo "   p) Previous page"
      fi
      DEFAULT_BRANCH="${BRANCHES[$PAGE_START]}"
      read -p "Select working branch [default: $DEFAULT_BRANCH, 0 to cancel]: " BRANCH_NUM
      if [[ -z "$BRANCH_NUM" ]]; then
        WORKING_BRANCH="$DEFAULT_BRANCH"
        break
      elif [[ "$BRANCH_NUM" == "0" ]]; then
        echo "Cancelled by user."
        exit 0
      elif [[ "$BRANCH_NUM" == "n" && $PAGE_END -lt $TOTAL_BRANCHES ]]; then
        PAGE_START=$((PAGE_START+PAGE_SIZE))
        PAGE_END=$((PAGE_END+PAGE_SIZE))
        continue
      elif [[ "$BRANCH_NUM" == "p" && $PAGE_START -ge $PAGE_SIZE ]]; then
        PAGE_START=$((PAGE_START-PAGE_SIZE))
        PAGE_END=$((PAGE_END-PAGE_SIZE))
        continue
      else
        IDX=$((BRANCH_NUM-1))
        if [[ $IDX -ge 0 && $IDX -lt $TOTAL_BRANCHES ]]; then
          WORKING_BRANCH="${BRANCHES[$IDX]}"
          break
        else
          echo "Invalid selection. Press Enter to continue."
          read
        fi
      fi
    done
  else
    # Show all branches without paging
    echo -e "${SEPARATOR}"
    echo -e "${BOLD}   Working Branch Selection${RESET}"
    echo -e "${SEPARATOR}"
    echo ""
    echo "Available working branches:"
    for i in "${!BRANCHES[@]}"; do
      printf "  %2d) %s\n" $((i+1)) "${BRANCHES[$i]}"
    done
    echo "-------------------------------"
    echo "   0) Cancel"
    DEFAULT_BRANCH="${BRANCHES[0]}"
    read -p "Select working branch [default: $DEFAULT_BRANCH, 0 to cancel]: " BRANCH_NUM
    if [[ -z "$BRANCH_NUM" ]]; then
      WORKING_BRANCH="$DEFAULT_BRANCH"
    elif [[ "$BRANCH_NUM" == "0" ]]; then
      echo "Cancelled by user."
      exit 0
    else
      IDX=$((BRANCH_NUM-1))
      if [[ $IDX -ge 0 && $IDX -lt $TOTAL_BRANCHES ]]; then
        WORKING_BRANCH="${BRANCHES[$IDX]}"
      else
        echo "Invalid selection. Aborting."
        exit 1
      fi
    fi
  fi

  echo -e "${GREEN}Selected working branch: $WORKING_BRANCH${RESET}"

  # If remote branch selected, check it out locally
  if [[ "$WORKING_BRANCH" =~ ^remotes/origin/ ]]; then
    LOCAL_BRANCH_NAME="${WORKING_BRANCH#remotes/origin/}"
    echo "Checking out remote branch as local: $LOCAL_BRANCH_NAME"
    git checkout -B "$LOCAL_BRANCH_NAME" --track "$WORKING_BRANCH"
    WORKING_BRANCH="$LOCAL_BRANCH_NAME"
  fi
fi

# --- Begin Integration Workflow ---
echo -e "${SEPARATOR}"
echo -e "${BOLD}${YELLOW}INTEGRATION WORKFLOW STARTED${RESET}"
echo -e "${SEPARATOR}"

# Checkout integration branch (only if not already on it)
if [[ "$(git rev-parse --abbrev-ref HEAD)" != "$INTEGRATION_BRANCH" ]]; then
  echo -e "${BOLD}Switching to integration branch:${RESET} $INTEGRATION_BRANCH"
  git checkout "$INTEGRATION_BRANCH"
else
  echo -e "${BOLD}Already on integration branch:${RESET} $INTEGRATION_BRANCH"
fi

# Merge testing/develop
echo -e "${SEPARATOR}"
echo -e "${BOLD}Merging from:${RESET} ${GREEN}$TESTING_BRANCH${RESET}"
git fetch origin "$TESTING_BRANCH"
git merge origin/"$TESTING_BRANCH" || true

# Merge agents/develop (if exists)
echo -e "${SEPARATOR}"
echo -e "${BOLD}Merging from:${RESET} ${GREEN}agents/develop${RESET} (if exists)"
if git show-ref --verify --quiet refs/remotes/origin/agents/develop; then
  git fetch origin agents/develop
  git merge origin/agents/develop || true
else
  echo -e "${YELLOW}agents/develop not found on remote. Skipping.${RESET}"
fi

# Merge working branch
echo -e "${SEPARATOR}"
echo -e "${BOLD}Merging from:${RESET} ${GREEN}$WORKING_BRANCH${RESET}"
git fetch origin "$WORKING_BRANCH"
git merge origin/"$WORKING_BRANCH" || true

# Resolve known conflicts
echo -e "${SEPARATOR}"
if git ls-files -u | grep -q '.devcontainer/devcontainer.json'; then
  echo -e "${YELLOW}Resolving devcontainer conflict: keeping from testing/develop...${RESET}"
  git checkout --ours .devcontainer/devcontainer.json
  git add .devcontainer/devcontainer.json
fi

if git ls-files -u | grep -q 'AGENTS.md'; then
  echo -e "${YELLOW}Resolving AGENTS.md conflict: keeping from working branch...${RESET}"
  git checkout --theirs AGENTS.md
  git add AGENTS.md
fi

save_state

echo -e "${SEPARATOR}"
echo -e "${BOLD}${GREEN}Integration branch is up to date.${RESET}"
echo -e "${YELLOW}Resolve any remaining conflicts as needed.${RESET}"
echo -e "${SEPARATOR}"
