#!/usr/bin/env bash
# Locks scripts/issue-response-reminder.sh: who counts as unanswered, the day-7
# remind and day-12 alert, the signature that avoids a daily comment, and
# dry-run writing nothing.
#
# No GitHub. A fake gh serves fixtures and records every write.
#
# Run from anywhere:
#   ./scripts/issue-response-reminder.test.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SCRIPT="${ROOT}/scripts/issue-response-reminder.sh"
command -v jq >/dev/null
command -v date >/dev/null

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

STATE="${TMP}/state"
BIN="${TMP}/bin"
mkdir -p "$STATE/comments" "$BIN"

cat >"${BIN}/gh" <<'EOF'
#!/usr/bin/env bash
# Stand-in for gh. Reads fixtures from FAKE_GH_STATE and appends one command
# name per invocation to FAKE_GH_CMDS. Writes go to files in the state dir.
set -euo pipefail

state="${FAKE_GH_STATE:?}"
cmds="${FAKE_GH_CMDS:?}"
sub="${1:-}"
shift || true

jq_filter=""
url=""
body=""
number=""

take_flags() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --jq)
        jq_filter="${2:-}"
        shift 2
        ;;
      --body)
        body="${2:-}"
        shift 2
        ;;
      --repo|--state|--search|--label|--title|--comment|--json)
        shift 2
        ;;
      --paginate)
        shift
        ;;
      *)
        if [[ -z "$url" && "$1" == repos/* ]]; then
          url="$1"
        elif [[ -z "$number" && "$1" =~ ^[0-9]+$ ]]; then
          number="$1"
        fi
        shift
        ;;
    esac
  done
}

case "$sub" in
  api)
    take_flags "$@"
    printf 'api %s\n' "$url" >>"$cmds"
    if [[ "$url" =~ /issues/([0-9]+)/comments ]]; then
      file="${state}/comments/${BASH_REMATCH[1]}.json"
      if [[ -f "$file" ]]; then
        jq -r "$jq_filter" "$file"
      else
        echo '[]' | jq -r "$jq_filter"
      fi
    elif [[ "$url" == *"/issues?"* ]]; then
      jq -r "$jq_filter" "${state}/issues.json"
    else
      echo "fake gh: unexpected api url: $url" >&2
      exit 1
    fi
    ;;
  issue)
    action="${1:-}"
    shift || true
    take_flags "$@"
    printf 'issue-%s %s\n' "$action" "$number" >>"$cmds"
    case "$action" in
      list)
        if [[ -f "${state}/tracking-number" ]]; then
          jq -n --argjson n "$(cat "${state}/tracking-number")" \
            '[{number:$n, title:"Reports awaiting a first response"}]' \
            | jq -r "$jq_filter"
        else
          echo '[]' | jq -r "$jq_filter"
        fi
        ;;
      create)
        printf '%s' "$body" >"${state}/tracking-body"
        echo 900 >"${state}/tracking-number"
        printf 'https://example.test/issues/900\n'
        ;;
      view)
        jq -n --rawfile b "${state}/tracking-body" '{body:$b}' | jq -r "$jq_filter"
        ;;
      edit)
        printf '%s' "$body" >"${state}/tracking-body"
        ;;
      comment)
        printf '%s\n' "$body" >"${state}/last-comment"
        ;;
      close)
        ;;
      *)
        echo "fake gh: unexpected issue action: $action" >&2
        exit 1
        ;;
    esac
    ;;
  *)
    echo "fake gh: unexpected command: $sub" >&2
    exit 1
    ;;
esac
EOF
chmod +x "${BIN}/gh"

fail=0
pass=0

assert_eq() {
  local name="$1" expected="$2" actual="$3"
  if [[ "$expected" == "$actual" ]]; then
    echo "PASS: ${name}"
    pass=$((pass + 1))
  else
    echo "FAIL: ${name}" >&2
    echo "  expected: ${expected}" >&2
    echo "  actual:   ${actual}" >&2
    fail=$((fail + 1))
  fi
}

assert_file_lacks() {
  local name="$1" file="$2" pattern="$3"
  if grep -Eq "$pattern" "$file"; then
    echo "FAIL: ${name}" >&2
    echo "  found /${pattern}/ in ${file}:" >&2
    grep -E "$pattern" "$file" >&2 || true
    fail=$((fail + 1))
  else
    echo "PASS: ${name}"
    pass=$((pass + 1))
  fi
}

days_ago() {
  date -u -d "$1 days ago" +%Y-%m-%dT%H:%M:%SZ
}

reset_state() {
  rm -rf "$STATE"
  mkdir -p "$STATE/comments"
  : >"${TMP}/cmds"
  : >"${TMP}/out"
}

write_issues() {
  printf '%s\n' "$1" >"${STATE}/issues.json"
}

run_reminder() {
  local mode="${1:-}"
  env -u GH_TOKEN \
    GH_REPO=acme/ezkey \
    MAINTAINER=mgagp \
    FAKE_GH_STATE="$STATE" \
    FAKE_GH_CMDS="${TMP}/cmds" \
    PATH="${BIN}:${PATH}" \
    "$SCRIPT" $mode >"${TMP}/out" 2>"${TMP}/err" || {
      echo "FAIL: reminder exited $?" >&2
      cat "${TMP}/out" >&2 || true
      cat "${TMP}/err" >&2 || true
      fail=$((fail + 1))
      return 1
    }
}

waiting_numbers() {
  grep -oE '#[0-9]+' "${TMP}/out" | sed 's/#//' | tr '\n' ' ' | sed 's/ $//'
}

# --- who is waiting, and when the alert starts --------------------------------

reset_state
young="$(days_ago 6)"
remind="$(days_ago 8)"
just_under_alert="$(days_ago 11)"
alert="$(days_ago 13)"
week="$(days_ago 7)"
jq -n \
  --arg young "$young" --arg remind "$remind" --arg under "$just_under_alert" \
  --arg alert "$alert" --arg week "$week" \
  '[
    {number:101, title:"external", pull_request:null,
      user:{type:"User", login:"alice"}, author_association:"NONE",
      created_at:$remind, comments:0},
    {number:102, title:"owner", pull_request:null,
      user:{type:"User", login:"mgagp"}, author_association:"OWNER",
      created_at:$alert, comments:0},
    {number:103, title:"member", pull_request:null,
      user:{type:"User", login:"sam"}, author_association:"MEMBER",
      created_at:$alert, comments:0},
    {number:104, title:"collaborator", pull_request:null,
      user:{type:"User", login:"pat"}, author_association:"COLLABORATOR",
      created_at:$alert, comments:0},
    {number:105, title:"bot", pull_request:null,
      user:{type:"Bot", login:"dependabot[bot]"}, author_association:"NONE",
      created_at:$alert, comments:0},
    {number:106, title:"bot login", pull_request:null,
      user:{type:"User", login:"helper[bot]"}, author_association:"NONE",
      created_at:$alert, comments:0},
    {number:107, title:"pull request", pull_request:{},
      user:{type:"User", login:"bob"}, author_association:"NONE",
      created_at:$alert, comments:0},
    {number:108, title:"Reports awaiting a first response", pull_request:null,
      user:{type:"User", login:"mgagp"}, author_association:"OWNER",
      created_at:$alert, comments:0},
    {number:109, title:"too young", pull_request:null,
      user:{type:"User", login:"new"}, author_association:"NONE",
      created_at:$young, comments:0},
    {number:110, title:"contributor answered", pull_request:null,
      user:{type:"User", login:"contrib"}, author_association:"CONTRIBUTOR",
      created_at:$alert, comments:1},
    {number:111, title:"outsider comments only", pull_request:null,
      user:{type:"User", login:"carol"}, author_association:"NONE",
      created_at:$under, comments:2},
    {number:112, title:"old enough to alert", pull_request:null,
      user:{type:"User", login:"dave"}, author_association:"NONE",
      created_at:$alert, comments:0},
    {number:113, title:"exactly seven days", pull_request:null,
      user:{type:"User", login:"erin"}, author_association:"NONE",
      created_at:$week, comments:0}
  ]' >"${STATE}/issues.json"

jq -n '[{author_association:"OWNER", user:{login:"mgagp"}}]' \
  >"${STATE}/comments/110.json"
jq -n '[{author_association:"NONE", user:{login:"carol"}},
        {author_association:"CONTRIBUTOR", user:{login:"contrib"}}]' \
  >"${STATE}/comments/111.json"

run_reminder --dry-run
assert_eq "waiting issue numbers" "101 111 112 113" "$(waiting_numbers)"
assert_eq "alert when one report is 12 days or older" "yes" \
  "$(grep -F 'Alert: true' "${TMP}/out" >/dev/null && echo yes || echo no)"
assert_file_lacks "dry-run does not write" "${TMP}/cmds" \
  '^issue-(create|edit|comment|close) '
if grep -F 'issue-create' "${TMP}/out" >/dev/null || grep -F '[dry-run] Would create' "${TMP}/out" >/dev/null; then
  echo "PASS: dry-run says it would create the tracking issue"
  pass=$((pass + 1))
else
  echo "FAIL: dry-run did not describe creating the tracking issue" >&2
  cat "${TMP}/out" >&2
  fail=$((fail + 1))
fi

# Day 11 is listed above (111) and must not be the reason for the alert.
# A queue of only that issue stays a reminder.
reset_state
jq -n --arg created "$(days_ago 11)" \
  '[{number:201, title:"eleven days", pull_request:null,
     user:{type:"User", login:"carol"}, author_association:"NONE",
     created_at:$created, comments:0}]' >"${STATE}/issues.json"
run_reminder --dry-run
assert_eq "eleven days is a reminder, not an alert" "yes" \
  "$(grep -F 'Alert: false' "${TMP}/out" >/dev/null && grep -F '#201' "${TMP}/out" >/dev/null && echo yes || echo no)"

reset_state
jq -n --arg created "$(days_ago 12)" \
  '[{number:202, title:"twelve days", pull_request:null,
     user:{type:"User", login:"dave"}, author_association:"NONE",
     created_at:$created, comments:0}]' >"${STATE}/issues.json"
run_reminder --dry-run
assert_eq "twelve days is an alert" "yes" \
  "$(grep -F 'Alert: true' "${TMP}/out" >/dev/null && echo yes || echo no)"

# --- nothing waiting closes the tracking issue, except on dry-run -------------

reset_state
jq -n --arg created "$(days_ago 1)" \
  '[{number:301, title:"fresh", pull_request:null,
     user:{type:"User", login:"new"}, author_association:"NONE",
     created_at:$created, comments:0}]' >"${STATE}/issues.json"
echo 900 >"${STATE}/tracking-number"
printf 'old body\n<!-- reminder-signature: aaaa -->\n' >"${STATE}/tracking-body"
run_reminder --dry-run
assert_eq "dry-run mentions closing" "yes" \
  "$(grep -F 'Closing tracking issue #900' "${TMP}/out" >/dev/null && echo yes || echo no)"
assert_file_lacks "dry-run does not close" "${TMP}/cmds" '^issue-close '

reset_state
jq -n --arg created "$(days_ago 1)" \
  '[{number:301, title:"fresh", pull_request:null,
     user:{type:"User", login:"new"}, author_association:"NONE",
     created_at:$created, comments:0}]' >"${STATE}/issues.json"
echo 900 >"${STATE}/tracking-number"
printf 'old body\n<!-- reminder-signature: aaaa -->\n' >"${STATE}/tracking-body"
run_reminder
assert_eq "live run closes the tracking issue" "yes" \
  "$(grep -F 'issue-close 900' "${TMP}/cmds" >/dev/null && echo yes || echo no)"
assert_file_lacks "close path does not open a new issue" "${TMP}/cmds" '^issue-create '

# --- signature ignores the day count; an alert still comments -----------------

reset_state
jq -n --arg created "$(days_ago 8)" \
  '[{number:401, title:"waiting", pull_request:null,
     user:{type:"User", login:"alice"}, author_association:"NONE",
     created_at:$created, comments:0}]' >"${STATE}/issues.json"
run_reminder
assert_eq "first run creates and comments" "1" \
  "$(grep -c '^issue-comment ' "${TMP}/cmds")"
signature_1="$(sed -n -E 's/.*reminder-signature: ([0-9a-f]+).*/\1/p' "${STATE}/tracking-body")"
if [[ -z "$signature_1" ]]; then
  echo "FAIL: tracking body has no signature" >&2
  cat "${STATE}/tracking-body" >&2
  fail=$((fail + 1))
else
  echo "PASS: tracking body carries a signature"
  pass=$((pass + 1))
fi

: >"${TMP}/cmds"
jq -n --arg created "$(days_ago 9)" \
  '[{number:401, title:"waiting", pull_request:null,
     user:{type:"User", login:"alice"}, author_association:"NONE",
     created_at:$created, comments:0}]' >"${STATE}/issues.json"
