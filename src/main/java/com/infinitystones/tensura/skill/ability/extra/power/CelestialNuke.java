package com.infinitystones.tensura.skill.ability.extra.power;

import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.ExtraSettings;
import com.infinitystones.tensura.skill.ability.extra.Executions;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Ground;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import com.infinitystones.tensura.skill.ability.extra.Terrain;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Celestial Annihilation (NUKE). Replaces the base node celestial_annihilation_aim. Aim a point (≤160), the sky above it
 * fills with layered magic circles that grow and spin faster while charging, then a colossal beam falls: white/purple
 * flash, heavy shake, double shockwave, flying debris, true damage (10000 at the centre down to 100 at the edge, nothing
 * resists it, hits players) and a flattened spherical crater carved over several ticks (no drops, unbreakables and block
 * entities kept, scorched rim). Scroll: scale 1–4 (crater radius 8/14/24/40); 3 and 4 are hidden (cap1 = 2 in the tree)
 * and only 4 plays the full nuclear animation (mushroom cloud, condensation ring, dust wall, ash, white-out).
 * One nuke per caster (re-use replaces).
 */
public final class CelestialNuke implements Ability {
    public static final CelestialNuke INSTANCE = new CelestialNuke();
    static final String MODE = "power.celestial_annihilation_aim";
    static final int FIRE = 100, LIFE = FIRE + 660; // the client keeps the mushroom cloud (about 30 s) after the impact
    private static final Map<UUID, Tasks.Handle> ACTIVE = new HashMap<>();
    /** Crater radius per scale 1..4. Scale 4 ("Nuke") is the only one with the full nuclear animation; 3 and 4 are hidden. */
    private static final float[] RADIUS = {0, 8, 14, 24, 40};
    private static final int FULL_SCALE = 4, HIDDEN_FROM = 3;
    private static final float DAMAGE_CENTRE = 10000F, DAMAGE_EDGE = 100F;

    /** Highest scale this player may pick (the wheel caps it at 2 without the secret tier). */
    private static int maxScale(ServerPlayer p) {
        var node = com.infinitystones.tensura.skill.StoneTree.of(Stone.POWER).nodes.get("celestial_annihilation_aim");
        var spec = node == null ? null : com.infinitystones.tensura.wheel.AbilityMeta.setting(node, p);
        return spec == null ? HIDDEN_FROM - 1 : Math.max(1, Math.min(FULL_SCALE, spec.max()));
    }

    @Override public Stone stone() { return Stone.POWER; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        ServerLevel l = p.serverLevel();
        int scale = ExtraSettings.get(p, MODE, 1, 1, maxScale(p), 1);
        boolean full = scale >= FULL_SCALE;
        float radius = RADIUS[scale];
        Vec3 aim = Aim.point(p, 160);
        Vec3 ground = Ground.below(l, aim.add(0, 0.2, 0), 64);
        if (!l.hasChunkAt(BlockPos.containing(ground))) return Fx.fail(p, "infinity.extra.power.nuke.far");
        double skyY = Math.min(l.getMaxBuildHeight() + 40, ground.y + 40 + radius);
        Vec3 sky = new Vec3(ground.x, skyY, ground.z);

        Tasks.Handle old = ACTIVE.remove(p.getUUID());
        if (old != null) old.cancel();

        int id = PowerNet.newId();
        PowerNet.nuke(l, id, sky, ground, p.position(), radius, FIRE, full ? LIFE : FIRE + 60, PowerUtil.COLOR, PowerUtil.ACCENT, full);
        Fx.sound(l, p.position(), SoundEvents.BEACON_ACTIVATE, 0.7F, 0.5F);
        Fx.sound(l, p.position(), SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.6F, 0.6F);
        Fx.bar(p, Stone.POWER, "infinity.extra.power.nuke.charging", scale);
        Nuke n = new Nuke(p, l, id, sky, ground, radius, scale);
        ACTIVE.put(p.getUUID(), n.handle = Tasks.add(n));
        return true;
    }

    private static final class Nuke implements Tasks.Task {
        final ServerPlayer p; final ServerLevel l; final int id; final Vec3 sky, ground; final float R; final int scale;
        Tasks.Handle handle;
        boolean fired;
        Scar scar;

        Nuke(ServerPlayer p, ServerLevel l, int id, Vec3 sky, Vec3 ground, float r, int scale) {
            this.p = p; this.l = l; this.id = id; this.sky = sky; this.ground = ground; this.R = r; this.scale = scale;
        }

