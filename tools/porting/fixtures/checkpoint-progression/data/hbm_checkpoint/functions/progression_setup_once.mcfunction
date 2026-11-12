# Test supplies and environment, never evidence of unassisted resource gathering.
fill 24 64 40 44 76 60 minecraft:air
fill 24 63 40 44 63 60 minecraft:stone
time set day
weather clear
gamerule doDaylightCycle false
gamerule doMobSpawning false
setworldspawn 28 64 45
tp @s 28.5 64 45.5
setblock 25 64 42 minecraft:furnace[facing=south]
setblock 26 64 42 minecraft:furnace[facing=south]
setblock 27 64 42 minecraft:furnace[facing=south]
setblock 28 64 42 minecraft:furnace[facing=south]
setblock 29 64 42 minecraft:furnace[facing=south]
setblock 30 64 42 minecraft:furnace[facing=south]
setblock 31 64 42 minecraft:furnace[facing=south]
setblock 32 64 42 minecraft:furnace[facing=south]
setblock 33 64 42 minecraft:furnace[facing=south]
setblock 34 64 42 minecraft:furnace[facing=south]
setblock 26 64 45 minecraft:crafting_table
setblock 25 64 45 minecraft:chest[facing=south]
setblock 24 64 45 minecraft:chest[facing=south]
# The source early aluminium path is cluster -> crystal -> two ingots.
setblock 28 64 45 hbm:cluster_aluminium
give @s minecraft:iron_ingot 87
give @s minecraft:copper_ingot 31
give @s hbm:ore_tungsten 3
give @s hbm:ore_beryllium 3
give @s hbm:ore_aluminium 1
give @s minecraft:clay_ball 36
give @s hbm:stone_resource_limestone 15
give @s minecraft:sand 29
give @s minecraft:redstone 1
give @s minecraft:potato 5
give @s minecraft:spruce_log 16
give @s minecraft:cobblestone 64
give @s minecraft:coal 64
give @s minecraft:iron_pickaxe 1
give @s minecraft:stone_axe 1
give @s minecraft:cooked_beef 32
data modify storage hbm_checkpoint:progression setup set value 1b
data modify storage hbm_checkpoint:progression provenance set value "Command-supplied inputs and vanilla workstations; HBM components must be crafted"
