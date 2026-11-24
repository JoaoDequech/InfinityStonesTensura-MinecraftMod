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
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Nightmare (conjunction Mind + Soul). Aimed creature lives a 10 s nightmare and is never moved or turned.
 * Player: heavy darkness (pulsing blindness + the nightmare vignette), fake mob sounds from every side heard ONLY by them
 * (hissing creepers, groans, spiders, endermen, footsteps closing in from behind, cave noises, whispers, a racing
 * heartbeat), fake hurt flashes (camera kick, red vignette, hurt sound, no damage) and hallucinated shadow mobs that flicker
 * at the edge of the view and vanish when looked at (packet-only entities, nobody else sees them).
 * Mob: panics, flees from you and is confused (its pack mates nearby scatter), weakened.
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
            victim.addEffect(new MobEffectInstance(MobEffects.DARKNESS, TICKS + 20, 0, true, false, false));
            FxNet.screen(victim, FxNet.NIGHTMARE, TICKS);
            FxNet.flash(victim, 0x000000, 0.7F, 16);
            MindKit.shake(victim, 1.2F, 20);
            final Dream dream = new Dream(victim);
            h = Tasks.add(new Tasks.Task() {
                @Override public boolean tick(int age) {
                    if (!victim.isAlive() || victim.hasDisconnected() || victim.level() != l) return false;
                    dream.tick(age);
                    if (age % 25 == 0) aura(victim, id);
                    return age < TICKS;
                }
                @Override public void end() {
                    dream.clear();
                    FxNet.screen(victim, FxNet.NIGHTMARE, 0);
                    FxNet.kill(victim.serverLevel(), victim.position(), id);
                    victim.removeEffect(MobEffects.DARKNESS);
                    victim.removeEffect(MobEffects.BLINDNESS);
                }
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
                    if (t instanceof Mob mob && age % 10 == 0) mob.setTarget(null);
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


    private static final EntityType<?>[] GHOSTS = {EntityType.ENDERMAN, EntityType.ZOMBIE, EntityType.CREEPER, EntityType.SPIDER, EntityType.SKELETON, EntityType.ENDERMAN, EntityType.WARDEN};

    /** Everything the victim alone experiences. Server side only: sounds and fake entities go out as vanilla packets to this one player. */
    private static final class Dream {
        final ServerPlayer v;
        final RandomSource r;
        final Map<Integer, Integer> ghosts = new HashMap<>(); // fake entity id -> age when it appeared
        final Map<Integer, Vec3> ghostAt = new HashMap<>();
        int stalk = -1, nextHurt = 45, nextGhost = 30;

        Dream(ServerPlayer v) { this.v = v; this.r = v.getRandom(); }

        void tick(int age) {
            ServerLevel l = v.serverLevel();
            int beat = age < 120 ? 20 : age < 170 ? 15 : 10;
            if (age % beat == 0) Priv.sound(v, SoundEvents.WARDEN_HEARTBEAT, v.getEyePosition(), 1.0F, 0.9F + age / 300F);
            if (age == 50 || age == 115 || age == 165) v.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 26, 0, true, false, false));
            if (age % 40 == 5) MindKit.shake(v, 0.7F + r.nextFloat() * 0.8F, 14);
            if (age % 8 == 0 && r.nextInt(3) > 0) around();
            if (age % 70 == 20) whisper();
            if (stalk < 0 && age > 25 && age % 55 == 0) stalk = 0;
            if (stalk >= 0 && age % 6 == 0) stalkStep();
            if (age >= nextGhost) { spawnGhost(age); nextGhost = age + 18 + r.nextInt(26); }
            if (age >= nextHurt) { fakeHurt(); nextHurt = age + 45 + r.nextInt(30); }
            vanishWatched(age);
        }

        Vec3 around(double minD, double maxD) {
            double a = r.nextDouble() * Math.PI * 2, d = minD + r.nextDouble() * (maxD - minD);
            return v.position().add(Math.cos(a) * d, r.nextDouble() * 1.6, Math.sin(a) * d);
        }

        void around() {
            Vec3 at = around(2.5, 7);
            switch (r.nextInt(12)) {
                case 0 -> Priv.sound(v, SoundEvents.CREEPER_PRIMED, at, 1.0F, 0.9F + r.nextFloat() * 0.2F);
                case 1 -> Priv.sound(v, SoundEvents.ZOMBIE_AMBIENT, at, 0.9F, 0.6F + r.nextFloat() * 0.3F);
                case 2 -> Priv.sound(v, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, at, 0.7F, 0.7F);
                case 3 -> Priv.sound(v, SoundEvents.SPIDER_AMBIENT, at, 0.9F, 0.8F + r.nextFloat() * 0.3F);
                case 4 -> Priv.sound(v, SoundEvents.ENDERMAN_STARE, at, 0.6F, 0.6F);
                case 5 -> Priv.sound(v, SoundEvents.ENDERMAN_SCREAM, at, 0.5F, 0.5F + r.nextFloat() * 0.3F);
                case 6 -> Priv.sound(v, SoundEvents.SKELETON_AMBIENT, at, 0.9F, 0.7F);
                case 7 -> Priv.sound(v, holder(SoundEvents.AMBIENT_CAVE), at, 1.0F, 0.6F + r.nextFloat() * 0.4F);
                case 8 -> Priv.sound(v, SoundEvents.GHAST_AMBIENT, at, 0.5F, 0.5F);
                case 9 -> Priv.sound(v, SoundEvents.CREEPER_HURT, at, 0.8F, 0.6F);
                case 10 -> Priv.sound(v, SoundEvents.PHANTOM_AMBIENT, at, 0.7F, 0.6F);
                default -> Priv.sound(v, SoundEvents.SCULK_SHRIEKER_SHRIEK, at, 0.4F, 0.5F);
            }
        }

        void whisper() {
            for (int i = 0; i < 3; i++) {
                Vec3 at = around(0.8, 2.2);
                Priv.sound(v, SoundEvents.SOUL_ESCAPE.value(), at, 0.8F, 0.45F + r.nextFloat() * 0.25F);
            }
            Priv.sound(v, SoundEvents.WARDEN_NEARBY_CLOSE, v.getEyePosition().add(v.getLookAngle().scale(-2)), 0.5F, 0.7F);
        }

        /** Steps closing in from behind, one every 6 ticks, ending with a breath right at the neck. */
        void stalkStep() {
            Vec3 back = v.getLookAngle().multiply(1, 0, 1);
            back = back.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : back.normalize().scale(-1);
            double d = 9 - stalk * 1.4;
            Vec3 at = v.position().add(back.scale(d)).add(back.yRot(1.5708F).scale((r.nextDouble() - 0.5) * 1.5));
            BlockPos below = BlockPos.containing(at.x, v.getY() - 0.5, at.z);
            var step = v.serverLevel().getBlockState(below).getSoundType().getStepSound();
            Priv.sound(v, step, at, 1.0F, 0.75F + r.nextFloat() * 0.2F);
            if (++stalk >= 6) {
                Priv.sound(v, SoundEvents.WARDEN_NEARBY_CLOSER, v.getEyePosition().add(back.scale(0.7)), 0.8F, 0.6F);
                Priv.sound(v, SoundEvents.PLAYER_BREATH, v.getEyePosition().add(back.scale(0.5)), 1.0F, 0.6F);
                stalk = -1;
            }
        }

        /** A hit that never lands: camera kick, red vignette, hurt sound. No damage at all. */
        void fakeHurt() {
            around();
            Vec3 from = around(1.0, 2.0);
            Priv.sound(v, SoundEvents.PLAYER_HURT, v.position().add(0, 1, 0), 1.0F, 0.9F + r.nextFloat() * 0.2F);
            float yaw = MindKit.yawTo(v, from) - v.getYRot();
            v.connection.send(new ClientboundHurtAnimationPacket(v.getId(), yaw));
            FxNet.flash(v, 0xAA0000, 0.55F, 12);
            MindKit.shake(v, 2.0F, 10);
        }

        void spawnGhost(int age) {
            if (ghosts.size() >= 2) return;
            ServerLevel l = v.serverLevel();
            Vec3 look = v.getLookAngle().multiply(1, 0, 1);
            double base = Math.atan2(look.z, look.x);
            boolean behind = r.nextInt(3) == 0;
            double off = behind ? Math.PI + (r.nextDouble() - 0.5) * 1.0 : (r.nextBoolean() ? 1 : -1) * (0.95 + r.nextDouble() * 0.75);
            double dist = 5 + r.nextDouble() * 7;
            double x = v.getX() + Math.cos(base + off) * dist, z = v.getZ() + Math.sin(base + off) * dist;
            double y = v.getY();
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, y, z);
            for (int i = 0; i < 4 && l.getBlockState(m.below()).getCollisionShape(l, m.below()).isEmpty(); i++) m.move(0, -1, 0);
            Entity fake = GHOSTS[r.nextInt(GHOSTS.length)].create(l);
            if (fake == null) return;
            Vec3 at = new Vec3(x, m.getY(), z);
            float yaw = (float) (net.minecraft.util.Mth.atan2(v.getZ() - at.z, v.getX() - at.x) * net.minecraft.util.Mth.RAD_TO_DEG) - 90F;
            v.connection.send(new ClientboundAddEntityPacket(fake.getId(), java.util.UUID.randomUUID(), at.x, at.y, at.z, 0F, yaw, fake.getType(), 0, Vec3.ZERO, yaw));
            ghosts.put(fake.getId(), age);
            ghostAt.put(fake.getId(), at);
            Priv.sound(v, SoundEvents.AMBIENT_CAVE.value(), at, 0.7F, 0.5F);
        }

        /** Shadows flicker out after a few ticks, or at once when the victim looks straight at them. */
        void vanishWatched(int age) {
            if (ghosts.isEmpty()) return;
            Vec3 eye = v.getEyePosition(), look = v.getLookAngle();
            List<Integer> gone = new ArrayList<>();
            for (Map.Entry<Integer, Integer> en : ghosts.entrySet()) {
                int life = age - en.getValue();
                Vec3 to = ghostAt.get(en.getKey()).add(0, 1, 0).subtract(eye);
                boolean watched = life >= 2 && to.normalize().dot(look) > 0.92;
                if (watched || life >= 16 + (en.getKey() & 7)) gone.add(en.getKey());
            }
            if (gone.isEmpty()) return;
            for (int id : gone) { ghosts.remove(id); ghostAt.remove(id); }
            v.connection.send(new ClientboundRemoveEntitiesPacket(gone.stream().mapToInt(Integer::intValue).toArray()));
        }

        void clear() {
            if (ghosts.isEmpty()) return;
            int[] ids = ghosts.keySet().stream().mapToInt(Integer::intValue).toArray();
            ghosts.clear(); ghostAt.clear();
            try { v.connection.send(new ClientboundRemoveEntitiesPacket(ids)); } catch (RuntimeException ignored) { }
        }
    }

    private static Holder<SoundEvent> holder(Holder<SoundEvent> h) { return h; }
}
