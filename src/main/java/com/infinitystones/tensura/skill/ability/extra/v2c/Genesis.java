package com.infinitystones.tensura.skill.ability.extra.v2c;

import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.ExtraSettings;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.MenuNet;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PinkPetalsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Reality, hidden: rewrites the real biome of a column (like /fillbiome) behind an expanding red wave and adapts the
 * surface (tops, light vegetation, water). Claimed chunks owned by others are skipped.
 */
public final class Genesis implements Ability {
    public static final Genesis INSTANCE = new Genesis();
    static final String NODE = "genesis", MODE = "reality.genesis", K = "infinity.extra.v2c.genesis.", KEY = "infinity_genesis_biome";
    private static final int RED = 0xFF2020, PAGE = 12;
    private static final String[] PRESETS = {"desert", "forest", "cherry_grove", "snowy_plains", "mushroom_fields", "jungle", "badlands", "plains", "swamp"};
    private static final String[] PRESET_ICONS = {"minecraft:sand", "minecraft:oak_sapling", "minecraft:cherry_sapling", "minecraft:snowball",
            "minecraft:red_mushroom", "minecraft:jungle_sapling", "minecraft:red_sand", "minecraft:grass_block", "minecraft:lily_pad"};

    private enum Profile { NONE, DESERT, BADLANDS, SNOW, MUSHROOM, JUNGLE, CHERRY, FOREST, PLAINS, SWAMP, TAIGA, SAVANNA }

    private record Entry(int type, String id) {}
    private static final int E_BIOME = 0, E_MORE = 1, E_NEXT = 2, E_BACK = 3;

    private static final Map<UUID, Integer> PAGES = new HashMap<>();
    private static final Set<UUID> ACTIVE = new HashSet<>();

    @Override public Stone stone() { return Stone.REALITY; }

    // ------------------------------------------------------------------ cast

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        if (alt) {
            PAGES.put(p.getUUID(), 0);
            return MenuNet.open(p, Stone.REALITY, NODE, this) || Fx.fail(p, K + "no_menu");
        }
        if (ACTIVE.contains(p.getUUID())) return Fx.fail(p, K + "busy");
        ServerLevel l = p.serverLevel();
        var hit = Aim.block(p, 128);
        if (hit.getType() == HitResult.Type.MISS) return Fx.fail(p, K + "no_target");
        ResourceLocation id = ResourceLocation.tryParse(Rules.data(p).contains(KEY) ? Rules.data(p).getString(KEY) : "minecraft:plains");
        var reg = l.registryAccess().registryOrThrow(Registries.BIOME);
        Holder<Biome> biome = id == null ? null : reg.getHolder(ResourceKey.create(Registries.BIOME, id)).orElse(null);
        if (biome == null) return Fx.fail(p, K + "no_biome");
        int r = ExtraSettings.get(p, MODE, 24, 8, 64, 4);
        Vec3 c = hit.getLocation();

        Set<Long> allowed = new HashSet<>();
        int denied = 0;
        for (int cx = Mth.floor(c.x - r) >> 4; cx <= Mth.floor(c.x + r) >> 4; cx++)
            for (int cz = Mth.floor(c.z - r) >> 4; cz <= Mth.floor(c.z + r) >> 4; cz++) {
                ChunkPos cp = new ChunkPos(cx, cz);
                if (ClaimGuard.canModify(p, l, cp)) allowed.add(cp.toLong()); else denied++;
            }
        if (allowed.isEmpty()) return Fx.fail(p, K + "claimed");

