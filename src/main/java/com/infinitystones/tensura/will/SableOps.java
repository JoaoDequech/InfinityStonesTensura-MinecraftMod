package com.infinitystones.tensura.will;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.UUID;

/**
 * Everything that touches Sable (Create Aeronautics physics bodies). Only loaded when the "sable" mod is present —
 * callers go through {@link Bodies}, which checks that first. Bodies are addressed by their Sable unique id.
 */
final class SableOps {
    private static final double MAX_SPEED = 60D;       // m/s
    private static final double MAX_SPIN = 8D;         // rad/s

    static @Nullable UUID bodyAt(Level level, BlockPos pos) {
        SubLevelAccess s = SableCompanion.INSTANCE.getContaining(level, pos);
        return s instanceof SubLevel sub ? sub.getUniqueId() : null;
    }

    static @Nullable SubLevel body(Level level, UUID id) {
        SubLevelContainer c = SubLevelContainer.getContainer(level);
        if (c == null) return null;
        SubLevel s = c.getSubLevel(id);
        return s == null || s.isRemoved() ? null : s;
    }

    static boolean exists(Level level, UUID id) { return body(level, id) != null; }

    /** World-space centre (interpolated on the client with partial ticks). */
    static @Nullable Vec3 center(Level level, UUID id, float partial) {
        SubLevel s = body(level, id);
        if (s == null) return null;
        Vector3dc now = s.logicalPose().position();
        Pose3dc last = s.lastPose();
        if (last == null || partial >= 1F) return new Vec3(now.x(), now.y(), now.z());
        Vector3dc then = last.position();
        return new Vec3(then.x() + (now.x() - then.x()) * partial, then.y() + (now.y() - then.y()) * partial, then.z() + (now.z() - then.z()) * partial);
    }

    static @Nullable Quaterniond orientation(Level level, UUID id) {
        SubLevel s = body(level, id);
        return s == null ? null : new Quaterniond(s.logicalPose().orientation());
    }

    /** Rough size of the body (half diagonal of its world bounds) — used for beam end and hold distance. */
    static double radius(Level level, UUID id) {
        SubLevel s = body(level, id);
        if (s == null) return 1;
        var b = s.boundingBox();
        double dx = b.maxX() - b.minX(), dy = b.maxY() - b.minY(), dz = b.maxZ() - b.minZ();
        return Math.max(0.8, Math.sqrt(dx * dx + dy * dy + dz * dz) / 2);
    }

    /** Points in a sub-level plot are far away; this maps them back to where the body is in the world. */
    static Vec3 projectOut(Level level, Vec3 p) { return SableCompanion.INSTANCE.projectOutOfSubLevel(level, p); }

    /**
     * Velocity-servo the body towards a point (and optionally an orientation). Called every server tick while held.
     * Mass is ignored on purpose: the Will has no weight limit.
     */
    static void drive(ServerLevel level, UUID id, Vec3 target, @Nullable Quaterniondc targetOri, double stiffness) {
        if (!(body(level, id) instanceof ServerSubLevel s)) return;
        RigidBodyHandle h = RigidBodyHandle.of(s);
        if (h == null || !h.isValid()) return;
        Vector3dc c = s.logicalPose().position();
        Vector3d desired = new Vector3d(target.x - c.x(), target.y - c.y(), target.z - c.z()).mul(stiffness);
        if (desired.length() > MAX_SPEED) desired.normalize(MAX_SPEED);
        Vector3d v = h.getLinearVelocity(new Vector3d());
        Vector3d dv = desired.sub(v).mul(0.55);
        dv.y += 0.49; // counter one tick of gravity so a held body doesn't sag
        Vector3d w = h.getAngularVelocity(new Vector3d());
        Vector3d wDesired = new Vector3d();
        if (targetOri != null) {
            Quaterniond err = new Quaterniond(targetOri).mul(new Quaterniond(s.logicalPose().orientation()).conjugate());
            if (err.w < 0) err.set(-err.x, -err.y, -err.z, -err.w);
            double angle = 2 * Math.acos(Math.min(1, err.w));
            double sin = Math.sqrt(Math.max(1e-9, 1 - err.w * err.w));
            if (angle > 1e-3) wDesired.set(err.x / sin, err.y / sin, err.z / sin).mul(angle * 5);
            if (wDesired.length() > MAX_SPIN) wDesired.normalize(MAX_SPIN);
        }
        Vector3d dw = wDesired.sub(w).mul(0.5);
        h.addLinearAndAngularVelocity(dv, dw);
    }

    /** Hurl: replace the body's velocity with the throw velocity plus a little tumble. */
    static void hurl(ServerLevel level, UUID id, Vec3 velocity, Vec3 spin) {
        if (!(body(level, id) instanceof ServerSubLevel s)) return;
        RigidBodyHandle h = RigidBodyHandle.of(s);
        if (h == null || !h.isValid()) return;
        Vector3d v = h.getLinearVelocity(new Vector3d());
        Vector3d w = h.getAngularVelocity(new Vector3d());
        h.addLinearAndAngularVelocity(new Vector3d(velocity.x, velocity.y, velocity.z).sub(v), new Vector3d(spin.x, spin.y, spin.z).sub(w));
    }

