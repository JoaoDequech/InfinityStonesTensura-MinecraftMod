package com.infinitystones.tensura.skill.ability.extra.space;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Ground;
import com.infinitystones.tensura.skill.ability.extra.Move;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import com.infinitystones.tensura.skill.ability.extra.mind.MindKit;
import com.infinitystones.tensura.skill.ability.extra.power.PowerNet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Portal Gun. Left: a blue portal on the block face you aim at (up to 64 blocks). Right: an orange one. Walk, fall,
 * throw items or fire arrows into one and they come out of the other, keeping their speed and turned to the new
 * direction (walls, floors and ceilings). Each player owns one pair; a new shot replaces that colour, both
 * portals must be in the same dimension, they close after 5 minutes, when the wall behind is gone or when you log out.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class PortalGun implements Ability {
    public static final PortalGun INSTANCE = new PortalGun();
    private static final int[] COLORS = {0x2F8CFF, 0xFF8A1E};
    private static final int[] LIGHT = {0x9CC8FF, 0xFFC27A};
    private static final int LIFE = 6000, COOLDOWN = 16, PER_PORTAL = 12;

    private static final class Portal {
        final ServerLevel level; final Vec3 c, n, side, up; final int color, light, fx = FxNet.newId(); final double sw, sh;
        int age;
        Portal(ServerLevel level, Vec3 c, Vec3 n, int idx) {
            this.level = level; this.c = c; this.n = n; this.color = COLORS[idx]; this.light = LIGHT[idx];
            boolean flat = Math.abs(n.y) > 0.9;
            // same axes as the client portal drawing: width 0.7·s, height s
            Vec3 u = flat ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            this.side = n.cross(u).normalize();
            this.up = side.cross(n).normalize();
            double s = flat ? 1.05 : 1.2;
            this.sw = s * 0.7; this.sh = s;
        }
    }

    private static final class Pair { final Portal[] p = new Portal[2]; }

    private static final Map<UUID, Pair> PAIRS = new HashMap<>();
    private static final Map<Integer, Long> COOL = new HashMap<>();
    private static final Map<Integer, Vec3> LAST = new HashMap<>();

    @Override public Stone stone() { return Stone.SPACE; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        int idx = alt ? 1 : 0;
        ServerLevel l = p.serverLevel();
        BlockHitResult hit = Aim.block(p, 64);
        if (hit.getType() == HitResult.Type.MISS) return Fx.fail(p, "infinity.extra.space.portal.nowall");

        Vec3 n = new Vec3(hit.getDirection().getStepX(), hit.getDirection().getStepY(), hit.getDirection().getStepZ());
        BlockPos bp = hit.getBlockPos();
        Vec3 c = Vec3.atCenterOf(bp).add(n.scale(0.52));
        BlockPos front = BlockPos.containing(c);
        if (!l.getBlockState(front).getCollisionShape(l, front).isEmpty()) return Fx.fail(p, "infinity.extra.space.portal.nowall");
        if (n.y == 0) {
            // wall portals rest on the floor in front of them when there is one (and the wall reaches that high)
            for (int dy = 0; dy <= 2; dy++) {
                BlockPos b = new BlockPos(front.getX(), front.getY() - dy - 1, front.getZ());
                if (l.getBlockState(b).getCollisionShape(l, b).isEmpty()) continue;
                Vec3 snapped = new Vec3(c.x, b.getY() + 2.0, c.z);
                if (backed(l, snapped, n)) c = snapped;
                break;
            }
        }

        Pair pair = PAIRS.computeIfAbsent(p.getUUID(), k -> new Pair());
        Portal other = pair.p[1 - idx];
        if (other != null && other.level != l) { drop(other); pair.p[1 - idx] = null; }
        if (pair.p[idx] != null) drop(pair.p[idx]);
        Portal portal = new Portal(l, c, n, idx);
        pair.p[idx] = portal;
        draw(portal, true);

        Vec3 from = p.getEyePosition().add(0, -0.3, 0);
        PowerNet.circle(l, 0, c, n, 2.2F, portal.color, portal.light, 30, 4F, PowerNet.SIMPLE);
        PowerNet.circle(l, 0, c.add(n.scale(0.05)), n, 1.4F, portal.light, portal.color, 30, -5F, PowerNet.GLYPHS_ONLY);
        FxNet.beam(l, from, c, portal.color, 0.1F, 8);
        FxNet.flare(l, c, 2.0F, portal.light, 8);
        Fx.at(l, Fx.dust(portal.color, 1.6F), c, 24, 0.7, 0.04);
        Fx.at(l, ParticleTypes.END_ROD, c, 8, 0.5, 0.08);
        Fx.sound(l, c, SoundEvents.BEACON_ACTIVATE, 0.5F, idx == 0 ? 1.6F : 1.2F);
        Fx.sound(l, p.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.5F, idx == 0 ? 1.3F : 0.8F);
        Fx.bar(p, Stone.SPACE, idx == 0 ? "infinity.extra.space.portal.blue" : "infinity.extra.space.portal.orange");
        return true;
    }

    // ------------------------------------------------------------------ upkeep + travel

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post e) {
        if (PAIRS.isEmpty()) return;
        long now = e.getServer().getTickCount();
        for (Iterator<Map.Entry<UUID, Pair>> it = PAIRS.entrySet().iterator(); it.hasNext(); ) {
            Pair pair = it.next().getValue();
            for (int i = 0; i < 2; i++) {
                Portal po = pair.p[i];
                if (po == null) continue;
                po.age++;
                if (po.age >= LIFE || (po.age % 20 == 0 && !backed(po))) { drop(po); pair.p[i] = null; continue; }
                draw(po, po.age % 100 == 1);
            }
            if (pair.p[0] == null && pair.p[1] == null) { it.remove(); continue; }
            if (pair.p[0] != null && pair.p[1] != null) {
                travel(pair.p[0], pair.p[1], now);
                travel(pair.p[1], pair.p[0], now);
            }
        }
        if (now % 200 == 0) { COOL.values().removeIf(t -> t < now); if (LAST.size() > 512) LAST.clear(); }
    }

    private static boolean backed(Portal po) { return backed(po.level, po.c, po.n); }

    private static boolean backed(ServerLevel l, Vec3 c, Vec3 n) {
        BlockPos b = BlockPos.containing(c.subtract(n.scale(0.52)));
        return !l.getBlockState(b).getCollisionShape(l, b).isEmpty();
    }

    private static void drop(Portal po) {
        FxNet.kill(po.level, po.c, po.fx);
        FxNet.flare(po.level, po.c, 1.4F, po.light, 6);
        Fx.at(po.level, Fx.dust(po.color, 1.4F), po.c, 12, 0.5, 0.03);
    }

    /** Re-sends the rift now and then and draws its glowing rim every few ticks. */
    private static void draw(Portal po, boolean resend) {
        ServerLevel l = po.level;
        if (resend) FxNet.portal(l, po.fx, po.c, po.n, (float) po.sh, po.color, 130);
        if (po.age % 3 != 0) return;
        double t = po.age * 0.12;
        int seg = 20;
        for (int i = 0; i < seg; i++) {
            double a = Math.PI * 2 * i / seg + t;
            Vec3 v = po.c.add(po.side.scale(Math.cos(a) * po.sw)).add(po.up.scale(Math.sin(a) * po.sh)).add(po.n.scale(0.04));
            l.sendParticles(i % 4 == 0 ? Fx.dust(po.light, 1.0F) : Fx.dust(po.color, 1.2F), v.x, v.y, v.z, 1, 0, 0, 0, 0);
        }
        if (po.age % 12 == 0) l.sendParticles(ParticleTypes.END_ROD, po.c.x, po.c.y, po.c.z, 1, po.sw * 0.5, po.sh * 0.5, po.sw * 0.5, 0.01);
    }

    private static void travel(Portal in, Portal out, long now) {
        ServerLevel l = in.level;
        AABB zone = new AABB(in.c, in.c).inflate(2.4);
        List<Entity> list = l.getEntitiesOfClass(Entity.class, zone, e -> eligible(e));
        int n = 0;
        for (Entity e : list) {
            if (++n > PER_PORTAL) break;
            Vec3 pos = e.position();
            Vec3 last = LAST.put(e.getId(), pos);
            if (COOL.getOrDefault(e.getId(), 0L) > now) continue;
            Vec3 center = e.getBoundingBox().getCenter();
            Vec3 rel = center.subtract(in.c);
            double dn = rel.dot(in.n);
            double half = Math.abs(in.n.x) * e.getBbWidth() / 2 + Math.abs(in.n.y) * e.getBbHeight() / 2 + Math.abs(in.n.z) * e.getBbWidth() / 2;
            if (dn - half > 0.22 || dn < -0.5) continue;
            double lat = rel.dot(in.side) / (in.sw + 0.15), vert = rel.dot(in.up) / (in.sh + 0.15);
            if (lat * lat + vert * vert > 1) continue;
            Vec3 vel = last != null && last.distanceToSqr(pos) > 1e-6 ? pos.subtract(last) : e.getDeltaMovement();
            if (e instanceof Projectile || e instanceof net.minecraft.world.entity.item.ItemEntity) vel = e.getDeltaMovement();
            if (transfer(l, in, out, e, vel, now)) COOL.put(e.getId(), now + COOLDOWN);
            else COOL.put(e.getId(), now + 8);
        }
    }

    private static boolean eligible(Entity e) {
        if (e.isSpectator() || e.isPassenger() || e.isVehicle() || e.isRemoved()) return false;
        if (e instanceof Display || e instanceof Marker || e instanceof ArmorStand || MindKit.isBody(e)) return false;
        if (e.getType() == EntityType.INTERACTION) return false;
        return e instanceof LivingEntity || e instanceof Projectile || e instanceof net.minecraft.world.entity.item.ItemEntity
                || e instanceof net.minecraft.world.entity.item.PrimedTnt || e instanceof net.minecraft.world.entity.item.FallingBlockEntity
                || e instanceof net.minecraft.world.entity.vehicle.AbstractMinecart || e instanceof net.minecraft.world.entity.ExperienceOrb;
    }

    private static boolean transfer(ServerLevel l, Portal in, Portal out, Entity e, Vec3 vel, long now) {
        if (com.infinitystones.tensura.skill.ability.extra.space.SpiritualGathering.isTrapped(e)) return false;
        Quaternionf q = new Quaternionf().rotationTo(
                new Vector3f((float) -in.n.x, (float) -in.n.y, (float) -in.n.z),
                new Vector3f((float) out.n.x, (float) out.n.y, (float) out.n.z));
        Vec3 vOut = rot(q, vel);
        double along = vOut.dot(out.n);
        if (along < 0.25) vOut = vOut.add(out.n.scale(0.25 - along));

        double w = e.getBbWidth(), h = e.getBbHeight();
        double half = Math.abs(out.n.x) * w / 2 + Math.abs(out.n.y) * h / 2 + Math.abs(out.n.z) * w / 2;
        Vec3 feet = out.c.add(out.n.scale(half + 0.25)).subtract(0, h / 2, 0);
        if (!Ground.fits(l, e, feet)) {
            Vec3 higher = feet.add(0, 0.45, 0);
            if (!Ground.fits(l, e, higher)) return false;
            feet = higher;
        }
        Vec3 look = e instanceof LivingEntity ? e.getLookAngle() : vel;
        Vec3 lookOut = rot(q, look.lengthSqr() < 1e-6 ? out.n : look);
        float yaw = Move.yaw(lookOut), pitch = Move.pitch(lookOut);

        Vec3 enter = e.getBoundingBox().getCenter();
        Fx.at(l, Fx.dust(in.color, 1.3F), enter, 10, 0.3, 0.03);
        FxNet.flare(l, in.c, 1.4F, in.light, 5);
        if (e instanceof ServerPlayer sp) sp.connection.teleport(feet.x, feet.y, feet.z, yaw, pitch);
        else {
            e.moveTo(feet.x, feet.y, feet.z, yaw, pitch);
            if (e instanceof net.minecraft.world.entity.Mob m) m.getNavigation().stop();
        }
        Move.set(e, vOut);
        e.fallDistance = 0;
        LAST.put(e.getId(), feet);
        Fx.at(l, Fx.dust(out.color, 1.3F), out.c.add(out.n.scale(0.4)), 14, 0.4, 0.05);
        FxNet.flare(l, out.c, 1.6F, out.light, 6);
        Fx.sound(l, in.c, SoundEvents.ENDERMAN_TELEPORT, 0.35F, 1.6F);
        Fx.sound(l, out.c, SoundEvents.ENDERMAN_TELEPORT, 0.45F, 1.0F);
        return true;
    }

    private static Vec3 rot(Quaternionf q, Vec3 v) {
        Vector3f t = new Vector3f((float) v.x, (float) v.y, (float) v.z);
        q.transform(t);
        return new Vec3(t.x, t.y, t.z);
    }

    // ------------------------------------------------------------------ cleanup

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        Pair pair = PAIRS.remove(e.getEntity().getUUID());
        if (pair == null) return;
        for (Portal po : pair.p) if (po != null) drop(po);
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent e) {
        PAIRS.clear();
        COOL.clear();
        LAST.clear();
    }
}
