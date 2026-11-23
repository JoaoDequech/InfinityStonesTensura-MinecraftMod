function infinity:items/tick

# --- throttle: the checks below don't need to run at 20Hz, so they only
# fire once/second (every 20th tick) instead of every tick ---
scoreboard players add global infinity_tickThrottle 1
execute if score global infinity_tickThrottle matches 20.. run scoreboard players set global infinity_tickThrottle 0

execute if score global infinity_tickThrottle matches 0 run function infinity:maps
execute if score global infinity_tickThrottle matches 0 run function infinity:items/stone_cleanup
execute if score global infinity_tickThrottle matches 0 run tag @e[type=minecraft:wolf,nbt={Sitting:1b}] add tamed_wolf

# Gauntlet ownership is handled by Palladium item-power providers in 1.21.1.
# Optional Infintrix and Infinity Protocol integrations are also data-driven.

execute as @e[tag=snap,limit=1] run function infinity:snap
