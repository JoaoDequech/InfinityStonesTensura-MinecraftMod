package com.infinitystones.tensura.will;

import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffServerHandler;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

import java.util.UUID;

/**
 * Drives Sable bodies through Create Aeronautics' own Physics Staff server handler (public API of the installed mod),
 * so the Will drags, turns and locks bodies with exactly the staff's physics. Only loaded when "simulated" is present.
 */
final class StaffOps {
    /** goal = point relative to the player's eye (look × distance); anchor = grabbed point in the body's plot space. */
    static void drag(ServerLevel level, UUID player, UUID body, Vec3 goalRelative, Vec3 anchor, Quaterniondc orientation) {
        PhysicsStaffServerHandler.get(level).drag(player, body, new Vector3d(goalRelative.x, goalRelative.y, goalRelative.z),
                new Vector3d(anchor.x, anchor.y, anchor.z), orientation);
    }

    static void stop(ServerLevel level, UUID player) { PhysicsStaffServerHandler.get(level).stopDragging(player); }

    static boolean isLocked(ServerLevel level, UUID body) {
        SubLevel s = SableOps.body(level, body);
        return s != null && PhysicsStaffServerHandler.get(level).isLocked(s);
    }

    static void setLocked(ServerLevel level, UUID body, boolean locked) {
        if (isLocked(level, body) != locked) PhysicsStaffServerHandler.get(level).toggleLock(body);
    }

    private StaffOps() {}
}
