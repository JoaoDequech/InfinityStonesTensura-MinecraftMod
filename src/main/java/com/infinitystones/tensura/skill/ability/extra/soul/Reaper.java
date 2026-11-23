package com.infinitystones.tensura.skill.ability.extra.soul;

import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.ExtraSettings;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reaper (Soul). Left: a giant spectral scythe sweeps a 180° arc in front of you in a few ticks (7 blocks): every creature
 * it cuts loses its soul — damage, and the souls fly into you as health (a golden shield beyond full health).
 * Right (Harvest): a rune circle opens around you and every creature within the radius (Alt+scroll 6–20) that is below a
 * third of its health is reaped on the spot; its soul comes to you. Bosses cannot be reaped. You can keep moving.
 */
public final class Reaper implements Ability {
    public static final Reaper INSTANCE = new Reaper();
    public static final String MODE = "soul.reaper";
    private static final String K = "infinity.extra.reaper.";
    private static final double REACH = 7;
    private static final int SWEEP_TICKS = 8;
    private static final float DAMAGE = 16F;

    @Override public Stone stone() { return Stone.SOUL; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        return alt ? harvest(p) : sweep(p);
    }

    // ------------------------------------------------------------------ scythe sweep

    private static boolean sweep(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        Vec3 look = p.getLookAngle();
        double base = Math.atan2(look.z, look.x);
        Vec3 c = p.position();
        Fx.sound(l, c, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7F, 0.5F);
        Fx.sound(l, c, SoundEvents.SOUL_ESCAPE.value(), 0.5F, 0.7F);
        Fx.sound(l, c, SoundEvents.WITHER_SHOOT, 0.25F, 1.6F);
        // the scythe is dragged forward a little, you are never rooted
        p.push(look.x * 0.35, 0, look.z * 0.35);
        p.hurtMarked = true;
        FxNet.runes(l, 0, c.add(0, 0.06, 0), 3.2F, SoulKit.ORANGE, 24, 2.2F);
        Set<LivingEntity> hit = new HashSet<>();
        List<LivingEntity> victims = new ArrayList<>();
        Tasks.add(new Tasks.Task() {
            @Override public boolean tick(int age) {
                if (!p.isAlive() || p.hasDisconnected()) return false;
                // sweep from the right shoulder (-90°) to the left (+90°) around the look direction
                double a0 = base - Math.PI / 2 + Math.PI * age / SWEEP_TICKS;
                double a1 = base - Math.PI / 2 + Math.PI * (age + 1) / SWEEP_TICKS;
                Vec3 o = p.position().add(0, p.getBbHeight() * 0.55, 0);
                for (int s = 0; s < 4; s++) {
                    double a = Mth.lerp(s / 3.0, a0, a1);
                    Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
                    Vec3 tip = o.add(dir.scale(REACH));
                    Vec3 mid = o.add(dir.scale(REACH * 0.62));
                    FxNet.beam(l, mid, tip, s % 2 == 0 ? SoulKit.ORANGE : SoulKit.PALE, 0.22F, 5);
                    for (double d = 2.5; d <= REACH; d += 0.9)
                        Fx.at(l, Fx.dust(d > REACH - 1 ? SoulKit.PALE : SoulKit.ORANGE, 1.7F), o.add(dir.scale(d)), 1, 0.05, 0);
                    Fx.at(l, ParticleTypes.SOUL_FIRE_FLAME, tip, 1, 0.1, 0.02);
                    if (s == 3) Fx.at(l, ParticleTypes.SWEEP_ATTACK, o.add(dir.scale(REACH * 0.8)), 1, 0, 0);
                }
                AABB box = new AABB(o, o).inflate(REACH, 2.6, REACH);
                for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, box, e -> Rules.canHarm(p, e) && !hit.contains(e))) {
                    Vec3 d = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(o);
                    double dist = Math.sqrt(d.x * d.x + d.z * d.z);
                    if (dist > REACH + e.getBbWidth() * 0.5) continue;
                    double ang = Math.atan2(d.z, d.x);
                    double diff = Mth.wrapDegrees((ang - (a0 + a1) / 2) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
                    if (Math.abs(diff) > Math.PI / SWEEP_TICKS + 0.25 + Math.min(0.5, e.getBbWidth() / Math.max(1, dist))) continue;
                    hit.add(e);
                    cut(p, l, e);
                    victims.add(e);
                }
                if (age == 2) FxNet.shake(l, p.position(), 12, 0.25F, 6);
                return age < SWEEP_TICKS;
            }
            @Override public void end() {
                if (victims.isEmpty()) { Fx.bar(p, Stone.SOUL, K + "whiff"); return; }
                Fx.bar(p, Stone.SOUL, K + "cut", victims.size());
            }
        });
        return true;
    }

    private static void cut(ServerPlayer p, ServerLevel l, LivingEntity e) {
        Vec3 at = e.position().add(0, e.getBbHeight() * 0.55, 0);
        float before = e.getHealth();
        Rules.hurt(p, e, DAMAGE);
        float dealt = Math.max(0, before - Math.max(0, e.getHealth()));
        Vec3 away = e.position().subtract(p.position()).multiply(1, 0, 1).normalize();
        e.push(away.x * 0.9, 0.35, away.z * 0.9);
        e.hurtMarked = true;
        SoulKit.soulBurst(l, at, 1.0F);
        Fx.at(l, ParticleTypes.SOUL_FIRE_FLAME, at, 10, 0.3, 0.08);
        FxNet.beam(l, at.add(0, 1.4, 0), at.add(0, -0.4, 0), SoulKit.SPIRIT, 0.14F, 6);
        float steal = Math.max(2F, dealt * 0.4F);
        SoulKit.wisp(l, at, p, () -> {
            SoulKit.gain(p, steal, 8F);
            Fx.at(l, Fx.dust(SoulKit.LIGHT, 1.0F), p.position().add(0, p.getBbHeight() * 0.6, 0), 6, 0.3, 0);
        });
    }

    // ------------------------------------------------------------------ harvest

    private static boolean harvest(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        int radius = ExtraSettings.get(p, MODE, 14, 6, 20, 1);
        Vec3 c = p.position();
        List<LivingEntity> ripe = l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(radius, radius * 0.5, radius),
                e -> Rules.canHarm(p, e) && !Rules.isBoss(e) && e.position().distanceToSqr(c) <= radius * radius
                        && e.getHealth() <= e.getMaxHealth() / 3F);
        if (ripe.isEmpty()) return Fx.fail(p, K + "none");
        if (ripe.size() > 24) ripe = ripe.subList(0, 24);

        float r = radius;
        SoulKit.circle(l, c, Math.max(4F, r * 0.55F), SoulKit.ORANGE, 70);
        FxNet.runes(l, 0, c.add(0, 0.09, 0), r, SoulKit.EMBER, 50, -0.8F);
        FxNet.dome(l, 0, c, r * 0.5F, SoulKit.ORANGE, 14);
        FxNet.shockwave(l, c.add(0, 0.1, 0), r, SoulKit.ORANGE, 22);
        FxNet.shockwave(l, c.add(0, 0.1, 0), r * 0.6F, SoulKit.PALE, 16);
        FxNet.spiral(l, c, 2.2F, 6F, SoulKit.LIGHT, 28, p.getId());
        FxNet.shake(l, c, r * 1.5, 0.45F, 14);
        FxNet.flash(p, SoulKit.PALE, 0.25F, 8);
        Fx.sound(l, c, SoundEvents.WITHER_SPAWN, 0.3F, 1.7F);
        Fx.sound(l, c, SoundEvents.SOUL_ESCAPE.value(), 0.7F, 0.5F);
        Fx.sound(l, c, SoundEvents.BELL_RESONATE, 0.5F, 0.6F);
        Fx.burstRing(l, c.add(0, 0.3, 0), 48, ParticleTypes.SOUL, 0.5);

        final List<LivingEntity> targets = ripe;
        Tasks.add(new Tasks.Task() {
            int reaped;
            @Override public boolean tick(int age) {
                if (!p.isAlive() || p.hasDisconnected()) return false;
                if (age < 6) return true;
                int i = (age - 6) * 2;
                for (int k = i; k < i + 2 && k < targets.size(); k++) {
                    LivingEntity e = targets.get(k);
                    if (!e.isAlive()) continue;
                    Vec3 at = e.position().add(0, e.getBbHeight() * 0.55, 0);
                    FxNet.beam(l, at.add(0, 7, 0), at, SoulKit.PALE, 0.2F, 8);
                    FxNet.chain(l, 0, p, at, e, SoulKit.ORANGE, 10);
                    float max = e.getMaxHealth();
                    e.invulnerableTime = 0;
                    e.hurt(Rules.magic(p), Math.max(max, 40F) * 3);
                    if (!e.isAlive()) {
                        reaped++;
                        SoulKit.soulBurst(l, at, 1.5F);
                        FxNet.shockwave(l, e.position().add(0, 0.1, 0), 2.5F, SoulKit.ORANGE, 12);
                        float heal = Mth.clamp(max * 0.15F, 3F, 10F);
                        SoulKit.wisp(l, at, p, () -> SoulKit.gain(p, heal, 20F));
                    }
                }
                return i + 2 < targets.size();
            }
            @Override public void end() { Fx.bar(p, Stone.SOUL, K + "reaped", reaped); }
        });
        return true;
    }
}
