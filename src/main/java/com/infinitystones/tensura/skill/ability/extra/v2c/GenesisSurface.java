package com.infinitystones.tensura.skill.ability.extra.v2c;

import com.infinitystones.tensura.skill.ability.extra.v2c.GenesisPalette.Hand;
import com.infinitystones.tensura.skill.ability.extra.v2c.GenesisPalette.Profile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.PinkPetalsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.neoforged.neoforge.common.Tags;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rewrites one terrain column for Genesis. The top layers come from the target biome's real surface rules (run on a
 * synthetic copy of the column, so modded biomes bring their own rock and soil) with hand-tuned tables as fallback;
 * then themed blocks, light sources, fluid pools, plants and the biome's own placed features are added on top.
 */
final class GenesisSurface {
    private static final int MAX_FEATURES = 6;

    private final ServerLevel l;
    private final Profile pr;
    private final GenesisPalette pal;
    private final Hand hand;
    private final Set<Long> allowed;
    private final Holder<Biome> biome;
    private final Map<Long, ProtoChunk> protos = new HashMap<>();
    private final List<BlockState> scatter = new ArrayList<>(), lights = new ArrayList<>(), plantPool = new ArrayList<>();
    private NoiseBasedChunkGenerator gen;
    private NoiseGeneratorSettings ngs;
    private Aquifer.FluidPicker picker;
    private boolean emu;
    private int trees, feats;

    GenesisSurface(ServerLevel l, Profile pr, GenesisPalette pal, Hand hand, Holder<Biome> biome, Set<Long> allowed) {
        this.l = l; this.pr = pr; this.pal = pal; this.hand = hand; this.biome = biome; this.allowed = allowed;
        if (hand != null) {
            scatter.addAll(List.of(hand.scatter()));
            lights.addAll(List.of(hand.lights()));
            plantPool.addAll(List.of(hand.plants()));
        } else {
            scatter.addAll(pal.solids.values());
            lights.addAll(pal.lights.values());
            if (pr == Profile.AUTO) plantPool.addAll(pal.plants.values());
        }
        if ((hand == null || hand.emu()) && l.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator g) {
            NoiseGeneratorSettings s = g.generatorSettings().value();
            if (s.defaultBlock().is(Blocks.STONE)) {
                gen = g; ngs = s;
                int sea = s.seaLevel();
                Aquifer.FluidStatus lava = new Aquifer.FluidStatus(-54, Blocks.LAVA.defaultBlockState());
                Aquifer.FluidStatus water = new Aquifer.FluidStatus(sea, s.defaultFluid());
                picker = (x, y, z) -> y < Math.min(-54, sea) ? lava : water;
                emu = true;
            }
        }
    }

    void tick() { trees = 0; feats = 0; }

    void done() { protos.clear(); }

    // ------------------------------------------------------------------ helpers

