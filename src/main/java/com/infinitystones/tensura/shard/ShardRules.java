package com.infinitystones.tensura.shard;

import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.StoneTree;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Which powers a mini gem can carry, and what using one costs (cooldown and magicule). */
public final class ShardRules {
    public static final int MAX_PER_CREATOR = 6;
    public static final int MIN_SECONDS = 60, MAX_SECONDS = 2 * 60 * 60;

    /**
     * Excluded on purpose: switches that leave persistent state behind (size, time speed, block duplication, the
     * soulworld pair, black hole pair) and powers whose code also asks for a second stone.
     */
    private static final Set<String> EXCLUDED = Set.of(
            "mind.passive", "mind.un-passive", "mind.lucid_dream",
            "power.radiation_texture_toggle", "power.size_toggle",
            "reality.size_toggle", "reality.save_block_on", "reality.Save Block Off", "reality.reincarnate_pet",
            "soul.enter_soulworld", "soul.exit_soulworld",
            "space.black_hole_on", "space.black_hole_off",
            "time.reset_time_speed", "time.fast_forward", "time.restoration", "time.alternate_timeline", "time.past_echo");

    private static final int DEFAULT_COOLDOWN = 20;
    private static final Map<String, Integer> COOLDOWN = Map.ofEntries(
            Map.entry("mind.aim", 8), Map.entry("mind.power_blast_head", 10), Map.entry("mind.psionic_overload_aim", 30),
            Map.entry("mind.synaptic_cascade", 25), Map.entry("mind.psychic_dominion", 60), Map.entry("mind.terror_projection", 40),
            Map.entry("mind.mental_bastion", 45), Map.entry("mind.phantasm", 30), Map.entry("mind.suggestion", 90),
            Map.entry("mind.puppet_strings", 90), Map.entry("mind.telepathic_link", 15), Map.entry("mind.borrowed_eyes", 30),
            Map.entry("power.power_blast_aim", 10), Map.entry("power.impower", 15), Map.entry("power.explosion2", 60),
            Map.entry("power.titanic_charge", 30), Map.entry("power.tectonic_rend", 40), Map.entry("power.kinetic_reservoir", 25),
            Map.entry("power.cosmic_arsenal", 75), Map.entry("power.celestial_annihilation_aim", 120), Map.entry("power.cataclysm_forge", 90),
            Map.entry("power.debris_orbit", 30), Map.entry("power.overcharge", 45), Map.entry("power.seismic_wave", 25),
            Map.entry("power.meteor_gate", 90), Map.entry("power.convergence", 60), Map.entry("power.gravity_hammer", 40),
            Map.entry("power.prism_barrage", 40), Map.entry("power.sky_fist", 60), Map.entry("power.sculptor_hands", 20),
            Map.entry("power.time_bomb", 45),
            Map.entry("reality.effects", 10), Map.entry("reality.weather_wheel", 60), Map.entry("reality.aether_transmutation", 20),
            Map.entry("reality.reality_shatter", 90), Map.entry("reality.crimson_lattice", 50), Map.entry("reality.harmless_form", 30),
            Map.entry("reality.reality_confiscation", 90), Map.entry("reality.will_construct", 15), Map.entry("reality.reality_pet", 60),
            Map.entry("reality.local_decree", 60), Map.entry("reality.mirage", 25), Map.entry("reality.greed", 45),
            Map.entry("reality.paint_world", 20), Map.entry("reality.pocket_card", 30), Map.entry("reality.creation_chest", 30),
            Map.entry("reality.painted_sky", 20), Map.entry("reality.genesis", 120), Map.entry("reality.enchanter", 30),
            Map.entry("soul.glow", 5), Map.entry("soul.summon_zombie", 30), Map.entry("soul.essence_siphon", 30),
            Map.entry("soul.spirit_legion", 90), Map.entry("soul.soul_sanctuary", 60), Map.entry("soul.reaper_brand", 40),
            Map.entry("soul.soul_severance", 60), Map.entry("soul.soul_capture", 45), Map.entry("soul.soul_possession", 90),
            Map.entry("soul.spectral_army", 90), Map.entry("soul.reaper", 60), Map.entry("soul.soul_link", 30),
            Map.entry("soul.return_pact", 60), Map.entry("soul.lost_lantern", 15), Map.entry("soul.wisp_guide", 10),
            Map.entry("soul.essence_harvest", 60),
            Map.entry("space.teleport", 8), Map.entry("space.grab_entity2", 12), Map.entry("space.teleport_gui", 30),
            Map.entry("space.spatial_exchange", 25), Map.entry("space.orbital_judgement", 90), Map.entry("space.spatial_reflection", 40),
            Map.entry("space.spatial_banishment", 90), Map.entry("space.event_horizon", 60), Map.entry("space.area_heist", 90),
            Map.entry("space.scene_anchor", 20), Map.entry("space.fold_distance", 20), Map.entry("space.space_mirror", 40),
            Map.entry("space.abyss_watch", 60), Map.entry("space.soul_rift", 60), Map.entry("space.portal_gun", 20),
            Map.entry("space.spiritual_gathering", 120), Map.entry("space.star_rails", 30), Map.entry("space.cosmic_scale", 60),
            Map.entry("space.deja_vu", 45),
            Map.entry("time.personal_rewind", 60), Map.entry("time.temporal_echo", 45), Map.entry("time.closed_time_loop", 90),
            Map.entry("time.season_in_a_moment", 90), Map.entry("time.temporal_cataclysm", 120), Map.entry("time.climate_zone", 60),
            Map.entry("time.stasis_bubble", 60), Map.entry("time.time_mark", 15), Map.entry("time.lapse_field", 60),
            Map.entry("time.eras", 60), Map.entry("time.rewind_me", 60), Map.entry("time.time_clones", 90),
            Map.entry("time.time_race", 60), Map.entry("time.temporal_lag", 60));

    /** A power of the stone's own tree that a mini gem may hold (a press or channel power that needs no other stone). */
    public static boolean shardable(StoneTree.Node n) {
        if (n == null || EXCLUDED.contains(n.modeId()) || !n.inBar()) return false;
        if (n.activation() != StoneTree.Activation.PRESS && n.activation() != StoneTree.Activation.HOLD) return false;
        if (com.infinitystones.tensura.skill.GauntletStones.isGauntletPower(n)) return false;
        for (String r : n.requiresPowers()) {
            Stone other = Stone.byOriginalPower(r);
            if (other != null && other != n.stone()) return false;
        }
        return true;
    }

    /** Shardable powers of one stone, in wheel order (the order the menu lists them). */
    public static List<StoneTree.Node> shardableNodes(Stone stone) {
        List<StoneTree.Node> out = new ArrayList<>();
        for (StoneTree.Node n : StoneTree.of(stone).wheelNodes) if (shardable(n)) out.add(n);
        return out;
    }

    public static int cooldownSeconds(String modeId) { return COOLDOWN.getOrDefault(modeId, DEFAULT_COOLDOWN); }

    /** Magicule per use: a bit above the normal 1.0 and growing with the cooldown (2 to 6). */
    public static double magiculeCost(String modeId) { return Math.min(6.0, 2.0 + cooldownSeconds(modeId) / 30.0); }

    private ShardRules() {}
}
