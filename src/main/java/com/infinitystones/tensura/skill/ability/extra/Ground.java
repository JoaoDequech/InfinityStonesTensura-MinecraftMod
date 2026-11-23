package com.infinitystones.tensura.skill.ability.extra;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Small world queries: floor below a point, safe standing spot. */
public final class Ground {
    /** Top of the first solid block at or below v (max depth blocks); v itself when nothing is found. */
    public static Vec3 below(ServerLevel l, Vec3 v, int depth) {
        BlockPos.MutableBlockPos m = BlockPos.containing(v).mutable();
        for (int i = 0; i <= depth && m.getY() > l.getMinBuildHeight(); i++) {
            BlockState s = l.getBlockState(m);
            if (!s.getCollisionShape(l, m).isEmpty()) return new Vec3(v.x, m.getY() + s.getCollisionShape(l, m).max(net.minecraft.core.Direction.Axis.Y), v.z);
            m.move(0, -1, 0);
        }
        return v;
    }

    /** The surface block under v (for copying its look), or null. */
    public static @Nullable BlockPos floorBlock(ServerLevel l, Vec3 v, int depth) {
        BlockPos.MutableBlockPos m = BlockPos.containing(v).mutable();
        for (int i = 0; i <= depth && m.getY() > l.getMinBuildHeight(); i++) {
            if (!l.getBlockState(m).getCollisionShape(l, m).isEmpty()) return m.immutable();
            m.move(0, -1, 0);
        }
        return null;
    }

    /** Is there room for the entity's box at feet position v? */
    public static boolean fits(ServerLevel l, Entity e, Vec3 v) {
        return l.noCollision(e, e.getDimensions(e.getPose()).makeBoundingBox(v).deflate(1.0E-4));
    }

    /** Last spot along a→b where the entity fits standing (walks back from b); null if none. */
    public static @Nullable Vec3 safeAlong(ServerLevel l, Entity e, Vec3 a, Vec3 b) {
        double len = a.distanceTo(b);
        for (double d = len; d >= 0; d -= 0.5) {
            Vec3 v = a.lerp(b, len == 0 ? 0 : d / len);
            Vec3 floor = below(l, v, 3);
            if (fits(l, e, floor)) return floor;
            if (fits(l, e, v)) return v;
        }
        return null;
    }

    private Ground() {}
}
