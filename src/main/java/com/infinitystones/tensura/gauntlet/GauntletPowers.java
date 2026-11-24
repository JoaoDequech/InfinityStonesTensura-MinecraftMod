package com.infinitystones.tensura.gauntlet;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.GauntletStones;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Shared rules of the gauntlet powers. Every one needs a COMPLETE gauntlet carried or worn (hands, inventory, accessory slot). The original's
 * world-scale powers (Snap, Bring Back, Kill Players, Kill Hostiles, Destroy Stones, Eternity's Convergence) keep
 * the original "burn": after Snap / Kill Players / Kill Hostiles / Destroy Stones the arm is burned for one
 * Minecraft day (20 min) and those six cannot be used until it heals.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class GauntletPowers {
    private static final String BURN = "infinity_gauntlet_burn_until";
    public static final long BURN_TICKS = 24000;

    /** @param original true for the six original powers (blocked while burned). */
    public static boolean ready(ServerPlayer p, boolean original) {
        if (!GauntletStones.wearsComplete(p)) return Fx.fail(p, "infinity.gauntlet.need_complete");
        if (original && burnLeft(p) > 0) {
            p.displayClientMessage(Component.translatable("infinity.gauntlet.burned", burnLeft(p) / 1200 + 1).withStyle(ChatFormatting.DARK_RED), true);
            return false;
        }
        return true;
    }

    public static long now(ServerPlayer p) { return p.server.overworld().getGameTime(); }
    public static long burnLeft(ServerPlayer p) { return Math.max(0, Rules.data(p).getLong(BURN) - now(p)); }

    public static void burn(ServerPlayer p) {
        Rules.data(p).putLong(BURN, now(p) + BURN_TICKS);
        p.displayClientMessage(Component.translatable("infinity.gauntlet.burn_start").withStyle(ChatFormatting.DARK_RED), false);
    }

    public static void advancement(ServerPlayer p, String id) {
        var adv = p.server.getAdvancements().get(ResourceLocation.parse(id));
        if (adv == null) return;
        for (String c : adv.value().criteria().keySet()) p.getAdvancements().award(adv, c);
    }

    /** A sound everybody on the server hears (the original used master volume for the whole server). */
    public static void global(ServerPlayer from, SoundEvent sound, float vol, float pitch) {
        for (ServerPlayer o : from.server.getPlayerList().getPlayers()) o.playNotifySound(sound, SoundSource.PLAYERS, Math.min(vol, 0.7F), pitch);
    }

    public static void announce(ServerPlayer from, String key, Object... args) {
        Component c = Component.translatable(key, args).withStyle(ChatFormatting.GOLD);
        for (ServerPlayer o : from.server.getPlayerList().getPlayers()) o.sendSystemMessage(c);
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.tickCount % 20 != 0) return;
        if (!Rules.data(p).contains(BURN)) return;
        if (burnLeft(p) <= 0) { Rules.data(p).remove(BURN); p.displayClientMessage(Component.translatable("infinity.gauntlet.burn_healed").withStyle(ChatFormatting.GREEN), true); return; }
        if (GauntletStones.inHand(p).isEmpty()) return;
        Vec3 hand = p.getEyePosition().add(p.getLookAngle().scale(0.5)).subtract(0, 0.5, 0);
        p.serverLevel().sendParticles(ParticleTypes.SMOKE, hand.x, hand.y, hand.z, 3, 0.08, 0.08, 0.08, 0.01);
        p.serverLevel().sendParticles(ParticleTypes.ASH, hand.x, hand.y, hand.z, 2, 0.1, 0.1, 0.1, 0);
    }

    private GauntletPowers() {}
}
