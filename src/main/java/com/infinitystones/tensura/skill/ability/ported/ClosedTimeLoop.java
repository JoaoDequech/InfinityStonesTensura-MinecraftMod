package com.infinitystones.tensura.skill.ability.ported;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.StoneTree;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Port of palladium:closed_time_loop (Time, 122-tick activation). Anchors up to three visible hostile monsters within
 * 16 blocks at their current positions. At 2 s, 4 s and 6 s each anchored target is pulled back to its anchor (bosses
 * and riders are not moved, only hurt) and takes 60 damage of type {@code infinity:closed_time_loop}. Only positions
 * are replayed; dead entities are never recreated.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class ClosedTimeLoop implements Ported {
    private static final ResourceKey<DamageType> LOOP_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, InfinityStones.id("closed_time_loop"));
    private static final Map<UUID, Loop> LOOPS = new HashMap<>();

    private record Anchor(UUID target, Vec3 position, boolean boss) {}
    private static final class Loop {
        final ServerLevel level; final ServerPlayer owner; final List<Anchor> anchors; final long start; final int rgb;
        long nextPulse = 40L;
        Loop(ServerLevel level, ServerPlayer owner, List<Anchor> anchors, long start, int rgb) {
            this.level = level; this.owner = owner; this.anchors = anchors; this.start = start; this.rgb = rgb;
        }
    }

    public ClosedTimeLoop() {}

    @Override
    public void start(ServerPlayer p, StoneTree.Node n) {
        ServerLevel level = p.serverLevel();
        List<Anchor> anchors = level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(16D),
                        t -> t instanceof Enemy && t.isAlive() && !t.isSpectator() && !t.isPassenger() && !t.isVehicle()
                                && !p.isAlliedTo(t) && !t.isAlliedTo(p) && p.distanceToSqr(t) <= 16D * 16D
                                && PortFx.canAffect(p, t) && p.hasLineOfSight(t) && (boss(t) || safe(level, t, t.position())))
                .stream().sorted(Comparator.comparingDouble(p::distanceToSqr)).limit(3)
                .map(t -> new Anchor(t.getUUID(), t.position(), boss(t))).toList();
        if (anchors.isEmpty()) return;
        Loop loop = new Loop(level, p, anchors, level.getGameTime(), n.stone().color() & 0xFFFFFF);
        LOOPS.put(p.getUUID(), loop);
        render(loop, false);
        PortFx.sound(level, p.getEyePosition(), n, "infinity:time_stone", 0.7F, 0.8F);
    }

    @Override
    public void tick(ServerPlayer p, StoneTree.Node n, int ticks) {
        Loop loop = LOOPS.get(p.getUUID());
        if (loop == null) return;
        if (!valid(loop)) { LOOPS.remove(p.getUUID()); return; }
        long elapsed = loop.level.getGameTime() - loop.start;
        if (elapsed >= loop.nextPulse) {
            pulse(loop);
            loop.nextPulse += 40L;
            if (loop.nextPulse > 120L) { LOOPS.remove(p.getUUID()); return; }
        }
        if (ticks % 5 == 0) render(loop, loop.nextPulse - elapsed <= 10L);
    }

    @Override
    public void stop(ServerPlayer p, StoneTree.Node n) { LOOPS.remove(p.getUUID()); }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { LOOPS.remove(e.getEntity().getUUID()); }

    private static boolean boss(LivingEntity t) { return t instanceof WitherBoss || t instanceof EnderDragon; }

    private static boolean valid(Loop loop) {
        return loop.owner.isAlive() && !loop.owner.isRemoved() && loop.owner.level() == loop.level
                && !loop.owner.hasDisconnected() && loop.level.getGameTime() - loop.start <= 122L
                && Stone.TIME.isHeldBy(loop.owner);
    }

    private static LivingEntity current(Loop loop, Anchor anchor) {
        Entity e = loop.level.getEntity(anchor.target);
        if (!(e instanceof LivingEntity t) || !t.isAlive() || t.isSpectator() || !(t instanceof Enemy)
                || loop.owner.isAlliedTo(t) || t.isAlliedTo(loop.owner) || loop.owner.distanceToSqr(t) > 64D * 64D
                || !PortFx.canAffect(loop.owner, t)) return null;
        return t;
    }

    private static boolean safe(ServerLevel level, LivingEntity t, Vec3 dest) {
        AABB box = t.getBoundingBox().move(dest.subtract(t.position())).deflate(0.001D);
        if (box.minY < level.getMinBuildHeight() + 1 || box.maxY >= level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(box)) return false;
        for (double x : new double[]{box.minX, box.maxX}) for (double z : new double[]{box.minZ, box.maxZ})
            if (!level.hasChunkAt(BlockPos.containing(x, dest.y, z))) return false;
        BlockPos feet = BlockPos.containing(dest), floor = feet.below();
        return level.getFluidState(feet).isEmpty() && level.getFluidState(floor).isEmpty()
                && !level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()
                && !level.getBlockCollisions(t, box).iterator().hasNext();
    }

    private static Holder<DamageType> damageType(ServerLevel level) {
        var reg = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        return reg.getHolder(LOOP_DAMAGE).<Holder<DamageType>>map(h -> h).orElseGet(() -> reg.getHolderOrThrow(DamageTypes.MAGIC));
    }

    private static void pulse(Loop loop) {
        Holder<DamageType> type = damageType(loop.level);
        for (Anchor anchor : loop.anchors) {
            LivingEntity t = current(loop, anchor);
            if (t == null) continue;
            if (!anchor.boss && !t.isPassenger() && !t.isVehicle() && safe(loop.level, t, anchor.position)) {
                Vec3 prev = t.getBoundingBox().getCenter();
                t.teleportTo(anchor.position.x, anchor.position.y, anchor.position.z);
                t.setDeltaMovement(Vec3.ZERO);
                t.fallDistance = 0F;
                if (t instanceof Mob mob) mob.getNavigation().stop();
                loop.level.sendParticles(ParticleTypes.REVERSE_PORTAL, prev.x, prev.y, prev.z, 12, 0.35D, 0.5D, 0.35D, 0.06D);
            }
            t.hurt(new DamageSource(type, null, loop.owner), Rules.amount(t, 60F, 0.08F, false));
            PortFx.sound(loop.level, t.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.7F, 0.8F + loop.nextPulse / 150F);
        }
        render(loop, true);
    }

    private static void render(Loop loop, boolean imminent) {
        DustParticleOptions green = PortFx.dust(loop.rgb, 0.75F), gold = PortFx.dust(0xBFFF66, 0.5F);
        for (Anchor anchor : loop.anchors) {
            LivingEntity t = current(loop, anchor);
            if (t == null) continue;
            double r = Math.max(0.7D, Math.min(2.5D, t.getBbWidth() * 0.7D));
            for (int ring = 0; ring < 3; ring++) for (int i = 0; i < 20; i++) {
                double a = i * Math.PI / 10D + loop.level.getGameTime() * 0.04D * (ring == 1 ? -1 : 1);
                loop.level.sendParticles(imminent || i % 5 == 0 ? gold : green, t.getX() + Math.cos(a) * r,
                        t.getY() + 0.25D + ring * t.getBbHeight() * 0.45D, t.getZ() + Math.sin(a) * r, 1, 0D, 0D, 0D, 0D);
            }
            if (imminent) loop.level.sendParticles(ParticleTypes.END_ROD, t.getX(), t.getEyeY() + 0.5D, t.getZ(), 2, 0.2D, 0.1D, 0.2D, 0D);
        }
    }
}
