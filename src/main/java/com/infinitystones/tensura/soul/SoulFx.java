package com.infinitystones.tensura.soul;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Soul Stone visuals (orange/amber/ember, seen from far away) and a tiny server-side animation scheduler.
 * Every emitter takes an optional "only" player: when set, the particles/sounds go to that player alone (used by
 * player possession, which must leave no trace for the possessed player).
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class SoulFx {
    public static final int ORANGE = 0xF28A1A, GOLD = 0xFFC860, EMBER = 0xFF4E0E, PALE = 0xFFE2B0;
    public static final DustParticleOptions D_ORANGE = Fx.dust(ORANGE, 1.6F), D_GOLD = Fx.dust(GOLD, 1.1F),
            D_EMBER = Fx.dust(EMBER, 2.2F), D_PALE = Fx.dust(PALE, 0.8F), D_BIG = Fx.dust(ORANGE, 3.2F);

    // ------------------------------------------------------------------ emitters

    public static void emit(ServerLevel l, ParticleOptions o, Vec3 v, int n, double spread, double speed, @Nullable ServerPlayer only) {
        if (only != null) { l.sendParticles(only, o, true, v.x, v.y, v.z, n, spread, spread, spread, speed); return; }
        for (ServerPlayer sp : l.players())
            if (sp.distanceToSqr(v) < 160 * 160) l.sendParticles(sp, o, true, v.x, v.y, v.z, n, spread, spread, spread, speed);
    }

    public static void sound(ServerLevel l, Vec3 v, SoundEvent e, float vol, float pitch, @Nullable ServerPlayer only) {
        if (only != null) only.playNotifySound(e, SoundSource.PLAYERS, vol, pitch);
        else l.playSound(null, v.x, v.y, v.z, e, SoundSource.PLAYERS, vol, pitch);
    }

    /** Horizontal ring, rotated by phase (radians). */
    public static void ring(ServerLevel l, Vec3 c, double r, int n, double phase, ParticleOptions o, @Nullable ServerPlayer only) {
        for (int i = 0; i < n; i++) {
            double a = phase + i * Mth.TWO_PI / n;
            emit(l, o, c.add(Math.cos(a) * r, 0, Math.sin(a) * r), 1, 0, 0, only);
        }
    }

    /** Vertical ring facing yaw (degrees) — a portal. */
    public static void ringV(ServerLevel l, Vec3 c, double r, int n, double phase, float yaw, ParticleOptions o) {
        double rx = Math.cos(Math.toRadians(yaw)), rz = Math.sin(Math.toRadians(yaw));
        for (int i = 0; i < n; i++) {
            double a = phase + i * Mth.TWO_PI / n;
            emit(l, o, c.add(rx * Math.cos(a) * r, Math.sin(a) * r, rz * Math.cos(a) * r), 1, 0, 0, null);
        }
    }

    /** Straight line of particles. */
    public static void line(ServerLevel l, Vec3 a, Vec3 b, double step, ParticleOptions o, @Nullable ServerPlayer only) {
        Vec3 d = b.subtract(a);
        int n = Math.max(1, (int) (d.length() / step));
        for (int i = 0; i <= n; i++) emit(l, o, a.add(d.scale(i / (double) n)), 1, 0, 0, only);
    }

    /** Helix of strands from a to b (soul being pulled along it). */
    public static void helix(ServerLevel l, Vec3 a, Vec3 b, int strands, double radius, double phase, ParticleOptions o, @Nullable ServerPlayer only) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 0.01) return;
        Vec3 f = d.scale(1 / len);
        Vec3 up = Math.abs(f.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = f.cross(up).normalize(), w = f.cross(u).normalize();
        int n = Math.max(6, (int) (len * 4));
        for (int s = 0; s < strands; s++)
            for (int i = 0; i <= n; i++) {
                double t = i / (double) n;
                double ang = phase + s * Mth.TWO_PI / strands + t * 9;
                double rr = radius * Math.sin(Math.PI * t);
                emit(l, o, a.add(d.scale(t)).add(u.scale(Math.cos(ang) * rr)).add(w.scale(Math.sin(ang) * rr)), 1, 0, 0, only);
            }
    }

    /** Layered magic circle: outer and inner rings turning in opposite directions + a hexagram + rune dots. */
    public static void sigil(ServerLevel l, Vec3 c, double r, double phase, @Nullable ServerPlayer only) {
        int n = (int) Math.max(24, r * 14);
        ring(l, c, r, n, phase, D_ORANGE, only);
        ring(l, c, r * 0.82, n, -phase, D_GOLD, only);
        ring(l, c, r * 0.45, n / 2, phase * 2, D_ORANGE, only);
        for (int tri = 0; tri < 2; tri++)
            for (int k = 0; k < 3; k++) {
                double a1 = -phase * 0.7 + tri * Math.PI / 3 + k * Mth.TWO_PI / 3, a2 = a1 + Mth.TWO_PI / 3;
                line(l, c.add(Math.cos(a1) * r * 0.82, 0, Math.sin(a1) * r * 0.82), c.add(Math.cos(a2) * r * 0.82, 0, Math.sin(a2) * r * 0.82),
                        0.45, D_GOLD, only);
            }
        for (int k = 0; k < 12; k++) {
            double a = phase * 1.5 + k * Mth.TWO_PI / 12;
            emit(l, ParticleTypes.SOUL_FIRE_FLAME, c.add(Math.cos(a) * r * 0.91, 0.05, Math.sin(a) * r * 0.91), 1, 0, 0, only);
        }
    }

    /** Giant stacked circles in the sky (reference: notes/ref-efeito-magnitude.webp). */
    public static void skySigil(ServerLevel l, Vec3 base, double scale, double phase) {
        sigil(l, base.add(0, 7 * scale, 0), 9 * scale, phase, null);
        sigil(l, base.add(0, 9.5 * scale, 0), 6 * scale, -phase * 1.3, null);
        sigil(l, base.add(0, 11.5 * scale, 0), 3.5 * scale, phase * 1.8, null);
    }

    /** Branching ember lightning from a to b. */
    public static void bolt(ServerLevel l, Vec3 a, Vec3 b) {
        Fx.bolt(l, a, b, D_EMBER);
        Fx.bolt(l, a, b, D_GOLD);
    }

    /** Spherical burst. */
    public static void burst(ServerLevel l, Vec3 c, double r, @Nullable ServerPlayer only) {
        emit(l, ParticleTypes.FLASH, c, 1, 0, 0, only);
        emit(l, D_BIG, c, 60, r * 0.5, 0.2, only);
        emit(l, ParticleTypes.SOUL_FIRE_FLAME, c, 50, r * 0.3, 0.25, only);
        emit(l, ParticleTypes.SOUL, c, 30, r * 0.3, 0.12, only);
    }

    // ------------------------------------------------------------------ scheduler

    /** A running animation; tick returns true when finished. */
    public interface Anim { boolean tick(); default void abort() {} }

    private static final List<Anim> ANIMS = new ArrayList<>();

    public static void play(Anim a) { ANIMS.add(a); }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post e) {
        if (ANIMS.isEmpty()) return;
        List<Anim> copy = new ArrayList<>(ANIMS);
        for (Anim a : copy) {
            boolean done;
            try { done = a.tick(); } catch (RuntimeException ex) { InfinityStones.LOGGER.warn("Soul animation failed", ex); done = true; }
            if (done) ANIMS.remove(a);
        }
    }

    @SubscribeEvent
    public static void onStop(ServerStoppingEvent e) {
        for (Anim a : new ArrayList<>(ANIMS)) a.abort();
        ANIMS.clear();
    }

    private SoulFx() {}
}
