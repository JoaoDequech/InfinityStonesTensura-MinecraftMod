package com.infinitystones.tensura;

import com.infinitystones.tensura.registry.ModBlocks;
import com.infinitystones.tensura.registry.ModItems;
import com.infinitystones.tensura.registry.ModMisc;
import com.infinitystones.tensura.registry.ModSounds;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Infinity Stones rebuilt on Tensura Reincarnated. Content keeps the original "infinity" namespace so the
 * original assets, worldgen, structures, loot tables and recipes load unchanged.
 */
@Mod(InfinityStones.MOD_ID)
public final class InfinityStones {
    public static final String MOD_ID = "infinity";
    public static final Logger LOGGER = LogUtils.getLogger();

    public InfinityStones(IEventBus modBus) {
        com.infinitystones.tensura.registry.ModMaterials.init();
        // Tensura/ManasCore skills register through Architectury's registrar during mod construction.
        com.infinitystones.tensura.skill.InfinitySkills.init();
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        com.infinitystones.tensura.registry.ModComponents.COMPONENTS.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        ModMisc.PARTICLES.register(modBus);
        ModMisc.POI.register(modBus);
        ModMisc.PROFESSIONS.register(modBus);
        ModMisc.TABS.register(modBus);
        com.infinitystones.tensura.skill.ability.extra.fear.FearRealm.GENERATORS.register(modBus);
        LOGGER.info("Infinity Stones (Tensura edition) registries attached");
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
