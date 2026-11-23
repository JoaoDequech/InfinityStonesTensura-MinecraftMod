package com.infinitystones.tensura.skill.ability.ported;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.StoneTree;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * palladium:projectile with palladium:custom_projectile (Event Horizon, Reality Shatter, Soul Severance). Instead of
 * registering an entity, the projectile is simulated on the server (position, block clip, entity sweep) and drawn
 * with particles in its original laser colours. Original numbers: velocity, Damage, Size, Lifetime, DieOnEntityHit,
 * DieOnBlockHit, ExplosionRadius/CausesFire/BlockInteraction. Entities are hurt once each while it passes through.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class VirtualProjectile implements Ported {
    private static final class Shot {
        final ServerLevel level; final UUID owner; final Vec3 vel; Vec3 pos;
        final float damage, size, explosion; final int lifetime; final boolean dieOnEntity, dieOnBlock, fire, destroy;
        final int outer, inner; final Set<UUID> hit = new HashSet<>(); int age;
        Shot(ServerLevel level, UUID owner, Vec3 pos, Vec3 vel, float damage, float size, int lifetime, boolean dieOnEntity,
             boolean dieOnBlock, float explosion, boolean fire, boolean destroy, int outer, int inner) {
            this.level = level; this.owner = owner; this.pos = pos; this.vel = vel; this.damage = damage; this.size = size;
            this.lifetime = lifetime; this.dieOnEntity = dieOnEntity; this.dieOnBlock = dieOnBlock; this.explosion = explosion;
            this.fire = fire; this.destroy = destroy; this.outer = outer; this.inner = inner;
        }
    }
    private static final List<Shot> SHOTS = new ArrayList<>();

    @Override
    public Boolean press(ServerPlayer p, StoneTree.Node n, boolean alt) {
        JsonObject d = PortFx.obj(n, "entity_data");
        if (d == null) d = new JsonObject();
        float velocity = PortFx.f(n, "velocity", 1.5F);
        float inaccuracy = PortFx.f(n, "inaccuracy", 0F);
        Vec3 dir = p.getViewVector(1F);
        if (inaccuracy > 0) dir = dir.add(p.getRandom().triangle(0, 0.0172275 * inaccuracy), p.getRandom().triangle(0, 0.0172275 * inaccuracy),
                p.getRandom().triangle(0, 0.0172275 * inaccuracy)).normalize();
        int outer = n.stone().color() & 0xFFFFFF, inner = 0xFFFFFF;
        if (d.has("Appearances") && d.get("Appearances").isJsonArray()) {
            JsonArray a = d.getAsJsonArray("Appearances");
            List<Integer> colours = new ArrayList<>();
            for (JsonElement e : a) if (e.isJsonObject() && e.getAsJsonObject().has("Color"))
                colours.add(PortFx.hex(e.getAsJsonObject().get("Color").getAsString(), outer));
            if (colours.size() > 0) outer = colours.get(0);
            if (colours.size() > 1) inner = colours.get(1);
        }
        Vec3 start = p.getEyePosition().add(dir.scale(0.8)).add(0, -0.1, 0);
        SHOTS.add(new Shot(p.serverLevel(), p.getUUID(), start, dir.scale(velocity), num(d, "Damage", 0), num(d, "Size", 1),
                (int) num(d, "Lifetime", 200), bool(d, "DieOnEntityHit", true), bool(d, "DieOnBlockHit", true),
                num(d, "ExplosionRadius", 0), bool(d, "ExplosionCausesFire", false),
                !"keep".equalsIgnoreCase(d.has("ExplosionBlockInteraction") ? d.get("ExplosionBlockInteraction").getAsString() : "keep"),
                outer, inner));
        // original companion "*_sound" abilities
        switch (n.modeId()) {
            case "space.event_horizon" -> PortFx.sound(p.serverLevel(), p.getEyePosition(), "minecraft:block.end_portal.spawn", 0.7F, 0.55F);
            case "reality.reality_shatter" -> PortFx.sound(p.serverLevel(), p.getEyePosition(), "infinity:reality_stone", 0.7F, 0.8F);
            case "soul.soul_severance" -> PortFx.sound(p.serverLevel(), p.getEyePosition(), "infinity:soul_stone", 0.7F, 0.78F);
            default -> PortFx.sound(p.serverLevel(), p.getEyePosition(), "minecraft:entity.illusioner.cast_spell", 0.5F, 1.2F);
        }
        return true;
    }

    private static float num(JsonObject o, String k, float def) {
        return o.has(k) && o.get(k).isJsonPrimitive() && o.get(k).getAsJsonPrimitive().isNumber() ? o.get(k).getAsFloat() : def;
    }
    private static boolean bool(JsonObject o, String k, boolean def) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsBoolean() : def;
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post e) {
        if (SHOTS.isEmpty()) return;
        for (Iterator<Shot> it = SHOTS.iterator(); it.hasNext(); ) {
            Shot s = it.next();
            if (step(s)) it.remove();
        }
    }

    /** @return true when the shot is finished. */
    private static boolean step(Shot s) {
        if (++s.age > s.lifetime || !s.level.isLoaded(BlockPos.containing(s.pos))) return true;
        Entity ownerEntity = s.level.getEntity(s.owner);
        ServerPlayer owner = ownerEntity instanceof ServerPlayer sp ? sp : null;
        Vec3 next = s.pos.add(s.vel);

        BlockHitResult block = s.level.clip(new ClipContext(s.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                net.minecraft.world.phys.shapes.CollisionContext.empty()));
        Vec3 end = block.getType() == HitResult.Type.MISS ? next : block.getLocation();

        // entities along the segment (projectile has a width = Size)
        AABB sweep = new AABB(s.pos, end).inflate(s.size / 2D);
        for (Entity t : s.level.getEntities((Entity) null, sweep, x -> x.isPickable() && !x.isSpectator() && !x.getUUID().equals(s.owner))) {
            if (s.hit.contains(t.getUUID())) continue;
            if (t.getBoundingBox().inflate(s.size / 2D).clip(s.pos, end).isEmpty() && !t.getBoundingBox().intersects(sweep.deflate(s.size / 4D))) continue;
            if (owner != null && !PortFx.canAffect(owner, t)) continue;
            s.hit.add(t.getUUID());
            t.hurt(owner != null ? PortFx.attack(owner, t) : s.level.damageSources().magic(), s.damage);
            s.level.sendParticles(ParticleTypes.FLASH, t.getX(), t.getY(0.5), t.getZ(), 1, 0, 0, 0, 0);
            explode(s, owner, t.position()); // original CustomProjectile explodes on every entity hit
            if (s.dieOnEntity) return true;
        }

        draw(s, end);
        s.pos = end;
        if (block.getType() != HitResult.Type.MISS && s.dieOnBlock) { explode(s, owner, block.getLocation()); return true; }
        return false;
    }

    private static void draw(Shot s, Vec3 end) {
        Vec3 tail = s.pos.subtract(s.vel.normalize().scale(Math.min(4, s.vel.length() * 2)));
        PortFx.line(s.level, tail, end, PortFx.dust(s.outer, Math.min(4F, s.size * 1.6F)), 2.2);
        PortFx.line(s.level, tail, end, PortFx.dust(s.inner, Math.min(2F, s.size * 0.8F)), 1.6);
        s.level.sendParticles(ParticleTypes.END_ROD, end.x, end.y, end.z, 2, s.size * 0.2, s.size * 0.2, s.size * 0.2, 0.01);
    }

    private static void explode(Shot s, ServerPlayer owner, Vec3 at) {
        s.level.sendParticles(PortFx.dust(s.outer, 3F), at.x, at.y, at.z, 60, s.explosion * 0.3, s.explosion * 0.3, s.explosion * 0.3, 0.1);
        s.level.sendParticles(PortFx.dust(s.inner, 2F), at.x, at.y, at.z, 30, s.explosion * 0.2, s.explosion * 0.2, s.explosion * 0.2, 0.1);
        if (s.explosion <= 0) return;
        boolean grief = s.destroy && owner != null && PortFx.mayEdit(owner, BlockPos.containing(at));
        // entity-less explosion source: also hurts players when PvP is off (project decision)
        s.level.explode(owner, new net.minecraft.world.damagesource.DamageSource(s.level.registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(net.minecraft.world.damagesource.DamageTypes.EXPLOSION)), null, at.x, at.y, at.z, s.explosion,
                s.fire && grief, grief ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
    }
}
