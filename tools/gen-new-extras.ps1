param([string]$Out)
# stone|id|activation|cost|cooldown|parents(;)|requires(;)|x|y|icon
$rows = @'
mind|telepathy|press|6|60|intangibility||-1|0|minecraft:bell
mind|mind_read|press|10|120|telepathy||-1|1|minecraft:spyglass
mind|calm_wave|press|12|500|mind_read||-1|2|minecraft:amethyst_shard
mind|mind_puppet|hold|16|300|mind_read||-2|1.5|minecraft:lead
mind|psychic_avatar|toggle|20|0|calm_wave||-2|2.5|minecraft:armor_stand
power|power_roar|press|8|160|impower||-1|-0.5|minecraft:goat_horn
power|crusher|press|10|30|power_roar||-1|-1.5|minecraft:netherite_pickaxe
power|stellar_fists|toggle|12|0|power_roar||-2|-1|minecraft:blaze_powder
power|cosmic_arc|press|14|120|crusher||-1|-2.5|minecraft:lightning_rod
power|cataclysm_forge|hold|20|240|cosmic_arc||-2|-3|minecraft:fire_charge
power|falling_star|press|24|600|cataclysm_forge|infinity:space_stone|-2|-4|minecraft:nether_star
space|pocket_dimension|press|8|20|teleport||-2|0|minecraft:ender_chest
space|abyss_gaze|press|10|60|teleport||-2|-1|minecraft:ender_eye
space|scene_anchor|press|14|100|pocket_dimension||-3|0|minecraft:respawn_anchor
space|fold_distance|hold|16|80|abyss_gaze||-3|-1|minecraft:chorus_fruit
space|wormhole_pair|press|20|40|fold_distance||-3|-2|minecraft:ender_pearl
reality|impossible_rain|press|10|400|weather_wheel||3|-1|minecraft:cherry_leaves
reality|reality_pet|press|14|20|effects||3|-2|minecraft:name_tag
reality|local_decree|press|18|600|impossible_rain||4|-1|minecraft:writable_book
reality|broken_reality|toggle|24|0|reality_pet||4|-2.5|minecraft:crying_obsidian
soul|soul_sight|toggle|8|0|glow||1|1|minecraft:soul_lantern
soul|soul_reaping|press|12|80|soul_sight||2|1|minecraft:soul_campfire
soul|soul_reliquary|press|16|60|soul_reaping||3|1|minecraft:echo_shard
soul|return_pact|press|14|600|soul_sight||1|2|minecraft:totem_of_undying
soul|soul_link|press|20|400|return_pact|infinity:mind_stone|2|2|minecraft:chain
time|time_field|press|10|500|fast_forward||0|3|minecraft:clock
time|age_shift|press|12|80|time_field||1|3|minecraft:bone_meal
time|foresight|toggle|16|0|age_shift||2|3|minecraft:recovery_compass
time|stolen_moment|press|22|900|foresight||3|3|minecraft:blue_ice
time|restoration|press|20|1200|age_shift|infinity:reality_stone|1|4|minecraft:end_crystal
soul|gauntlet_snap|press|64|0||ALL|4|2|infinity:infinity_gauntlet
soul|gauntlet_return|press|48|0|gauntlet_snap|ALL|5|2|minecraft:heart_of_the_sea
soul|gauntlet_undo_death|press|40|1200|gauntlet_snap|ALL|4|3|minecraft:enchanted_golden_apple
power|gauntlet_execution|press|64|0||ALL|-3|-3|minecraft:wither_skeleton_skull
power|gauntlet_purge|press|48|0|gauntlet_execution|ALL|-3|-4|minecraft:zombie_head
mind|gauntlet_eye|press|32|200||ALL|-3|0.5|minecraft:observer
mind|gauntlet_dominion|press|56|1200|gauntlet_eye|ALL|-3|1.5|minecraft:dragon_head
time|gauntlet_stop|press|56|1800||ALL|4|3|minecraft:packed_ice
reality|gauntlet_transmute|press|48|300||ALL|5|-1|minecraft:egg
reality|gauntlet_destroy|toggle|32|0|gauntlet_transmute|ALL|5|-2.5|minecraft:barrier
space|gauntlet_convergence|press|48|600||ALL|-4|-1|infinity:infinity_gauntlet
'@ -split "`n" | Where-Object { $_.Trim() -ne '' }
# "ALL" in the requires column = every other stone (gauntlet powers: the six together)
$powerId = @{ mind = 'infinity:mind_stone'; power = 'infinity:power_stone_passive'; space = 'infinity:space_stone';
              reality = 'infinity:reality_stone'; soul = 'infinity:soul_stone'; time = 'infinity:time_stone' }

