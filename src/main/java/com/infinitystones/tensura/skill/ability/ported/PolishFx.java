package com.infinitystones.tensura.skill.ability.ported;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.registry.ModSounds;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.StoneTree;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * "AAA" visual layer for the ORIGINAL ported powers: huge layered magic circles in the stone colour (client/port/
 * MagicCircles through the ported {@link com.infinitystones.tensura.skill.ability.ported.FxNet}), additive glow shapes
 * (domes, portals, tunnels, clocks, spirals, orbs, shockwaves; {@code extra.fx.FxNet}), camera shake, flashes, particles
 * and moderate sounds. Pure decoration: nothing here changes a power's behaviour or numbers, and every entry point
 * swallows its own errors so an effect can never break an ability.
 *
 * <p>{@link com.infinitystones.tensura.skill.ability.PortedActions} calls {@link #press}/{@link #start}/{@link #tick}/
 * {@link #stop} for every node before dispatching it; a few powers call the dedicated methods directly (telekinesis,
 * force field, size, time speed, weather). Long effects use keyed refreshes so the packet rate stays low (about one
 * small packet per tick per active power); the client does all the geometry.</p>
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class PolishFx {
    private static final int T1 = 1, T2 = 2, T3 = 3, RAYS = 4, HEPTA = 8, FAST = 16;
    private static final Vec3 UP = new Vec3(0, 1, 0), DOWN = new Vec3(0, -1, 0);

    // ------------------------------------------------------------------ entry points (called by PortedActions)

    public static void press(ServerPlayer p, StoneTree.Node n, boolean alt) {
        try {
            switch (n.modeId()) {
                case "space.teleport" -> teleportDepart(p);
                case "space.black_hole_on" -> { if (!p.getTags().contains("blackholeon")) holeSearch(p); }
                case "power.meteor_storm" -> stormStart(p);
                case "soul.summon_zombie" -> summonZombie(p);
                case "soul.enter_soulworld" -> { if (!p.getTags().contains("in_soul")) soulDepart(p); }
                case "soul.exit_soulworld" -> { if (p.getTags().contains("in_soul")) soulDepart(p); }
                case "reality.effects" -> realityEffects(p);
                default -> { }
            }
        } catch (RuntimeException ex) { fail(ex); }
    }

    public static void start(ServerPlayer p, StoneTree.Node n) {
        try {
            switch (n.modeId()) {
                case "time.time_freeze" -> freezeStart(p);
                case "time.fast_forward" -> forwardStart(p, n);
                case "time.projectile_stop" -> stopperStart(p);
                case "mind.intangibility" -> phaseStart(p);
                default -> { }
            }
        } catch (RuntimeException ex) { fail(ex); }
    }

    public static void tick(ServerPlayer p, StoneTree.Node n, int ticks) {
        try {
            switch (n.modeId()) {
                case "time.time_freeze" -> freezeTick(p, ticks);
                case "time.fast_forward" -> forwardTick(p, n, ticks);
                case "time.projectile_stop" -> stopperTick(p, ticks);
                case "mind.flight-dummy" -> flightTick(p, ticks);
                default -> { }
            }
        } catch (RuntimeException ex) { fail(ex); }
    }

    public static void stop(ServerPlayer p, StoneTree.Node n) {
        try {
            switch (n.modeId()) {
                case "time.time_freeze" -> freezeStop(p);
                case "time.fast_forward" -> forwardStop(p);
                case "time.projectile_stop" -> stopperStop(p);
                case "mind.flight-dummy" -> FLYERS.remove(p.getUUID());
                default -> { }
            }
        } catch (RuntimeException ex) { fail(ex); }
    }

    private static boolean warned;

    private static void fail(RuntimeException ex) {
        if (warned) return;
        warned = true;
        InfinityStones.LOGGER.warn("Polish effect failed (ignored): {}", ex.toString());
    }

    // ------------------------------------------------------------------ helpers

    private static int c(Stone s) { return s.color() & 0xFFFFFF; }
    private static int l(Stone s) { return com.infinitystones.tensura.skill.ability.ported.FxNet.light(s); }
    private static int key(Entity e, int slot) { return ((e.getId() * 31 + slot * 7919) & 0x3FFFFFFF) | 0x40000000; }
    private static int key(UUID id, int slot) { return ((id.hashCode() * 31 + slot * 7919) & 0x3FFFFFFF) | 0x40000000; }

    /** Magic circle at a fixed point. */
    private static void at(ServerLevel lv, Vec3 pos, Vec3 normal, float r, Stone s, int dur, int style) {
        com.infinitystones.tensura.skill.ability.ported.FxNet.circle(lv, pos, normal, r, s, dur, style);
    }

    /** Flat circle under an entity that follows it; the same slot refreshed again keeps it alive without re-fading. */
    private static void under(Entity e, int slot, double y, float r, Stone s, int dur, int style) {
        com.infinitystones.tensura.skill.ability.ported.FxNet.circleKeyed(e, key(e, slot), new Vec3(0, y, 0), UP, r, c(s), l(s), dur, style);
    }

    /** Circle floating above an entity, face down, following it (sky seals). */
    private static void over(Entity e, int slot, double h, float r, Stone s, int dur, int style) {
        com.infinitystones.tensura.skill.ability.ported.FxNet.circleKeyed(e, key(e, slot), new Vec3(0, h, 0), DOWN, r, c(s), l(s), dur, style);
    }

    private static Vec3 flat(Vec3 look) {
        Vec3 f = new Vec3(look.x, 0, look.z);
        return f.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : f.normalize();
    }

    private static Vec3 mid(Entity e) { return e.position().add(0, e.getBbHeight() * 0.5, 0); }

    private static void dustRing(ServerLevel lv, Vec3 c, double radius, int points, Stone s, float size, double phase) {
        PortFx.ring(lv, c, radius, points, PortFx.dust(s, size), phase);
    }

    // ------------------------------------------------------------------ Space: teleport

    private record Pending(Vec3 from, ResourceKey<Level> dim, int at, Stone stone) {}
    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private static void teleportDepart(ServerPlayer p) {
        ServerLevel lv = p.serverLevel();
        Vec3 pos = p.position(), m = mid(p), look = flat(p.getLookAngle());
        int col = c(Stone.SPACE);
        at(lv, pos.add(0, 0.05, 0), UP, 3.4F, Stone.SPACE, 30, T2 | FAST | RAYS);
        at(lv, m.add(look.scale(0.4)), look, 2.1F, Stone.SPACE, 24, T1 | FAST);
        FxNet.portal(lv, 0, m.add(look.scale(0.5)), look, 1.5F, col, 22);
        FxNet.spiral(lv, pos, 1.3F, 2.8F, col, 20, -1);
        FxNet.shockwave(lv, pos, 4.5F, col, 16);
        lv.sendParticles(ParticleTypes.REVERSE_PORTAL, m.x, m.y, m.z, 50, 0.4, 0.8, 0.4, 0.15);
        lv.sendParticles(ParticleTypes.END_ROD, m.x, m.y, m.z, 14, 0.5, 0.9, 0.5, 0.05);
        PortFx.sound(lv, pos, SoundEvents.ENDERMAN_TELEPORT, 0.5F, 1.4F);
        PortFx.sound(lv, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.5F, 0.7F);
        FxNet.flash(p, col, 0.3F, 8);
        PENDING.put(p.getUUID(), new Pending(pos, lv.dimension(), p.tickCount, Stone.SPACE));
    }

    /** Wheel destination teleport (middle click): the player is about to move. */
    public static void destinationDepart(ServerPlayer p) {
        try {
            ServerLevel lv = p.serverLevel();
            Vec3 pos = p.position();
            teleportDepart(p);
            PENDING.remove(p.getUUID());
            lv.sendParticles(PortFx.dust(Stone.SPACE, 1.4F), pos.x, pos.y + 1, pos.z, 40, 0.5, 0.9, 0.5, 0);
        } catch (RuntimeException ex) { fail(ex); }
    }

    /** The player has landed (wheel destination teleport). */
    public static void destinationArrive(ServerPlayer p, Vec3 from, boolean sameDim) {
        try { arrive(p, from, sameDim, Stone.SPACE); } catch (RuntimeException ex) { fail(ex); }
    }

    private static void arrive(ServerPlayer p, Vec3 from, boolean sameDim, Stone st) {
        ServerLevel lv = p.serverLevel();
        Vec3 to = p.position(), m = mid(p), look = flat(p.getLookAngle());
        int col = c(st);
        at(lv, to.add(0, 0.05, 0), UP, st == Stone.SOUL ? 5.2F : 4.4F, st, 40, T3 | RAYS | FAST);
        at(lv, m, look, 2.4F, st, 26, T2 | HEPTA);
        FxNet.pillar(lv, 0, to, 0.9F, 7F, col, 18);
        FxNet.shockwave(lv, to, 8F, col, 22);
        FxNet.flare(lv, m, 4.5F, col, 14);
        if (st == Stone.SPACE) {
            FxNet.portal(lv, 0, m, look, 1.8F, col, 24);
            if (sameDim && from.distanceToSqr(to) < 140 * 140) {
                Vec3 a = from.add(0, 1, 0);
                FxNet.tunnel(lv, a, m, 1.1F, col, 14);
                if (a.distanceTo(m) < 60) PortFx.line(lv, a, m, ParticleTypes.END_ROD, 0.6);
            }
            lv.sendParticles(ParticleTypes.REVERSE_PORTAL, m.x, m.y, m.z, 60, 0.5, 0.9, 0.5, 0.2);
            lv.sendParticles(ParticleTypes.END_ROD, m.x, m.y, m.z, 20, 0.6, 1.0, 0.6, 0.08);
            PortFx.sound(lv, to, ModSounds.SPACE_STONE_PORTAL, 0.55F, 1.3F);
            PortFx.sound(lv, to, SoundEvents.ENDERMAN_TELEPORT, 0.45F, 1.1F);
        } else {
            FxNet.spiral(lv, to, 2.4F, 5F, col, 30, p.getId());
            soulParticles(lv, to, 60);
            PortFx.sound(lv, to, ModSounds.SOUL_STONE, 0.6F, 1.1F);
            PortFx.sound(lv, to, SoundEvents.SOUL_ESCAPE, 0.5F, 0.9F);
        }
        FxNet.shake(lv, to, 18, 0.6F, 8);
        FxNet.flash(p, col, 0.45F, 10);
    }

    // ------------------------------------------------------------------ Space: telekinesis (called from Telekinesis)

    public static void grabStart(ServerPlayer p, Entity t) {
        try {
            ServerLevel lv = p.serverLevel();
            int col = c(Stone.SPACE);
            Vec3 tp = t.position();
            FxNet.shockwave(lv, tp, 3.2F + t.getBbWidth(), col, 14);
            FxNet.flare(lv, mid(t), 2.4F, col, 10);
            under(t, 20, 0.05, 1.3F + t.getBbWidth(), Stone.SPACE, 30, T2 | FAST);
            lv.sendParticles(ParticleTypes.END_ROD, t.getX(), t.getY(0.5), t.getZ(), 18, t.getBbWidth() * 0.5, t.getBbHeight() * 0.4, t.getBbWidth() * 0.5, 0.05);
        } catch (RuntimeException ex) { fail(ex); }
    }

    public static void grabHold(ServerPlayer p, Entity t, int ticks) {
        try {
            ServerLevel lv = p.serverLevel();
            int col = c(Stone.SPACE), col2 = l(Stone.SPACE);
            if (ticks % 3 == 0) {
                FxNet.tether(lv, key(p, 10), p, t, col, 0.07F, 7);
                FxNet.tether(lv, key(p, 11), p, t, col2, 0.03F, 7);
            }
            if (ticks % 3 == 1) {
                Vec3 hand = p.getLookAngle().scale(0.9).add(0, p.getEyeHeight() * 0.85, 0);
                FxNet.orb(lv, key(p, 12), p.position(), 0.26F, col2, 7, p.getId(), hand);
            }
            if (ticks % 20 == 0) under(t, 20, 0.05, 1.3F + t.getBbWidth(), Stone.SPACE, 30, T2 | FAST);
            if (ticks % 6 == 0) lv.sendParticles(ParticleTypes.END_ROD, t.getX(), t.getY(0.5), t.getZ(), 2, t.getBbWidth() * 0.5, t.getBbHeight() * 0.5, t.getBbWidth() * 0.5, 0.01);
        } catch (RuntimeException ex) { fail(ex); }
    }

    public static void grabEnd(ServerPlayer p, Entity t, boolean thrown) {
        try {
            ServerLevel lv = p.serverLevel();
            int col = c(Stone.SPACE);
            FxNet.kill(lv, t.position(), key(p, 10));
            FxNet.kill(lv, t.position(), key(p, 11));
            FxNet.kill(lv, t.position(), key(p, 12));
            if (thrown) {
                Vec3 a = mid(t);
                FxNet.beam(lv, a, a.add(p.getLookAngle().scale(10)), col, 0.2F, 10);
                FxNet.shockwave(lv, t.position(), 5F, col, 14);
                FxNet.flare(lv, a, 3.5F, l(Stone.SPACE), 10);
                at(lv, a, p.getLookAngle(), 1.9F, Stone.SPACE, 16, T1 | FAST);
            } else {
                FxNet.shockwave(lv, t.position(), 2.6F, col, 10);
            }
        } catch (RuntimeException ex) { fail(ex); }
    }

    // ------------------------------------------------------------------ Space: force field (called from AttributeModifier)

    public static void fieldStart(ServerPlayer p) {
        try {
            ServerLevel lv = p.serverLevel();
            Vec3 m = mid(p), pos = p.position();
            int col = c(Stone.SPACE);
            FxNet.dome(lv, key(p, 1), m, 2.1F, col, 30);
            under(p, 2, 0.05, 2.7F, Stone.SPACE, 40, T3 | HEPTA | FAST);
            FxNet.shockwave(lv, pos, 7F, col, 18);
            FxNet.flare(lv, m, 4F, l(Stone.SPACE), 12);
            FxNet.flash(p, col, 0.2F, 8);
            FxNet.shake(lv, pos, 10, 0.35F, 6);
            lv.sendParticles(ParticleTypes.END_ROD, m.x, m.y, m.z, 30, 1.2, 1.2, 1.2, 0.08);
        } catch (RuntimeException ex) { fail(ex); }
    }

    public static void fieldTick(ServerPlayer p, int ticks) {
        if (ticks <= 0) return;
        try {
            if (ticks % 12 != 0) return;
            ServerLevel lv = p.serverLevel();
            FxNet.dome(lv, key(p, 1), mid(p), 2.1F, c(Stone.SPACE), 26);
            under(p, 2, 0.05, 2.7F, Stone.SPACE, 40, T3 | HEPTA | FAST);
            if (ticks % 48 == 0) FxNet.shockwave(lv, p.position(), 3.2F, l(Stone.SPACE), 12);
        } catch (RuntimeException ex) { fail(ex); }
    }

    public static void fieldStop(ServerPlayer p) {
        try {
            ServerLevel lv = p.serverLevel();
            Vec3 m = mid(p);
            FxNet.kill(lv, m, key(p, 1));
            FxNet.shockwave(lv, p.position(), 5.5F, c(Stone.SPACE), 14);
            lv.sendParticles(ParticleTypes.END_ROD, m.x, m.y, m.z, 24, 1.3, 1.3, 1.3, 0.12);
        } catch (RuntimeException ex) { fail(ex); }
    }

    // ------------------------------------------------------------------ Space: black hole

    private static final class Hole {
        final UUID stand; final ResourceKey<Level> dim; final long born; Vec3 center; final int id;
        Hole(UUID stand, int id, ResourceKey<Level> dim, Vec3 center, long born) { this.stand = stand; this.id = id; this.dim = dim; this.center = center; this.born = born; }
    }
    private record Search(UUID player, ResourceKey<Level> dim, Vec3 pos, long expire) {}
    private static final List<Hole> HOLES = new ArrayList<>();
    private static final List<Search> SEARCHES = new ArrayList<>();
    /** The black hole item floats at the armour stand's head. */
    private static final double HOLE_Y = 1.6;

    private static void holeSearch(ServerPlayer p) {
        SEARCHES.add(new Search(p.getUUID(), p.serverLevel().dimension(), p.position(), p.server.getTickCount() + 10));
    }

    private static void holeTick(MinecraftServer s, long now) {
        for (Iterator<Search> it = SEARCHES.iterator(); it.hasNext(); ) {
            Search q = it.next();
            ServerLevel lv = s.getLevel(q.dim());
            if (lv == null || now > q.expire()) { it.remove(); continue; }
            Set<UUID> known = new HashSet<>();
            for (Hole h : HOLES) known.add(h.stand);
            List<ArmorStand> found = lv.getEntitiesOfClass(ArmorStand.class, new AABB(q.pos(), q.pos()).inflate(4),
                    a -> a.getTags().contains("blackhole") && !known.contains(a.getUUID()));
            if (found.isEmpty()) continue;
            ArmorStand a = found.get(0);
            Hole h = new Hole(a.getUUID(), a.getId(), q.dim(), a.position().add(0, HOLE_Y, 0), now);
            HOLES.add(h);
            holeBirth(lv, h);
            it.remove();
        }
        for (Iterator<Hole> it = HOLES.iterator(); it.hasNext(); ) {
            Hole h = it.next();
            ServerLevel lv = s.getLevel(h.dim);
            if (lv == null) { it.remove(); continue; }
            Entity en = lv.getEntity(h.stand);
            if (en == null || !en.isAlive()) {
                if (lv.isLoaded(net.minecraft.core.BlockPos.containing(h.center))) holeCollapse(lv, h);
                it.remove();
                continue;
            }
            h.center = en.position().add(0, HOLE_Y, 0);
            holeLive(lv, en, h, now - h.born);
        }
    }

    private static void holeBirth(ServerLevel lv, Hole h) {
        int col = c(Stone.SPACE);
        Vec3 g = h.center.subtract(0, HOLE_Y, 0);
        at(lv, g.add(0, 0.06, 0), UP, 10F, Stone.SPACE, 70, T3 | RAYS | FAST);
        FxNet.shockwave(lv, g, 18F, col, 24);
        FxNet.shockwave(lv, g, 9F, l(Stone.SPACE), 16);
        FxNet.flare(lv, h.center, 8F, col, 20);
        FxNet.flashAround(lv, h.center, 30, col, 0.35F, 12);
        FxNet.shake(lv, h.center, 40, 0.9F, 14);
        lv.sendParticles(ParticleTypes.END_ROD, h.center.x, h.center.y, h.center.z, 60, 0.4, 0.4, 0.4, 0.5);
    }

    private static void holeLive(ServerLevel lv, Entity stand, Hole h, long age) {
        Vec3 c = h.center;
        int col = c(Stone.SPACE), col2 = l(Stone.SPACE);
        if (age % 40 == 0) {
            // two crossed accretion discs + a ground seal, kept alive by keyed refreshes
            com.infinitystones.tensura.skill.ability.ported.FxNet.circleKeyed(stand, key(stand, 1), new Vec3(0, HOLE_Y, 0),
                    new Vec3(0.28, 1, 0.1), 6.5F, col, col2, 56, T2 | RAYS | FAST);
            com.infinitystones.tensura.skill.ability.ported.FxNet.circleKeyed(stand, key(stand, 2), new Vec3(0, HOLE_Y, 0),
                    new Vec3(-0.3, 1, 0.25), 4.6F, col2, col, 56, T1 | HEPTA | FAST);
            com.infinitystones.tensura.skill.ability.ported.FxNet.circleKeyed(stand, key(stand, 3), new Vec3(0, 0.06, 0), UP,
                    10F, col, col2, 56, T3 | RAYS);
        }
        if (age % 10 == 0) FxNet.orb(lv, key(stand, 5), c, 1.25F, 0x2438B8, 24, stand.getId(), new Vec3(0, HOLE_Y, 0));
        if (age % 20 == 0) {
            FxNet.flare(lv, c, 5F, col, 22);
            FxNet.spiral(lv, c.subtract(0, 2, 0), 6.5F, -4F, col2, 36, -1);
        }
        if (age % 20 == 10) {
            FxNet.spiral(lv, c.subtract(0, 1.5, 0), 4F, 3F, col, 30, -1);
            FxNet.shockwave(lv, c.subtract(0, HOLE_Y, 0), 12F, col2, 20);
        }
        if (age % 2 == 0) {
            lv.sendParticles(ParticleTypes.PORTAL, c.x, c.y, c.z, 14, 5, 3, 5, 0.6);
            lv.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 5, 0.6, 0.6, 0.6, 0.05);
            dustRing(lv, c, 4.5, 6, Stone.SPACE, 1.3F, age * 0.25);
            dustRing(lv, c, 2.6, 5, Stone.SPACE, 1.0F, -age * 0.4);
        }
        if (age % 8 == 0) FxNet.shake(lv, c, 28, 0.3F, 8);
    }

    private static void holeCollapse(ServerLevel lv, Hole h) {
        int col = c(Stone.SPACE);
        Vec3 c = h.center;
        FxNet.kill(lv, c, key(h.id, 5));
        FxNet.shockwave(lv, c.subtract(0, HOLE_Y, 0), 22F, col, 26);
        FxNet.shockwave(lv, c.subtract(0, HOLE_Y, 0), 12F, l(Stone.SPACE), 18);
        FxNet.flare(lv, c, 11F, 0xFFFFFF, 18);
        at(lv, c.subtract(0, HOLE_Y - 0.06, 0), UP, 9F, Stone.SPACE, 36, T2 | RAYS | FAST);
        FxNet.flashAround(lv, c, 36, col, 0.5F, 14);
        FxNet.shake(lv, c, 48, 1.6F, 18);
        lv.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 90, 0.5, 0.5, 0.5, 0.9);
        lv.sendParticles(ParticleTypes.PORTAL, c.x, c.y, c.z, 140, 1, 1, 1, 1.4);
        PortFx.sound(lv, c, SoundEvents.GENERIC_EXPLODE, 0.5F, 0.55F);
    }

    private static int key(int entityId, int slot) { return ((entityId * 31 + slot * 7919) & 0x3FFFFFFF) | 0x40000000; }

    // ------------------------------------------------------------------ Power: meteor storm

    private static final class Storm {
        final ResourceKey<Level> dim; final Vec3 origin; final long start; int impacts;
        Storm(ResourceKey<Level> dim, Vec3 origin, long start) { this.dim = dim; this.origin = origin; this.start = start; }
    }
    private static final class Meteor {
        Vec3 last; final Storm storm;
        Meteor(Vec3 last, Storm storm) { this.last = last; this.storm = storm; }
    }
    private static final List<Storm> STORMS = new ArrayList<>();
    private static final Map<UUID, Meteor> METEORS = new HashMap<>();
    private static final int FIERY = 0xFF7A1A;

    private static void stormStart(ServerPlayer p) {
        ServerLevel lv = p.serverLevel();
        Vec3 pos = p.position();
        STORMS.add(new Storm(lv.dimension(), pos, p.server.getTickCount()));
        // a colossal seal in the sky where the rocks are summoned, plus the caster's own ground seal
        at(lv, pos.add(0, 66, 0), DOWN, 30F, Stone.POWER, 90, T3 | RAYS | FAST);
        at(lv, pos.add(0, 62, 0), DOWN, 18F, Stone.POWER, 80, T2 | HEPTA);
        at(lv, pos.add(0, 0.06, 0), UP, 5F, Stone.POWER, 50, T2 | RAYS | FAST);
        FxNet.pillar(lv, 0, pos, 1.2F, 70F, c(Stone.POWER), 30);
        FxNet.shockwave(lv, pos, 14F, c(Stone.POWER), 22);
        FxNet.flare(lv, pos.add(0, 66, 0), 22F, c(Stone.POWER), 40);
        FxNet.shake(lv, pos, 60, 0.7F, 40);
        FxNet.flash(p, c(Stone.POWER), 0.3F, 10);
        PortFx.sound(lv, pos, ModSounds.POWER_STONE_EXPLODE, 0.35F, 0.6F);
        PortFx.sound(lv, pos, SoundEvents.FIRECHARGE_USE, 0.6F, 0.5F);
    }

    private static void stormTick(MinecraftServer s, long now) {
        for (Iterator<Storm> it = STORMS.iterator(); it.hasNext(); ) {
            Storm st = it.next();
            ServerLevel lv = s.getLevel(st.dim);
            long el = now - st.start;
            if (lv == null || el > 400) { dropMeteors(st); it.remove(); continue; }
            if (el % 2 != 0) continue;
            Vec3 o = st.origin;
            AABB box = new AABB(o.x - 110, o.y - 30, o.z - 110, o.x + 110, o.y + 120, o.z + 110);
            List<FallingBlockEntity> current = lv.getEntitiesOfClass(FallingBlockEntity.class, box, e -> e.getTags().contains("meteorite"));
            Set<UUID> seen = new HashSet<>();
            for (FallingBlockEntity m : current) {
                seen.add(m.getUUID());
                Vec3 pos = m.position();
                Meteor known = METEORS.get(m.getUUID());
                if (known == null) {
                    METEORS.put(m.getUUID(), new Meteor(pos, st));
                    int gy = lv.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(pos.x), Mth.floor(pos.z));
                    FxNet.runes(lv, 0, new Vec3(pos.x, gy + 0.1, pos.z), 3.4F, c(Stone.POWER), 60, 2F);
                } else {
                    known.last = pos;
                }
                if (el % 4 == 0) {
                    FxNet.flare(lv, pos, 4.2F, FIERY, 6);
                    lv.sendParticles(PortFx.dust(FIERY, 2.4F), pos.x, pos.y + 0.5, pos.z, 8, 0.45, 0.45, 0.45, 0);
                    lv.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y + 1.0, pos.z, 3, 0.3, 0.5, 0.3, 0.02);
                } else {
                    lv.sendParticles(ParticleTypes.FLAME, pos.x, pos.y + 0.5, pos.z, 6, 0.35, 0.35, 0.35, 0.03);
                }
            }
            for (Iterator<Map.Entry<UUID, Meteor>> mi = METEORS.entrySet().iterator(); mi.hasNext(); ) {
                Map.Entry<UUID, Meteor> en = mi.next();
                Meteor m = en.getValue();
                if (m.storm != st || seen.contains(en.getKey())) continue;
                mi.remove();
                impact(lv, st, m.last);
            }
            if (el > 30 && countFor(st) == 0) it.remove();
        }
    }

    private static int countFor(Storm st) {
        int n = 0;
        for (Meteor m : METEORS.values()) if (m.storm == st) n++;
        return n;
    }

    private static void dropMeteors(Storm st) { METEORS.values().removeIf(m -> m.storm == st); }

    private static void impact(ServerLevel lv, Storm st, Vec3 last) {
        int gy = lv.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(last.x), Mth.floor(last.z));
        Vec3 g = new Vec3(last.x, Math.abs(last.y - gy) < 10 ? gy : last.y, last.z);
        int col = c(Stone.POWER);
        FxNet.shockwave(lv, g, 9F, FIERY, 20);
        FxNet.shockwave(lv, g, 5F, col, 14);
        FxNet.flare(lv, g.add(0, 1, 0), 6F, FIERY, 12);
        FxNet.shake(lv, g, 48, 0.9F, 10);
        lv.sendParticles(ParticleTypes.FLAME, g.x, g.y + 0.5, g.z, 40, 1.4, 0.8, 1.4, 0.18);
        lv.sendParticles(ParticleTypes.LAVA, g.x, g.y + 0.5, g.z, 14, 1.2, 0.5, 1.2, 0);
        lv.sendParticles(ParticleTypes.LARGE_SMOKE, g.x, g.y + 1, g.z, 10, 1.2, 1, 1.2, 0.06);
        if (st.impacts++ < 6) {
            at(lv, g.add(0, 0.06, 0), UP, 6.5F, Stone.POWER, 34, T2 | RAYS | FAST);
            PortFx.sound(lv, g, ModSounds.POWER_STONE_EXPLODE, 0.25F, 0.8F);
        }
    }

    // ------------------------------------------------------------------ server tick (black holes + meteors)

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post e) {
        if (HOLES.isEmpty() && SEARCHES.isEmpty() && STORMS.isEmpty()) return;
        try {
            MinecraftServer s = e.getServer();
            long now = s.getTickCount();
            if (!HOLES.isEmpty() || !SEARCHES.isEmpty()) holeTick(s, now);
            if (!STORMS.isEmpty()) stormTick(s, now);
        } catch (RuntimeException ex) { fail(ex); }
    }

    // ------------------------------------------------------------------ player tick (teleport / soulworld arrivals)

    @SubscribeEvent
    public static void playerTick(PlayerTickEvent.Post e) {
        if (PENDING.isEmpty() || !(e.getEntity() instanceof ServerPlayer p)) return;
        Pending pe = PENDING.get(p.getUUID());
        if (pe == null) return;
        try {
            boolean same = p.serverLevel().dimension().equals(pe.dim());
            if (!same || p.position().distanceToSqr(pe.from()) > 6.25) {
                PENDING.remove(p.getUUID());
                arrive(p, pe.from(), same, pe.stone());
            } else if (p.tickCount - pe.at() > (pe.stone() == Stone.SOUL ? 30 : 10)) {
                PENDING.remove(p.getUUID());
            }
        } catch (RuntimeException ex) { PENDING.remove(p.getUUID()); fail(ex); }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID id = e.getEntity().getUUID();
        PENDING.remove(id);
        FLYERS.remove(id);
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent e) {
        PENDING.clear(); FLYERS.clear(); HOLES.clear(); SEARCHES.clear(); STORMS.clear(); METEORS.clear();
    }

    // ------------------------------------------------------------------ Soul

    private static void soulParticles(ServerLevel lv, Vec3 pos, int n) {
        lv.sendParticles(ParticleTypes.SOUL, pos.x, pos.y + 0.2, pos.z, n, 1.6, 0.2, 1.6, 0.06);
        lv.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y + 0.3, pos.z, n / 2, 1.4, 0.3, 1.4, 0.08);
        lv.sendParticles(PortFx.dust(Stone.SOUL, 1.6F), pos.x, pos.y + 1, pos.z, n, 1.1, 1.0, 1.1, 0);
    }

    private static void summonZombie(ServerPlayer p) {
        ServerLevel lv = p.serverLevel();
        Vec3 pos = p.position();
        int col = c(Stone.SOUL);
        at(lv, pos.add(0, 0.05, 0), UP, 5F, Stone.SOUL, 50, T3 | RAYS | FAST);
        at(lv, pos.add(0, 0.04, 0), UP, 8F, Stone.SOUL, 44, T1 | HEPTA);
        FxNet.spiral(lv, pos, 2.6F, 4.5F, col, 30, p.getId());
        FxNet.spiral(lv, pos, 1.6F, 3.2F, l(Stone.SOUL), 24, p.getId());
        FxNet.pillar(lv, 0, pos, 1.3F, 8F, col, 26);
        FxNet.shockwave(lv, pos, 9F, col, 22);
        for (int k = 0; k < 6; k++) {
            double a = Math.PI * 2 * k / 6;
            FxNet.orb(lv, 0, pos.add(Math.cos(a) * 3, 1.2, Math.sin(a) * 3), 0.35F, l(Stone.SOUL), 24, -1, Vec3.ZERO);
        }
        soulParticles(lv, pos, 70);
        FxNet.shake(lv, pos, 14, 0.4F, 8);
        PortFx.sound(lv, pos, ModSounds.SOUL_STONE, 0.6F, 0.7F);
        PortFx.sound(lv, pos, SoundEvents.SOUL_ESCAPE, 0.6F, 0.8F);
    }

    private static void soulDepart(ServerPlayer p) {
        ServerLevel lv = p.serverLevel();
        Vec3 pos = p.position();
        int col = c(Stone.SOUL);
        at(lv, pos.add(0, 0.05, 0), UP, 5.2F, Stone.SOUL, 40, T3 | RAYS | FAST);
        FxNet.spiral(lv, pos, 2.6F, -5F, col, 24, -1);
        FxNet.spiral(lv, pos, 1.5F, -3.5F, l(Stone.SOUL), 20, -1);
        FxNet.shockwave(lv, pos, 8F, col, 18);
        FxNet.pillar(lv, 0, pos, 1F, 8F, col, 16);
        soulParticles(lv, pos, 60);
        FxNet.flash(p, col, 0.4F, 10);
        PortFx.sound(lv, pos, ModSounds.SOUL_STONE, 0.6F, 0.9F);
        PortFx.sound(lv, pos, SoundEvents.SOUL_ESCAPE, 0.5F, 1.1F);
        PENDING.put(p.getUUID(), new Pending(pos, lv.dimension(), p.tickCount, Stone.SOUL));
    }

    // ------------------------------------------------------------------ Time

    private static void freezeStart(ServerPlayer p) {
        ServerLevel lv = p.serverLevel();
        Vec3 pos = p.position();
        int col = c(Stone.TIME);
        FxNet.clock(lv, key(p, 4), pos, 7.5F, col, 30, 1.2F);
        under(p, 2, 0.05, 4.6F, Stone.TIME, 40, T3 | HEPTA);
        over(p, 3, 9, 9F, Stone.TIME, 50, T2 | HEPTA | FAST);
        FxNet.shockwave(lv, pos, 10F, col, 14);
        FxNet.shockwave(lv, pos, 18F, col, 22);
        FxNet.shockwave(lv, pos, 30F, l(Stone.TIME), 30);
        FxNet.flare(lv, mid(p), 6F, col, 16);
        FxNet.flash(p, col, 0.5F, 16);
        FxNet.shake(lv, pos, 30, 0.7F, 12);
        lv.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + 1, pos.z, 40, 1.4, 1.2, 1.4, 0.12);
    }

    private static void freezeTick(ServerPlayer p, int ticks) {
        if (ticks % 12 != 0) return;
        ServerLevel lv = p.serverLevel();
        FxNet.clock(lv, key(p, 4), p.position(), 7.5F, c(Stone.TIME), 26, 1.2F);
        under(p, 2, 0.05, 4.6F, Stone.TIME, 40, T3 | HEPTA);
        over(p, 3, 9, 9F, Stone.TIME, 50, T2 | HEPTA | FAST);
        if (ticks % 48 == 0) FxNet.shockwave(lv, p.position(), 14F, l(Stone.TIME), 20);
    }

    private static void freezeStop(ServerPlayer p) {
        ServerLevel lv = p.serverLevel();
        Vec3 pos = p.position();
        FxNet.kill(lv, pos, key(p, 4));
        FxNet.shockwave(lv, pos, 16F, c(Stone.TIME), 18);
        FxNet.shockwave(lv, pos, 26F, l(Stone.TIME), 26);
        FxNet.flash(p, c(Stone.TIME), 0.35F, 12);
        FxNet.shake(lv, pos, 24, 0.5F, 8);
    }

    private static void forwardStart(ServerPlayer p, StoneTree.Node n) {
        ServerLevel lv = p.serverLevel();
        Vec3 pos = p.position();
        int col = c(Stone.TIME);
        FxNet.clock(lv, key(p, 5), pos, 6F, col, 26, forwardSpeed(p, n));
        under(p, 6, 0.05, 3.8F, Stone.TIME, 40, T3 | HEPTA | FAST);
        over(p, 7, 7, 6.5F, Stone.TIME, 40, T2 | HEPTA | FAST);
        FxNet.shockwave(lv, pos, 11F, col, 16);
        FxNet.flash(p, col, 0.25F, 10);
    }

    private static float forwardSpeed(ServerPlayer p, StoneTree.Node n) {
        int speed = Levels.get(p, n.modeId(), -10, 10, 5);
        return speed == 0 ? 0.2F : speed * 0.5F;
    }

    private static void forwardTick(ServerPlayer p, StoneTree.Node n, int ticks) {
        if (ticks % 12 != 0) return;
        ServerLevel lv = p.serverLevel();
        FxNet.clock(lv, key(p, 5), p.position(), 6F, c(Stone.TIME), 26, forwardSpeed(p, n));
        under(p, 6, 0.05, 3.8F, Stone.TIME, 40, T3 | HEPTA | FAST);
        over(p, 7, 7, 6.5F, Stone.TIME, 40, T2 | HEPTA | FAST);
        if (ticks % 36 == 0) FxNet.shockwave(lv, p.position(), 9F, l(Stone.TIME), 16);
    }

    private static void forwardStop(ServerPlayer p) {
        FxNet.kill(p.serverLevel(), p.position(), key(p, 5));
        FxNet.shockwave(p.serverLevel(), p.position(), 9F, c(Stone.TIME), 14);
    }

    /** Called when the world speed changes (CommandPorts.timeSpeedChanged). cycle > 0 slows the world, < 0 speeds it up. */
    public static void timeSpeed(ServerPlayer p, int cycle) {
        try {
            ServerLevel lv = p.serverLevel();
            Vec3 pos = p.position();
            int col = c(Stone.TIME);
            int a = Math.abs(cycle);
            if (cycle == 0) {
                FxNet.shockwave(lv, pos, 10F, col, 16);
                FxNet.flash(p, col, 0.2F, 8);
                return;
            }
            FxNet.clock(lv, 0, pos, 6F + a, col, 44, cycle > 0 ? 0.35F : 2.4F);
            at(lv, pos.add(0, 8, 0), DOWN, 7F + a, Stone.TIME, 50, T3 | HEPTA | (cycle < 0 ? FAST : 0));
            FxNet.shockwave(lv, pos, 12F + a * 2, col, 20);
            FxNet.shockwave(lv, pos, 7F + a, l(Stone.TIME), 14);
            FxNet.flare(lv, mid(p), 5F, col, 14);
            FxNet.flash(p, col, 0.3F, 10);
            FxNet.shake(lv, pos, 20, 0.35F + a * 0.08F, 10);
            lv.sendParticles(PortFx.dust(Stone.TIME, 1.6F), pos.x, pos.y + 1, pos.z, 30 + a * 10, 1.4, 1.0, 1.4, 0);
        } catch (RuntimeException ex) { fail(ex); }
    }

    private static void stopperStart(ServerPlayer p) {
        ServerLevel lv = p.serverLevel();
        Vec3 pos = p.position();
        int col = c(Stone.TIME);
        FxNet.orb(lv, key(p, 8), pos, 2.4F, col, 14, p.getId(), new Vec3(0, 1, 0));
        under(p, 9, 0.05, 2.8F, Stone.TIME, 40, T1 | FAST);
        FxNet.shockwave(lv, pos, 6F, col, 14);
        FxNet.flare(lv, mid(p), 3.5F, col, 10);
    }

    private static void stopperTick(ServerPlayer p, int ticks) {
        if (ticks % 10 != 0) return;
        ServerLevel lv = p.serverLevel();
        FxNet.orb(lv, key(p, 8), p.position(), 2.4F, c(Stone.TIME), 14, p.getId(), new Vec3(0, 1, 0));
        if (ticks % 30 == 0) under(p, 9, 0.05, 2.8F, Stone.TIME, 40, T1 | FAST);
        if (ticks % 60 == 0) FxNet.shockwave(lv, p.position(), 4F, l(Stone.TIME), 12);
    }

    private static void stopperStop(ServerPlayer p) {
        FxNet.kill(p.serverLevel(), p.position(), key(p, 8));
        FxNet.shockwave(p.serverLevel(), p.position(), 5F, c(Stone.TIME), 12);
    }

    // ------------------------------------------------------------------ Reality

    private static void realityEffects(ServerPlayer p) {
        ServerLevel lv = p.serverLevel();
        Vec3 pos = p.position();
        int col = c(Stone.REALITY);
        at(lv, pos.add(0, 0.06, 0), UP, 12F, Stone.REALITY, 70, T3 | RAYS | FAST);
        at(lv, pos.add(0, 16, 0), DOWN, 18F, Stone.REALITY, 70, T2 | HEPTA | RAYS);
        FxNet.shockwave(lv, pos, 60F, col, 40);
        FxNet.shockwave(lv, pos, 26F, l(Stone.REALITY), 28);
        FxNet.pillar(lv, 0, pos, 1.4F, 30F, col, 30);
        FxNet.flare(lv, mid(p), 9F, col, 20);
        FxNet.flashAround(lv, pos, 100, col, 0.45F, 18);
        FxNet.shake(lv, pos, 100, 1.4F, 30);
        lv.sendParticles(PortFx.dust(Stone.REALITY, 2.2F), pos.x, pos.y + 1, pos.z, 120, 3, 1.6, 3, 0);
        PortFx.sound(lv, pos, ModSounds.REALITY_STONE, 0.6F, 0.7F);
    }

    /** Weather Wheel result: 0 clear, 1 rain, 2 thunder (called from WeatherWheel). */
    public static void weather(ServerPlayer p, int weather) {
        try {
            ServerLevel lv = p.serverLevel();
            Vec3 pos = p.position();
            int col = weather == 0 ? 0xFFE49A : weather == 1 ? 0x6FA8FF : c(Stone.REALITY);
            int col2 = weather == 0 ? 0xFFFFFF : weather == 1 ? 0xCFE3FF : 0xFFFFFF;
            com.infinitystones.tensura.skill.ability.ported.FxNet.circle(lv, pos.add(0, 24, 0), DOWN, 22F, col, col2, 80, T3 | RAYS | FAST);
            com.infinitystones.tensura.skill.ability.ported.FxNet.circle(lv, pos.add(0, 20, 0), DOWN, 13F, col2, col, 70, T2 | HEPTA);
            at(lv, pos.add(0, 0.06, 0), UP, 6F, Stone.REALITY, 50, T2 | RAYS | FAST);
            FxNet.pillar(lv, 0, pos, 1.2F, 26F, col, 30);
            FxNet.shockwave(lv, pos, 24F, col, 26);
            FxNet.flare(lv, pos.add(0, 24, 0), 16F, col, 30);
            FxNet.shake(lv, pos, 30, weather == 2 ? 1F : 0.5F, 18);
            if (weather == 2) {
                for (int k = 0; k < 3; k++) {
                    double a = p.getRandom().nextDouble() * Math.PI * 2, d = 9 + p.getRandom().nextDouble() * 8;
                    LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(lv);
                    if (bolt == null) continue;
                    bolt.moveTo(pos.x + Math.cos(a) * d, pos.y, pos.z + Math.sin(a) * d);
                    bolt.setVisualOnly(true);
                    lv.addFreshEntity(bolt);
                }
            }
        } catch (RuntimeException ex) { fail(ex); }
    }

    /** Called when the Reality size changes (cycle = the new size cycle, grow = bigger). */
    public static void sizeChange(ServerPlayer p, int cycle, boolean grow) {
        try {
            ServerLevel lv = p.serverLevel();
            Vec3 pos = p.position();
            double s = CommandPorts.scale(cycle);
            float r = (float) (1.5 + 1.6 * s);
            int col = c(Stone.REALITY);
            FxNet.shockwave(lv, pos, r * 1.6F, col, 12);
            FxNet.shockwave(lv, pos, r * 2.4F, l(Stone.REALITY), 20);
            FxNet.shockwave(lv, pos, r * 3.4F, col, 28);
            FxNet.spiral(lv, pos, (float) (0.6 + 0.8 * s), (float) ((1.4 + 1.3 * s) * (grow ? 1 : -1)), col, 22, p.getId());
            FxNet.flare(lv, mid(p), (float) (2.6 + 1.2 * s), col, 12);
            at(lv, pos.add(0, 0.06, 0), UP, r * 1.7F, Stone.REALITY, 32, T2 | RAYS | (grow ? FAST : 0));
            if (s >= 3 || s <= 0.3) FxNet.shake(lv, pos, 24, (float) Math.min(1.2, 0.4 + Math.abs(s - 1) * 0.12), 12);
            FxNet.flash(p, col, 0.2F, 8);
            lv.sendParticles(PortFx.dust(Stone.REALITY, 1.6F), pos.x, pos.y + p.getBbHeight() * 0.5, pos.z, 40, r * 0.5, 0.6, r * 0.5, 0);
        } catch (RuntimeException ex) { fail(ex); }
    }

    // ------------------------------------------------------------------ Mind

    private static void phaseStart(ServerPlayer p) {
        FxNet.flash(p, c(Stone.MIND), 0.25F, 8);
    }

    private static final class Flyer {
        Vec3 last, mark; double avg; int lastRing = -1000;
    }
    private static final Map<UUID, Flyer> FLYERS = new HashMap<>();

    /** Mind flight: golden ribbon + sparks behind a fast flyer, and a sonic ring when it breaks into a sprint. */
    private static void flightTick(ServerPlayer p, int ticks) {
        Flyer f = FLYERS.computeIfAbsent(p.getUUID(), k -> new Flyer());
        Vec3 cur = p.position();
        boolean air = (p.getAbilities().flying || p.isFallFlying()) && !p.onGround();
        if (!air || f.last == null) { f.last = cur; f.mark = cur; f.avg = 0; return; }
        Vec3 delta = cur.subtract(f.last);
        f.last = cur;
        f.avg = f.avg * 0.7 + delta.length() * 0.3;
        if (f.avg < 0.6 || delta.lengthSqr() < 1e-6) return;
        ServerLevel lv = p.serverLevel();
        Vec3 dir = delta.normalize();
        int col = c(Stone.MIND);
        Vec3 back = cur.add(0, 0.9, 0).subtract(dir.scale(0.5));
        lv.sendParticles(PortFx.dust(Stone.MIND, 1.4F), back.x, back.y, back.z, 3, 0.2, 0.3, 0.2, 0);
        if (ticks % 2 == 0) lv.sendParticles(ParticleTypes.END_ROD, back.x, back.y, back.z, 1, 0.1, 0.2, 0.1, 0.01);
        if (ticks % 3 == 0) {
            FxNet.beam(lv, f.mark.add(0, 0.9, 0), cur.add(0, 0.9, 0), col, 0.11F, 16);
            f.mark = cur;
        }
        if (f.avg > 0.95 && ticks - f.lastRing > 30) {
            f.lastRing = ticks;
            Vec3 ring = cur.add(0, 0.9, 0).add(dir.scale(0.8));
            at(lv, ring, dir, 2.6F, Stone.MIND, 16, T1 | FAST);
            FxNet.flare(lv, ring, 3F, l(Stone.MIND), 8);
        }
    }

    private PolishFx() {}
}