        @Override
        public boolean tick(int age) {
            RandomSource rnd = l.random;
            if (age < FIRE) {
                float charge = age / (float) FIRE;
                // ascending pillar sparks from the caster for the first second
                if (age < 20 && age % 2 == 0) Fx.far(l, Fx.dust(Stone.POWER, 2F), p.position().add(0, 1 + age * 2.5, 0), 8, 0.4, 0.02, 160);
                // the sky circle breathes particles, the ground answers
                if (age % 3 == 0) {
                    Fx.far(l, ParticleTypes.REVERSE_PORTAL, sky, (int) (10 + 30 * charge), R * 0.7, 0.04, 220);
                    Fx.far(l, Fx.light(Stone.POWER, 2.5F), sky.add(0, -2, 0), 6 + (int) (14 * charge), R * 0.5, 0.0, 220);
                }
                if (age > 30 && age % 2 == 0) {
                    // dust and debris particles sucked up from the ground
                    for (int k = 0; k < 3; k++) {
                        double a = rnd.nextDouble() * Math.PI * 2, rr = R * (0.4 + rnd.nextDouble() * 0.8);
                        Vec3 s = ground.add(Math.cos(a) * rr, 0.3, Math.sin(a) * rr);
                        Vec3 v = sky.subtract(s).normalize();
                        l.sendParticles(Fx.dust(Stone.POWER, 2F), s.x, s.y, s.z, 0, v.x, v.y, v.z, 0.6 + charge);
                        l.sendParticles(ParticleTypes.END_ROD, s.x, s.y, s.z, 0, v.x, v.y, v.z, 0.5 + charge);
                    }
                }
                if (age % 20 == 0) PowerUtil.farSound(l, sky, SoundEvents.BEACON_POWER_SELECT, 0.6F, 0.5F + charge, 200);
                if (age == 40) PowerUtil.farSound(l, sky, SoundEvents.WARDEN_SONIC_CHARGE, 0.7F, 0.5F, 200);
                if (age == 70) PowerUtil.farSound(l, sky, SoundEvents.WARDEN_SONIC_CHARGE, 0.7F, 0.8F, 200);
                if (age == 88) PowerUtil.farSound(l, sky, SoundEvents.END_PORTAL_SPAWN, 0.6F, 1.4F, 200);
                if (age > 60 && age % 10 == 0) FxNet.shake(l, ground, 60, 0.4F + charge, 12);
                return true;
            }
            if (age == FIRE) impact();
            if (age == FIRE + 8) {
                FxNet.shockwave(l, ground.add(0, 0.3, 0), R * 3.4F, 0xFFFFFF, 36);
                Fx.far(l, ParticleTypes.EXPLOSION_EMITTER, ground.add(0, 2, 0), 2 + scale, R * 0.6, 0, 220);
            }
            if (scale >= FULL_SCALE && age > FIRE && age < FIRE + 30 && age % 3 == 0) {
                // dust ring rolling out
                double rr = R * (0.5 + (age - FIRE) / 30.0 * 2.2);
                for (int k = 0; k < 10; k++) {
                    double a = rnd.nextDouble() * Math.PI * 2;
                    Fx.far(l, ParticleTypes.CAMPFIRE_COSY_SMOKE, ground.add(Math.cos(a) * rr, 0.5, Math.sin(a) * rr), 1, 0.5, 0.02, 200);
                }
            }
            // smoke rising from the crater for 10 s
            if (age > FIRE && age % 4 == 0) {
                for (int k = 0; k < 2 + scale; k++) {
                    double a = rnd.nextDouble() * Math.PI * 2, rr = rnd.nextDouble() * R * 0.8;
                    Vec3 s = ground.add(Math.cos(a) * rr, 0.2, Math.sin(a) * rr);
                    PowerUtil.farMotion(l, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, s, new Vec3(0, 1, 0), 0.06, 200);
                }
            }
            if (scar != null && !scar.step()) scar = null;
            return age < FIRE + 200 || scar != null;
        }

