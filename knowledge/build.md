# Build and environment

The Gradle build, generated game data, profiling and the JDK. Load this file when
a build or test task misbehaves, when regenerating prototypes or icons, or when
profiling.

## Generated data — do not hand-edit

- `src/commonMain/resources/prototypes/{base,space-age}.json` are `data.raw`
  dumps from `factorio --dump-data` for the base game and base + Space Age DLC.
  Regenerate with `./gradlew dumpPrototypes` (needs a Factorio Steam install).
  Because the game resolves the whole data stage, the dump already contains every
  `data-updates.lua`/`data-final-fixes.lua` change; the sanitizer in
  `buildSrc/` keeps only the prototype categories and fields the parser reads
  (recipes, machines, belts and their underground reach, inserters, poles, pumps,
  fluids, labs, surface properties, ...). The raw dumps stay in
  `build/prototype-dump/`, so after changing the sanitizer `./gradlew
  sanitizePrototypes` regenerates the resources without Factorio. `RecipeLoader`
  (`game/production/`) caches parsed results; the UI and CLI use the base dump,
  or the Space Age dump when Space Age is active.
- `src/jsMain/resources/icons/*.png` are 64x64 icons cropped from the 120x64
  source sheets of a Linux Factorio Steam install. `./gradlew updateImages` copies
  and optimizes only icons whose top-left 64x64 region changed by more than
  `imageChangedThreshold` (needs `imagemagick optipng pngquant`). Do not add icons
  by hand.

## Profiling

`./gradlew jvmTestProfile` reuses the `jvmTest` classpath and attaches JDK Flight
Recorder (`-XX:StartFlightRecording=…,settings=profile,dumponexit=true`,
`-XX:FlightRecorderOptions=stackdepth=1024`). It writes
`build/profiles/jvmTest/jvmTest-<pid>.jfr` and forces `maxParallelForks = 1`, so
every test case lands in one recording — that recording *is* the merge across
test cases. The task deletes the directory before each run and never counts as
up to date, so a stale profile cannot be mistaken for a fresh one.

The report is CPU only: self time per method from `jdk.ExecutionSample`. The
recording also carries `jdk.ThreadPark`, `jdk.ThreadSleep` and monitor events
with stacks, so blocking time can be inspected without a re-run. One profiler
only: a second sampler or a generated report is not added, and a wall-clock
question is answered from this recording.

There is no report generator and no text artifact: the JDK's own tooling reads
the recording. Open the `.jfr` in IntelliJ's JFR viewer (`File | Open`), JDK
Mission Control, or use the JDK CLI:

```
jfr view --width 160 hot-methods build/profiles/jvmTest/jvmTest-<pid>.jfr
jfr summary build/profiles/jvmTest/jvmTest-<pid>.jfr
```

Filter to `de.bjrke` in the viewer: the recording starts at JVM launch and
includes Gradle's test framework and regex/class-loading work. For the agent
workflow (filtering to `de.bjrke`, allocation/exception views), see
`.opencode/skills/jfr-profiles/SKILL.md`.

## Prerequisites

- JDK 25 is required. `gradle.properties` sets
  `org.gradle.java.installations.auto-download=false`, so a local JDK 25 must
  exist or the build fails with a cryptic error (only the installed version
  string). CI image: `$CI_REGISTRY_IMAGE:latest`
  (`registry.gitlab.com/bjrke/factorio-optimizer:latest`), built from
  `ci/Dockerfile` on `gradle:9.8.1-jdk25` plus Google Chrome for `jsBrowserTest`.

## Related tooling

- `buildSrc/` — Gradle build logic (`dumpPrototypes`, `sanitizePrototypes`,
  `updateImages` are the `factorio` group tasks).
- `.opencode/skills/` — the Factorio blueprint and optimizer skills.

## CI pipeline

`.gitlab-ci.yml` runs `gradle --no-daemon -PCI … check jsBrowserDistribution` on
every pipeline and caches `.gradle` as `GRADLE_USER_HOME`.

- **`-PCI` keeps the build clean.** `build.gradle.kts` reads the `CI` project
  property and calls `outputs.cacheIf { false }` on every task, so no compile,
  test, detekt or Spotless result is ever restored from an earlier commit; the
  build cache only serves `buildSrc` (a separate build, unaffected by that
  block). `.gradle/9.8.1` (the task history behind up-to-date checks) is
  deliberately not cached for the same reason.
- **`buildSrc/build` and `buildSrc/.gradle` are cached as a unit.** Rebuilding
  buildSrc invalidates the configuration cache entry even when the resulting jar
  is byte-identical, and a pipeline checkout has neither directory; without both
  restored, the configuration cache would be discarded on every pipeline. This
  is the only build output CI restores, and it is build logic, not the product.
- **The configuration cache is persisted** in `.gradle/configuration-cache` and
  is reused across commits, so nothing that changes per pipeline may be read
  while configuring: `generateBuildInfo` declares date/commit/branch as a
  `MapProperty` input fed by `providers.environmentVariable`/`providers.exec`,
  which is only read when the copy runs. A configuration-time `System.getenv`,
  `Instant.now()` or `expand` discards the entry every commit or freezes a stale
  value.
- **`BuildInfo.date` is the commit timestamp** (`CI_COMMIT_TIMESTAMP`, falling
  back to `git show -s --format=%cI`), not the wall-clock build time: it is
  stable per commit, which is what keeps `generateBuildInfo` up to date locally
  and the configuration cache valid.
- **`build/js/node_modules` is cached as part of the zip cache.** GitLab's cache
  archive, including the fastzip path the shared runners use, stores and
  restores symlinks, so the restored Yarn install keeps its `.bin` links and
  only needs validating; no `cache:archive` keyword is needed or accepted (see
  Traps).
