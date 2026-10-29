"""Prepare and control the isolated, loopback-only two-client development server.

Run prepare --accept-eula after agreeing to https://aka.ms/MinecraftEULA, then
./gradlew runServer -PhbmMultiplayer and ./gradlew runMultiplayerClient in separate
terminals. The ordinary runClient can join as the first player. This helper only
controls that fixture; it never targets a public or user-configured server.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import secrets
import socket
import struct

ROOT = Path(__file__).resolve().parents[2]
SERVER = ROOT / "run-multiplayer"
CLIENT = ROOT / "run-multiplayer-client"
TEMPLATE = ROOT / "tools/porting/fixtures/multiplayer-server.properties"


def properties() -> dict[str, str]:
    result = {}
    for line in (SERVER / "server.properties").read_text(encoding="utf-8").splitlines():
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            result[key] = value
    if (result.get("server-ip") != "127.0.0.1" or result.get("rcon.port") != "25576"
            or result.get("server-port") != "25566" or result.get("level-name") != "survival-checkpoint"):
        raise ValueError("Refusing RCON outside the isolated loopback fixture")
    return result


def prepare(accept_eula: bool) -> None:
    SERVER.mkdir(exist_ok=True)
    CLIENT.mkdir(exist_ok=True)
    config = SERVER / "server.properties"
    if not config.exists():
        config.write_text(TEMPLATE.read_text(encoding="utf-8")
                          + "\nrcon.password=" + secrets.token_hex(24) + "\n", encoding="utf-8")
    properties()  # Validate an existing fixture without replacing its credentials/settings.
    if accept_eula:
        (SERVER / "eula.txt").write_text("# https://aka.ms/MinecraftEULA\neula=true\n", encoding="utf-8")
    source = ROOT / "run/options.txt"
    if source.exists() and not (CLIENT / "options.txt").exists():
        options = source.read_text(encoding="utf-8").splitlines()
        limits = {"renderDistance": "5", "simulationDistance": "5", "maxFps": "60"}
        options = [line.split(":", 1)[0] + ":" + limits[line.split(":", 1)[0]]
                   if line.split(":", 1)[0] in limits else line for line in options]
        (CLIENT / "options.txt").write_text("\n".join(options) + "\n", encoding="utf-8")
    write_datapack()
    print("Prepared loopback server on 127.0.0.1:25566 and separate HbmObserver client directory")


def write_datapack() -> None:
    """Explicit injected machine fixtures; these never establish raw Survival progression."""
    pack = SERVER / "survival-checkpoint/datapacks/hbm_checkpoint"
    functions = pack / "data/hbm_checkpoint/functions"
    functions.mkdir(parents=True, exist_ok=True)
    (pack / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 15,
        "description": "Local HBM two-client machine conservation fixtures"}}), encoding="utf-8")
    seed = [
        "# WARNING: resets only the isolated fixture area; inputs are injected for machine QA.",
        "forceload add 24 0 43 30",
        "fill 24 98 0 43 112 30 minecraft:air",
        "kill @e[type=minecraft:item,x=24,y=97,z=0,dx=20,dy=20,dz=32]",
        "fill 24 99 0 43 99 30 minecraft:smooth_stone",
        "setworldspawn 30 100 8",
    ]
    # x=31 cores and their x=32 proxy parts cross a chunk boundary. Waste pours east.
    for block, core, height in [("heater_firebox", (31, 100, 16), 1),
                                ("machine_crucible", (31, 101, 16), 2),
                                ("machine_blast_furnace", (39, 100, 16), 7)]:
        cx, cy, cz = core
        for y in range(height):
            for z in range(3):
                for x in range(3):
                    seed.append(f"setblock {cx+x-1} {cy+y} {cz+z-1} "
                                f"hbm:{block}[facing=west,x={x},y={y},z={z}]")
    seed += [
        'data merge block 31 100 16 {items:{Size:2,Items:[{Slot:0,Count:16b,id:"minecraft:coal"}]}}',
        'data merge block 31 101 16 {items:{Size:10,Items:[' + ','.join(
            f'{{Slot:{slot},Count:1b,id:"hbm:ingot_copper"}}' for slot in range(1, 10)) + ']}}',
        'setblock 33 100 16 hbm:foundry_mold{items:{Size:2,Items:[{Slot:0,Count:1b,id:"hbm:mold_plate_cast"}]}}',
        'setblock 33 99 16 minecraft:hopper[facing=east]',
        'setblock 34 99 16 minecraft:chest',
        'data merge block 39 100 16 {items:{Size:5,Items:[{Slot:0,Count:16b,id:"minecraft:coal"},'
        '{Slot:1,Count:64b,id:"minecraft:iron_ingot"},{Slot:2,Count:32b,id:"minecraft:sand"}]}}',
        'setblock 35 100 20 hbm:machine_battery{power:1000000L,redLow:2s,redHigh:2s}',
        'setblock 36 100 20 hbm:red_cable_classic',
        'setblock 37 100 20 hbm:machine_electric_furnace{items:{Size:4,Items:[{Slot:1,Count:64b,id:"minecraft:iron_ore"}]}}',
        'scoreboard objectives add hbm_checkpoint dummy',
        'data modify storage hbm_checkpoint:smoke seeded set value 1b',
    ]
    (functions / "seed.mcfunction").write_text("\n".join(seed) + "\n", encoding="utf-8")
    snapshot = ["# One function executes atomically on the server thread, avoiding cross-tick reads.",
                "data modify storage hbm_checkpoint:smoke snapshot set value {}"]

    def read(name: str, position: str, path: str) -> None:
        snapshot.append(f"execute store result score #{name} hbm_checkpoint run data get block {position} {path}")

    def constant(name: str, value: int) -> None:
        snapshot.append(f"scoreboard players set #{name} hbm_checkpoint {value}")

    def operation(target: str, action: str, operand: str) -> None:
        snapshot.append(f"scoreboard players operation #{target} hbm_checkpoint {action} #{operand} hbm_checkpoint")

    def save(name: str) -> None:
        snapshot.append(f"execute store result storage hbm_checkpoint:smoke snapshot.{name} int 1 "
                        f"run scoreboard players get #{name} hbm_checkpoint")

    constant("ingot", 72); constant("plate", 216); constant("coal", 1600)
    constant("smelt", 5000); constant("tick_he", 50); constant("blast_cost", 400)
    constant("copper_quanta", 0)
    for slot in range(1, 10):
        read("value", "31 101 16", f"items.Items[{{Slot:{slot}}}].Count")
        operation("value", "*=", "ingot"); operation("copper_quanta", "+=", "value")
    for store in ["recipeMaterials", "wasteMaterials"]:
        read("value", "31 101 16", store + '[{material:"copper"}].amount')
        operation("copper_quanta", "+=", "value")
    read("value", "33 100 16", "amount"); operation("copper_quanta", "+=", "value")
    constant("cast_plates", 0)
    for position, path in [("33 100 16", 'items.Items[{Slot:1}].Count')]:
        read("value", position, path); operation("cast_plates", "+=", "value")
    for position, slots in [("33 99 16", 5), ("34 99 16", 27)]:
        for slot in range(slots):
            read("value", position, f'Items[{{Slot:{slot}b,id:"hbm:plate_cast_copper"}}].Count')
            operation("cast_plates", "+=", "value")
    # Include cast plates carried by either named test player after a GUI transfer.
    # Offline inventories are verified after both clients rejoin, using the strict gate.
    for player in ["Dev", "HbmObserver"]:
        for slot in [*range(36), -106]:
            snapshot.append(f'execute store result score #value hbm_checkpoint run data get entity {player} '
                            f'Inventory[{{Slot:{slot}b,id:"hbm:plate_cast_copper"}}].Count')
            operation("cast_plates", "+=", "value")
            snapshot.append(f'execute store result score #value hbm_checkpoint run data get entity {player} '
                            f'Inventory[{{Slot:{slot}b,id:"hbm:ingot_copper"}}].Count')
            operation("value", "*=", "ingot")
            operation("copper_quanta", "+=", "value")
    operation("value", "=", "cast_plates"); operation("value", "*=", "plate")
    operation("copper_quanta", "+=", "value")
    read("electric_input", "37 100 20", "items.Items[{Slot:1}].Count")
    read("electric_output", "37 100 20", "items.Items[{Slot:2}].Count")
    read("electric_progress", "37 100 20", "progress")
    read("electric_he", "35 100 20", "power")
    read("value", "37 100 20", "power"); operation("electric_he", "+=", "value")
    operation("value", "=", "electric_output"); operation("value", "*=", "smelt")
    operation("electric_he", "+=", "value")
    operation("value", "=", "electric_progress"); operation("value", "*=", "tick_he")
    operation("electric_he", "+=", "value")
    read("blast_input", "39 100 16", "items.Items[{Slot:1}].Count")
    read("steel_output", "39 100 16", "items.Items[{Slot:3}].Count")
    read("slag_output", "39 100 16", "items.Items[{Slot:4}].Count")
    read("blast_fuel_budget", "39 100 16", "items.Items[{Slot:0}].Count")
    operation("blast_fuel_budget", "*=", "coal")
    read("value", "39 100 16", "fuel"); operation("blast_fuel_budget", "+=", "value")
    operation("value", "=", "steel_output"); operation("value", "*=", "blast_cost")
    operation("blast_fuel_budget", "+=", "value")
    for name, pos, field in [("flue", "39 100 16", "flueGas"),
                             ("crucible_heat", "31 101 16", "heat"),
                             ("firebox_heat", "31 100 16", "heatEnergy"),
                             ("firebox_burn", "31 100 16", "burnTime"),
                             ("mold_cooling", "33 100 16", "cooloff")]:
        read(name, pos, field)
    for name in ["copper_quanta", "cast_plates", "electric_input", "electric_output", "electric_progress",
                 "electric_he", "blast_input", "steel_output", "slag_output", "blast_fuel_budget", "flue",
                 "crucible_heat", "firebox_heat", "firebox_burn", "mold_cooling"]:
        save(name)
    (functions / "snapshot.mcfunction").write_text("\n".join(snapshot) + "\n", encoding="utf-8")


def read_exact(connection: socket.socket, count: int) -> bytes:
    chunks = bytearray()
    while len(chunks) < count:
        chunk = connection.recv(count - len(chunks))
        if not chunk:
            raise ConnectionError("RCON closed an incomplete packet")
        chunks.extend(chunk)
    return bytes(chunks)


def receive(connection: socket.socket) -> tuple[int, int, str]:
    size, = struct.unpack("<i", read_exact(connection, 4))
    if not 10 <= size <= 4_194_304:
        raise ValueError("Invalid RCON packet length")
    packet = read_exact(connection, size)
    request, kind = struct.unpack("<ii", packet[:8])
    if packet[-2:] != b"\0\0":
        raise ValueError("Malformed RCON packet terminator")
    return request, kind, packet[8:-2].decode("utf-8", errors="replace")


def send(connection: socket.socket, request: int, kind: int, text: str) -> None:
    packet = struct.pack("<ii", request, kind) + text.encode("utf-8") + b"\0\0"
    connection.sendall(struct.pack("<i", len(packet)) + packet)


def command(text: str) -> str:
    config = properties()
    with socket.create_connection(("127.0.0.1", 25576), timeout=15) as connection:
        send(connection, 7311, 3, config["rcon.password"])
        request, kind, _ = receive(connection)
        if request != 7311 or kind != 2:
            raise PermissionError("RCON authentication failed")
        send(connection, 7312, 2, text)
        if text.strip().lower() == "stop":
            # Shutdown may close the socket immediately after acknowledging the command.
            try:
                return receive(connection)[2]
            except ConnectionError:
                return "Stop requested; confirm process termination separately."
        request, _, first = receive(connection)
        if request != 7312:
            raise ValueError("Unexpected RCON command response identifier")
        # A second request delimits fragmented command responses without a timing guess.
        # Wait for the first reply before sending it: Minecraft's RCON listener
        # rejects multiple request packets coalesced into a single socket read.
        send(connection, 7313, 2, "")
        output = [first]
        while True:
            request, _, message = receive(connection)
            if request == 7313:
                break
            if request != 7312:
                raise ValueError("Unexpected RCON response identifier")
            output.append(message)
        return "".join(output)


def snapshot(require_two_players: bool) -> None:
    if require_two_players and not command("list").startswith("There are 2 of a max of 2 players online:"):
        raise AssertionError("Two real clients must be connected for this check")
    command("function hbm_checkpoint:snapshot")
    result = command("data get storage hbm_checkpoint:smoke snapshot")
    state = {key: int(value) for key, value in re.findall(r"(\w+):\s*(-?\d+)", result)}
    if state.get("copper_quanta") != 648:
        raise AssertionError(f"Copper ledger changed: {result}")
    if state.get("electric_he") != 1_000_000 or state.get("electric_input", -1) + state.get("electric_output", -1) != 64:
        raise AssertionError(f"Electric furnace material/HE ledger changed: {result}")
    if state.get("blast_input", -1) + state.get("steel_output", -1) != 64 or state.get("blast_fuel_budget") != 25_600:
        raise AssertionError(f"Blast furnace material/fuel ledger changed: {result}")
    if state.get("slag_output", -1) * 2 != state.get("steel_output") or not 0 <= state.get("flue", -1) <= 1000:
        raise AssertionError(f"Blast furnace slag/flue state changed: {result}")
    print(json.dumps(state, sort_keys=True, indent=2))
    print("Conservation verified" + (" with two connected clients" if require_two_players else " (server fixture only)"))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="action", required=True)
    setup = commands.add_parser("prepare")
    setup.add_argument("--accept-eula", action="store_true")
    control = commands.add_parser("command")
    control.add_argument("text")
    audit = commands.add_parser("snapshot")
    audit.add_argument("--require-two-players", action="store_true")
    arguments = parser.parse_args()
    if arguments.action == "prepare":
        prepare(arguments.accept_eula)
    elif arguments.action == "snapshot":
        snapshot(arguments.require_two_players)
    else:
        print(command(arguments.text))
