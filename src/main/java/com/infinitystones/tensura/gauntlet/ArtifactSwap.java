package com.infinitystones.tensura.gauntlet;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.registry.ModSounds;
import com.infinitystones.tensura.skill.GauntletStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.ported.FxNet;
import com.infinitystones.tensura.skill.ability.ported.PortFx;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Taking stones out of / putting them into their artifacts, like the original mod (Palladium command abilities and
 * ground-relic interactions), without Palladium:
 * <ul>
 *   <li>Scepter (Mind), Orb (Power), Tesseract (Space), Aether (Reality), Eye of Agamotto (Time): <b>Shift + look at the
 *       sky + left click</b> with the artifact in the main hand takes the stone out (the original Eye of Agamotto
 *       "Shift Left Click" + the look-up rule asked for 20/11) and leaves
 *       the empty artifact; <b>right click</b> with the empty artifact in the main hand and the stone in the off hand
 *       puts it back (the original "Insert" abilities: stone in the off hand). The Orb had no empty item: an emptied Orb
 *       is marked in its data (the original used a player tag "power_stone_removed").</li>
 *   <li>Cosmi-Rod: right click with the empty rod in the main hand and a stone in the off hand inserts it; <b>Shift +
 *       right click</b> with a filled rod takes it out (original cosmi-rod.json).</li>
 * </ul>
 * Server-authoritative and swap-based: one item is replaced in the same slot and exactly one stone is given/taken, so
 * nothing can be duplicated. Works in survival and multiplayer.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class ArtifactSwap {
    public static final String EMPTY_FLAG = "InfinityEmpty";

    private record Artifact(Stone stone, String full, @Nullable String empty, boolean rod) {}

    private static final Artifact[] ARTIFACTS = {
            new Artifact(Stone.MIND, "infinity:scepter", "infinity:empty_scepter", false),
            new Artifact(Stone.POWER, "infinity:orb", null, false),
            new Artifact(Stone.SPACE, "infinity:tesseract", "infinity:empty_tesseract", false),
            new Artifact(Stone.REALITY, "infinity:aether", "infinity:empty_aether", false),
            new Artifact(Stone.TIME, "infinity:eye_of_agamotto", "infinity:empty_eye_of_agamotto", false),
            new Artifact(Stone.MIND, "infinity:cosmi-rod_mind", "infinity:cosmi-rod", true),
            new Artifact(Stone.POWER, "infinity:cosmi-rod_power", "infinity:cosmi-rod", true),
            new Artifact(Stone.SPACE, "infinity:cosmi-rod_space", "infinity:cosmi-rod", true),
            new Artifact(Stone.REALITY, "infinity:cosmi-rod_reality", "infinity:cosmi-rod", true),
            new Artifact(Stone.SOUL, "infinity:cosmi-rod_soul", "infinity:cosmi-rod", true),
            new Artifact(Stone.TIME, "infinity:cosmi-rod_time", "infinity:cosmi-rod", true),
    };

    private static Item item(String id) { return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)); }

    /** Emptied Orb (marked) — Stone.isCarrier ignores it. */
    public static boolean isEmptied(ItemStack s) {
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        return d != null && d.contains(EMPTY_FLAG);
    }

    /** Filled artifact in this stack (null if none / emptied). */
    public static @Nullable Artifact filled(ItemStack s) {
        if (s.isEmpty() || isEmptied(s)) return null;
        for (Artifact a : ARTIFACTS) if (s.is(item(a.full()))) return a;
        return null;
    }

    /** Empty artifact matching the stone in the other hand (null if none). */
    private static @Nullable Artifact emptyFor(ItemStack main, ItemStack off) {
        if (main.isEmpty() || off.isEmpty()) return null;
        for (Artifact a : ARTIFACTS) {
            if (!GauntletStones.isGem(off, a.stone())) continue;
            boolean emptyMatch = a.empty() != null ? main.is(item(a.empty())) : main.is(item(a.full())) && isEmptied(main);
            if (emptyMatch) return a;
        }
        return null;
    }

    /** Client check (no side effects): would Shift + left click extract something? */
    public static boolean canExtractByLeftClick(Player p) {
        Artifact a = filled(p.getMainHandItem());
        return p.isShiftKeyDown() && a != null && !a.rod() && lookingUp(p);
    }

    /** "Shift + look at the sky + hit" (user rule for artifacts): pitch above ~55 degrees up. */
    public static boolean lookingUp(Player p) { return p.getXRot() < -55F; }

    // ---------------- server actions ----------------

    /** Takes the stone out of the artifact in the main hand. */
    public static boolean extract(ServerPlayer p, boolean viaRightClick) {
        ItemStack main = p.getMainHandItem();
        Artifact a = filled(main);
        // Shift + right click works for every artifact; Shift + left click looking up for the non-rod ones (original rule)
        if (a == null || !p.isShiftKeyDown() || (!viaRightClick && (a.rod() || !lookingUp(p)))) return false;
        ItemStack emptied;
        if (a.empty() != null) {
            emptied = new ItemStack(item(a.empty()));
            emptied.applyComponents(main.getComponentsPatch());
        } else {
            emptied = main.copy();
            CustomData.update(DataComponents.CUSTOM_DATA, emptied, t -> t.putBoolean(EMPTY_FLAG, true));
        }
        p.setItemInHand(InteractionHand.MAIN_HAND, emptied);
        ItemStack stone = new ItemStack(GauntletStones.gem(a.stone()));
        if (!p.getInventory().add(stone)) p.drop(stone, false);
        fx(p, a.stone(), false);
        return true;
    }

    /** Puts the off-hand stone into the empty artifact in the main hand. */
    public static boolean insert(ServerPlayer p) {
        ItemStack main = p.getMainHandItem(), off = p.getOffhandItem();
        Artifact a = emptyFor(main, off);
        if (a == null) return false;
        ItemStack full;
        if (a.empty() != null) {
            full = new ItemStack(item(a.full()));
            full.applyComponents(main.getComponentsPatch());
        } else {
            full = main.copy();
            CustomData.update(DataComponents.CUSTOM_DATA, full, t -> t.remove(EMPTY_FLAG));
        }
        off.shrink(1);
        p.setItemInHand(InteractionHand.MAIN_HAND, full);
        fx(p, a.stone(), true);
        return true;
    }

    /** Original insert/remove feedback: equip / remove sound and dust in the stone colour, plus a socket circle. */
    public static void fx(ServerPlayer p, Stone stone, boolean inserted) {
        var level = p.serverLevel();
        Vec3 at = p.position().add(0, 1, 0);
        PortFx.sound(level, at, inserted ? ModSounds.INFINITY_STONE_EQUIP : ModSounds.STONE_REMOVE, 0.6F, 1F);
        level.sendParticles(PortFx.dust(stone, 1.6F), at.x, at.y, at.z, 50, 0.5, 0.6, 0.5, 0.02);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, at.x, at.y, at.z, 12, 0.4, 0.5, 0.4, 0.05);
        circle(p, stone, inserted);
    }

    /** Socket circle around the player in the stone colour (also used by the gauntlet screen). */
    public static void circle(ServerPlayer p, Stone stone, boolean inserted) {
        FxNet.circleOn(p, 0.06, 1.6F, stone, 30, FxNet.TIERS_1 | (inserted ? FxNet.RAYS : 0));
    }

    // ---------------- events ----------------

    @SubscribeEvent
    public static void rightClick(PlayerInteractEvent.RightClickItem e) {
        if (e.getHand() != InteractionHand.MAIN_HAND) return;
        Player p = e.getEntity();
        ItemStack main = p.getMainHandItem();
        Artifact filled = filled(main);
        boolean rodOut = filled != null && p.isShiftKeyDown();
        boolean in = emptyFor(main, p.getOffhandItem()) != null;
        if (!rodOut && !in) return;
        if (p instanceof ServerPlayer sp) { if (rodOut) extract(sp, true); else insert(sp); }
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
    }

    // ---------------- network: Shift + left click (client only sees clicks in the air) ----------------

    public record Extract() implements CustomPacketPayload {
        public static final Type<Extract> TYPE = new Type<>(InfinityStones.id("port_extract_stone"));
        public static final StreamCodec<ByteBuf, Extract> CODEC = StreamCodec.unit(new Extract());
        @Override public Type<Extract> type() { return TYPE; }
    }

    @EventBusSubscriber(modid = InfinityStones.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Network {
        @SubscribeEvent
        public static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("infinity_port_extract").playToServer(Extract.TYPE, Extract.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> {
                if (ctx.player() instanceof ServerPlayer p) extract(p, false);
            }));
        }
        private Network() {}
    }

    private ArtifactSwap() {}
}
