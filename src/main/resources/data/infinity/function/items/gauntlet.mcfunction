# Dropped items are NOT turned into ground props any more: the prop could not keep the stack's data
# (gauntlet sockets, emptied artifact, name). Relics placed by structures still work through the stands below.
# one NBT scan, distance-limited to players, tag the matches

# act on the tag from here on -- no more NBT comparisons

execute as @e[type=armor_stand,tag=gauntlet_ground] at @s run infinity_ground_display infinity:infinity_gauntlet
execute as @e[type=armor_stand,tag=gauntlet_ground] at @s if entity @p[distance=..0.5] run tp @s ~ ~ ~ facing entity @p[distance=..1] feet

execute as @e[type=minecraft:interaction,tag=gauntlet_ground_interaction] on target run give @s infinity:infinity_gauntlet 1
execute as @e[type=minecraft:interaction,tag=gauntlet_ground_interaction] if data entity @s interaction run execute as @s on vehicle run kill @s
execute as @e[type=minecraft:interaction,tag=gauntlet_ground_interaction] if data entity @s interaction run kill @s
execute as @e[type=minecraft:interaction,tag=gauntlet_ground_interaction] run data remove entity @s interaction

tag @e[tag=gauntlet_found] remove gauntlet_found
