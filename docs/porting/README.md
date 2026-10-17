# HBM Forge 1.20.1 port

The parity baseline is commit `9d7b7ca3bc999b7909fdb349738d305dff58bd7d` on the 1.7.10 `master` branch. This branch builds only `src/forge120`; the original `src/main` implementation remains as a reference. The old Gradle properties are preserved in `gradle-1.7.10.properties`.

`legacy-inventory.csv` is a source-linked audit index. Generate it with `python tools/porting/build_inventory.py` and verify it with `--check`. Put implementation status and evidence in `status.csv`; do not edit the generated inventory directly. `partial` means the content exists but its original behavior or acceptance checks are incomplete. `verified` requires a runtime or asset comparison check, not merely a compiled class. The scanner lists recipe, configuration, screen, and worldgen source files as audit units; each must be expanded into individual behavior checks before that category can be declared complete.

## Current gate

- Forge 1.20.1-47.4.10 / Java 17 / ForgeGradle 6 build: passing.
- `hbm:ore_uranium` and `hbm:ingot_uranium`: registered with source textures and a smelting recipe. The dedicated GameTest server passed smelting and fresh-chunk uranium generation checks.
- The development client reached the render thread and loaded texture atlases. Visual inspection in a world remains pending.
- Uranium ore's original radon gas behavior, radiation, all other content, client renderers, multiplayer systems, and integrations: pending.
- No 1.7.10 world conversion or generation in already generated 1.20.1 chunks is planned.

## Next gates

1. Port radon and radiation persistence, then complete and test the uranium slice.
2. Port common energy, fluid, machine, and networking frameworks before expanding registries in feature groups.
3. Close each inventory item with equivalent gameplay and client/server checks. Audit optional 1.20.1 integrations separately.

The development JAR is intentionally versioned `1.20.1-0.1.0-dev`. It is not a gameplay-parity release.
