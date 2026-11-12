# Idempotent setup for the separately named, explicitly command-assisted world.
execute unless data storage hbm_checkpoint:progression setup run function hbm_checkpoint:progression_setup_once
