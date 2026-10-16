---
name: Factorio Blueprints
description: Decode and read Factorio blueprint strings and their JSON (entities, coordinates, directions, recipes, wires). Use when a user pastes a blueprint string or asks to inspect, explain, or compare a blueprint layout.
---

# Factorio Blueprints

Read a Factorio blueprint the fast way: decode the string to JSON, then read the
entities. This repo both **generates** blueprints (`--blueprint`) and models them
in `commonMain`, so the same JSON shape appears in the tests.

## Decode

A blueprint string is the version byte `0`, then the **zlib-deflated** blueprint
JSON in **standard base64**:

```
"0" + base64( zlib( json ) )
```

Factorio 2.0 also imports the raw JSON directly, so a paste may be either form.

Use the bundled script (standard library only, no network, works offline):

```sh
S=.opencode/skills/factorio-blueprints/scripts/decode.py

python3 $S --summary blueprint.txt     # kind, label, icon, entity/tile counts
python3 $S --entities blueprint.txt    # one line per entity: no, name, pos, dir, recipe
python3 $S --output /tmp/bp.json blueprint.txt   # full JSON, then query with jq
```

`blueprint.txt` may be a file **or** the string itself; stdin works too. Wrapped
or padded strings are handled. Example entity line from a generated blueprint:

```
1 assembling-machine-3 (41.5, 3.5) recipe=iron-gear-wheel
4 fast-transport-belt (40.5, 0.5) dir=4 (east)
14 constant-combinator (45.5, 5.5) control_behavior
```

## Read the JSON

Top level is one object: `blueprint`, `blueprint_book`, `deconstruction_planner`
or `upgrade_planner`. A book nests more blueprints in `blueprints[].blueprint`.

The fields that matter for reading a layout:

| Field | Meaning |
| --- | --- |
| `entity_number` | Unique id within the blueprint; wire links refer to it |
| `name` | Prototype name, e.g. `assembling-machine-3`, `fast-transport-belt` |
| `position` | `{x, y}` in tiles — the **center** of the entity, not a corner |
| `direction` | `0`=north, `4`=east, `8`=south, `12`=west (2.0); absent = no facing |
| `recipe` | The recipe an assembling machine / furnace runs |
| `type` | `input`/`output` for `underground-belt` and `pipe-to-ground` |
| `neighbours` | `entity_number`s this entity is wired to (power poles, see below) |
| `control_behavior` | Circuit conditions / filters (constant combinators, inserters) |
| `infinity_settings` | Infinity chest contents (`remove_unfiltered_items`, `filters`) |
| `items` | Machine-specific item settings, e.g. a silo's launch target |
| `tags` | Free-form user data attached to the entity |

`icons` (up to 4, each `{index, signal:{name, type?, quality?}}`) sets the map
icon; `label` is the name shown on the blueprint. `version` packs the game
version as `major<<48 | minor<<32 | patch<<16 | dev` (the script prints it).

### Coordinates and directions

- `position` is the entity **center**. A 1x1 or 3x3 entity sits on a half-tile
  (x.5); a 2x2 entity sits on a whole tile (x.0). `topLeft = position - size/2`.
- 2.0 doubles the cardinal `direction`s to `0/4/8/12`; older blueprints use
  `0/2/4/6`. Do not assume `2` means east in a 2.0 blueprint.
- Belts carry no material in the blueprint — the contents only exist in-game.

### Wires

Power-pole copper wires are listed per entity in `neighbours` (a list of
`entity_number`s). A blueprint does **not** rebuild wires from proximity, so a
layout that runs is expected to wire its poles explicitly. Factorio 2.0 may also
emit a top-level `wires` array of `[entity, connector, entity, connector]` for
some circuit connections — when in doubt, check both.

## This repo's mapping

- `src/commonMain/.../model/Blueprint.kt` — `Blueprint`, `BlueprintEntity`,
  `InfinitySettings`, and `toJson()`. This is the code that writes the fields
  above (`direction`, `recipe`, `type`, `neighbours`, `control_behavior`, ...).
- `src/commonMain/.../business/blueprintString.kt` — `blueprintString(json)`
  emits `"0" + base64(zlibStored(json))`. The zlib payload uses **stored**
  (uncompressed) DEFLATE blocks, but it is a normal zlib stream any decoder reads.
- `src/commonMain/.../business/BlueprintLayout.kt` — `buildBlueprint` lays out
  entities and wires poles (`neighbours`); `NORTH/EAST/SOUTH/WEST = 0/4/8/12`.
- `src/commonMain/.../business/BlueprintPlan.kt` — reduces an optimization
  result to the recipes of one blueprint.
- Tests to copy patterns from: `commonTest/.../BlueprintStringTest.kt`,
  `jvmTest/.../BlueprintStringDecodeTest.kt` (decodes with the JDK's zlib),
  `jvmTest/.../BlueprintIntegrationTest.kt` (decodes and inspects entities).

The repo's own blueprints omit `version` and use stored DEFLATE; both are legal
and decode fine.

## Workflow when a user pastes a blueprint

1. Write the string to `tmp/blueprint.txt` (or pass it as the argument).
2. `--summary` for the label and counts, `--entities` to see the machines.
3. Full JSON to `/tmp/bp.json` and `jq` it for questions like "how many
   `fast-inserter`s" or "which recipes run".
4. To check a machine's size or footprint, use `blueprintMachineFootprint(...)`
   in `BlueprintPlan.kt` rather than guessing from `position` alone.

See `references/blueprint-json.md` for the fuller per-object field reference.
