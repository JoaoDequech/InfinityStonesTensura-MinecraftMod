package com.infinitystones.tensura.skill.ability.ported;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.StoneTree;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.function.Predicate;

/** Shared helpers for ported abilities: raw parameter reading, aiming, PvP-safe targeting, particles and sounds. */
public final class PortFx {
    /** Ceiling for ability sound volume (the user finds the originals too loud). */
    public static final float MAX_VOLUME = 0.7F;

    // ---------------- raw parameters (original Palladium JSON) ----------------

    public static float f(StoneTree.Node n, String key, float def) {
        JsonElement e = n.raw().get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsFloat() : def;
    }
    public static int i(StoneTree.Node n, String key, int def) {
        JsonElement e = n.raw().get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsInt() : def;
    }
    public static boolean b(StoneTree.Node n, String key, boolean def) {
        JsonElement e = n.raw().get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean() : def;
    }
    public static String s(StoneTree.Node n, String key, String def) {
        JsonElement e = n.raw().get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : def;
    }
    public static @Nullable JsonObject obj(StoneTree.Node n, String key) {
        JsonElement e = n.raw().get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    /** The first enabling condition of the original JSON, or null. */
    public static @Nullable JsonObject enabling(StoneTree.Node n) {
        JsonObject c = n.raw().has("conditions") && n.raw().get("conditions").isJsonObject() ? n.raw().getAsJsonObject("conditions") : null;
        if (c == null || !c.has("enabling") || !c.get("enabling").isJsonArray() || c.getAsJsonArray("enabling").isEmpty()) return null;
        JsonElement e = c.getAsJsonArray("enabling").get(0);
        return e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    /** Original "palladium:activation" duration (press once, stays on N ticks), or 0 when the node is a real hold. */
    public static int activationTicks(StoneTree.Node n) {
        JsonObject en = enabling(n);
        if (en == null || !"palladium:activation".equals(en.has("type") ? en.get("type").getAsString() : "")) return 0;
        return en.has("ticks") ? Math.max(1, en.get("ticks").getAsInt()) : 1;
    }

    // ---------------- colours / particles ----------------

    public static Vector3f rgb(int argb) {
        return new Vector3f(((argb >> 16) & 255) / 255F, ((argb >> 8) & 255) / 255F, (argb & 255) / 255F);
    }
    public static DustParticleOptions dust(Stone stone, float size) { return new DustParticleOptions(rgb(stone.color()), size); }
    public static DustParticleOptions dust(int rgb, float size) { return new DustParticleOptions(rgb(rgb), size); }

    /** "#RRGGBB" → 0xRRGGBB, or the fallback. */
    public static int hex(String s, int fallback) {
        try { return Integer.parseInt(s.startsWith("#") ? s.substring(1) : s, 16); } catch (Exception e) { return fallback; }
    }

    public static void line(ServerLevel level, Vec3 a, Vec3 b, ParticleOptions particle, double perBlock) {
        double len = a.distanceTo(b);
        int steps = Math.max(2, Mth.ceil(len * perBlock));
        for (int k = 0; k <= steps; k++) {
            Vec3 pt = a.lerp(b, k / (double) steps);
            level.sendParticles(particle, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
        }
    }

    public static void ring(ServerLevel level, Vec3 c, double radius, int points, ParticleOptions particle, double phase) {
        for (int k = 0; k < points; k++) {
            double a = Math.PI * 2 * k / points + phase;
            level.sendParticles(particle, c.x + Math.cos(a) * radius, c.y, c.z + Math.sin(a) * radius, 1, 0, 0, 0, 0);
        }
    }

    public static void burst(ServerLevel level, Vec3 c, ParticleOptions particle, int count, double spread, double speed) {
        level.sendParticles(particle, c.x, c.y, c.z, count, spread, spread, spread, speed);
    }

    // ---------------- sounds (moderate volume, heard by everyone nearby) ----------------

    public static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, Math.min(MAX_VOLUME, volume), pitch);
    }
    public static void sound(ServerLevel level, Vec3 at, Holder<SoundEvent> sound, float volume, float pitch) {
        sound(level, at, sound.value(), volume, pitch);
    }
    /** Sound by id (original "sound" fields); unknown ids still play client-side if a resource pack defines them. */
    public static void sound(ServerLevel level, Vec3 at, String id, float volume, float pitch) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return;
        SoundEvent ev = BuiltInRegistries.SOUND_EVENT.getOptional(rl).orElseGet(() -> SoundEvent.createVariableRangeEvent(rl));
        sound(level, at, ev, volume, pitch);
    }
    public static void sound(ServerLevel level, Vec3 at, StoneTree.Node n, String fallback, float volume, float pitch) {
        sound(level, at, s(n, "sound", fallback), volume, pitch);
    }

