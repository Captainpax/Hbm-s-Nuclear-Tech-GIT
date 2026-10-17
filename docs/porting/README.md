# HBM Forge 1.20.1 port

The parity baseline is commit `9d7b7ca3bc999b7909fdb349738d305dff58bd7d` on the 1.7.10 `master` branch. This branch builds only `src/forge120`; the original `src/main` implementation remains as a reference. The old Gradle properties are preserved in `gradle-1.7.10.properties`.

`legacy-inventory.csv` is a source-linked audit index. Generate it with `python tools/porting/build_inventory.py` and verify it with `--check`. Put implementation status and evidence in `status.csv`; do not edit the generated inventory directly. `partial` means the content exists but its original behavior or acceptance checks are incomplete. `verified` requires a runtime or asset comparison check, not merely a compiled class. The scanner covers every legacy Java file and asset and identifies config-option and recipe-definition call sites, but it is a static candidate list, not an exhaustive inventory of conditional, loop-generated, or JSON-loaded runtime recipes. Screen and worldgen source files likewise need individual behavior checks before their categories can be declared complete. See [the parity and acceptance matrix](parity-matrix.md).

## Current gate

- Forge 1.20.1-47.4.10 / Java 17 / ForgeGradle 6 build: passing.
- Eleven ordinary Overworld ores are registered with source textures: seven self-dropping ores with ingot smelting, aluminium ore smelting to a named cryolite-chunk item, plus sulfur, fluorite, and niter with mineral drops. Twenty-three dedicated GameTests pass, including eight ore smelting recipes, mineral yields and Silk Touch, fresh-chunk generation, ore outgassing, gas movement, exposure persistence, illness and lethal damage, filter installation/wear, fine-particle protection, packet validation, chunk-radiation NBT/diffusion/no-generation checks, Geiger server use and click-sound thresholds, Nether ambient exposure, HE storage/transfer arithmetic, and a two-launch SavedData restart check. The eleven ported ores read legacy-default per-ore server settings at fresh-chunk generation; zero-count copper and the Overworld master-off case were checked in fresh chunks. Custom-count persistence across restart remains untested.
- The development client launched into a newly generated Creative world and shut down cleanly (`runClient`, 2026-10-17). Manual inspection confirmed the Geiger inventory/hand texture, its zero-dose HUD background and right-click chunk/environment/personal readout, plus the uranium ore item and placed-block texture. A second fresh world opened with the expanded ore registry; thorium and beryllium ore/ingot icons rendered in creative search, and its log had no HBM missing-model/texture or registry-remap warning. A later eleven-ore client startup reached resource loading without HBM missing-model/texture/sound or registry errors; it did not visually inspect the newly added mineral or cryolite icons. This is a sample visual smoke, not an asset-completeness audit; nonzero HUD rate, other models, screens, and effects remain unchecked. The Geiger's seven source audio files are byte-for-byte copies under modern lowercase IDs, packaged in the JAR; a separate client startup loaded the sound resources without HBM missing-sound warnings, but audible comparison remains pending.
- Radon decay, movement, unprotected exposure, and protection by fine-particle filters are implemented. Exposure NBT persistence and chunk-radiation NBT round-trip passed dedicated-server tests. A separate seed/verify GameTest sequence also demonstrated chunk-radiation SavedData surviving a full dedicated-server stop and restart; it does not prove player or machine state across restart. Four server radiation/hazard settings and two client Geiger HUD offsets are wired in. The legacy 200/400/600/800 RAD illness tiers and 1000 RAD lethal threshold run server-side. The default scalar chunk radiation system persists per dimension, diffuses across loaded chunks, and doses entities; Nether ambient radiation is included. The Geiger counter reports chunk, environmental, and personal readings on right-click, and its HUD shows synced personal dose. Multiplayer rejoin and nonzero-dose HUD visual checks remain pending. Other radiation sources, hazards, mob transformations, full ore-break parity, nearly all other content, and integrations remain pending.
- The long-valued HE storage and one-tick priority network distribution core now exists, with tests for proportional source/receiver shares, conservation, transfer limits, long-range values, NBT clamping, and self-transfer prevention. This is only a shared core: actual batteries, cables, machine block entities, world topology, item charging, Forge Energy conversion, and save/reload in-world are not ported yet.
- No 1.7.10 world conversion or generation in already generated 1.20.1 chunks is planned. The legacy ore generator uses `minHeight + rand.nextInt(variance)`; the uranium feature now correctly targets Y 5–24 (previously 5–20). Modern stone and deepslate are both replacement targets to keep these ranges productive in 1.20.1 terrain.

## Next gates

1. Complete the radiation ecosystem: remaining sources, PRISM and high-radiation world effects, armor resistance and other hazards, audible Geiger sound comparison, mob transformations, and in-world multiplayer/HUD/reload checks.
2. Port common energy, fluid, machine, and networking frameworks before expanding registries in feature groups.
3. Close each inventory item with equivalent gameplay and client/server checks. Audit optional 1.20.1 integrations separately.

The development JAR is intentionally versioned `1.20.1-0.1.0-dev`. It is not a gameplay-parity release.

## Dedicated restart smoke

Run these in order in the same checkout and `run` directory. The first process writes a radiation SavedData fixture; the second starts a new dedicated GameTest server, checks the value, then clears the fixture. CI runs the same pair.

```sh
./gradlew build runGameTestServer -PhbmPersistencePhase=seed --no-daemon
./gradlew runGameTestServer -PhbmPersistencePhase=verify --no-daemon
```
