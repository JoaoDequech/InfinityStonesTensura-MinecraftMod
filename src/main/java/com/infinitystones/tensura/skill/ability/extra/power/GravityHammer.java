package com.infinitystones.tensura.skill.ability.extra.power;

import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.ExtraSettings;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Ground;
import com.infinitystones.tensura.skill.ability.extra.Move;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Gravity Hammer. Left: a purple leap; at the top gravity multiplies and you crash down as a comet. Right: from the
 * air an instant dive, from the ground a short hop then the dive. The impact cracks the ground in a star of fissures,
 * throws earth, hurts and launches everything around (stronger the higher you fell, 10–30 damage with falloff).
 * Alt + scroll: impact radius 5–12. The real ground is never changed; you take no fall damage from the slam.
 */
public final class GravityHammer implements Ability {
    public static final GravityHammer INSTANCE = new GravityHammer();
    static final String MODE = "power.gravity_hammer";
    private static final Set<UUID> ACTIVE = new HashSet<>();
    private static final Set<UUID> DIVE_NOW = new HashSet<>();

    @Override public Stone stone() { return Stone.POWER; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        if (ACTIVE.contains(p.getUUID())) {
            DIVE_NOW.add(p.getUUID());
            return true;
        }
        ServerLevel l = p.serverLevel();
        int radius = ExtraSettings.get(p, MODE, 8, 5, 12, 1);
        Vec3 o = p.position();
        Vec3 look = p.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0, look.z);
        dir = dir.lengthSqr() < 1e-4 ? Vec3.ZERO : dir.normalize();
        boolean grounded = p.onGround();
        if (!alt || grounded) {
            double up = alt ? 0.55 : 1.25;
            Move.set(p, new Vec3(dir.x * (alt ? 0.1 : 0.75), up, dir.z * (alt ? 0.1 : 0.75)));
            PowerNet.circle(l, 0, o.add(0, 0.06, 0), new Vec3(0, 1, 0), 3.2F, PowerUtil.COLOR, PowerUtil.ACCENT, 28, 5F, PowerNet.RAYS);
            PowerNet.circle(l, 0, o.add(0, 0.07, 0), new Vec3(0, 1, 0), 1.9F, PowerUtil.ACCENT, PowerUtil.COLOR, 28, -6F, PowerNet.GLYPHS_ONLY);
            FxNet.shockwave(l, o.add(0, 0.1, 0), 5F, PowerUtil.COLOR, 14);
            FxNet.spiral(l, o, 1.2F, 3.5F, PowerUtil.COLOR, 18, p.getId());
            Fx.at(l, ParticleTypes.CLOUD, o.add(0, 0.2, 0), 14, 0.5, 0.08);
            Fx.sound(l, o, SoundEvents.WIND_CHARGE_BURST, 0.7F, 0.6F);
            Fx.sound(l, o, SoundEvents.ANVIL_PLACE, 0.4F, 0.5F);
        }
        p.fallDistance = 0;
        ACTIVE.add(p.getUUID());
        if (alt && !grounded) DIVE_NOW.add(p.getUUID());
        final Vec3 heading = dir;
        final boolean hop = alt && grounded;
        Tasks.add(new Tasks.Task() {
            boolean diving;
            double peak = o.y;
            int dive0;
            @Override public boolean tick(int age) {
                if (!p.isAlive() || p.hasDisconnected() || p.level() != l || age > 140) return false;
                peak = Math.max(peak, p.getY());
                p.fallDistance = 0;
                Vec3 c = p.position();
                if (!diving) {
                    if (age % 2 == 0) {
                        l.sendParticles(Fx.dust(Stone.POWER, 1.6F), c.x, c.y + 0.2, c.z, 5, 0.25, 0.1, 0.25, 0);
                        l.sendParticles(Fx.light(Stone.POWER, 1.0F), c.x, c.y + 0.5, c.z, 2, 0.3, 0.3, 0.3, 0);
                    }
                    boolean apex = age >= 4 && p.getDeltaMovement().y < -0.15;
                    boolean wait = hop && age < 6;
                    if ((apex || DIVE_NOW.remove(p.getUUID())) && !wait) {
                        diving = true;
                        dive0 = age;
                        peak = Math.max(peak, p.getY());
                        FxNet.flash(p, PowerUtil.COLOR, 0.3F, 8);
                        Fx.sound(l, c, SoundEvents.BREEZE_WIND_CHARGE_BURST, 0.6F, 0.5F);
                        Fx.sound(l, c, SoundEvents.ENDER_DRAGON_GROWL, 0.35F, 1.6F);
                        PowerNet.circle(l, 0, c.add(0, -0.2, 0), new Vec3(0, 1, 0), 2.4F, PowerUtil.HOT, PowerUtil.COLOR, 14, 8F, PowerNet.SIMPLE);
                    }
                    return true;
                }
                Move.set(p, new Vec3(heading.x * 0.12, -2.4, heading.z * 0.12));
                l.sendParticles(Fx.dust(Stone.POWER, 2.2F), c.x, c.y + 1.0, c.z, 8, 0.3, 0.8, 0.3, 0);
                l.sendParticles(Fx.dust(PowerUtil.HOT, 1.6F), c.x, c.y + 1.4, c.z, 4, 0.25, 0.6, 0.25, 0);
                l.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 1.9, c.z, 2, 0.15, 0.4, 0.15, 0.02);
                FxNet.pillar(l, 0, c, 0.5F, 5F, PowerUtil.COLOR, 3);
                if (age - dive0 >= 2 && (p.onGround() || p.isInWater() || p.verticalCollision)) {
                    slam(p, l, Math.max(0, peak - p.getY()), radius);
                    return false;
                }
                return true;
            }
            @Override public void end() {
                ACTIVE.remove(p.getUUID());
                DIVE_NOW.remove(p.getUUID());
                p.fallDistance = 0;
            }
        });
        return true;
    }

    private static void slam(ServerPlayer p, ServerLevel l, double fell, int radius) {
        Vec3 c = p.position();
        float k = (float) Mth.clamp(fell / 7.0, 0.55, 2.0);
        float dmg = 10 + 10 * k;
        double r = radius * (0.85 + 0.15 * k);
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 30, 4, true, false, false));
        p.fallDistance = 0;
        Move.set(p, new Vec3(0, 0.0, 0));

        for (LivingEntity e : PowerUtil.victims(p, l, c, r)) {
            double d = e.position().distanceTo(c);
            float f = (float) (1 - Math.min(1, d / r) * 0.6);
            Rules.hurt(p, e, dmg * f);
            PowerUtil.push(e, c, 1.5 * f + 0.3, 0.9 * f + 0.25);
        }

        // layered circles + rings + beam of light
        Vec3 up = new Vec3(0, 1, 0);
        PowerNet.circle(l, 0, c.add(0, 0.06, 0), up, (float) r * 1.1F, PowerUtil.COLOR, PowerUtil.ACCENT, 60, 3F, PowerNet.RAYS);
        PowerNet.circle(l, 0, c.add(0, 0.07, 0), up, (float) r * 0.75F, PowerUtil.ACCENT, PowerUtil.HOT, 60, -4F, PowerNet.GLYPHS_ONLY);
        PowerNet.circle(l, 0, c.add(0, 0.08, 0), up, (float) r * 0.42F, PowerUtil.HOT, PowerUtil.COLOR, 60, 6F, PowerNet.SIMPLE);
        FxNet.shockwave(l, c.add(0, 0.1, 0), (float) r * 1.4F, PowerUtil.COLOR, 20);
        FxNet.shockwave(l, c.add(0, 0.12, 0), (float) r * 0.9F, PowerUtil.ACCENT, 14);
        FxNet.pillar(l, 0, c, 1.2F * k, 10F, PowerUtil.COLOR, 14);
        FxNet.flare(l, c.add(0, 0.4, 0), (float) r * 0.7F, PowerUtil.ACCENT, 12);
        FxNet.shake(l, c, 40, 3.2F * Math.min(1.4F, k), 26);
        FxNet.flashAround(l, c, 14, PowerUtil.COLOR, 0.45F, 10);

        Bits.dust(l, c, r);
        for (int ring = 1; ring <= 3; ring++) {
            int n = ring * 4 + 4;
            double rr = r * ring / 3.4;
            for (int i = 0; i < n; i++) {
                double a = Math.PI * 2 * i / n + ring;
                Vec3 g = Ground.below(l, c.add(Math.cos(a) * rr, 3, Math.sin(a) * rr), 8);
                PowerUtil.debris(l, g.add(0, 0.2, 0), PowerUtil.surface(l, g),
                        new Vec3(Math.cos(a) * 0.12, 0.5 + 0.12 * ring + l.random.nextDouble() * 0.2, Math.sin(a) * 0.12), 0.5F + l.random.nextFloat() * 0.3F, 24, 0);
            }
        }
        fissures(l, c, r);

        Fx.sound(l, c, SoundEvents.GENERIC_EXPLODE, 0.7F, 0.55F);
        Fx.sound(l, c, SoundEvents.WARDEN_ATTACK_IMPACT, 0.7F, 0.6F);
        Fx.sound(l, c, SoundEvents.ANVIL_LAND, 0.5F, 0.5F);
        Fx.sound(l, c, SoundEvents.AMETHYST_CLUSTER_BREAK, 0.6F, 0.5F);
    }

    /** A star of ground fissures grows outward, glowing purple. */
    private static void fissures(ServerLevel l, Vec3 c, double r) {
        int n = 10;
        double[] ang = new double[n];
        for (int i = 0; i < n; i++) ang[i] = Math.PI * 2 * i / n + l.random.nextDouble() * 0.4;
        double len = r * 1.2;
        Tasks.add(age -> {
            double d0 = age * 1.0, d1 = d0 + 1.0;
            for (int i = 0; i < n; i++) {
                double wob = Math.sin(d1 * 1.3 + i) * 0.25;
                double a0 = ang[i], a1 = ang[i] + wob * 0.2;
                Vec3 s = Ground.below(l, c.add(Math.cos(a0) * d0, 2, Math.sin(a0) * d0), 6).add(0, 0.08, 0);
                Vec3 e = Ground.below(l, c.add(Math.cos(a1) * d1, 2, Math.sin(a1) * d1), 6).add(0, 0.08, 0);
                Fx.line(l, s, e, 0.35, Fx.dust(0x2A0B45, 1.8F));
                Fx.line(l, s.add(0, 0.05, 0), e.add(0, 0.05, 0), 0.7, Fx.dust(PowerUtil.HOT, 1.2F));
                if (age % 3 == 0) l.sendParticles(Bits.lava(), e.x, e.y + 0.1, e.z, 1, 0.1, 0.05, 0.1, 0.02);
            }
            return d1 < len;
        });
    }

    /** Small particle helpers kept apart so the main flow stays readable. */
    private static final class Bits {
        static net.minecraft.core.particles.ParticleOptions lava() { return ParticleTypes.ELECTRIC_SPARK; }

        static void dust(ServerLevel l, Vec3 c, double r) {
            var st = PowerUtil.surface(l, c);
            l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), c.x, c.y + 0.3, c.z, 60, r * 0.25, 0.2, r * 0.25, 0.35);
            Fx.burstRing(l, c.add(0, 0.2, 0), 36, new BlockParticleOption(ParticleTypes.BLOCK, st), 0.9);
            Fx.burstRing(l, c.add(0, 0.3, 0), 28, ParticleTypes.WITCH, 0.7);
            Fx.burstRing(l, c.add(0, 0.4, 0), 20, ParticleTypes.CLOUD, 0.5);
        }
    }
}
