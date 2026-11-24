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
import com.infinitystones.tensura.skill.ability.extra.v2c.GenesisPalette.Profile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
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
 * Reality, hidden: rewrites the real biome of an area (like /fillbiome) behind an expanding red wave and brings the
 * biome's own terrain: surface layers from its surface rules, themed blocks, plants, fluids and placed features.
 * Claims only protect block changes: chunks claimed by others keep their blocks but still get the biome.
 */
public final class Genesis implements Ability {
    public static final Genesis INSTANCE = new Genesis();
    static final String NODE = "genesis", MODE = "reality.genesis", K = "infinity.extra.v2c.genesis.", KEY = "infinity_genesis_biome";
    private static final int PAGE = 12;
    private static final String[] PRESETS = {"desert", "forest", "cherry_grove", "snowy_plains", "mushroom_fields", "jungle", "badlands", "plains", "swamp"};
    private static final String[] PRESET_ICONS = {"minecraft:sand", "minecraft:oak_sapling", "minecraft:cherry_sapling", "minecraft:snowball",
            "minecraft:red_mushroom", "minecraft:jungle_sapling", "minecraft:red_sand", "minecraft:grass_block", "minecraft:lily_pad"};

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

        start(p, l, c, r, biome, id, allowed);
        Fx.bar(p, Stone.REALITY, K + "begin", Component.translatable(biomeKey(id)), r);
        if (denied > 0) Fx.barGray(p, K + "partial");
        return true;
    }

    private static String biomeKey(ResourceLocation id) { return "biome." + id.getNamespace() + "." + id.getPath().replace('/', '.'); }

    private static void start(ServerPlayer p, ServerLevel l, Vec3 c, int r, Holder<Biome> biome, ResourceLocation id, Set<Long> allowed) {
        UUID owner = p.getUUID();
        ACTIVE.add(owner);
        Profile profile = GenesisPalette.classify(id, biome);
        GenesisPalette palette = GenesisPalette.of(l, biome);
        GenesisSurface surface = new GenesisSurface(l, profile, palette, GenesisPalette.hand(profile, id), biome, allowed);
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
        final int waveId = V2CNet.newId();

        float vis = Math.min(r, 24);
        FxNet.runes(l, 0, c.add(0, 0.1, 0), vis * 0.35F, 0xFF2020, total + 30, 0.5F);
        FxNet.runes(l, 0, c.add(0, 0.12, 0), vis * 0.7F, 0xFF7A30, total + 30, -0.35F);
        FxNet.runes(l, 0, c.add(0, 0.14, 0), vis, 0x7A0A0A, total + 30, 0.2F);
        FxNet.pillar(l, 0, c, 1.6F, 40, 0xFF2020, total);
        FxNet.flashAround(l, c, Math.min(r, 48), 0xFF2020, 0.3F, 16);
        V2CNet.near(l, c, r + 140, V2CNet.msg(V2CNet.GENESIS, waveId, r, total, 0F, c, 0));
        Fx.sound(l, c, SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.7F, 0.6F);
        Fx.sound(l, c, SoundEvents.BEACON_ACTIVATE, 0.6F, 0.6F);

        final int[] next = {0};
        final boolean[] done = {false};
        Tasks.add(new Tasks.Task() {
            @Override public boolean tick(int age) {
                float prog = Math.min(1F, (age + 1) / (float) total);
                double rad = r * (1 - (1 - prog) * (1 - prog));
                RandomSource rnd = l.random;
                surface.tick();
                if (age % 3 == 0 || prog >= 1F) biomes(l, biome, c.x, c.z, Math.min(r, rad + 6));
                long lim = (long) (rad * rad);
                while (next[0] < cols && (order[next[0]] >>> 32) <= lim) {
                    int i = (int) (order[next[0]] & 0xFFFFFFFFL);
                    next[0]++;
                    if (allowed.contains(ChunkPos.asLong(xs[i] >> 4, zs[i] >> 4))) surface.column(xs[i], zs[i], rnd);
                }
                if (age % 8 == 0) sweep(l, c, rad, prog);
                if (prog >= 1F) { done[0] = true; return false; }
                return true;
            }
            @Override public void end() {
                ACTIVE.remove(owner);
                surface.done();
                if (!done[0]) {
                    biomes(l, biome, c.x, c.z, r);
                    V2CNet.near(l, c, r + 140, V2CNet.msg(V2CNet.GENESIS_END, waveId, 0, 0, 0F, c, 0));
                }
                Vec3 top = Vec3.atCenterOf(l.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(c)));
                FxNet.shockwave(l, top, Math.min(r, 40), 0xFF2020, 30);
                FxNet.shake(l, c, Math.min(r, 48) + 16, 0.5F, 20);
                Fx.sound(l, c, SoundEvents.RESPAWN_ANCHOR_DEPLETE, 0.7F, 0.7F);
                Fx.sound(l, c, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.4F, 0.6F);
                ServerPlayer pl = V2CNet.player(l.getServer(), owner);
                if (pl != null) Fx.bar(pl, Stone.REALITY, K + "done");
            }
        });
    }

    /** Soft sound sweep: the wave is heard passing at the point of the front nearest to each nearby listener. */
    private static void sweep(ServerLevel l, Vec3 c, double rad, float prog) {
        if (rad < 2) return;
        List<Vec3> played = new ArrayList<>();
        for (ServerPlayer sp : l.players()) {
            double dx = sp.getX() - c.x, dz = sp.getZ() - c.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (Math.abs(d - rad) > 40) continue;
            double k = d < 0.01 ? 0 : rad / d;
            Vec3 at = d < 0.01 ? c.add(rad, 0, 0) : new Vec3(c.x + dx * k, sp.getY(), c.z + dz * k);
            boolean near = false;
            for (Vec3 v : played) if (v.distanceToSqr(at) < 14 * 14) { near = true; break; }
            if (near) continue;
            played.add(at);
            Fx.sound(l, at, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.55F, 0.5F + prog * 0.9F);
            Fx.sound(l, at, SoundEvents.RESPAWN_ANCHOR_AMBIENT, 0.3F, 0.7F + prog * 0.5F);
        }
    }

    // ------------------------------------------------------------------ biome

    private static void biomes(ServerLevel l, Holder<Biome> biome, double cx, double cz, double rad) {
        if (rad < 1) return;
        List<ChunkAccess> changed = new ArrayList<>();
        var sampler = l.getChunkSource().randomState().sampler();
        double r2 = rad * rad;
        for (int x = Mth.floor(cx - rad) >> 4; x <= Mth.floor(cx + rad) >> 4; x++)
            for (int z = Mth.floor(cz - rad) >> 4; z <= Mth.floor(cz + rad) >> 4; z++) {
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
