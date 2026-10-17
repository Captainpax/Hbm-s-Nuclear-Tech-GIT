"""Generate the tiny NBT structure used by the Forge GameTest smoke suite."""

from __future__ import annotations

import gzip
import struct
from pathlib import Path


OUTPUT = (
    Path(__file__).resolve().parents[2]
    / "src/forge120/resources/data/hbm/structures/empty.nbt"
)


def string(value: str) -> bytes:
    encoded = value.encode("utf-8")
    return struct.pack(">H", len(encoded)) + encoded


def tag(tag_id: int, name: str, payload: bytes) -> bytes:
    return bytes([tag_id]) + string(name) + payload


def int_list(values: list[int]) -> bytes:
    return bytes([3]) + struct.pack(">i", len(values)) + b"".join(
        struct.pack(">i", value) for value in values
    )


def compound(payload: bytes) -> bytes:
    return payload + b"\x00"


def main() -> None:
    stone = compound(tag(8, "Name", string("minecraft:stone")))
    palette = bytes([10]) + struct.pack(">i", 1) + stone
    blocks = []
    for x in range(3):
        for z in range(3):
            blocks.append(compound(tag(9, "pos", int_list([x, 0, z])) + tag(3, "state", struct.pack(">i", 0))))
    block_list = bytes([10]) + struct.pack(">i", len(blocks)) + b"".join(blocks)
    empty_entities = bytes([10]) + struct.pack(">i", 0)
    root = compound(
        tag(3, "DataVersion", struct.pack(">i", 3465))
        + tag(9, "size", int_list([3, 3, 3]))
        + tag(9, "palette", palette)
        + tag(9, "blocks", block_list)
        + tag(9, "entities", empty_entities)
    )
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_bytes(gzip.compress(b"\x0a\x00\x00" + root, mtime=0))
    print(OUTPUT)


if __name__ == "__main__":
    main()
