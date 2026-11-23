package com.infinitystones.tensura.skill.ability.extra.power;

import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.ExtraSettings;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Ground;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
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
 * flash, heavy shake, double shockwave, flying debris, falloff damage (lethal in the centre, hits players) and — with
 * mobGriefing — a flattened spherical crater carved over several ticks (≤ 400 blocks per tick, no drops, unbreakables
 * and block entities kept, scorched rim). Alt+scroll: scale 1–3 (radius 8/14/20). One nuke per caster (re-use replaces).
 */
public final class CelestialNuke implements Ability {
    public static final CelestialNuke INSTANCE = new CelestialNuke();
    static final String MODE = "power.celestial_annihilation_aim";
    static final int FIRE = 100, LIFE = 150;
    private static final Map<UUID, Tasks.Handle> ACTIVE = new HashMap<>();

    @Override public Stone stone() { return Stone.POWER; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        ServerLevel l = p.serverLevel();
        int scale = ExtraSettings.get(p, MODE, 1, 1, 3, 1);
        float radius = scale == 1 ? 8 : scale == 2 ? 14 : 20;
        Vec3 aim = Aim.point(p, 160);
        Vec3 ground = Ground.below(l, aim.add(0, 0.2, 0), 64);
        if (!l.hasChunkAt(BlockPos.containing(ground))) return Fx.fail(p, "infinity.extra.power.nuke.far");
        double skyY = Math.min(l.getMaxBuildHeight() + 40, ground.y + 40 + radius);
        Vec3 sky = new Vec3(ground.x, skyY, ground.z);

        Tasks.Handle old = ACTIVE.remove(p.getUUID());
        if (old != null) old.cancel();

        int id = PowerNet.newId();
        PowerNet.nuke(l, id, sky, ground, p.position(), radius, FIRE, LIFE, PowerUtil.COLOR, PowerUtil.ACCENT);
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
        Crater crater;

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
                PowerUtil.farSound(l, ground, SoundEvents.GENERIC_EXPLODE, 0.7F, 0.6F, 220);
                Fx.far(l, ParticleTypes.EXPLOSION_EMITTER, ground.add(0, 2, 0), 2 + scale, R * 0.6, 0, 220);
            }
            if (age > FIRE && age < FIRE + 30 && age % 3 == 0) {
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
            if (crater != null && !crater.step()) crater = null;
            return age < FIRE + 200 || crater != null;
        }

        private void impact() {
            fired = true;
            FxNet.flashAround(l, ground, 120, 0xF4E4FF, 0.92F, 18);
            FxNet.shake(l, ground, 110, 7F, 50);
            FxNet.shockwave(l, ground.add(0, 0.2, 0), R * 2.4F, PowerUtil.COLOR, 30);
            FxNet.beam(l, sky, ground, PowerUtil.COLOR, 1.5F, 18);
            PowerUtil.farSound(l, ground, SoundEvents.GENERIC_EXPLODE, 0.7F, 0.45F, 240);
            PowerUtil.farSound(l, ground, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.7F, 0.55F, 240);
            PowerUtil.farSound(l, ground, SoundEvents.WARDEN_SONIC_BOOM, 0.7F, 0.5F, 240);
            Fx.far(l, ParticleTypes.EXPLOSION_EMITTER, ground.add(0, 1, 0), 3 + scale * 2, R * 0.4, 0, 240);
            Fx.far(l, ParticleTypes.FLASH, ground.add(0, 2, 0), 4, R * 0.3, 0, 240);
            Fx.far(l, Fx.dust(Stone.POWER, 4F), ground.add(0, 2, 0), 120, R * 0.6, 0.2, 240);
            Fx.far(l, ParticleTypes.END_ROD, ground.add(0, 1, 0), 80, 1, 0.9, 240);

            // damage: lethal centre, falloff outward; everyone non-allied, players included
            double reach = R * 1.8;
            for (LivingEntity e : PowerUtil.victims(p, l, ground, reach)) {
                double d = e.position().distanceTo(ground);
                float dmg = d < R * 0.3 ? 100000F : (float) ((40 + 20 * scale) * Math.pow(1 - d / reach, 1.3) + 4);
                Rules.hurt(p, e, dmg);
                PowerUtil.push(e, ground, 1.6 * (1 - d / reach) + 0.3, 0.9 * (1 - d / reach) + 0.3);
                e.igniteForSeconds(4);
            }

            // flying debris
            RandomSource rnd = l.random;
            int count = 12 + 8 * scale;
            for (int k = 0; k < count; k++) {
                double a = rnd.nextDouble() * Math.PI * 2, rr = rnd.nextDouble() * R * 0.9;
                Vec3 at = ground.add(Math.cos(a) * rr, 0.5, Math.sin(a) * rr);
                BlockState st = PowerUtil.surface(l, at);
                double sp = 0.5 + rnd.nextDouble() * 0.9;
                Vec3 vel = new Vec3(Math.cos(a) * sp, 0.9 + rnd.nextDouble() * 1.1, Math.sin(a) * sp);
                PowerUtil.debris(l, at.add(0, 1, 0), st, vel, 0.5F + rnd.nextFloat() * 0.9F, 50, k % 4 == 0 ? PowerUtil.COLOR : 0);
            }

            if (Rules.griefing(l)) crater = new Crater(l, BlockPos.containing(ground.add(0, -0.5, 0)), R * 0.85, R * 0.42, R * 0.6);
        }

