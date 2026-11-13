---
name: JFR Profiles
description: Inspect JDK Flight Recorder (.jfr) profiles from ./gradlew jvmTestProfile — CPU hot methods, allocation sites, exceptions and thread parks, including filtering to de.bjrke with jfr print --json | jq. Use when asked to profile tests, find a hot method, or read a .jfr recording.
---

# JFR Profiles

`./gradlew jvmTestProfile` runs `jvmTest` with JDK Flight Recorder attached and
writes one merged recording to `build/profiles/jvmTest/jvmTest-<pid>.jfr`. The
JDK's own `jfr` CLI reads it; there is no report generator in this repo.

## Get a profile

```sh
ls -t build/profiles/jvmTest/*.jfr | head -1   # newest
./gradlew jvmTestProfile                       # fresh run; deletes the directory first
```

The task forces `maxParallelForks = 1`, so every test case lands in one file.
The recording starts at JVM launch and includes Gradle, JUnit, regex and
class-loading work — filter to `de.bjrke` before drawing conclusions.

`jfr` comes from JDK 25. The sandbox sets `JAVA_TOOL_OPTIONS`, which makes every
`jfr` call print a `Picked up JAVA_TOOL_OPTIONS: …` banner on stderr. It is
noise; suppress it with `env -u JAVA_TOOL_OPTIONS jfr …`.

## Quick triage

```sh
F=$(ls -t build/profiles/jvmTest/*.jfr | head -1)

jfr summary "$F"                          # duration + event counts
jfr view --width 160 hot-methods "$F"     # CPU self time per method
jfr view events-by-count "$F"             # which event types were recorded
jfr view types "$F"                       # all known event types
jfr view --help                           # every available view
```

`jfr view --verbose <view> "$F"` shows the columns and the query behind a view.

## What this recording contains

The `profile` settings record:

| Event | Use |
| --- | --- |
| `jdk.ExecutionSample` | CPU self time (`hot-methods`) |
| `jdk.ObjectAllocationSample` | allocation pressure (`allocation-by-class`, `allocation-by-site`) |
| `jdk.NativeMethodSample` | time in native methods |
| `jdk.ThreadPark` | blocking on parks/locks, with stacks |
| `jdk.JavaExceptionThrow` | thrown exceptions, with stacks |

These are **not** recorded (count 0), so their views print “No events found”:

- `jdk.CPUTimeSample` → `cpu-time-hot-methods`
- `jdk.MethodTiming` → `method-timing`
- `jdk.JavaMonitorEnter` → `contention-*`
- `jdk.ThreadSleep`

An empty `contention-*` view means the event type is off, not that nothing
contended. Use `jdk.ThreadPark` for blocking.

## Filter to project code

`jfr view` has no filter. Use `jfr print --json` and `jq`; the top frame is the
method that was running (self time), deeper frames are its callers.

Top self-time frames in `de.bjrke`:

```sh
jfr print --events jdk.ExecutionSample --json "$F" |
  jq -r '.recording.events[].values.stackTrace.frames[0].method
    | select((.type.name // "") | startswith("de/bjrke"))
    | "\(.type.name).\(.name)"' |
  sort | uniq -c | sort -rn | head -20
```

Inclusive time — the first `de.bjrke` frame from the top of each stack:

```sh
jfr print --events jdk.ExecutionSample --json "$F" |
  jq -r '.recording.events[].values.stackTrace.frames
    | map(select((.method.type.name // "") | startswith("de/bjrke")))
    | if length > 0 then .[0].method | "\(.type.name).\(.name)" else empty end' |
  sort | uniq -c | sort -rn | head -20
```

Allocation by `de.bjrke` site (weight is bytes; samples are estimates):

```sh
jfr print --events jdk.ObjectAllocationSample --json "$F" |
  jq -r '[.recording.events[].values
    | select((.stackTrace.frames[0].method.type.name // "") | startswith("de/bjrke"))
    | {w: .weight, m: (.stackTrace.frames[0].method | "\(.type.name).\(.name)")}]
    | group_by(.m) | map({m: .[0].m, mb: ((map(.w) | add) / 1048576)})
    | sort_by(-.mb) | .[:20][] | "\(.mb | floor) MB\t\(.m)"'
```

Exceptions by class and message:

```sh
jfr print --events jdk.JavaExceptionThrow --json "$F" |
  jq -r '.recording.events[].values | "\(.thrownClass.name)\t\(.message // "")"' |
  sort | uniq -c | sort -rn | head -20
```

Thread parks by parked class and duration:

```sh
jfr print --events jdk.ThreadPark --json "$F" |
  jq -r '.recording.events[].values | "\(.parkedClass.name)\t\(.duration)"' |
  sort | uniq -c | sort -rn | head -20
```

The helper script wraps the common queries:

```sh
S=.opencode/skills/jfr-profiles/scripts/jfr-top.sh

$S self        # top-frame self time in de.bjrke
$S inclusive   # first de.bjrke frame from the top
$S alloc       # allocation MB per de.bjrke site
$S exceptions  # exception class + message counts
$S self "$F" -n 30
PKG=de/bjrke/factorio/optimizer/common/blueprint $S self
```

## Read a stack

```sh
jfr view --width 200 --cell-height 3 jdk.ExecutionSample "$F"
jfr print --events jdk.ExecutionSample --stack-depth 64 "$F"
```

`jfr view` truncates long cells; widen with `--width`, add rows with
`--cell-height`, or use `--truncate beginning`. `jfr print` defaults to 5 stack
frames — pass `--stack-depth` for the full chain. The build records up to 1,024
frames (`-XX:FlightRecorderOptions=stackdepth=1024`).

## Caveats

- The profile is **CPU only**; there is no wall-clock attribution. Blocking is
  visible as `jdk.ThreadPark` events, not as time in `hot-methods`.
- Allocation weights are sampled estimates, not exact byte counts.
- A recording is per forked JVM. `maxParallelForks > 1` would produce several
  files instead of one merged profile.
- `jfr scrub` can drop events or threads to make a recording smaller to share.

## GUI alternatives

Open the `.jfr` in IntelliJ's JFR viewer (`File | Open`) or JDK Mission Control
for flame graphs and per-thread timelines.

## Repo map

- `build.gradle.kts` — the `jvmTestProfile` task and its JFR flags.
- `knowledge/build.md` §Profiling — how the recording is produced.
- `knowledge/decisions.md` — why JFR and CPU-only.
- `knowledge/build.md` §Traps — JFR recording traps (output directory, stack depth).
