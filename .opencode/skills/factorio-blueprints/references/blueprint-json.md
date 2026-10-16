# Blueprint JSON reference

Decoded blueprints are plain JSON. This is the field-level reference for the
object types and entities you are most likely to read. It targets Factorio 2.0.
When a field is ambiguous, prefer what actually appears in the decoded JSON over
this list, and check `src/commonMain/.../model/Blueprint.kt` for what this repo
writes.

## Contents

- [Top-level objects](#top-level-objects)
- [Wires and connections](#wires-and-connections)
- [Common entity fields](#common-entity-fields)
- [Entity-specific fields](#entity-specific-fields)
- [Signals and icons](#signals-and-icons)
- [Decoding without the script](#decoding-without-the-script)
- [jq recipes](#jq-recipes)

## Top-level objects

Exactly one of these is the root key.

### `blueprint`

| Field | Type | Notes |
| --- | --- | --- |
| `item` | string | Always `"blueprint"` |
| `label` | string | Name shown on the blueprint |
| `label_color` | `{r,g,b,a}` | Optional label colour, 0..1 floats |
| `description` | string | Free text |
| `icons` | array | Up to 4 icon entries, see below |
| `entities` | array | The placed entities |
| `tiles` | array | Manually placed tiles, `{name, position}` |
| `schedules` | array | Train schedules (trains reference them by index) |
| `wires` | array | Optional circuit-wire list, see below |
| `version` | int | Packed game version |

### `blueprint_book`

| Field | Type | Notes |
| --- | --- | --- |
| `item` | string | Always `"blueprint-book"` |
| `label` | string | Book name |
| `icons` | array | Book icon(s) |
| `blueprints` | array | `[{index, blueprint: {...}}]`, ordered |
| `active_index` | int | Selected tab (0-based) |
| `version` | int | Packed game version |

### `deconstruction_planner` / `upgrade_planner`

| Field | Type | Notes |
| --- | --- | --- |
| `item` | string | `"deconstruction-planner"` / `"upgrade-planner"` |
| `label` | string | Planner name |
| `settings` | object | Filters/mapper held by the planner |
| `version` | int | Packed game version |

## Wires and connections

Two representations exist:

- Per-entity `neighbours`: an array of `entity_number`s. Factorio historically
  used this for **power-pole copper wires**. This repo emits it for poles.
- Top-level `wires`: an array of 4-tuples
  `[entity_a, connector_a, entity_b, connector_b]`. 2.0 uses this for some
  circuit connections, and `connector_id` distinguishes red/green/generic.

A blueprint does not recreate wires from proximity, so a working layout wires its
poles explicitly. If `neighbours` and `wires` disagree or one is missing, trust
what the game shows in the editor.

## Common entity fields

| Field | Type | Notes |
| --- | --- | --- |
| `entity_number` | int | Unique within the blueprint; `neighbours`/`wires` use it |
| `name` | string | Prototype name |
| `position` | `{x,y}` | **Center** of the entity, in tiles |
| `direction` | int | 2.0: `0`N `4`E `8`S `12`W; absent if it has no facing |
| `mirror` | bool | Entity is mirrored (some prototypes only) |
| `quality` | string | `"normal"`, `"uncommon"`, ... (2.0 quality) |
| `tags` | object | Free-form user data |
| `player_description` | string | Owner note (blueprint books) |

`position` is the geometric center: `topLeft.x = x - width/2`,
`topLeft.y = y - height/2`. Odd-sized entities land on half-tiles, even-sized on
whole tiles.

## Entity-specific fields

| Entity | Fields |
| --- | --- |
| Assembling machine, furnace, chemical plant, etc. | `recipe`; `recipe_quality` (2.0) |
| `underground-belt`, `pipe-to-ground` | `type`: `"input"` or `"output"` |
| `loader` | `type`: `"input"` or `"output"` |
| Constant combinator | `control_behavior.sections.sections[].filters[]` with `{index, name, quality?, comparator, count}`; `control_behavior.is_on` |
| Arithmetic/decider/etc. combinator | `control_behavior` conditions and outputs |
| Inserter | `control_behavior`, `filters` (filter inserters), `override_stack_size` |
| Logistic/requester/buffer chest | `request_filters` / `logistic_sections`, `bar` |
| Infinity chest / pipe | `infinity_settings`: `{remove_unfiltered_items, filters:[{name, count, mode, index}]}` |
| `splitter` | `input_priority` / `output_priority` (`"left"`/`"right"`/`"none"`) |
| Locomotive / wagon | `orientation`, and `color` for locomotives |
| Blueprint of another blueprint | `items` holds a nested blueprint item |

A constant combinator marking a fluid needs `type: "fluid"` in its filter;
without it the filter is an item signal and the game may not display it.

## Signals and icons

An icon or a circuit signal is `{"name": ..., "type"?: ..., "quality"?: ...}`.
`type` is required for fluids (`"fluid"`) and virtual signals
(`"virtual"`); plain items omit it. `icons` entries add `"index"` (1..4) for the
position in the icon corner.

`version` is a packed integer:

```
major = (version >> 48) & 0xFFFF
minor = (version >> 32) & 0xFFFF
patch = (version >> 16) & 0xFFFF
dev   =  version        & 0xFFFF
```

## Decoding without the script

If only a one-liner is available (the repo's blueprint strings use stored DEFLATE
but are normal zlib):

```sh
python3 - <<'PY'
import base64, json, zlib
s = open("tmp/blueprint.txt").read().strip()
s = "".join(s.split())[1:]                     # drop the version byte "0"
print(json.dumps(json.loads(zlib.decompress(base64.b64decode(s + "=" * (-len(s) % 4)))), indent=2))
PY
```

The equivalent in the tests uses the JDK's `java.util.zip.Inflater`
(`jvmTest/.../BlueprintStringDecodeTest.kt`).

## jq recipes

```sh
python3 $S --output /tmp/bp.json tmp/blueprint.txt

jq '.blueprint.label' /tmp/bp.json
jq '[.blueprint.entities[].name] | group_by(.)[] | {name: .[0], count: length}' /tmp/bp.json
jq '.blueprint.entities[] | select(.recipe) | {name, recipe, position}' /tmp/bp.json
jq '[.blueprint.entities[] | select(.direction != null)] | length' /tmp/bp.json
```
