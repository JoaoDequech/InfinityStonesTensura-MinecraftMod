package com.infinitystones.tensura.skill.ability.extra;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Server-side movement helpers for entities carried by an ability (players get the motion packet through hurtMarked). */
public final class Move {
    /** Sets the velocity and syncs it. */
    public static void set(Entity e, Vec3 v) {
        e.setDeltaMovement(v);
        e.hurtMarked = true;
    }

    /** Pulls e towards target: velocity = offset * gain, at most max blocks per tick. */
    public static void steer(Entity e, Vec3 target, double gain, double max) {
        Vec3 d = target.subtract(e.getBoundingBox().getCenter()).scale(gain);
        double len = d.length();
        if (len > max) d = d.scale(max / len);
        set(e, d);
        e.fallDistance = 0;
    }

    /** Heading → yaw (degrees, Minecraft convention). */
    public static float yaw(Vec3 d) { return (float) (Math.atan2(-d.x, d.z) * 180.0 / Math.PI); }

    public static float pitch(Vec3 d) { return (float) -(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * 180.0 / Math.PI); }

    private Move() {}
}
