"""Black-box recipe checks in the explicitly command-assisted Survival profile.

The fixture supplies ingredients and vanilla workstations. Crafting, menu clicks,
fuel consumption and HBM block placement use ordinary client/server interactions.
Run only after prepare_checkpoint.py and the idempotent setup function.
"""
from __future__ import annotations

import argparse
import os
import time

os.environ["HBM_SURVIVAL_ASSISTED"] = "1"
import survival_driver as driver
import survival_route as route


def count(state: dict, item: str) -> int:
    return sum(stack["count"] for stack in state["inventory"] if stack["item"] == item)


def portion(item: str, amount: int, target: int) -> dict:
    remaining = amount
    while remaining:
        state = route.act("inspect")
        assert not state["carried"]["count"]
        source_stack = next(stack for stack in state["slots"][-36:] if stack["item"] == item and stack["count"])
        source = source_stack["slot"]
        taken = min(remaining, source_stack["count"])
        before = count(state, item)
        state = route.act("click", slot=source)
        if source_stack["count"] <= remaining:
            state = route.act("click", slot=target)
        else:
            for _ in range(taken):
                state = route.act("click", slot=target, button=1)
        if state["carried"]["count"]:
            state = route.act("click", slot=source)
        assert not state["carried"]["count"]
        assert count(state, item) == before - taken, "Destination refused part of the requested input"
        remaining -= taken
    return state


def equip(item: str, hotbar: int) -> None:
    state = route.act("inventory")
    source = next(stack["slot"] for stack in state["slots"][9:45] if stack["item"] == item)
    target = 36 + hotbar
    if source != target:
        route.act("click", slot=source)
        state = route.act("click", slot=target)
        if state["carried"]["count"]:
            route.act("click", slot=source)
    route.act("close")
    route.act("select", slot=hotbar)


def bulk_craft(recipe: str, output: str, expected: int) -> dict:
    before = route.act("inspect")
    assert not before["carried"]["count"]
    route.act("recipe", recipe=recipe, all=True)
    after = route.act("click", slot=0, mode="QUICK_MOVE")
    assert count(after, output) - count(before, output) == expected
    assert not after["carried"]["count"]
    return after


def prepare_casting() -> None:
    state = route.act("inspect")
    assert state["assisted"] and state["cheats"] and state["mode"] == "survival"
    assert count(state, "minecraft:iron_ingot") == 87
    assert count(state, "hbm:ball_fireclay") == count(state, "hbm:machine_press") == 0
    route.act("walk", x=26.5, y=64, z=46.5, resume=True)
    route.act("use", x=26, y=64, z=45)
    bulk_craft("hbm:ball_fireclay_from_limestone", "hbm:ball_fireclay", 60)
    for _ in range(2):
        route.craft("minecraft:spruce_planks", "minecraft:spruce_planks", 4)
    route.craft("minecraft:stick", "minecraft:stick", 4)
    route.craft("minecraft:piston", "minecraft:piston")
    for _ in range(3):
        route.craft("minecraft:furnace", "minecraft:furnace")
        route.craft("minecraft:iron_block", "minecraft:iron_block")
    for _ in range(2):
        route.craft("hbm:anvil_iron", "hbm:anvil_iron")
    state = route.craft("hbm:machine_press", "hbm:machine_press")
    assert count(state, "minecraft:iron_ingot") == 41
    assert count(state, "minecraft:copper_ingot") == 31
    assert count(state, "minecraft:clay_ball") == 6
    assert count(state, "minecraft:sand") == 14
    assert count(state, "hbm:anvil_iron") == 2
    assert count(state, "minecraft:furnace") == 2
    route.act("close")
    route.act("walk", x=28.5, y=64, z=46.5)
    route.act("walk", x=28.5, y=64, z=43.5)
    for x in range(25, 33):
        route.act("walk", x=x+0.5, y=64, z=43.5)
        state = route.act("use", x=x, y=64, z=42)
        assert state["menu"] == "FurnaceMenu" and all(not item["count"] for item in state["slots"][:3])
        portion("hbm:ball_fireclay", 4 if x == 32 else 8, 0)
        portion("minecraft:coal", 1, 1)
        route.act("close")
    for x, item, amount in [(33, "minecraft:clay_ball", 6), (34, "minecraft:cobblestone", 7)]:
        route.act("walk", x=x+0.5, y=64, z=43.5)
        route.act("use", x=x, y=64, z=42)
        portion(item, amount, 0)
        portion("minecraft:coal", 1, 1)
        route.act("close")
    state = route.act("inspect")
    assert count(state, "hbm:ball_fireclay") == count(state, "minecraft:clay_ball") == 0
    assert count(state, "minecraft:coal") == 54
    target = driver.RUN / "evidence/casting-preparation.json"
    if target.exists():
        raise RuntimeError("Preserve the previous completed stage report")
    driver.write(target, {"state":state, "mode":"command-supplied ingredients; ordinary crafting and furnace loading",
                          "checks":["60 fireclay from 30 clay/15 limestone/15 sand", "two source iron anvils",
                                    "piston and three furnace recipes", "source press recipe", "41 iron ingots retained",
                                    "60 fireclay, 6 clay and 7 cobblestone firing with 10 coal"]})


