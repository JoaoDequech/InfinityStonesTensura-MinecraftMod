package com.infinitystones.tensura.skill.ability.extra.fear;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** "Roubar itens": a take-only chest view of the victim's live inventory (hotbar, main, armour, off hand). */
final class FearSteal {
    static void open(ServerPlayer caster, ServerPlayer victim) {
        caster.openMenu(new SimpleMenuProvider((id, inv, pl) -> new Menu(id, caster, new View(caster, victim)),
                Component.translatable("infinity.fear.steal", victim.getName())));
    }

    private static final class View implements Container {
        final ServerPlayer viewer, victim;
        View(ServerPlayer viewer, ServerPlayer victim) { this.viewer = viewer; this.victim = victim; }

        private boolean real(int i) { return i >= 0 && i < victim.getInventory().getContainerSize(); }

        @Override public int getContainerSize() { return 45; }
        @Override public boolean isEmpty() { return victim.getInventory().isEmpty(); }
        @Override public ItemStack getItem(int i) { return real(i) ? victim.getInventory().getItem(i) : ItemStack.EMPTY; }
        @Override public ItemStack removeItem(int i, int n) { return real(i) ? victim.getInventory().removeItem(i, n) : ItemStack.EMPTY; }
        @Override public ItemStack removeItemNoUpdate(int i) { return real(i) ? victim.getInventory().removeItemNoUpdate(i) : ItemStack.EMPTY; }
        @Override public void setItem(int i, ItemStack s) { if (real(i)) victim.getInventory().setItem(i, s); }
        @Override public void setChanged() { victim.getInventory().setChanged(); victim.inventoryMenu.broadcastChanges(); }
        @Override public boolean stillValid(Player p) {
            return p == viewer && viewer.isAlive() && !viewer.hasDisconnected() && victim.isAlive() && !victim.hasDisconnected() && FearSessions.isVictim(victim);
        }
        @Override public void clearContent() {}
    }

    private static final class Menu extends ChestMenu {
        private final ServerPlayer viewer;
        private final View view;

        Menu(int id, ServerPlayer viewer, View view) {
            super(MenuType.GENERIC_9x5, id, viewer.getInventory(), view, 5);
            this.viewer = viewer; this.view = view;
            for (int i = 0; i < 45; i++) {
                Slot original = this.slots.get(i);
                Slot take = new Slot(view, i, original.x, original.y) {
                    @Override public boolean mayPlace(ItemStack stack) { return false; }
                    @Override public boolean mayPickup(Player player) { return view.stillValid(player); }
                };
                take.index = i;
                this.slots.set(i, take);
            }
        }

        @Override
        public void clicked(int slot, int button, ClickType type, Player player) {
            if (player != viewer || !view.stillValid(player)) { viewer.closeContainer(); return; }
            super.clicked(slot, button, type, player);
            view.setChanged();
        }

        @Override
        public ItemStack quickMoveStack(Player player, int slot) {
            if (player != viewer || !view.stillValid(player) || slot < 0 || slot >= 45) return ItemStack.EMPTY;
            ItemStack out = super.quickMoveStack(player, slot);
            view.setChanged();
            return out;
        }

        @Override public boolean stillValid(Player player) { return view.stillValid(player); }
    }

    private FearSteal() {}
}
