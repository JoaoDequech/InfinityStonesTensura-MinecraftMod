package com.infinitystones.tensura.will;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.wheel.WheelPayloads;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reality Stone "Matter Manipulation" — grip mode (server side, authoritative, multiplayer, no OP). While it is on:
 * HOLD left to take hold of what you aim at (any entity, any player regardless of PvP, Sable/Aeronautics bodies and
 * constructs); release to let go — it keeps its momentum, so swinging and releasing flings it. Right click while
 * holding locks it in the air; right click on something you locked unlocks it. Scroll = distance, Alt + mouse = turn.
 * A held or locked player cannot walk, jump or fly out — only a teleport breaks the grip. No cooldown, no weight limit.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class WillMode {
    /** Hard light in the Reality Stone's colour. */
    public static final int COLOR = 0xFF3346;
    private static final DustParticleOptions LIGHT = new DustParticleOptions(new Vector3f(1F, 0.2F, 0.27F), 1.0F);
    private static final DustParticleOptions LIGHT_SMALL = new DustParticleOptions(new Vector3f(1F, 0.55F, 0.6F), 0.6F);
    private static final double REACH = 128D;

    /** What is held or locked: an entity (by uuid) or a Sable body (by uuid). */
    static final class Grip {
        @Nullable UUID entity;
        @Nullable UUID body;
        double distance;
        @Nullable Vec3 pinPoint;            // set when locked
        @Nullable Quaterniond pinOri;       // body orientation when locked
        @Nullable Quaterniond relOri;       // body orientation relative to the holder's yaw
        @Nullable Vec3 anchor;              // grabbed point on the body, in its plot space
        @Nullable Vec3 lastSet;             // where we last put a held player (teleport detection)
        @Nullable Vec3 lastTarget, aimVel;  // how fast the aim point moves (fling on release)
        float relYaw;                       // entity yaw relative to holder's yaw
        /** Free 3D turn of a held entity (Alt + mouse), drawn by every client around the entity's centre. */
        final Quaterniond vis = new Quaterniond();
        boolean isBody() { return body != null; }
    }

    private static final Map<UUID, Boolean> ACTIVE = new HashMap<>();
    private static final Map<UUID, Grip> HELD = new HashMap<>();
    private static final Map<UUID, List<Grip>> PINNED = new HashMap<>();
    /** Flung entities checked for impact: entity uuid → ticks left. */
    private static final Map<UUID, int[]> THROWN = new HashMap<>();
    private static boolean beamsDirty;
    private static boolean lastBeamsEmpty;

    // ------------------------------------------------------------ mode

    public static boolean isActive(ServerPlayer p) { return ACTIVE.getOrDefault(p.getUUID(), false); }

    public static void toggle(ServerPlayer p) { setActive(p, !isActive(p)); }

    public static void setActive(ServerPlayer p, boolean on) {
        if (on == isActive(p)) return;
        if (on) ACTIVE.put(p.getUUID(), true);
        else { ACTIVE.remove(p.getUUID()); releaseAll(p); }
        PacketDistributor.sendToPlayer(p, new WheelPayloads.WillState(on));
        p.displayClientMessage(Component.translatable(on ? "infinity.will.on" : "infinity.will.off").withStyle(on ? ChatFormatting.RED : ChatFormatting.GRAY), true);
        sound(p.serverLevel(), p.position(), on ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE, 0.5F, on ? 1.6F : 1.3F);
        ring(p.serverLevel(), p.position().add(0, 0.1, 0), 1.2, 28);
        beamsDirty = true;
    }

    // ------------------------------------------------------------ input (validated)

    /**
     * 0 = left pressed (take hold), 6 = left released (let go, momentum kept), 1 = right (lock what you hold / unlock
     * the locked thing you aim at), 2 = scroll (distance), 3 = middle (unlock everything you locked),
     * 4/5 = turn what you hold (value = yaw / pitch degrees, Alt + mouse).
     */
    public static void input(ServerPlayer p, int action, float value) {
        if (!isActive(p)) return;
        if (!Stone.REALITY.isHeldBy(p)) { setActive(p, false); return; }
        Grip g = HELD.get(p.getUUID());
        switch (action) {
            case 0 -> { if (g == null) grab(p); }
            case 6 -> { if (g != null) letGo(p, g); }
            case 1 -> { if (g != null) pin(p, g); else unpinAimed(p); }
            case 2 -> { if (g != null) g.distance = Mth.clamp(g.distance + value * Math.max(0.5, g.distance * 0.12), 1.5, REACH); }
            case 3 -> releasePins(p);
            case 4, 5 -> { if (g != null) rotate(g, action == 4 ? value : 0, action == 5 ? value : 0, p.getYRot()); }
            default -> { }
        }
    }

    /** Turn the held thing in the holder's frame (yaw around up, pitch around the holder's right axis). */
    private static void rotate(Grip g, float yawDeg, float pitchDeg, float holderYaw) {
        Quaterniond r = new Quaterniond().rotateY(Math.toRadians(-yawDeg)).rotateX(Math.toRadians(pitchDeg));
        if (g.isBody() && g.relOri != null) g.relOri = r.mul(g.relOri);
        else if (!g.isBody()) {
            // same control as bodies: turn in the holder's frame, any axis — mobs, items and players tumble freely
            Quaterniond h = yawQuat(holderYaw);
            Quaterniond world = new Quaterniond(h).mul(r).mul(new Quaterniond(h).conjugate());
            world.mul(g.vis, g.vis);
            g.vis.normalize();
            visDirty = true;
        }
    }

    private static boolean visDirty;

    /** "q:x,y,z,w" when a held entity is turned (carried in the beam's body field, which entities don't use). */
    private static String visTag(Grip g) {
        Quaterniond q = g.vis;
        if (Math.abs(q.w) > 0.9999) return "";
        return String.format(java.util.Locale.ROOT, "q:%.4f,%.4f,%.4f,%.4f", q.x, q.y, q.z, q.w);
    }

    /** Let go while moving: entities keep the velocity the grip gave them; bodies keep their physics momentum. */
    private static void letGo(ServerPlayer p, Grip g) {
        HELD.remove(p.getUUID());
        ServerLevel level = p.serverLevel();
        if (g.isBody()) Bodies.gripStop(level, p.getUUID());
        else {
            Entity e = level.getEntity(g.entity);
            if (e != null) {
                // the swing of your aim carries over: a fast flick at release throws it hard
                if (g.aimVel != null && g.aimVel.length() > e.getDeltaMovement().length()) {
                    Vec3 v = g.aimVel.scale(1.15);
                    e.setDeltaMovement(v.length() > 4.5 ? v.normalize().scale(4.5) : v);
                }
                e.hasImpulse = true;
                if (e instanceof ServerPlayer sp) sp.hurtMarked = true;   // send the fling velocity to that client
                if (e.getDeltaMovement().length() > 0.6) THROWN.put(e.getUUID(), new int[]{60}); // flung: impacts hurt
            }
        }
        Vec3 c = targetCenter(level, g);
        if (c != null) burst(level, c, 12);
        sound(level, p.position(), SoundEvents.BEACON_DEACTIVATE, 0.35F, 1.9F);
        beamsDirty = true;
    }

    private static void unpinAimed(ServerPlayer p) {
        Grip aimed = aim(p);
        List<Grip> pins = PINNED.get(p.getUUID());
        if (aimed == null || pins == null) return;
        for (Iterator<Grip> it = pins.iterator(); it.hasNext(); ) {
            Grip pin = it.next();
            if (!same(pin, aimed)) continue;
            it.remove();
            if (pin.isBody()) Bodies.staffLock(p.serverLevel(), pin.body, false);
            Vec3 c = targetCenter(p.serverLevel(), pin);
            if (c != null) { burst(p.serverLevel(), c, 16); ring(p.serverLevel(), c, 1.3, 24); }
            sound(p.serverLevel(), p.position(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 0.4F, 1.6F);
            beamsDirty = true;
            return;
        }
    }

    private static void releasePins(ServerPlayer p) {
        List<Grip> pins = PINNED.remove(p.getUUID());
        if (pins == null) return;
        for (Grip g : pins) {
            if (g.isBody()) Bodies.staffLock(p.serverLevel(), g.body, false);
            Vec3 c = targetCenter(p.serverLevel(), g);
            if (c != null) burst(p.serverLevel(), c, 14);
        }
        sound(p.serverLevel(), p.position(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 0.45F, 1.4F);
        beamsDirty = true;
    }

    private static void grab(ServerPlayer p) {
        Grip g = aim(p);
        if (g == null) { refuse(p); return; }
        ServerLevel level = p.serverLevel();
        if (g.isBody()) Bodies.staffLock(level, g.body, false); // grabbing a locked body frees it
        unpin(g);
        // whoever else was holding it loses it: the newest Will wins
        for (Iterator<Map.Entry<UUID, Grip>> it = HELD.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Grip> en = it.next();
            if (!same(en.getValue(), g)) continue;
            if (g.isBody()) Bodies.gripStop(level, en.getKey());
            it.remove();
        }
        HELD.put(p.getUUID(), g);
        Vec3 at = targetCenter(level, g);
        if (at != null) { burst(level, at, 24); ring(level, at, 1.0, 24); }
        sound(level, p.position(), SoundEvents.BEACON_POWER_SELECT, 0.45F, 1.9F);
        sound(level, p.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6F, 1.4F);
        beamsDirty = true;
    }

    /** What the player is aiming at (any entity, or a Sable body), as a fresh grip — no side effects. */
    private static @Nullable Grip aim(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        Vec3 end = eye.add(look.scale(REACH));
        // blocks (Sable makes this ray hit physics bodies too; the hit is then in the body's plot) bound the entity search
        BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        Vec3 blockHitWorld = block.getType() == HitResult.Type.MISS ? null : Bodies.projectOut(level, block.getLocation());
        double maxDist = blockHitWorld == null ? REACH : blockHitWorld.distanceTo(eye) + 0.5;
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(level, p, eye, eye.add(look.scale(maxDist)),
                p.getBoundingBox().expandTowards(look.scale(maxDist)).inflate(2), e -> grabbable(p, e), 0.6F);
        Grip g = new Grip();
        if (eh != null) {
            Entity e = eh.getEntity() instanceof EnderDragonPart part ? part.parentMob : eh.getEntity();
            g.entity = e.getUUID();
            g.distance = Math.max(2.5, e.getBoundingBox().getCenter().distanceTo(eye));
            g.relYaw = Mth.wrapDegrees(e.getYRot() - p.getYRot());
        } else if (block.getType() != HitResult.Type.MISS) {
            UUID body = Bodies.at(level, block.getBlockPos());
            if (body == null) return null;
            g.body = body;
            g.anchor = block.getLocation();                       // plot space: the physics anchor
            g.distance = Math.max(2.5, blockHitWorld.distanceTo(eye)); // keep the grabbed point where it is
            Quaterniond ori = Bodies.orientation(level, body);
            g.relOri = ori == null ? new Quaterniond() : yawQuat(p.getYRot()).conjugate().mul(ori);
        } else return null;
        return g;
    }

    private static boolean grabbable(ServerPlayer p, Entity e) {
        // every entity in the game, players included (PvP setting ignored on purpose) — only yourself and spectators excluded
        return e != p && e.isAlive() && !e.isSpectator();
    }

    private static void pin(ServerPlayer p, Grip g) {
        ServerLevel level = p.serverLevel();
        Vec3 c = targetCenter(level, g);
        if (c == null) { release(p); return; }
        g.pinPoint = c;
        if (g.isBody()) {
            g.pinOri = Bodies.orientation(level, g.body);
            Bodies.gripStop(level, p.getUUID());
            Bodies.staffLock(level, g.body, true);   // a real fixed physics constraint (saved with the world)
        } else {
            Entity e = level.getEntity(g.entity);
            if (e != null) e.setDeltaMovement(Vec3.ZERO);
        }
        HELD.remove(p.getUUID());
        PINNED.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).add(g);
        ring(level, c, 1.4, 36);
        sound(level, c, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, 0.45F, 1.7F);
        beamsDirty = true;
    }

    private static void release(ServerPlayer p) {
        Grip g = HELD.remove(p.getUUID());
        if (g != null) {
            if (g.isBody()) Bodies.gripStop(p.serverLevel(), p.getUUID());
            sound(p.serverLevel(), p.position(), SoundEvents.BEACON_DEACTIVATE, 0.35F, 1.8F);
        }
        beamsDirty = true;
    }

    private static void releaseAll(ServerPlayer p) {
        Grip held = HELD.remove(p.getUUID());
        if (held != null && held.isBody()) Bodies.gripStop(p.serverLevel(), p.getUUID());
        List<Grip> pins = PINNED.remove(p.getUUID());
        if (pins != null) for (Grip g : pins) {
            if (g.isBody()) Bodies.staffLock(p.serverLevel(), g.body, false);
            Vec3 c = targetCenter(p.serverLevel(), g); if (c != null) burst(p.serverLevel(), c, 14);
        }
        beamsDirty = true;
    }

    private static void unpin(Grip g) {
        for (List<Grip> list : PINNED.values()) list.removeIf(x -> same(x, g));
    }

    private static boolean same(Grip a, Grip b) {
        return (a.entity != null && a.entity.equals(b.entity)) || (a.body != null && a.body.equals(b.body));
    }

    private static void refuse(ServerPlayer p) {
        sound(p.serverLevel(), p.position(), SoundEvents.NOTE_BLOCK_BASS.value(), 0.3F, 0.6F);
    }

    // ------------------------------------------------------------ tick

    @SubscribeEvent
    public static void playerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !isActive(p)) return;
        ServerLevel level = p.serverLevel();
        if (!Stone.REALITY.isHeldBy(p)) { setActive(p, false); return; }
        Grip g = HELD.get(p.getUUID());
        if (g != null) {
            Vec3 target = p.getEyePosition().add(p.getLookAngle().scale(g.distance));
            Quaterniond ori = g.relOri == null ? null : yawQuat(p.getYRot()).mul(g.relOri);
            boolean ok;
            if (g.isBody()) {
                ok = Bodies.exists(level, g.body);
                if (ok && g.anchor != null && ori != null) Bodies.gripDrag(level, p.getUUID(), g.body, p.getLookAngle().scale(g.distance), g.anchor, ori);
            } else ok = hold(level, g, target, p.getYRot() + g.relYaw);
            if (g.lastTarget != null) g.aimVel = g.aimVel == null ? target.subtract(g.lastTarget) : g.aimVel.scale(0.4).add(target.subtract(g.lastTarget).scale(0.6));
            g.lastTarget = target;
            if (!ok) release(p);
            else if (p.tickCount % 2 == 0) { Vec3 c = targetCenter(level, g); if (c != null) aura(level, c, g); }
        }
        List<Grip> pins = PINNED.get(p.getUUID());
        if (pins != null) for (Iterator<Grip> it = pins.iterator(); it.hasNext(); ) {
            Grip pin = it.next();
            boolean alive;
            if (pin.isBody()) {
                alive = Bodies.exists(level, pin.body);
                if (alive && !Bodies.staff()) Bodies.drive(level, pin.body, pin.pinPoint, pin.pinOri, 7.0); // no Aeronautics: servo it
            } else alive = hold(level, pin, pin.pinPoint, Float.NaN);
            if (!alive) { it.remove(); beamsDirty = true; }
            else if (p.tickCount % 6 == 0) aura(level, pin.isBody() ? targetCenter(level, pin) : pin.pinPoint, pin);
        }
    }

    /** One tick of holding an entity at a point. Returns false when the target is gone (or teleported away). */
    private static boolean hold(ServerLevel level, Grip g, Vec3 target, float yaw) {
        Entity e = level.getEntity(g.entity);
        if (e == null || !e.isAlive()) return false;
        Vec3 c = e.getBoundingBox().getCenter();
        if (e instanceof ServerPlayer sp) {
            // a held / locked player cannot walk, jump or fly out of the grip — only the holder lets go, or a teleport
            if (g.lastSet != null && sp.position().distanceTo(g.lastSet) > 10) return false; // pearl, /home, a power...
            Vec3 step = target.subtract(c);
            if (step.length() > 3.0) step = step.normalize().scale(3.0);
            Vec3 to = sp.position().add(step);
            sp.connection.teleport(to.x, to.y, to.z, sp.getYRot(), sp.getXRot());
            sp.setDeltaMovement(step);
            sp.fallDistance = 0;
            g.lastSet = to;
            THROWN.remove(sp.getUUID());
            return true;
        }
        Vec3 v = target.subtract(c).scale(0.42);
        if (v.length() > 3.2) v = v.normalize().scale(3.2);
        e.setDeltaMovement(v.add(0, 0.08, 0)); // cancel this tick's gravity so it hangs where you aim
        e.hasImpulse = true;
        e.fallDistance = 0;
        if (!Float.isNaN(yaw)) { e.setYRot(yaw); e.setYHeadRot(yaw); }
        if (e instanceof Mob m) m.getNavigation().stop();
        THROWN.remove(e.getUUID());
        return true;
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        // impacts of flung entities: a slam into the ground or a wall hurts and bursts
        for (Iterator<Map.Entry<UUID, int[]>> it = THROWN.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, int[]> en = it.next();
            if (--en.getValue()[0] <= 0) { it.remove(); continue; }
            Entity e = null;
            for (ServerLevel l : event.getServer().getAllLevels()) { e = l.getEntity(en.getKey()); if (e != null) break; }
            if (e == null) { it.remove(); continue; }
            double speed = e.getDeltaMovement().length();
            if ((e.horizontalCollision || e.verticalCollision) && en.getValue()[0] < 58) {
                if (e instanceof LivingEntity le && e.level() instanceof ServerLevel sl) {
                    float dmg = (float) (4 + speed * 9);
                    le.hurt(sl.damageSources().flyIntoWall(), dmg);
                    burst(sl, e.position(), 26);
                    sl.sendParticles(ParticleTypes.EXPLOSION, e.getX(), e.getY() + 0.3, e.getZ(), 1, 0, 0, 0, 0);
                    sound(sl, e.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.4F, 1.6F);
                    sound(sl, e.position(), SoundEvents.ANVIL_LAND, 0.35F, 0.6F);
                }
                it.remove();
            }
        }
        // turning sends at most every 2 ticks (clients smooth it); grabs / locks / releases go out at once
        boolean turn = visDirty && event.getServer().getTickCount() % 2 == 0;
        if (beamsDirty || turn || event.getServer().getTickCount() % 20 == 0) { beamsDirty = false; if (turn) visDirty = false; broadcast(event.getServer()); }
    }

    /**
     * Send every grip to everyone — drawn client-side for all players (multiplayer). Held things travel as a beam
     * (holder id ≥ 0); locked things travel with holder = -(holderId + 1) and get the lock seal instead of a beam.
     */
    private static void broadcast(net.minecraft.server.MinecraftServer server) {
        List<WheelPayloads.Beam> beams = new ArrayList<>();
        for (Map.Entry<UUID, Grip> en : HELD.entrySet()) add(server, beams, en.getKey(), en.getValue(), false);
        for (Map.Entry<UUID, List<Grip>> en : PINNED.entrySet()) for (Grip g : en.getValue()) add(server, beams, en.getKey(), g, true);
        // nobody holds anything and clients already have the empty list: nothing to say
        if (beams.isEmpty() && lastBeamsEmpty) return;
        lastBeamsEmpty = beams.isEmpty();
        PacketDistributor.sendToAllPlayers(new WheelPayloads.WillBeams(beams));
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (lastBeamsEmpty && event.getEntity() instanceof ServerPlayer p) PacketDistributor.sendToPlayer(p, new WheelPayloads.WillBeams(List.of()));
    }

    private static void add(net.minecraft.server.MinecraftServer server, List<WheelPayloads.Beam> out, UUID holderId, Grip g, boolean pinned) {
        ServerPlayer holder = server.getPlayerList().getPlayer(holderId);
        if (holder == null) return;
        int entityId = -1;
        if (!g.isBody()) { Entity e = holder.serverLevel().getEntity(g.entity); if (e == null) return; entityId = e.getId(); }
        out.add(new WheelPayloads.Beam(pinned ? -(holder.getId() + 1) : holder.getId(), entityId, g.isBody() ? g.body.toString() : visTag(g)));
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) releaseAll(p);
        ACTIVE.remove(event.getEntity().getUUID());
        beamsDirty = true;
    }

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        UUID id = event.getEntity().getUUID();
        if (event.getEntity() instanceof ServerPlayer p) Bodies.gripStop(p.serverLevel(), id);
        HELD.remove(id); PINNED.remove(id);
        beamsDirty = true;
    }

    /** Is this entity held or locked by anyone? (used to stop held players from escaping by other means) */
    public static boolean isGripped(Entity e) {
        UUID id = e.getUUID();
        for (Grip g : HELD.values()) if (id.equals(g.entity)) return true;
        for (List<Grip> l : PINNED.values()) for (Grip g : l) if (id.equals(g.entity)) return true;
        return false;
    }

    // ------------------------------------------------------------ helpers

    private static @Nullable Vec3 targetCenter(ServerLevel level, Grip g) {
        if (g.isBody()) return Bodies.center(level, g.body, 1F);
        Entity e = level.getEntity(g.entity);
        return e == null ? null : e.getBoundingBox().getCenter();
    }

    static Quaterniond yawQuat(float yawDeg) { return new Quaterniond().rotateY(Math.toRadians(-yawDeg)); }

    private static void aura(ServerLevel level, @Nullable Vec3 c, Grip g) {
        if (c == null) return;
        double r = g.isBody() ? Math.min(4, Bodies.radius(level, g.body)) : 0.7;
        for (int i = 0; i < (g.isBody() ? 4 : 2); i++) {
            double a = level.random.nextDouble() * Math.PI * 2, b = level.random.nextDouble() * Math.PI - Math.PI / 2;
            level.sendParticles(i % 2 == 0 ? LIGHT : LIGHT_SMALL, c.x + Math.cos(a) * Math.cos(b) * r, c.y + Math.sin(b) * r, c.z + Math.sin(a) * Math.cos(b) * r, 1, 0, 0, 0, 0);
        }
    }

    static void burst(ServerLevel level, Vec3 c, int n) {
        level.sendParticles(LIGHT, c.x, c.y, c.z, n, 0.45, 0.45, 0.45, 0.05);
        level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, n / 4, 0.3, 0.3, 0.3, 0.06);
    }

    static void ring(ServerLevel level, Vec3 c, double r, int n) {
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            level.sendParticles(LIGHT, c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }

    static void sound(ServerLevel level, Vec3 at, SoundEvent s, float vol, float pitch) {
        level.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    private WillMode() {}
}
