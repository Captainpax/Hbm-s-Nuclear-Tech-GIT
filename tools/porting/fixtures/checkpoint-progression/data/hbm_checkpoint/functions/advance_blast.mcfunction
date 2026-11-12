# Optional test-only timer shortcut, authorized separately from unassisted play.
# Inputs, fuel checks and outputs still run through the next normal machine tick.
# Duration/airblast behavior must also pass the unaccelerated GameTests.
execute if block 31 64 48 hbm:machine_blast_furnace run data merge block 31 64 48 {progress:1599}
data modify storage hbm_checkpoint:progression accelerated_blast set value 1b