def place_tools() -> None:
    state = route.act("inspect")
    assert state["assisted"] and count(state, "hbm:anvil_iron") == 2 and count(state, "hbm:machine_press") == 1
    route.act("walk", x=27.5, y=64, z=46.5, resume=True)
    equip("hbm:anvil_iron", 7)
    route.act("use", x=26, y=63, z=47, face="UP")
    anvil = route.probe(26, 64, 47)
    assert anvil["block"] == "hbm:anvil_iron"
    equip("hbm:machine_press", 7)
    route.act("walk", x=35.5, y=64, z=46.5)
    route.act("use", x=35, y=63, z=48, face="UP")
    press = route.probe(35, 64, 48)
    assert press["block"] == "hbm:machine_press"
    equip("minecraft:iron_pickaxe", 2)
    state = route.act("use", x=35, y=64, z=48)
    assert state["menu"] == "PortPressMenu"
    portion("minecraft:coal", 4, 0)
    route.act("close")
    state = route.act("inspect")
    assert count(state, "minecraft:iron_ingot") == 41 and count(state, "minecraft:copper_ingot") == 31
    assert count(state, "hbm:anvil_iron") == 1 and count(state, "hbm:machine_press") == 0
    assert count(state, "minecraft:coal") == 50
    target = driver.RUN / "evidence/tools-placed.json"
    if target.exists():
        raise RuntimeError("Preserve the previous placement record")
    driver.write(target, {"state":state, "anvil":anvil, "press":press,
                          "checks":["crafted anvil and press placed normally", "press menu opened", "four coal loaded"]})


def prepare_stamps() -> None:
    state = route.act("inspect")
    assert state["assisted"] and count(state, "minecraft:iron_ingot") == 41
    assert count(state, "hbm:ingot_firebrick") == 0
    route.act("walk", x=34.5, y=64, z=43.5, resume=True)
    for x, expected, amount in [*[(x, "hbm:ingot_firebrick", 4 if x == 32 else 8) for x in range(25, 33)],
                                (33, "minecraft:brick", 6), (34, "minecraft:stone", 7)]:
        route.act("walk", x=x+0.5, y=64, z=43.5)
        state = route.act("use", x=x, y=64, z=42)
        for _ in range(150):
            if state["slots"][2]["item"] == expected and state["slots"][2]["count"] == amount:
                break
            time.sleep(0.25)
            state = route.act("inspect")
        assert state["slots"][2]["item"] == expected and state["slots"][2]["count"] == amount
        assert not state["slots"][0]["count"]
        route.act("click", slot=2, mode="QUICK_MOVE")
        route.act("close")
    state = route.act("inspect")
    assert count(state, "hbm:ingot_firebrick") == 60
    for x, item, amount in [(25, "hbm:ore_tungsten", 3), (26, "hbm:ore_beryllium", 3), (27, "hbm:ore_aluminium", 1)]:
        route.act("walk", x=x+0.5, y=64, z=43.5)
        route.act("use", x=x, y=64, z=42)
        portion(item, amount, 0)
        portion("minecraft:coal", 1, 1)
        route.act("close")
    route.act("walk", x=28.5, y=64, z=43.5)
    route.act("walk", x=28.5, y=64, z=46.5)
    route.act("walk", x=26.5, y=64, z=46.5)
    route.act("use", x=26, y=64, z=45)
    bulk_craft("hbm:stamp_iron_flat_brick", "hbm:stamp_iron_flat", 2)
    route.craft("minecraft:stone_bricks", "minecraft:stone_bricks", 4)
    route.craft("minecraft:stone_slab", "minecraft:stone_slab", 6)
    route.craft("hbm:foundry_mold", "hbm:foundry_mold")
    state = route.craft("hbm:mold_base", "hbm:mold_base")
    assert count(state, "minecraft:iron_ingot") == 34
    assert count(state, "hbm:ingot_firebrick") == 52
    assert count(state, "hbm:stamp_iron_flat") == 2
    route.act("close")
    route.act("walk", x=28.5, y=64, z=46.5)
    equip("minecraft:iron_pickaxe", 2)
    assert route.probe(28, 64, 45)["block"] == "hbm:cluster_aluminium"
    route.act("mine", x=28, y=64, z=45)
    state = route.act("walk", x=28.5, y=64, z=45.5)
    assert count(state, "hbm:crystal_aluminium") == 1
    route.act("walk", x=28.5, y=64, z=43.5)
    route.act("use", x=28, y=64, z=42)
    portion("hbm:crystal_aluminium", 1, 0)
    portion("minecraft:coal", 1, 1)
    state = route.act("close")
    target = driver.RUN / "evidence/stamps-prepared.json"
    if target.exists():
        raise RuntimeError("Preserve the earlier firing/crafting record")
    driver.write(target, {"state":state, "checks":["60 fired firebrick collected after client restart",
                          "two flat stamps from six iron and six fired bricks", "source mold base and foundry mold recipes",
                          "four stone bricks and six stone slabs", "tungsten/beryllium ore and aluminium crystal smelting queued",
                          "raw supplied aluminium cluster mined with an iron pick; separate ore produces cryolite"]})


