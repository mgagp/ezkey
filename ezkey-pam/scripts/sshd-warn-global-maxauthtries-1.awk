# Detect global MaxAuthTries 1 before any Match (OpenSSH keywords are
# case-insensitive). Exit 0 when found (caller should warn); else exit 1.
# Shared by install.sh, docker-entrypoint.sh, and test-install-sshd-warn.sh.
# Do not duplicate this program inline.
#
# Matches only the integer 1 (not 10/12). Tabs and spaces are [[:space:]].
{
  if ($0 ~ /^[[:space:]]*#/) {
    next
  }
  low = tolower($0)
  if (low ~ /^[[:space:]]*match[[:space:]]/) {
    exit
  }
  # Trailing (space|EOL) keeps MaxAuthTries 10 from matching as 1.
  if (low ~ /^[[:space:]]*maxauthtries[[:space:]]+1([[:space:]]|$)/) {
    found = 1
    exit
  }
}
END {
  exit !found
}
