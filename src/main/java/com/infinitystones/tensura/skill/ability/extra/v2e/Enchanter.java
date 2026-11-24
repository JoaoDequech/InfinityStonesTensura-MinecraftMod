package com.infinitystones.tensura.skill.ability.extra.v2e;

import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.StoneProgress;
import com.infinitystones.tensura.skill.StoneTier;
import com.infinitystones.tensura.skill.StoneTree;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Ground;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Enchanter (Reality stone, hidden). Left click opens a table listing every enchantment of the game, modded ones too. Any
 * enchantment, at any level up to 255, goes on any stack of the player's equipment (main hand, off hand, armour), whatever
 * the item or the usual rules say; or it comes off again. No experience, no lapis, no cost. The server checks the stone and
 * the node again on every request. A small cauldron and an anvil appear in front of the caster for a few seconds (drawn on
 * the clients only, the world is not touched); the stack is changed when the flask is poured. If the stack was moved
 * meanwhile the same stack is looked for in the inventory, and when it is gone nothing happens.
 */
public final class Enchanter implements Ability {
    public static final Enchanter INSTANCE = new Enchanter();
    private static final String NODE = "enchanter";
    /** Server tick of the pour (the change happens now) and of the end of the show. */
    private static final int POUR = 54, TOTAL = 68;
    private static final double SHOW_RANGE = 96;
    private static final Map<UUID, Tasks.Handle> ACTIVE = new HashMap<>();

    @Override public Stone stone() { return Stone.REALITY; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        PacketDistributor.sendToPlayer(p, new V2eNet.Open());
        return true;
    }

    private static boolean allowed(ServerPlayer p) {
        StoneTree.Node n = StoneTree.of(Stone.REALITY).nodes.get(NODE);
        return n != null && Stone.REALITY.isHeldBy(p) && StoneProgress.isUnlocked(p, Stone.REALITY, NODE) && !StoneTier.hidden(p, n);
    }

    static void apply(ServerPlayer p, V2eNet.Apply msg) {
        if (msg.slot() < 0 || msg.slot() >= V2eNet.SLOTS.length || msg.level() < 0 || msg.level() > V2eNet.MAX_LEVEL) return;
        if (!p.isAlive() || p.isSpectator() || !allowed(p)) return;
        Tasks.Handle running = ACTIVE.get(p.getUUID());
        if (running != null && running.alive()) return;
        EquipmentSlot slot = V2eNet.SLOTS[msg.slot()];
        ItemStack target = p.getItemBySlot(slot);
        if (target.isEmpty()) return;
        Optional<Holder.Reference<Enchantment>> h = p.registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolder(ResourceKey.create(Registries.ENCHANTMENT, msg.id()));
        if (h.isEmpty()) return;
        ACTIVE.put(p.getUUID(), Tasks.add(new Show(p, p.serverLevel(), msg.slot(), slot, target.copy(), h.get(), msg.level())));
    }

    private static DataComponentType<ItemEnchantments> typeFor(ItemStack s) {
        return s.is(Items.ENCHANTED_BOOK) ? DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS;
    }

    private static void change(ItemStack s, Holder<Enchantment> e, int level) {
        DataComponentType<ItemEnchantments> type = typeFor(s);
        ItemEnchantments.Mutable m = new ItemEnchantments.Mutable(s.getOrDefault(type, ItemEnchantments.EMPTY));
        m.set(e, level);
        s.set(type, m.toImmutable());
    }

    private static final class Show implements Tasks.Task {
        final ServerPlayer p;
        final ServerLevel level;
        final int slotIndex;
        final EquipmentSlot slot;
        final ItemStack snapshot;
        final Holder<Enchantment> ench;
        final int lvl;
        Vec3 anchor = Vec3.ZERO;

        Show(ServerPlayer p, ServerLevel level, int slotIndex, EquipmentSlot slot, ItemStack snapshot, Holder<Enchantment> ench, int lvl) {
            this.p = p; this.level = level; this.slotIndex = slotIndex; this.slot = slot; this.snapshot = snapshot; this.ench = ench; this.lvl = lvl;
        }

        private void send(int flags) {
            V2eNet.Anim m = new V2eNet.Anim(p.getId(), slotIndex, snapshot, anchor.x, anchor.y, anchor.z, p.getYRot(), flags);
            for (ServerPlayer sp : level.players())
                if (sp == p || sp.position().distanceToSqr(anchor) <= SHOW_RANGE * SHOW_RANGE) PacketDistributor.sendToPlayer(sp, m);
        }

        private ItemStack find() {
            ItemStack cur = p.getItemBySlot(slot);
            if (ItemStack.matches(cur, snapshot)) return cur;
            var inv = p.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (!s.isEmpty() && ItemStack.matches(s, snapshot)) return s;
            }
            ItemStack held = p.containerMenu.getCarried();
            return !held.isEmpty() && ItemStack.matches(held, snapshot) ? held : ItemStack.EMPTY;
        }

        @Override
        public boolean tick(int age) {
            if (!p.isAlive() || p.isRemoved() || p.serverLevel() != level) {
                if (age > 0 && !p.isRemoved()) send(V2eNet.FLAG_CANCEL);
                return false;
            }
            if (age == 0) {
                double yaw = Math.toRadians(p.getYRot());
                Vec3 c = p.position().add(-Math.sin(yaw) * 2.6, 0, Math.cos(yaw) * 2.6);
                Vec3 g = Ground.below(level, c.add(0, 1.5, 0), 5);
                anchor = g.y < p.getY() - 1.5 || g.y > p.getY() + 1.0 ? new Vec3(c.x, p.getY(), c.z) : new Vec3(c.x, g.y, c.z);
                send(lvl == 0 ? V2eNet.FLAG_REMOVE : 0);
            }
            if (age == POUR) {
                ItemStack s = find();
                if (s.isEmpty()) { send(V2eNet.FLAG_CANCEL); return false; }
                change(s, ench, Mth.clamp(lvl, 0, V2eNet.MAX_LEVEL));
                p.inventoryMenu.broadcastChanges();
            }
            return age < TOTAL;
        }

        @Override public void end() { ACTIVE.remove(p.getUUID()); }
    }

    private Enchanter() {}
}