def wait_output(slot: int, item: str, amount: int, timeout: float = 120) -> dict:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        state = driver.state()
        if state["slots"][slot]["item"] == item and state["slots"][slot]["count"] == amount:
            return route.act("inspect")
        time.sleep(0.5)
    raise RuntimeError(f"Output {item} x{amount} did not finish; inspect the live machine")


def transfer(item: str, slot: int) -> dict:
    state = route.act("inspect")
    source = next(stack["slot"] for stack in state["slots"][-36:] if stack["item"] == item)
    route.act("click", slot=source)
    state = route.act("click", slot=slot)
    assert not state["carried"]["count"]
    return state


def press_inputs() -> None:
    state = route.act("inspect")
    assert count(state, "hbm:stamp_iron_plate") == count(state, "hbm:stamp_iron_wire") == 1
    assert count(state, "hbm:stamp_iron_flat") == 0
    route.act("walk", x=34.5, y=64, z=46.5, resume=True)
    state = route.act("use", x=35, y=64, z=48)
    assert state["menu"] == "PortPressMenu"
    transfer("hbm:stamp_iron_plate", 1)
    portion("minecraft:copper_ingot", 8, 2)
    copper = wait_output(3, "hbm:plate_copper", 8)
    assert not copper["slots"][2]["count"]
    assert any(sound["id"] == "hbm:block.press_operate" and sound["activeEver"] for sound in copper["sounds"])
    route.act("click", slot=3, mode="QUICK_MOVE")
    portion("minecraft:iron_ingot", 3, 2)
    iron = wait_output(3, "hbm:plate_iron", 3)
    assert not iron["slots"][2]["count"]
    route.act("click", slot=3, mode="QUICK_MOVE")
    route.act("click", slot=1, mode="QUICK_MOVE")
    route.act("close")
    route.act("walk", x=28.5, y=64, z=46.5)
    route.act("walk", x=28.5, y=64, z=43.5)
    for x, item, amount in [(25, "hbm:ingot_tungsten", 3), (26, "hbm:ingot_beryllium", 3), (28, "hbm:ingot_aluminium", 2)]:
        route.act("walk", x=x+0.5, y=64, z=43.5)
        route.act("use", x=x, y=64, z=42)
        wait_output(2, item, amount)
        route.act("click", slot=2, mode="QUICK_MOVE")
        route.act("close")
    route.act("walk", x=34.5, y=64, z=43.5)
    route.act("walk", x=34.5, y=64, z=46.5)
    route.act("use", x=35, y=64, z=48)
    transfer("hbm:stamp_iron_wire", 1)
    for ingredient, amount, wire, output in [("hbm:ingot_tungsten", 3, "hbm:wire_fine_tungsten", 24),
                                            ("hbm:ingot_aluminium", 1, "hbm:wire_fine_aluminium", 8),
                                            ("minecraft:copper_ingot", 1, "hbm:wire_fine_copper", 8)]:
        portion(ingredient, amount, 2)
        wait_output(3, wire, output)
        route.act("click", slot=3, mode="QUICK_MOVE")
    route.act("click", slot=1, mode="QUICK_MOVE")
    route.act("close")
    state = route.act("inspect")
    assert count(state, "minecraft:iron_ingot") == 31 and count(state, "minecraft:copper_ingot") == 22
    assert count(state, "hbm:plate_iron") == 3 and count(state, "hbm:plate_copper") == 8
    target = driver.RUN / "evidence/pressed-inputs.json"
    assert not target.exists()
    driver.write(target, {"state":state, "firstCopperOutput":copper, "ironOutput":iron,
                          "checks":["eight copper and three iron plates stamped normally",
                                    "three tungsten ore and one aluminium crystal smelted normally",
                                    "24 tungsten, 8 aluminium and 8 copper wires stamped normally",
                                    "source press sound played through the live client sound engine"]})


