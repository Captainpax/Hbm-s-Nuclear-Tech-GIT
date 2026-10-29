"""Source machine interaction and restart checks with two ordinary Forge clients.

Fixtures are explicitly injected. This is not raw-resource Survival acceptance.
Run before-restart, restart the dedicated server without reseeding, run
resume-server, reconnect both clients, then run after-restart.
"""
from __future__ import annotations

import argparse
import json
import re
import time

import client_driver as driver
import multiplayer_smoke as smoke

EVIDENCE = smoke.SERVER / "evidence/two-client-restart.json"
PLAYERS = tuple(driver.PLAYERS)


def save(data: dict) -> None:
    driver.write_json(EVIDENCE, data)


def action(data: dict, player: str, name: str, **fields) -> dict:
    result = driver.request(player, name, **fields)
    data["actions"].append({"player": player, "action": name, "fields": fields, "result": result})
    save(data)
    print(f"{player}: {name}; menu={result['menu']}; carried={result['carried']}", flush=True)
    return result


def ready() -> None:
    if not smoke.command("list").startswith("There are 2 of a max of 2 players online:"):
        raise AssertionError("Both named Forge clients must be connected")
    for player in PLAYERS:
        state = driver.state(player)
        assert state["connected"] and state["player"] == player and state["gameMode"] == "survival"


def no_carried_copper(state: dict) -> None:
    assert state["carried"]["count"] == 0, "Clear the mouse cursor through the menu before reseeding"
    assert not any(slot["count"] > 0 and slot["item"] in {"hbm:ingot_copper", "hbm:plate_cast_copper"}
                   for slot in state["inventory"]), "Return previous test copper through its chest before reseeding"


def before_restart(data: dict) -> None:
    ready()
    for player in PLAYERS:
        no_carried_copper(action(data, player, "close"))
    smoke.prepare(False)
    smoke.command("reload")
    smoke.command("function hbm_checkpoint:seed")
    data["fixture"] = "Nine copper ingots; source inputs injected; 648 quanta; 64 furnace ores; 1M HE; 64 blast iron/32 sand/16 coal"
    smoke.command("weather clear 1000000")
    smoke.command("time set 1000")
    smoke.command("tp Dev 31.5 100.0 13.0 0 0")
    smoke.command("tp HbmObserver 32.5 100.0 13.0 0 0")
    state = action(data, "Dev", "open", x=31, y=101, z=15)
    assert state["menu"] == "PortCrucibleMenu"
    state = action(data, "Dev", "button", button=1)
    assert state["selectedRecipe"] == 1
    observer = action(data, "HbmObserver", "open", x=32, y=101, z=15)
    assert observer["menu"] == "PortCrucibleMenu" and observer["selectedRecipe"] == 1
    candidates = [slot["slot"] for slot in observer["slots"] if slot["slot"] < 9
                  and slot["item"] == "hbm:ingot_copper" and slot["count"] == 1]
    assert candidates, "Crucible has no buffered copper for the competing pickup"
    slot = candidates[0]
    game_time = int(re.search(r"\d+", smoke.command("time query gametime"))[0])
    requests = {player: driver.submit(player, "click", slot=slot, button=0, mode="PICKUP", notBeforeTick=game_time + 40)
                for player in PLAYERS}
    data["competing_requests"] = requests
    save(data)
    responses = {player: driver.result(player, identifier) for player, identifier in requests.items()}
    data["competing_results"] = responses
    save(data)
    winners = [player for player, result in responses.items() if result["carried"]["item"] == "hbm:ingot_copper"
               and result["carried"]["count"] == 1]
    assert len(winners) == 1 and sum(result["carried"]["count"] for result in responses.values()) == 1, \
        "Competing clicks lost or duplicated a copper ingot"
    winner = winners[0]
    assert responses[winner]["inventory"][0]["count"] == 0
    action(data, winner, "click", slot=36, button=0, mode="PICKUP")
    data["after_pickup"] = smoke.snapshot(True, quiet=True)
    action(data, "Dev", "button", button=0)
    action(data, winner, "click", slot=36, button=0, mode="PICKUP")
    returned = action(data, winner, "click", slot=slot, button=0, mode="PICKUP")
    assert returned["carried"]["count"] == 0
    data["returned_copper"] = smoke.snapshot(True, quiet=True)
    # Preheat the injected fixture, then let actual machine ticks melt and cool.
    # No material quantity, recipe result or HE state is changed by these commands.
    smoke.command("data merge block 31 100 16 {heatEnergy:100000}")
    smoke.command("data merge block 31 101 16 {heat:100000}")
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        state = smoke.snapshot(True, quiet=True)
        if state["mold_amount"] == 216 and 0 < state["mold_cooling"] < 180:
            break
        time.sleep(0.25)
    else:
        raise AssertionError("Casting fixture did not reach active cooling")
    assert state["blast_progress"] > 0 and state["firebox_burn"] > 0
    data["before_restart"] = state
    data["clients_before_restart"] = {player: driver.state(player) for player in PLAYERS}
    for state in data["clients_before_restart"].values():
        assert state["menu"] == "PortCrucibleMenu" and state["carried"]["count"] == 0
    data["before_complete"] = True
    save(data)
    print("Active casting and furnace state verified; saving and stopping the dedicated server.", flush=True)
    data["save_response"] = smoke.command("save-all flush")
    data["stop_response"] = smoke.command("stop")
    save(data)


