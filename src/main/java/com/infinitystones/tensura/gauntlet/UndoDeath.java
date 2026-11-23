package com.infinitystones.tensura.gauntlet;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * New gauntlet power — Undo Death: the aimed player (or you, aiming at nobody) gets back what their last death took:
 * every item still lying where they died flies back into their inventory, and the experience levels they lost.
 * Only items that still exist come back (nothing is duplicated: what someone else picked up stays theirs).
 * Remembers each player's last death until the server restarts.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class UndoDeath {
    private record Death(ResourceKey<Level> dim, List<UUID> items, int level, float progress) {}
    private static final Map<UUID, Death> LAST = new HashMap<>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void drops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.isCanceled()) return;
        List<UUID> ids = new ArrayList<>();
        for (ItemEntity it : event.getDrops()) ids.add(it.getUUID());
        LAST.put(p.getUUID(), new Death(p.level().dimension(), ids, p.experienceLevel, p.experienceProgress));
    }

    public static boolean press(ServerPlayer p) {
        if (!GauntletPowers.ready(p, false)) return false;
        ServerPlayer t = Aim.entity(p, 64, ServerPlayer.class, o -> o.isAlive());
        if (t == null) t = p;
        Death d = LAST.remove(t.getUUID());
        if (d == null) return Fx.fail(p, "infinity.gauntlet.undo.nothing");
        ServerLevel l = p.server.getLevel(d.dim());
        int back = 0;
        if (l != null) {
            for (UUID id : d.items()) {
                Entity e = l.getEntity(id);
                if (!(e instanceof ItemEntity it) || !it.isAlive()) continue;
                l.sendParticles(ParticleTypes.REVERSE_PORTAL, it.getX(), it.getY() + 0.2, it.getZ(), 6, 0.1, 0.1, 0.1, 0.02);
                var stack = it.getItem().copy();
                it.discard();
                back += stack.getCount();
                if (!t.getInventory().add(stack)) t.drop(stack, false);
            }
        }
        if (t.experienceLevel < d.level()) { t.setExperienceLevels(d.level()); t.experienceProgress = d.progress(); t.giveExperiencePoints(0); }
        ServerLevel tl = t.serverLevel();
        Fx.shell(tl, t.position().add(0, 1, 0), 1.2, 40, Fx.dust(Stone.SOUL, 1.2F));
        Fx.at(tl, ParticleTypes.TOTEM_OF_UNDYING, t.position().add(0, 1, 0), 30, 0.4, 0.3);
        Fx.sound(tl, t.position(), SoundEvents.TOTEM_USE, 0.5F, 1.2F);
        Fx.bar(p, "infinity.gauntlet.undo.done", t.getDisplayName(), back);
        return true;
    }

    private UndoDeath() {}
}
