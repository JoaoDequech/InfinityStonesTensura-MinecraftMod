package com.infinitystones.tensura.skill.ability.extra.fear;

import com.infinitystones.tensura.InfinityStones;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

/** The dimension of fear (data/infinity/dimension/fear_realm.json) and its arena grid. */
public final class FearRealm {
    public static final ResourceKey<Level> DIM = ResourceKey.create(Registries.DIMENSION, InfinityStones.id("fear_realm"));
    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> GENERATORS = DeferredRegister.create(Registries.CHUNK_GENERATOR, InfinityStones.MOD_ID);

    static {
        GENERATORS.register("fear", () -> FearChunkGenerator.CODEC);
    }

    /** Arena slots sit on a 64 x 64 grid, one cell apart (2048 blocks); slot 2080 is cell (0, 0). */
    public static int centerX(int slot) { return (slot % 64 - 32) * FearTerrain.CELL; }
    public static int centerZ(int slot) { return (slot / 64 - 32) * FearTerrain.CELL; }
    public static final int SLOTS = 64 * 64;

    public static @Nullable ServerLevel level(MinecraftServer server) { return server == null ? null : server.getLevel(DIM); }

    private FearRealm() {}
}
