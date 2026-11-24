package com.infinitystones.tensura.skill.ability.extra.soul;

import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Ground;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rift of Souls (conjunction Space + Soul). Left (aim at the ground): a rift of orange and blue light tears open above the
 * aimed spot under a giant double rune circle and, for 6 s, drags every creature within 12 blocks toward its centre (players
 * too, bosses are hurt but not dragged) while their souls stream into it, hurting more the closer they get. Using it again
 * (or the end of the time) collapses the rift: a huge shockwave deals 24 to everything in range, and the souls it swallowed
 * (up to 4 creatures that died in it) come back as spectral allies of yours for 30 s. No blocks are touched.
 */
public final class SoulRift implements Ability {
    public static final SoulRift INSTANCE = new SoulRift();
    private static final String K = "infinity.extra.rift.";
    private static final int TICKS = 120;
    private static final float R = 12F;
    private static final int BLUE = Stone.SPACE.color() & 0xFFFFFF;

    private static final class Rift {
        final UUID owner; final ServerLevel level; final Vec3 c; final Vec3 core; final int id = FxNet.newId();
        Tasks.Handle handle; boolean collapse;
        Rift(UUID owner, ServerLevel level, Vec3 c) { this.owner = owner; this.level = level; this.c = c; this.core = c.add(0, 5, 0); }
    }

    private static final Map<UUID, Rift> RIFTS = new HashMap<>();

    @Override public Stone stone() { return Stone.SPACE; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        Rift live = RIFTS.get(p.getUUID());
        if (live != null) { live.collapse = true; live.handle.cancel(); return true; }
        ServerLevel l = p.serverLevel();
        Vec3 c = Aim.ground(p, 48);
        Rift r = new Rift(p.getUUID(), l, c);
        RIFTS.put(p.getUUID(), r);
        open(p, l, r);
        r.handle = Tasks.add(new Tasks.Task() {
            @Override public boolean tick(int age) {
                if (RIFTS.get(r.owner) != r || !p.isAlive() || p.hasDisconnected()) return false;
                run(p, r, age);
                if (age >= TICKS) { r.collapse = true; return false; }
                return true;
            }
            @Override public void end() {
                RIFTS.remove(r.owner, r);
                FxNet.kill(l, r.c, r.id);
                FxNet.kill(l, r.c, r.id + 1);
                if (r.collapse && !p.hasDisconnected()) collapse(p, r);
                else Fx.sound(l, r.c, SoundEvents.AMETHYST_BLOCK_BREAK, 0.5F, 0.5F);
            }
        });
        return true;
    }

    private static Vec3 facing(ServerPlayer p, Vec3 c) {
        Vec3 d = p.position().subtract(c).multiply(1, 0, 1);
        return d.lengthSqr() < 0.01 ? new Vec3(0, 0, 1) : d.normalize();
    }

    private static void open(ServerPlayer p, ServerLevel l, Rift r) {
        Vec3 c = r.c;
        SoulKit.circle(l, c, 9F, SoulKit.ORANGE, TICKS + 10);
        FxNet.runes(l, 0, c.add(0, 0.1, 0), 13F, BLUE, TICKS + 10, -0.7F);
        FxNet.runes(l, 0, c.add(0, 0.12, 0), 5F, SoulKit.PALE, TICKS + 10, 2.2F);
        FxNet.pillar(l, 0, c, 1.6F, 11F, SoulKit.ORANGE, 40);
        FxNet.shockwave(l, c.add(0, 0.1, 0), 14F, BLUE, 24);
        FxNet.shake(l, c, 40, 0.5F, 16);
        FxNet.flash(p, SoulKit.LIGHT, 0.2F, 8);
        Fx.sound(l, c, SoundEvents.END_PORTAL_SPAWN, 0.4F, 0.6F);
        Fx.sound(l, c, SoundEvents.SOUL_ESCAPE.value(), 0.7F, 0.5F);
        Fx.sound(l, c, SoundEvents.WARDEN_EMERGE, 0.25F, 1.4F);
        Fx.burstRing(l, c.add(0, 0.3, 0), 60, ParticleTypes.SOUL, 0.6);
        Fx.bar(p, Stone.SOUL, K + "open");
    }

