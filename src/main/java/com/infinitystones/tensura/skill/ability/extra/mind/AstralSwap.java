package com.infinitystones.tensura.skill.ability.extra.mind;

import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Ground;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import com.infinitystones.tensura.skill.ability.extra.power.PowerNet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Astral Projection Swap (conjunction Mind + Space).
 * Left (aim): you and the aimed creature trade places through a braided gold-and-blue rift. The creature is dazed and
 * every monster that was hunting you now hunts it for 8 s.
 * Right: leave a glowing astral double where you stand (30 s); right again swaps you with it from anywhere in the
 * same dimension.
 */
public final class AstralSwap implements Ability {
    public static final AstralSwap INSTANCE = new AstralSwap();
    private static final int BLUE = 0x2F8CFF, MASK = (1 << Stone.MIND.ordinal()) | (1 << Stone.SPACE.ordinal());
    private static final int DOUBLE_TICKS = 600;

    private static final class Twin {
        final ArmorStand body; final ServerLevel level; final int ring = PowerNet.newId(); Tasks.Handle handle;
        Twin(ArmorStand b, ServerLevel l) { body = b; level = l; }
    }

    private static final Map<UUID, Twin> DOUBLES = new HashMap<>();

    @Override public Stone stone() { return Stone.MIND; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        return alt ? doublePress(p) : swap(p);
    }

    // ------------------------------------------------------------------ left: swap with the aimed creature

