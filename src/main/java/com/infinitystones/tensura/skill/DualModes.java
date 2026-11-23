package com.infinitystones.tensura.skill;

import java.util.Set;

/**
 * Extra wheel inputs per power.
 * <ul>
 *   <li>Dual: left click = primary, right click = alternate (Shift + click also = alternate, like the original).</li>
 *   <li>Destination: middle click (scroll-wheel button) opens the destination menu â€” any dimension of the pack,
 *       its default point or exact coordinates. Labels: infinity.wheel.dual.&lt;node&gt;.</li>
 * </ul>
 */
public final class DualModes {
    private static final Set<String> DUAL = Set.of(
            "space.spatial_banishment",      // left: centre of the End  Â· right: above the Nether roof
            "reality.reality_confiscation",  // left: open their inventory Â· right: make them drop everything
            "reality.will_construct",        // left: grip mode on/off Â· right: construct menu
            // new abilities (skill/ability/extra, notes/new-abilities.md)
            "mind.phantasm", "mind.suggestion", "mind.psionic_overload_aim",
            "power.cataclysm_forge", "power.debris_orbit", "power.overcharge", "power.seismic_wave",
            "space.area_heist", "space.scene_anchor", "space.fold_distance", "space.event_horizon",
            "space.space_mirror", "space.abyss_watch",
            "reality.reality_shatter", "reality.reality_pet", "reality.local_decree", "reality.mirage", "reality.greed",
            "soul.soul_possession", "soul.soul_capture",
            "soul.spectral_army", "soul.reaper", "soul.soul_link", "soul.return_pact", "time.past_echo",
            "reality.paint_world", "reality.pocket_card", "reality.creation_chest", "soul.lost_lantern", "time.rewind_me", "time.time_clones",
            "time.climate_zone", "time.stasis_bubble", "time.time_mark", "time.lapse_field", "time.eras",
            // round 3 (Mind / Power / Space)
            "mind.puppet_strings", "mind.astral_swap", "power.gravity_hammer", "power.prism_barrage", "space.portal_gun",
            // Infinity Gauntlet powers
            "soul.gauntlet_return", "power.gauntlet_execution", "mind.gauntlet_dominion", "reality.gauntlet_transmute"
    );
    /** Destination powers that move the aimed target (the rest move the caster). */
    private static final Set<String> DESTINATION_TARGET = Set.of("space.spatial_banishment");
    private static final Set<String> DESTINATION_SELF = Set.of("space.teleport", "space.teleport_gui");
    /** Powers whose left click itself opens the destination menu (the original "Teleport GUI"). */
    private static final Set<String> MENU_ON_LEFT = Set.of("space.teleport_gui");

    public static boolean isDual(String modeId) { return DUAL.contains(modeId); }
    public static boolean hasDestination(String modeId) { return DESTINATION_TARGET.contains(modeId) || DESTINATION_SELF.contains(modeId); }
    public static boolean movesTarget(String modeId) { return DESTINATION_TARGET.contains(modeId); }
    public static boolean menuOnLeft(String modeId) { return MENU_ON_LEFT.contains(modeId); }

    private DualModes() {}
}
