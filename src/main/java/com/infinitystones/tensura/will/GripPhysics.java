package com.infinitystones.tensura.will;

import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.FreeConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Our own physics grip on Sable bodies, written against Sable's public constraint API: every physics step a free
 * constraint ties the grabbed point of the body to a point in front of the holder's eyes, with motors pulling it there
 * (acceleration-based, so mass never matters — the Will has no weight limit). Letting go just removes the constraint,
 * so the body keeps all its momentum (flings work). Unlike the Aeronautics Physics Staff handler (which drops a drag
 * session every tick when the player isn't holding the staff item) this needs no item. Only loaded with Sable present.
 */
final class GripPhysics {
    private static final double LIN_STIFF = 2650, LIN_DAMP = 125, ANG_STIFF = 10000, ANG_DAMP = 850;

    private static final class Session {
        final UUID body;
        final ServerLevel level;
        final Vector3d anchor = new Vector3d(), goalRel = new Vector3d(), localGoal = new Vector3d();
        final Quaterniond ori = new Quaterniond();
        PhysicsConstraintHandle constraint;
        Session(ServerLevel level, UUID body) { this.level = level; this.body = body; }
        void detach() { if (constraint != null) { try { constraint.remove(); } catch (Throwable ignored) { } constraint = null; } }
    }

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static boolean registered;

    private static void ensureRegistered() {
        if (registered) return;
        registered = true;
        SableEventPlatform.INSTANCE.onPhysicsTick(GripPhysics::physicsTick);
    }

    /** goalRel = point relative to the holder's eyes; anchor = grabbed point in the body's plot space. */
    static void drag(ServerLevel level, UUID player, UUID body, Vec3 goalRel, Vec3 anchor, Quaterniondc ori) {
        ensureRegistered();
        Session s = SESSIONS.get(player);
        if (s == null || !s.body.equals(body) || s.level != level) {
            if (s != null) s.detach();
            s = new Session(level, body);
            SESSIONS.put(player, s);
        }
        s.goalRel.set(goalRel.x, goalRel.y, goalRel.z);
        s.anchor.set(anchor.x, anchor.y, anchor.z);
        s.ori.set(ori);
    }

    static void stop(UUID player) {
        Session s = SESSIONS.remove(player);
        if (s != null) s.detach();
    }

    static boolean holding(UUID player) { return SESSIONS.containsKey(player); }

    private static void physicsTick(SubLevelPhysicsSystem system, double timeStep) {
        if (SESSIONS.isEmpty()) return;
        ServerLevel level = system.getLevel();
        for (Map.Entry<UUID, Session> en : SESSIONS.entrySet()) {
            Session s = en.getValue();
            if (s.level != level) continue;
            s.detach();
            SubLevel sub = SableOps.body(level, s.body);
            Player p = level.getPlayerByUUID(en.getKey());
            if (!(sub instanceof ServerSubLevel body) || p == null) continue;
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null) continue;
            s.constraint = system.getPipeline().addConstraint(null, body, new FreeConstraintConfiguration(new Vector3d(), s.anchor, s.ori));
            if (s.constraint == null) continue;
            for (ConstraintJointAxis axis : ConstraintJointAxis.ANGULAR) s.constraint.setMotor(axis, 0, ANG_STIFF, ANG_DAMP, false, 0);
            double pt = system.getPartialPhysicsTick();
            s.localGoal.set(s.goalRel).add(Mth.lerp(pt, p.xOld, p.getX()), Mth.lerp(pt, p.yOld, p.getY()) + p.getEyeHeight(), Mth.lerp(pt, p.zOld, p.getZ()));
            s.ori.transformInverse(s.localGoal);
            s.constraint.setMotor(ConstraintJointAxis.LINEAR_X, s.localGoal.x, LIN_STIFF, LIN_DAMP, false, 0);
            s.constraint.setMotor(ConstraintJointAxis.LINEAR_Y, s.localGoal.y, LIN_STIFF, LIN_DAMP, false, 0);
            s.constraint.setMotor(ConstraintJointAxis.LINEAR_Z, s.localGoal.z, LIN_STIFF, LIN_DAMP, false, 0);
        }
    }

    private GripPhysics() {}
}