        @Override
        public void end() {
            ACTIVE.remove(p.getUUID(), handle);
            if (!fired) PowerNet.kill(l, ground, id);
        }
    }

    /** Flattened spherical crater, carved outward from the centre over several ticks. */
    private static final class Crater {
        static final int MAX_CHANGES = 380, MAX_CHECKS = 6000;
        final ServerLevel l;
        final List<long[]> todo = new ArrayList<>();   // {packed pos, rim flag}
        int idx;

        Crater(ServerLevel l, BlockPos c, double rh, double depth, double up) {
            this.l = l;
            int ir = (int) Math.ceil(rh * 1.12), id = (int) Math.ceil(depth * 1.12), iu = (int) Math.ceil(up);
            List<long[]> list = new ArrayList<>();
            for (int dx = -ir; dx <= ir; dx++) for (int dz = -ir; dz <= ir; dz++) {
                double hz = (dx * dx + dz * dz) / (rh * rh);
                if (hz > 1.26) continue;
                for (int dy = -id; dy <= iu; dy++) {
                    double v = hz + (dy <= 0 ? (dy * dy) / (depth * depth) : (dy * dy) / (up * up));
                    // jagged edge
                    double jag = 1 + 0.08 * Math.sin(dx * 1.7 + dz * 0.9) * Math.cos(dz * 1.3 - dx * 0.6);
                    if (v <= jag) list.add(new long[]{c.offset(dx, dy, dz).asLong(), 0, (long) ((dx * dx + dy * dy + dz * dz) * 16)});
                    else if (dy <= 0 && v <= jag * 1.25) list.add(new long[]{c.offset(dx, dy, dz).asLong(), 1, (long) ((dx * dx + dy * dy + dz * dz) * 16) + 4});
                }
            }
            list.sort(Comparator.comparingLong(a -> a[2]));
            todo.addAll(list);
        }

        /** @return false when finished. */
        boolean step() {
            int changes = 0, checks = 0;
            RandomSource rnd = l.random;
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            while (idx < todo.size() && changes < MAX_CHANGES && checks < MAX_CHECKS) {
                long[] e = todo.get(idx++);
                checks++;
                m.set(e[0]);
                if (!l.hasChunkAt(m)) continue;
                BlockState s = l.getBlockState(m);
                if (s.isAir() || s.getDestroySpeed(l, m) < 0 || s.hasBlockEntity()) continue;
                if (e[1] == 0) {
                    l.setBlock(m, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    if (rnd.nextInt(14) == 0) l.sendParticles(ParticleTypes.LARGE_SMOKE, m.getX() + 0.5, m.getY() + 0.5, m.getZ() + 0.5, 1, 0.3, 0.3, 0.3, 0.02);
                    changes++;
                } else if (s.isSolidRender(l, m) && s.getFluidState().isEmpty()) {
                    int r = rnd.nextInt(10);
                    BlockState rim = r < 2 ? Blocks.MAGMA_BLOCK.defaultBlockState() : r < 6 ? Blocks.BLACKSTONE.defaultBlockState() : r < 8 ? Blocks.BASALT.defaultBlockState() : null;
                    if (rim != null) { l.setBlock(m, rim, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE); changes++; }
                }
            }
            return idx < todo.size();
        }
    }
}