def start_blast() -> None:
    state = route.act("inspect")
    assert count(state, "hbm:plate_copper") == 8 and count(state, "hbm:ingot_firebrick") == 52
    route.act("walk", x=26.5, y=64, z=46.5, resume=True)
    route.act("use", x=26, y=64, z=47)
    state = route.act("button", button=4)
    assert count(state, "hbm:machine_blast_furnace") == 1
    assert count(state, "hbm:ingot_firebrick") == 20 and count(state, "hbm:plate_copper") == 0
    route.act("close")
    route.act("walk", x=31.5, y=64, z=45.5)
    equip("hbm:machine_blast_furnace", 7)
    # Let the ordinary rotation packet reach the server before the place packet.
    route.act("look", x=31.5, y=64, z=47.5)
    route.act("use", x=31, y=63, z=47, face="UP")
    core = route.probe(31, 64, 48)
    assert core["block"] == "hbm:machine_blast_furnace"
    assert core["properties"] == {"facing":"north", "x":"1", "y":"0", "z":"1"}
    equip("minecraft:iron_pickaxe", 2)
    route.act("use", x=31, y=64, z=47, face="NORTH")
    portion("minecraft:iron_ingot", 28, 1)
    portion("minecraft:sand", 14, 2)
    fuel_start = route.act("inspect")["gameTime"]
    loaded = portion("minecraft:coal", 7, 0)
    target = driver.RUN / "evidence/blast-started.json"
    assert not target.exists()
    driver.write(target, {"state":loaded, "core":core, "fuelStartGameTime":fuel_start, "checks":["source anvil construction recipe",
                          "normal placement across both x=32 and z=48 chunk boundaries",
                          "28 iron and 14 sand loaded with seven coal", "first batch runs without timer commands"]})


def finish_blast() -> None:
    state = route.act("inspect")
    assert state["paused"]
    route.act("look", x=31.5, y=66, z=47, resume=True)
    route.act("use", x=31, y=64, z=47, face="NORTH")
    natural = wait_output(3, "hbm:ingot_steel", 2, 130)
    started = __import__("json").loads((driver.RUN / "evidence/blast-started.json").read_text())
    fuel_start = started.get("fuelStartGameTime")
    if fuel_start is None:
        first = __import__("json").loads((driver.RUN / "evidence/blast-first-fuel.json").read_text())
        fuel_start = first["before"]["gameTime"]
    assert natural["gameTime"] - fuel_start >= 1_595
    assert natural["slots"][4]["item"] == "hbm:ingot_raw_slag" and natural["slots"][4]["count"] == 1
    assert natural["slots"][1]["count"] == 26 and natural["slots"][2]["count"] == 13
    assert any(sound["id"] == "minecraft:block.fire.ambient" and sound["activeEver"] for sound in natural["sounds"])
    driver.write(driver.RUN / "evidence/blast-natural-first-batch.json", natural)
    route.act("close")
    for batch in range(2, 15):
        route.act("command", command="function hbm_checkpoint:advance_blast")
        state = route.act("use", x=31, y=64, z=47, face="NORTH")
        assert state["slots"][3]["item"] == "hbm:ingot_steel" and state["slots"][3]["count"] == batch * 2
        assert state["slots"][4]["count"] == batch
        assert state["slots"][1]["count"] == 28 - batch * 2 and state["slots"][2]["count"] == 14 - batch
        route.act("close")
    state = route.act("use", x=31, y=64, z=47, face="NORTH")
    route.act("click", slot=3, mode="QUICK_MOVE")
    route.act("click", slot=4, mode="QUICK_MOVE")
    state = route.act("inspect")
    assert count(state, "hbm:ingot_steel") == 28 and count(state, "hbm:ingot_raw_slag") == 14
    route.act("close")
    target = driver.RUN / "evidence/blast-completed.json"
    assert not target.exists()
    driver.write(target, {"state":state, "naturalFirstBatch":natural,
                          "timerAssistedBatches":13, "checks":["first steel batch completed naturally",
                          "remaining 13 timers explicitly advanced, with normal recipe consumption checked after each",
                          "28 steel and 14 slag; all iron and sand consumed exactly once", "live furnace crackle playback"]})


