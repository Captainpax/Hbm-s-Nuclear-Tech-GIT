"""Small operator helpers for the ordinary-action Survival client driver.

Terrain reads select targets; all excavation and travel still use normal client
actions. Unsafe floors, wrong tools and hazardous ores stop the route.
"""
from __future__ import annotations

import json
import math
from collections import Counter

import survival_driver as driver

HAZARDS = ("uranium", "thorium", "asbestos", "schrabidium", "gas_shale")
LIQUIDS = {"minecraft:water", "minecraft:lava"}
AIR = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"}


def act(action: str, **fields) -> dict:
    identifier = driver.submit(action, **fields)
    result = driver.result(identifier)
    inventory, before = Counter(), Counter()
    for item in result["inventory"]: inventory[item["item"]] += item["count"]
    for item in result.get("before", {}).get("inventory", []): before[item["item"]] += item["count"]
    changes = {key:inventory[key]-before[key] for key in inventory.keys()|before.keys() if inventory[key]!=before[key]}
    if action != "probe" or not result.get("ok"):
        print(json.dumps({"id": identifier, "action": action, **fields, "ok": result.get("ok"),
                          "position": [round(result[key], 2) for key in ("x", "y", "z")],
                          "health": result["health"], "food":result["food"], "inventory_delta":changes}), flush=True)
    if not result.get("ok"):
        raise RuntimeError(result.get("error", "Client rejected action"))
    return result


def probe(x: int, y: int, z: int) -> dict:
    return act("probe", x=x, y=y, z=z)["observation"]


def block_pos(block: dict) -> tuple[int, int, int]:
    return tuple(block[key] for key in ("x", "y", "z"))


def clear(x: int, y: int, z: int, depth: int = 0) -> None:
    if depth > 6:
        raise RuntimeError("Obstructed excavation; inspect the terrain before continuing")
    for _ in range(24):
        target = probe(x, y, z)
        name = target["block"]
        if name in AIR:
            return
        if name in LIQUIDS or any(hazard in name for hazard in HAZARDS):
            raise RuntimeError(f"Route stopped at {name}: {(x,y,z)}")
        if not target["canHarvest"] or target["hardness"] < 0:
            raise RuntimeError(f"Held tool cannot obtain {name}: {(x,y,z)}")
        collision = target.get("firstCollision")
        if collision and block_pos(collision) != (x,y,z):
            clear(*block_pos(collision), depth=depth+1)
            continue
        act("mine", x=x, y=y, z=z)
    raise RuntimeError("Falling blocks did not settle; inspect before continuing")


def step(x: int, y: int, z: int, *, tall: bool = True) -> dict:
    floor = probe(x, y-1, z)
    if floor["block"] in AIR | LIQUIDS or floor["hardness"] <= 0:
        raise RuntimeError(f"Unsupported route floor: {floor}")
    for height in ((2,1,0) if tall else (1,0)):
        clear(x, y+height, z)
    return act("walk", x=x+0.5, y=y, z=z+0.5)


def tunnel_to(x: int, y: int, z: int, record: str) -> None:
    target = (x,y,z)
    state = driver.state()
    current = tuple(math.floor(state[key]) for key in ("x","y","z"))
    if abs(x-current[0])+abs(z-current[2]) < abs(y-current[1]):
        raise ValueError("Give the staircase enough horizontal distance for its height change")
    points = [current]
    evidence = driver.RUN / "evidence" / record
    if evidence.exists():
        raise RuntimeError("Preserve the earlier route record")
    try:
        for _ in range(100):
            if current == target:
                break
            cx,cy,cz = current
            ny = cy + (1 if y>cy else -1 if y<cy else 0)
            if x != cx:
                nx,nz = cx+(1 if x>cx else -1),cz
            elif z != cz:
                nx,nz = cx,cz+(1 if z>cz else -1)
            else:
                raise RuntimeError("A staircase needs horizontal room; inspect before continuing")
            next_pos = (nx,ny,nz)
            step(*next_pos, tall=ny!=cy)
            current = next_pos
            points.append(current)
            driver.write(evidence, {"target":target,"completed":points,"complete":current==target})
        else:
            raise RuntimeError("Route exceeded its bounded step count")
    except Exception as error:
        driver.write(evidence, {"target":target,"completed":points,"complete":False,"failure":str(error)})
        raise
