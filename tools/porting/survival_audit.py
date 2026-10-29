"""Read saved Survival metadata, player inventory and ordinary Minecraft statistics.

Run after the integrated server saves/stops. This never edits world or player files.
The bootstrap check covers the beginning of the walkthrough, not the electric-furnace gate.
"""
from __future__ import annotations

import argparse
from collections import Counter
import gzip
import io
import json
from pathlib import Path
import struct
import zlib

import survival_driver as driver

SAVE = driver.RUN / "saves/HBM Survival Progression"


def nbt(path: Path) -> dict:
    return decode_nbt(gzip.decompress(path.read_bytes()))


def decode_nbt(data: bytes) -> dict:
    stream = io.BytesIO(data)

    def read(length: int) -> bytes:
        if length < 0 or length > 32_000_000:
            raise ValueError("Invalid NBT length")
        value = stream.read(length)
        if len(value) != length:
            raise ValueError("Truncated NBT")
        return value

    def number(format: str):
        return struct.unpack(format, read(struct.calcsize(format)))[0]

    def string() -> str:
        return read(number(">H")).decode("utf-8")

    def payload(kind: int):
        formats = {1: ">b", 2: ">h", 3: ">i", 4: ">q", 5: ">f", 6: ">d"}
        if kind in formats:
            return number(formats[kind])
        if kind == 7:
            return list(read(number(">i")))
        if kind == 8:
            return string()
        if kind == 9:
            child, length = number(">B"), number(">i")
            if not 0 <= length <= 1_000_000:
                raise ValueError("Invalid NBT list")
            return [payload(child) for _ in range(length)]
        if kind == 10:
            result = {}
            while (child := number(">B")) != 0:
                key = string()
                result[key] = payload(child)
            return result
        if kind in (11, 12):
            length = number(">i")
            if not 0 <= length <= 1_000_000:
                raise ValueError("Invalid NBT array")
            return [number(">i" if kind == 11 else ">q") for _ in range(length)]
        raise ValueError(f"Unsupported NBT type: {kind}")

    if number(">B") != 10:
        raise ValueError("NBT root must be a compound")
    string()
    result = payload(10)
    if stream.read():
        raise ValueError("Trailing NBT bytes")
    return result


def region_chunks(path: Path):
    """Read saved Anvil region chunks, including the separate entity store."""
    data = path.read_bytes()
    if len(data) < 8_192:
        raise ValueError(f"Truncated region header: {path}")
    for index in range(1_024):
        location = int.from_bytes(data[index*4:index*4+4], "big")
        if not location:
            continue
        offset, sectors = (location >> 8) * 4_096, location & 255
        if offset < 8_192 or not sectors or offset + sectors * 4_096 > len(data):
            raise ValueError(f"Invalid chunk location: {path} slot {index}")
        length = int.from_bytes(data[offset:offset+4], "big")
        if not 1 < length <= sectors * 4_096 - 4:
            raise ValueError(f"Invalid chunk length: {path} slot {index}")
        compression, payload = data[offset+4], data[offset+5:offset+4+length]
        if compression == 1:
            payload = gzip.decompress(payload)
        elif compression == 2:
            payload = zlib.decompress(payload)
        elif compression != 3:
            raise ValueError(f"Unsupported region compression {compression}: {path}")
        yield decode_nbt(payload)


def nearby_saved_items() -> dict:
    """Account for ordinary death drops and the starter furnace without edits."""
    drops, furnaces, mobs = [], [], []
    # Only saved chunks near the starter base; reading does not load them in-game.
    for rx in (-1, 0):
        for rz in (-1, 0):
            name = f"r.{rx}.{rz}.mca"
            entities = SAVE / "entities" / name
            if entities.exists():
                for chunk in region_chunks(entities):
                    for entity in chunk.get("Entities", []):
                        pos = entity.get("Pos", [])
                        if len(pos) != 3 or (pos[0]-29)**2 + (pos[2]-39)**2 > 64**2:
                            continue
                        if entity["id"] == "minecraft:item":
                            drops.append({"position":pos, "age":entity["Age"], "item":entity["Item"]})
                        elif entity["id"] in {"minecraft:skeleton", "minecraft:zombie", "minecraft:creeper", "minecraft:spider"}:
                            mobs.append({"type":entity["id"], "position":pos, "health":entity.get("Health")})
            terrain = SAVE / "region" / name
            if terrain.exists():
                for chunk in region_chunks(terrain):
                    for block in chunk.get("block_entities", []):
                        if block["id"] == "minecraft:furnace" and (block["x"],block["y"],block["z"]) == (29,63,38):
                            furnaces.append({key:block[key] for key in ("x","y","z","Items","BurnTime","CookTime")})
    return {"dropped_items":drops, "starter_furnaces":furnaces, "nearby_hostile_mobs":mobs}