def resume_server(data: dict) -> None:
    assert data.get("before_complete"), "No completed pre-restart fixture"
    if "server_resumed" in data:
        raise RuntimeError("The first restored snapshot is already recorded; preserve it")
    deadline = time.monotonic() + 120
    while True:
        try:
            data["server_resumed"] = smoke.snapshot(False, quiet=True)
            break
        except OSError:
            if time.monotonic() >= deadline:
                raise
            time.sleep(0.2)
    save(data)
    print(json.dumps(data["server_resumed"], indent=2), flush=True)


def after_restart(data: dict) -> None:
    assert data.get("before_complete") and "server_resumed" in data, "Verify the server resume before reconnecting clients"
    ready()
    before, resumed = data["before_restart"], data["server_resumed"]
    electric_ticks = (resumed["electric_output"] - before["electric_output"]) * 100 \
        + resumed["electric_progress"] - before["electric_progress"]
    blast_ticks = (resumed["steel_output"] - before["steel_output"]) // 2 * 1600 \
        + resumed["blast_progress"] - before["blast_progress"]
    assert electric_ticks == blast_ticks and electric_ticks >= 0, "Active machines advanced by inconsistent work across restart"
    if before["mold_amount"] == resumed["mold_amount"] == 216 and 0 < resumed["mold_cooling"] <= before["mold_cooling"]:
        assert before["mold_cooling"] - resumed["mold_cooling"] == electric_ticks, "Casting cooldown did not preserve its active state"
    data["resumed_active_ticks"] = electric_ticks
    data["session_after"] = driver.session_id()
    for player in PLAYERS:
        assert driver.state(player)["uuid"] == data["clients_before_restart"][player]["uuid"], "Rejoined a different player profile"
    data["after_rejoin"] = smoke.snapshot(True, quiet=True)
    for player in PLAYERS:
        state = action(data, player, "open", x=31 if player == "Dev" else 32, y=101, z=15)
        assert state["menu"] == "PortCrucibleMenu" and state["selectedRecipe"] == 0
    changed = action(data, "HbmObserver", "button", button=1)
    observed = action(data, "Dev", "inspect")
    assert changed["selectedRecipe"] == observed["selectedRecipe"] == 1
    action(data, "Dev", "button", button=0)
    for player in PLAYERS:
        action(data, player, "close")
    deadline = time.monotonic() + 45
    while time.monotonic() < deadline:
        state = smoke.snapshot(True, quiet=True)
        if state["cast_plates"] == 3:
            break
        time.sleep(0.5)
    else:
        raise AssertionError("Restarted casting line did not finish three conserved plates")
    for player in PLAYERS:
        smoke.command(f"tp {player} 34.5 100.0 14.0 0 35")
        state = action(data, player, "open", x=34, y=99, z=16)
        assert state["menu"] == "ChestMenu"
    requests = {player: driver.submit(player, "click", slot=0, button=0, mode="QUICK_MOVE") for player in PLAYERS}
    for player, identifier in requests.items():
        data["actions"].append({"player": player, "action": "competing_cast_collection", "result": driver.result(player, identifier)})
    views = {player: action(data, player, "inspect") for player in PLAYERS}
    assert sum(slot["count"] for state in views.values() for slot in state["inventory"]
               if slot["item"] == "hbm:plate_cast_copper") == 3
    assert all(state["carried"]["count"] == 0 for state in views.values())
    data["after_collection"] = smoke.snapshot(True, quiet=True)
    # Both viewers must lose an old controller even when a replacement of the
    # same block type appears at that position in one atomic server function.
    for player in PLAYERS:
        action(data, player, "close")
        smoke.command(f"tp {player} 31.5 100.0 13.0 0 0")
        state = action(data, player, "open", x=31, y=101, z=15)
        assert state["menu"] == "PortCrucibleMenu" and state["carried"]["count"] == 0
    smoke.command("function hbm_checkpoint:replace_crucible")
    for player in PLAYERS:
        state = action(data, player, "inspect")
        assert state["menu"] == "InventoryMenu", "A viewer retained the removed controller's inventory"
        state = action(data, player, "open", x=31, y=101, z=15)
        assert state["menu"] == "PortCrucibleMenu", "The replacement controller could not open a fresh menu"
        action(data, player, "close")
    data["after_controller_replacement"] = smoke.snapshot(True, quiet=True)
    data["after_complete"] = True
    save(data)
    print("Two-client controls, competing collection, server restart/rejoin and conservation passed.", flush=True)


