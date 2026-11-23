package com.infinitystones.tensura.will;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;

import java.util.UUID;

/** Safe facade over Sable: every call is a no-op / null when Sable (Create Aeronautics physics) is not installed. */
public final class Bodies {
    private static Boolean present;

    public static boolean present() {
        if (present == null) present = ModList.get() != null && ModList.get().isLoaded("sable");
        return present;
    }

    public static @Nullable UUID at(Level level, BlockPos pos) { return present() ? SableOps.bodyAt(level, pos) : null; }
    public static boolean exists(Level level, UUID id) { return present() && SableOps.exists(level, id); }
    public static @Nullable Vec3 center(Level level, UUID id, float partial) { return present() ? SableOps.center(level, id, partial) : null; }
    public static @Nullable Quaterniond orientation(Level level, UUID id) { return present() ? SableOps.orientation(level, id) : null; }
    public static double radius(Level level, UUID id) { return present() ? SableOps.radius(level, id) : 1; }
    public static Vec3 projectOut(Level level, Vec3 p) { return present() ? SableOps.projectOut(level, p) : p; }
    public static void drive(ServerLevel level, UUID id, Vec3 target, @Nullable Quaterniondc ori, double k) { if (present()) SableOps.drive(level, id, target, ori, k); }
    public static void hurl(ServerLevel level, UUID id, Vec3 v, Vec3 spin) { if (present()) SableOps.hurl(level, id, v, spin); }

    public static java.util.List<BlockPos> blocksOf(ServerLevel l, UUID id) { return present() ? SableOps.bodyBlocks(l, id) : java.util.List.of(); }
    public static @Nullable UUID assemble(ServerLevel l, BlockPos anchor, java.util.List<BlockPos> blocks, net.minecraft.nbt.CompoundTag data) {
        return present() ? SableOps.assemble(l, anchor, blocks, data) : null;
    }
    public static void remove(ServerLevel l, UUID id) { if (present()) SableOps.remove(l, id); }
    public static @Nullable net.minecraft.nbt.CompoundTag constructAt(Level l, BlockPos pos) { return present() ? SableOps.constructTag(l, pos) : null; }
    public static java.util.List<BlockPos> plotBlocks(Level l, UUID id) { return present() ? SableOps.plotBlocks(l, id) : java.util.List.of(); }
    public static @Nullable Vec3 toWorld(Level l, UUID id, Vec3 local, float partial) { return present() ? SableOps.toWorld(l, id, local, partial) : null; }
    public static @Nullable net.minecraft.nbt.CompoundTag userData(Level l, UUID id) { return present() ? SableOps.userData(l, id) : null; }
    public static java.util.List<UUID> constructsOf(ServerLevel l, UUID owner) { return present() ? SableOps.constructsOf(l, owner) : java.util.List.of(); }
    public static @Nullable net.minecraft.world.phys.AABB bounds(Level l, UUID id) { return present() ? SableOps.worldBounds(l, id) : null; }

    // ---- Create Aeronautics Physics Staff (same physics as the staff); falls back to our servo when absent ----
    private static Boolean staff;
    public static boolean staff() {
        if (staff == null) staff = present() && ModList.get().isLoaded("simulated");
        return staff;
    }
    /** Physics grip (our own Sable constraint; works with or without Aeronautics — see GripPhysics for why). */
    public static void gripDrag(ServerLevel l, UUID player, UUID body, Vec3 goalRel, Vec3 anchor, Quaterniondc ori) { if (present()) GripPhysics.drag(l, player, body, goalRel, anchor, ori); }
    public static void gripStop(ServerLevel l, UUID player) { if (present()) GripPhysics.stop(player); }
    public static boolean anyBodyIn(Level l, net.minecraft.world.phys.AABB box) { return present() && SableOps.anyBodyIn(l, box); }
    public static void staffLock(ServerLevel l, UUID body, boolean locked) { if (staff()) StaffOps.setLocked(l, body, locked); }

    private Bodies() {}
}