- **The cache key uses `gradle/libs.versions.toml` and `ci/Dockerfile`.** A key
  file that does not exist is silently ignored, so the path must stay in sync
  with the real Dockerfile location.
- **The production bundle is minified by esbuild** (`esbuild-loader` via
  `webpack.config.d/02-esbuild-minify.js`), not webpack's Terser default; the
  dev bundle stays unminified either way. Development builds and `jsBrowserTest`
  do not load that config path.
- **CI cannot choose the image pull policy.** The instance's shared runners
  allow only `always`, and a job-level `pull_policy: if-not-present` fails the
  job before it starts with `invalid pull policy … not one of the
  allowed_pull_policies ([always])`. The image is therefore pulled once per
  pipeline even though it only changes with `ci/Dockerfile`.
- CI compiles in-process
  (`-Pkotlin.compiler.execution.strategy=in-process` in `.gitlab-ci.yml`): a
  pipeline is a single build, so a Kotlin daemon's start-up is never amortized,
  while locally the daemon is reused and its separate JVM keeps metaspace
  pressure off the Gradle daemon.

## Traps

- **`cache: archive` is not a GitLab CI keyword.** Adding `archive: tar` under
  `cache` fails config validation with `cache config contains unknown keys:
  archive`, which GitLab shows on the pipeline page before any job starts. The
  only cache keywords are `key`, `paths`, `untracked`, `when`, `policy`,
  `unprotect` and `fallback_keys`; the archive is a zip and already keeps
  symlinks. Verified on 2026-11-13 against the GitLab CI reference (`ci/yaml`).
- **`detektMainJvm`/`detektTestJvm` report "compiler errors found during analysis"
  although the code compiles.** Verified on 2026-10-24 at `395323f`, both with and
  without working-tree changes: both tasks print `There were 3 compiler errors
  found during analysis. This affects accuracy of reporting.` while `check` (and
  `compileKotlinJvm`) succeed, and no error names a file. detekt analyzes
  `commonMain` and `jvmMain` as one fragment, which is a known detekt limitation
  (issue #9602). Treat it as analysis-only noise unless the count changes or a
  finding looks wrong; prove a real compile failure with `./gradlew jvmTest`, not
  with this line.
- **A package move can crash `detektTestJvm` with a stale-analysis-index
  `FileNotFoundException`.** After moving `Blueprint` to `blueprint/printing/model`, the
  typed task failed resolving a class from
  `build/libs/factorio-optimizer-jvm-1.0.jar` even though the jar already contained
  only the new path; `--rerun-tasks` did not help, `./gradlew --stop` plus a rerun
  did. Recognize it by a `FileNotFoundException` for a jar entry that no longer
  exists — restart the daemon instead of editing code.
- **A `Copy` filter lambda cannot be used with the configuration cache.** A
  `filter { line -> … }` that reaches a build-script `val` closes over the script
  object, and `--configuration-cache-problems=fail` fails the build with
  `cannot serialize Gradle script object references`. `generateBuildInfo` therefore
  filters with a named `ReplaceValues` transformer that reads a `MapProperty`
  (`inputs.property("values", buildInfo)`), which is Configuration Cache-safe and
  keeps the substitution at execution time. Verified on Gradle 9.8.1.
- **A cold or stale Gradle daemon can fail build-script compilation with
  `Unresolved reference 'de'`.** After the daemon was stopped, `./gradlew` reported
  the `de.bjrke.factorio.optimizer.buildlogic.*` imports in `build.gradle.kts` as
  unresolved even though `buildSrc/build/libs/buildSrc.jar` contained the classes.
  It is a stale script classpath, not a code error: retry, or rerun the underlying
  task with `--rerun-tasks`; clearing `.gradle/<version>` plus `buildSrc/build`
  rebuilds `buildSrc` and succeeds. Do not edit `build.gradle.kts` over it.
- **Never delete `buildSrc/build/kotlin` while a Kotlin daemon from an earlier run
  is still alive.** A Kotlin compile daemon outlives the Gradle daemon that started
  it (seen: one from 20:02 still writing at 21:13, hours after `--stop`). Removing
  the cache directory underneath it makes the *next* builds fail with
  `Could not close incremental caches … NoSuchFileException` or `Can't write logical
  size … caches-jvm/inputs` / `Failed to clean up output files`, i.e. directories
  vanishing mid-write. Cure, in order: `./gradlew --stop`,
  `pkill -f '[K]otlinCompileDaemon'`, `rm -rf buildSrc/build`, then run again.
- **Host-share phantoms recur and block every build.** On the virtiofs checkout an
  interrupted Gradle run leaves entries that `stat` rejects while `ls` lists them
  as `d?????????`; renaming their *immediate* parent fails too, so detach at the
  shallowest ancestor that accepts the rename (`de` → `de_`, `META-INF` →
  `META-INF_`), then remove the tree. Recognize it by `Failed to clean up output
  files` or `Cannot access output property 'destinationDirectory'`.
- **A JFR recording is per forked JVM and needs an existing output directory.**
  `-XX:StartFlightRecording=filename=…/jvmTest-%p.jfr` expands `%p` to the PID but
  does not create `…/profiles/jvmTest`, so `jvmTestProfile` creates it in
  `doFirst` (and deletes it first, or old PIDs would mix into a "merged" run).
  JFR also truncates stacks at 64 frames by default;
  `-XX:FlightRecorderOptions=stackdepth=1024` is required for deep Kotlin call
  chains, and `jfr view`/JMC show the caller chain only from the stored depth.
  See §Profiling above.
