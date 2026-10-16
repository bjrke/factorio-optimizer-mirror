#!/usr/bin/env python3
"""Decode a Factorio blueprint string into JSON.

A blueprint string is the version byte ``0`` followed by the zlib-deflated
blueprint JSON in standard base64. Since Factorio 2.0 the raw JSON is imported
as well, so this script accepts either form.

Usage::

    python3 decode.py <string-or-file>
    python3 decode.py --entities blueprint.txt
    cat blueprint.txt | python3 decode.py

It only uses the standard library, so it runs anywhere Python 3 does.
"""

from __future__ import annotations

import argparse
import base64
import binascii
import json
import os
import sys
import zlib
from typing import Any

# The top-level objects a blueprint string may wrap. A blueprint book nests
# more blueprints under "blueprints".
BLUEPRINT_KINDS = (
    "blueprint",
    "blueprint_book",
    "deconstruction_planner",
    "upgrade_planner",
    "blueprint_book_item",
)


def decode(blueprint_string: str) -> Any:
    """Return the JSON of *blueprint_string* (a string, or raw JSON)."""
    # Blueprint strings are shared as one long line but often wrapped; drop all
    # whitespace before decoding.
    text = "".join(blueprint_string.split())
    if not text:
        raise SystemExit("empty input")
    if text.startswith("{"):
        # Factorio 2.0 imports raw blueprint JSON directly.
        return json.loads(text)

    # First character is the version byte (always ASCII "0" today); the rest is
    # base64. Padding may be stripped by copy/paste, so re-add it.
    payload = text[1:]
    payload += "=" * (-len(payload) % 4)
    try:
        compressed = base64.b64decode(payload, validate=True)
    except (ValueError, binascii.Error) as error:
        raise SystemExit(f"not valid base64 after the version byte: {error}")
    try:
        raw = zlib.decompress(compressed)
    except zlib.error as error:
        raise SystemExit(
            "the payload is not a zlib stream; is this really a blueprint "
            f"string? ({error})"
        )
    return json.loads(raw)


def decode_version(version: int) -> str:
    """Turn the blueprint's packed ``version`` integer into ``major.minor.patch``."""
    major = (version >> 48) & 0xFFFF
    minor = (version >> 32) & 0xFFFF
    patch = (version >> 16) & 0xFFFF
    dev = version & 0xFFFF
    text = f"{major}.{minor}.{patch}"
    return f"{text} (dev {dev})" if dev else text


def iter_entities(document: Any):
    """Yield ``(path, entity)`` for every blueprint in *document*, books included."""
    if not isinstance(document, dict):
        return
    for kind in BLUEPRINT_KINDS:
        if kind not in document:
            continue
        node = document[kind]
        if kind == "blueprint_book" and isinstance(node, dict):
            for index, child in enumerate(node.get("blueprints", []), start=1):
                label = child.get("blueprint", {}).get("label", "")
                for path, entity in iter_entities(child):
                    yield (f"[{index}] {label} {path}".strip(), entity)
        elif kind == "blueprint" and isinstance(node, dict):
            for entity in node.get("entities", []):
                yield ("", entity)


def print_entities(document: Any) -> None:
    """Print one line per entity: number, name, position, direction, extras."""
    for path, entity in iter_entities(document):
        parts = [
            str(entity.get("entity_number", "?")),
            entity.get("name", "?"),
            format_position(entity.get("position")),
        ]
        if entity.get("direction") is not None:
            parts.append(f"dir={entity['direction']} ({direction_name(entity['direction'])})")
        for key in ("recipe", "type", "items", "tags"):
            if key in entity:
                parts.append(f"{key}={entity[key]}")
        if "control_behavior" in entity:
            parts.append("control_behavior")
        if "infinity_settings" in entity:
            parts.append("infinity_settings")
        if entity.get("neighbours"):
            parts.append(f"neighbours={entity['neighbours']}")
        prefix = f"{path}: " if path else ""
        print(prefix + " ".join(parts))


def format_position(position: Any) -> str:
    if not isinstance(position, dict):
        return "?"
    return f"({position.get('x')}, {position.get('y')})"


def direction_name(direction: int) -> str:
    # 2.0 uses 0/4/8/12 for the cardinals; older blueprints used 0/2/4/6.
    if direction in (0,):
        return "north"
    if direction in (2, 4):
        return "east"
    if direction in (6, 8):
        return "south"
    if direction in (10, 12):
        return "west"
    return "?"


def summarize(document: Any) -> None:
    """Print the top-level kind, label and counts without the whole body."""
    if isinstance(document, dict):
        for kind in BLUEPRINT_KINDS:
            if kind not in document:
                continue
            node = document[kind]
            print(f"kind: {kind}")
            if isinstance(node, dict):
                if "label" in node:
                    print(f"label: {node['label']}")
                if "version" in node:
                    print(f"version: {decode_version(node['version'])}")
                if "item" in node:
                    print(f"item: {node['item']}")
                if "icons" in node:
                    names = [icon.get("signal", {}).get("name") for icon in node["icons"]]
                    print(f"icons: {', '.join(filter(None, names))}")
                if "entities" in node:
                    print(f"entities: {len(node['entities'])}")
                if "tiles" in node:
                    print(f"tiles: {len(node['tiles'])}")
                if "blueprints" in node:
                    print(f"blueprints: {len(node['blueprints'])}")
            return
    print("not a recognized blueprint object")


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument(
        "source",
        nargs="?",
        help="blueprint string or a file containing one; reads stdin when omitted",
    )
    parser.add_argument(
        "--entities",
        action="store_true",
        help="print one line per entity instead of the whole JSON",
    )
    parser.add_argument(
        "--summary",
        action="store_true",
        help="print only the top-level kind, label and counts",
    )
    parser.add_argument(
        "--compact",
        action="store_true",
        help="pretty-print the JSON with no indentation on one line",
    )
    parser.add_argument(
        "--output",
        metavar="FILE",
        help="write the decoded JSON to FILE instead of stdout",
    )
    args = parser.parse_args(argv)

    if args.source is None:
        raw = sys.stdin.read()
    elif os.path.isfile(args.source):
        with open(args.source, encoding="utf-8") as handle:
            raw = handle.read()
    else:
        # The argument may itself be the blueprint string; isfile() is False for
        # a name that is too long or does not exist.
        raw = args.source

    document = decode(raw)

    if args.summary:
        summarize(document)
        return 0
    if args.entities:
        print_entities(document)
        return 0

    text = json.dumps(document, indent=None if args.compact else 2)
    if args.output:
        with open(args.output, "w", encoding="utf-8") as handle:
            handle.write(text + "\n")
    else:
        print(text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
