execute unless score global mapsGiven matches 1 run execute as @e[type=minecraft:villager,tag=!giveninfinitymaps] run function infinity:map_give

execute if entity @p[nbt={Inventory:[{id:"minecraft:map",components:{"minecraft:custom_data":{sanctummap:1b}}}]}] run function infinity:map_replace_sanctum
execute if entity @p[nbt={Inventory:[{id:"minecraft:map",components:{"minecraft:custom_data":{norsemap:1b}}}]}] run function infinity:map_replace_norse
execute if entity @p[nbt={Inventory:[{id:"minecraft:map",components:{"minecraft:custom_data":{moragmap:1b}}}]}] run function infinity:map_replace_morag
execute if entity @p[nbt={Inventory:[{id:"minecraft:map",components:{"minecraft:custom_data":{aethermap:1b}}}]}] run function infinity:map_replace_aether
execute if entity @p[nbt={Inventory:[{id:"minecraft:map",components:{"minecraft:custom_data":{sanctuarymap:1b}}}]}] run function infinity:map_replace_sanctuary