run_reminder
assert_eq "same set one day later does not comment again" "0" \
  "$(grep -c '^issue-comment ' "${TMP}/cmds" || true)"
signature_2="$(sed -n -E 's/.*reminder-signature: ([0-9a-f]+).*/\1/p' "${STATE}/tracking-body")"
assert_eq "signature stable when only the day count changes" "$signature_1" "$signature_2"
assert_eq "body still updated" "yes" \
  "$(grep -F 'issue-edit ' "${TMP}/cmds" >/dev/null && echo yes || echo no)"

: >"${TMP}/cmds"
jq -n --arg created "$(days_ago 13)" \
  '[{number:401, title:"waiting", pull_request:null,
     user:{type:"User", login:"alice"}, author_association:"NONE",
     created_at:$created, comments:0}]' >"${STATE}/issues.json"
run_reminder
assert_eq "alert comments even when the set is unchanged" "1" \
  "$(grep -c '^issue-comment ' "${TMP}/cmds" || true)"
assert_eq "alert run says alert" "yes" \
  "$(grep -F 'Alert: true' "${TMP}/out" >/dev/null && echo yes || echo no)"
assert_eq "alert comment names the maintainer" "yes" \
  "$(grep -F '@mgagp' "${STATE}/last-comment" >/dev/null && grep -F '**Alert:**' "${STATE}/last-comment" >/dev/null && echo yes || echo no)"

echo
echo "${pass} passed, ${fail} failed"
[[ "$fail" -eq 0 ]]
