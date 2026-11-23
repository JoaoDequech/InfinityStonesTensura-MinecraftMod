package com.infinitystones.tensura.skill.ability;

import com.infinitystones.tensura.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Set;

/**
 * Shared Space Stone movement: safe landings in any dimension of the pack, cross-dimension moves for players and
 * mobs, and the portal effect at both ends. Only the destination chunk is loaded/generated.
 */
public final class Relocation {
    // Every candidate and its collision box stays within the single (0, 0) destination chunk.
    private static final int[][] CENTRAL = {{4, 4}, {6, 4}, {4, 6}, {2, 4}, {4, 2}, {2, 2}, {6, 6}};
    private static final DustParticleOptions BLUE = new DustParticleOptions(new Vector3f(0.04F, 0.27F, 1F), 1.05F);
    private static final DustParticleOptions CYAN = new DustParticleOptions(new Vector3f(0.28F, 0.95F, 1F), 0.72F);

    /** Original banishment landings: central End island, or on top of the Nether bedrock roof. */
    public static @Nullable Vec3 central(ServerLevel level, Entity e, boolean roof) {
        level.getChunk(0, 0);
        for (int[] c : CENTRAL) {
            int x = c[0], z = c[1];
            int y = Math.max(roof ? 128 : level.getMinBuildHeight() + 1, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
            Vec3 p = new Vec3(x + 0.5D, y, z + 0.5D);
            if (standable(level, e, p)) return p;
        }
        return null;
    }

    /** Default point of a dimension: End → central island, Nether → roof, Overworld → world spawn, others → near 0,0. */
    public static @Nullable Vec3 defaultPoint(ServerLevel level, Entity e) {
        if (level.dimension() == Level.END) return central(level, e, false);
        if (level.dimension() == Level.NETHER) return central(level, e, true);
        BlockPos spawn = level.dimension() == Level.OVERWORLD ? level.getSharedSpawnPos() : BlockPos.ZERO;
        return around(level, e, spawn.getX(), spawn.getZ(), Double.NaN);
    }

    /**
     * Exact coordinates. With y = NaN ("automatic") the highest safe floor is used (below the ceiling in roofed
     * dimensions). With an explicit y the spot is used if the body fits, else the nearest free space above it;
     * an explicit y over the void is allowed — that is the caster's choice.
     */
    public static @Nullable Vec3 at(ServerLevel level, Entity e, double x, double y, double z) {
        x = Math_clamp(x, -29_999_000, 29_999_000);
        z = Math_clamp(z, -29_999_000, 29_999_000);
        if (Double.isNaN(y)) return around(level, e, (int) Math.floor(x), (int) Math.floor(z), Double.NaN);
        level.getChunk(BlockPos.containing(x, 0, z));
        double cy = Math_clamp(y, level.getMinBuildHeight(), level.getMaxBuildHeight() - e.getBbHeight() - 1);
        for (int up = 0; up <= 24; up++) {
            Vec3 p = new Vec3(x, cy + up, z);
            if (p.y + e.getBbHeight() >= level.getMaxBuildHeight()) break;
            if (fits(level, e, p)) return p;
        }
        return null;
    }

    private static @Nullable Vec3 around(ServerLevel level, Entity e, int bx, int bz, double unused) {
        level.getChunk(bx >> 4, bz >> 4);
        // small spiral inside the same chunk so we never generate more than one
        int[][] offsets = {{0, 0}, {1, 0}, {0, 1}, {-1, 0}, {0, -1}, {2, 2}, {-2, 2}, {2, -2}, {-2, -2}, {3, 0}, {0, 3}, {-3, 0}, {0, -3}};
        for (int[] o : offsets) {
            int x = bx + o[0], z = bz + o[1];
            if ((x >> 4) != (bx >> 4) || (z >> 4) != (bz >> 4)) continue;
            Vec3 p = surface(level, e, x, z);
            if (p != null) return p;
        }
        // last resort: float on open water (oceans, flooded dimensions) — never lava
        if (!level.dimensionType().hasCeiling()) {
            for (int[] o : offsets) {
                int x = bx + o[0], z = bz + o[1];
                if ((x >> 4) != (bx >> 4) || (z >> 4) != (bz >> 4)) continue;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                BlockPos below = new BlockPos(x, y - 1, z);
                if (!level.getFluidState(below).is(net.minecraft.tags.FluidTags.WATER)) continue;
                Vec3 p = new Vec3(x + 0.5D, y, z + 0.5D);
                if (fits(level, e, p)) return p;
            }
        }
        return null;
    }

    private static @Nullable Vec3 surface(ServerLevel level, Entity e, int x, int z) {
        if (level.dimensionType().hasCeiling()) {
            int top = Math.min(level.getMaxBuildHeight(), level.getMinBuildHeight() + level.dimensionType().logicalHeight()) - 2;
            for (int y = top; y > level.getMinBuildHeight(); y--) {
                Vec3 p = new Vec3(x + 0.5D, y, z + 0.5D);
                if (standable(level, e, p)) return p;
            }
            return null;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        Vec3 p = new Vec3(x + 0.5D, y, z + 0.5D);
        return standable(level, e, p) ? p : null;
    }

    private static boolean standable(ServerLevel level, Entity e, Vec3 p) {
        BlockPos feet = BlockPos.containing(p);
        BlockPos floor = feet.below();
        BlockPos head = feet.above(Math.max(1, (int) Math.ceil(e.getBbHeight())));
        if (p.y < level.getMinBuildHeight() + 1 || p.y + e.getBbHeight() + 1 >= level.getMaxBuildHeight()) return false;
        if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) return false;
        if (!level.getFluidState(feet).isEmpty() || !level.getFluidState(head).isEmpty()) return false;
        return fits(level, e, p);
    }

    private static boolean fits(ServerLevel level, Entity e, Vec3 p) {
        AABB box = e.getBoundingBox().move(p.subtract(e.position())).deflate(0.001D);
        return level.getWorldBorder().isWithinBounds(box) && !level.getBlockCollisions(e, box).iterator().hasNext();
    }

    /** Moves the entity (player or mob) to the destination with the portal effect at both ends. Null on failure. */
    public static @Nullable Entity move(Entity target, ServerLevel destination, Vec3 landing, float arrivePitch) {
        if (com.infinitystones.tensura.skill.ability.extra.space.SpiritualGathering.isTrapped(target)) return null;
        if (!(target.level() instanceof ServerLevel source)) return null;
        if (destination != source && !target.canChangeDimensions(source, destination)) return null;
        Vec3 origin = target.position();
        gate(source, origin, target.getBbHeight(), 0.78F);
        Entity moved;
        if (destination == source) {
            if (target instanceof ServerPlayer sp) sp.connection.teleport(landing.x, landing.y, landing.z, sp.getYRot(), sp.getXRot());
            else target.teleportTo(landing.x, landing.y, landing.z);
            moved = target;
        } else if (target instanceof ServerPlayer player) {
            moved = player.teleportTo(destination, landing.x, landing.y, landing.z, Set.of(), player.getYRot(), player.getXRot()) ? player : null;
        } else {
            moved = target.changeDimension(new DimensionTransition(destination, landing, Vec3.ZERO,
                    target.getYRot(), target.getXRot(), DimensionTransition.DO_NOTHING));
        }
        if (moved == null) return null;
        moved.setDeltaMovement(Vec3.ZERO);
        moved.fallDistance = 0F;
        gate(destination, moved.position(), moved.getBbHeight(), arrivePitch);
        return moved;
    }

    public static void gate(ServerLevel level, Vec3 base, double height, float pitch) {
        Vec3 center = base.add(0D, Math.max(0.9D, height * 0.5D), 0D);
        double radius = Math.max(0.85D, Math.min(1.7D, height * 0.58D));
        for (int i = 0; i < 64; i++) {
            double angle = Math.PI * 2D * i / 64D;
            double x = Math.cos(angle) * radius, y = Math.sin(angle) * radius;
            level.sendParticles(i % 3 == 0 ? CYAN : BLUE, center.x + x, center.y + y, center.z, 1, 0D, 0D, 0D, 0D);
            if (i % 2 == 0) {
                level.sendParticles(CYAN, center.x, center.y + y, center.z + x, 1, 0D, 0D, 0D, 0D);
                level.sendParticles(CYAN, center.x + x * 0.65D, center.y + y * 0.65D, center.z + 0.16D, 1, 0D, 0D, 0D, 0D);
            }
        }
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, 36, radius * 0.45D, radius * 0.6D, 0.25D, 0.08D);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z, 16, radius * 0.6D, radius * 0.6D, 0.25D, 0.06D);
        level.playSound(null, base.x, base.y, base.z, ModSounds.SPACE_STONE_PORTAL.get(), SoundSource.PLAYERS, 0.7F, pitch);
        level.playSound(null, base.x, base.y, base.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6F, pitch + 0.2F);
        level.playSound(null, base.x, base.y, base.z, SoundEvents.RESPAWN_ANCHOR_DEPLETE, SoundSource.PLAYERS, 0.5F, pitch);
    }

    private static double Math_clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    private Relocation() {}
}