    private static void run(ServerPlayer p, Rift r, int age) {
        ServerLevel l = r.level;
        Vec3 core = r.core;
        if (age % 20 == 0) {
            Vec3 n = facing(p, r.c);
            FxNet.portal(l, r.id, core, n, 5.2F, SoulKit.ORANGE, 40);
            FxNet.portal(l, r.id + 1, core, new Vec3(-n.z, 0, n.x), 4.0F, BLUE, 40);
            FxNet.orb(l, r.id + 2, core, 1.6F, SoulKit.PALE, 40, -1, Vec3.ZERO);
            FxNet.spiral(l, r.c, 7F, 5F, SoulKit.LIGHT, 24, -1);
            FxNet.spiral(l, r.c, 11F, -5F, BLUE, 24, -1);
        }
        if (age % 30 == 0) Fx.sound(l, core, SoundEvents.WARDEN_HEARTBEAT, 0.6F, 0.6F + age / 240F);
        if (age % 40 == 5) Fx.sound(l, core, SoundEvents.SOUL_ESCAPE.value(), 0.5F, 0.5F + age / 200F);
        if (age % 10 == 0) FxNet.shake(l, r.c, 36, 0.2F + age / 400F, 10);

        // souls streaming in from a ring of random points
        for (int i = 0; i < 10; i++) {
            double a = l.random.nextDouble() * Math.PI * 2, d = 3 + l.random.nextDouble() * (R - 3);
            Vec3 from = r.c.add(Math.cos(a) * d, 0.3 + l.random.nextDouble() * 2.5, Math.sin(a) * d);
            Vec3 dir = core.subtract(from).normalize();
            l.sendParticles(i % 3 == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.SOUL, from.x, from.y, from.z, 0, dir.x, dir.y, dir.z, 0.35);
            if (i % 2 == 0) l.sendParticles(Fx.dust(i % 4 == 0 ? BLUE : SoulKit.ORANGE, 1.4F), from.x, from.y, from.z, 0, dir.x, dir.y, dir.z, 0.3);
        }
        Fx.at(l, Fx.dust(SoulKit.PALE, 2F), core, 4, 0.7, 0);

        AABB box = new AABB(r.c, r.c).inflate(R, 12, R);
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, box, e -> Rules.canHarm(p, e))) {
            Vec3 at = e.position().add(0, e.getBbHeight() * 0.5, 0);
            double flat = Math.hypot(at.x - r.c.x, at.z - r.c.z);
            if (flat > R) continue;
            double near = 1 - flat / R;
            if (!Rules.isBoss(e)) {
                Vec3 pull = core.subtract(at);
                Vec3 dir = pull.lengthSqr() < 1 ? Vec3.ZERO : pull.normalize();
                Vec3 v = e.getDeltaMovement().scale(0.75).add(dir.scale(0.1 + near * 0.22));
                if (v.length() > 1.1) v = v.normalize().scale(1.1);
                e.setDeltaMovement(v);
                e.hurtMarked = true;
                e.fallDistance = 0;
            }
            if (age % 10 == 0) {
                Rules.hurtPct(p, e, (float) (2.0 + 4.0 * near), (float) (0.02 + 0.03 * near));
                SoulKit.soulBurst(l, at, 0.5F);
                FxNet.beam(l, at, core, SoulKit.ORANGE, 0.06F, 6);
            } else if (age % 5 == 0) {
                FxNet.tetherPoint(l, r.id + 10 + e.getId() % 500, e, core, SoulKit.PALE, 0.04F, 6);
            }
        }
    }

    private static void collapse(ServerPlayer p, Rift r) {
        ServerLevel l = r.level;
        Vec3 c = r.c, core = r.core;
        FxNet.shockwave(l, c.add(0, 0.1, 0), R + 4, SoulKit.ORANGE, 24);
        FxNet.shockwave(l, c.add(0, 0.1, 0), R, BLUE, 20);
        FxNet.shockwave(l, c.add(0, 0.1, 0), R * 0.6F, SoulKit.PALE, 16);
        FxNet.dome(l, 0, c.add(0, 2, 0), 9F, SoulKit.ORANGE, 16);
        FxNet.flare(l, core, 9F, SoulKit.PALE, 14);
        FxNet.pillar(l, 0, c, 3F, 30F, SoulKit.PALE, 14);
        FxNet.flashAround(l, c, 48, SoulKit.PALE, 0.7F, 18);
        FxNet.shake(l, c, 64, 1.2F, 24);
        Fx.burstRing(l, c.add(0, 0.4, 0), 72, ParticleTypes.SOUL_FIRE_FLAME, 0.8);
        Fx.burstRing(l, c.add(0, 0.4, 0), 72, Fx.dust(SoulKit.ORANGE, 2.2F), 0.9);
        Fx.far(l, ParticleTypes.EXPLOSION_EMITTER, core, 1, 0, 0, 96);
        Fx.sound(l, c, SoundEvents.GENERIC_EXPLODE.value(), 0.55F, 0.6F);
        Fx.sound(l, c, SoundEvents.WITHER_BREAK_BLOCK, 0.4F, 0.6F);
        Fx.sound(l, c, SoundEvents.SOUL_ESCAPE.value(), 0.7F, 0.4F);
        Fx.sound(l, c, SoundEvents.BELL_RESONATE, 0.6F, 0.5F);

        AABB box = new AABB(c, c).inflate(R, 12, R);
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, box, e -> Rules.canHarm(p, e))) {
            if (e.position().multiply(1, 0, 1).distanceToSqr(c.multiply(1, 0, 1)) > R * R) continue;
            Rules.hurtPct(p, e, 24F, 0.15F);
            Vec3 away = e.position().subtract(c).multiply(1, 0, 1);
            away = away.lengthSqr() < 0.01 ? new Vec3(0, 0, 0) : away.normalize();
            if (!Rules.isBoss(e)) e.push(away.x * 1.2, 0.7, away.z * 1.2);
            e.hurtMarked = true;
            SoulKit.soulBurst(l, e.position().add(0, e.getBbHeight() * 0.5, 0), 0.8F);
        }

        // the souls it swallowed come back on your side
        List<SoulKit.Corpse> souls = SoulKit.take(l, c, R + 6, TICKS + 20, 4);
        int raised = 0;
        for (SoulKit.Corpse k : souls) {
            double a = Math.PI * 2 * raised / Math.max(1, souls.size());
            Vec3 at = Ground.below(l, c.add(Math.cos(a) * 3, 2, Math.sin(a) * 3), 8);
            float hp = Mth.clamp(k.maxHealth() * 1.3F, 24F, 70F);
            Ghosts.Ghost g = Ghosts.spawn(p, k.type(), at, k.yaw(), Ghosts.ARMY, 600, hp, Mth.clamp(hp * 0.3F, 7F, 16F), SoulKit.ORANGE, null, 0);
            if (g != null) {
                raised++;
                FxNet.beam(l, core, at.add(0, 1, 0), SoulKit.PALE, 0.2F, 10);
            }
        }
        Fx.bar(p, Stone.SOUL, K + "collapse", raised);
    }
}