$inv = [Globalization.CultureInfo]::InvariantCulture
function J([string]$s) { '"' + $s.Replace('\','\\').Replace('"','\"') + '"' }
function Arr($list) { if ($list.Count -eq 0) { '[]' } else { '[' + (($list | ForEach-Object { J $_ }) -join ', ') + ']' } }

$byStone = [ordered]@{}
foreach ($r in $rows) {
  $f = $r.Trim().Split('|')
  $stone = $f[0]; $id = $f[1]
  $parents = @($f[5].Split(';') | Where-Object { $_ -ne '' })
  $req = if ($f[6] -eq 'ALL') { @($powerId.Keys | Where-Object { $_ -ne $stone } | Sort-Object | ForEach-Object { $powerId[$_] }) }
         else { @($f[6].Split(';') | Where-Object { $_ -ne '' }) }
  $x = [double]::Parse($f[7], $inv); $y = [double]::Parse($f[8], $inv)
  $node = @"
    {
      "id": $(J $id),
      "original_type": $(J "infinity:$id"),
      "title": $(J "@ability.infinity.${stone}_stone.$id.title"),
      "description": $(J "@ability.infinity.${stone}_stone.$id.description"),
      "icon": $(J $f[9]),
      "cost": { "type": "item", "item": "infinity:aspects_of_existance", "amount": $($f[3]) },
      "parents": $(Arr $parents),
      "requires_stones": $(Arr $req),
      "activation": $(J $f[2]),
      "cooldown_ticks": $($f[4]),
      "in_tree": true,
      "in_bar": true,
      "gui_position": [$($x.ToString($inv)), $($y.ToString($inv))],
      "commands": [],
      "first_tick_commands": [],
      "last_tick_commands": []
    }
"@
  if (-not $byStone.Contains($stone)) { $byStone[$stone] = New-Object System.Collections.ArrayList }
  [void]$byStone[$stone].Add($node.TrimEnd())
}

# keep the existing Will node in reality
$will = @'
    {
      "id": "will_construct",
      "original_type": "infinity:will",
      "title": "@ability.infinity.reality_stone.will.title",
      "description": "@ability.infinity.reality_stone.will.description",
      "icon": "minecraft:lime_stained_glass",
      "cost": { "type": "item", "item": "infinity:aspects_of_existance", "amount": 16 },
      "parents": ["weather_wheel"],
      "requires_stones": [],
      "activation": "press",
      "cooldown_ticks": 0,
      "in_tree": true,
      "in_bar": true,
      "gui_position": [-1, -1.5],
      "commands": [],
      "first_tick_commands": [],
      "last_tick_commands": []
    }
'@
$byStone['reality'].Insert(0, $will.TrimEnd())

$utf8 = New-Object System.Text.UTF8Encoding($false)
foreach ($s in $byStone.Keys) {
  $json = "{`n  `"nodes`": [`n" + ($byStone[$s] -join ",`n") + "`n  ]`n}`n"
  [IO.File]::WriteAllText((Join-Path $Out "$s.json"), $json.Replace("`r`n", "`n"), $utf8)
  "$s : $($byStone[$s].Count) nodes"
}