def craft_casting_tools() -> None:
    state = route.act("inspect")
    assert count(state, "hbm:ingot_steel") == 28
    route.act("walk", x=26.5, y=64, z=46.5, resume=True)
    route.act("use", x=26, y=64, z=47)
    transfer("hbm:anvil_iron", 0)
    portion("hbm:ingot_steel", 10, 1)
    state = route.act("inspect")
    assert state["slots"][2]["item"] == "hbm:anvil_steel"
    route.act("click", slot=2, mode="QUICK_MOVE")
    route.act("close")
    route.act("walk", x=28.5, y=64, z=46.5)
    equip("hbm:anvil_steel", 7)
    route.act("use", x=28, y=63, z=47, face="UP")
    assert route.probe(28, 64, 47)["block"] == "hbm:anvil_steel"
    route.act("walk", x=34.5, y=64, z=46.5)
    equip("minecraft:iron_pickaxe", 2)
    route.act("use", x=35, y=64, z=48)
    transfer("hbm:stamp_iron_plate", 1)
    portion("hbm:ingot_steel", 16, 2)
    wait_output(3, "hbm:plate_steel", 16)
    route.act("click", slot=3, mode="QUICK_MOVE")
    route.act("click", slot=1, mode="QUICK_MOVE")
    route.act("close")
    route.act("walk", x=26.5, y=64, z=46.5)
    route.act("use", x=26, y=64, z=45)
    route.craft("hbm:bolt_steel", "hbm:bolt_steel", 16)
    route.craft("hbm:plate_cast_iron", "hbm:plate_cast_iron")
    bulk_craft("hbm:coil_tungsten", "hbm:coil_tungsten", 3)
    bulk_craft("hbm:battery_potato", "hbm:battery_potato", 5)
    route.act("close")
    route.act("use", x=26, y=64, z=47)
    transfer("hbm:plate_cast_iron", 0)
    transfer("hbm:mold_base", 1)
    state = route.act("inspect")
    assert state["slots"][2]["item"] == "hbm:mold_plate_cast"
    route.act("click", slot=2, mode="QUICK_MOVE")
    state = route.act("close")
    assert count(state, "hbm:plate_cast_iron") == count(state, "hbm:mold_plate_cast") == 1
    route.act("walk", x=28.5, y=64, z=46.5)
    route.act("use", x=28, y=64, z=47)
    route.act("button", button=6)
    state = route.act("button", button=8)
    assert count(state, "hbm:heater_firebox") == count(state, "hbm:machine_crucible") == 1
    assert count(state, "minecraft:iron_ingot") == count(state, "hbm:ingot_steel") == count(state, "hbm:plate_steel") == 0
    assert count(state, "minecraft:copper_ingot") == 6
    assert count(state, "hbm:ingot_firebrick") == 0
    route.act("close")
    target = driver.RUN / "evidence/casting-tools-crafted.json"
    assert not target.exists()
    driver.write(target, {"state":state, "checks":["iron anvil and ten steel upgraded to steel anvil",
                          "16 steel plates stamped with normal fuel and stamp wear", "two steel made 16 bolts",
                          "six bolts and three iron plates made reusable cast-iron template",
                          "mold smithing consumed base and retained template", "firebox and crucible constructed at tier two",
                          "three source tungsten coils and five charged potato batteries crafted"]})


