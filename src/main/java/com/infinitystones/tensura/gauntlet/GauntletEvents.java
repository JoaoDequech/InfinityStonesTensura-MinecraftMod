package com.infinitystones.tensura.gauntlet;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.registry.ModSounds;
import com.infinitystones.tensura.skill.GauntletStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Inserting / removing stones (the original gauntlet function). The socket screen lives on the wheel's gauntlet page
 * (see {@link GauntletGems}); crouch + right click with the gauntlet still sets every loose gem from your inventory
 * into its empty socket at once.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class GauntletEvents {
    @SubscribeEvent
    public static void use(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        if (!GauntletStones.isGauntlet(stack) || !event.getEntity().isShiftKeyDown()) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getEntity() instanceof ServerPlayer p) quickInsert(p, stack);
    }

    private static void quickInsert(ServerPlayer p, ItemStack gauntlet) {
        NonNullList<ItemStack> six = GauntletStones.sockets(gauntlet);
        int before = GauntletStones.mask(gauntlet);
        boolean any = false;
        for (Stone s : Stone.values()) {
            if (!six.get(s.ordinal()).isEmpty()) continue;
            for (ItemStack it : p.getInventory().items) {
                if (it == gauntlet || !GauntletStones.isGem(it, s)) continue;
                six.set(s.ordinal(), it.split(1));
                any = true;
                break;
            }
        }
        if (!any) { Fx.barGray(p, "infinity.gauntlet.no_loose"); return; }
        GauntletStones.setSockets(gauntlet, six);
        p.getInventory().setChanged();
        socketFx(p, before, GauntletStones.mask(gauntlet));
    }

    /** Sounds and lights when sockets change; a crown of light when the sixth stone goes in. */
    static void socketFx(ServerPlayer p, int before, int after) {
        ServerLevel l = p.serverLevel();
        Vec3 hand = p.getEyePosition().add(p.getLookAngle().scale(0.6)).subtract(0, 0.45, 0);
        for (Stone s : Stone.values()) {
            int bit = 1 << s.ordinal();
            if ((after & bit) != 0 && (before & bit) == 0) {
                Fx.at(l, Fx.dust(s, 1.2F), hand, 18, 0.15, 0.05);
                Fx.sound(l, hand, ModSounds.INFINITY_STONE_EQUIP.get(), 0.5F, 1.0F);
            } else if ((after & bit) == 0 && (before & bit) != 0) {
                Fx.at(l, Fx.dust(s, 0.8F), hand, 10, 0.2, 0.02);
                Fx.sound(l, hand, ModSounds.STONE_REMOVE.get(), 0.5F, 1.0F);
            }
        }
        if (after == 0b111111 && before != 0b111111) {
            for (Stone s : Stone.values()) Fx.ring(l, p.position().add(0, 0.2 + s.ordinal() * 0.3, 0), 1.2, 20, Fx.dust(s, 1.2F));
            Fx.at(l, ParticleTypes.END_ROD, p.position().add(0, 1, 0), 40, 0.6, 0.08);
            Fx.sound(l, p.position(), ModSounds.USE_INFINITY_GAUNTLET.get(), 0.6F, 1.0F);
            p.displayClientMessage(Component.translatable("infinity.gauntlet.complete").withStyle(ChatFormatting.GOLD), true);
        }
    }

    private GauntletEvents() {}
}
