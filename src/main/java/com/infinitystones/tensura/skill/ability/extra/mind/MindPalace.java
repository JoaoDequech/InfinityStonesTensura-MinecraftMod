package com.infinitystones.tensura.skill.ability.extra.mind;

import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.ExtraSettings;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Priv;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import com.infinitystones.tensura.skill.ability.extra.power.PowerNet;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Mind Palace (toggle). Time stretches inside the palace: you move and strike fast, every creature around you is
 * slowed and projectiles crawl (bullet time). Only you see a golden outline on each creature through walls, a dotted
 * line showing where it is heading (about two seconds ahead) and a red-orange thread to whoever it is about to attack.
 * Alt + scroll: palace radius 12–32.
 */
public final class MindPalace implements Ability {
    public static final MindPalace INSTANCE = new MindPalace();
    static final String MODE = "mind.mind_palace";
    private static final int THREAT = 0xFF6A3D;
    private static final int MAX_SEEN = 24, MAX_SHOTS = 16;

    private static final class State {
        final Set<Integer> glowed = new HashSet<>();
        final Map<Integer, Vec3> last = new HashMap<>();
        final int zone = PowerNet.newId(), inner = PowerNet.newId(), crown = FxNet.newId();
        int age;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    @Override public Stone stone() { return Stone.MIND; }

    @Override
    public void start(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        STATES.put(p.getUUID(), new State());
        Vec3 o = p.position();
        PowerNet.circle(l, 0, o.add(0, 0.06, 0), new Vec3(0, 1, 0), 5F, MindKit.GOLD, MindKit.PALE, 46, 3F, PowerNet.RAYS);
        PowerNet.circle(l, 0, o.add(0, 0.07, 0), new Vec3(0, 1, 0), 3F, MindKit.PALE, MindKit.GOLD, 46, -4F, PowerNet.GLYPHS_ONLY);
        PowerNet.circle(l, 0, o.add(0, 1.0, 0), p.getLookAngle(), 2.2F, MindKit.GOLD, MindKit.PALE, 40, 2.5F, PowerNet.SIMPLE);
        FxNet.shockwave(l, o.add(0, 0.1, 0), 9F, MindKit.GOLD, 24);
        FxNet.spiral(l, o, 1.1F, 3.2F, MindKit.GOLD, 30, p.getId());
        FxNet.flash(p, MindKit.GOLD, 0.35F, 14);
        MindKit.spark(l, o.add(0, 1, 0), 30);
        Fx.sound(l, o, SoundEvents.BEACON_ACTIVATE, 0.5F, 1.7F);
        Priv.sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, p.getEyePosition(), 0.6F, 0.5F);
        Fx.bar(p, Stone.MIND, "infinity.extra.mind.palace.on");
    }

