package com.infinitystones.tensura.gauntlet;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.GauntletStones;
import com.infinitystones.tensura.skill.Stone;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The ONE Infinity Gauntlet socket screen (replaces the original Palladium "cws_menu"; opened by right click with the
 * gauntlet, see {@link GauntletEvents}; crouch + right click = quick insert). Six sockets, each accepting only its own
 * gem, stored in the gauntlet stack (vanilla CONTAINER component, slot = stone ordinal, see {@link GauntletStones}),
 * so the stones travel with the item and every client can render them. The gauntlet's own inventory slot is locked
 * while the screen is open and every change is written straight back, so nothing can be duplicated or lost.
 * Drawn by client/port/GauntletScreen (gauntlet silhouette with glowing sockets).
 */
public final class GauntletMenu extends AbstractContainerMenu {
    /** Socket order on screen: four knuckles, palm, thumb. */
    public static final Stone[] ORDER = {Stone.SPACE, Stone.REALITY, Stone.POWER, Stone.MIND, Stone.TIME, Stone.SOUL};
    public static final int[][] POS = {{50, 30}, {78, 22}, {106, 22}, {134, 30}, {92, 64}, {40, 78}};

    public static MenuType<GauntletMenu> TYPE;

    private final Player player;
    private final InteractionHand hand;
    private final ItemStack gauntlet;
    private final int lockedSlot;
    private final SimpleContainer sockets = new SimpleContainer(6);
    private boolean loading;
    private int lastMask;

    /** Client constructor (from the open packet). */
    public static GauntletMenu client(int id, Inventory inv, net.minecraft.network.RegistryFriendlyByteBuf buf) {
        return new GauntletMenu(id, inv, buf.readEnum(InteractionHand.class));
    }

    public GauntletMenu(int id, Inventory inv, InteractionHand hand) {
        super(TYPE, id);
        this.player = inv.player;
        this.hand = hand;
        this.gauntlet = player.getItemInHand(hand);
        this.lockedSlot = hand == InteractionHand.MAIN_HAND ? inv.selected : -1;
        loading = true;
        NonNullList<ItemStack> six = GauntletStones.sockets(gauntlet);
        for (int i = 0; i < ORDER.length; i++) sockets.setItem(i, six.get(ORDER[i].ordinal()));
        loading = false;
        lastMask = GauntletStones.mask(gauntlet);
        sockets.addListener(c -> save());
        for (int i = 0; i < ORDER.length; i++) {
            Stone s = ORDER[i];
            addSlot(new Slot(sockets, i, POS[i][0] + 1, POS[i][1] + 1) {
                @Override public boolean mayPlace(ItemStack st) { return GauntletStones.isGem(st, s); }
                @Override public int getMaxStackSize() { return 1; }
                @Override public int getMaxStackSize(ItemStack st) { return 1; }
            });
        }
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, 9 + r * 9 + c, 18 + c * 18, 112 + r * 18));
        for (int c = 0; c < 9; c++) {
            final int idx = c;
            addSlot(new Slot(inv, c, 18 + c * 18, 170) {
                @Override public boolean mayPickup(Player p) { return idx != lockedSlot; }
                @Override public boolean mayPlace(ItemStack st) { return idx != lockedSlot; }
            });
        }
    }

    public Stone stoneAt(int socket) { return ORDER[socket]; }

    private void save() {
        if (loading || player.level().isClientSide || !GauntletStones.isGauntlet(gauntlet)) return;
        List<ItemStack> six = new ArrayList<>(6);
        for (int i = 0; i < 6; i++) six.add(ItemStack.EMPTY);
        for (int i = 0; i < ORDER.length; i++) six.set(ORDER[i].ordinal(), sockets.getItem(i));
        GauntletStones.setSockets(gauntlet, six);
        int mask = GauntletStones.mask(gauntlet);
        if (mask != lastMask && player instanceof ServerPlayer sp) {
            GauntletEvents.socketFx(sp, lastMask, mask);
            for (Stone s : Stone.values()) {
                int bit = 1 << s.ordinal();
                if ((mask & bit) != (lastMask & bit)) ArtifactSwap.circle(sp, s, (mask & bit) != 0);
            }
        }
        lastMask = mask;
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player p) {
        if (type == ClickType.SWAP && (button == lockedSlot || (hand == InteractionHand.OFF_HAND && button == 40))) return;
        super.clicked(slotId, button, type, p);
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack st = slot.getItem();
        ItemStack copy = st.copy();
        if (index < 6) {
            if (!moveItemStackTo(st, 6, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            for (int i = 0; i < 6 && !moved; i++) {
                if (!slots.get(i).hasItem() && slots.get(i).mayPlace(st)) { slots.get(i).set(st.split(1)); moved = true; }
            }
            if (!moved) return ItemStack.EMPTY;
        }
        if (st.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return copy.getCount() == st.getCount() ? ItemStack.EMPTY : copy;
    }

    @Override
    public boolean stillValid(Player p) { return p.isAlive() && p.getItemInHand(hand) == gauntlet && GauntletStones.isGauntlet(gauntlet); }

    @Override
    public void removed(Player p) {
        super.removed(p);
        save();
    }

    @EventBusSubscriber(modid = InfinityStones.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent
        public static void register(RegisterEvent e) {
            e.register(Registries.MENU, helper -> {
                TYPE = IMenuTypeExtension.create(GauntletMenu::client);
                helper.register(InfinityStones.id("gauntlet"), TYPE);
            });
        }
        private Registration() {}
    }
}
