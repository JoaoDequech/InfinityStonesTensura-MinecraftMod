package com.infinitystones.tensura.skill.ability.extra.v2a;

import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.ExtraSettings;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Ground;
import com.infinitystones.tensura.skill.ability.extra.MenuNet;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import com.infinitystones.tensura.skill.ability.extra.power.PowerNet;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sculptor's Hands. Left (aim): two giant stone-and-purple-light hands rise out of the ground and press the earth like
 * clay: a smooth, permanent hill (the surface and sub-surface blocks of the area, natural noise, stone higher up) grows
 * layer by layer. Right: they scoop out a valley instead; the "Lake" choice (middle click menu) or a floor below sea level
 * fills it with water. Scroll: radius 4-24 (the height follows the radius). Blocks are placed in batches over many
 * ticks, only where the player may build (claims), never over block entities, and anything standing on the rising ground
 * is carried up. One sculpt at a time per caster.
 */
public final class SculptorHands implements Ability {
    public static final SculptorHands INSTANCE = new SculptorHands();
    static final String MODE = "power.sculptor_hands";
    private static final Map<UUID, Tasks.Handle> ACTIVE = new HashMap<>();

    private static final int RAISE_TOP = 0, RAISE_SUB = 1, RAISE_DEEP = 2, DIG = 3, WATER = 4, CLEAR_ABOVE = 5;

    @Override public Stone stone() { return Stone.POWER; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        if (ACTIVE.containsKey(p.getUUID())) return Fx.fail(p, "infinity.v2a.sculptor.busy");
        ServerLevel l = p.serverLevel();
        int radius = ExtraSettings.get(p, MODE, 10, 4, 24, 2);
        Vec3 aim = Aim.point(p, 64);
        Vec3 g = Ground.below(l, aim.add(0, 0.3, 0), 24);
        BlockPos gp = BlockPos.containing(g);
        if (!l.hasChunkAt(gp)) return Fx.fail(p, "infinity.v2a.far");
        boolean lake = alt && ExtraSettings.choice(p, MODE, 0) == 1;
        Plan plan = Plan.build(p, l, BlockPos.containing(g.x, g.y - 0.5, g.z), radius, alt, lake);
        if (plan == null || plan.count() == 0) return Fx.fail(p, "infinity.v2a.sculptor.nothing");

        float handScale = Mth.clamp(radius / 7F, 0.8F, 3.2F);
        double dx = p.getX() - g.x, dz = p.getZ() - g.z;
        float axis = (float) (Math.atan2(dz, dx) + Math.PI / 2);
        int life = V2aAnim.H_RISE + plan.ticks + V2aAnim.H_SINK;
        int id = V2aNet.newId();
        V2aNet.send(l, g, 220, V2aNet.msg(V2aNet.HANDS, id, alt ? 1 : 0, life, handScale, g, axis, 0, alt ? -plan.maxDepth : plan.maxHeight, plan.ticks, radius));

        Vec3 up = new Vec3(0, 1, 0);
        PowerNet.circle(l, PowerNet.newId(), g.add(0, 0.08, 0), up, radius * 1.15F, Bits.PURPLE, Bits.ACCENT, life, 2F, PowerNet.RAYS);
        PowerNet.circle(l, PowerNet.newId(), g.add(0, 0.1, 0), up, radius * 0.7F, Bits.ACCENT, Bits.HOT, life, -3F, PowerNet.GLYPHS_ONLY);
        FxNet.shockwave(l, g.add(0, 0.1, 0), radius * 1.3F, Bits.PURPLE, 24);
        Bits.farSound(l, g, SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.5F, 0.55F, 100);
        Bits.farSound(l, g, SoundEvents.DEEPSLATE_BREAK, 0.7F, 0.5F, 100);

        ACTIVE.put(p.getUUID(), Tasks.add(new Sculpt(p, l, g, plan, radius, life)));
        return true;
    }

    @Override
    public @Nullable List<MenuNet.Option> menu(ServerPlayer p) {
        int c = ExtraSettings.choice(p, MODE, 0);
        return List.of(
                MenuNet.opt("infinity.v2a.sculptor.valley", "minecraft:iron_shovel", c == 0),
                MenuNet.opt("infinity.v2a.sculptor.lake", "minecraft:water_bucket", c == 1));
    }

    @Override
    public void pick(ServerPlayer p, int index) {
        ExtraSettings.setChoice(p, MODE, index == 1 ? 1 : 0);
        Fx.bar(p, Stone.POWER, index == 1 ? "infinity.v2a.sculptor.lake" : "infinity.v2a.sculptor.valley");
    }

    // ------------------------------------------------------------------ the terrain plan