    @Override
    public void tick(ServerPlayer p) {
        State s = STATES.computeIfAbsent(p.getUUID(), k -> new State());
        ServerLevel l = p.serverLevel();
        int r = ExtraSettings.get(p, MODE, 20, 12, 32, 4);
        int age = ++s.age;

        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 6, 2, true, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 6, 2, true, false, false));

        if (age % 10 == 1) {
            Vec3 up = new Vec3(0, 1, 0);
            PowerNet.circle(l, s.zone, new Vec3(0, 0.06, 0), up, r * 0.9F, MindKit.GOLD, MindKit.PALE, 14, 0.4F, PowerNet.SIMPLE, p);
            PowerNet.circle(l, s.inner, new Vec3(0, 0.08, 0), up, 2.6F, MindKit.PALE, MindKit.GOLD, 14, -1.6F, PowerNet.RAYS, p);
            FxNet.crown(l, s.crown, p, MindKit.GOLD, 14);
        }
        if (age % 20 == 0) Priv.sound(p, SoundEvents.COMPARATOR_CLICK, p.getEyePosition(), 0.35F, 0.6F);

        if (age % 2 == 0) scan(p, l, s, r, age);
        bulletTime(p, l, r, age);
    }

    private void scan(ServerPlayer p, ServerLevel l, State s, int r, int age) {
        List<LivingEntity> seen = new ArrayList<>(l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r),
                e -> e != p && Rules.isLiving(e) && !Rules.isAlly(p, e) && !MindKit.isBody(e) && e.distanceToSqr(p) <= (double) r * r));
        seen.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));
        if (seen.size() > MAX_SEEN) seen = seen.subList(0, MAX_SEEN);
        Set<Integer> now = new HashSet<>();
        boolean draw = age % 4 == 0;
        for (LivingEntity e : seen) {
            int id = e.getId();
            now.add(id);
            if (Rules.canControl(p, e)) e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 6, 3, true, false, false));
            if (age % 10 == 0 || !s.glowed.contains(id)) Priv.glow(p, e, true);
            if (draw) foresee(p, l, s, e);
        }
        for (int id : s.glowed) {
            if (now.contains(id)) continue;
            Entity x = l.getEntity(id);
            if (x != null) Priv.glow(p, x, false);
            s.last.remove(id);
        }
        s.glowed.clear();
        s.glowed.addAll(now);
    }

    /** Private golden path (where it is heading) and threat thread (who it is after). */
    private void foresee(ServerPlayer p, ServerLevel l, State s, LivingEntity e) {
        Vec3 pos = e.position();
        Vec3 prev = s.last.put(e.getId(), pos);
        if (prev != null) {
            Vec3 v = pos.subtract(prev).multiply(1, 0, 1).scale(0.25);
            double sp = v.length();
            if (sp > 0.012) {
                Vec3 dir = v.normalize();
                double len = Math.min(8, sp * 40);
                Vec3 from = pos.add(0, 0.15, 0);
                ParticleOptions dust = Fx.dust(MindKit.GOLD, 0.9F);
                for (double d = 0.6; d <= len; d += 0.8) dot(p, l, dust, from.add(dir.scale(d)));
                Vec3 tip = from.add(dir.scale(len + 0.4));
                dot(p, l, ParticleTypes.END_ROD, tip);
                dot(p, l, ParticleTypes.END_ROD, tip.subtract(dir.scale(0.3)).add(-dir.z * 0.25, 0, dir.x * 0.25));
                dot(p, l, ParticleTypes.END_ROD, tip.subtract(dir.scale(0.3)).add(dir.z * 0.25, 0, -dir.x * 0.25));
            }
        }
        if (e instanceof Mob m && m.getTarget() != null && m.getTarget().isAlive()) {
            Vec3 a = e.getEyePosition(), b = m.getTarget().getEyePosition();
            ParticleOptions red = Fx.dust(THREAT, 1.0F);
            int n = Math.min(24, (int) (a.distanceTo(b) / 1.1));
            for (int i = 1; i <= n; i++) dot(p, l, red, a.lerp(b, i / (double) (n + 1)));
        }
    }

    private static void dot(ServerPlayer p, ServerLevel l, ParticleOptions o, Vec3 v) {
        l.sendParticles(p, o, true, v.x, v.y, v.z, 1, 0, 0, 0, 0);
    }

    private void bulletTime(ServerPlayer p, ServerLevel l, int r, int age) {
        var shots = l.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(r), pr -> {
            Entity owner = pr.getOwner();
            return owner != p && (owner == null || !Rules.isAlly(p, owner)) && pr.getDeltaMovement().lengthSqr() > 0.01 && pr.distanceToSqr(p) <= (double) r * r;
        });
        int n = 0;
        for (Projectile pr : shots) {
            if (++n > MAX_SHOTS) break;
            Vec3 v = pr.getDeltaMovement();
            double sp = v.length();
            if (sp > 0.4) { pr.setDeltaMovement(v.scale(0.4 / sp)); pr.hurtMarked = true; }
            if (age % 2 == 0) l.sendParticles(Fx.light(Stone.MIND, 0.7F), pr.getX(), pr.getY(), pr.getZ(), 1, 0.03, 0.03, 0.03, 0);
        }
    }

    @Override
    public void stop(ServerPlayer p) {
        State s = STATES.remove(p.getUUID());
        ServerLevel l = p.serverLevel();
        if (s != null) {
            for (int id : s.glowed) {
                Entity x = l.getEntity(id);
                if (x != null) Priv.glow(p, x, false);
            }
            PowerNet.kill(l, p.position(), s.zone);
            PowerNet.kill(l, p.position(), s.inner);
            FxNet.kill(l, p.position(), s.crown);
        }
        if (p.isAlive() && !p.hasDisconnected()) {
            FxNet.flash(p, MindKit.GOLD, 0.25F, 10);
            FxNet.shockwave(l, p.position().add(0, 0.1, 0), 6F, MindKit.PALE, 16);
            Priv.sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, p.getEyePosition(), 0.5F, 0.4F);
            Fx.bar(p, Stone.MIND, "infinity.extra.mind.palace.off");
        }
    }
}
