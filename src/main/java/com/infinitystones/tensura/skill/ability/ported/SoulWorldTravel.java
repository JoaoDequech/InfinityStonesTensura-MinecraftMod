package com.infinitystones.tensura.skill.ability.ported;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.StoneTree;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;

/**
 * Enter / Exit Soulworld. The original ran {@code tp @s ~ 72 ~} on everything within 5 blocks, in both directions, so the way
 * back always dropped everybody at y=72 of the overworld. Here every entity taken in remembers where it stood (dimension,
 * position, rotation) in its own persistent data, which is saved with the entity: the way out puts each one back on exactly that
 * spot, even after a relog or a server restart. Only when the spot became solid the nearest free place above it is used.
 */
public final class SoulWorldTravel {
    private static final ResourceKey<Level> SOULWORLD = ResourceKey.create(Registries.DIMENSION, InfinityStones.id("soulworld"));
    private static final String ENTRY = "infinity_soul_entry";
    private static final double RADIUS = 5;
    private static final String IN_SOUL = "in_soul";

    public static final class Enter implements Ported {
        @Override
        public Boolean press(ServerPlayer p, StoneTree.Node n, boolean alt) {
            if (p.getTags().contains(IN_SOUL) || p.level().dimension().equals(SOULWORLD)) return false;
            ServerLevel soul = p.server.getLevel(SOULWORLD);
            if (soul == null) return null;
            for (Entity e : crowd(p)) {
                if (e.level().dimension().equals(SOULWORLD)) continue;
                remember(e);
                e.stopRiding();
                e.teleportTo(soul, e.getX(), 72, e.getZ(), Set.of(), e.getYRot(), e.getXRot());
            }
            p.addTag(IN_SOUL);
            return true;
        }
    }

    public static final class Exit implements Ported {
        @Override
        public Boolean press(ServerPlayer p, StoneTree.Node n, boolean alt) {
            if (!p.getTags().contains(IN_SOUL)) {
                CommandPorts.msg(p, "not_in_soulworld", ChatFormatting.GOLD);
                return false;
            }
            CompoundTag own = p.getPersistentData().contains(ENTRY) ? p.getPersistentData().getCompound(ENTRY).copy() : null;
            Vec3 center = p.position();
            for (Entity e : crowd(p)) {
                CompoundTag at = e.getPersistentData().contains(ENTRY) ? e.getPersistentData().getCompound(ENTRY).copy() : null;
                if (at == null && own != null) at = shifted(own, e.position().subtract(center));
                if (at != null) e.getPersistentData().remove(ENTRY);
                send(e, at, p);
            }
            p.getPersistentData().remove(ENTRY);
            p.removeTag(IN_SOUL);
            return true;
        }
    }

    /** Everything within 5 blocks of the caster (the caster included), like {@code @e[distance=..5]} plus the caster. */
    private static List<Entity> crowd(ServerPlayer p) {
        AABB box = p.getBoundingBox().inflate(RADIUS);
        return p.level().getEntities((Entity) null, box, e -> e == p || e.position().distanceToSqr(p.position()) <= RADIUS * RADIUS);
    }

    private static void remember(Entity e) {
        CompoundTag t = new CompoundTag();
        t.putString("Dim", e.level().dimension().location().toString());
        t.putDouble("X", e.getX());
        t.putDouble("Y", e.getY());
        t.putDouble("Z", e.getZ());
        t.putFloat("Yaw", e.getYRot());
        t.putFloat("Pitch", e.getXRot());
        e.getPersistentData().put(ENTRY, t);
    }

    private static CompoundTag shifted(CompoundTag base, Vec3 offset) {
        CompoundTag t = base.copy();
        t.putDouble("X", base.getDouble("X") + offset.x);
        t.putDouble("Z", base.getDouble("Z") + offset.z);
        return t;
    }

    private static void send(Entity e, CompoundTag at, ServerPlayer caster) {
        ServerLevel to;
        double x, y, z;
        float yaw = e.getYRot(), pitch = e.getXRot();
        boolean exact = at != null;
        if (exact) {
            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(at.getString("Dim")));
            ServerLevel l = caster.server.getLevel(dim);
            exact = l != null;
            to = l != null ? l : caster.server.overworld();
            x = at.getDouble("X"); y = at.getDouble("Y"); z = at.getDouble("Z");
            yaw = at.getFloat("Yaw"); pitch = at.getFloat("Pitch");
        } else {
            to = caster.server.overworld();
            x = e.getX(); z = e.getZ(); y = Double.NaN;
        }
        if (!exact || !fits(to, e, x, y, z)) {
            // the spot is solid now (or unknown): nearest free place above it, else the surface
            double free = Double.NaN;
            if (exact) for (int up = 1; up <= 48 && Double.isNaN(free); up++) if (fits(to, e, x, y + up, z)) free = y + up;
            if (Double.isNaN(free)) {
                to.getChunk((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4);
                free = to.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
            }
            y = free;
        }
        e.stopRiding();
        e.teleportTo(to, x, y, z, Set.of(), yaw, pitch);
        if (e instanceof LivingEntity le) le.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, true, false, false));
    }

    private static boolean fits(ServerLevel level, Entity e, double x, double y, double z) {
        if (Double.isNaN(y) || y < level.getMinBuildHeight() || y > level.getMaxBuildHeight()) return false;
        if (!level.hasChunkAt(net.minecraft.core.BlockPos.containing(x, y, z))) level.getChunk((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4);
        return level.noCollision(e.getType().getDimensions().makeBoundingBox(x, y, z));
    }

    private SoulWorldTravel() {}
}
