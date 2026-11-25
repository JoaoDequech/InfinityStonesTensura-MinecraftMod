package com.infinitystones.tensura.skill.ability.extra.fear;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Pure-function generator of the fear realm (see {@link FearTerrain}); no carvers, no structures, no mobs. */
public final class FearChunkGenerator extends ChunkGenerator {
    public static final MapCodec<FearChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource)).apply(i, FearChunkGenerator::new));

    public FearChunkGenerator(BiomeSource source) { super(source); }

    @Override protected MapCodec<? extends ChunkGenerator> codec() { return CODEC; }

    @Override public void applyCarvers(WorldGenRegion region, long seed, RandomState random, BiomeManager biomes, StructureManager structures, ChunkAccess chunk, GenerationStep.Carving step) {}

    @Override public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState random, ChunkAccess chunk) {}

    @Override public void spawnOriginalMobs(WorldGenRegion region) {}

    @Override public int getGenDepth() { return 256; }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState random, StructureManager structures, ChunkAccess chunk) {
        FearTerrain.fill(chunk);
        return CompletableFuture.completedFuture(chunk);
    }

    @Override public int getSeaLevel() { return FearTerrain.BASE - 2; }

    @Override public int getMinY() { return 0; }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        return FearTerrain.height(x, z) + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        int top = FearTerrain.height(x, z);
        BlockState[] states = new BlockState[Math.max(1, level.getHeight())];
        for (int i = 0; i < states.length; i++) states[i] = level.getMinBuildHeight() + i <= top ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
        return new NoiseColumn(level.getMinBuildHeight(), states);
    }

    @Override public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {}
}