        private void impact() {
            fired = true;
            FxNet.flashAround(l, ground, 120, 0xF4E4FF, 0.92F, 18);
            FxNet.shake(l, ground, 110, scale >= FULL_SCALE ? 7F : 2.5F + scale, 50);
            FxNet.shockwave(l, ground.add(0, 0.2, 0), R * 2.4F, PowerUtil.COLOR, 30);
            FxNet.beam(l, sky, ground, PowerUtil.COLOR, 1.5F, 18);
            // the blast, thunder and rumble are played by each client after a delay that grows with the distance (NukeCloud)
            Fx.far(l, ParticleTypes.EXPLOSION_EMITTER, ground.add(0, 1, 0), 3 + scale * 2, R * 0.4, 0, 240);
            Fx.far(l, ParticleTypes.FLASH, ground.add(0, 2, 0), 4, R * 0.3, 0, 240);
            Fx.far(l, Fx.dust(Stone.POWER, 4F), ground.add(0, 2, 0), 120, R * 0.6, 0.2, 240);
            Fx.far(l, ParticleTypes.END_ROD, ground.add(0, 1, 0), 80, 1, 0.9, 240);

            // damage: true damage (nothing resists it, armour ignored), 10000 at the centre falling linearly to 100 at the edge
            // of the blast radius; everyone non-allied in it takes at least 100, players included (claims still protect)
            double reach = R * 1.6;
            Runnable blast = () -> {
                for (LivingEntity e : PowerUtil.victims(p, l, ground, reach)) {
                    double d = e.position().distanceTo(ground);
                    float k = (float) Math.max(0, 1 - d / reach);
                    Rules.trueHit(p, e, DAMAGE_EDGE + (DAMAGE_CENTRE - DAMAGE_EDGE) * k);
                    if (!e.isAlive()) continue;
                    PowerUtil.push(e, ground, 1.6 * (1 - d / reach) + 0.3, 0.9 * (1 - d / reach) + 0.3);
                    e.igniteForSeconds(4);
                }
            };
            if (scale >= HIDDEN_FROM) Executions.silently(p.server, blast); else blast.run();

            // flying debris
            RandomSource rnd = l.random;
            int count = 24 + 16 * scale;
            for (int k = 0; k < count; k++) {
                double a = rnd.nextDouble() * Math.PI * 2, rr = rnd.nextDouble() * R * 0.9;
                Vec3 at = ground.add(Math.cos(a) * rr, 0.5, Math.sin(a) * rr);
                BlockState st = PowerUtil.surface(l, at);
                double sp = 0.5 + rnd.nextDouble() * 0.9;
                Vec3 vel = new Vec3(Math.cos(a) * sp, 0.9 + rnd.nextDouble() * 1.1, Math.sin(a) * sp);
                PowerUtil.debris(l, at.add(0, 1, 0), st, vel, 0.5F + rnd.nextFloat() * 0.9F, 50, k % 4 == 0 ? PowerUtil.COLOR : 0);
            }

            scar = new Scar(p, l, BlockPos.containing(ground.add(0, -0.5, 0)), R);
        }

        @Override
        public void end() {
            ACTIVE.remove(p.getUUID(), handle);
            if (!fired) PowerNet.kill(l, ground, id);
        }
    }

    /**
     * The scar of a nuke: a deep crater (depth about half the radius, dome above ground cleared), a scorched rim, burning
     * edge, and an outer ring (up to 1.5 R) where trees are stripped and the ground is burnt. Carved outward from the
     * centre over many ticks (per-tick caps in {@link Terrain}), no drops, claims and unbreakables respected.
     */
    private static final class Scar {
        final ServerPlayer p; final ServerLevel l; final BlockPos c; final double R;
        final Terrain.Job crater, rim, ring;
        boolean fired;

        Scar(ServerPlayer p, ServerLevel l, BlockPos c, double R) {
            this.p = p; this.l = l; this.c = c; this.R = R;
            double depth = R * 0.5, up = R * 0.9;
            int ir = (int) Math.ceil(R * 1.35), idp = (int) Math.ceil(depth * 1.3), iu = (int) Math.ceil(up);
            int buckets = (int) Math.ceil(R * 1.8) + 2;
            LongList[] carve = new LongList[buckets], edge = new LongList[buckets];
            for (int i = 0; i < buckets; i++) { carve[i] = new LongArrayList(); edge[i] = new LongArrayList(); }
            for (int dx = -ir; dx <= ir; dx++) for (int dz = -ir; dz <= ir; dz++) {
                double hz = (dx * dx + dz * dz) / (R * R);
                if (hz > 1.7) continue;
                double jag = 1 + 0.08 * Math.sin(dx * 0.37 + dz * 0.21) * Math.cos(dz * 0.29 - dx * 0.13) + 0.04 * Math.sin(dx * 1.7 + dz * 0.9);
                for (int dy = -idp; dy <= iu; dy++) {
                    double v = hz + (dy <= 0 ? (dy * dy) / (depth * depth) : (dy * dy) / (up * up));
                    int b = Math.min(buckets - 1, (int) Math.sqrt(dx * dx + dy * dy * 1.5 + dz * dz));
                    if (v <= jag) carve[b].add(c.offset(dx, dy, dz).asLong());
                    else if (dy <= 0 && v <= jag * 1.3) edge[b].add(c.offset(dx, dy, dz).asLong());
                }
            }
            crater = Terrain.queue(l, p, concat(carve), 1000, Terrain.AIR);
            rim = Terrain.queue(l, p, concat(edge), 500, (lv, pos, old, r) -> {
                if (!old.isSolidRender(lv, pos) || !old.getFluidState().isEmpty()) return null;
                int k = r.nextInt(20);
                if (k < 3) return Blocks.MAGMA_BLOCK.defaultBlockState();
                if (k < 9) return Blocks.BLACKSTONE.defaultBlockState();
                if (k < 13) return Blocks.BASALT.defaultBlockState();
                if (k < 16) return Blocks.COAL_BLOCK.defaultBlockState();
                return null;
            });
            ring = Terrain.queue(l, p, ringColumns(), 700, this::burn);
        }

