# HBM Forge 1.20.1 port

The parity baseline is commit `9d7b7ca3bc999b7909fdb349738d305dff58bd7d` on the 1.7.10 `master` branch. This branch builds only `src/forge120`; the original `src/main` implementation remains as a reference. The old Gradle properties are preserved in `gradle-1.7.10.properties`.

`legacy-inventory.csv` is a source-linked audit index. Generate it with `python tools/porting/build_inventory.py` and verify it with `--check`. Put implementation status and evidence in `status.csv`; do not edit the generated inventory directly. `partial` means the content exists but its original behavior or acceptance checks are incomplete. `verified` requires a runtime or asset comparison check, not merely a compiled class. The scanner covers every legacy Java file and asset and identifies config-option and recipe-definition call sites, but it is a static candidate list, not an exhaustive inventory of conditional, loop-generated, or JSON-loaded runtime recipes. Screen and worldgen source files likewise need individual behavior checks before their categories can be declared complete. See [the parity and acceptance matrix](parity-matrix.md).

## Current gate

- Forge 1.20.1-47.4.10 / Java 17 / ForgeGradle 6 build: passing.
- `hbm:ore_uranium`, `hbm:gas_radon`, `hbm:ingot_uranium`, four gas masks, five filters, and a Geiger counter: registered with source inventory textures. Eleven dedicated GameTests pass, including smelting, fresh-chunk uranium generation, ore outgassing, gas movement, exposure persistence, illness and lethal damage, filter installation/wear, the fine-particle protection matrix, and exposure-packet serialization/validation.
- The development client reached the render thread and loaded texture atlases. Visual inspection in a world remains pending.
- Radon decay, movement, unprotected exposure, and protection by fine-particle filters are implemented. Exposure NBT persistence passed a dedicated-server test. Two server radiation/hazard switches and two client Geiger HUD offsets are wired in. The legacy 200/400/600/800 RAD illness tiers and 1000 RAD lethal threshold run server-side. Player exposure now has a server-to-client sync packet and a counter-gated personal-dose HUD, but actual multiplayer rejoin and in-world HUD visual checks remain pending. Environmental radiation, other hazards, mob transformations, full ore-break parity, nearly all other content, and integrations remain pending.
- No 1.7.10 world conversion or generation in already generated 1.20.1 chunks is planned.

## Next gates

1. Complete the radiation ecosystem: environmental/chunk radiation, other protective equipment and hazards, Geiger readout and sounds, mob transformations, and in-world multiplayer/HUD checks.
2. Port common energy, fluid, machine, and networking frameworks before expanding registries in feature groups.
3. Close each inventory item with equivalent gameplay and client/server checks. Audit optional 1.20.1 integrations separately.

The development JAR is intentionally versioned `1.20.1-0.1.0-dev`. It is not a gameplay-parity release.
