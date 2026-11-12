"""Prepare the explicitly authorized command-assisted QA profile.

The original cheats-disabled Survival directory is never modified.
"""
from __future__ import annotations

import argparse
import os
import shutil

os.environ["HBM_SURVIVAL_ASSISTED"] = "1"
import survival_driver as driver


def prepare() -> None:
    session = driver.prepare()
    options = driver.ROOT / "run-survival-client/options.txt"
    destination = driver.RUN / "options.txt"
    if options.is_file() and not destination.exists():
        shutil.copy2(options, destination)
    print(session)


def install_pack() -> None:
    world = driver.RUN / "saves/HBM Survival Checkpoint QA"
    if not (world / "level.dat").is_file():
        raise RuntimeError("Create the named test world in Minecraft before installing the fixture pack")
    source = driver.ROOT / "tools/porting/fixtures/checkpoint-progression"
    destination = world / "datapacks/hbm_checkpoint_progression"
    shutil.copytree(source, destination, dirs_exist_ok=True)
    print("Installed test-supply pack in the separate checkpoint world")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["prepare", "install-pack"])
    arguments = parser.parse_args()
    (prepare if arguments.action == "prepare" else install_pack)()