def reconnect(data: dict) -> None:
    """Optional second restart: retain the client processes and their driver session."""
    assert data.get("after_complete"), "Finish the original restart round first"
    smoke.command("list")  # Confirm the restarted server's RCON listener is ready.
    assert all(not driver.state(player)["connected"] for player in PLAYERS), "Both clients must be disconnected first"
    requests = {player: driver.submit(player, "reconnect") for player in PLAYERS}
    data["reconnect_requests"] = requests
    save(data)
    for player, identifier in requests.items():
        driver.result(player, identifier)
    deadline = time.monotonic() + 60
    while time.monotonic() < deadline:
        states = {player: driver.state(player) for player in PLAYERS}
        if all(state["connected"] for state in states.values()):
            break
        time.sleep(0.25)
    else:
        raise AssertionError("Both ordinary Forge clients did not reconnect")
    ready()
    for player in PLAYERS:
        assert states[player]["uuid"] == data["clients_before_restart"][player]["uuid"]
        assert states[player]["carried"]["count"] == 0
    assert sum(slot["count"] for state in states.values() for slot in state["inventory"]
               if slot["item"] == "hbm:plate_cast_copper") == 3
    data["clients_after_reconnect"] = states
    data["after_reconnect_ledger"] = smoke.snapshot(True, quiet=True)
    data["reconnect_complete"] = True
    save(data)
    print("Both running Forge clients reconnected with the original UUIDs and three conserved carried plates.", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("phase", choices=["before-restart", "resume-server", "after-restart", "reconnect"])
    phase = parser.parse_args().phase
    if phase == "before-restart":
        if EVIDENCE.exists():
            raise RuntimeError("Preserve and inspect the previous round's evidence before starting a fresh fixture")
        data = {"scope": "Injected two-client fixture; not raw Survival", "session": driver.session_id(), "actions": []}
    else:
        data = json.loads(EVIDENCE.read_text(encoding="utf-8"))
    try:
        {"before-restart": before_restart, "resume-server": resume_server,
         "after-restart": after_restart, "reconnect": reconnect}[phase](data)
    except Exception as failure:
        data["failure"] = {"phase": phase, "message": str(failure)}
        save(data)
        raise
