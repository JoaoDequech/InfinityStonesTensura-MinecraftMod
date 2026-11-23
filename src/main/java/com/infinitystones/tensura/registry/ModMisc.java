package com.infinitystones.tensura.registry;

import com.google.common.collect.ImmutableSet;
import com.infinitystones.tensura.InfinityStones;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/** Particle type, Soul Guardian villager and the two original creative tabs. */
public final class ModMisc {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, InfinityStones.MOD_ID);
    public static final DeferredRegister<PoiType> POI = DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, InfinityStones.MOD_ID);
    public static final DeferredRegister<VillagerProfession> PROFESSIONS = DeferredRegister.create(Registries.VILLAGER_PROFESSION, InfinityStones.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, InfinityStones.MOD_ID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SHOCKWAVE =
            PARTICLES.register("shockwave", () -> new SimpleParticleType(true));

    // Original addon: soul_guardian POI on the command block, librarian work sound.
    public static final DeferredHolder<PoiType, PoiType> SOUL_GUARDIAN_POI = POI.register("soul_guardian",
            () -> new PoiType(ImmutableSet.copyOf(Blocks.COMMAND_BLOCK.getStateDefinition().getPossibleStates()), 1, 1));
    public static final DeferredHolder<VillagerProfession, VillagerProfession> SOUL_GUARDIAN = PROFESSIONS.register("soul_guardian",
            () -> new VillagerProfession("soul_guardian",
                    holder -> holder.is(SOUL_GUARDIAN_POI.getKey()), holder -> holder.is(SOUL_GUARDIAN_POI.getKey()),
                    com.google.common.collect.ImmutableSet.of(), com.google.common.collect.ImmutableSet.of(),
                    SoundEvents.VILLAGER_WORK_LIBRARIAN));

    private static final List<String> MAIN_TAB = List.of(
            "infinity:power_stone", "infinity:space_stone", "infinity:reality_stone", "infinity:soul_stone", "infinity:time_stone",
            "infinity:mind_stone", "infinity:orb", "infinity:tesseract", "infinity:aether", "infinity:best_friends_soul",
            "infinity:eye_of_agamotto", "infinity:scepter", "infinity:infinity_gauntlet", "infinity:infinity_gauntlet_cast",
            "infinity:cosmi-rod", "infinity:aspects_of_existance", "infinity:shards_of_existance", "infinity:thanos_classic_helmet",
            "infinity:thanos_classic_chestplate", "infinity:thanos_classic_leggings", "infinity:thanos_classic_boots",
            "infinity:thanos_mcu_helmet", "infinity:thanos_mcu_chestplate", "infinity:thanos_mcu_leggings", "infinity:thanos_mcu_boots",
            "infinity:double_edged_sword", "infinity:single_edged_sword", "infinity:fancy_bottle");
    private static final List<String> RESOURCES_TAB = List.of(
            "infinity:hammer", "infinity:injection", "infinity:coal_dust", "infinity:dwarf_star_meteor", "infinity:dwarf_star_alloy_dust",
            "infinity:dwarf_star_alloy_ingot", "infinity:dwarf_star_alloy_nugget", "infinity:dwarf_star_alloy_plate", "infinity:dwarf_star_alloy_block",
            "infinity:gold_dust", "infinity:gold_plate", "infinity:gold_titanium_alloy_dust", "infinity:gold_titanium_alloy_handle",
            "infinity:gold_titanium_alloy_ingot", "infinity:gold_titanium_alloy_nugget", "infinity:gold_titanium_alloy_plate",
            "infinity:gold_titanium_alloy_block", "infinity:iron_dust", "infinity:iron_plate", "infinity:steel_dust", "infinity:steel_ingot",
            "infinity:steel_nugget", "infinity:steel_plate", "infinity:steel_block", "infinity:titanium_dust", "infinity:titanium_ingot",
            "infinity:titanium_nugget", "infinity:titanium_plate", "infinity:titanium_block", "infinity:uru_ore", "infinity:raw_uru",
            "infinity:uru_blade", "infinity:uru_dust", "infinity:uru_ingot", "infinity:uru_nugget", "infinity:uru_plate", "infinity:uru_block",
            "infinity:morag_stone", "infinity:svartelheim_sand", "infinity:svartelheim_stone", "infinity:vormir_sand", "infinity:vormir_stone",
            "infinity:52-d_unit", "infinity:endosym_ingot", "infinity:ferro-titanium-gold_alloy_ingot", "infinity:soul_infused_netherite_ingot");

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB_MAIN = TABS.register("infinity", () -> tab(
            "Infinity Stone Core", "infinity:infinity_gauntlet", MAIN_TAB));
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB_RESOURCES = TABS.register("infinity_resources", () -> tab(
            "Infinity Stone Core Resources", "infinity:hammer", RESOURCES_TAB));

    private static CreativeModeTab tab(String title, String icon, List<String> ids) {
        return CreativeModeTab.builder()
                .title(Component.literal(title))
                .icon(() -> new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(icon))))
                .displayItems((params, out) -> ids.forEach(id -> {
                    // Items from optional companion mods (ultron, generators, tracker) are skipped when absent.
                    var key = ResourceLocation.parse(id);
                    if (BuiltInRegistries.ITEM.containsKey(key)) out.accept(BuiltInRegistries.ITEM.get(key));
                }))
                .build();
    }

    private ModMisc() {}
}
