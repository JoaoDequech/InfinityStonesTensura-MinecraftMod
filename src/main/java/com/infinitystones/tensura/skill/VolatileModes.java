package com.infinitystones.tensura.skill;

import java.util.Set;

/**
 * The one list of switches that never survive a session. Anything that changes the player's body, physics, visibility,
 * camera or rendering (or the world around them) is turned OFF, with its normal stop(), when the player leaves the
 * server and when the server stops (before the player is saved), and once more on login in case a crash left it stored
 * as ON. While the player is in the world these never turn off by themselves.
 *
 * <p>Simple stat buffs (saturation, regeneration, night vision, water breathing, speed, step assist, fall immunity,
 * effect immunity, knockback resistance, impower/health, sovereignty bonuses...) are deliberately NOT here: they keep
 * their switch across sessions. Add a mode id ("stone.node") below to make a power volatile.</p>
 */
public final class VolatileModes {
    private static final Set<String> MODES = Set.of(
            // Mind
            "mind.intangibility", "mind.flight-dummy", "mind.flight_toggle", "mind.mind_palace", "mind.psychic_avatar",
            // Power
            "power.size_toggle", "power.radiation_texture_toggle",
            // Reality
            "reality.size_toggle", "reality.invisibility", "reality.broken_mode", "reality.gauntlet_destroy",
            // Soul
            "soul.glow", "soul.soul_sight",
            // Space
            "space.force_field", "space.pocket_orbit",
            // Time (and its hidden water-walking helper, which follows Time Freeze)
            "time.time_freeze", "time.reset_time_speed", "time.projectile_stop", "time.daylight_cycle_toggle",
            "time.screen_toggle", "time.water_walking");

    public static boolean is(String modeId) { return MODES.contains(modeId); }

    public static boolean is(StoneTree.Node node) { return MODES.contains(node.modeId()); }

    private VolatileModes() {}
}