    private boolean swap(ServerPlayer p) {
        LivingEntity t = Aim.living(p, 40, e -> Rules.canControl(p, e) && !MindKit.isBody(e) && !com.infinitystones.tensura.skill.ability.extra.space.SpiritualGathering.isTrapped(e));
        if (t == null) return Fx.fail(p, "infinity.extra.mind.no_target");
        ServerLevel l = p.serverLevel();
        Vec3 pa = p.position(), pt = t.position();
        if (!Ground.fits(l, p, pt) || !Ground.fits(l, t, pa)) return Fx.fail(p, "infinity.extra.mind.astral.blocked");
        float py = p.getYRot(), pp = p.getXRot(), ty = t.getYRot(), tp = t.getXRot();

        rift(l, pa.add(0, p.getBbHeight() * 0.5, 0), pt.add(0, t.getBbHeight() * 0.5, 0));
        place(p, pt, ty, tp);
        place(t, pa, py, pp);
        rift(l, pa.add(0, 0.9, 0), pt.add(0, 0.9, 0));

        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 2));
        if (t instanceof ServerPlayer tp2) {
            tp2.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0));
            MindKit.shake(tp2, 1.4F, 14);
            FxNet.flash(tp2, BLUE, 0.4F, 10);
        }
        if (t instanceof Mob tm) tm.setTarget(null);
        redirect(p, l, t);
        FxNet.flash(p, MindKit.GOLD, 0.3F, 8);
        FxNet.shake(l, pa, 18, 1.2F, 10);
        Fx.sound(l, pa, SoundEvents.ENDERMAN_TELEPORT, 0.6F, 0.7F);
        Fx.sound(l, pt, SoundEvents.ENDERMAN_TELEPORT, 0.6F, 1.3F);
        Fx.sound(l, pt, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.5F, 1.2F);
        return true;
    }

    private static void place(LivingEntity e, Vec3 to, float yaw, float pitch) {
        if (e instanceof ServerPlayer sp) {
            sp.connection.teleport(to.x, to.y, to.z, yaw, pitch);
        } else {
            e.moveTo(to.x, to.y, to.z, yaw, pitch);
            if (e instanceof Mob m) m.getNavigation().stop();
        }
        e.fallDistance = 0;
    }

    /** Everything that was hunting the caster now hunts the swapped creature for a while. */
    private static void redirect(ServerPlayer p, ServerLevel l, LivingEntity t) {
        List<Mob> hunters = l.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(32),
                m -> m != t && m.getTarget() == p && Rules.canControl(p, m));
        if (hunters.size() > 24) hunters = hunters.subList(0, 24);
        final List<Mob> hs = hunters;
        for (Mob m : hs) {
            m.setTarget(t);
            FxNet.tether(l, FxNet.newId(), m, t, BLUE, 0.04F, 12);
        }
        Tasks.add(age -> {
            if (!t.isAlive()) return false;
            for (Mob m : hs) if (m.isAlive() && (m.getTarget() == null || m.getTarget() == p)) m.setTarget(t);
            if (age % 20 == 0) {
                Vec3 c = t.getBoundingBox().getCenter();
                l.sendParticles(Fx.dust(MindKit.GOLD, 1.2F), c.x, c.y + t.getBbHeight() * 0.6, c.z, 6, 0.3, 0.2, 0.3, 0);
                l.sendParticles(Fx.dust(BLUE, 1.2F), c.x, c.y + t.getBbHeight() * 0.6, c.z, 6, 0.3, 0.2, 0.3, 0);
            }
            return age < 160;
        });
    }

    /** Braided rift between a and b with a portal disc and layered circles at each end. */
    private static void rift(ServerLevel l, Vec3 a, Vec3 b) {
        PowerNet.braid(l, 0, a, b, 0.22F, MASK, 18);
        for (int i = 0; i < 2; i++) {
            Vec3 c = i == 0 ? a : b, other = i == 0 ? b : a;
            Vec3 n = other.subtract(c);
            n = n.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : n.normalize();
            FxNet.portal(l, 0, c, n, 1.1F, i == 0 ? MindKit.GOLD : BLUE, 22);
            PowerNet.circle(l, 0, c, n, 1.9F, MindKit.GOLD, BLUE, 22, 4F, PowerNet.SIMPLE);
            PowerNet.circle(l, 0, c.add(n.scale(0.15)), n, 1.2F, BLUE, MindKit.PALE, 22, -5F, PowerNet.GLYPHS_ONLY);
            FxNet.flare(l, c, 1.6F, i == 0 ? MindKit.PALE : 0x9CC8FF, 8);
            Fx.at(l, Fx.dust(MindKit.GOLD, 1.3F), c, 14, 0.5, 0.02);
            Fx.at(l, Fx.dust(BLUE, 1.3F), c, 14, 0.5, 0.02);
            Fx.at(l, ParticleTypes.END_ROD, c, 6, 0.4, 0.06);
        }
    }

    // ------------------------------------------------------------------ right: astral Twin

    private boolean doublePress(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        Twin d = DOUBLES.get(p.getUUID());
        if (d == null || d.body.isRemoved()) {
            if (d != null) clear(p.getUUID());
            return leave(p, l);
        }
        if (d.level != l) { clear(p.getUUID()); return leave(p, l); }

        Vec3 dest = d.body.position(), here = p.position();
        float yaw = d.body.getYRot();
        if (!Ground.fits(l, p, dest)) return Fx.fail(p, "infinity.extra.mind.astral.blocked");
        rift(l, here.add(0, 0.9, 0), dest.add(0, 0.9, 0));
        place(p, dest, yaw, p.getXRot());
        clear(p.getUUID());
        FxNet.flash(p, BLUE, 0.35F, 10);
        FxNet.shake(l, dest, 16, 1F, 8);
        Fx.sound(l, here, SoundEvents.ENDERMAN_TELEPORT, 0.6F, 0.8F);
        Fx.sound(l, dest, SoundEvents.ENDERMAN_TELEPORT, 0.6F, 1.2F);
        Fx.bar(p, Stone.MIND, "infinity.extra.mind.astral.swapped");
        return true;
    }

    private boolean leave(ServerPlayer p, ServerLevel l) {
        ArmorStand body = MindKit.body(p, p.position(), p.getYRot(), true, false);
        if (body == null) return Fx.fail(p, "infinity.extra.mind.astral.blocked");
        MindKit.pose(body, 0, 0, 8, -6, -8, 6, 0, 0);
        MindKit.goldGlow(body);
        Twin d = new Twin(body, l);
        DOUBLES.put(p.getUUID(), d);
        Vec3 o = p.position();
        FxNet.runes(l, 0, o.add(0, 0.05, 0), 2.4F, BLUE, 40, 3F);
        PowerNet.circle(l, 0, o.add(0, 0.06, 0), new Vec3(0, 1, 0), 2.8F, MindKit.GOLD, BLUE, 40, 3F, PowerNet.RAYS);
        FxNet.spiral(l, o, 0.9F, 2.6F, BLUE, 26, -1);
        FxNet.flare(l, o.add(0, 1, 0), 1.8F, MindKit.PALE, 8);
        Fx.sound(l, o, SoundEvents.BEACON_POWER_SELECT, 0.5F, 1.6F);
        Fx.bar(p, Stone.MIND, "infinity.extra.mind.astral.left");
        UUID id = p.getUUID();
        d.handle = Tasks.add(new Tasks.Task() {
            @Override public boolean tick(int age) {
                if (DOUBLES.get(id) != d || body.isRemoved() || !p.isAlive() || p.hasDisconnected() || age >= DOUBLE_TICKS) return false;
                if (age % 10 == 0) PowerNet.circle(l, d.ring, new Vec3(0, 0.06, 0), new Vec3(0, 1, 0), 1.5F, BLUE, MindKit.GOLD, 14, 2.5F, PowerNet.SIMPLE, body);
                Vec3 c = body.position();
                l.sendParticles(Fx.dust(BLUE, 1.1F), c.x, c.y + 1.0, c.z, 2, 0.25, 0.7, 0.25, 0);
                if (age % 3 == 0) l.sendParticles(Fx.dust(MindKit.GOLD, 1.0F), c.x, c.y + 1.0, c.z, 1, 0.25, 0.7, 0.25, 0);
                if (age % 8 == 0) l.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 0.2, c.z, 1, 0.2, 0.1, 0.2, 0.03);
                return true;
            }
            @Override public void end() {
                if (DOUBLES.get(id) == d) DOUBLES.remove(id);
                Vec3 c = body.position();
                MindKit.spark(l, c.add(0, 1, 0), 24);
                FxNet.shockwave(l, c.add(0, 0.1, 0), 2.5F, BLUE, 12);
                PowerNet.kill(l, c, d.ring);
                MindKit.discard(body);
            }
        });
        return true;
    }

    private static void clear(UUID id) {
        Twin d = DOUBLES.remove(id);
        if (d != null && d.handle != null) d.handle.cancel();
    }
}
