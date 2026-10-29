"""Normal client actions in the isolated, cheats-disabled Survival walkthrough.

This helper cannot write world/player data, issue commands, or grant inventory.
Receipts keep the pre-action state and normal client results in the ignored run directory.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import secrets
import time

ROOT = Path(__file__).resolve().parents[2]
RUN = ROOT / "run-survival-client"
SESSION = RUN / "hbm-survival-session.json"


def write(path: Path, value: dict) -> None:
    temporary = path.with_suffix(path.suffix + ".tmp")
    contents = json.dumps(value, indent=2) + "\n"
    deadline = time.monotonic() + 5
    while True:
        try:
            temporary.write_text(contents, encoding="utf-8")
            temporary.replace(path)
            return
        except PermissionError:
            if time.monotonic() >= deadline:
                raise
            # A Windows reader/OneDrive can hold the destination briefly. Keep
            # the exact request ID and contents; never issue a second action.
            time.sleep(0.05)


def session_id() -> str:
    return json.loads(SESSION.read_text(encoding="utf-8"))["session"]


def prepare() -> str:
    if SESSION.exists():
        raise RuntimeError("A Survival session already exists; resume it rather than overwriting its evidence")
    RUN.mkdir(exist_ok=True)
    value = secrets.token_hex(16)
    write(SESSION, {"session": value})
    write(RUN / "hbm-survival-command.json", {"session": value, "id": 0, "action": "inspect"})
    return value


def compact(value: dict) -> dict:
    result = dict(value)
    result.pop("before", None)
    for key in ("slots", "inventory"):
        if key in result:
            result[key] = [slot for slot in result[key] if slot["count"]]
    return result


def state() -> dict:
    result = json.loads((RUN / "hbm-survival-state.json").read_text(encoding="utf-8"))
    if result.get("session") != session_id() or abs(time.time() * 1000 - result.get("timestamp", 0)) > 10_000:
        raise RuntimeError("No fresh heartbeat for this Survival session")
    return result


def submit(action: str, **fields) -> int:
    value = {"session": session_id(), "id": time.time_ns(), "action": action, **fields}
    write(RUN / "hbm-survival-command.json", value)
    return value["id"]


def result(identifier: int, timeout: float = 70) -> dict:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            value = json.loads((RUN / "hbm-survival-result.json").read_text(encoding="utf-8"))
            if value.get("session") == session_id() and value.get("id") == identifier:
                receipts = RUN / "evidence"
                receipts.mkdir(exist_ok=True)
                write(receipts / f"{identifier}.json", value)
                return value
        except (FileNotFoundError, PermissionError, json.JSONDecodeError):
            pass
        time.sleep(0.15)
    raise TimeoutError(f"Poll the same request id {identifier}; do not issue it again")


def request(action: str, **fields) -> dict:
    identifier = submit(action, **fields)
    print(f"Request {identifier}: {action}", flush=True)
    value = result(identifier)
    print(json.dumps(compact(value), indent=2), flush=True)
    if not value.get("ok"):
        raise RuntimeError(value.get("error", "Action rejected"))
    return value


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["prepare", "state", "poll", "inspect", "scan", "probe", "walk", "mine", "use", "select", "inventory", "close", "pause", "recipe", "click", "button", "exit"])
    parser.add_argument("--fields", default="{}", help="JSON action fields")
    parser.add_argument("--id", type=int)
    arguments = parser.parse_args()
    if arguments.action == "prepare":
        print(prepare())
    elif arguments.action == "state":
        print(json.dumps(compact(state()), indent=2))
    elif arguments.action == "poll":
        print(json.dumps(compact(result(arguments.id)), indent=2))
    else:
        request(arguments.action, **json.loads(arguments.fields))
