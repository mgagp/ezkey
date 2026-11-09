#!/usr/bin/env bash
# Reminds the maintainer about reports that still wait for a first response.
#
# Promise (SECURITY.md, CONTRIBUTING.md): a first response within 14 days.
# An issue needs a response when it is open, was opened by someone without
# write access, is not from a bot, and has no comment yet from an owner,
# member or collaborator. Closing an issue counts as a response.
#
# From REMIND_DAYS on, the issue is listed in one tracking issue that mentions
# the maintainer. From ALERT_DAYS on, the mention becomes an alert. When
# nothing is waiting, the tracking issue is closed.
#
# Usage: GH_REPO=owner/repo MAINTAINER=login ./scripts/issue-response-reminder.sh [--dry-run]
# Needs `gh` and `jq`, and a token that can read and write issues.
set -euo pipefail

REPO="${GH_REPO:?set GH_REPO=owner/repo}"
MAINTAINER="${MAINTAINER:?set MAINTAINER=github-login}"
REMIND_DAYS="${REMIND_DAYS:-7}"
ALERT_DAYS="${ALERT_DAYS:-12}"
PROMISE_DAYS="${PROMISE_DAYS:-14}"
TRACKING_TITLE="Reports awaiting a first response"
TRACKING_LABEL="hygiene"
DRY_RUN=false
[ "${1:-}" = "--dry-run" ] && DRY_RUN=true

now=$(date -u +%s)
# Override only to test the detection, for example MAINTAINER_ROLES='["NONE"]' with --dry-run.
maintainer_roles="${MAINTAINER_ROLES:-[\"OWNER\",\"MEMBER\",\"COLLABORATOR\"]}"

# Open issues (pull requests excluded) from people without write access, not bots,
# and not the tracking issue itself.
candidates=$(gh api --paginate "repos/$REPO/issues?state=open&per_page=100" --jq \
  ".[] | select(.pull_request == null)
       | select(.title != \"$TRACKING_TITLE\")
       | select(.user.type != \"Bot\" and (.user.login | endswith(\"[bot]\") | not))
       | select(.author_association as \$a | $maintainer_roles | index(\$a) | not)
       | [.number, .created_at, .comments] | @tsv")

waiting=""
alert=false
while IFS=$'\t' read -r number created comments; do
  [ -z "${number:-}" ] && continue
  answered=false
  if [ "$comments" -gt 0 ]; then
    hits=$(gh api --paginate "repos/$REPO/issues/$number/comments?per_page=100" --jq \
      "[.[] | select(.author_association as \$a | $maintainer_roles | index(\$a))] | length" \
      | awk '{s+=$1} END {print s+0}')
    [ "$hits" -gt 0 ] && answered=true
  fi
  $answered && continue
  age=$(( (now - $(date -u -d "$created" +%s)) / 86400 ))
  [ "$age" -lt "$REMIND_DAYS" ] && continue
  [ "$age" -ge "$ALERT_DAYS" ] && alert=true
  waiting+="- [ ] #$number — open for $age days"$'\n'
done <<< "$candidates"

tracking=$(gh issue list --repo "$REPO" --state open --search "\"$TRACKING_TITLE\" in:title" \
  --json number,title --jq ".[] | select(.title == \"$TRACKING_TITLE\") | .number" | head -n 1)

if [ -z "$waiting" ]; then
  echo "No report waits for a first response."
  if [ -n "$tracking" ]; then
    echo "Closing tracking issue #$tracking."
    $DRY_RUN || gh issue close "$tracking" --repo "$REPO" \
      --comment "Every report now has a first response. Closing."
  fi
  exit 0
fi

# The list changes only when an issue is added, removed or crosses a day, so the
# signature drops the day counts: a new comment is posted only when the set changes
# or once a day during an alert.
signature=$(printf '%s' "$waiting" | sed -E 's/ — open for [0-9]+ days//' | sha256sum | cut -c1-16)
body="Issues opened by people outside the project that still have no maintainer response.

The promise in \`SECURITY.md\` and \`CONTRIBUTING.md\` is a first response within $PROMISE_DAYS days. A short comment is enough; closing the issue also counts.

$waiting
<!-- reminder-signature: $signature -->
_Updated by \`.github/workflows/issue-response-reminder.yml\`._"

if $alert; then
  mention="@$MAINTAINER **Alert:** at least one report is $ALERT_DAYS days old or more. The promise is $PROMISE_DAYS days."
else
  mention="@$MAINTAINER Reports are waiting for a first response."
fi

echo "Waiting reports:"
printf '%s' "$waiting"
echo "Alert: $alert"

if $DRY_RUN; then
  echo "[dry-run] Would ${tracking:+update #$tracking}${tracking:-create the tracking issue} and post: $mention"
  exit 0
fi

if [ -z "$tracking" ]; then
  gh issue create --repo "$REPO" --title "$TRACKING_TITLE" --label "$TRACKING_LABEL" \
    --body "$body" > /dev/null
  tracking=$(gh issue list --repo "$REPO" --state open --search "\"$TRACKING_TITLE\" in:title" \
    --json number,title --jq ".[] | select(.title == \"$TRACKING_TITLE\") | .number" | head -n 1)
  gh issue comment "$tracking" --repo "$REPO" --body "$mention"
  exit 0
fi

previous=$(gh issue view "$tracking" --repo "$REPO" --json body --jq .body \
  | sed -n -E 's/.*reminder-signature: ([0-9a-f]+).*/\1/p')
gh issue edit "$tracking" --repo "$REPO" --body "$body" > /dev/null
if [ "$previous" != "$signature" ] || $alert; then
  gh issue comment "$tracking" --repo "$REPO" --body "$mention"
fi