    // ---------------- constructs ----------------

    /** Every non-air block of a body (positions are in its plot, i.e. real positions of the level). */
    static java.util.List<BlockPos> bodyBlocks(ServerLevel level, UUID id) { return plotBlocks(level, id); }

    /** Turns placed blocks into a physics body (Create blocks keep working, like any Aeronautics build). */
    static @Nullable UUID assemble(ServerLevel level, BlockPos anchor, java.util.List<BlockPos> blocks, net.minecraft.nbt.CompoundTag userData) {
        ServerSubLevel s = dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(level, anchor, blocks,
                dev.ryanhcode.sable.companion.math.BoundingBox3i.from(blocks));
        if (s == null) return null;
        s.setUserDataTag(userData);
        s.setName(userData.getString("name"));
        return s.getUniqueId();
    }

    static void remove(ServerLevel level, UUID id) {
        SubLevel s = body(level, id);
        if (s != null) SubLevelContainer.getContainer(level).removeSubLevel(s, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
    }

    /** Our constructs carry {infinity_construct:1b, owner, name} in their Sable user data (saved with the body). */
    static @Nullable net.minecraft.nbt.CompoundTag constructTag(Level level, BlockPos pos) {
        SubLevelAccess a = SableCompanion.INSTANCE.getContaining(level, pos);
        if (!(a instanceof ServerSubLevel s)) return null;
        net.minecraft.nbt.CompoundTag t = s.getUserDataTag();
        return t != null && t.getBoolean("infinity_construct") ? t : null;
    }

    static @Nullable net.minecraft.nbt.CompoundTag userData(Level level, UUID id) {
        return body(level, id) instanceof ServerSubLevel s ? s.getUserDataTag() : null;
    }

    /** Ids of every construct body owned by a player in this level. */
    static java.util.List<UUID> constructsOf(ServerLevel level, UUID owner) {
        java.util.List<UUID> out = new java.util.ArrayList<>();
        for (ServerSubLevel s : SubLevelContainer.getContainer(level).getAllSubLevels()) {
            net.minecraft.nbt.CompoundTag t = s.getUserDataTag();
            if (t != null && t.getBoolean("infinity_construct") && t.hasUUID("owner") && owner.equals(t.getUUID("owner"))) out.add(s.getUniqueId());
        }
        return out;
    }

    /** World-space bounds of a body (for the hard-light shimmer and dissolve effects). */
    static @Nullable net.minecraft.world.phys.AABB worldBounds(Level level, UUID id) {
        SubLevel s = body(level, id);
        if (s == null) return null;
        var b = s.boundingBox();
        return new net.minecraft.world.phys.AABB(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
    }

    /** Non-air blocks of a body in plot coordinates (works on the client level too). */
    /** Non-air blocks of a body in plot coordinates, read straight from the plot's own chunks (works on the client too). */
    static java.util.List<BlockPos> plotBlocks(Level level, UUID id) {
        java.util.List<BlockPos> out = new java.util.ArrayList<>();
        SubLevel s = body(level, id);
        if (s == null) return out;
        for (var holder : s.getPlot().getLoadedChunks()) {
            net.minecraft.world.level.chunk.LevelChunk chunk = holder.getChunk();
            if (chunk == null) continue;
            var sections = chunk.getSections();
            int bx = chunk.getPos().getMinBlockX(), bz = chunk.getPos().getMinBlockZ();
            for (int si = 0; si < sections.length; si++) {
                var sec = sections[si];
                if (sec == null || sec.hasOnlyAir()) continue;
                int by = chunk.getSectionYFromSectionIndex(si) << 4;
                for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                    if (sec.getBlockState(x, y, z).isAir()) continue;
                    out.add(new BlockPos(bx + x, by + y, bz + z));
                    if (out.size() > 4096) return out;
                }
            }
        }
        return out;
    }

    /** Plot-space point → world, using the body's pose interpolated by partial ticks. */
    static @Nullable Vec3 toWorld(Level level, UUID id, Vec3 local, float partial) {
        SubLevel s = body(level, id);
        if (s == null) return null;
        Pose3dc now = s.logicalPose(), last = s.lastPose();
        Vec3 a = now.transformPosition(local);
        if (last == null || partial >= 1F) return a;
        Vec3 b = last.transformPosition(local);
        return b.lerp(a, partial);
    }

    /** True when any physics body's world bounds touch the box (so summons never overlap constructs or ships). */
    static boolean anyBodyIn(Level level, net.minecraft.world.phys.AABB box) {
        SubLevelContainer c = SubLevelContainer.getContainer(level);
        if (c == null) return false;
        for (var o : c.getAllSubLevels()) {
            if (!(o instanceof SubLevel s) || s.isRemoved()) continue;
            var b = s.boundingBox();
            if (b.maxX() > box.minX && b.minX() < box.maxX && b.maxY() > box.minY && b.minY() < box.maxY && b.maxZ() > box.minZ && b.minZ() < box.maxZ) return true;
        }
        return false;
    }

    private SableOps() {}
}
