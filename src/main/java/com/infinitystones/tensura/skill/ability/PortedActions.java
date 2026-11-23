package com.infinitystones.tensura.skill.ability;

import com.infinitystones.tensura.skill.StoneTree;
import com.infinitystones.tensura.skill.ability.ported.*;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Integration point for hand-ported original abilities and new abilities (owned by the "port" work stream).
 * NodeActions asks here FIRST for every node. Dispatch on {@code n.modeId()} ("stone.node_id") first, then on
 * {@code n.originalType()}.
 *
 * <p>Return values: {@code press} → null = not handled here, TRUE = ran OK (cooldown starts), FALSE = fizzled.
 * Lifecycle methods → true = handled here (the original command replay is skipped), false = not handled.
 * Everything runs on the server thread, validated by the wheel (stone carried, node unlocked, cooldown); never
 * require OP — players have none on the target server.</p>
 */
public final class PortedActions {
    private static final Map<String, Ported> BY_MODE = new HashMap<>();
    private static final Map<String, Ported> BY_TYPE = new HashMap<>();

    static {
        // ---- original Palladium core types ----
        EnergyBeam beam = new EnergyBeam();
        BY_TYPE.put("palladium:energy_beam", beam);
        BY_TYPE.put("palladium:aim", beam);
        BY_TYPE.put("palladium:projectile", new VirtualProjectile());
        BY_TYPE.put("palladium:attribute_modifier", new AttributeModifier());
        BY_TYPE.put("palladium:damage_immunity", new DamageImmunity());
        BY_TYPE.put("palladium:intangibility", new Intangibility());
        BY_TYPE.put("palladium:invisibility", new Invisibility());
        BY_TYPE.put("corewithstuff:telekinesis", new Telekinesis());
        BY_TYPE.put("palladium:ability_wheel", new WeatherWheel());

        // ---- original Java extras ----
        BY_TYPE.put("palladium:synaptic_cascade", new SynapticCascade());
        BY_TYPE.put("palladium:psychic_dominion", new PsychicDominion());
        BY_TYPE.put("palladium:terror_projection", new TerrorProjection());
        BY_TYPE.put("palladium:mental_bastion", new MentalBastion());
        BY_TYPE.put("palladium:titanic_charge", new TitanicCharge());
        BY_TYPE.put("palladium:tectonic_rend", new TectonicRend());
        BY_TYPE.put("palladium:kinetic_reservoir", new KineticReservoir());
        BY_TYPE.put("palladium:cosmic_arsenal", new CosmicArsenal());
        BY_TYPE.put("palladium:spatial_exchange", new SpatialExchange());
        BY_TYPE.put("palladium:orbital_judgement", new OrbitalJudgement());
        BY_TYPE.put("palladium:spatial_reflection", new SpatialReflection());
        BY_TYPE.put("palladium:aether_transmutation", new AetherTransmutation());
        BY_TYPE.put("palladium:crimson_lattice", new CrimsonLattice());
        BY_TYPE.put("palladium:harmless_form", new HarmlessForm());
        BY_TYPE.put("palladium:reality_confiscation", new RealityConfiscation());
        BY_TYPE.put("palladium:essence_siphon", new EssenceSiphon());
        BY_TYPE.put("palladium:spirit_legion", new SpiritLegion());
        BY_TYPE.put("palladium:soul_sanctuary", new SoulSanctuary());
        BY_TYPE.put("palladium:reaper_brand", new ReaperBrand());
        BY_TYPE.put("palladium:personal_rewind", new PersonalRewind());
        BY_TYPE.put("palladium:temporal_echo", new TemporalEcho());
        BY_TYPE.put("palladium:closed_time_loop", new ClosedTimeLoop());
        BY_TYPE.put("palladium:season_in_a_moment", new SeasonInAMoment());

        // ---- command nodes that called Palladium-only / missing-mod commands ----
        CommandPorts.register(BY_MODE);

    }

    private static @Nullable Ported find(StoneTree.Node n) {
        Ported p = BY_MODE.get(n.modeId());
        return p != null ? p : BY_TYPE.get(n.originalType());
    }

    /** One-shot use (activation "press"). alt = right click on the wheel or Shift (the original's second variant). */
    public static @Nullable Boolean press(ServerPlayer p, StoneTree.Node n, boolean alt) {
        if (RealityConfiscation.isFrozen(p)) return false; // bound by Reality Confiscation: no powers (original mixin)
        PolishFx.press(p, n, alt);
        Ported a = find(n);
        return a == null ? null : a.press(p, n, alt);
    }

    /** Channel (activation "hold"): start / every tick while the wheel key stays held / end. */
    public static boolean holdStart(ServerPlayer p, StoneTree.Node n) {
        if (RealityConfiscation.isFrozen(p)) return true; // swallowed: nothing starts while bound
        PolishFx.start(p, n);
        Ported a = find(n);
        if (a == null) return false;
        int timed = PortFx.activationTicks(n);
        if (timed > 0) Sessions.begin(p, n, a, timed);
        else a.start(p, n);
        return true;
    }
    public static boolean holdTick(ServerPlayer p, StoneTree.Node n, int ticks) {
        PolishFx.tick(p, n, ticks);
        Ported a = find(n);
        if (a == null) return false;
        if (PortFx.activationTicks(n) == 0) a.tick(p, n, ticks);
        return true;
    }
    public static boolean holdEnd(ServerPlayer p, StoneTree.Node n) {
        PolishFx.stop(p, n);
        Ported a = find(n);
        if (a == null) return false;
        if (PortFx.activationTicks(n) == 0) a.stop(p, n);
        return true;
    }

    /** Passive (while unlocked + stone carried) and toggles (while on): start / every tick / stop. */
    public static boolean start(ServerPlayer p, StoneTree.Node n) {
        PolishFx.start(p, n);
        Ported a = find(n);
        if (a == null) return false;
        a.start(p, n);
        return true;
    }
    public static boolean tick(ServerPlayer p, StoneTree.Node n) {
        PolishFx.tick(p, n, p.tickCount);
        Ported a = find(n);
        if (a == null) return false;
        a.tick(p, n, p.tickCount);
        return true;
    }
    public static boolean stop(ServerPlayer p, StoneTree.Node n) {
        PolishFx.stop(p, n);
        Ported a = find(n);
        if (a == null) return false;
        a.stop(p, n);
        return true;
    }

    private PortedActions() {}
}
