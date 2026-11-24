package com.infinitystones.tensura.skill.ability.extra.v2a;

import com.infinitystones.tensura.compat.ClaimsCompat;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.ExtraSettings;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Ground;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import com.infinitystones.tensura.skill.ability.extra.power.PowerNet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Fist from the Sky. Left (aim, up to 48 blocks): purple layered magic circles open high above the point, the sky darkens
 * locally and a giant runed forearm and fist (fingers closing as it falls) comes down out of the circle and slams the point:
 * shockwave rings, dust, flying debris, camera shake. Weak (about 6 hearts, 3-4 block radius, knockback) and the ground
 * is dented by a crater that puts itself back after about 10 s (see {@link Crater}). Right: open-palm slam, knockback only.
 * Scroll: fist size 1-3.
 */
public final class SkyFist implements Ability {
    public static final SkyFist INSTANCE = new SkyFist();
    static final String MODE = "power.sky_fist";

    @Override public Stone stone() { return Stone.POWER; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        ServerLevel l = p.serverLevel();
        int size = ExtraSettings.get(p, MODE, 1, 1, 3, 1);
        Vec3 aim = Aim.point(p, 48);
        Vec3 ground = Ground.below(l, aim.add(0, 0.3, 0), 24);
        if (!l.hasChunkAt(BlockPos.containing(ground))) return Fx.fail(p, "infinity.v2a.far");
        double yC = Math.min(l.getMaxBuildHeight() - 6, ground.y + 34 + 2 * size);
        Vec3 sky = new Vec3(ground.x, yC, ground.z);
        float scale = 1.8F + 0.5F * (size - 1);
        double dx = p.getX() - ground.x, dz = p.getZ() - ground.z;
        float yaw = dx * dx + dz * dz < 1 ? (float) Math.toRadians(-p.getYRot() + 180) : (float) Math.atan2(dx, dz);
        int id = V2aNet.newId();
        V2aNet.send(l, ground, 200, V2aNet.msg(V2aNet.ARM, id, alt ? 1 : 0, V2aAnim.FIST_LIFE, scale, ground, yaw, yC, ground.y, 0, size));

        float r1 = 9 + 2 * size;
        Vec3 up = new Vec3(0, 1, 0);
        int life = V2aAnim.FIST_LIFE - 6;
        PowerNet.circle(l, PowerNet.newId(), sky, up, r1, Bits.PURPLE, Bits.ACCENT, life, 3F, PowerNet.RAYS);
        PowerNet.circle(l, PowerNet.newId(), sky.add(0, -0.3, 0), up, r1 * 0.62F, Bits.ACCENT, Bits.HOT, life, -4.5F, PowerNet.GLYPHS_ONLY);
        PowerNet.circle(l, PowerNet.newId(), sky.add(0, -0.6, 0), up, r1 * 0.3F, Bits.HOT, Bits.PURPLE, life, 6F, PowerNet.SIMPLE);
        Bits.farSound(l, ground, SoundEvents.BEACON_ACTIVATE, 0.5F, 0.55F, 120);
        Bits.farSound(l, ground, SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.45F, 0.7F, 120);
        Tasks.add(new Fist(p, l, ground, yC, size, alt, r1));
        return true;
    }

    private static final class Fist implements Tasks.Task {
        final ServerPlayer p; final ServerLevel l; final Vec3 ground; final double yC; final int size; final boolean palm; final float r1;

        Fist(ServerPlayer p, ServerLevel l, Vec3 ground, double yC, int size, boolean palm, float r1) {
            this.p = p; this.l = l; this.ground = ground; this.yC = yC; this.size = size; this.palm = palm; this.r1 = r1;
        }

