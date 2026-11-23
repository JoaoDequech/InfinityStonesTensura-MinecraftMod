package com.infinitystones.tensura.skill.ability.ported;

import com.google.gson.JsonElement;
import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.StoneTree;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * palladium:damage_immunity — while on, damage whose type is in one of the original "damage_sources" tags
 * (e.g. minecraft:is_fall, is_fire, is_explosion...) is cancelled.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class DamageImmunity implements Ported {
    private static final Map<UUID, Map<String, Set<TagKey<DamageType>>>> ACTIVE = new HashMap<>();

    @Override
    public void start(ServerPlayer p, StoneTree.Node n) {
        Set<TagKey<DamageType>> tags = new HashSet<>();
        if (n.raw().has("damage_sources") && n.raw().get("damage_sources").isJsonArray()) {
            for (JsonElement e : n.raw().getAsJsonArray("damage_sources")) {
                ResourceLocation rl = ResourceLocation.tryParse(e.getAsString().replace("#", ""));
                if (rl != null) tags.add(TagKey.create(Registries.DAMAGE_TYPE, rl));
            }
        }
        ACTIVE.computeIfAbsent(p.getUUID(), k -> new HashMap<>()).put(n.modeId(), tags);
    }

    @Override
    public void tick(ServerPlayer p, StoneTree.Node n, int ticks) {
        Map<String, Set<TagKey<DamageType>>> m = ACTIVE.get(p.getUUID());
        if (m == null || !m.containsKey(n.modeId())) start(p, n);
    }

    @Override
    public void stop(ServerPlayer p, StoneTree.Node n) {
        Map<String, Set<TagKey<DamageType>>> m = ACTIVE.get(p.getUUID());
        if (m != null) { m.remove(n.modeId()); if (m.isEmpty()) ACTIVE.remove(p.getUUID()); }
    }

    public static boolean immune(ServerPlayer p, net.minecraft.world.damagesource.DamageSource src) {
        Map<String, Set<TagKey<DamageType>>> m = ACTIVE.get(p.getUUID());
        if (m == null) return false;
        for (Set<TagKey<DamageType>> tags : m.values()) for (TagKey<DamageType> t : tags) if (src.is(t)) return true;
        return false;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void incoming(LivingIncomingDamageEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && immune(p, e.getSource())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { ACTIVE.remove(e.getEntity().getUUID()); }
}
