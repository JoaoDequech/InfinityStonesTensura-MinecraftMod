package com.infinitystones.tensura.shard;

import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.StoneTree;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** The mini gem: a small, clearly temporary holographic copy of a stone. All real state lives in {@link ShardRegistry}. */
public final class ShardItem extends Item {
    public ShardItem(Properties props) { super(props); }

    @Override public Component getName(ItemStack stack) {
        ShardData d = stack.get(ShardRegistries.SHARD.get());
        if (d == null || d.stone() < 0 || d.stone() >= Stone.values().length) return super.getName(stack);
        return Component.translatable("item.infinity.mini_joia.of", Component.translatable("infinity.skill." + Stone.values()[d.stone()].key + "_stone"))
                .withStyle(s -> s.withColor(Stone.values()[d.stone()].color() & 0xFFFFFF));
    }

    @Override public boolean isFoil(ItemStack stack) { return true; }

    @Override public void inventoryTick(ItemStack stack, Level level, Entity holder, int slot, boolean selected) {
        if (level.isClientSide || !(holder instanceof ServerPlayer p)) return;
        ShardLife.tickStack(p, stack);
    }

    /** Item entity tick: a dead mini gem on the ground shatters. */
    @Override public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (entity.level() instanceof ServerLevel && ShardSweep.dead(stack)) {
            ShardSweep.scrubEntity(entity, false);
            return true;
        }
        return false;
    }

    /** Fraction of the life used up (0..1), 0 for infinite ones. Client-safe: only needs the level's game time. */
    public static float decay(ItemStack stack, long gameTime) {
        ShardData d = stack.get(ShardRegistries.SHARD.get());
        if (d == null || d.infinite() || d.total() <= 0) return 0F;
        long left = d.expiry() - gameTime;
        return Math.max(0F, Math.min(1F, 1F - left / (float) d.total()));
    }

    /** 0 until the last 10 seconds, then rises to 1 at the moment it runs out (drives the "final" cracks of the model). */
    public static float finalFrac(ItemStack stack, long gameTime) {
        ShardData d = stack.get(ShardRegistries.SHARD.get());
        if (d == null || d.infinite()) return 0F;
        long left = d.expiry() - gameTime;
        if (left > ShardBelt.FINAL_TICKS) return 0F;
        return Math.max(0F, Math.min(1F, 1F - left / (float) ShardBelt.FINAL_TICKS));
    }

    /** Whole seconds left when inside the final countdown (10..1), else 0. */
    public static int finalSeconds(ItemStack stack, long gameTime) {
        ShardData d = stack.get(ShardRegistries.SHARD.get());
        if (d == null || d.infinite()) return 0;
        long left = d.expiry() - gameTime;
        return left > 0 && left <= ShardBelt.FINAL_TICKS ? (int) ((left + 19) / 20) : 0;
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        ShardData d = stack.get(ShardRegistries.SHARD.get());
        if (d == null) return;
        Level lv = ctx.level();
        int fin = lv == null ? 0 : finalSeconds(stack, lv.getGameTime());
        if (fin > 0) tip.add(Component.translatable("item.infinity.mini_joia.final", fin).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        tip.add(Component.translatable("item.infinity.mini_joia.temp").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
        if (d.stone() >= 0 && d.stone() < Stone.values().length) {
            Stone st = Stone.values()[d.stone()];
            List<String> ids = d.powers();
            for (String id : ids) {
                StoneTree.Node n = StoneTree.of(st).nodes.get(id);
                tip.add(Component.literal(" \u25C6 ").withStyle(s -> s.withColor(st.color() & 0xFFFFFF))
                        .append(n == null ? Component.literal(id) : n.displayName().withStyle(ChatFormatting.WHITE)));
            }
        }
        Level level = ctx.level();
        if (d.infinite()) tip.add(Component.translatable("item.infinity.mini_joia.time", Component.literal("\u221E").withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.GRAY));
        else if (level != null) {
            long left = d.expiry() - level.getGameTime();
            tip.add(Component.translatable("item.infinity.mini_joia.time", left <= 0
                    ? Component.translatable("item.infinity.mini_joia.expired").withStyle(ChatFormatting.RED)
                    : Component.literal(formatTicks(left)).withStyle(left < 1200 ? ChatFormatting.RED : ChatFormatting.AQUA)).withStyle(ChatFormatting.GRAY));
        }
        tip.add(Component.translatable("item.infinity.mini_joia.bound", d.boundTo() == null
                ? Component.translatable("item.infinity.mini_joia.unbound").withStyle(ChatFormatting.DARK_GRAY)
                : Component.literal(d.boundName()).withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("item.infinity.mini_joia.creator", Component.literal(d.creatorName()).withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("#" + d.code()).withStyle(ChatFormatting.DARK_GRAY));
    }

    public static String formatTicks(long ticks) {
        long s = Math.max(0, ticks / 20);
        long h = s / 3600, m = s % 3600 / 60, sec = s % 60;
        if (h > 0) return h + "h " + String.format("%02d", m) + "m";
        if (m > 0) return m + "m " + String.format("%02d", sec) + "s";
        return sec + "s";
    }
}
