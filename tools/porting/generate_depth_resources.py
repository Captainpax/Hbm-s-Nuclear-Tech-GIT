"""Generate the mechanical block/item JSON and copy original textures for the depth slice."""

import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "src/main/resources/assets/hbm/textures"
ASSETS = ROOT / "src/forge120/resources/assets/hbm"
DATA = ROOT / "src/forge120/resources/data/hbm"

BLOCKS = {
    "stone_resource_limestone": ("stone_resource.limestone", None, None),
    "stone_depth": ("stone_depth", None, None),
    "cluster_depth_iron": ("cluster_depth_iron", "crystal_iron", None),
    "cluster_depth_titanium": ("cluster_depth_titanium", "crystal_titanium", None),
    "cluster_depth_tungsten": ("cluster_depth_tungsten", "crystal_tungsten", None),
    "ore_depth_cinnebar": ("ore_depth_cinnebar", "cinnebar", (2, 4)),
    "ore_depth_zirconium": ("ore_depth_zirconium", "nugget_zirconium", (2, 3)),
    "ore_depth_borax": ("ore_depth_borax", "powder_borax", None),
}
ITEMS = ("crystal_tungsten", "nugget_zirconium", "powder_borax")


def write(path: Path, payload: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")


for block, (texture, drop, count) in BLOCKS.items():
    shutil.copyfile(SOURCE / "blocks" / f"{texture}.png", ASSETS / "textures/block" / f"{block}.png")
    write(ASSETS / "blockstates" / f"{block}.json", {"variants": {"": {"model": f"hbm:block/{block}"}}})
    write(ASSETS / "models/block" / f"{block}.json",
          {"parent": "minecraft:block/cube_all", "textures": {"all": f"hbm:block/{block}"}})
    write(ASSETS / "models/item" / f"{block}.json", {"parent": f"hbm:block/{block}"})

    children = []
    if drop:
        children.append({"type": "minecraft:item", "name": f"hbm:{block}", "conditions": [
            {"condition": "minecraft:match_tool", "predicate": {"enchantments": [
                {"enchantment": "minecraft:silk_touch", "levels": {"min": 1}}
            ]}}
        ]})
    ordinary = {"type": "minecraft:item", "name": f"hbm:{drop or block}"}
    if count:
        ordinary["functions"] = [{"function": "minecraft:set_count", "count": {
            "type": "minecraft:uniform", "min": count[0], "max": count[1]
        }}]
    children.append(ordinary)
    entry = {"type": "minecraft:alternatives", "children": children} if drop else ordinary
    write(DATA / "loot_tables/blocks" / f"{block}.json", {"type": "minecraft:block", "pools": [
        {"rolls": 1, "entries": [entry], "conditions": [{"condition": "minecraft:survives_explosion"}]}
    ]})

for item in ITEMS:
    shutil.copyfile(SOURCE / "items" / f"{item}.png", ASSETS / "textures/item" / f"{item}.png")
    write(ASSETS / "models/item" / f"{item}.json",
          {"parent": "minecraft:item/generated", "textures": {"layer0": f"hbm:item/{item}"}})
