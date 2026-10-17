"""Build a reproducible, source-linked checklist for the 1.20.1 port.

The 1.7.10 registration code is deliberately kept as the baseline on this
branch. This scanner records declarations and source locations; it does not
claim that a discovered entry has been implemented or tested.
"""

from __future__ import annotations

import argparse
import csv
import io
import re
from collections import Counter
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "src" / "main" / "java" / "com" / "hbm"
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "hbm"
OUTPUT = ROOT / "docs" / "porting" / "legacy-inventory.csv"
STATUS = ROOT / "docs" / "porting" / "status.csv"
FIELDS = ("kind", "key", "source", "line", "status", "target", "notes")


def rel(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def add(rows: list[dict[str, str]], kind: str, key: str, path: Path, line: int = 0) -> None:
    rows.append(
        dict(
            kind=kind,
            key=key,
            source=rel(path),
            line=str(line) if line else "",
            status="unported",
            target="",
            notes="",
        )
    )


def scan_calls(rows: list[dict[str, str]], path: Path, kind: str, pattern: str) -> None:
    expression = re.compile(pattern)
    for number, line in enumerate(path.read_text(encoding="utf-8", errors="replace").splitlines(), 1):
        match = expression.search(line)
        if match:
            add(rows, kind, match.group(1), path, number)


def inventory() -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    scan_calls(rows, JAVA / "blocks" / "ModBlocks.java", "block", r"GameRegistry\.registerBlock\(\s*(\w+)")
    scan_calls(rows, JAVA / "items" / "ModItems.java", "item", r"GameRegistry\.registerItem\(\s*(\w+)")
    scan_calls(rows, JAVA / "tileentity" / "TileMappings.java", "block_entity", r"^\s*put\(\s*(\w+)\.class")
    scan_calls(rows, JAVA / "entity" / "EntityMappings.java", "entity", r"^\s*add(?:Entity|Mob)\(\s*(\w+)\.class")

    for path in sorted((JAVA / "inventory" / "recipes").glob("*.java")):
        add(rows, "recipe_source", path.stem, path)
    for path in sorted((JAVA / "crafting").rglob("*.java")):
        add(rows, "recipe_source", path.stem, path)
    for path in sorted((JAVA / "config").glob("*.java")):
        add(rows, "config_source", path.stem, path)
    for path in sorted((JAVA / "inventory" / "gui").rglob("*.java")):
        add(rows, "screen_source", path.stem, path)
    for path in sorted((JAVA / "wiaj").rglob("*Gui*.java")):
        add(rows, "screen_source", path.stem, path)
    for path in sorted((JAVA / "render").rglob("*.java")):
        add(rows, "renderer_source", path.stem, path)
    for path in sorted((JAVA / "packet").rglob("*.java")):
        add(rows, "packet_source", path.stem, path)
    for path in sorted((JAVA / "handler").rglob("*.java")):
        if any(part in {"ae2", "imc", "microblocks", "nei"} for part in path.parts):
            add(rows, "integration_source", path.stem, path)
    for path in sorted((JAVA / "world").rglob("*.java")):
        add(rows, "worldgen_source", path.stem, path)
    add(rows, "worldgen_source", "HbmWorldGen", JAVA / "lib" / "HbmWorldGen.java")
    add(rows, "worldgen_source", "StructureManager", JAVA / "main" / "StructureManager.java")
    for name in ("BlockEnums.java", "ItemEnums.java", "ItemAmmoEnums.java"):
        folder = "blocks" if name.startswith("Block") else "items"
        add(rows, "variant_source", Path(name).stem, JAVA / folder / name)
    for path in sorted((JAVA / "util").glob("Compat*.java")):
        add(rows, "integration_source", path.stem, path)
    for path in sorted(ASSETS.rglob("*")):
        if path.is_file():
            kind = "structure_asset" if "structures" in path.parts else "asset"
            add(rows, kind, path.relative_to(ASSETS).as_posix(), path)
    if STATUS.exists():
        with STATUS.open(encoding="utf-8", newline="") as stream:
            overrides = {
                (row["kind"], row["key"]): row
                for row in csv.DictReader(stream)
            }
        for row in rows:
            override = overrides.get((row["kind"], row["key"]))
            if override:
                row.update({field: override[field] for field in ("status", "target", "notes")})
    rows.sort(key=lambda row: (row["kind"], row["key"], row["source"], int(row["line"] or 0)))
    return rows


def render(rows: list[dict[str, str]]) -> str:
    stream = io.StringIO(newline="")
    writer = csv.DictWriter(stream, fieldnames=FIELDS, lineterminator="\n")
    writer.writeheader()
    writer.writerows(rows)
    return stream.getvalue()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="fail if the committed checklist is stale")
    args = parser.parse_args()
    rows = inventory()
    result = render(rows)
    if args.check:
        if not OUTPUT.exists() or OUTPUT.read_text(encoding="utf-8") != result:
            raise SystemExit("legacy inventory is missing or stale; run build_inventory.py")
    else:
        OUTPUT.parent.mkdir(parents=True, exist_ok=True)
        OUTPUT.write_text(result, encoding="utf-8", newline="")
    print(f"Inventory: {len(rows)} entries")
    for kind, count in sorted(Counter(row["kind"] for row in rows).items()):
        print(f"  {kind}: {count}")


if __name__ == "__main__":
    main()