        @Override
        public boolean tick(int age) {
            RandomSource rnd = l.random;
            Vec3 sky = new Vec3(ground.x, yC, ground.z);
            if (age < V2aAnim.FIST_IMPACT && age % 3 == 0) {
                Fx.far(l, Fx.dust(Bits.PURPLE, 2F), sky.add(0, -1, 0), 8, r1 * 0.45, 0.02, 120);
                Fx.far(l, ParticleTypes.REVERSE_PORTAL, sky, 6, r1 * 0.5, 0.05, 120);
            }
            if (age >= V2aAnim.FIST_START && age < V2aAnim.FIST_IMPACT) {
                float k = V2aAnim.easeIn((age - V2aAnim.FIST_START) / (float) (V2aAnim.FIST_IMPACT - V2aAnim.FIST_START));
                double fy = ground.y + (yC - ground.y) * (1 - k);
                Vec3 at = new Vec3(ground.x, fy, ground.z);
                FxNet.pillar(l, 0, at, 0.6F + 0.2F * size, 16F, Bits.PURPLE, 3);
                l.sendParticles(Fx.dust(Bits.PURPLE, 2.4F), at.x, at.y + 1, at.z, 6, 1.2, 1.5, 1.2, 0);
                l.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 3, at.z, 2, 0.8, 2, 0.8, 0.02);
                if (age == V2aAnim.FIST_START + 2) Bits.farSound(l, at, SoundEvents.WIND_CHARGE_BURST, 0.6F, 0.5F, 100);
                if (age == V2aAnim.FIST_START + 14) Bits.farSound(l, ground, SoundEvents.BREEZE_WIND_CHARGE_BURST, 0.6F, 0.45F, 100);
                if (age == V2aAnim.FIST_IMPACT - 8) {
                    Bits.farSound(l, ground, SoundEvents.ENDER_DRAGON_GROWL, 0.35F, 1.7F, 100);
                    FxNet.shake(l, ground, 36, 0.8F, 10);
                }
            }
            if (age == V2aAnim.FIST_IMPACT) impact();
            if (age > V2aAnim.FIST_IMPACT && age < V2aAnim.FIST_STAY && age % 3 == 0) {
                double rr = (age - V2aAnim.FIST_IMPACT) * 0.7 + 1;
                for (int k = 0; k < 6; k++) {
                    double a = rnd.nextDouble() * Math.PI * 2;
                    Vec3 s = ground.add(Math.cos(a) * rr, 0.3, Math.sin(a) * rr);
                    l.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, s.x, s.y, s.z, 0, 0, 0.05, 0, 1);
                }
            }
            return age < V2aAnim.FIST_LIFE;
        }

        private void impact() {
            RandomSource rnd = l.random;
            float s = 1 + 0.35F * (size - 1);
            BlockState st = Bits.surface(l, ground);
            float flat = palm ? 1.5F : 1F;
            FxNet.shockwave(l, ground.add(0, 0.1, 0), 7F * s * flat, Bits.PURPLE, 22);
            FxNet.shockwave(l, ground.add(0, 0.12, 0), 4.2F * s * flat, Bits.ACCENT, 16);
            FxNet.shockwave(l, ground.add(0, 0.14, 0), 11F * s * flat, 0xFFFFFF, 30);
            FxNet.flare(l, ground.add(0, 0.5, 0), 5F * s, Bits.ACCENT, 10);
            FxNet.shake(l, ground, 48, 3.4F + size, 24);
            FxNet.flashAround(l, ground, 10, Bits.PURPLE, 0.35F, 8);
            l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), ground.x, ground.y + 0.3, ground.z, 70, 2.0 * s, 0.2, 2.0 * s, 0.45);
            Fx.burstRing(l, ground.add(0, 0.2, 0), 42, new BlockParticleOption(ParticleTypes.BLOCK, st), 0.9 * flat);
            Fx.burstRing(l, ground.add(0, 0.3, 0), 30, ParticleTypes.CLOUD, 0.7 * flat);
            Fx.burstRing(l, ground.add(0, 0.4, 0), 24, ParticleTypes.WITCH, 0.6 * flat);
            Bits.farSound(l, ground, SoundEvents.GENERIC_EXPLODE, 0.7F, 0.5F, 100);
            Bits.farSound(l, ground, SoundEvents.WARDEN_ATTACK_IMPACT, 0.7F, 0.55F, 100);
            Bits.farSound(l, ground, SoundEvents.ANVIL_LAND, 0.5F, 0.45F, 100);
            Bits.farSound(l, ground, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.4F, 0.6F, 120);

            int count = 8 + 4 * size;
            for (int k = 0; k < count; k++) {
                double a = rnd.nextDouble() * Math.PI * 2, rr = 0.8 + rnd.nextDouble() * 2.2 * s;
                Vec3 at = ground.add(Math.cos(a) * rr, 0.6, Math.sin(a) * rr);
                double sp = 0.25 + rnd.nextDouble() * 0.5;
                Vec3 vel = new Vec3(Math.cos(a) * sp, 0.55 + rnd.nextDouble() * 0.7, Math.sin(a) * sp);
                Bits.debris(l, at, Bits.surface(l, at), vel, 0.35F + rnd.nextFloat() * 0.55F, 44);
            }

            if (palm) {
                double r = 6 + 1.5 * (size - 1);
                for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(ground, ground).inflate(r),
                        e -> Rules.isLiving(e) && Rules.canHarm(p, e) && e.position().distanceToSqr(ground) <= r * r)) {
                    if (e instanceof ServerPlayer vp && ClaimsCompat.isProtected(vp, p)) continue;
                    double f = 1 - Math.min(1, e.position().distanceTo(ground) / r) * 0.6;
                    Bits.push(e, ground, 1.7 * f + 0.3, 0.55 * f + 0.2);
                }
                return;
            }
            double r = 3 + 0.6 * (size - 1);
            for (LivingEntity e : Bits.victims(p, l, ground, r)) {
                double d = e.position().distanceTo(ground);
                float f = (float) (1 - Math.min(1, d / r) * 0.45);
                Rules.hurt(p, e, 12F * f);
                if (e instanceof ServerPlayer vp && ClaimsCompat.isProtected(vp, p)) continue;
                Bits.push(e, ground, 1.1 * f + 0.2, 0.55 * f + 0.15);
            }
            if (Rules.griefing(l)) Crater.carve(p, l, BlockPos.containing(ground.add(0, -0.5, 0)), 2.6 + 0.8 * size, 2 + (size > 1 ? 1 : 0));
        }
    }
}
