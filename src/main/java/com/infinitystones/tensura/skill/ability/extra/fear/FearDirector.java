package com.infinitystones.tensura.skill.ability.extra.fear;

import com.infinitystones.tensura.skill.ability.extra.Priv;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Scares sent to one victim. {@link #tick} runs the AUTO director (escalating, randomised); {@link #fire} and
 * {@link #apparition} are also what the control room triggers by hand. Events only ever show or play something to that
 * player: no damage, no item loss (the real danger comes from creatures in KILLER mode, see {@link FearSessions}).
 */
final class FearDirector {
    static final int GRACE = 200, MESSAGES = 10;

    /** Called every tick while the victim is online in the realm. */
    static void tick(FearLive live, ServerPlayer v) {
        for (Iterator<FearLive.Timed> it = live.timers.iterator(); it.hasNext(); ) {
            FearLive.Timed t = it.next();
            if (live.age >= t.at) { it.remove(); t.run.accept(v); }
        }
        if (live.fallLeft >= 0) fall(live, v);
        if (live.fakeDeadUntil >= 0 && live.age >= live.fakeDeadUntil) live.fakeDeadUntil = -1;
        if (!live.flash) live.scare(0.012F);
        live.fear = Math.max(0F, live.fear - 0.004F);
        if (!live.auto || live.age < GRACE || live.busy()) return;
        if (live.next == 0) live.next = live.age + 40;
        if (live.age < live.next) return;
        RandomSource r = v.getRandom();
        float k = live.intensity >= 0 ? live.intensity / 100F * Mth.clamp((live.age - GRACE) / 1200F, 0.15F, 1F) : Mth.clamp((live.age - GRACE) / 3000F, 0F, 1F);
        int kind = pick(live, r, k);
        if (kind == -1) { live.next = live.age + 100; return; }
        if (kind == 0) apparition(live, v, null);
        else fire(live, v, kind);
        live.next = live.age + Math.max(30, (int) ((Math.max(45, (int) (160 - 100 * k) + r.nextInt(60))) * 100F / Math.max(10, live.freq)));
    }

    private static int pick(FearLive live, RandomSource r, float k) {
        List<int[]> w = new ArrayList<>();
        w.add(new int[]{FearNet.FOOTSTEPS, 3}); w.add(new int[]{FearNet.WHISPER, 3}); w.add(new int[]{FearNet.FAKE_CHAT, 2}); w.add(new int[]{FearNet.CREEPER, 2});
        w.add(new int[]{FearNet.FLASH_FLICKER, 2});
        if (k > 0.08F) { w.add(new int[]{FearNet.LOUD, 2}); w.add(new int[]{FearNet.EYES, 2}); w.add(new int[]{FearNet.HEARTBEAT, 2}); }
        if (k > 0.12F) { w.add(new int[]{0, 4}); w.add(new int[]{FearNet.PULSE, 2}); w.add(new int[]{FearNet.SCREEN_TEAR, 1}); }
        if (k > 0.2F) { w.add(new int[]{FearNet.FLASH_OFF, 2}); w.add(new int[]{FearNet.LIGHTNING, 2}); w.add(new int[]{FearNet.BLACKOUT, 1}); }
        if (k > 0.28F) w.add(new int[]{FearNet.DOPPEL, 2});
        if (k > 0.33F && live.age - live.lastHeavy > 420) {
            int[] heavy = {FearNet.JUMPSCARE, FearNet.JUMPSCARE, FearNet.FAKE_DEATH, FearNet.FAKE_LAVA, FearNet.VOID_FALL, FearNet.FLIP, FearNet.LOST, FearNet.INV_EMPTY};
            if ((live.cats & 8) != 0 && r.nextFloat() < 0.35F + k * 0.4F) { live.lastHeavy = live.age; return heavy[r.nextInt(heavy.length)]; }
        }
        w.removeIf(e -> !allowed(live, e[0]));
        if (w.isEmpty()) return -1;
        int total = 0;
        for (int[] e : w) total += e[1];
        int roll = r.nextInt(total);
        for (int[] e : w) { roll -= e[1]; if (roll < 0) return e[0]; }
        return FearNet.WHISPER;
    }

    /** Effect categories of a preset: 1 sounds, 2 visual, 4 apparitions, 8 heavy. */
    private static boolean allowed(FearLive live, int kind) {
        int c = live.cats;
        return switch (kind) {
            case 0 -> (c & 4) != 0;
            case FearNet.FOOTSTEPS, FearNet.WHISPER, FearNet.FAKE_CHAT, FearNet.CREEPER, FearNet.LOUD, FearNet.HEARTBEAT -> (c & 1) != 0;
            case FearNet.FLASH_FLICKER, FearNet.PULSE, FearNet.SCREEN_TEAR, FearNet.EYES, FearNet.FLASH_OFF, FearNet.LIGHTNING, FearNet.BLACKOUT, FearNet.DOPPEL, FearNet.LIGHTS_OUT -> (c & 2) != 0;
            default -> (c & 8) != 0;
        };
    }

    // ------------------------------------------------------------------ apparitions (made by the creatures part)

    /** {@code kind} null = a random one. Without a creatures provider a jumpscare stands in. */
    static void apparition(FearLive live, ServerPlayer v, String kind) {
        FearApi.Creatures c = FearApi.creatures();
        ServerLevel l = v.serverLevel();
        if (c == null) { fire(live, v, FearNet.JUMPSCARE); return; }
        List<String> kinds = c.apparitionKinds();
        if (kind == null) {
            if (kinds == null || kinds.isEmpty()) return;
            kind = kinds.get(v.getRandom().nextInt(kinds.size()));
        }
        live.scare(18F);
        c.apparition(l, v, kind);
    }

    // ------------------------------------------------------------------ the effects

    static void fire(FearLive live, ServerPlayer v, int kind) {
        if (live.state != FearLive.ACTIVE || v.level().dimension() != FearRealm.DIM) return;
        RandomSource r = v.getRandom();
        switch (kind) {
            case FearNet.JUMPSCARE -> { live.scare(35F); live.rec.mark("jump", live.total); FearNet.send(v, kind, r.nextInt(3), 0); }
            case FearNet.LOUD -> { live.scare(14F); FearNet.send(v, kind, r.nextInt(6), 0); }
            case FearNet.FAKE_DEATH -> {
                live.fakeDeadUntil = live.age + 900;
                live.rec.mark("fake", live.total);
                live.scare(30F);
                FearNet.send(v, kind, 0, 0);
            }
            case FearNet.FAKE_LAVA -> { live.scare(25F); FearNet.send(v, kind, 130, 0); }
            case FearNet.VOID_FALL -> {
                live.fallLeft = 150;
                live.fallX = v.getX(); live.fallY = v.getY(); live.fallZ = v.getZ();
                live.scare(28F);
                FearNet.send(v, kind, 150, 0);
            }
            case FearNet.LIGHTS_OUT -> {
                v.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 140, 0, true, false, false));
                FearNet.send(v, kind, 140, 0);
                Priv.sound(v, SoundEvents.REDSTONE_TORCH_BURNOUT, v.getEyePosition(), 1F, 0.6F);
            }
            case FearNet.FOOTSTEPS -> footsteps(live, v);
            case FearNet.WHISPER -> {
                for (int i = 0; i < 4; i++) {
                    final int n = i;
                    live.after(i * 9, p -> {
                        Vec3 a = around(p, 0.7, 2.0);
                        Priv.sound(p, SoundEvents.SOUL_ESCAPE.value(), a, 0.9F, 0.4F + 0.07F * n + p.getRandom().nextFloat() * 0.15F);
                    });
                }
                live.scare(8F);
                Priv.sound(v, SoundEvents.WARDEN_NEARBY_CLOSER, v.getEyePosition().add(v.getLookAngle().scale(-2)), 0.5F, 0.6F);
                FearNet.send(v, new FearNet.Event(kind, r.nextInt(), 0, 0, 0, 0, 0, v.getGameProfile().getName()));
            }
            case FearNet.FAKE_CHAT -> { live.scare(6F); FearNet.send(v, new FearNet.Event(kind, r.nextInt(), r.nextInt(MESSAGES), 0, 0, 0, 0, friend(live, v))); }
            case FearNet.INV_EMPTY -> { live.scare(18F); FearNet.send(v, kind, 110, 0); }
            case FearNet.DOPPEL -> { live.scare(24F); FearNet.send(v, kind, 260, 0); }
            case FearNet.CREEPER -> { live.scare(10F); creeper(live, v); }
            case FearNet.FLIP -> { live.scare(18F); FearNet.send(v, kind, 130, 0); }
            case FearNet.PULSE -> FearNet.send(v, kind, 220, 0);
            case FearNet.LOST -> { live.scare(20F); FearNet.send(v, kind, 320, 0); }
            case FearNet.FLASH_OFF -> { live.scare(12F); FearNet.send(v, kind, 200 + r.nextInt(160), 0); }
            case FearNet.FLASH_FLICKER -> { live.scare(6F); FearNet.send(v, kind, 70, 0); }
            case FearNet.BLACKOUT -> { live.scare(22F); FearNet.send(v, kind, 220, 0); }
            case FearNet.LIGHTNING -> { live.scare(10F); FearNet.send(v, kind, 0, 0); Priv.sound(v, SoundEvents.LIGHTNING_BOLT_THUNDER, v.getEyePosition().add(v.getLookAngle().scale(40)), 1F, 0.8F); }
            case FearNet.EYES -> { live.scare(14F); FearNet.send(v, kind, 260, 0); }
            case FearNet.SCREEN_TEAR -> { live.scare(8F); FearNet.send(v, kind, 50, 0); }
            case FearNet.HEARTBEAT -> { live.scare(6F); FearNet.send(v, kind, 240, 0); }
            case FearNet.SHOCK -> FearNet.send(v, kind, 0, 0);
            default -> { }
        }
    }

    // ------------------------------------------------------------------ helpers

    private static Vec3 around(ServerPlayer v, double min, double max) {
        RandomSource r = v.getRandom();
        double a = r.nextDouble() * Math.PI * 2, d = min + r.nextDouble() * (max - min);
        return v.position().add(Math.cos(a) * d, 0.5 + r.nextDouble() * 1.5, Math.sin(a) * d);
    }

    static double ground(ServerLevel l, double x, double z, double fromY) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(fromY) + 8, Mth.floor(z));
        if (!l.hasChunkAt(p)) return fromY;
        for (int i = 0; i < 40; i++) {
            if (!l.getBlockState(p).getCollisionShape(l, p).isEmpty()) return p.getY() + 1;
            p.move(0, -1, 0);
        }
        return fromY;
    }

    private static String friend(FearLive live, ServerPlayer v) {
        List<String> names = new ArrayList<>();
        for (ServerPlayer p : v.server.getPlayerList().getPlayers()) if (p != v && !p.getUUID().equals(live.caster)) names.add(p.getGameProfile().getName());
        if (names.isEmpty()) for (ServerPlayer p : v.server.getPlayerList().getPlayers()) if (p != v) names.add(p.getGameProfile().getName());
        if (names.isEmpty()) return "Steve";
        return names.get(v.getRandom().nextInt(names.size()));
    }

    private static void footsteps(FearLive live, ServerPlayer v) {
        live.scare(8F);
        for (int i = 0; i < 7; i++) {
            final int n = i;
            live.after(i * 7, p -> {
                Vec3 look = p.getLookAngle().multiply(1, 0, 1);
                Vec3 back = look.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : look.normalize().scale(-1);
                Vec3 at = p.position().add(back.scale(10 - n * 1.4)).add(back.yRot(1.5708F).scale((p.getRandom().nextDouble() - 0.5) * 1.5));
                var below = p.serverLevel().getBlockState(BlockPos.containing(at.x, p.getY() - 0.5, at.z));
                Priv.sound(p, below.getSoundType().getStepSound(), at, 1F, 0.7F + p.getRandom().nextFloat() * 0.15F);
                if (n == 6) Priv.sound(p, SoundEvents.PLAYER_BREATH, p.getEyePosition().add(back.scale(0.5)), 1F, 0.55F);
            });
        }
    }

    private static void creeper(FearLive live, ServerPlayer v) {
        Vec3 look = v.getLookAngle().multiply(1, 0, 1);
        Vec3 back = look.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : look.normalize().scale(-1);
        Vec3 at = v.getEyePosition().add(back.scale(2.4));
        Priv.sound(v, SoundEvents.CREEPER_PRIMED, at, 1F, 1F);
        live.after(24, p -> Priv.sound(p, SoundEvents.CREEPER_PRIMED, at, 0.8F, 1F));
        live.after(40, p -> Priv.sound(p, SoundEvents.CREEPER_HURT, at, 0.6F, 0.5F));
    }

    /** The real endless fall: up to the sky, down through darkness, up again; at the end back on the exact spot. */
    private static void fall(FearLive live, ServerPlayer v) {
        ServerLevel l = v.serverLevel();
        if (live.fallLeft == 150) {
            v.teleportTo(l, live.fallX, 244, live.fallZ, v.getYRot(), v.getXRot());
            v.setDeltaMovement(0, -1.2, 0);
            v.hurtMarked = true;
        }
        v.fallDistance = 0F;
        if (v.getY() < 150 && live.fallLeft > 6) {
            v.teleportTo(l, v.getX(), 244, v.getZ(), v.getYRot(), v.getXRot());
            v.setDeltaMovement(v.getDeltaMovement().x, -1.6, v.getDeltaMovement().z);
            v.hurtMarked = true;
        }
        if (--live.fallLeft <= 0) {
            v.teleportTo(l, live.fallX, live.fallY, live.fallZ, v.getYRot(), v.getXRot());
            v.setDeltaMovement(Vec3.ZERO);
            v.hurtMarked = true;
            v.fallDistance = 0F;
            live.fallLeft = -1;
            FearNet.send(v, FearNet.WAKE_FLASH, 0, 0);
        }
    }

    private FearDirector() {}
}
