#!/usr/bin/env bash
# Summarise a JFR recording produced by ./gradlew jvmTestProfile.
#
# Usage: jfr-top.sh [self|inclusive|alloc|exceptions] [FILE] [-n N]
#
#   self        top-frame CPU self time in the package (default)
#   inclusive   first package frame from the top of each stack
#   alloc       allocation MB per package site
#   exceptions  exception class + message counts
#
# FILE defaults to the newest build/profiles/jvmTest/*.jfr. N defaults to 15.
# PKG overrides the package prefix (default de/bjrke).
set -euo pipefail

usage() {
  sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'
}

mode=self
file=
limit=15
pkg=${PKG:-de/bjrke}

while (($#)); do
  case $1 in
    self | inclusive | alloc | exceptions) mode=$1 ;;
    -n)
      [[ $# -ge 2 ]] || {
        echo "jfr-top: -n needs a number" >&2
        exit 2
      }
      limit=$2
      shift
      ;;
    -h | --help)
      usage
      exit 0
      ;;
    -*)
      echo "jfr-top: unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
    *) file=$1 ;;
  esac
  shift
done

[[ $limit =~ ^[0-9]+$ ]] || {
  echo "jfr-top: -n must be a non-negative integer" >&2
  exit 2
}

if [[ -z $file ]]; then
  file=$(ls -t build/profiles/jvmTest/*.jfr 2>/dev/null | head -1 || true)
fi
if [[ -z $file || ! -f $file ]]; then
  echo "jfr-top: no .jfr found; run ./gradlew jvmTestProfile" >&2
  exit 1
fi

command -v jq >/dev/null 2>&1 || {
  echo "jfr-top: jq is not on PATH" >&2
  exit 1
}
JFR_BIN=$(type -P jfr) || {
  echo "jfr-top: jfr is not on PATH (needs JDK 25)" >&2
  exit 1
}

# The sandbox sets JAVA_TOOL_OPTIONS, which makes every jfr call print a banner.
jfr() { env -u JAVA_TOOL_OPTIONS "$JFR_BIN" "$@"; }

echo "# $mode: $file" >&2

case $mode in
  self)
    jfr print --events jdk.ExecutionSample --json "$file" |
      jq -r --arg pkg "$pkg" --argjson limit "$limit" '
        [.recording.events[].values.stackTrace.frames[0].method
         | select((.type.name // "") | startswith($pkg))
         | "\(.type.name).\(.name)"]
        | group_by(.) | map({m: .[0], n: length})
        | sort_by(-.n) | .[:$limit][]
        | "\(.n)\t\(.m)"'
    ;;
  inclusive)
    jfr print --events jdk.ExecutionSample --json "$file" |
      jq -r --arg pkg "$pkg" --argjson limit "$limit" '
        [.recording.events[].values.stackTrace.frames
         | map(select((.method.type.name // "") | startswith($pkg)))
         | if length > 0 then .[0].method | "\(.type.name).\(.name)" else empty end]
        | group_by(.) | map({m: .[0], n: length})
        | sort_by(-.n) | .[:$limit][]
        | "\(.n)\t\(.m)"'
    ;;
  alloc)
    jfr print --events jdk.ObjectAllocationSample --json "$file" |
      jq -r --arg pkg "$pkg" --argjson limit "$limit" '
        [.recording.events[].values
         | select((.stackTrace.frames[0].method.type.name // "") | startswith($pkg))
         | {w: .weight, m: (.stackTrace.frames[0].method | "\(.type.name).\(.name)")}]
        | group_by(.m) | map({m: .[0].m, mb: ((map(.w) | add) / 1048576)})
        | sort_by(-.mb) | .[:$limit][]
        | "\(.mb | floor) MB\t\(.m)"'
    ;;
  exceptions)
    jfr print --events jdk.JavaExceptionThrow --json "$file" |
      jq -r --argjson limit "$limit" '
        [.recording.events[].values | "\(.thrownClass.name)\t\(.message // "")"]
        | group_by(.) | map({m: .[0], n: length})
        | sort_by(-.n) | .[:$limit][]
        | "\(.n)\t\(.m)"'
    ;;
esac
