package com.infinitystones.tensura.skill;

import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One Tensura UNIQUE skill per stone. Each slottable node of the original tree is a mode of this skill.
 * Learned automatically (free, no max-MP sacrifice) the first time the stone is carried; usable only while carried;
 * modes stay locked until bought in the tree.
 */
public class StoneSkill extends Skill {
    public final Stone stone;

    public StoneSkill(Stone stone) {
        super(SkillType.UNIQUE);
        this.stone = stone;
    }

    private List<StoneTree.Node> modes() { return StoneTree.of(stone).activeNodes; }

    @Nullable
    private StoneTree.Node node(int mode) {
        List<StoneTree.Node> m = modes();
        return mode >= 0 && mode < m.size() ? m.get(mode) : null;
    }

    @Override public ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("infinity", "textures/item/" + stone.key + "_stone.png");
    }
    @Override public double getDefaultAcquiringMagiculeCost() { return 0D; }
    /** Stone powers are used from the Infinity wheel, never from Tensura's slots/presets (user requirement). */
    @Override public boolean canBeSlotted(ManasSkillInstance instance, LivingEntity entity, int mode) { return false; }
    @Override public int getModes(ManasSkillInstance instance) { return Math.max(1, modes().size()); }

    @Override public String getModeId(ManasSkillInstance instance, int mode) {
        StoneTree.Node n = node(mode);
        return n == null ? "default" : n.modeId();
    }

    @Override public Component getModeName(ManasSkillInstance instance, int mode) {
        StoneTree.Node n = node(mode);
        return n == null ? Component.literal("-") : n.displayName().withStyle(ChatFormatting.GRAY);
    }

    /** Cycle only through modes the player has unlocked; -1 refuses when none is unlocked. */
    @Override public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        if (!(entity instanceof Player p)) return -1;
        int size = modes().size();
        for (int step = 1; step <= size; step++) {
            int m = Math.floorMod(mode + (reverse ? -step : step), size);
            if (StoneProgress.isUnlocked(p, stone, modes().get(m).id())) return m;
        }
        return -1;
    }

    @Override public boolean canInteractSkill(ManasSkillInstance instance, LivingEntity entity) {
        return entity instanceof Player p && stone.isHeldBy(p) && super.canInteractSkill(instance, entity);
    }

    @Override public boolean canActivateSkill(ManasSkillInstance instance, LivingEntity entity, int mode) {
        StoneTree.Node n = node(mode);
        return n != null && entity instanceof Player p && StoneProgress.isUnlocked(p, stone, n.id())
                && super.canActivateSkill(instance, entity, mode);
    }

    @Override public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        StoneTree.Node n = node(mode);
        if (n == null || !(entity instanceof ServerPlayer p)) return;
        NodeEffects.press(p, n, p.isShiftKeyDown()); // no cooldowns anywhere (user decision 18/11)
        instance.addMasteryPoint(entity);
    }

    @Override public boolean onHeld(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int mode) {
        StoneTree.Node n = node(mode);
        return n != null && entity instanceof ServerPlayer p && NodeEffects.held(p, n, heldTicks);
    }

    @Override public void onRelease(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int keyNumber, int mode) {
        StoneTree.Node n = node(mode);
        if (n == null || !(entity instanceof ServerPlayer p)) return;
        NodeEffects.release(p, n);
    }
}