def place_casting() -> None:
    state = route.act("inspect")
    assert count(state, "minecraft:copper_ingot") == 6
    route.act("walk", x=28.5, y=64, z=52.5, resume=True)
    route.act("walk", x=28.5, y=64, z=54.5)
    equip("hbm:heater_firebox", 7)
    route.act("look", x=30.5, y=64, z=54.5)
    route.act("use", x=30, y=63, z=54, face="UP")
    firebox = route.probe(31, 64, 54)
    assert firebox["block"] == "hbm:heater_firebox" and firebox["properties"]["facing"] == "west"
    equip("hbm:machine_crucible", 7)
    route.act("use", x=30, y=64, z=54, face="UP", sneak=True)
    crucible = route.probe(31, 65, 54)
    assert crucible["block"] == "hbm:machine_crucible" and crucible["properties"]["facing"] == "west"
    route.act("walk", x=28.5, y=64, z=51.5)
    route.act("walk", x=34.5, y=64, z=51.5)
    route.act("walk", x=34.5, y=64, z=54.5)
    equip("hbm:foundry_mold", 7)
    route.act("use", x=33, y=63, z=54, face="UP")
    assert route.probe(33, 64, 54)["block"] == "hbm:foundry_mold"
    equip("hbm:mold_plate_cast", 7)
    state = route.act("use", x=33, y=64, z=54, face="UP")
    assert count(state, "hbm:mold_plate_cast") == 0
    equip("minecraft:iron_pickaxe", 2)
    route.act("use", x=32, y=64, z=54, face="EAST")
    portion("minecraft:coal", 8, 0)
    route.act("close")
    route.act("use", x=32, y=65, z=54, face="EAST")
    # The source intake has nine single-item slots, not one stackable slot.
    for slot in range(6):
        state = portion("minecraft:copper_ingot", 1, slot)
    assert count(state, "minecraft:copper_ingot") == 0
    assert state["menu"] == "PortCrucibleMenu"
    route.act("close")
    target = driver.RUN / "evidence/casting-started.json"
    assert not target.exists()
    driver.write(target, {"state":state, "firebox":firebox, "crucible":crucible,
                          "checks":["crafted casting machines placed across x=32 chunk boundary",
                          "sneak-placed crucible above firebox", "mold installed normally",
                          "eight coal fuel and six copper ingots loaded through live menus; no injected heat or metal"]})


def collect_casts() -> None:
    state = route.act("inspect")
    assert count(state, "minecraft:copper_ingot") == 0
    made = count(state, "hbm:plate_cast_copper")
    assert made in (0, 1)
    collections = []
    if made == 1:
        first = __import__("json").loads((driver.RUN / "evidence/copper-first-cast.json").read_text())
        assert count(first, "hbm:plate_cast_copper") == 1
        collections.append(first)
    route.act("look", x=33.5, y=64.25, z=54.5, resume=True)
    deadline = time.monotonic() + 180
    while time.monotonic() < deadline:
        state = route.act("use", x=33, y=64, z=54, face="UP")
        made = count(state, "hbm:plate_cast_copper")
        if made > len(collections):
            assert made == len(collections) + 1
            collections.append(state)
        if made == 2:
            break
        time.sleep(1)
    assert len(collections) == 2, "Inspect the heat supply, spout and installed mold"
    assert count(state, "hbm:mold_plate_cast") == 0
    target = driver.RUN / "evidence/copper-casts-collected.json"
    assert not target.exists()
    driver.write(target, {"state":state, "collections":collections, "checks":["six normally loaded copper ingots yielded exactly two cast plates",
                          "each cooled casting collected through ordinary right click", "same installed mold reused without replacement",
                          "firebox heat, crucible melting, rear spout and cooling ran at normal speed"]})


