package com.infinitystones.tensura.registry;

import com.infinitystones.tensura.InfinityStones;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, InfinityStones.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GauntletContents>> GAUNTLET_CONTENTS =
            COMPONENTS.register("gauntlet_contents", () -> DataComponentType.<GauntletContents>builder()
                    .persistent(GauntletContents.CODEC)
                    .networkSynchronized(GauntletContents.STREAM_CODEC)
                    .cacheEncoding()
                    .build());

    private ModComponents() {}
}
