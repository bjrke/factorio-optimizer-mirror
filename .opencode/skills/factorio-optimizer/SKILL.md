---
name: Factorio Optimizer CLI
description: Run the Factorio-Optimizer CLI for a material with chosen settings (surface, machines, belt/inserter tiers, belt lanes, stack sizes, cheats) and inspect the result as resolved settings, a production listing, an ASCII layout, or blueprint JSON/string. Use to reproduce or verify what the optimizer builds, e.g. a full belt of a specific material, without opening the web UI.
---

# Factorio Optimizer CLI

`./gradlew run` optimizes one material and prints the result. Run it from the
repo root; keep `--args="…"` quoted. `-q` hides Gradle's own output, and errors
go to stderr while the result goes to stdout, so `| head` / `> out.txt` / `| jq`
work:

```sh
./gradlew -q run --args="advanced-circuit --settings"
```

## Settings you can pass

The first non-flag argument is the **material** to optimize (default
`all-science-packs`, which has no blueprint). Everything else is a flag:

| Flag | Effect | Values |
| --- | --- | --- |
| `--base-game` | base game instead of Space Age | (flag) |
| `--planet=<s>` | which surface | `nauvis`, `vulcanus`, `gleba`, `fulgora`, `aquilo`, `space-platform-surface` |
| `--factory=<m>` | switch on a machine (repeatable) | its identifier, e.g. `assembling-machine-2`, `foundry`, `big-mining-drill`, `recycler` |
| `--belt=<t>` | belt tier | `transport-belt`, `fast-transport-belt` (default), `express-transport-belt`, `turbo-transport-belt` |
| `--inserter=<t>` | inserter tier | `burner-inserter`, `inserter`, `long-handed-inserter`, `fast-inserter` (default), `bulk-inserter`, `stack-inserter` |
| `--full-belt` | use **both** belt lanes (a full belt) instead of one | (flag) |
| `--belt-stack=<n>` | items per belt slot with the stack inserter | 1..4 |
| `--inserter-stack=<n>` | items per inserter hand | up to the tier's size |
| `--normalize=<x>` | scale the whole plan (rate multiplier) | a positive number |
| `--cheats` | add infinity-chest test inputs/outputs to the blueprint | (flag) |

Tiers/machines also accept their single-letter code. `--settings` prints the
**resolved** configuration, so you can see what a set of flags (or a pasted URL)
actually selected before trusting the rest of the output:

```sh
./gradlew -q run --args="advanced-circuit --belt=express-transport-belt --full-belt --settings"
# settings
#   material: advanced-circuit
#   belt: express-transport-belt
#   beltLanes: 2
#   ...
```

To get a full belt of a material, pass the material plus `--full-belt` (add the
belt tier / stack size you want to model):

```sh
./gradlew -q run --args="advanced-circuit --full-belt --belt=turbo-transport-belt --belt-stack=4"
```

## Choosing the output

`--settings`, `--listing`, `--layout`, `--json`, `--blueprint` are **not**
exclusive — pass several and they print in that order. No output flag means the
listing.

| Output | What it is |
| --- | --- |
| `--settings` | the resolved configuration (see above) |
| `--listing` | per-recipe machines, input/output rates and belt/inserter counts |
| `--layout` | a one-char-per-tile ASCII map + legend + failed routes |
| `--json` | the blueprint as JSON, one line → `| jq` |
| `--blueprint` | the paste-able Factorio blueprint string |

## Verifying a blueprint

`--layout` is the quickest way to see whether a plan is valid. One char per
tile: a **digit** is a machine (recipe in the legend), a **lowercase letter** is
the material a belt carries (legend), `^`/`v` are inserters, `(`/`)` underground
belts, `=` splitter, `-` pipe, `o` pipe-to-ground, `+` pole, `C` constant
combinator, `#` infinity chest, `L` loader. Rows carry their **y**, with a tens
ruler on top, so a coordinate in a failure can be located.

A valid layout ends with no failure section; a broken one lists them:

```
failures (1)
  plastic-bar to (44, 33) (no route)
```

That — not the blueprint string — is usually the thing worth reporting. For a
deeper check, decode `--json` with `jq` (`.blueprint.entities[]`), and to read a
blueprint string someone pasted rather than generated here, use the
`factorio-blueprints` skill.

## Pasting a URL instead

A web-UI URL is accepted in place of the flags (humans paste it because it is
convenient); its segments fill in the material, selection, normalize target and
stack sizes, and explicit flags override it. Run it with `--settings` to see what
it decoded to:

```sh
./gradlew -q run --args="http://localhost:8080/#/4.5/advanced-circuit/CFSXbmr/1/1 --settings"
```

## Repo map for deeper digging

- `jvmMain/.../jvm/CliOptimizer.kt` — flags, URL parsing and output modes.
- `commonMain/.../business/factorioUrl.kt` — `parseFactorioUrl`.
- `commonMain/.../business/LayoutAscii.kt` — the text layout renderer.
- `commonMain/.../business/BlueprintLayout.kt` — `buildBlueprint`,
  `blueprintLayoutDump` (entities + failures).
- `commonMain/.../business/BlueprintPlan.kt` — which recipes one blueprint builds.