    private static final class Plan {
        final LongArrayList pos = new LongArrayList();
        final List<BlockState> palette = new ArrayList<>();
        final List<Byte> kinds = new ArrayList<>();
        int ticks, maxHeight, maxDepth;
        BlockState top, sub, deep;
        int waterLevel = Integer.MIN_VALUE;

        int count() { return pos.size(); }

        static @Nullable Plan build(ServerPlayer p, ServerLevel l, BlockPos c, int R, boolean dig, boolean lake) {
            Plan plan = new Plan();
            int n = 2 * R + 1;
            int[] gy = new int[n * n];
            double[] w = new double[n * n];
            int minY = l.getMinBuildHeight();
            Map<BlockState, Integer> tops = new HashMap<>();
            for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) {
                int i = (dx + R) * n + (dz + R);
                gy[i] = Integer.MIN_VALUE;
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > R) continue;
                int x = c.getX() + dx, z = c.getZ() + dz;
                if (!l.hasChunk(x >> 4, z >> 4)) continue;
                int top = l.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, top, z);
                int found = Integer.MIN_VALUE;
                for (int k = 0; k < 48 && m.getY() > minY; k++, m.move(0, -1, 0)) {
                    if (isGround(l.getBlockState(m))) { found = m.getY(); break; }
                }
                if (found == Integer.MIN_VALUE) continue;
                gy[i] = found;
                double t = d / R;
                double bell = (1 - t * t) * (1 - t * t);
                double noise = 0.88 + 0.24 * noise(x * 0.17, z * 0.17);
                w[i] = bell * noise;
                if (d < R * 0.6) tops.merge(l.getBlockState(new BlockPos(x, found, z)), 1, Integer::sum);
            }
            int cy = gy[R * n + R];
            if (cy == Integer.MIN_VALUE) return null;

            BlockState top = Blocks.GRASS_BLOCK.defaultBlockState();
            int best = 0;
            for (Map.Entry<BlockState, Integer> e : tops.entrySet()) if (e.getValue() > best && !e.getKey().hasBlockEntity()) { best = e.getValue(); top = e.getKey(); }
            BlockState below = l.getBlockState(new BlockPos(c.getX(), cy - 1, c.getZ()));
            BlockState sub = isGround(below) && !below.hasBlockEntity() ? below : Blocks.DIRT.defaultBlockState();
            BlockState deepS = l.getBlockState(new BlockPos(c.getX(), cy - 6, c.getZ()));
            BlockState deep = isGround(deepS) && !deepS.hasBlockEntity() ? deepS : Blocks.STONE.defaultBlockState();
            if (sub.getBlock() instanceof FallingBlock) sub = Blocks.SANDSTONE.defaultBlockState();
            if (deep.getBlock() instanceof FallingBlock) deep = Blocks.SANDSTONE.defaultBlockState();
            if (top.is(BlockTags.LEAVES) || top.is(BlockTags.LOGS)) top = Blocks.GRASS_BLOCK.defaultBlockState();
            plan.top = top; plan.sub = sub; plan.deep = deep;
            plan.palette.add(top); plan.palette.add(sub); plan.palette.add(deep);

            int height = Mth.clamp(Math.round(R * 0.65F), 3, 16);
            int depth = Mth.clamp(Math.round(R * 0.45F), 2, 11);
            Integer[] order = new Integer[n * n];
            int cnt = 0;
            for (int i = 0; i < n * n; i++) if (gy[i] != Integer.MIN_VALUE) order[cnt++] = i;
            java.util.Arrays.sort(order, 0, cnt, java.util.Comparator.comparingInt(i -> {
                int dx = i / n - R, dz = i % n - R;
                return dx * dx + dz * dz;
            }));
            int[] amount = new int[n * n];
            int maxAmount = 0;
            for (int k = 0; k < cnt; k++) {
                int i = order[k];
                amount[i] = (int) Math.round((dig ? depth : height) * w[i]);
                maxAmount = Math.max(maxAmount, amount[i]);
            }
            plan.maxHeight = dig ? 0 : maxAmount;
            plan.maxDepth = dig ? maxAmount : 0;

            if (dig) {
                int rimMin = Integer.MAX_VALUE;
                for (int k = 0; k < cnt; k++) {
                    int i = order[k];
                    int dx = i / n - R, dz = i % n - R;
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d >= R - 2) rimMin = Math.min(rimMin, gy[i]);
                }
                int sea = l.getSeaLevel();
                plan.waterLevel = lake ? Math.min(cy - 1, rimMin == Integer.MAX_VALUE ? cy - 1 : rimMin - 1) : sea;
                for (int layer = 0; layer < maxAmount; layer++) {
                    for (int k = 0; k < cnt; k++) {
                        int i = order[k];
                        if (amount[i] <= layer) continue;
                        plan.add(c.getX() + i / n - R, gy[i] - layer, c.getZ() + i % n - R, DIG);
                    }
                }
                for (int k = 0; k < cnt; k++) {
                    int i = order[k];
                    if (amount[i] > 0) plan.add(c.getX() + i / n - R, gy[i] + 1, c.getZ() + i % n - R, CLEAR_ABOVE);
                }
                for (int k = 0; k < cnt; k++) {
                    int i = order[k];
                    int floor = gy[i] - amount[i];
                    if (amount[i] <= 0 || floor >= plan.waterLevel) continue;
                    for (int y = floor + 1; y <= plan.waterLevel; y++) plan.add(c.getX() + i / n - R, y, c.getZ() + i % n - R, WATER);
                }
            } else {
                for (int layer = 1; layer <= maxAmount; layer++) {
                    for (int k = 0; k < cnt; k++) {
                        int i = order[k];
                        int a = amount[i];
                        if (a < layer) continue;
                        int kind = layer == a ? RAISE_TOP : layer > a - 4 ? RAISE_SUB : RAISE_DEEP;
                        if (kind == RAISE_TOP && a >= height * 0.72 && height >= 8) kind = RAISE_DEEP;
                        plan.add(c.getX() + i / n - R, gy[i] + layer, c.getZ() + i % n - R, kind);
                    }
                }
            }
            int total = plan.count();
            plan.ticks = Mth.clamp((int) Math.ceil(total / 600.0), 50, 160);
            return plan;
        }

        void add(int x, int y, int z, int kind) {
            pos.add(BlockPos.asLong(x, y, z));
            kinds.add((byte) kind);
        }

        BlockState state(int kind) {
            return switch (kind) {
                case RAISE_TOP -> top;
                case RAISE_SUB -> sub;
                case RAISE_DEEP -> deep;
                case WATER -> Blocks.WATER.defaultBlockState();
                default -> Blocks.AIR.defaultBlockState();
            };
        }

        static boolean isGround(BlockState s) {
            return !s.isAir() && s.getFluidState().isEmpty() && s.blocksMotion()
                    && !s.is(BlockTags.LEAVES) && !s.is(BlockTags.LOGS) && !s.is(BlockTags.ICE);
        }

        static double noise(double x, double z) {
            int x0 = (int) Math.floor(x), z0 = (int) Math.floor(z);
            double fx = x - x0, fz = z - z0;
            fx = fx * fx * (3 - 2 * fx); fz = fz * fz * (3 - 2 * fz);
            double a = hash(x0, z0), b = hash(x0 + 1, z0), c = hash(x0, z0 + 1), d = hash(x0 + 1, z0 + 1);
            return (a + (b - a) * fx) * (1 - fz) + (c + (d - c) * fx) * fz;
        }

        static double hash(int x, int z) {
            int h = x * 374761393 + z * 668265263;
            h = (h ^ (h >>> 13)) * 1274126177;
            h ^= h >>> 16;
            return ((h & 0xFFFF) / 65535.0) * 2 - 1;
        }
    }

    // ------------------------------------------------------------------ the work

    private static final class Sculpt implements Tasks.Task {
        final ServerPlayer p; final ServerLevel l; final Vec3 g; final Plan plan; final int radius, life;
        final Perm perm;
        int idx;
        final int perTick;

        Sculpt(ServerPlayer p, ServerLevel l, Vec3 g, Plan plan, int radius, int life) {
            this.p = p; this.l = l; this.g = g; this.plan = plan; this.radius = radius; this.life = life;
            this.perm = new Perm(p, l);
            this.perTick = (int) Math.ceil(plan.count() / (double) plan.ticks);
        }

        @Override
        public boolean tick(int age) {
            if (age == V2aAnim.H_RISE - 4) Bits.farSound(l, g, SoundEvents.DEEPSLATE_PLACE, 0.7F, 0.45F, 100);
            if (age < V2aAnim.H_RISE) {
                if (age % 3 == 0) l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, plan.sub), g.x, g.y + 0.3, g.z, 12, radius * 0.5, 0.2, radius * 0.5, 0.2);
                return true;
            }
            int build = age - V2aAnim.H_RISE;
            if (idx < plan.count()) {
                int end = Math.min(plan.count(), idx + perTick);
                for (; idx < end; idx++) apply(idx);
                effects(build);
                lift();
                if (idx >= plan.count()) finale();
            }
            return age < life;
        }

        private void apply(int i) {
            BlockPos pos = BlockPos.of(plan.pos.getLong(i));
            int kind = plan.kinds.get(i);
            if (!l.hasChunkAt(pos) || pos.getY() < l.getMinBuildHeight() || pos.getY() >= l.getMaxBuildHeight()) return;
            BlockState cur = l.getBlockState(pos);
            switch (kind) {
                case RAISE_TOP, RAISE_SUB, RAISE_DEEP -> {
                    if (!(cur.isAir() || cur.canBeReplaced() || cur.is(BlockTags.LEAVES))) return;
                    if (!perm.can(pos)) return;
                    l.setBlock(pos, plan.state(kind), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
                case DIG -> {
                    if (cur.isAir() || cur.hasBlockEntity() || cur.getDestroySpeed(l, pos) < 0 || !cur.getFluidState().isEmpty()) return;
                    if (!perm.can(pos)) return;
                    l.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
                case CLEAR_ABOVE -> {
                    if (cur.isAir() || cur.hasBlockEntity() || !cur.canBeReplaced() || !cur.getFluidState().isEmpty()) return;
                    if (!perm.can(pos)) return;
                    l.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
                case WATER -> {
                    if (!cur.isAir()) return;
                    if (!perm.can(pos)) return;
                    l.setBlock(pos, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
                }
                default -> { }
            }
        }

        private void effects(int build) {
            var rnd = l.random;
            if (build % 4 == 0) FxNet.shake(l, g, radius + 28, 0.7F, 7);
            if (build % 5 == 0) {
                Bits.farSound(l, g, SoundEvents.GRAVEL_BREAK, 0.7F, 0.45F, 90);
                Bits.farSound(l, g, SoundEvents.STONE_BREAK, 0.5F, 0.4F, 90);
            }
            for (int k = 0; k < 7 && idx > 0; k++) {
                int j = Math.max(0, idx - 1 - rnd.nextInt(Math.max(1, perTick)));
                BlockPos bp = BlockPos.of(plan.pos.getLong(j));
                BlockState st = plan.kinds.get(j) == WATER ? Blocks.WATER.defaultBlockState() : plan.palette.get(rnd.nextInt(3));
                l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), bp.getX() + 0.5, bp.getY() + 1.0, bp.getZ() + 0.5, 3, 0.4, 0.3, 0.4, 0.05);
            }
            if (build % 3 == 0) {
                double a = rnd.nextDouble() * Math.PI * 2, rr = radius * (0.3 + 0.7 * rnd.nextDouble());
                Vec3 at = g.add(Math.cos(a) * rr, 0.3, Math.sin(a) * rr);
                Fx.far(l, Fx.dust(Bits.PURPLE, 2F), at.add(0, 1.5, 0), 4, 0.6, 0.02, 100);
                l.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1, at.z, 2, 0.4, 0.8, 0.4, 0.04);
            }
        }

        /** Anything caught inside the rising ground is carried up to the new surface. */
        private void lift() {
            AABB box = new AABB(g.x - radius, g.y - 2, g.z - radius, g.x + radius, g.y + plan.maxHeight + 6, g.z + radius);
            for (Entity e : l.getEntitiesOfClass(Entity.class, box, x -> !(x instanceof Display) && !x.isSpectator() && !x.isRemoved())) {
                if (l.noCollision(e, e.getBoundingBox())) continue;
                double y = e.getY();
                for (int i = 0; i < 48; i++) {
                    y += 0.5;
                    if (l.noCollision(e, e.getBoundingBox().move(0, y - e.getY(), 0))) break;
                }
                Bits.place(e, new Vec3(e.getX(), y, e.getZ()));
            }
        }

        private void finale() {
            Vec3 c = g.add(0, plan.maxHeight * 0.6, 0);
            FxNet.shockwave(l, g.add(0, 0.2, 0), radius * 1.8F, Bits.PURPLE, 30);
            FxNet.shockwave(l, g.add(0, 0.25, 0), radius * 1.1F, Bits.ACCENT, 20);
            FxNet.flare(l, c, radius * 0.6F, Bits.ACCENT, 14);
            FxNet.shake(l, g, radius + 34, 2.2F, 16);
            Bits.farSound(l, g, SoundEvents.GENERIC_EXPLODE, 0.45F, 0.5F, 100);
            Bits.farSound(l, g, SoundEvents.AMETHYST_CLUSTER_BREAK, 0.7F, 0.5F, 100);
            l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, plan.top), g.x, g.y + 1, g.z, 80, radius * 0.5, 1.0, radius * 0.5, 0.3);
        }

        @Override
        public void end() {
            ACTIVE.remove(p.getUUID());
            // blocks are placed as the plan goes on; if the server stops the rest is simply not built
        }
    }
}
