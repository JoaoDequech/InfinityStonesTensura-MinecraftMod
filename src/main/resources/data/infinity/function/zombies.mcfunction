playsound minecraft:entity.panda.sneeze block @s

# Summon zombies with guaranteed identities
# (thanos_zombie = the original thanos_classic skin power, read by MobSkins; the named ones add their own skin)
summon minecraft:zombie ~ ~ ~ {CustomName:'{"text":"Transfer Zombie","color":"green"}',CustomNameVisible:1b,Tags:["thanos_zombie","transfer_zombie"]}
summon minecraft:zombie ~ ~ ~ {CustomName:'{"text":"Pugmeowla Zombie","color":"dark_purple"}',CustomNameVisible:1b,Tags:["thanos_zombie","pugmeowla_zombie"]}
summon minecraft:zombie ~ ~ ~ {CustomName:'{"text":"Dark Zombie","color":"black"}',CustomNameVisible:1b,Tags:["thanos_zombie","dark_zombie"]}

# Extra zombies (optional ambience)
summon minecraft:zombie ~ ~ ~ {Tags:["thanos_zombie"]}
summon minecraft:zombie ~ ~ ~ {Tags:["thanos_zombie"]}
summon minecraft:zombie ~ ~ ~ {Tags:["thanos_zombie"]}

# Shared setup
team join peaceful
execute as @e[type=zombie,distance=..5] run team join peaceful
