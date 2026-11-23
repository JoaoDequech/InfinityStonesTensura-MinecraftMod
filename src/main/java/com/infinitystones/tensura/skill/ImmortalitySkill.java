package com.infinitystones.tensura.skill;

import com.infinitystones.tensura.InfinityStones;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.skill.Skill;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * The Soul Stone's original "immortal" node, now a switch the holder controls (required for the shared server).
 * OFF by default. While ON and the Soul Stone is carried, death is cancelled and the holder is left at 1 health —
 * exactly the original behaviour (damage is still taken). Pressing its Tensura slot key flips it.
 */
public class ImmortalitySkill extends Skill {
    public ImmortalitySkill() { super(SkillType.ULTIMATE); }

    @Override public ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("infinity", "textures/item/soul_stone.png");
    }
    @Override public double getDefaultAcquiringMagiculeCost() { return 0D; }
    @Override public boolean canBeToggled(ManasSkillInstance instance, LivingEntity entity) { return true; }
    /** Switched from the Infinity wheel, not from Tensura's slots. */
    @Override public boolean canBeSlotted(ManasSkillInstance instance, LivingEntity entity, int mode) { return false; }

    /** The Soul tree node this switch belongs to (its wheel toggle is the single source of truth). */
    public static StoneTree.Node node() { return StoneTree.of(Stone.SOUL).nodes.get("immortal"); }

    /** Server-side switch (wheel, Tensura skill menu). */
    public static void toggle(net.minecraft.server.level.ServerPlayer player) {
        StoneTree.Node n = node();
        if (n != null && !StoneTier.hidden(player, n)) NodeEffects.setToggle(player, n, !NodeEffects.isToggled(player, n));
    }

    @Override public boolean canInteractSkill(ManasSkillInstance instance, LivingEntity entity) {
        // Deliberately not gated by Tensura status effects: the switch must always respond to its owner.
        return entity instanceof Player p && Stone.SOUL.isHeldBy(p) && StoneTier.has(p, Stone.SOUL);
    }

    @Override public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (entity instanceof net.minecraft.server.level.ServerPlayer sp) toggle(sp);
    }

    /** ON (wheel switch), unlocked and the Soul Stone carried. */
    public static boolean isActive(LivingEntity entity) {
        if (!(entity instanceof Player p) || !Stone.SOUL.isHeldBy(p) || p.getTags().contains("burn")) return false;
        StoneTree.Node n = node();
        return n != null && NodeEffects.isToggled(p, n) && StoneProgress.isUnlocked(p, Stone.SOUL, n.id());
    }

    @EventBusSubscriber(modid = InfinityStones.MOD_ID)
    public static final class Guard {
        /** Safety net independent of skill-hook ordering or other mods' vetoes. */
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void onDeath(LivingDeathEvent event) {
            if (isActive(event.getEntity())) {
                event.setCanceled(true);
                event.getEntity().setHealth(1F);
            }
        }
    }
}
