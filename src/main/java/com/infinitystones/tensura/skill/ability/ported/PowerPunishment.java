package com.infinitystones.tensura.skill.ability.ported;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.registry.ModSounds;
import com.infinitystones.tensura.skill.GauntletStones;
import com.infinitystones.tensura.skill.HeldItems;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.StoneTree;
import com.infinitystones.tensura.skill.ability.extra.Terrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Power hidden helpers explosion_timer / pre_explosion / explosion (original). Holding the bare Power Stone in the main
 * hand without protection (stone set in an Infinity Gauntlet, or a Power cosmi-rod in either hand) fills a 0..10 tick
 * timer. 1..9: violet particles; reaching 10: the stone detonates — every tick the same ring of eight TNT-strength
 * blasts as Cosmic Explosion (8 blocks out) plus Wither 2 for a second on the holder — until the stone is put away.
 * Leaving the 1..9 range gives one big particle burst.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class PowerPunishment implements Ported {
    private static final Map<UUID, Integer> TIMER = new HashMap<>();
    private static final int[][] RING = {{8, 0}, {-8, 0}, {0, 8}, {0, -8}, {8, 8}, {-8, -8}, {8, -8}, {-8, 8}};
    private static Item stone, rod;

    private static boolean isStone(ItemStack s) {
        if (stone == null) stone = BuiltInRegistries.ITEM.get(ResourceLocation.parse("infinity:power_stone"));
        return !s.isEmpty() && s.is(stone);
    }
    private static boolean isRod(ItemStack s) {
        if (rod == null) rod = BuiltInRegistries.ITEM.get(ResourceLocation.parse("infinity:cosmi-rod_power"));
        return !s.isEmpty() && s.is(rod);
    }

    private static boolean exposed(ServerPlayer p) {
        if (!isStone(p.getMainHandItem())) return false;
        if (isRod(p.getMainHandItem()) || isRod(p.getOffhandItem())) return false;
        return !HeldItems.any(p, s -> GauntletStones.has(s, Stone.POWER));
    }

    @Override
    public void tick(ServerPlayer p, StoneTree.Node n, int ticks) {
        int old = TIMER.getOrDefault(p.getUUID(), 0);
        int t = exposed(p) ? Math.min(10, old + 1) : Math.max(0, old - 1);
        if (t == 0) TIMER.remove(p.getUUID()); else TIMER.put(p.getUUID(), t);
        ServerLevel level = p.serverLevel();
        boolean pre = t >= 1 && t <= 9;
        if (pre) {
            level.sendParticles(PortFx.dust(0xC747E6, 1.5F), p.getX(), p.getY() + 1, p.getZ(), 10, 0.3, 0.5, 0.3, 0);
            level.sendParticles(ParticleTypes.DRAGON_BREATH, p.getX(), p.getY() + 1, p.getZ(), 10, 0.3, 0.5, 0.3, 0.01);
        }
        if (old >= 1 && old <= 9 && !pre) {
            level.sendParticles(ParticleTypes.DRAGON_BREATH, p.getX(), p.getY() + 1, p.getZ(), 150, 0.8, 0.8, 0.8, 0.05);
            level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 40, 0.6, 0.6, 0.6, 0.08);
        }
        if (t == 10) {
            if (old < 10) {
                PortFx.sound(level, p.position(), ModSounds.POWER_STONE_EXPLODE, 0.6F, 1F);
                Radiation.cause(p, "punish", true);
            }
            for (int[] o : RING) {
                double x = p.getX() + o[0], z = p.getZ() + o[1];
                level.explode(null, x, p.getY(), z, 4F, Level.ExplosionInteraction.NONE); // the original blast: hurts everything, the holder included
            }
            for (int i = 0; i < 2; i++) { // terrain: two ring points per tick, carved without drops (claims respected)
                int[] o = RING[(p.tickCount % 4 * 2 + i) % 8];
                Terrain.carve(level, p, new net.minecraft.world.phys.Vec3(p.getX() + o[0], p.getY(), p.getZ() + o[1]), 4F, 150F);
            }
            if (p.tickCount % 20 == 0) // 5% of the holder's max HP a second on top, so the punishment still bites at 1000 HP
                p.hurt(p.damageSources().magic(), Math.max(4F, p.getMaxHealth() * 0.05F));
            p.addEffect(new MobEffectInstance(MobEffects.WITHER, 20, 1, true, true));
        } else if (old == 10) {
            Radiation.cause(p, "punish", false);
        }
    }

    @Override
    public void stop(ServerPlayer p, StoneTree.Node n) { reset(p.getUUID(), p); }

    private static void reset(UUID id, ServerPlayer p) {
        if (TIMER.remove(id) != null && p != null) Radiation.cause(p, "punish", false);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { TIMER.remove(e.getEntity().getUUID()); }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent e) { TIMER.remove(e.getEntity().getUUID()); }
}
