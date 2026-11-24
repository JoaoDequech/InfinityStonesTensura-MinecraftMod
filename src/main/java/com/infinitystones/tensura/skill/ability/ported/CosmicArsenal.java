package com.infinitystones.tensura.skill.ability.ported;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.StoneTree;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Power Stone "Cosmic Arsenal" — port of CosmicArsenalAbility (timed session, 160 ticks): {@code fragments} Power Stone
 * shards orbit the caster; every shot_interval ticks one launches at the hostile mob under the caster's aim (within
 * target_range), flying straight at {@code velocity} blocks/tick for up to 80 ticks, dealing {@code damage} with 0.45
 * knockback and vanishing on the first block or hostile hit. Unfired shards are dismissed when the session ends.
 * The original Palladium CustomProjectile (laser look) is replaced by a server-simulated, particle-rendered shard.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class CosmicArsenal implements Ported {

    private static final int SHARD_LIFETIME = 80;
    private static final double SHARD_HALF_SIZE = 0.11D;
    private static final float SHARD_KNOCKBACK = 0.45F;

    private static final class Arsenal {
        final ResourceKey<Level> dimension;
        int elapsed, fired, nextShot = 10;
        Arsenal(ResourceKey<Level> dimension) { this.dimension = dimension; }
    }

    private static final class Shard {
        final ServerLevel level; final UUID owner; final Vec3 velocity; final float damage; final int color;
        Vec3 pos; int age;
        Shard(ServerLevel level, UUID owner, Vec3 pos, Vec3 velocity, float damage, int color) {
            this.level = level; this.owner = owner; this.pos = pos; this.velocity = velocity; this.damage = damage; this.color = color;
        }
    }

    private static final Map<UUID, Arsenal> ARSENALS = new HashMap<>();
    private static final List<Shard> SHARDS = new ArrayList<>();

    @Override
    public void start(ServerPlayer p, StoneTree.Node n) {
        ServerLevel level = p.serverLevel();
        ARSENALS.put(p.getUUID(), new Arsenal(level.dimension()));
        PortFx.sound(level, new Vec3(p.getX(), p.getEyeY(), p.getZ()), n, "infinity:power_stone_beam1", 0.6F, 1.18F);
        PortFx.sound(level, p.position(), SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.55F, 0.68F);
        int fragments = fragments(n);
        DustParticleOptions core = PortFx.dust(light(n.stone().color(), 75), 0.6F);
        for (int i = 0; i < fragments; i++) {
            Vec3 pt = fragmentPosition(p, i, 0, fragments);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, pt.x, pt.y, pt.z, 9, 0.18D, 0.18D, 0.18D, 0.035D);
            level.sendParticles(core, pt.x, pt.y, pt.z, 4, 0.08D, 0.08D, 0.08D, 0.01D);
        }
    }

    @Override
    public void tick(ServerPlayer p, StoneTree.Node n, int ticks) {
        ServerLevel level = p.serverLevel();
        Arsenal a = ARSENALS.get(p.getUUID());
        if (a == null || !a.dimension.equals(level.dimension())) return;
        int fragments = fragments(n);
        a.elapsed++;
        renderOrbit(level, p, a, fragments, n.stone().color());

        if (a.fired >= fragments || a.elapsed < a.nextShot) return;
        LivingEntity target = aimedTarget(p, PortFx.f(n, "target_range", 48F));
        if (target == null) {
            a.nextShot = a.elapsed + 2;
            return;
        }
        Vec3 origin = fragmentPosition(p, a.fired, a.elapsed, fragments);
        launch(level, p, n, target, origin, a.fired);
        a.fired++;
        a.nextShot = a.elapsed + Math.max(1, PortFx.i(n, "shot_interval", 12));
    }

    @Override
    public void stop(ServerPlayer p, StoneTree.Node n) {
        Arsenal a = ARSENALS.remove(p.getUUID());
        if (a == null) return;
        ServerLevel level = p.serverLevel();
        int fragments = fragments(n);
        DustParticleOptions violet = PortFx.dust(n.stone().color(), 0.8F);
        for (int i = 0; i < Math.max(0, fragments - a.fired); i++) {
            Vec3 pt = fragmentPosition(p, i, 160, fragments);
            level.sendParticles(violet, pt.x, pt.y, pt.z, 6, 0.18D, 0.18D, 0.18D, 0.035D);
        }
        PortFx.sound(level, p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.5F, 0.72F);
    }

    // ---------------- hooks ----------------

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post e) {
        if (SHARDS.isEmpty()) return;
        for (Iterator<Shard> it = SHARDS.iterator(); it.hasNext(); ) {
            Shard s = it.next();
            if (!step(s, e.getServer().getPlayerList().getPlayer(s.owner))) it.remove();
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        ARSENALS.remove(e.getEntity().getUUID());
        SHARDS.removeIf(s -> s.owner.equals(e.getEntity().getUUID()));
    }

    @SubscribeEvent
    public static void serverStopping(ServerStoppingEvent e) { ARSENALS.clear(); SHARDS.clear(); }

    // ---------------- logic ----------------

    private static int fragments(StoneTree.Node n) { return Math.max(1, Math.min(12, PortFx.i(n, "fragments", 5))); }

    /** Same aim assist as the original: closest to the crosshair line within a narrow cone, unobstructed. */
    private static @Nullable LivingEntity aimedTarget(ServerPlayer caster, float range) {
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle().normalize();
        AABB searchBox = caster.getBoundingBox().expandTowards(look.scale(range)).inflate(4D);
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity candidate : caster.level().getEntitiesOfClass(LivingEntity.class, searchBox,
                l -> l.isAlive() && l instanceof Enemy && PortFx.canAffect(caster, l))) {
            Vec3 center = candidate.getBoundingBox().getCenter();
            Vec3 delta = center.subtract(eye);
            double projection = delta.dot(look);
            if (projection <= 0D || projection > range) continue;
            double lateralSq = Math.max(0D, delta.lengthSqr() - projection * projection);
            double tolerance = Math.max(1.25D, candidate.getBbWidth() * 0.75D) + projection * 0.035D;
            if (lateralSq > tolerance * tolerance) continue;
            HitResult obstruction = caster.level().clip(new ClipContext(eye, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            if (obstruction.getType() != HitResult.Type.MISS && obstruction.getLocation().distanceToSqr(eye) + 0.25D < delta.lengthSqr()) continue;
            double score = lateralSq * 8D + delta.lengthSqr() * 0.001D;
            if (score < bestScore) { bestScore = score; best = candidate; }
        }
        return best;
    }

    private static void launch(ServerLevel level, ServerPlayer caster, StoneTree.Node n, LivingEntity target, Vec3 origin, int index) {
        Vec3 aim = target.getBoundingBox().getCenter().subtract(origin);
        if (aim.lengthSqr() < 1.0E-6) aim = caster.getLookAngle();
        Vec3 velocity = aim.normalize().scale(PortFx.f(n, "velocity", 2.6F));
        SHARDS.add(new Shard(level, caster.getUUID(), origin, velocity, PortFx.f(n, "damage", 90F), n.stone().color()));
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, origin.x, origin.y, origin.z, 12, 0.2D, 0.2D, 0.2D, 0.12D);
        PortFx.sound(level, origin, n, "infinity:power_stone_beam1", 0.45F, 1.3F + index * 0.09F);
        PortFx.sound(level, caster.position(), SoundEvents.AMETHYST_CLUSTER_BREAK, 0.5F, 1.15F + index * 0.08F);
    }

    /** Moves one shard a tick (no gravity); false when it is gone (hit, blocked, expired, owner offline). */
    private static boolean step(Shard s, @Nullable ServerPlayer owner) {
        if (owner == null || s.age++ >= SHARD_LIFETIME) return false;
        ServerLevel level = s.level;
        Vec3 from = s.pos;
        Vec3 to = from.add(s.velocity);
        if (!level.hasChunkAt(BlockPos.containing(to))) return false;

        BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();

        LivingEntity hit = null;
        double hitDist = Double.MAX_VALUE;
        for (LivingEntity c : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(1.0D),
                l -> l.isAlive() && l instanceof Enemy && PortFx.canAffect(owner, l))) {
            Optional<Vec3> clip = c.getBoundingBox().inflate(0.3D + SHARD_HALF_SIZE).clip(from, end);
            if (clip.isPresent()) {
                double d = from.distanceToSqr(clip.get());
                if (d < hitDist) { hitDist = d; hit = c; }
            }
        }

        renderTrail(level, from, hit != null ? from.add(end.subtract(from).normalize().scale(Math.sqrt(hitDist))) : end, s.color);
        if (hit != null) {
            DamageSource src = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(DamageTypes.THROWN), null, owner);
            hit.hurt(src, Rules.amount(hit, s.damage, 0.04F, false));
            hit.knockback(SHARD_KNOCKBACK, -s.velocity.x, -s.velocity.z);
            Vec3 c = hit.getBoundingBox().getCenter();
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 10, 0.25D, 0.25D, 0.25D, 0.12D);
            level.sendParticles(PortFx.dust(s.color, 0.9F), c.x, c.y, c.z, 8, 0.25D, 0.25D, 0.25D, 0.03D);
            return false;
        }
        if (block.getType() != HitResult.Type.MISS) {
            level.sendParticles(PortFx.dust(s.color, 0.9F), end.x, end.y, end.z, 10, 0.15D, 0.15D, 0.15D, 0.03D);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z, 6, 0.15D, 0.15D, 0.15D, 0.08D);
            return false;
        }
        s.pos = to;
        return true;
    }

    private static int light(int color, int pct) {
        int r = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255;
        r = r + (255 - r) * pct / 100; g = g + (255 - g) * pct / 100; b = b + (255 - b) * pct / 100;
        return (r << 16) | (g << 8) | b;
    }

    private static void renderTrail(ServerLevel level, Vec3 from, Vec3 to, int color) {
        PortFx.line(level, from, to, PortFx.dust(color, 1.0F), 4D);
        PortFx.line(level, from, to, PortFx.dust(light(color, 80), 0.45F), 2D);
    }

    private static void renderOrbit(ServerLevel level, ServerPlayer caster, Arsenal a, int fragments, int color) {
        DustParticleOptions violet = PortFx.dust(color, 0.8F), magenta = PortFx.dust(light(color, 35), 0.7F),
                core = PortFx.dust(light(color, 75), 0.5F);
        int remaining = Math.max(0, fragments - a.fired);
        for (int i = a.fired; i < fragments; i++) {
            Vec3 pt = fragmentPosition(caster, i, a.elapsed, fragments);
            level.sendParticles(i % 2 == 0 ? magenta : violet, pt.x, pt.y, pt.z, 2, 0.045D, 0.045D, 0.045D, 0.01D);
            level.sendParticles(core, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
        }
        if (remaining > 0 && a.elapsed % 10 == 0) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, caster.getX(), caster.getEyeY(), caster.getZ(),
                    2 + remaining, 0.75D, 0.35D, 0.75D, 0.02D);
        }
    }

    private static Vec3 fragmentPosition(LivingEntity caster, int index, int elapsed, int fragments) {
        double angle = Math.PI * 2D * index / fragments + elapsed * 0.075D;
        double radius = 1.05D + (index % 2) * 0.18D;
        double height = caster.getEyeY() - 0.12D + Math.sin(angle * 2D + index) * 0.28D;
        return new Vec3(caster.getX() + Math.cos(angle) * radius, height, caster.getZ() + Math.sin(angle) * radius);
    }
}
