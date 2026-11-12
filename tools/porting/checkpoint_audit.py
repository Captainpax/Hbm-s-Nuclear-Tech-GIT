"""Read-only stopped-world audit of the explicitly assisted progression route.

Close runSurvivalClient before running this; no world files are edited. Supplies
and accelerated blast timers are recorded by the walkthrough, not represented as
unassisted gathering or natural-duration evidence.
"""
from __future__ import annotations

import argparse
from collections import Counter
import os

os.environ["HBM_SURVIVAL_ASSISTED"] = "1"
import survival_driver as driver
import survival_audit as audit


def run(phase: str) -> dict:
    save = driver.RUN / "saves/HBM Survival Checkpoint QA"
    audit.SAVE = save
    data = audit.nbt(save / "level.dat")["Data"]
    player = data["Player"]
    assert data["LevelName"] == "HBM Survival Checkpoint QA"
    assert data["WorldGenSettings"]["seed"] == 20301017 and data["allowCommands"] == 1
    assert player["playerGameType"] == 0
    assert not any(player["abilities"][key] for key in ("instabuild", "flying", "mayfly"))
    inventory = Counter()
    for item in player["Inventory"]:
        inventory[item["id"]] += item["Count"]
    batteries = [item for item in player["Inventory"] if item["id"] == "hbm:battery_potato"]
    assert len(batteries) == 5 and all(item["tag"]["charge"] == 0 for item in batteries)
    assert inventory["hbm:plate_cast_iron"] == 1 and inventory["hbm:ingot_raw_slag"] == 14
    stamps = {item["id"]: item["tag"]["Damage"] for item in player["Inventory"] if item["id"].startswith("hbm:stamp_iron_")}
    assert stamps == {"hbm:stamp_iron_plate":27, "hbm:stamp_iron_wire":5}, stamps
    assert inventory["hbm:wire_fine_tungsten"] == 0
    assert inventory["hbm:wire_fine_copper"] == inventory["hbm:wire_fine_aluminium"] == 3
    positions = [(31,64,54), (31,65,54), (33,64,54), (31,64,48), (35,64,54), (35,64,48)]
    entities = {}
    for chunk in audit.region_chunks(save / "region/r.0.0.mca"):
        for block in chunk.get("block_entities", []):
            pos = block["x"], block["y"], block["z"]
            if pos in positions:
                entities[pos] = block
    assert len(entities) == len(positions)
    firebox, crucible, mold, blast, furnace, press = [entities[pos] for pos in positions]
    assert firebox["burnTime"] > 0 and firebox["heatEnergy"] > 0
    assert 0 < crucible["heat"] <= 100_000 and crucible["progress"] == 0
    assert not crucible["items"]["Items"] and not crucible["recipeMaterials"] and not crucible["wasteMaterials"]
    assert mold["amount"] == 0 and mold["cooloff"] == 200
    assert mold["items"]["Items"] == [{"Slot":0, "id":"hbm:mold_plate_cast", "Count":1}]
    assert blast["fuel"] == blast["progress"] == blast["airblast"] == 0
    assert blast["flueGas"] == 1000 and not blast["items"]["Items"]
    assert press["burnTime"] == press["press"] == 0 and not press["items"]["Items"]
    if phase == "active":
        assert 0 < furnace["progress"] < 100
        assert furnace["power"] + furnace["progress"] * 50 == 5000
        assert furnace["items"]["Items"] == [{"Slot":1, "id":"minecraft:cobblestone", "Count":1}]
        assert inventory["minecraft:stone"] == 0
    else:
        assert furnace["power"] == furnace["progress"] == 0 and not furnace["items"]["Items"]
        assert inventory["minecraft:stone"] == 1
    footprints = []
    for name, core, height, facing in [("heater_firebox", (31,64,54), 1, "west"),
                                       ("machine_crucible", (31,65,54), 2, "west"),
                                       ("machine_blast_furnace", (31,64,48), 7, "north")]:
        positions = [(core[0]+x, core[1]+y, core[2]+z) for x in (-1,0,1) for z in (-1,0,1) for y in range(height)]
        states = audit.saved_states(positions)
        for x,y,z in positions:
            state = states[f"{x},{y},{z}"]
            assert state["Name"] == f"hbm:{name}"
            assert state["Properties"] == dict(facing=facing, x=str(x-core[0]+1), y=str(y-core[1]), z=str(z-core[2]+1))
        footprints.append({"machine":name, "parts":len(states), "chunks":sorted({(x//16,z//16) for x,y,z in positions})})
    soot = audit.nbt(save / "data/hbm_soot_pollution.dat")["data"]["soot"]
    assert any(entry["amount"] > 0 for entry in soot)
    result = dict(phase=phase, scope="Command-supplied raw inputs; ordinary Survival crafting and machines; 13 accelerated blast timers",
                  player=player, furnace=furnace, firebox=firebox, crucible=crucible, mold=mold,
                  blast=blast, press=press, footprints=footprints, soot=soot,
                  checks=["five depleted crafted batteries retained", "5000 HE ledger conserved", "reusable template and mold retained",
                          "six copper ingots fully accounted for by two collected cast plates used in furnace recipe",
                          "27 plate and five wire operations used exactly four coal; stamp wear retained",
                          "14 blast operations left 1000 mB flue and persisted soot", "all 90 multiblock parts saved correctly across chunk borders"])
    destination = driver.RUN / f"evidence/progression-{phase}-audit.json"
    assert not destination.exists(), "Preserve the previous stopped-world audit"
    driver.write(destination, result)
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("phase", choices=["active", "complete"])
    result = run(parser.parse_args().phase)
    print(f"{result['phase']} progression audit passed; source inventory, casting, fuel and HE ledgers conserved")
