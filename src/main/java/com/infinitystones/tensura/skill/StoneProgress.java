package com.infinitystones.tensura.skill;

import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Tree unlock state. Bought nodes are stored in the stone skill instance tag ("infinity_bought"), so they live in
 * ManasCore storage (saved, synced, copied on respawn). Unlock rules mirror the original Palladium conditions:
 * a node is unlocked when it is bought (or free), all parent nodes are unlocked and all required stones are carried.
 */
public final class StoneProgress {
    private static final String BOUGHT = "infinity_bought";

    public static Optional<ManasSkillInstance> instance(Player player, Stone stone) {
        return SkillAPI.getSkillsFrom(player).getSkill(stone.skillId());
    }

    public static Set<String> bought(ManasSkillInstance inst) {
        Set<String> out = new HashSet<>();
        CompoundTag tag = inst.getTag();
        if (tag != null && tag.contains(BOUGHT, Tag.TAG_LIST)) {
            ListTag list = tag.getList(BOUGHT, Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) out.add(list.getString(i));
        }
        return out;
    }

    private static boolean isBought(ManasSkillInstance inst, String nodeId) {
        CompoundTag tag = inst.getTag();
        if (tag == null || !tag.contains(BOUGHT, Tag.TAG_LIST)) return false;
        ListTag list = tag.getList(BOUGHT, Tag.TAG_STRING);
        for (int i = 0, n = list.size(); i < n; i++) if (list.getString(i).equals(nodeId)) return true;
        return false;
    }

    public static boolean isUnlocked(Player player, Stone stone, String nodeId) {
        return isUnlocked(player, stone, nodeId, heldStones(player), 0);
    }

    public static boolean isUnlocked(Player player, Stone stone, String nodeId, Set<Stone> held) {
        return isUnlocked(player, stone, nodeId, held, 0);
    }

    /** Parent check for a child node: a veiled node counts as satisfied when its own requirements are. */
    public static boolean parentOk(Player player, Stone stone, String parentId, Set<Stone> held) {
        return isUnlocked(player, stone, parentId, held, 1);
    }

    public static Set<Stone> heldStones(Player player) {
        Set<Stone> held = java.util.EnumSet.noneOf(Stone.class);
        int mask = Stone.heldMask(player);
        if (mask == 0) return held;
        for (Stone s : Stone.values()) if ((mask >> s.ordinal() & 1) != 0) held.add(s);
        return held;
    }

    private static boolean isUnlocked(Player player, Stone stone, String nodeId, Set<Stone> held, int depth) {
        if (depth > 32) return false;
        StoneTree.Node node = StoneTree.of(stone).nodes.get(nodeId);
        if (node == null) return false;
        if (depth == 0 && com.infinitystones.tensura.shard.ShardGrants.grants(player, stone, nodeId)) return true; // lent by a mini gem
        boolean veiled = StoneTier.hidden(player, node);
        if (veiled && depth == 0) return false;
        Optional<ManasSkillInstance> inst = instance(player, stone);
        if (inst.isEmpty()) return false;
        if (!veiled && node.cost() != null && !isBought(inst.get(), nodeId)) return false;
        for (String parent : node.parents()) if (!isUnlocked(player, stone, parent, held, depth + 1)) return false;
        for (String power : node.requiresPowers()) {
            Stone other = Stone.byOriginalPower(power);
            if (other != null && !held.contains(other)) return false;
        }
        // powers of the complete gauntlet need the gauntlet itself, not just six loose stones
        return depth > 0 || !GauntletStones.isGauntletPower(node) || GauntletStones.wearsComplete(player);
    }

    public enum BuyResult { OK, UNKNOWN_NODE, NOT_BUYABLE, ALREADY_OWNED, NEED_STONE, LOCKED_PARENT, NEED_OTHER_STONES, NOT_ENOUGH }

    /** Validates and pays for a node. Server side only. */
    public static BuyResult buy(ServerPlayer player, Stone stone, String nodeId) {
        StoneTree.Node node = StoneTree.of(stone).nodes.get(nodeId);
        if (node == null || StoneTier.hidden(player, node)) return BuyResult.UNKNOWN_NODE;
        if (node.cost() == null) return BuyResult.NOT_BUYABLE;
        if (!stone.ownedBy(player)) return BuyResult.NEED_STONE;
        Optional<ManasSkillInstance> inst = instance(player, stone);
        if (inst.isEmpty()) return BuyResult.NEED_STONE;
        if (bought(inst.get()).contains(nodeId)) return BuyResult.ALREADY_OWNED;
        Set<Stone> held = heldStones(player);
        for (String parent : node.parents()) if (!parentOk(player, stone, parent, held)) return BuyResult.LOCKED_PARENT;
        for (String power : node.requiresPowers()) {
            Stone other = Stone.byOriginalPower(power);
            if (other != null && !other.ownedBy(player)) return BuyResult.NEED_OTHER_STONES;
        }
        if (!pay(player, node.cost())) return BuyResult.NOT_ENOUGH;
        CompoundTag tag = inst.get().getOrCreateTag();
        ListTag list = tag.contains(BOUGHT, Tag.TAG_LIST) ? tag.getList(BOUGHT, Tag.TAG_STRING) : new ListTag();
        list.add(StringTag.valueOf(nodeId));
        tag.put(BOUGHT, list);
        inst.get().markDirty();
        SkillAPI.getSkillsFrom(player).markDirty();
        NodeEffects.onBought(player, stone, node);
        return BuyResult.OK;
    }

    private static boolean pay(ServerPlayer player, StoneTree.Cost cost) {
        if (player.getAbilities().instabuild) return true;
        if ("xp_levels".equals(cost.type())) {
            if (player.experienceLevel < cost.amount()) return false;
            player.giveExperienceLevels(-cost.amount());
            return true;
        }
        if (!cost.isItem() || cost.item() == null) return false;
        Item item = BuiltInRegistries.ITEM.get(cost.item());
        int have = 0;
        for (ItemStack s : player.getInventory().items) if (s.is(item)) have += s.getCount();
        if (have < cost.amount()) return false;
        int left = cost.amount();
        for (ItemStack s : player.getInventory().items) {
            if (left <= 0) break;
            if (!s.is(item)) continue;
            int take = Math.min(left, s.getCount());
            s.shrink(take);
            left -= take;
        }
        player.getInventory().setChanged();
        return true;
    }

    public static Component describe(BuyResult r) {
        return Component.translatable("infinity.tree.result." + r.name().toLowerCase());
    }

    private StoneProgress() {}
}
