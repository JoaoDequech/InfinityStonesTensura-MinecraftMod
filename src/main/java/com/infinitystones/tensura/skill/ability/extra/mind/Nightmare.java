package com.infinitystones.tensura.skill.ability.extra.mind;

import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Priv;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Nightmare (conjunction Mind + Soul). Aimed creature lives a 10 s nightmare. Player: darkness + nightmare screen,
 * creeper hisses / footsteps / whispers / heartbeat heard ONLY by them from every side, shaking and a wandering
 * camera. Mob: panics and flees from you (and its pack mates nearby scatter), weakened.
 */
public final class Nightmare implements Ability {
    public static final Nightmare INSTANCE = new Nightmare();
    private static final int TICKS = 200;
    private static final int DARK = 0x6B3A08;
    private static final Map<LivingEntity, Tasks.Handle> ACTIVE = new java.util.WeakHashMap<>();

    @Override public Stone stone() { return Stone.MIND; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        LivingEntity t = Aim.living(p, 32, e -> Rules.canHarm(p, e));
        if (t == null) return Fx.fail(p, "infinity.extra.mind.no_target");
        ServerLevel l = p.serverLevel();
        Tasks.Handle old = ACTIVE.remove(t);
        if (old != null) old.cancel();

        Vec3 tc = t.position().add(0, t.getBbHeight() * 0.7, 0);
        // cast: a dark-gold eye of terror opens over the victim
        FxNet.beam(l, p.getEyePosition(), tc, DARK, 0.1F, 14);
        FxNet.beam(l, p.getEyePosition(), tc, MindKit.GOLD, 0.04F, 14);
        FxNet.portal(l, 0, t.position().add(0, t.getBbHeight() + 1.2, 0), p.position().subtract(t.position()).normalize(), 1.1F, DARK, 50);
        FxNet.runes(l, 0, t.position().add(0, 0.05, 0), Math.max(1.6F, t.getBbWidth() * 2), DARK, 60, -0.8F);
        FxNet.shockwave(l, t.position().add(0, 0.05, 0), 5F, DARK, 22);
        FxNet.spiral(l, t.position().add(0, t.getBbHeight() + 1.5, 0), 1.2F, -2.5F, MindKit.GOLD, 30, t.getId());
        Fx.at(l, ParticleTypes.SCULK_SOUL, tc, 20, 0.5, 0.03);
        Fx.at(l, ParticleTypes.SQUID_INK, tc, 16, 0.4, 0.05);
        Fx.sound(l, tc, SoundEvents.WARDEN_NEARBY_CLOSER, 0.6F, 0.8F);
        Fx.sound(l, p.getEyePosition(), SoundEvents.EVOKER_PREPARE_ATTACK, 0.5F, 0.6F);

        int id = FxNet.newId();
        Tasks.Handle h;
        if (t instanceof ServerPlayer victim) {
            victim.addEffect(new MobEffectInstance(MobEffects.DARKNESS, TICKS + 20, 0));
            FxNet.screen(victim, FxNet.NIGHTMARE, TICKS);
            MindKit.shake(victim, 1.6F, 20);
            h = Tasks.add(new Tasks.Task() {
                float drift;
                @Override public boolean tick(int age) {
                    if (!victim.isAlive() || victim.hasDisconnected()) return false;
                    haunt(victim, age);
                    // camera wanders (lost aim)
                    if (age % 3 == 0) {
                        drift += (victim.getRandom().nextFloat() - 0.5F) * 1.4F;
                        drift *= 0.9F;
                        MindKit.nudge(victim, drift * 2.2F, (victim.getRandom().nextFloat() - 0.5F) * 2.5F);
                    }
                    if (age % 30 == 0) MindKit.shake(victim, 0.9F + victim.getRandom().nextFloat(), 16);
                    if (age % 25 == 0) aura(victim, id);
                    return age < TICKS;
                }
                @Override public void end() { FxNet.screen(victim, FxNet.NIGHTMARE, 0); FxNet.kill(victim.serverLevel(), victim.position(), id); }
            });
        } else {
            t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, TICKS, 1));
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, TICKS, 1));
            Suggestion.flee(p, t, TICKS);
            // the panic spreads to its pack
            for (Mob m : l.getEntitiesOfClass(Mob.class, t.getBoundingBox().inflate(6),
                    m -> m != t && m.getType() == t.getType() && Rules.canControl(p, m))) Suggestion.flee(p, m, TICKS / 2);
            h = Tasks.add(new Tasks.Task() {
                @Override public boolean tick(int age) {
                    if (!t.isAlive()) return false;
                    if (age % 25 == 0) aura(t, id);
                    if (age % 30 == 0) Fx.sound(l, t.position(), SoundEvents.WARDEN_HEARTBEAT, 0.5F, 1.4F);
                    return age < TICKS;
                }
                @Override public void end() { FxNet.kill(l, t.position(), id); }
            });
        }
        ACTIVE.put(t, h);
        Fx.bar(p, Stone.MIND, "infinity.extra.mind.nightmare.cast", t.getDisplayName());
        return true;
    }

    private static void aura(LivingEntity t, int id) {
        ServerLevel l = (ServerLevel) t.level();
        FxNet.orb(l, id, t.position(), Math.max(0.7F, t.getBbWidth()), DARK, 40, t.getId(), new Vec3(0, t.getBbHeight() * 0.55, 0));
        Fx.at(l, ParticleTypes.SMOKE, t.position().add(0, t.getBbHeight() * 0.6, 0), 6, 0.3, 0.01);
        Fx.at(l, Fx.dust(DARK, 1F), t.position().add(0, t.getBbHeight() + 0.3, 0), 4, 0.3, 0.0);
    }

    /** Private sounds around the victim, from random directions. */
    private static void haunt(ServerPlayer v, int age) {
        RandomSource r = v.getRandom();
        if (age % 20 == 0) Priv.sound(v, SoundEvents.WARDEN_HEARTBEAT, v.getEyePosition(), 0.9F, 1.0F + age / 400F);
        if (age % 9 != 0) return;
        double a = r.nextDouble() * Math.PI * 2, d = 2.5 + r.nextDouble() * 4;
        Vec3 at = v.position().add(Math.cos(a) * d, r.nextDouble() * 1.5, Math.sin(a) * d);
        switch (r.nextInt(7)) {
            case 0 -> Priv.sound(v, SoundEvents.CREEPER_PRIMED, at, 0.9F, 0.9F + r.nextFloat() * 0.2F);
            case 1, 2 -> Priv.sound(v, SoundEvents.STONE_STEP, at, 0.8F, 0.7F + r.nextFloat() * 0.3F);
            case 3 -> Priv.sound(v, holder(SoundEvents.AMBIENT_CAVE), at, 0.9F, 0.6F + r.nextFloat() * 0.4F);
            case 4 -> Priv.sound(v, SoundEvents.ZOMBIE_AMBIENT, at, 0.7F, 0.6F);
            case 5 -> Priv.sound(v, SoundEvents.SOUL_ESCAPE, at, 1F, 0.5F + r.nextFloat() * 0.3F);
            default -> Priv.sound(v, SoundEvents.SCULK_SHRIEKER_SHRIEK, at, 0.5F, 0.5F);
        }
    }

    private static Holder<SoundEvent> holder(Holder<SoundEvent> h) { return h; }
}