def saved_blocks(positions: list[tuple[int, int, int]]) -> dict:
    """Decode the saved block palette at explicit audit positions; never load chunks."""
    wanted = set(positions)
    result = {}
    for rx, rz in {(x//512, z//512) for x,y,z in wanted}:
        for chunk in region_chunks(SAVE / "region" / f"r.{rx}.{rz}.mca"):
            selected = [(x,y,z) for x,y,z in wanted if x//16 == chunk["xPos"] and z//16 == chunk["zPos"]]
            if not selected:
                continue
            sections = {section["Y"]:section for section in chunk["sections"]}
            for x,y,z in selected:
                states = sections.get(y//16, {}).get("block_states", {"palette":[{"Name":"minecraft:air"}]})
                palette = states["palette"]
                index = 0
                if len(palette) > 1:
                    bits = max(4, (len(palette)-1).bit_length())
                    per_long = 64//bits
                    cell = (y & 15)*256 + (z & 15)*16 + (x & 15)
                    word = states["data"][cell//per_long] & ((1 << 64)-1)
                    index = (word >> ((cell % per_long)*bits)) & ((1 << bits)-1)
                result[f"{x},{y},{z}"] = palette[index]["Name"]
    assert len(result) == len(wanted), "An audited position has no saved chunk"
    return result


def audit(bootstrap: bool, first_iron: bool = False, iron_tools: bool = False) -> dict:
    data = nbt(SAVE / "level.dat")["Data"]
    assert data["LevelName"] == "HBM Survival Progression" and data["GameType"] == 0
    assert data["allowCommands"] == 0 and data["Difficulty"] == 2
    generation = data["WorldGenSettings"]
    assert generation["seed"] == 20_301_017 and generation["bonus_chest"] == 0 and generation["generate_features"] == 1
    packs = set(data["DataPacks"]["Enabled"])
    assert packs <= {"vanilla", "mod:forge", "mod:hbm", "mod_resources"}
    assert packs & {"mod:hbm", "mod_resources"}, "HBM data pack is not enabled"
    rules = data["GameRules"]
    for key in ("doDaylightCycle", "doMobSpawning", "doTileDrops", "doEntityDrops"):
        assert rules[key] == "true", key
    assert rules["keepInventory"] == "false" and rules["randomTickSpeed"] == "3"
    players = list((SAVE / "playerdata").glob("*.dat"))
    assert len(players) == 1, "Expected only the isolated walkthrough player"
    player = nbt(players[0])
    assert player["playerGameType"] == 0
    assert not any(player["abilities"][key] for key in ("instabuild", "mayfly", "flying", "invulnerable"))
    inventory = [{"slot": item["Slot"], "item": item["id"], "count": item["Count"], "tag": item.get("tag", {})}
                 for item in player["Inventory"]]
    stats = json.loads((SAVE / "stats" / f"{players[0].stem}.json").read_text(encoding="utf-8"))["stats"]
    initial = json.loads((driver.RUN / "hbm-survival-initial.json").read_text(encoding="utf-8"))
    assert initial["session"] == driver.session_id() and initial["mode"] == "survival" and not initial["cheats"]
    assert initial["carried"]["count"] == 0 and all(not slot["count"] for slot in initial["inventory"])
    report = {"scope": "Beginning of an automated raw-resource Survival walkthrough; electric-furnace checkpoint remains open",
              "seed": generation["seed"], "cheats": False, "difficulty": data["Difficulty"], "enabled_packs": sorted(packs),
              "world_time": data["Time"], "day_time": data["DayTime"], "position": player["Pos"],
              "health": player["Health"], "food": player["foodLevel"], "inventory": inventory, "stats": stats,
              "initial_empty_inventory": True}
    if bootstrap:
        counts = Counter({})
        for item in inventory:
            counts[item["item"]] += item["count"]
        expected = Counter({"minecraft:spruce_sapling": 1, "minecraft:spruce_log": 3, "minecraft:dirt": 16,
                            "minecraft:sand": 2, "minecraft:sandstone": 1, "minecraft:stick": 4,
                            "minecraft:wooden_pickaxe": 1, "minecraft:stone_pickaxe": 1,
                            "minecraft:charcoal": 1, "minecraft:torch": 4})
        assert counts == expected, (counts, expected)
        crafted = stats["minecraft:crafted"]
        for key, count in {"spruce_planks": 12, "stick": 8, "crafting_table": 1, "wooden_pickaxe": 1,
                           "stone_pickaxe": 1, "furnace": 1, "torch": 4, "charcoal": 2}.items():
            assert crafted[f"minecraft:{key}"] == count, key
        mined, picked, used = (stats[f"minecraft:{category}"] for category in ("mined", "picked_up", "used"))
        assert mined["minecraft:spruce_log"] == picked["minecraft:spruce_log"] == 8
        assert mined["minecraft:stone"] == picked["minecraft:cobblestone"] == 11
        assert mined["minecraft:grass_block"] + mined["minecraft:dirt"] == picked["minecraft:dirt"] == 16
        assert picked["minecraft:stick"] == 2 and used["minecraft:crafting_table"] == used["minecraft:furnace"] == 1
        tools = {item["item"]: item["tag"] for item in inventory if item["item"].endswith("_pickaxe")}
        assert tools["minecraft:stone_pickaxe"]["Damage"] == 0
        assert tools["minecraft:wooden_pickaxe"]["Damage"] == used["minecraft:wooden_pickaxe"] == 30
        # 8 logs = 3 retained + 3 -> 12 planks + 2 -> charcoal. The twelve
        # planks become one table, one pick, eight sticks and one fuel plank.
        # 8 crafted + 2 gathered sticks = 4 retained + 4 in picks + 1 fuel + 1 torch recipe.
        assert counts["minecraft:spruce_log"] + crafted["minecraft:spruce_planks"] // 4 + 2 == 8
        assert counts["minecraft:stick"] + 4 + 1 + 1 == crafted["minecraft:stick"] + picked["minecraft:stick"]
        assert stats["minecraft:custom"].get("minecraft:deaths", 0) == 0
        report["bootstrap_material_ledger"] = "passed"
    if first_iron:
        saved = nearby_saved_items()
        report["saved_items"] = saved
        counts = Counter()
        for item in inventory:
            counts[item["item"]] += item["count"]
        for drop in saved["dropped_items"]:
            counts[drop["item"]["id"]] += drop["item"]["Count"]
        assert len(saved["starter_furnaces"]) == 1
        furnace = saved["starter_furnaces"][0]
        for item in furnace["Items"]:
            counts[item["id"]] += item["Count"]
        expected = Counter({"minecraft:spruce_sapling":1, "minecraft:spruce_log":3,
                            "minecraft:dirt":16, "minecraft:sand":2, "minecraft:sandstone":1,
                            "minecraft:stick":4, "minecraft:torch":2, "minecraft:wooden_pickaxe":1,
                            "minecraft:stone_pickaxe":1, "minecraft:cobblestone":64,
                            "minecraft:diorite":12, "minecraft:coal":4, "minecraft:iron_ingot":4})
        assert counts == expected, (counts, expected)
        mined, crafted, used = (stats[f"minecraft:{key}"] for key in ("mined", "crafted", "used"))
        assert mined["minecraft:iron_ore"] == mined["minecraft:coal_ore"] == 4
        assert mined["minecraft:stone"] == counts["minecraft:cobblestone"] + 3 + 8 == 75
        assert mined["minecraft:diorite"] == counts["minecraft:diorite"] == 12
        assert crafted["minecraft:charcoal"] == 2 and crafted["minecraft:torch"] == 4
        assert used["minecraft:torch"] + counts["minecraft:torch"] == 4
        assert counts["minecraft:spruce_log"] + crafted["minecraft:spruce_planks"] // 4 + 2 == mined["minecraft:spruce_log"] == 8
        assert counts["minecraft:stick"] + 4 + 1 + 1 == crafted["minecraft:stick"] + 2
        assert furnace["BurnTime"] == furnace["CookTime"] == 0
        assert furnace["Items"] == [{"Slot":2, "id":"minecraft:iron_ingot", "Count":4}]
        all_tools = [{"item":item["item"], "tag":item["tag"]} for item in inventory if item["item"].endswith("_pickaxe")]
        all_tools += [{"item":drop["item"]["id"], "tag":drop["item"].get("tag", {})}
                      for drop in saved["dropped_items"] if drop["item"]["id"].endswith("_pickaxe")]
        tools = {tool["item"]:tool["tag"]["Damage"] for tool in all_tools}
        assert tools["minecraft:stone_pickaxe"] == used["minecraft:stone_pickaxe"] == 84
        assert 84 == mined["minecraft:stone"] - 11 + mined["minecraft:diorite"] + 4 + 4
        assert tools["minecraft:wooden_pickaxe"] == used["minecraft:wooden_pickaxe"] == 30
        report["first_iron_material_ledger"] = "passed across player, ordinary death drops and furnace"
        report["scope"] = "First four naturally mined iron smelted; death-drop recovery and iron tools remain pending"
    if iron_tools:
        counts = Counter()
        for item in inventory:
            counts[item["item"]] += item["count"]
        expected = Counter({"minecraft:cobblestone":56, "minecraft:coal":4, "minecraft:furnace":1,
                            "minecraft:stone_pickaxe":1, "minecraft:dirt":35, "minecraft:diorite":12,
                            "minecraft:torch":2, "minecraft:iron_pickaxe":1, "minecraft:iron_ingot":1,
                            "minecraft:sand":2, "minecraft:stick":2, "minecraft:wooden_pickaxe":1,
                            "minecraft:spruce_sapling":1, "minecraft:spruce_log":2, "minecraft:sandstone":1})
        assert counts == expected, (counts, expected)
        crafted, mined, used = (stats[f"minecraft:{key}"] for key in ("crafted","mined","used"))
        assert crafted["minecraft:iron_pickaxe"] == 1 and crafted["minecraft:iron_ingot"] == mined["minecraft:iron_ore"] == 4
        assert counts["minecraft:iron_ingot"] + 3 == 4
        assert counts["minecraft:spruce_log"] + crafted["minecraft:spruce_planks"]//4 + 2 == mined["minecraft:spruce_log"] == 8
        assert crafted["minecraft:spruce_planks"] == 16 and crafted["minecraft:crafting_table"] == 2
        assert counts["minecraft:stick"] + 2 + 2 + 2 + 1 + 1 == crafted["minecraft:stick"] + 2
        assert mined["minecraft:coal_ore"] == counts["minecraft:coal"] == 4
        assert crafted["minecraft:charcoal"] == 2 and crafted["minecraft:torch"] == 4
        assert used["minecraft:torch"] + counts["minecraft:torch"] == 4
        tools = {item["item"]:item["tag"]["Damage"] for item in inventory if item["item"].endswith("_pickaxe")}
        assert tools == {"minecraft:wooden_pickaxe":30, "minecraft:stone_pickaxe":84, "minecraft:iron_pickaxe":0}
        assert used["minecraft:stone_pickaxe"] == 84 and used.get("minecraft:iron_pickaxe", 0) == 0
        assert stats["minecraft:custom"]["minecraft:deaths"] == 4
        assert stats["minecraft:killed"]["minecraft:skeleton"] == 1
        assert stats["minecraft:killed_by"]["minecraft:skeleton"] == 4
        blocks = saved_blocks([(24,57,41), (29,63,38), (30,63,39), (29,63,36), (29,61,39),
                               (35,51,38), (26,51,56)])
        assert blocks == {"24,57,41":"minecraft:crafting_table", "29,63,38":"minecraft:air",
                          "30,63,39":"minecraft:air", "29,63,36":"minecraft:cobblestone",
                          "29,61,39":"minecraft:cobblestone", "35,51,38":"minecraft:wall_torch",
                          "26,51,56":"minecraft:wall_torch"}, blocks
        assert mined["minecraft:stone"] - 3 - 8 == counts["minecraft:cobblestone"] + 2 + 6 == 64
        assert used["minecraft:cobblestone"] == 8
        saved = nearby_saved_items()
        assert not saved["starter_furnaces"]
        originals = set(expected) - {"minecraft:dirt"}
        assert not [drop for drop in saved["dropped_items"] if drop["item"]["id"] in originals]
        report["scope"] = "Naturally mined iron crafted into the first iron pickaxe; full Survival progression remains open"
        report["iron_tools_material_ledger"] = "passed; all original death-drop stacks recovered"
        report["environmental_changes"] = {"cobblestone_lost":6, "crafting_table_lost":1, "explosion_dirt_salvaged":19,
                                           "note":"One wrong-tool cobblestone break and a nearby Creeper explosion; no save rollback or grants"}
        report["saved_blocks"] = blocks
        report["saved_items"] = saved
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    phase = parser.add_mutually_exclusive_group()
    phase.add_argument("--bootstrap", action="store_true")
    phase.add_argument("--first-iron", action="store_true")
    phase.add_argument("--iron-tools", action="store_true")
    parser.add_argument("--record", action="store_true", help="Preserve the first phase report in the ignored evidence directory")
    arguments = parser.parse_args()
    report = audit(arguments.bootstrap, arguments.first_iron, arguments.iron_tools)
    if arguments.record:
        assert arguments.bootstrap or arguments.first_iron or arguments.iron_tools, "Recording requires a phase ledger"
        name = "bootstrap" if arguments.bootstrap else "first-iron" if arguments.first_iron else "iron-tools"
        target = driver.RUN / f"evidence/{name}-audit.json"
        if target.exists():
            raise RuntimeError("Preserve the existing phase audit instead of overwriting it")
        driver.write(target, report)
    print(json.dumps(report, indent=2))
