package com.infinitystones.tensura.skill.ability.extra.v2a;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.ExtraSettings;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Ground;
import com.infinitystones.tensura.skill.ability.extra.Move;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import com.infinitystones.tensura.skill.ability.extra.power.PowerNet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hand of the Creator (complete Infinity Gauntlet, strong). Left (aim): a colossal golden gauntlet with the six stones
 * comes down out of the sky, opens its fingers over the point (the stones light one after another) and closes its fist over
 * every living being in the radius, players included. The fist rises with them and holds them up in the sky: they are
 * carried inside it and cannot get away. Right: squeeze (four strong damage pulses; claims still block damage to
 * players); a second right click opens the hand and lets everyone down gently (slow falling). Scroll: radius 4-16.
 * Everyone is released when the caster logs out, dies, changes dimension or no longer carries the whole gauntlet, and put
 * on the ground if the server stops. Captives are held by velocity and position only (no entity data is changed), so
 * nothing persists if the game crashes.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class CreatorHand implements Ability {
    public static final CreatorHand INSTANCE = new CreatorHand();
    static final String MODE = "power.creator_hand";
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();
    private static final Map<UUID, Session> HELD = new HashMap<>();

    private static final int HOLD_CAP = 600;

    @Override public Stone stone() { return Stone.POWER; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        Session s = ACTIVE.get(p.getUUID());
        if (s != null) {
            if (!alt) return Fx.fail(p, "infinity.v2a.creator.busy");
            s.rightClick();
            return true;
        }
        if (alt) return Fx.fail(p, "infinity.v2a.creator.none");
        ServerLevel l = p.serverLevel();
        int radius = ExtraSettings.get(p, MODE, 8, 4, 16, 1);
        Vec3 aim = Aim.point(p, 48);
        Vec3 ground = Ground.below(l, aim.add(0, 0.3, 0), 40);
        if (!l.hasChunkAt(BlockPos.containing(ground))) return Fx.fail(p, "infinity.v2a.far");
        Session ns = new Session(p, l, ground, radius);
        ACTIVE.put(p.getUUID(), ns);
        Tasks.add(ns);
        return true;
    }

    private static boolean holdsAll(ServerPlayer p) {
        for (Stone s : Stone.values()) if (!s.isHeldBy(p)) return false;
        return true;
    }

    private static final class Cap {
        final Entity e; final Vec3 off;
        Cap(Entity e, Vec3 off) { this.e = e; this.off = off; }
    }

    private static final class Session implements Tasks.Task {
        final ServerPlayer p; final ServerLevel l; final Vec3 ground; final int radius;
        final int id = V2aNet.newId();
        final float s;
        final float yaw;
        final double yC, hoverY, riseY;
        final List<Cap> caps = new ArrayList<>();
        int state = -1, moveStart, dur = 1, age, squeezeStart, holdStart, relStart;
        double y0, y1;
        boolean releasing, over;

        Session(ServerPlayer p, ServerLevel l, Vec3 ground, int radius) {
            this.p = p; this.l = l; this.ground = ground; this.radius = radius;
            this.s = Mth.clamp(radius / 2.2F, 1.6F, 6F);
            double dx = p.getX() - ground.x, dz = p.getZ() - ground.z;
            float toCaster = dx * dx + dz * dz < 1 ? (float) Math.toRadians(-p.getYRot() + 180) : (float) Math.atan2(dx, dz);
            this.yaw = toCaster + (float) Math.PI;
            this.yC = Math.min(l.getMaxBuildHeight() - 6, ground.y + 76 + 2 * s);
            this.hoverY = ground.y + 2.7 * s + 0.3;
            this.riseY = Math.min(yC - 14, ground.y + 26 + 1.5 * s);
        }

        double yb() { return V2aAnim.lerp(y0, y1, V2aAnim.ease((age - moveStart) / (float) Math.max(1, dur))); }

        void setState(int st, double toY, int ticks, int life) {
            double cur = state < 0 ? yC : yb();
            y0 = cur; y1 = toY; moveStart = age; dur = ticks; state = st;
            V2aNet.send(l, ground, 260, V2aNet.msg(V2aNet.GAUNTLET, id, st, life, s, ground, yaw, y0, y1, ticks, radius));
        }

        Vec3 centre() {
            double yb = yb();
            return new Vec3(ground.x + Math.sin(yaw) * 0.85 * s, yb + 1.0 * s, ground.z + Math.cos(yaw) * 0.85 * s);
        }

        void rightClick() {
            if (releasing) return;
            if (state == V2aAnim.G_HOLD) {
                squeezeStart = age;
                setState(V2aAnim.G_SQUEEZE, riseY, 1, 1200);
            } else release();
        }

        @Override
        public boolean tick(int a) {
            age = a;
            if (releasing) {
                if (age % 3 == 0) sparkle(0.6F);
                return age < relStart + V2aAnim.G_RELEASE_TICKS + 6;
            }
            if (!p.isAlive() || p.hasDisconnected() || p.isRemoved() || p.level() != l || !holdsAll(p)) {
                release();
                return true;
            }
            if (age == 0) {
                setState(V2aAnim.G_DESC, hoverY, V2aAnim.G_DESC_TICKS, 1200);
                Vec3 sky = new Vec3(ground.x, yC, ground.z);
                float rc = 4.2F * s;
                Vec3 up = new Vec3(0, 1, 0);
                PowerNet.circle(l, PowerNet.newId(), sky, up, rc, Bits.GOLD, 0xFFF3C4, 74, 2.5F, PowerNet.RAYS);
                PowerNet.circle(l, PowerNet.newId(), sky.add(0, -0.3, 0), up, rc * 0.6F, 0xFFF3C4, Bits.GOLD, 74, -4F, PowerNet.GLYPHS_ONLY);
                Bits.farSound(l, ground, SoundEvents.BEACON_ACTIVATE, 0.6F, 0.5F, 140);
                Bits.farSound(l, ground, SoundEvents.ENDER_DRAGON_GROWL, 0.3F, 0.7F, 140);
            }
            if (age > 0 && age < V2aAnim.G_DESC_TICKS) {
                Vec3 h = new Vec3(ground.x, yb(), ground.z);
                FxNet.pillar(l, 0, h, 0.7F * s * 0.5F, 18F, Bits.GOLD, 3);
                if (age % 2 == 0) l.sendParticles(Fx.dust(Bits.GOLD, 2.6F), h.x, h.y + 2, h.z, 8, s, 2, s, 0);
                if (age == 20) Bits.farSound(l, h, SoundEvents.WIND_CHARGE_BURST, 0.6F, 0.4F, 120);
                if (age == V2aAnim.G_DESC_TICKS - 4) FxNet.shake(l, ground, 50, 2.2F, 14);
            }
            if (age == V2aAnim.G_DESC_TICKS) {
                setState(V2aAnim.G_OPEN, hoverY, V2aAnim.G_OPEN_TICKS, 1200);
                FxNet.shockwave(l, ground.add(0, 0.1, 0), radius * 1.4F, Bits.GOLD, 22);
            }
            int openAge = age - V2aAnim.G_DESC_TICKS;
            if (state == V2aAnim.G_OPEN && openAge >= 0 && openAge % 3 == 0 && openAge / 3 < 6) {
                int i = openAge / 3;
                Fx.sound(l, ground, SoundEvents.NOTE_BLOCK_CHIME.value(), 0.6F, 0.6F + 0.14F * i);
                FxNet.flare(l, centre().add(0, 0.5 * s, 0), s * 1.2F, PowerNet.STONE_COLORS[i], 8);
            }
            int closeAt = V2aAnim.G_DESC_TICKS + V2aAnim.G_OPEN_TICKS;
            if (age == closeAt) {
                capture();
                setState(V2aAnim.G_CLOSE, hoverY, V2aAnim.G_CLOSE_TICKS, 1200);
                Bits.farSound(l, ground, SoundEvents.ANVIL_LAND, 0.6F, 0.5F, 120);
                Bits.farSound(l, ground, SoundEvents.IRON_GOLEM_ATTACK, 0.7F, 0.5F, 120);
                FxNet.shake(l, ground, 50, 2F, 14);
                FxNet.flashAround(l, ground, radius + 6, Bits.GOLD, 0.5F, 10);
            }
            if (age == closeAt + V2aAnim.G_CLOSE_TICKS) setState(V2aAnim.G_RISE, riseY, V2aAnim.G_RISE_TICKS, 1200);
            if (age == closeAt + V2aAnim.G_CLOSE_TICKS + V2aAnim.G_RISE_TICKS) {
                holdStart = age;
                setState(V2aAnim.G_HOLD, riseY, 1, 1200);
            }
            if (state == V2aAnim.G_SQUEEZE) squeeze(age - squeezeStart);
            if (state == V2aAnim.G_HOLD || state == V2aAnim.G_SQUEEZE) {
                if (age % 4 == 0) sparkle(1F);
                if (age - holdStart > HOLD_CAP) { release(); return true; }
            }
            if (age >= closeAt) holdAll(closeAt);
            return true;
        }

        private void sparkle(float k) {
            Vec3 c = centre();
            l.sendParticles(Fx.dust(Bits.GOLD, 2.2F), c.x, c.y, c.z, (int) (6 * k), s * 0.7, s * 0.6, s * 0.7, 0);
            l.sendParticles(ParticleTypes.END_ROD, c.x, c.y - 0.5 * s, c.z, 2, s * 0.5, s * 0.5, s * 0.5, 0.02);
        }

        private void capture() {
            AABB box = new AABB(ground, ground).inflate(radius, radius * 0.8 + 3, radius);
            List<LivingEntity> found = l.getEntitiesOfClass(LivingEntity.class, box, e ->
                    Rules.isLiving(e) && Rules.canControl(p, e) && !HELD.containsKey(e.getUUID())
                            && Math.hypot(e.getX() - ground.x, e.getZ() - ground.z) <= radius);
            int n = Math.min(found.size(), 64);
            for (int i = 0; i < n; i++) {
                LivingEntity e = found.get(i);
                double ang = i * 2.399963, rr = 0.42 * s * Math.sqrt((i + 0.5) / n);
                Vec3 off = new Vec3(Math.cos(ang) * rr, (l.random.nextDouble() - 0.5) * 0.3 * s, Math.sin(ang) * rr);
                e.stopRiding();
                caps.add(new Cap(e, off));
                HELD.put(e.getUUID(), this);
                if (e instanceof ServerPlayer sp) FxNet.flash(sp, Bits.GOLD, 0.7F, 14);
            }
        }

        private void holdAll(int closeAt) {
            Vec3 c = centre();
            boolean closing = age < closeAt + V2aAnim.G_CLOSE_TICKS + 4;
            for (Iterator<Cap> it = caps.iterator(); it.hasNext(); ) {
                Cap cap = it.next();
                Entity e = cap.e;
                if (e.isRemoved() || !e.isAlive() || e.level() != l) { HELD.remove(e.getUUID(), this); it.remove(); continue; }
                Vec3 tc = c.add(cap.off);
                Vec3 feet = tc.subtract(0, e.getBbHeight() * 0.5, 0);
                double d = e.position().distanceTo(feet);
                if (!closing && d > 1.8) Bits.place(e, feet);
                else if (d < 0.35) { Move.set(e, Vec3.ZERO); e.fallDistance = 0; }
                else Move.steer(e, tc, 0.28, 0.9);
                if (e instanceof ServerPlayer sp && age % 20 == 0) FxNet.screen(sp, FxNet.AVATAR_TINT, 30);
            }
        }

        private void squeeze(int t) {
            if (t % 10 != 0 || t > 30) {
                if (t >= 40) { setState(V2aAnim.G_HOLD, riseY, 1, 1200); }
                return;
            }
            Vec3 c = centre();
            FxNet.shake(l, c, 70, 3.2F, 10);
            FxNet.flare(l, c, s * 2.4F, 0xFFFFFF, 8);
            FxNet.shockwave(l, new Vec3(ground.x, c.y, ground.z), s * 3F, Bits.GOLD, 14);
            Bits.farSound(l, c, SoundEvents.ANVIL_LAND, 0.55F, 0.5F + 0.1F * (t / 10), 100);
            Bits.farSound(l, c, SoundEvents.IRON_GOLEM_DAMAGE, 0.7F, 0.5F, 100);
            for (Cap cap : caps) {
                if (cap.e instanceof LivingEntity le && Rules.canHarm(p, le)) Rules.hurt(p, le, 9F);
                if (cap.e instanceof ServerPlayer sp) FxNet.flash(sp, 0xFFFFFF, 0.35F, 5);
            }
        }

        /** Opens the hand and lets everyone down gently. */
        void release() {
            if (releasing) return;
            releasing = true;
            relStart = age;
            for (Cap cap : caps) {
                Entity e = cap.e;
                HELD.remove(e.getUUID(), this);
                e.fallDistance = 0;
                if (e instanceof LivingEntity le && le.isAlive()) le.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 240, 0, true, false, false));
                if (e instanceof ServerPlayer sp) FxNet.screen(sp, FxNet.AVATAR_TINT, 0);
            }
            caps.clear();
            double up = Math.min(l.getMaxBuildHeight() - 6, yC + 4);
            setState(V2aAnim.G_RELEASE, up, V2aAnim.G_RELEASE_TICKS, V2aAnim.G_RELEASE_TICKS + 8);
            Bits.farSound(l, centre(), SoundEvents.BEACON_DEACTIVATE, 0.5F, 0.8F, 120);
        }

        @Override
        public void end() {
            ACTIVE.remove(p.getUUID(), this);
            for (Cap cap : caps) {
                HELD.remove(cap.e.getUUID(), this);
                putDown(cap.e);
            }
            caps.clear();
            V2aNet.kill(l, ground, id);
        }

        void putDown(Entity e) {
            if (e.isRemoved()) return;
            Vec3 g = e.level() instanceof ServerLevel sl ? Ground.below(sl, e.position().add(0, 1, 0), 120) : e.position();
            Bits.place(e, g);
            if (e instanceof ServerPlayer sp) FxNet.screen(sp, FxNet.AVATAR_TINT, 0);
        }

        void forget(Entity e) {
            caps.removeIf(c -> c.e == e);
            HELD.remove(e.getUUID(), this);
        }
    }

    /** A captive who leaves the game is put on the ground first, so the save never holds them in mid-air. */
    @SubscribeEvent
    public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        Session s = HELD.get(sp.getUUID());
        if (s != null) {
            s.forget(sp);
            s.putDown(sp);
        }
        Session own = ACTIVE.get(sp.getUUID());
        if (own != null) own.release();
    }
}
