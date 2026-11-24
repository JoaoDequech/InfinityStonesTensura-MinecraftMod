package com.infinitystones.tensura.skill.ability.extra.v2b;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.ExtraSettings;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Ground;
import com.infinitystones.tensura.skill.ability.extra.Move;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Star Rails (Space, normal). Left: pins a glowing star in the air (up to 5, each one is joined to the previous by a line
 * of light). Right: rides the rails from the nearest star to the end of the line; friends who right-click a rider within
 * 3 blocks are hitched on behind like cars of a train. Right + sneak clears the stars, sneak + left removes the last one.
 * Stars last 5 minutes and vanish on logout, dimension change and server stop. No damage.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class StarRails implements Ability {
    public static final StarRails INSTANCE = new StarRails();
    public static final String MODE = "space.star_rails";
    private static final String K = "infinity.extra.v2b.rails.";
    private static final int MAX_STARS = 5, LIFE = 6000, MAX_CARS = 7;
    private static final double REACH = 40, RIDE_REACH = 96;
    private static final int STAR_COLOR = 0x9CC8FF, LINE_COLOR = 0x7FB2FF;

    @Override public Stone stone() { return Stone.SPACE; }

    private static final class Stars {
        ServerLevel level;
        final List<Vec3> pts = new ArrayList<>();
        int age, ownerId;
    }

    private static final class Saved { boolean mayfly, flying, noGravity; }

    private static final class Train {
        ServerLevel level;
        UUID leader;
        List<Vec3> path = new ArrayList<>();
        double[] cum;
        final List<Double> starArc = new ArrayList<>();
        double s, speed;
        int age, nextStar;
        final List<Vec3> trail = new ArrayList<>();
        final List<UUID> cars = new ArrayList<>();
        Vec3 last = Vec3.ZERO;

        Vec3 at(double d) {
            d = Mth.clamp(d, 0, cum[cum.length - 1]);
            int lo = 0, hi = cum.length - 1;
            while (lo < hi - 1) { int mid = (lo + hi) >>> 1; if (cum[mid] <= d) lo = mid; else hi = mid; }
            double span = cum[hi] - cum[lo];
            double t = span <= 1e-6 ? 0 : (d - cum[lo]) / span;
            return path.get(lo).lerp(path.get(hi), t);
        }
    }

    private static final Map<UUID, Stars> STARS = new HashMap<>();
    private static final Map<UUID, Train> RIDERS = new HashMap<>();
    private static final Map<UUID, Saved> SAVED = new HashMap<>();

    // ------------------------------------------------------------------ press

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        if (!alt) return p.isShiftKeyDown() ? undo(p) : place(p);
        if (RIDERS.containsKey(p.getUUID())) { Train t = RIDERS.get(p.getUUID()); if (t.leader.equals(p.getUUID())) end(t, true); else detach(p, t, true); return true; }
        if (p.isShiftKeyDown()) return clear(p);
        return ride(p);
    }

    private static Stars mine(ServerPlayer p) {
        Stars s = STARS.get(p.getUUID());
        if (s != null && s.level != p.serverLevel()) { drop(p.getUUID(), s, false); s = null; }
        return s;
    }

    private static boolean place(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        BlockHitResult hit = Aim.block(p, REACH);
        Vec3 pos = hit.getType() == HitResult.Type.MISS ? p.getEyePosition().add(p.getLookAngle().scale(REACH * 0.8))
                : hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(0.9));
        if (pos.y < l.getMinBuildHeight() + 1 || pos.y > l.getMaxBuildHeight() - 1) return Fx.fail(p, K + "bounds");
        Stars s = mine(p);
        if (s == null) { s = new Stars(); s.level = l; s.ownerId = p.getId(); STARS.put(p.getUUID(), s); }
        s.ownerId = p.getId();
        s.age = 0;
        if (s.pts.size() >= MAX_STARS) s.pts.remove(0);
        s.pts.add(pos);
        int n = s.pts.size();
        sync(p, s);

        Fx.at(l, ParticleTypes.END_ROD, pos, 24, 0.35, 0.06);
        Fx.burstRing(l, pos, 20, Fx.dust(STAR_COLOR, 1.1F), 0.12);
        FxNet.flare(l, pos, 2.6F, 0xFFFFFF, 14);
        FxNet.runes(l, 0, pos, 1.5F, STAR_COLOR, 26, 2.2F);
        FxNet.shockwave(l, pos, 2.2F, LINE_COLOR, 12);
        Fx.sound(l, pos, SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, 0.8F + 0.15F * n);
        Fx.sound(l, pos, SoundEvents.ENDER_EYE_DEATH, 0.4F, 1.5F);
        if (n > 1) {
            Vec3 prev = s.pts.get(n - 2);
            Fx.line(l, prev, pos, 0.8, Fx.dust(LINE_COLOR, 0.8F));
            Fx.sound(l, prev.lerp(pos, 0.5), SoundEvents.BEACON_POWER_SELECT, 0.25F, 1.8F);
        }
        Fx.bar(p, Stone.SPACE, K + "placed", n, MAX_STARS);
        return true;
    }

    private static boolean undo(ServerPlayer p) {
        Stars s = mine(p);
        if (s == null || s.pts.isEmpty()) return Fx.fail(p, K + "none");
        Vec3 gone = s.pts.remove(s.pts.size() - 1);
        Fx.at(s.level, ParticleTypes.END_ROD, gone, 14, 0.3, 0.1);
        Fx.sound(s.level, gone, SoundEvents.AMETHYST_BLOCK_BREAK, 0.5F, 1.4F);
        if (s.pts.isEmpty()) { drop(p.getUUID(), s, true); return true; }
        sync(p, s);
        return true;
    }

    private static boolean clear(ServerPlayer p) {
        Stars s = mine(p);
        if (s == null) return Fx.fail(p, K + "none");
        for (Vec3 v : s.pts) {
            Fx.at(s.level, ParticleTypes.END_ROD, v, 14, 0.3, 0.1);
            FxNet.flare(s.level, v, 1.8F, 0xFFFFFF, 8);
        }
        Fx.sound(s.level, p.position(), SoundEvents.AMETHYST_CLUSTER_BREAK, 0.6F, 1.2F);
        drop(p.getUUID(), s, true);
        Fx.bar(p, Stone.SPACE, K + "cleared");
        return true;
    }

    private static void drop(UUID owner, Stars s, boolean notify) {
        STARS.remove(owner);
        if (!s.level.players().isEmpty()) {
            V2BNet.Msg m = V2BNet.msg(V2BNet.STARS_CLEAR, s.ownerId, 0, 0, 0);
            for (ServerPlayer sp : s.level.players()) V2BNet.to(sp, m);
        }
    }

    private static void sync(ServerPlayer owner, Stars s) {
        boolean riding = RIDERS.containsKey(owner.getUUID());
        V2BNet.Msg m = V2BNet.msg(V2BNet.STARS, s.ownerId, STAR_COLOR, 120, riding ? 1 : 0, pointsOf(s.pts));
        for (ServerPlayer sp : s.level.players()) {
            boolean near = sp.position().distanceToSqr(owner.position()) < 192 * 192;
            if (!near) for (Vec3 v : s.pts) if (sp.position().distanceToSqr(v) < 192 * 192) { near = true; break; }
            if (near) V2BNet.to(sp, m);
        }
    }

    private static double[] pointsOf(List<Vec3> l) { return V2BNet.pts(l.toArray(new Vec3[0])); }

    // ------------------------------------------------------------------ ride

    private static boolean ride(ServerPlayer p) {
        Stars s = mine(p);
        if (s == null || s.pts.isEmpty()) return Fx.fail(p, K + "none");
        if (p.isSpectator() || p.isSleeping() || !p.isAlive()) return false;
        Vec3 from = p.getBoundingBox().getCenter();
        int near = 0;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < s.pts.size(); i++) {
            double d = s.pts.get(i).distanceToSqr(from);
            if (d < best) { best = d; near = i; }
        }
        if (best > RIDE_REACH * RIDE_REACH) return Fx.fail(p, K + "far", (int) RIDE_REACH);
        int n = s.pts.size();
        boolean forward = near <= n - 1 - near;
        List<Vec3> nodes = new ArrayList<>();
        nodes.add(from);
        if (forward) for (int i = near; i < n; i++) nodes.add(s.pts.get(i));
        else for (int i = near; i >= 0; i--) nodes.add(s.pts.get(i));

        Train t = new Train();
        t.level = p.serverLevel();
        t.leader = p.getUUID();
        build(t, nodes);
        int lvl = ExtraSettings.get(p, MODE, 3, 1, 5, 1);
        t.speed = 0.7 + 0.45 * lvl;
        t.last = from;
        if (p.isPassenger()) p.stopRiding();
        board(p, t);
        Vec3 c = p.position();
        FxNet.flash(p, STAR_COLOR, 0.4F, 10);
        FxNet.shockwave(t.level, c.add(0, 0.1, 0), 4F, STAR_COLOR, 16);
        FxNet.flare(t.level, c.add(0, 1, 0), 3F, 0xFFFFFF, 10);
        Fx.burstRing(t.level, c.add(0, 1, 0), 24, Fx.dust(STAR_COLOR, 1.2F), 0.3);
        Fx.sound(t.level, c, SoundEvents.BREEZE_WIND_CHARGE_BURST, 0.8F, 1.3F);
        Fx.sound(t.level, c, SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.5F, 1.6F);
        sync(p, s);
        return true;
    }

    private static void build(Train t, List<Vec3> nodes) {
        List<Vec3> out = new ArrayList<>();
        int segs = nodes.size() - 1;
        for (int i = 0; i < segs; i++) {
            Vec3 p0 = nodes.get(Math.max(i - 1, 0)), p1 = nodes.get(i), p2 = nodes.get(i + 1), p3 = nodes.get(Math.min(i + 2, segs));
            int steps = Math.max(2, (int) Math.ceil(p1.distanceTo(p2) / 0.5));
            for (int k = 0; k < steps; k++) {
                double u = (double) k / steps, u2 = u * u, u3 = u2 * u;
                out.add(new Vec3(cr(p0.x, p1.x, p2.x, p3.x, u, u2, u3), cr(p0.y, p1.y, p2.y, p3.y, u, u2, u3), cr(p0.z, p1.z, p2.z, p3.z, u, u2, u3)));
            }
            t.starArc.add((double) out.size()); // sample index of the next star, converted to arc length below
        }
        out.add(nodes.get(nodes.size() - 1));
        t.path = out;
        t.cum = new double[out.size()];
        for (int i = 1; i < out.size(); i++) t.cum[i] = t.cum[i - 1] + out.get(i).distanceTo(out.get(i - 1));
        for (int i = 0; i < t.starArc.size(); i++) {
            int idx = (int) Math.min(t.starArc.get(i), t.cum.length - 1);
            t.starArc.set(i, t.cum[idx]);
        }
    }

    private static double cr(double a, double b, double c, double d, double u, double u2, double u3) {
        return 0.5 * ((2 * b) + (-a + c) * u + (2 * a - 5 * b + 4 * c - d) * u2 + (-a + 3 * b - 3 * c + d) * u3);
    }

    private static void board(ServerPlayer p, Train t) {
        Saved sv = new Saved();
        sv.mayfly = p.getAbilities().mayfly;
        sv.flying = p.getAbilities().flying;
        sv.noGravity = p.isNoGravity();
        SAVED.put(p.getUUID(), sv);
        RIDERS.put(p.getUUID(), t);
        if (!t.leader.equals(p.getUUID())) t.cars.add(p.getUUID());
        p.getAbilities().mayfly = true;
        p.getAbilities().flying = true;
        p.onUpdateAbilities();
        p.setNoGravity(true);
        p.fallDistance = 0;
    }

    private static void detach(ServerPlayer p, Train t, boolean fx) {
        Saved sv = SAVED.remove(p.getUUID());
        RIDERS.remove(p.getUUID());
        t.cars.remove(p.getUUID());
        if (sv == null) return;
        boolean gmFly = p.gameMode.getGameModeForPlayer() == GameType.CREATIVE || p.isSpectator();
        p.getAbilities().mayfly = sv.mayfly || gmFly;
        p.getAbilities().flying = sv.flying && p.getAbilities().mayfly;
        p.onUpdateAbilities();
        p.setNoGravity(sv.noGravity);
        p.noPhysics = p.isSpectator();
        p.fallDistance = 0;
        V2BNet.to(p, V2BNet.msg(V2BNet.RIDE, 0, 0, 0, 0));
        if (p.serverLevel() == t.level && p.isAlive() && !t.level.noCollision(p, p.getBoundingBox())) {
            for (int dy = 1; dy <= 48; dy++) {
                Vec3 v = p.position().add(0, dy, 0);
                if (Ground.fits(t.level, p, v)) { p.teleportTo(t.level, v.x, v.y, v.z, p.getYRot(), p.getXRot()); break; }
            }
        }
        if (fx) {
            Vec3 dir = t.last.lengthSqr() == 0 ? Vec3.ZERO : p.getDeltaMovement();
            Move.set(p, dir.scale(0.5));
            if (!p.onGround()) p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 120, 0, false, false, true));
            Vec3 c = p.position().add(0, 1, 0);
            Fx.burstRing(t.level, c, 18, Fx.dust(STAR_COLOR, 1F), 0.2);
            FxNet.flare(t.level, c, 2F, 0xFFFFFF, 8);
            Fx.sound(t.level, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.5F, 1.5F);
        }
    }

    private static void end(Train t, boolean fx) {
        List<UUID> all = new ArrayList<>(t.cars);
        all.add(0, t.leader);
        for (UUID id : all) {
            ServerPlayer sp = t.level.getServer().getPlayerList().getPlayer(id);
            if (sp != null) detach(sp, t, fx);
            else { SAVED.remove(id); RIDERS.remove(id); }
        }
        RIDERS.values().removeIf(x -> x == t);
        ServerPlayer lead = t.level.getServer().getPlayerList().getPlayer(t.leader);
        Stars s = STARS.get(t.leader);
        if (lead != null && s != null) sync(lead, s);
    }

    private static void tickTrain(Train t) {
        ServerPlayer lead = t.level.getServer().getPlayerList().getPlayer(t.leader);
        if (lead == null || !lead.isAlive() || lead.serverLevel() != t.level) { end(t, false); return; }
        t.age++;
        if (t.age > 6 && lead.isShiftKeyDown()) { end(t, true); return; }
        double ramp = Math.min(1, 0.35 + t.age / 12.0);
        t.s += t.speed * ramp;
        boolean done = t.s >= t.cum[t.cum.length - 1];
        Vec3 target = t.at(t.s);
        Move.steer(lead, target, 0.55, t.speed * 2.5 + 1);
        lead.fallDistance = 0;
        Vec3 pos = lead.getBoundingBox().getCenter();
        t.trail.add(pos);
        if (t.trail.size() > 600) t.trail.remove(0);
        t.last = pos;

        List<UUID> cars = new ArrayList<>(t.cars);
        int gap = Math.max(3, (int) Math.round(2.6 / t.speed));
        List<ServerPlayer> riders = new ArrayList<>();
        riders.add(lead);
        for (int i = 0; i < cars.size(); i++) {
            ServerPlayer f = t.level.getServer().getPlayerList().getPlayer(cars.get(i));
            if (f == null || !f.isAlive() || f.serverLevel() != t.level || (t.age > 6 && f.isShiftKeyDown())) {
                if (f != null && f.serverLevel() == t.level) detach(f, t, true);
                else { SAVED.remove(cars.get(i)); RIDERS.remove(cars.get(i)); t.cars.remove(cars.get(i)); }
                continue;
            }
            int idx = t.trail.size() - 1 - (i + 1) * gap;
            Vec3 ft = idx >= 0 ? t.trail.get(idx) : t.path.get(0);
            Move.steer(f, ft, 0.55, t.speed * 3 + 1);
            f.fallDistance = 0;
            riders.add(f);
        }

        for (ServerPlayer r : riders) {
            Vec3 c = r.getBoundingBox().getCenter();
            Fx.at(t.level, ParticleTypes.END_ROD, c, 2, 0.25, 0.01);
            Fx.at(t.level, Fx.dust(STAR_COLOR, 0.9F), c, 3, 0.35, 0);
            if (t.age % 2 == 0) Fx.at(t.level, ParticleTypes.GLOW, c, 1, 0.4, 0.02);
        }
        if (t.age % 6 == 0) {
            Fx.sound(t.level, pos, SoundEvents.PHANTOM_FLAP, 0.3F, 1.6F + t.level.random.nextFloat() * 0.3F);
            Fx.sound(t.level, pos, SoundEvents.ELYTRA_FLYING, 0.25F, 1.3F);
        }
        if (t.age % 5 == 1) {
            double[] ids = new double[riders.size()];
            for (int i = 0; i < ids.length; i++) ids[i] = riders.get(i).getId();
            V2BNet.Msg m = V2BNet.msg(V2BNet.RIDE, lead.getId(), STAR_COLOR, 14, riders.size(), ids);
            for (ServerPlayer sp : t.level.players())
                if (sp.position().distanceToSqr(pos) < 160 * 160) V2BNet.to(sp, m);
        }
        while (t.nextStar < t.starArc.size() && t.s >= t.starArc.get(t.nextStar)) {
            Vec3 at = t.at(t.starArc.get(t.nextStar));
            FxNet.flare(t.level, at, 3.2F, 0xFFFFFF, 10);
            FxNet.shockwave(t.level, at, 3F, STAR_COLOR, 12);
            Fx.burstRing(t.level, at, 20, Fx.dust(STAR_COLOR, 1.1F), 0.2);
            Fx.sound(t.level, at, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 0.9F + 0.12F * t.nextStar);
            t.nextStar++;
        }
        if (done) end(t, true);
    }

    // ------------------------------------------------------------------ events

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post e) {
        if (STARS.isEmpty() && RIDERS.isEmpty()) return;
        for (Train t : new ArrayList<>(new java.util.LinkedHashSet<>(RIDERS.values()))) tickTrain(t);
        for (Map.Entry<UUID, Stars> en : new ArrayList<>(STARS.entrySet())) {
            Stars s = en.getValue();
            ServerPlayer owner = s.level.getServer().getPlayerList().getPlayer(en.getKey());
            if (owner == null || owner.serverLevel() != s.level || ++s.age > LIFE) { drop(en.getKey(), s, false); continue; }
            if (s.age % 40 == 0) sync(owner, s);
            if (s.age % 10 == 0) for (Vec3 v : s.pts) Fx.at(s.level, ParticleTypes.END_ROD, v, 1, 0.3, 0.01);
        }
    }

    /** Riders fly through blocks: set right after the level tick (Player.tick resets it) so the next movement packets are not rolled back. */
    @SubscribeEvent
    public static void levelTick(LevelTickEvent.Post e) {
        if (RIDERS.isEmpty() || !(e.getLevel() instanceof ServerLevel sl)) return;
        for (UUID id : RIDERS.keySet()) {
            net.minecraft.world.entity.player.Player pl = sl.getPlayerByUUID(id);
            if (pl != null) pl.noPhysics = true;
        }
    }

    @SubscribeEvent
    public static void interact(PlayerInteractEvent.EntityInteract e) {
        if (e.getLevel().isClientSide || e.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(e.getEntity() instanceof ServerPlayer f) || !(e.getTarget() instanceof ServerPlayer t)) return;
        Train tr = RIDERS.get(t.getUUID());
        if (tr == null || RIDERS.containsKey(f.getUUID()) || f.isSpectator() || !f.isAlive()) return;
        if (f.serverLevel() != tr.level || f.distanceToSqr(t) > 9 || tr.cars.size() >= MAX_CARS) return;
        if (f.isPassenger()) f.stopRiding();
        board(f, tr);
        Fx.sound(tr.level, f.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.6F, 1.7F);
        FxNet.flare(tr.level, f.position().add(0, 1, 0), 2F, STAR_COLOR, 8);
        e.setCanceled(true);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Train t = RIDERS.get(p.getUUID());
        if (t != null) { if (t.leader.equals(p.getUUID())) end(t, false); else detach(p, t, false); }
        Stars s = STARS.get(p.getUUID());
        if (s != null) drop(p.getUUID(), s, false);
    }

    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Train t = RIDERS.get(p.getUUID());
        if (t != null) { if (t.leader.equals(p.getUUID())) end(t, false); else detach(p, t, false); }
        Stars s = STARS.get(p.getUUID());
        if (s != null && s.level != p.serverLevel()) drop(p.getUUID(), s, false);
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent e) {
        for (Train t : new ArrayList<>(new java.util.LinkedHashSet<>(RIDERS.values()))) end(t, false);
        STARS.clear(); RIDERS.clear(); SAVED.clear();
    }

    private StarRails() {}
}
