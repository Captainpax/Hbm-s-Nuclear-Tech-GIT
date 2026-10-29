"""Drive normal inventory packets in the two opt-in local Forge QA clients.

This uses injected machine fixtures, not raw-resource Survival acceptance.
No action writes a server inventory, grants items, or bypasses the server menu.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import secrets
import time

import multiplayer_smoke as smoke

SESSION = smoke.SERVER / "evidence/client-driver-session.json"
PLAYERS = {"Dev": smoke.ROOT / "run", "HbmObserver": smoke.CLIENT}


def write_json(path: Path, data: dict) -> None:
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    temporary.replace(path)


def prepare() -> str:
    smoke.properties()
    SESSION.parent.mkdir(exist_ok=True)
    session = secrets.token_hex(16)
    write_json(SESSION, {"session": session})
    for directory in PLAYERS.values():
        directory.mkdir(exist_ok=True)
        write_json(directory / "hbm-driver-command.json", {"session": session, "id": 0, "action": "inspect"})
    return session


def session_id() -> str:
    value = json.loads(SESSION.read_text(encoding="utf-8"))["session"]
    if len(value) != 32 or any(character not in "0123456789abcdef" for character in value):
        raise ValueError("Invalid local driver session")
    return value


def state(player: str) -> dict:
    result = json.loads((PLAYERS[player] / "hbm-driver-state.json").read_text(encoding="utf-8"))
    if result.get("session") != session_id() or time.time() * 1000 - result.get("timestamp", 0) > 10_000:
        raise RuntimeError("Client has not produced a fresh heartbeat for this session")
    return result


def submit(player: str, action: str, **fields) -> int:
    request = {"session": session_id(), "id": time.time_ns(), "action": action, **fields}
    write_json(PLAYERS[player] / "hbm-driver-command.json", request)
    return request["id"]


def result(player: str, request_id: int, timeout: float = 10) -> dict:
    deadline = time.monotonic() + timeout
    path = PLAYERS[player] / "hbm-driver-result.json"
    while time.monotonic() < deadline:
        try:
            response = json.loads(path.read_text(encoding="utf-8"))
            if response.get("session") == session_id() and response.get("id") == request_id:
                if not response.get("ok"):
                    raise RuntimeError(response.get("error", "Client rejected request"))
                return response
        except (FileNotFoundError, json.JSONDecodeError):
            pass
        time.sleep(0.1)
    raise TimeoutError(f"Request remains pending; poll {player} id {request_id} without resubmitting it")


def request(player: str, action: str, **fields) -> dict:
    return result(player, submit(player, action, **fields))


def summary(response: dict) -> dict:
    """Keep CLI output compact; full slot/state evidence stays in the runtime JSON."""
    result = dict(response)
    for key in ["slots", "inventory"]:
        if key in result:
            result[key] = [slot for slot in result[key] if slot["count"] > 0]
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("prepare")
    status = commands.add_parser("state")
    status.add_argument("player", choices=PLAYERS)
    pending = commands.add_parser("poll")
    pending.add_argument("player", choices=PLAYERS)
    pending.add_argument("id", type=int)
    inspect = commands.add_parser("inspect")
    inspect.add_argument("player", choices=PLAYERS)
    close = commands.add_parser("close")
    close.add_argument("player", choices=PLAYERS)
    reconnect = commands.add_parser("reconnect")
    reconnect.add_argument("player", choices=PLAYERS)
    exit_client = commands.add_parser("exit")
    exit_client.add_argument("player", choices=PLAYERS)
    open_menu = commands.add_parser("open")
    open_menu.add_argument("player", choices=PLAYERS)
    for axis in ["x", "y", "z"]:
        open_menu.add_argument(axis, type=int)
    click = commands.add_parser("click")
    click.add_argument("player", choices=PLAYERS)
    click.add_argument("slot", type=int)
    click.add_argument("--button", type=int, choices=[0, 1], default=0)
    click.add_argument("--mode", choices=["PICKUP", "QUICK_MOVE"], default="PICKUP")
    button = commands.add_parser("button")
    button.add_argument("player", choices=PLAYERS)
    button.add_argument("button", type=int, choices=[0, 1])
    arguments = vars(parser.parse_args())
    action = arguments.pop("command")
    if action == "prepare":
        print(prepare())
    else:
        player = arguments.pop("player")
        if action == "state":
            print(json.dumps(summary(state(player)), indent=2))
        elif action == "poll":
            print(json.dumps(summary(result(player, arguments["id"])), indent=2))
        else:
            print(json.dumps(summary(request(player, action, **arguments)), indent=2))