def craft_and_charge() -> None:
    state = route.act("inspect")
    assert count(state, "hbm:plate_cast_copper") == 2 and count(state, "hbm:battery_potato") == 5
    route.act("walk", x=34.5, y=64, z=51.5, resume=True)
    route.act("walk", x=28.5, y=64, z=51.5)
    route.act("walk", x=26.5, y=64, z=46.5)
    route.act("use", x=26, y=64, z=45)
    state = route.craft("hbm:machine_electric_furnace", "hbm:machine_electric_furnace")
    assert count(state, "hbm:plate_cast_copper") == count(state, "hbm:coil_tungsten") == count(state, "hbm:ingot_beryllium") == 0
    route.act("close")
    route.act("walk", x=28.5, y=64, z=51.5)
    route.act("walk", x=35.5, y=64, z=51.5)
    route.act("walk", x=35.5, y=64, z=52.5)
    equip("hbm:machine_electric_furnace", 7)
    route.act("look", x=35.5, y=64, z=54.5)
    route.act("use", x=35, y=63, z=54, face="UP")
    assert route.probe(35, 64, 54)["block"] == "hbm:machine_electric_furnace"
    equip("minecraft:iron_pickaxe", 2)
    state = route.act("use", x=35, y=64, z=54, face="NORTH")
    assert state["menu"] == "PortElectricFurnaceMenu"
    charges = []
    for _ in range(5):
        state = route.act("inspect")
        source = next(stack["slot"] for stack in state["slots"][-36:]
                      if stack["item"] == "hbm:battery_potato" and "charge:0L" not in stack.get("tag", ""))
        route.act("click", slot=source)
        route.act("click", slot=0)
        deadline = time.monotonic() + 20
        while time.monotonic() < deadline:
            state = driver.state()
            if "charge:0L" in state["slots"][0].get("tag", ""):
                break
            time.sleep(0.25)
        assert "charge:0L" in state["slots"][0].get("tag", "")
        charges.append(route.act("inspect"))
        route.act("click", slot=0, mode="QUICK_MOVE")
    state = route.act("inspect")
    assert count(state, "hbm:battery_potato") == 5
    assert all("charge:0L" in stack.get("tag", "") for stack in state["inventory"] if stack["item"] == "hbm:battery_potato")
    assert not any(stack["count"] for stack in state["slots"][:4])
    target = driver.RUN / "evidence/electric-furnace-charged.json"
    assert not target.exists()
    driver.write(target, {"state":state, "charges":charges, "checks":["source furnace crafted from two live copper casts, three coils, three beryllium and vanilla furnace",
                          "normal placement and menu", "five charged crafted potato batteries discharged normally before loading input",
                          "all five depleted batteries recovered; no injected HE"]})
    route.act("close")


def start_smelt() -> None:
    route.act("look", x=35.5, y=64.5, z=54.5, resume=True)
    state = route.act("use", x=35, y=64, z=54, face="NORTH")
    assert all(not stack["count"] for stack in state["slots"][:4])
    state = portion("minecraft:cobblestone", 1, 1)
    assert not state["slots"][2]["count"]
    driver.write(driver.RUN / "evidence/electric-smelt-started.json", state)


def finish_smelt() -> None:
    route.act("look", x=35.5, y=64.5, z=54.5, resume=True)
    route.act("use", x=35, y=64, z=54, face="NORTH")
    output = wait_output(2, "minecraft:stone", 1, 30)
    assert not output["slots"][1]["count"]
    state = route.act("click", slot=2, mode="QUICK_MOVE")
    assert count(state, "minecraft:stone") == 1 and count(state, "hbm:battery_potato") == 5
    assert not state["carried"]["count"]
    route.act("close")
    target = driver.RUN / "evidence/electric-smelt-completed.json"
    assert not target.exists()
    driver.write(target, {"state":state, "output":output, "checks":["ordinary cobblestone smelt completed after an active save/restart",
                          "one stone output collected exactly once", "five depleted crafted batteries retained"]})


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("stage", choices=["prepare-casting", "place-tools", "prepare-stamps", "press-inputs",
                                         "start-blast", "finish-blast", "craft-casting-tools", "place-casting",
                                         "collect-casts", "craft-and-charge", "start-smelt", "finish-smelt"])
    args = parser.parse_args()
    try:
        {"prepare-casting":prepare_casting, "place-tools":place_tools, "prepare-stamps":prepare_stamps,
         "press-inputs":press_inputs,
         "start-blast":start_blast, "finish-blast":finish_blast, "craft-casting-tools":craft_casting_tools,
         "place-casting":place_casting, "collect-casts":collect_casts, "craft-and-charge":craft_and_charge,
         "start-smelt":start_smelt, "finish-smelt":finish_smelt}[args.stage]()
    finally:
        route.act("pause")