    // ---------------- targeting ----------------

    public static DamageSource attack(ServerPlayer p) { return p.damageSources().playerAttack(p); }

    /** Like {@link #attack} but still lands on players when server PvP / teams would block a player attack (project decision). */
    public static DamageSource attack(ServerPlayer p, Entity target) {
        if (target instanceof Player other && (!p.server.isPvpAllowed() || !p.canHarmPlayer(other)))
            return new DamageSource(p.serverLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(net.minecraft.world.damagesource.DamageTypes.MAGIC));
        return attack(p);
    }

    /**
     * True when the caster may hurt/affect this entity: not itself, alive, not its own pet/ally; players only with
     * server PvP on and when the caster may harm them (teams), like the original abilities on a server.
     */
    public static boolean canAffect(ServerPlayer caster, Entity target) {
        if (target == caster || !target.isAlive() || target.isSpectator()) return false;
        if (target instanceof ArmorStand) return false;
        if (target instanceof Player other) // hits players even with PvP off (project decision), but never inside somebody else's claim
            return !other.isCreative() && !(other instanceof ServerPlayer victim && (com.infinitystones.tensura.compat.ClaimsCompat.isProtected(victim, caster)
                    || com.infinitystones.tensura.compat.ClaimsCompat.sameParty(caster, victim)));
        if (target instanceof OwnableEntity own && caster.getUUID().equals(own.getOwnerUUID())) return false;
        if (target instanceof TamableAnimal t && t.isOwnedBy(caster)) return false;
        return !caster.isAlliedTo(target);
    }

    public static boolean hostileTarget(ServerPlayer caster, Entity target) {
        return target instanceof LivingEntity && canAffect(caster, target);
    }

    /** First living entity along the caster's view (blocked by solid blocks), or null. */
    public static @Nullable LivingEntity aimed(ServerPlayer caster, double distance, Predicate<LivingEntity> filter) {
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getViewVector(1F);
        Vec3 end = eye.add(look.scale(distance));
        BlockHitResult block = caster.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
        AABB box = caster.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.5D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(caster.level(), caster, eye, end, box,
                e -> e instanceof LivingEntity l && filter.test(l), 0.3F);
        if (hit != null && hit.getEntity() instanceof LivingEntity l) return l;
        // generous fallback: closest to the crosshair line within a small cone (like the Banishment port)
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        double max = eye.distanceTo(end);
        for (LivingEntity c : caster.level().getEntitiesOfClass(LivingEntity.class, box, filter::test)) {
            if (c == caster) continue;
            Vec3 d = c.getBoundingBox().getCenter().subtract(eye);
            double proj = d.dot(look);
            if (proj <= 0 || proj > max) continue;
            double lat = Math.max(0, d.lengthSqr() - proj * proj);
            double tol = Math.max(1.1D, c.getBbWidth() * 0.7D) + proj * 0.025D;
            if (lat > tol * tol) continue;
            double score = lat * 8 + d.lengthSqr() * 0.001;
            if (score < bestScore) { bestScore = score; best = c; }
        }
        return best;
    }

    /** Where the caster's view hits a block (or the max distance point). */
    public static Vec3 aimPoint(ServerPlayer caster, double distance) {
        Vec3 eye = caster.getEyePosition();
        Vec3 end = eye.add(caster.getViewVector(1F).scale(distance));
        BlockHitResult block = caster.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        return block.getType() == HitResult.Type.MISS ? end : block.getLocation();
    }

    public static BlockHitResult aimBlock(ServerPlayer caster, double distance) {
        Vec3 eye = caster.getEyePosition();
        Vec3 end = eye.add(caster.getViewVector(1F).scale(distance));
        return caster.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, caster));
    }

    /** Can the caster change blocks here? Claims + spawn protection + world border; mobGriefing and OP are not involved. */
    public static boolean mayEdit(ServerPlayer caster, net.minecraft.core.BlockPos pos) {
        return com.infinitystones.tensura.skill.ability.extra.Terrain.may(caster, caster.serverLevel(), pos);
    }

    private PortFx() {}
}
