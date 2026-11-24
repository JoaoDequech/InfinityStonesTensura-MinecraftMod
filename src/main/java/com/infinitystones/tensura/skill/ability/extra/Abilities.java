package com.infinitystones.tensura.skill.ability.extra;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/** original_type ("infinity:<name>") → ability. Design: notes/new-abilities.md. */
public final class Abilities {
    private static final Map<String, Ability> ALL = new HashMap<>();

    static {
        r("infinity:psychic_avatar", com.infinitystones.tensura.skill.ability.extra.mind.PsychicAvatar.INSTANCE);
        r("infinity:phantasm", com.infinitystones.tensura.skill.ability.extra.mind.Phantasm.INSTANCE);
        r("infinity:suggestion", com.infinitystones.tensura.skill.ability.extra.mind.Suggestion.INSTANCE);
        r("infinity:hive_mind", com.infinitystones.tensura.skill.ability.extra.mind.HiveMind.INSTANCE);
        r("infinity:nightmare", com.infinitystones.tensura.skill.ability.extra.mind.Nightmare.INSTANCE);
        r("infinity:cataclysm_forge", com.infinitystones.tensura.skill.ability.extra.power.CataclysmForge.INSTANCE);
        r("infinity:debris_orbit", com.infinitystones.tensura.skill.ability.extra.power.DebrisOrbit.INSTANCE);
        r("infinity:overcharge", com.infinitystones.tensura.skill.ability.extra.power.Overcharge.INSTANCE);
        r("infinity:seismic_wave", com.infinitystones.tensura.skill.ability.extra.power.SeismicWave.INSTANCE);
        r("infinity:meteor_gate", com.infinitystones.tensura.skill.ability.extra.power.MeteorGate.INSTANCE);
        r("infinity:celestial_nuke", com.infinitystones.tensura.skill.ability.extra.power.CelestialNuke.INSTANCE);
        r("infinity:scene_anchor", com.infinitystones.tensura.skill.ability.extra.space.SceneAnchor.INSTANCE);
        r("infinity:fold_distance", com.infinitystones.tensura.skill.ability.extra.space.FoldDistance.INSTANCE);
        r("infinity:containment_cube", com.infinitystones.tensura.skill.ability.extra.space.ContainmentCube.INSTANCE);
        r("infinity:space_mirror", com.infinitystones.tensura.skill.ability.extra.space.SpaceMirror.INSTANCE);
        r("infinity:abyss_watch", com.infinitystones.tensura.skill.ability.extra.space.AbyssWatch.INSTANCE);
        r("infinity:reality_pet", com.infinitystones.tensura.skill.ability.extra.reality.RealityPet.INSTANCE);
        r("infinity:broken_mode", com.infinitystones.tensura.skill.ability.extra.reality.BrokenMode.INSTANCE);
        r("infinity:local_decree", com.infinitystones.tensura.skill.ability.extra.reality.LocalDecree.INSTANCE);
        r("infinity:mirage", com.infinitystones.tensura.skill.ability.extra.reality.Mirage.INSTANCE);
        r("infinity:metamorphosis", com.infinitystones.tensura.skill.ability.extra.reality.Metamorphosis.INSTANCE);
        r("infinity:stasis_bubble", com.infinitystones.tensura.skill.ability.extra.time.StasisBubble.INSTANCE);
        r("infinity:time_mark", com.infinitystones.tensura.skill.ability.extra.time.TimeMark.INSTANCE);
        r("infinity:lapse_field", com.infinitystones.tensura.skill.ability.extra.time.LapseField.INSTANCE);
        r("infinity:eras", com.infinitystones.tensura.skill.ability.extra.time.Eras.INSTANCE);
        r("infinity:restoration", com.infinitystones.tensura.skill.ability.extra.time.Restoration.INSTANCE);
        r("infinity:area_heist", com.infinitystones.tensura.skill.ability.extra.space.AreaHeist.INSTANCE);
        r("infinity:climate_zone", com.infinitystones.tensura.skill.ability.extra.time.ClimateZone.INSTANCE);
        r("infinity:convergence", com.infinitystones.tensura.skill.ability.extra.power.Convergence.INSTANCE);
        r("infinity:greed", com.infinitystones.tensura.skill.ability.extra.reality.Greed.INSTANCE);
        r("infinity:spectral_army", com.infinitystones.tensura.skill.ability.extra.soul.SpectralArmy.INSTANCE);
        r("infinity:reaper", com.infinitystones.tensura.skill.ability.extra.soul.Reaper.INSTANCE);
        r("infinity:soul_link", com.infinitystones.tensura.skill.ability.extra.soul.SoulLink.INSTANCE);
        r("infinity:return_pact", com.infinitystones.tensura.skill.ability.extra.soul.ReturnPact.INSTANCE);
        r("infinity:past_echo", com.infinitystones.tensura.skill.ability.extra.soul.PastEcho.INSTANCE);
        r("infinity:soul_rift", com.infinitystones.tensura.skill.ability.extra.soul.SoulRift.INSTANCE);
        r("infinity:mind_palace", com.infinitystones.tensura.skill.ability.extra.mind.MindPalace.INSTANCE);
        r("infinity:puppet_strings", com.infinitystones.tensura.skill.ability.extra.mind.PuppetStrings.INSTANCE);
        r("infinity:astral_swap", com.infinitystones.tensura.skill.ability.extra.mind.AstralSwap.INSTANCE);
        r("infinity:gravity_hammer", com.infinitystones.tensura.skill.ability.extra.power.GravityHammer.INSTANCE);
        r("infinity:prism_barrage", com.infinitystones.tensura.skill.ability.extra.power.PrismBarrage.INSTANCE);
        r("infinity:portal_gun", com.infinitystones.tensura.skill.ability.extra.space.PortalGun.INSTANCE);
        r("infinity:pocket_orbit", com.infinitystones.tensura.skill.ability.extra.space.PocketOrbit.INSTANCE);
        r("infinity:paint_world", com.infinitystones.tensura.skill.ability.extra.reality.PaintWorld.INSTANCE);
        r("infinity:pocket_card", com.infinitystones.tensura.skill.ability.extra.reality.PocketCard.INSTANCE);
        r("infinity:soul_sight", com.infinitystones.tensura.skill.ability.extra.soul.SoulSight.INSTANCE);
        r("infinity:lost_lantern", com.infinitystones.tensura.skill.ability.extra.soul.LostLantern.INSTANCE);
        r("infinity:rewind_me", com.infinitystones.tensura.skill.ability.extra.time.RewindMe.INSTANCE);
        r("infinity:time_clones", com.infinitystones.tensura.skill.ability.extra.time.TimeClones.INSTANCE);
        r("infinity:alternate_timeline", com.infinitystones.tensura.skill.ability.extra.time.AlternateTimeline.INSTANCE);
        r("infinity:creation_chest", com.infinitystones.tensura.skill.ability.extra.reality.CreationChest.INSTANCE);
        r("infinity:spiritual_gathering", com.infinitystones.tensura.skill.ability.extra.space.SpiritualGathering.INSTANCE);
        com.infinitystones.tensura.skill.ability.extra.v2a.V2A.register(Abilities::r);
        com.infinitystones.tensura.skill.ability.extra.v2b.V2B.register(Abilities::r);
        com.infinitystones.tensura.skill.ability.extra.v2c.V2C.register(Abilities::r);
        com.infinitystones.tensura.skill.ability.extra.v2d.V2D.register(Abilities::r);
        com.infinitystones.tensura.skill.ability.extra.v2e.V2E.register(Abilities::r);
    }

    private static void r(String type, Ability a) { ALL.put(type, a); }

    public static @Nullable Ability get(String type) { return ALL.get(type); }

    private Abilities() {}
}