        start(p, l, c, r, biome, id, allowed);
        Fx.bar(p, Stone.REALITY, K + "begin", Component.translatable(biomeKey(id)), r);
        if (denied > 0) Fx.barGray(p, K + "partial");
        return true;
    }

    private static String biomeKey(ResourceLocation id) { return "biome." + id.getNamespace() + "." + id.getPath().replace('/', '.'); }

    private static void start(ServerPlayer p, ServerLevel l, Vec3 c, int r, Holder<Biome> biome, ResourceLocation id, Set<Long> allowed) {
        UUID owner = p.getUUID();
        ACTIVE.add(owner);
        Profile profile = classify(id, biome);
        int n = 0;
        int[] xs = new int[(2 * r + 1) * (2 * r + 1)], zs = new int[xs.length];
        long[] order = new long[xs.length];
        int bx = Mth.floor(c.x), bz = Mth.floor(c.z);
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > r * r) continue;
                xs[n] = bx + dx; zs[n] = bz + dz;
                order[n] = ((long) d2 << 32) | n;
                n++;
            }
        Arrays.sort(order, 0, n);
        final int cols = n;
        final int total = 24 + (int) (r * 1.1);

        float vis = Math.min(r, 24);
        FxNet.runes(l, 0, c.add(0, 0.1, 0), vis * 0.35F, RED, total + 30, 0.5F);
        FxNet.runes(l, 0, c.add(0, 0.12, 0), vis * 0.7F, 0xFF7A30, total + 30, -0.35F);
        FxNet.runes(l, 0, c.add(0, 0.14, 0), vis, 0x7A0A0A, total + 30, 0.2F);
        FxNet.pillar(l, 0, c, 1.6F, 40, RED, total);
        FxNet.flashAround(l, c, Math.min(r, 48), RED, 0.3F, 16);
        Fx.sound(l, c, SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.7F, 0.6F);
        Fx.sound(l, c, SoundEvents.BEACON_ACTIVATE, 0.6F, 0.6F);

        final int[] next = {0};
        final boolean[] done = {false};
        final int[] trees = {0};
        Tasks.add(new Tasks.Task() {
            @Override public boolean tick(int age) {
                float prog = Math.min(1F, (age + 1) / (float) total);
                double rad = r * (1 - (1 - prog) * (1 - prog));
                RandomSource rnd = l.random;
                trees[0] = 0;
                long lim = (long) (rad * rad);
                while (next[0] < cols && (order[next[0]] >>> 32) <= lim) {
                    int i = (int) (order[next[0]] & 0xFFFFFFFFL);
                    next[0]++;
                    if (allowed.contains(ChunkPos.asLong(xs[i] >> 4, zs[i] >> 4))) surface(l, profile, xs[i], zs[i], rnd, trees, allowed);
                }
                if (age % 6 == 0 || prog >= 1F) biomes(l, biome, allowed, c.x, c.z, rad);
                wave(l, c, rad, age);
                if (age % 10 == 0) Fx.sound(l, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6F, 0.5F + prog);
                if (prog >= 1F) { done[0] = true; return false; }
                return true;
            }
            @Override public void end() {
                ACTIVE.remove(owner);
                if (!done[0]) biomes(l, biome, allowed, c.x, c.z, r);
                Vec3 top = Vec3.atCenterOf(l.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(c)));
                FxNet.shockwave(l, top, Math.min(r, 40), RED, 30);
                FxNet.shake(l, c, Math.min(r, 48) + 16, 0.5F, 20);
                Fx.sound(l, c, SoundEvents.RESPAWN_ANCHOR_DEPLETE, 0.7F, 0.7F);
                Fx.sound(l, c, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.4F, 0.6F);
                ServerPlayer pl = V2CNet.player(l.getServer(), owner);
                if (pl != null) Fx.bar(pl, Stone.REALITY, K + "done");
            }
        });
    }

    private static void wave(ServerLevel l, Vec3 c, double rad, int age) {
        if (rad < 1) return;
        int pts = Mth.clamp((int) (rad * 2.2), 16, 90);
        RandomSource rnd = l.random;
        for (int i = 0; i < pts; i++) {
            double a = Math.PI * 2 * i / pts + age * 0.05;
            int x = Mth.floor(c.x + Math.cos(a) * rad), z = Mth.floor(c.z + Math.sin(a) * rad);
            if (!l.hasChunk(x >> 4, z >> 4)) continue;
            double y = l.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 0.15;
            Vec3 v = new Vec3(x + 0.5, y, z + 0.5);
            Fx.far(l, Fx.dust(i % 4 == 0 ? 0xFF8A40 : RED, 1.7F), v, 1, 0.1, 0, 96);
            Fx.far(l, Fx.dust(0x9A0A0A, 1.3F), v.add(0, 0.9 + rnd.nextDouble() * 1.2, 0), 1, 0.15, 0, 96);
            if (i % 5 == 0) Fx.far(l, net.minecraft.core.particles.ParticleTypes.CRIMSON_SPORE, v.add(0, 1.5, 0), 1, 0.4, 0, 96);
        }
    }

    // ------------------------------------------------------------------ biome

    private static void biomes(ServerLevel l, Holder<Biome> biome, Set<Long> allowed, double cx, double cz, double rad) {
        if (rad < 1) return;
        List<ChunkAccess> changed = new ArrayList<>();
        var sampler = l.getChunkSource().randomState().sampler();
        double r2 = rad * rad;
        for (int x = Mth.floor(cx - rad) >> 4; x <= Mth.floor(cx + rad) >> 4; x++)
            for (int z = Mth.floor(cz - rad) >> 4; z <= Mth.floor(cz + rad) >> 4; z++) {
                if (!allowed.contains(ChunkPos.asLong(x, z))) continue;
                LevelChunk chunk = l.getChunkSource().getChunkNow(x, z);
                if (chunk == null) continue;
                int[] n = {0};
                chunk.fillBiomesFromNoise((qx, qy, qz, s) -> {
                    Holder<Biome> cur = chunk.getNoiseBiome(qx, qy, qz);
                    double dx = qx * 4 + 2 - cx, dz = qz * 4 + 2 - cz;
                    if (dx * dx + dz * dz <= r2 && !cur.equals(biome)) { n[0]++; return biome; }
                    return cur;
                }, sampler);
                if (n[0] > 0) { chunk.setUnsaved(true); changed.add(chunk); }
            }
        if (!changed.isEmpty()) l.getChunkSource().chunkMap.resendBiomesForChunks(changed);
    }

    private static Profile classify(ResourceLocation id, Holder<Biome> b) {
        String p = id.getPath();
        if (p.contains("desert")) return Profile.DESERT;
        if (p.contains("badlands") || p.contains("mesa")) return Profile.BADLANDS;
        if (p.contains("mushroom")) return Profile.MUSHROOM;
        if (p.contains("cherry")) return Profile.CHERRY;
        if (p.contains("snowy") || p.contains("frozen") || p.contains("ice") || p.contains("grove")) return Profile.SNOW;
        if (p.contains("jungle") || p.contains("bamboo")) return Profile.JUNGLE;
        if (p.contains("swamp") || p.contains("mangrove")) return Profile.SWAMP;
        if (p.contains("taiga")) return Profile.TAIGA;
        if (p.contains("savanna")) return Profile.SAVANNA;
        if (p.contains("forest") || p.contains("birch")) return Profile.FOREST;
        if (p.contains("ocean") || p.contains("river") || p.contains("beach") || p.contains("cave") || p.contains("nether")
                || p.contains("end") || p.contains("void") || p.contains("dripstone") || p.contains("lush") || p.contains("deep_dark")) return Profile.NONE;
        Biome bi = b.value();
        float t = bi.getBaseTemperature();
        if (!bi.hasPrecipitation() && t > 1.5F) return Profile.DESERT;
        if (t < 0.15F) return Profile.SNOW;
        return Profile.PLAINS;
    }

    // ------------------------------------------------------------------ surface

    private static boolean natural(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(Blocks.SNOW_BLOCK);
    }

    private static void surface(ServerLevel l, Profile pr, int x, int z, RandomSource rnd, int[] trees, Set<Long> allowed) {
        if (pr == Profile.NONE || !l.hasChunk(x >> 4, z >> 4)) return;
        int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        BlockPos pos = new BlockPos(x, y, z);
        BlockState st = l.getBlockState(pos);
        for (int i = 0; i < 2 && (st.is(Blocks.SNOW) || st.canBeReplaced()) && y > l.getMinBuildHeight() + 1; i++) {
            pos = pos.below(); y--; st = l.getBlockState(pos);
        }
        if (y <= l.getMinBuildHeight() + 1) return;
        if (st.is(Blocks.WATER) || st.is(Blocks.ICE)) { water(l, pr, pos, st, rnd); return; }
        if (!natural(st)) return;

        boolean inland = y >= l.getSeaLevel() + 3;
        boolean sandTop = st.is(BlockTags.SAND);
        BlockState top = null;
        switch (pr) {
            case DESERT -> top = Blocks.SAND.defaultBlockState();
            case BADLANDS -> top = Blocks.RED_SAND.defaultBlockState();
            case SNOW -> { if (!sandTop && !st.is(Blocks.SNOW_BLOCK)) top = Blocks.GRASS_BLOCK.defaultBlockState(); }
            case MUSHROOM -> { if (!sandTop || inland) top = Blocks.MYCELIUM.defaultBlockState(); }
            case TAIGA -> { if (!sandTop || inland) top = rnd.nextFloat() < 0.35F ? Blocks.PODZOL.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState(); }
            default -> { if (!sandTop || inland) top = Blocks.GRASS_BLOCK.defaultBlockState(); }
        }
        if (top != null && !st.is(top.getBlock())) l.setBlock(pos, top, 2);
        if (pr == Profile.DESERT || pr == Profile.BADLANDS) {
            for (int d = 1; d <= 3; d++) {
                BlockPos bp = pos.below(d);
                BlockState bs = l.getBlockState(bp);
                if (!natural(bs) || bs.is(Blocks.SNOW_BLOCK)) break;
                if (pr == Profile.DESERT) l.setBlock(bp, d < 3 ? Blocks.SAND.defaultBlockState() : Blocks.SANDSTONE.defaultBlockState(), 2);
                else l.setBlock(bp, ((y - d) % 3 == 0 ? Blocks.ORANGE_TERRACOTTA : (y - d) % 3 == 1 ? Blocks.TERRACOTTA : Blocks.YELLOW_TERRACOTTA).defaultBlockState(), 2);
            }
        }
        BlockPos above = pos.above();
        BlockState a = l.getBlockState(above);
        if (a.is(Blocks.SNOW) || a.is(BlockTags.REPLACEABLE) || a.is(BlockTags.FLOWERS)) {
            if (a.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) l.setBlock(above.above(), Blocks.AIR.defaultBlockState(), 2);
            l.setBlock(above, Blocks.AIR.defaultBlockState(), 2);
            a = Blocks.AIR.defaultBlockState();
        }
        if (!a.isAir() || !l.getBlockState(pos).isFaceSturdy(l, pos, net.minecraft.core.Direction.UP)) return;
        plants(l, pr, pos, above, rnd, trees, allowed);
    }

    private static void put(ServerLevel l, BlockPos at, BlockState s, int flags) {
        if (s.canSurvive(l, at)) l.setBlock(at, s, flags);
    }

    private static void plants(ServerLevel l, Profile pr, BlockPos ground, BlockPos at, RandomSource rnd, int[] trees, Set<Long> allowed) {
        float f = rnd.nextFloat();
        switch (pr) {
            case DESERT -> {
                if (f < 0.018F) put(l, at, Blocks.DEAD_BUSH.defaultBlockState(), 2);
                else if (f < 0.024F) cactus(l, at, rnd);
            }
            case BADLANDS -> { if (f < 0.014F) put(l, at, Blocks.DEAD_BUSH.defaultBlockState(), 2); }
            case SNOW -> {
                if (f < 0.009F) tree(l, TreeFeatures.SPRUCE, at, rnd, trees, allowed);
                else put(l, at, Blocks.SNOW.defaultBlockState(), 3);
            }
            case MUSHROOM -> {
                if (f < 0.004F) tree(l, rnd.nextBoolean() ? TreeFeatures.HUGE_RED_MUSHROOM : TreeFeatures.HUGE_BROWN_MUSHROOM, at, rnd, trees, allowed);
                else if (f < 0.03F) put(l, at, (rnd.nextBoolean() ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM).defaultBlockState(), 2);
            }
            case JUNGLE -> {
                if (f < 0.018F) tree(l, TreeFeatures.JUNGLE_TREE_NO_VINE, at, rnd, trees, allowed);
                else if (f < 0.03F) put(l, at, Blocks.BAMBOO.defaultBlockState(), 2);
                else if (f < 0.2F) put(l, at, Blocks.SHORT_GRASS.defaultBlockState(), 2);
                else if (f < 0.27F) put(l, at, Blocks.FERN.defaultBlockState(), 2);
            }
            case CHERRY -> {
                if (f < 0.014F) tree(l, TreeFeatures.CHERRY, at, rnd, trees, allowed);
                else if (f < 0.05F) put(l, at, Blocks.PINK_PETALS.defaultBlockState().setValue(PinkPetalsBlock.AMOUNT, 1 + rnd.nextInt(4)), 2);
                else if (f < 0.17F) put(l, at, Blocks.SHORT_GRASS.defaultBlockState(), 2);
            }
            case FOREST -> {
                if (f < 0.02F) tree(l, rnd.nextInt(3) == 0 ? TreeFeatures.BIRCH : TreeFeatures.OAK, at, rnd, trees, allowed);
                else if (f < 0.03F) flower(l, at, rnd);
                else if (f < 0.14F) put(l, at, Blocks.SHORT_GRASS.defaultBlockState(), 2);
            }
            case PLAINS -> {
                if (f < 0.0015F) tree(l, TreeFeatures.OAK, at, rnd, trees, allowed);
                else if (f < 0.03F) flower(l, at, rnd);
                else if (f < 0.25F) put(l, at, Blocks.SHORT_GRASS.defaultBlockState(), 2);
            }
            case SWAMP -> {
                if (f < 0.011F) tree(l, TreeFeatures.SWAMP_OAK, at, rnd, trees, allowed);
                else if (f < 0.02F) put(l, at, Blocks.BLUE_ORCHID.defaultBlockState(), 2);
                else if (f < 0.14F) put(l, at, Blocks.SHORT_GRASS.defaultBlockState(), 2);
            }
            case TAIGA -> {
                if (f < 0.018F) tree(l, TreeFeatures.SPRUCE, at, rnd, trees, allowed);
                else if (f < 0.12F) put(l, at, Blocks.FERN.defaultBlockState(), 2);
                else if (f < 0.2F) put(l, at, Blocks.SHORT_GRASS.defaultBlockState(), 2);
            }
            case SAVANNA -> {
                if (f < 0.01F) tree(l, TreeFeatures.ACACIA, at, rnd, trees, allowed);
                else if (f < 0.22F) put(l, at, Blocks.SHORT_GRASS.defaultBlockState(), 2);
            }
            default -> { }
        }
    }

    private static void flower(ServerLevel l, BlockPos at, RandomSource rnd) {
        BlockState[] f = {Blocks.DANDELION.defaultBlockState(), Blocks.POPPY.defaultBlockState(), Blocks.AZURE_BLUET.defaultBlockState(), Blocks.OXEYE_DAISY.defaultBlockState()};
        put(l, at, f[rnd.nextInt(f.length)], 2);
    }

    private static void cactus(ServerLevel l, BlockPos at, RandomSource rnd) {
        int h = 1 + rnd.nextInt(3);
        for (int i = 0; i < h; i++) {
            BlockPos p = at.above(i);
            if (!l.getBlockState(p).isAir() || !Blocks.CACTUS.defaultBlockState().canSurvive(l, p)) break;
            l.setBlock(p, Blocks.CACTUS.defaultBlockState(), 2);
        }
    }

    private static void tree(ServerLevel l, ResourceKey<ConfiguredFeature<?, ?>> key, BlockPos at, RandomSource rnd, int[] trees, Set<Long> allowed) {
        if (trees[0] >= 3) return;
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                if (!allowed.contains(ChunkPos.asLong((at.getX() + dx * 5) >> 4, (at.getZ() + dz * 5) >> 4))) return;
        var h = l.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getHolder(key);
        if (h.isEmpty()) return;
        trees[0]++;
        h.get().value().place(l, l.getChunkSource().getGenerator(), rnd, at);
    }

    private static void water(ServerLevel l, Profile pr, BlockPos pos, BlockState st, RandomSource rnd) {
        BlockPos above = pos.above();
        if (pr == Profile.SNOW) {
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

    // ------------------------------------------------------------------ menu

    private List<Entry> entries(ServerPlayer p) {
        int page = PAGES.getOrDefault(p.getUUID(), 0);
        List<Entry> out = new ArrayList<>();
        if (page == 0) {
            for (String s : PRESETS) out.add(new Entry(E_BIOME, "minecraft:" + s));
            out.add(new Entry(E_MORE, ""));
            return out;
        }
        List<String> all = new ArrayList<>();
        for (ResourceLocation rl : p.serverLevel().registryAccess().registryOrThrow(Registries.BIOME).keySet()) all.add(rl.toString());
        all.sort(String::compareTo);
        int from = (page - 1) * PAGE;
        for (int i = from; i < Math.min(all.size(), from + PAGE); i++) out.add(new Entry(E_BIOME, all.get(i)));
        if (from + PAGE < all.size()) out.add(new Entry(E_NEXT, ""));
        out.add(new Entry(E_BACK, ""));
        return out;
    }

    @Override
    public List<MenuNet.Option> menu(ServerPlayer p) {
        String cur = Rules.data(p).contains(KEY) ? Rules.data(p).getString(KEY) : "minecraft:plains";
        List<MenuNet.Option> out = new ArrayList<>();
        for (Entry e : entries(p)) {
            switch (e.type()) {
                case E_BIOME -> {
                    ResourceLocation id = ResourceLocation.tryParse(e.id());
                    int pi = Arrays.asList(PRESETS).indexOf(id == null ? "" : id.getPath());
                    boolean preset = pi >= 0 && "minecraft".equals(id.getNamespace()) && PAGES.getOrDefault(p.getUUID(), 0) == 0;
                    boolean sel = e.id().equals(cur);
                    out.add(preset ? MenuNet.opt(K + PRESETS[pi], PRESET_ICONS[pi], sel)
                            : new MenuNet.Option(id == null ? e.id() : biomeKey(id), K + "any.desc", "minecraft:grass_block", sel));
                }
                case E_MORE -> out.add(MenuNet.opt(K + "more", "minecraft:compass", false));
                case E_NEXT -> out.add(MenuNet.opt(K + "next", "minecraft:arrow", false));
                default -> out.add(MenuNet.opt(K + "back", "minecraft:barrier", false));
            }
        }
        return out;
    }

    @Override
    public void pick(ServerPlayer p, int index) {
        List<Entry> es = entries(p);
        if (index < 0 || index >= es.size()) return;
        Entry e = es.get(index);
        int page = PAGES.getOrDefault(p.getUUID(), 0);
        switch (e.type()) {
            case E_MORE -> { PAGES.put(p.getUUID(), 1); MenuNet.open(p, Stone.REALITY, NODE, this); }
            case E_NEXT -> { PAGES.put(p.getUUID(), page + 1); MenuNet.open(p, Stone.REALITY, NODE, this); }
            case E_BACK -> { PAGES.put(p.getUUID(), page <= 1 ? 0 : page - 1); MenuNet.open(p, Stone.REALITY, NODE, this); }
            default -> {
                Rules.data(p).putString(KEY, e.id());
                ResourceLocation id = ResourceLocation.tryParse(e.id());
                if (id != null) Fx.bar(p, Stone.REALITY, K + "chosen", Component.translatable(biomeKey(id)));
            }
        }
    }

    private Genesis() {}
}