        private static long[] concat(LongList[] parts) {
            int n = 0;
            for (LongList x : parts) n += x.size();
            long[] out = new long[n];
            int i = 0;
            for (LongList x : parts) for (int k = 0; k < x.size(); k++) out[i++] = x.getLong(k);
            return out;
        }

        /** Surface slice of every column between the crater edge and 1.5 R (trees included), nearest first. */
        private long[] ringColumns() {
            int ir = (int) Math.ceil(R * 1.5);
            int buckets = ir + 2;
            LongList[] parts = new LongList[buckets];
            for (int i = 0; i < buckets; i++) parts[i] = new LongArrayList();
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int dx = -ir; dx <= ir; dx++) for (int dz = -ir; dz <= ir; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d < R * 0.95 || d > R * 1.5) continue;
                int x = c.getX() + dx, z = c.getZ() + dz;
                if (!l.hasChunk(x >> 4, z >> 4)) continue;
                int top = l.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                for (int y = top - 1; y >= Math.max(l.getMinBuildHeight(), top - 28); y--) parts[Math.min(buckets - 1, (int) d)].add(m.set(x, y, z).asLong());
            }
            return concat(parts);
        }

        private BlockState burn(ServerLevel lv, BlockPos pos, BlockState old, RandomSource r) {
            double d = Math.sqrt(pos.distSqr(new BlockPos(c.getX(), pos.getY(), c.getZ()))) / R;
            if (!old.getFluidState().isEmpty()) return null;
            if (old.is(BlockTags.LEAVES)) return Blocks.AIR.defaultBlockState();
            if (old.is(BlockTags.LOGS)) {
                if (r.nextFloat() > (d < 1.25 ? 0.9F : 0.5F)) return null;
                BlockState charred = r.nextInt(4) == 0 ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState();
                return old.hasProperty(BlockStateProperties.AXIS) && charred.hasProperty(BlockStateProperties.AXIS)
                        ? charred.setValue(BlockStateProperties.AXIS, old.getValue(BlockStateProperties.AXIS)) : charred;
            }
            if (old.canBeReplaced() && !old.isSolidRender(lv, pos)) return Blocks.AIR.defaultBlockState();
            if (old.is(Blocks.GRASS_BLOCK) || old.is(Blocks.PODZOL) || old.is(Blocks.MYCELIUM)) {
                if (r.nextFloat() > (d < 1.25 ? 1F : 0.6F)) return null;
                return r.nextBoolean() ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.DIRT.defaultBlockState();
            }
            return null;
        }

        /** @return false when the scar is finished. */
        boolean step() {
            if (!crater.done() || !rim.done() || !ring.done()) return true;
            if (!fired) { fired = true; ignite(); }
            return false;
        }

        /** Fires along the rim. */
        private void ignite() {
            RandomSource r = l.random;
            Terrain.Session t = new Terrain.Session(p);
            int tries = (int) (R * 4);
            for (int k = 0; k < tries; k++) {
                double a = r.nextDouble() * Math.PI * 2, rr = R * (0.95 + r.nextDouble() * 0.35);
                int x = c.getX() + (int) Math.round(Math.cos(a) * rr), z = c.getZ() + (int) Math.round(Math.sin(a) * rr);
                if (!l.hasChunk(x >> 4, z >> 4)) continue;
                BlockPos fp = new BlockPos(x, l.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
                if (l.isEmptyBlock(fp) && BaseFireBlock.canBePlacedAt(l, fp, Direction.UP) && t.may(fp)) l.setBlockAndUpdate(fp, BaseFireBlock.getState(l, fp));
            }
        }
    }
}