    private boolean natural(BlockState s) {
        if (s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.BASE_STONE_NETHER)
                || s.is(BlockTags.TERRACOTTA) || s.is(BlockTags.NYLIUM) || s.is(BlockTags.SOUL_FIRE_BASE_BLOCKS)) return true;
        if (s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY) || s.is(Blocks.PACKED_ICE) || s.is(Blocks.SANDSTONE)
                || s.is(Blocks.RED_SANDSTONE) || s.is(Blocks.SCULK) || s.is(Blocks.DRIPSTONE_BLOCK) || s.is(Blocks.CALCITE)
                || s.is(Blocks.END_STONE) || s.is(Blocks.SMOOTH_BASALT) || s.is(Blocks.POWDER_SNOW) || s.is(Blocks.MAGMA_BLOCK)
                || s.is(Blocks.BONE_BLOCK) || s.is(Blocks.PRISMARINE)) return true;
        if (s.is(Tags.Blocks.STONES) || s.is(Tags.Blocks.GRAVELS) || s.is(Tags.Blocks.SANDS)) return true;
        return pal.solids.containsKey(s.getBlock()) || (pal.stone != null && s.is(pal.stone.getBlock()));
    }

    private boolean stoneFamily(BlockState s) { return s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.BASE_STONE_NETHER) || s.is(Blocks.END_STONE); }

    /** Y of the natural ground block of the column (below plants, snow, water), or Integer.MIN_VALUE. */
    private int ground(int x, int z, boolean[] wet) {
        int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        int min = l.getMinBuildHeight() + 1;
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (int n = 0; n < 14 && y > min; n++, y--) {
            BlockState st = l.getBlockState(mp.set(x, y, z));
            if (st.getFluidState().is(FluidTags.WATER) || st.is(Blocks.ICE)) { wet[0] = true; continue; }
            if (st.is(Blocks.SNOW) || st.canBeReplaced() || st.is(BlockTags.LEAVES)) continue;
            return natural(st) ? y : Integer.MIN_VALUE;
        }
        return Integer.MIN_VALUE;
    }

    private void set(BlockPos pos, BlockState to) {
        if (!l.getBlockState(pos).is(to.getBlock())) l.setBlock(pos, to, 2);
    }

    private void put(BlockPos at, BlockState s) {
        if (s.getBlock() instanceof DoublePlantBlock) {
            if (l.getBlockState(at.above()).isAir() && s.canSurvive(l, at)) DoublePlantBlock.placeAt(l, s, at, 2);
        } else if (s.canSurvive(l, at)) l.setBlock(at, s, 2);
    }

    private boolean chunksOk(BlockPos at, int reach) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                int cx = (at.getX() + dx * reach) >> 4, cz = (at.getZ() + dz * reach) >> 4;
                if (!allowed.contains(ChunkPos.asLong(cx, cz)) || !l.hasChunk(cx, cz)) return false;
            }
        return true;
    }

    // ------------------------------------------------------------------ column

    void column(int x, int z, RandomSource rnd) {
        if (pr == Profile.NONE || !l.hasChunk(x >> 4, z >> 4)) return;
        boolean[] wet = {false};
        int y = ground(x, z, wet);
        if (y == Integer.MIN_VALUE) return;
        BlockPos pos = new BlockPos(x, y, z);
        BlockState st = l.getBlockState(pos);
        if (wet[0]) {
            int top = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            BlockPos wp = new BlockPos(x, top, z);
            BlockState ws = l.getBlockState(wp);
            if (ws.is(Blocks.WATER) || ws.is(Blocks.ICE)) water(wp, ws, rnd);
        }

        int depth = 3 + rnd.nextInt(4);
        if (!(emu && layersEmu(pos, depth))) layersHand(pos, st, depth, rnd);

        BlockState gs = l.getBlockState(pos);
        if (!scatter.isEmpty() && rnd.nextFloat() < 0.065F) set(pos, scatter.get(rnd.nextInt(scatter.size())));
        else if (!lights.isEmpty() && rnd.nextFloat() < 0.004F && !wet[0]) set(pos, lights.get(rnd.nextInt(lights.size())));
        else if (!pal.fluids.isEmpty() && !wet[0] && rnd.nextFloat() < 0.025F) pool(pos, pal.fluids.get(rnd.nextInt(pal.fluids.size())));
        if (wet[0]) return;

        gs = l.getBlockState(pos);
        BlockPos above = pos.above();
        BlockState a = l.getBlockState(above);
        if (a.is(Blocks.SNOW) || a.is(BlockTags.REPLACEABLE) || a.is(BlockTags.FLOWERS)) {
            if (a.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) l.setBlock(above.above(), Blocks.AIR.defaultBlockState(), 2);
            l.setBlock(above, Blocks.AIR.defaultBlockState(), 2);
            a = Blocks.AIR.defaultBlockState();
        }
        if (!a.isAir() || !gs.isFaceSturdy(l, pos, Direction.UP)) return;

        legacyPlants(pr, above, rnd);
        if (!plantPool.isEmpty() && l.getBlockState(above).isAir() && rnd.nextFloat() < 0.13F) put(above, plantPool.get(rnd.nextInt(plantPool.size())));
        if (feats < MAX_FEATURES && !pal.features.isEmpty() && l.getBlockState(above).isAir() && rnd.nextFloat() < 0.026F) feature(above, rnd);
        if (l.getBlockState(above).isAir() && pr != Profile.SNOW && biome.value().shouldSnow(l, above)) put(above, Blocks.SNOW.defaultBlockState());
    }

    // ------------------------------------------------------------------ layers

    private boolean layersEmu(BlockPos pos, int depth) {
        ProtoChunk pc = proto(pos.getX() >> 4, pos.getZ() >> 4);
        if (pc == null) return false;
        BlockState dflt = ngs.defaultBlock();
        for (int d = 0; d <= depth; d++) {
            BlockPos bp = pos.below(d);
            BlockState cur = l.getBlockState(bp);
            if (!natural(cur)) break;
            BlockState nw = pc.getBlockState(bp);
            if (nw.isAir() || !nw.getFluidState().isEmpty() || nw.hasBlockEntity()) break;
            if (nw.is(cur.getBlock())) continue;
            if (nw.is(dflt.getBlock()) && stoneFamily(cur)) continue;
            l.setBlock(bp, nw, 2);
        }
        return true;
    }

    private ProtoChunk proto(int cx, int cz) {
        long key = ChunkPos.asLong(cx, cz);
        if (protos.containsKey(key)) return protos.get(key);
        ProtoChunk pc = null;
        try {
            pc = buildProto(cx, cz);
        } catch (RuntimeException | LinkageError ex) {
            emu = false;
        }
        protos.put(key, pc);
        return pc;
    }

    private ProtoChunk buildProto(int cx, int cz) {
        Registry<Biome> reg = l.registryAccess().registryOrThrow(Registries.BIOME);
        ProtoChunk pc = new ProtoChunk(new ChunkPos(cx, cz), UpgradeData.EMPTY, l, reg, null);
        BlockState dflt = ngs.defaultBlock(), fluid = ngs.defaultFluid();
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        boolean[] wet = {false};
        int min = l.getMinBuildHeight();
        for (int lx = 0; lx < 16; lx++)
            for (int lz = 0; lz < 16; lz++) {
                int wx = (cx << 4) + lx, wz = (cz << 4) + lz;
                wet[0] = false;
                int gy = ground(wx, wz, wet);
                if (gy == Integer.MIN_VALUE) continue;
                for (int y = Math.max(min, gy - 32); y <= gy; y++) pc.setBlockState(mp.set(wx, y, wz), dflt, false);
                if (wet[0]) {
                    int top = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx, wz) - 1;
                    for (int y = gy + 1; y <= top; y++) pc.setBlockState(mp.set(wx, y, wz), fluid, false);
                }
            }
        Heightmap.primeHeightmaps(pc, EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG));
        RandomState rs = l.getChunkSource().randomState();
        BiomeManager bm = new BiomeManager((qx, qy, qz) -> biome, 0L);
        NoiseChunk nc = pc.getOrCreateNoiseChunk(c -> NoiseChunk.forChunk(c, rs, marker(), ngs, picker, Blender.empty()));
        rs.surfaceSystem().buildSurface(rs, bm, reg, ngs.useLegacyRandomSource(), new WorldGenerationContext(gen, l), pc, nc, ngs.surfaceRule());
        return pc;
    }

    private static DensityFunctions.BeardifierOrMarker marker() {
        try {
            return (DensityFunctions.BeardifierOrMarker) Class.forName("net.minecraft.world.level.levelgen.DensityFunctions$BeardifierMarker").getEnumConstants()[0];
        } catch (ReflectiveOperationException | ClassCastException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void layersHand(BlockPos pos, BlockState st, int depth, RandomSource rnd) {
        if (hand != null) {
            set(pos, hand.top()[rnd.nextInt(hand.top().length)]);
            int f = 1 + rnd.nextInt(Math.min(4, depth));
            for (int d = 1; d <= f; d++) {
                BlockPos bp = pos.below(d);
                if (!natural(l.getBlockState(bp))) break;
                set(bp, hand.filler());
            }
            return;
        }
        if (pr == Profile.AUTO) {
            if (pal.stone == null) return;
            for (int d = 0; d <= Math.min(depth, 3); d++) {
                BlockPos bp = pos.below(d);
                if (!natural(l.getBlockState(bp))) break;
                set(bp, pal.stone);
            }
            return;
        }
        RandomSource r = rnd;
        int y = pos.getY();
        boolean inland = y >= l.getSeaLevel() + 3;
        boolean sandTop = st.is(BlockTags.SAND);
        BlockState top = null;
        switch (pr) {
            case DESERT -> top = Blocks.SAND.defaultBlockState();
            case BADLANDS -> top = Blocks.RED_SAND.defaultBlockState();
            case SNOW -> { if (!sandTop && !st.is(Blocks.SNOW_BLOCK)) top = Blocks.GRASS_BLOCK.defaultBlockState(); }
            case MUSHROOM -> { if (!sandTop || inland) top = Blocks.MYCELIUM.defaultBlockState(); }
            case TAIGA -> { if (!sandTop || inland) top = r.nextFloat() < 0.35F ? Blocks.PODZOL.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState(); }
            default -> { if (!sandTop || inland) top = Blocks.GRASS_BLOCK.defaultBlockState(); }
        }
        if (top != null) set(pos, top);
        if (pr == Profile.DESERT || pr == Profile.BADLANDS) {
            for (int d = 1; d <= 3; d++) {
                BlockPos bp = pos.below(d);
                if (!natural(l.getBlockState(bp))) break;
                if (pr == Profile.DESERT) l.setBlock(bp, d < 3 ? Blocks.SAND.defaultBlockState() : Blocks.SANDSTONE.defaultBlockState(), 2);
                else l.setBlock(bp, ((y - d) % 3 == 0 ? Blocks.ORANGE_TERRACOTTA : (y - d) % 3 == 1 ? Blocks.TERRACOTTA : Blocks.YELLOW_TERRACOTTA).defaultBlockState(), 2);
            }
        } else if (pr != Profile.OCEAN) {
            for (int d = 1; d <= 3; d++) {
                BlockPos bp = pos.below(d);
                BlockState bs = l.getBlockState(bp);
                if (!(bs.is(BlockTags.SAND) || bs.is(Blocks.SNOW_BLOCK))) break;
                set(bp, Blocks.DIRT.defaultBlockState());
            }
        }
    }

    // ------------------------------------------------------------------ water, pools, features

    private void water(BlockPos pos, BlockState st, RandomSource rnd) {
        BlockPos above = pos.above();
        boolean freeze = pr == Profile.SNOW || biome.value().shouldFreeze(l, pos, false);
        if (freeze) {
            if (st.is(Blocks.WATER) && st.getFluidState().isSource() && l.getBlockState(above).isAir()) l.setBlock(pos, Blocks.ICE.defaultBlockState(), 2);
            return;
        }
        if (st.is(Blocks.ICE)) { l.setBlock(pos, Blocks.WATER.defaultBlockState(), 3); return; }
        if (pr == Profile.DESERT || pr == Profile.BADLANDS) {
            int floor = l.getHeight(Heightmap.Types.OCEAN_FLOOR, pos.getX(), pos.getZ());
            if (pos.getY() - floor + 1 <= 2)
                for (int yy = floor; yy <= pos.getY(); yy++) {
                    BlockPos bp = new BlockPos(pos.getX(), yy, pos.getZ());
                    if (l.getBlockState(bp).is(Blocks.WATER)) l.setBlock(bp, Blocks.SAND.defaultBlockState(), 2);
                }
            return;
        }
        if (pr == Profile.SWAMP && rnd.nextFloat() < 0.08F && l.getBlockState(above).isAir() && st.getFluidState().isSource())
            l.setBlock(above, Blocks.LILY_PAD.defaultBlockState(), 2);
    }

    /** One-block pool, only where the ground around it is solid at the same level so the fluid cannot spread. */
    private void pool(BlockPos pos, BlockState fluid) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockState n = l.getBlockState(pos.relative(d));
            if (n.isAir() || !n.getFluidState().isEmpty() || n.canBeReplaced()) return;
        }
        BlockState below = l.getBlockState(pos.below());
        if (below.isAir() || !below.getFluidState().isEmpty() || below.canBeReplaced()) return;
        BlockState up = l.getBlockState(pos.above());
        if (!up.isAir() && !up.canBeReplaced()) return;
        l.setBlock(pos, fluid, 2);
    }

    private void feature(BlockPos at, RandomSource rnd) {
        if (!chunksOk(at, 10)) return;
        Holder<ConfiguredFeature<?, ?>> h = pal.features.get(rnd.nextInt(pal.features.size()));
        feats++;
        try {
            h.value().place(l, l.getChunkSource().getGenerator(), rnd, at);
        } catch (RuntimeException ignored) {
        }
    }

    // ------------------------------------------------------------------ legacy hand plants

    private void legacyPlants(Profile pr, BlockPos at, RandomSource rnd) {
        float f = rnd.nextFloat();
        switch (pr) {
            case DESERT -> {
                if (f < 0.018F) put(at, Blocks.DEAD_BUSH.defaultBlockState());
                else if (f < 0.024F) cactus(at, rnd);
            }
            case BADLANDS -> { if (f < 0.014F) put(at, Blocks.DEAD_BUSH.defaultBlockState()); }
            case SNOW -> {
                if (f < 0.009F) tree(TreeFeatures.SPRUCE, at, rnd);
                else put(at, Blocks.SNOW.defaultBlockState());
            }
            case MUSHROOM -> {
                if (f < 0.004F) tree(rnd.nextBoolean() ? TreeFeatures.HUGE_RED_MUSHROOM : TreeFeatures.HUGE_BROWN_MUSHROOM, at, rnd);
                else if (f < 0.03F) put(at, (rnd.nextBoolean() ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM).defaultBlockState());
            }
            case JUNGLE -> {
                if (f < 0.018F) tree(TreeFeatures.JUNGLE_TREE_NO_VINE, at, rnd);
                else if (f < 0.03F) put(at, Blocks.BAMBOO.defaultBlockState());
                else if (f < 0.2F) put(at, Blocks.SHORT_GRASS.defaultBlockState());
                else if (f < 0.27F) put(at, Blocks.FERN.defaultBlockState());
            }
            case CHERRY -> {
                if (f < 0.014F) tree(TreeFeatures.CHERRY, at, rnd);
                else if (f < 0.05F) put(at, Blocks.PINK_PETALS.defaultBlockState().setValue(PinkPetalsBlock.AMOUNT, 1 + rnd.nextInt(4)));
                else if (f < 0.17F) put(at, Blocks.SHORT_GRASS.defaultBlockState());
            }
            case FOREST -> {
                if (f < 0.02F) tree(rnd.nextInt(3) == 0 ? TreeFeatures.BIRCH : TreeFeatures.OAK, at, rnd);
                else if (f < 0.03F) flower(at, rnd);
                else if (f < 0.14F) put(at, Blocks.SHORT_GRASS.defaultBlockState());
            }
            case PLAINS -> {
                if (f < 0.0015F) tree(TreeFeatures.OAK, at, rnd);
                else if (f < 0.03F) flower(at, rnd);
                else if (f < 0.25F) put(at, Blocks.SHORT_GRASS.defaultBlockState());
            }
            case SWAMP -> {
                if (f < 0.011F) tree(TreeFeatures.SWAMP_OAK, at, rnd);
                else if (f < 0.02F) put(at, Blocks.BLUE_ORCHID.defaultBlockState());
                else if (f < 0.14F) put(at, Blocks.SHORT_GRASS.defaultBlockState());
            }
            case TAIGA -> {
                if (f < 0.018F) tree(TreeFeatures.SPRUCE, at, rnd);
                else if (f < 0.12F) put(at, Blocks.FERN.defaultBlockState());
                else if (f < 0.2F) put(at, Blocks.SHORT_GRASS.defaultBlockState());
            }
            case SAVANNA -> {
                if (f < 0.01F) tree(TreeFeatures.ACACIA, at, rnd);
                else if (f < 0.22F) put(at, Blocks.SHORT_GRASS.defaultBlockState());
            }
            default -> { }
        }
    }

    private void flower(BlockPos at, RandomSource rnd) {
        BlockState[] f = {Blocks.DANDELION.defaultBlockState(), Blocks.POPPY.defaultBlockState(), Blocks.AZURE_BLUET.defaultBlockState(), Blocks.OXEYE_DAISY.defaultBlockState()};
        put(at, f[rnd.nextInt(f.length)]);
    }

    private void cactus(BlockPos at, RandomSource rnd) {
        int h = 1 + rnd.nextInt(3);
        for (int i = 0; i < h; i++) {
            BlockPos p = at.above(i);
            if (!l.getBlockState(p).isAir() || !Blocks.CACTUS.defaultBlockState().canSurvive(l, p)) break;
            l.setBlock(p, Blocks.CACTUS.defaultBlockState(), 2);
        }
    }

    private void tree(ResourceKey<ConfiguredFeature<?, ?>> key, BlockPos at, RandomSource rnd) {
        if (trees >= 3 || !chunksOk(at, 5)) return;
        var h = l.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getHolder(key);
        if (h.isEmpty()) return;
        trees++;
        h.get().value().place(l, l.getChunkSource().getGenerator(), rnd, at);
    }
}
