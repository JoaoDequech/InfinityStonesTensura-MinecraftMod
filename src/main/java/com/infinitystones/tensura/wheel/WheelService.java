package com.infinitystones.tensura.wheel;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.GauntletStones;
import com.infinitystones.tensura.skill.ImmortalitySkill;
import com.infinitystones.tensura.skill.InfinitySkills;
import com.infinitystones.tensura.skill.NodeEffects;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.StoneProgress;
import com.infinitystones.tensura.skill.StoneTier;
import com.infinitystones.tensura.skill.StoneTree;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import com.infinitystones.tensura.skill.DualModes;
import com.infinitystones.tensura.skill.ability.Relocation;
import com.infinitystones.tensura.skill.ability.ported.PolishFx;
import com.infinitystones.tensura.skill.ability.BanishLog;
import com.infinitystones.tensura.skill.ability.SpatialBanishment;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server side of the Infinity wheel. Every use is validated here (stone carried, node unlocked, cooldown) — the
 * client only says what it wants. Stone powers never go through Tensura slots.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class WheelService {
    public static final String IMMORTALITY = "immortality";
    private static final Map<UUID, StoneTree.Node> HOLDING = new HashMap<>();
    private static final Map<UUID, Integer> HOLD_TICKS = new HashMap<>();
    private static final Map<UUID, Integer> LAST_SYNC = new HashMap<>();

    /**
     * phase 0 = use (press / flip a switch / start-or-stop a channel), 1 = stop channel, 2 = use the alternate variant
     * (right click). Aim mode is purely client-side: the use simply arrives when the player confirms, and is validated
     * here like any other. No cooldowns (user decision 18/11).
     */
    public static void handleUse(ServerPlayer p, int stoneOrdinal, String nodeId, int phase) {
        if (com.infinitystones.tensura.skill.ability.extra.space.SpiritualGathering.isTrapped(p)) return;
        if (phase < 0 || phase > 2) return;
        if (stoneOrdinal < 0) { // legacy client id for immortality
            if (IMMORTALITY.equals(nodeId) && phase != 1 && Stone.SOUL.isHeldBy(p) && StoneTier.has(p, Stone.SOUL)) { ImmortalitySkill.toggle(p); castFx(p, Stone.SOUL); }
            sync(p, true);
            return;
        }
        if (stoneOrdinal >= Stone.values().length) return;
        Stone stone = Stone.values()[stoneOrdinal];
        StoneTree.Node node = StoneTree.of(stone).nodes.get(nodeId);
        if (node == null || !(node.isActive() || node.isPassiveToggle()) || StoneTree.HIDDEN_IN_WHEEL.contains(node.modeId())) return;
        if (StoneTier.hidden(p, node)) return;
        if (phase == 1) { stopHold(p, node); return; }
        StoneTree.Node current = HOLDING.get(p.getUUID());
        if (current != null && current.modeId().equals(node.modeId())) { stopHold(p, node); return; } // channel = start/stop toggle
        if (!stone.isHeldBy(p) || !StoneProgress.isUnlocked(p, stone, nodeId)) { deny(p, "infinity.wheel.locked"); return; }
        boolean alt = phase == 2;
        if (!node.upper()) spendMagicule(p);
        if (!AbilityMeta.isSwitch(node)) stopHold(p, null); // flipping a switch never interrupts a channel
        switch (node.activation()) {
            case PASSIVE -> { NodeEffects.use(p, node, false); castFx(p, stone); }
            case PRESS, TOGGLE -> {
                if (NodeEffects.use(p, node, alt)) castFx(p, stone);
            }
            case HOLD -> {
                NodeEffects.use(p, node, alt);
                castFx(p, stone);
                HOLDING.put(p.getUUID(), node);
                HOLD_TICKS.put(p.getUUID(), 0);
            }
        }
        sync(p, true);
    }

    /** Token Tensura magicule cost per power use (never blocks a power, never runs out in practice). */
    private static final double MAGICULE_COST = 1.0;

    private static void spendMagicule(ServerPlayer p) {
        try {
            var ep = io.github.manasmods.tensura.storage.TensuraStorages.getExistenceFrom(p);
            if (ep != null) ep.setMagicule(Math.max(0, ep.getMagicule() - MAGICULE_COST));
        } catch (RuntimeException | LinkageError ignored) {
            // Tensura storage unavailable: powers still work
        }
    }

    /** Alt + mouse wheel: change the power's integer setting (validated: power exists, declares a setting, is unlocked). */
    public static void handleSetting(ServerPlayer p, int stoneOrdinal, String nodeId, int delta) {
        if (com.infinitystones.tensura.skill.ability.extra.space.SpiritualGathering.isTrapped(p)) return;
        if (stoneOrdinal < 0 || stoneOrdinal >= Stone.values().length || delta == 0) return;
        Stone stone = Stone.values()[stoneOrdinal];
        StoneTree.Node node = StoneTree.of(stone).nodes.get(nodeId);
        AbilityMeta.SettingSpec spec = node == null ? null : AbilityMeta.setting(node, p);
        if (spec == null || !stone.isHeldBy(p) || !StoneProgress.isUnlocked(p, stone, nodeId)) return;
        int before = AbilitySettings.get(p, node);
        int v = AbilitySettings.set(p, node, before + Integer.signum(delta) * Math.max(1, spec.step()));
        if (v != before) VariableAbility.fire(p, node, v); // level bar + HUD show it client-side
        sync(p, true);
    }

    /** Middle-click destination use: same validation as a normal use, then moves the target (or the caster). */
    public static void handleDestination(ServerPlayer p, WheelPayloads.Destination d) {
        if (com.infinitystones.tensura.skill.ability.extra.space.SpiritualGathering.isTrapped(p)) return;
        if (d.stone() < 0 || d.stone() >= Stone.values().length) return;
        Stone stone = Stone.values()[d.stone()];
        StoneTree.Node node = StoneTree.of(stone).nodes.get(d.node());
        if (node == null || !DualModes.hasDestination(node.modeId()) || StoneTier.hidden(p, node)) return;
        if (!stone.isHeldBy(p) || !StoneProgress.isUnlocked(p, stone, node.id())) { deny(p, "infinity.wheel.locked"); return; }
        ResourceLocation dimId = ResourceLocation.tryParse(d.dimension());
        ServerLevel level = dimId == null ? null : p.server.getLevel(ResourceKey.create(Registries.DIMENSION, dimId));
        if (level == null) { deny(p, "infinity.dest.unknown_dimension"); return; }
        if (!Double.isFinite(d.x()) || !Double.isFinite(d.z()) || (!d.autoY() && !Double.isFinite(d.y()))) return;

        Entity target = DualModes.movesTarget(node.modeId()) ? SpatialBanishment.aimedTarget(p) : p;
        if (target == null || target.isPassenger() || target.isVehicle()) { deny(p, "ability.infinity.space_stone.spatial_banishment.failed"); return; }
        Vec3 landing = d.useDefault() ? Relocation.defaultPoint(level, target)
                : Relocation.at(level, target, d.x(), d.autoY() ? Double.NaN : d.y(), d.z());
        if (landing == null) { deny(p, "infinity.dest.no_safe_spot"); return; }
        stopHold(p, null);
        castFx(p, stone);
        var fromDim = target.level().dimension();
        Vec3 from = target.position();
        if (target == p) PolishFx.destinationDepart(p);
        Entity moved = Relocation.move(target, level, landing, 1.0F);
        if (moved == null) { deny(p, "infinity.dest.failed"); return; }
        if (target == p) PolishFx.destinationArrive(p, from, level.dimension().equals(fromDim));
        if (target != p) BanishLog.record(p, target, fromDim, from, moved);
        startCooldown(p, node);
        p.displayClientMessage(Component.translatable(target == p ? "infinity.dest.done_self" : "infinity.dest.done_target",
                dimName(dimId).withStyle(ChatFormatting.AQUA),
                Mth.floor(landing.x) + " " + Mth.floor(landing.y) + " " + Mth.floor(landing.z)), true);
        sync(p, true);
    }

    /** Tree screen purchase. No permission needed — the same rules as the original tree (parents, stones, cost). */
    public static void handleBuy(ServerPlayer p, int stoneOrdinal, String nodeId) {
        if (com.infinitystones.tensura.skill.ability.extra.space.SpiritualGathering.isTrapped(p)) return;
        if (stoneOrdinal < 0 || stoneOrdinal >= Stone.values().length || nodeId.length() > 64) return;
        Stone stone = Stone.values()[stoneOrdinal];
        StoneProgress.BuyResult r = StoneProgress.buy(p, stone, nodeId);
        if (r == StoneProgress.BuyResult.OK) {
            StoneTree.Node n = StoneTree.of(stone).nodes.get(nodeId);
            p.displayClientMessage(Component.translatable("infinity.tree.result.ok", n.displayName()).withStyle(ChatFormatting.GREEN), true);
            castFx(p, stone);
            p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 1.3F);
        } else {
            p.displayClientMessage(StoneProgress.describe(r).copy().withStyle(ChatFormatting.RED), true);
        }
        sync(p, true);
    }

    /** "Bring it back" menu: action 0 = send me my banished list, 1 = bring back entry #index. */
    public static void handleBanished(ServerPlayer p, int action, int index) {
        if (com.infinitystones.tensura.skill.ability.extra.space.SpiritualGathering.isTrapped(p)) return;
        StoneTree.Node node = StoneTree.of(Stone.SPACE).nodes.get("spatial_banishment");
        if (node == null || !Stone.SPACE.isHeldBy(p) || !StoneProgress.isUnlocked(p, Stone.SPACE, node.id())) return;
        if (action == 1) {
            BanishLog.Result r = BanishLog.bringBack(p, index);
            p.displayClientMessage(BanishLog.describe(r).withStyle(r == BanishLog.Result.OK ? ChatFormatting.AQUA : ChatFormatting.GRAY), true);
            if (r == BanishLog.Result.OK) castFx(p, Stone.SPACE);
        }
        List<WheelPayloads.Banished> list = new ArrayList<>();
        for (BanishLog.Entry e : BanishLog.list(p)) list.add(new WheelPayloads.Banished(e.name(), e.toDim().location().toString(), e.fromDim().location().toString()));
        PacketDistributor.sendToPlayer(p, new WheelPayloads.BanishedList(list));
    }

    /** Client-translated dimension name: vanilla via our keys, modded via "dimension.ns.path", else a readable path. */
    private static net.minecraft.network.chat.MutableComponent dimName(ResourceLocation id) {
        String path = id.getPath().replace('/', ' ').replace('_', ' ');
        StringBuilder b = new StringBuilder();
        for (String w : path.split(" ")) if (!w.isEmpty()) b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        String key = id.getNamespace().equals("minecraft") ? "infinity.dest.dim." + id.getPath() : "dimension." + id.getNamespace() + "." + id.getPath();
        return Component.translatableWithFallback(key, b.toString().trim());
    }

    private static void stopHold(ServerPlayer p, StoneTree.Node only) {
        StoneTree.Node held = HOLDING.get(p.getUUID());
        if (held == null || (only != null && !held.modeId().equals(only.modeId()))) return;
        HOLDING.remove(p.getUUID());
        HOLD_TICKS.remove(p.getUUID());
        NodeEffects.releaseHold(p, held);
        startCooldown(p, held);
        sync(p, true);
    }

    /** Small burst in the stone's colour at the caster's hand, so everyone nearby sees which stone was used. */
    private static void castFx(ServerPlayer p, Stone stone) {
        int c = stone.color();
        var dust = new DustParticleOptions(new Vector3f(((c >> 16) & 255) / 255F, ((c >> 8) & 255) / 255F, (c & 255) / 255F), 1.1F);
        Vec3 look = p.getLookAngle();
        double x = p.getX() + look.x * 0.6 - look.z * 0.35, y = p.getEyeY() - 0.45, z = p.getZ() + look.z * 0.6 + look.x * 0.35;
        p.serverLevel().sendParticles(dust, x, y, z, 18, 0.18, 0.18, 0.18, 0.02);
        p.serverLevel().sendParticles(ParticleTypes.END_ROD, x, y, z, 4, 0.12, 0.12, 0.12, 0.03);
    }

    /** No cooldowns anywhere (user decision 18/11); kept as a no-op hook. */
    private static void startCooldown(ServerPlayer p, StoneTree.Node node) { }

    private static void deny(ServerPlayer p, String key) {
        p.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY), true);
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        StoneTree.Node held = HOLDING.get(p.getUUID());
        if (held != null) {
            int t = HOLD_TICKS.merge(p.getUUID(), 1, Integer::sum);
            if (!held.stone().isHeldBy(p) || !p.isAlive()) stopHold(p, held);
            else NodeEffects.holdTick(p, held, t);
        }
        if (p.tickCount % 10 == 0) sync(p, false);
    }

    /** Ends the channel in progress (if any) with its normal release, without touching the network (logout, server stop). */
    public static void releaseChannel(ServerPlayer p) {
        StoneTree.Node held = HOLDING.remove(p.getUUID());
        HOLD_TICKS.remove(p.getUUID());
        if (held == null) return;
        try {
            NodeEffects.releaseHold(p, held);
        } catch (RuntimeException ignored) {
            // never block a logout
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        if (event.getEntity() instanceof ServerPlayer p) releaseChannel(p);
        HOLDING.remove(id); HOLD_TICKS.remove(id); LAST_SYNC.remove(id);
    }

    /** Sends the wheel contents (carried stones, their slottable nodes, lock/toggle/cooldown) when it changed. */
    public static void sync(ServerPlayer p, boolean force) {
        Set<Stone> held = StoneProgress.heldStones(p);
        int gauntlet = GauntletStones.carriedMask(p);
        List<WheelPayloads.Entry> entries = new ArrayList<>();
        for (Stone s : Stone.values()) {
            boolean carried = held.contains(s);
            // with a gauntlet the wheel's gauntlet page lists every complete-gauntlet power, even of a stone that is not set yet
            if (!carried && gauntlet < 0) continue;
            boolean learned = StoneProgress.instance(p, s).isPresent();
            if (carried && !learned) continue;
            for (StoneTree.Node n : StoneTree.of(s).wheelNodes) { // powers + passive switches (immortality included)
                if (!carried && !GauntletStones.isGauntletPower(n)) continue;
                if (StoneTier.hidden(p, n)) continue;
                boolean unlocked = carried && learned && StoneProgress.isUnlocked(p, s, n.id(), held);
                entries.add(new WheelPayloads.Entry(s.ordinal(), n.id(), unlocked,
                        NodeEffects.isToggled(p, n), 0, 1, AbilitySettings.get(p, n)));
            }
        }
        StoneTree.Node h = HOLDING.get(p.getUUID());
        WheelPayloads.State state = new WheelPayloads.State(entries, h == null ? "" : h.modeId(), StoneTier.mask(p), gauntlet);
        int hash = state.hashCode();
        if (!force && Integer.valueOf(hash).equals(LAST_SYNC.get(p.getUUID()))) return;
        LAST_SYNC.put(p.getUUID(), hash);
        PacketDistributor.sendToPlayer(p, state);
    }

    private WheelService() {}
}
