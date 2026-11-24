package com.infinitystones.tensura.skill.ability.extra;

import com.infinitystones.tensura.skill.Stone;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Server particles (seen by everyone), sounds (moderate volume) and caster messages for the new abilities. */
public final class Fx {
    public static DustParticleOptions dust(Stone s, float size) { return dust(s.color(), size); }

    public static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F), Math.min(4F, size));
    }

    /** A lighter tint of the stone colour (highlights). */
    public static DustParticleOptions light(Stone s, float size) {
        int c = s.color();
        int r = (((c >> 16) & 255) + 255) / 2, g = (((c >> 8) & 255) + 255) / 2, b = ((c & 255) + 255) / 2;
        return dust((r << 16) | (g << 8) | b, size);
    }

    public static void at(ServerLevel l, ParticleOptions p, Vec3 v, int n, double spread, double speed) {
        l.sendParticles(p, v.x, v.y, v.z, n, spread, spread, spread, speed);
    }

    public static void at(ServerLevel l, ParticleOptions p, Vec3 v, int n, double sx, double sy, double sz, double speed) {
        l.sendParticles(p, v.x, v.y, v.z, n, sx, sy, sz, speed);
    }

    /** Long-distance particles (seen up to range blocks, not only 32). */
    public static void far(ServerLevel l, ParticleOptions p, Vec3 v, int n, double spread, double speed, double range) {
        for (ServerPlayer sp : l.players())
            if (sp.position().distanceToSqr(v) <= range * range) l.sendParticles(sp, p, true, v.x, v.y, v.z, n, spread, spread, spread, speed);
    }

    public static void ring(ServerLevel l, Vec3 c, double r, int n, ParticleOptions p) {
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            l.sendParticles(p, c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }

    /** Ring of particles flying outward (shockwave debris). */
    public static void burstRing(ServerLevel l, Vec3 c, int n, ParticleOptions p, double speed) {
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            l.sendParticles(p, c.x, c.y, c.z, 0, Math.cos(a), 0.15, Math.sin(a), speed);
        }
    }

    public static void shell(ServerLevel l, Vec3 c, double r, int n, ParticleOptions p) {
        var rnd = l.random;
        for (int i = 0; i < n; i++) {
            double u = rnd.nextDouble() * 2 - 1, t = rnd.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - u * u);
            l.sendParticles(p, c.x + s * Math.cos(t) * r, c.y + u * r, c.z + s * Math.sin(t) * r, 1, 0, 0, 0, 0);
        }
    }

    public static void line(ServerLevel l, Vec3 a, Vec3 b, double step, ParticleOptions p) {
        double len = a.distanceTo(b);
        int n = Mth.clamp((int) (len / step), 1, 200);
        for (int i = 0; i <= n; i++) {
            Vec3 v = a.lerp(b, i / (double) n);
            l.sendParticles(p, v.x, v.y, v.z, 1, 0, 0, 0, 0);
        }
    }

    /** Jagged bolt between two points (for arcs). */
    public static void bolt(ServerLevel l, Vec3 a, Vec3 b, ParticleOptions p) {
        var rnd = l.random;
        Vec3 prev = a;
        int seg = net.minecraft.util.Mth.clamp((int) (a.distanceTo(b) / 1.2), 2, 24);
        for (int i = 1; i <= seg; i++) {
            Vec3 next = a.lerp(b, i / (double) seg);
            if (i < seg) next = next.add((rnd.nextDouble() - 0.5) * 0.7, (rnd.nextDouble() - 0.5) * 0.7, (rnd.nextDouble() - 0.5) * 0.7);
            line(l, prev, next, 0.25, p);
            prev = next;
        }
    }

    public static void sound(ServerLevel l, Vec3 v, SoundEvent e, float vol, float pitch) {
        l.playSound(null, v.x, v.y, v.z, e, SoundSource.PLAYERS, Math.min(vol, 0.7F), pitch);
    }

    public static void sound(ServerLevel l, Vec3 v, Holder<SoundEvent> e, float vol, float pitch) { sound(l, v, e.value(), vol, pitch); }

    public static void bar(ServerPlayer p, String key, Object... args) { if (com.infinitystones.tensura.skill.NodeEffects.quiet()) return; p.displayClientMessage(Component.translatable(key, args), true); }

    public static void bar(ServerPlayer p, Stone s, String key, Object... args) {
        if (com.infinitystones.tensura.skill.NodeEffects.quiet()) return;
        p.displayClientMessage(Component.translatable(key, args).withColor(s.color() & 0xFFFFFF), true);
    }

    public static void barGray(ServerPlayer p, String key, Object... args) {
        p.displayClientMessage(Component.translatable(key, args).withStyle(ChatFormatting.GRAY), true);
    }

    /** Fizzle: gray action-bar message, returns false (the wheel reports a fizzle). */
    public static boolean fail(ServerPlayer p, String key, Object... args) { barGray(p, key, args); return false; }

    private Fx() {}
}
