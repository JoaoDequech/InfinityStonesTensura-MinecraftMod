package com.infinitystones.tensura.skill.ability.ported;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.StoneTree;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Port of palladium:reality_confiscation (Reality, press, dual). Aims at a player within {@code max_distance} (PvP
 * rules via {@link PortFx#canAffect}) and binds them in place.
 * <ul>
 *   <li>Left click (alt=false): opens the victim's REAL inventory (main, hotbar, armour, offhand and — when Curios is
 *       installed — equipped accessories) in a vanilla 9x6 chest screen for up to 60 s. Take-only: nothing can be
 *       inserted. The screen re-validates every tick (alive, same dimension, within 64 blocks, PvP/team rules, caster
 *       still carries the Reality Stone) and closes when invalid; closing it releases the victim.</li>
 *   <li>Right click / sneaking (alt=true): the victim drops everything they carry (1 s bind).</li>
 * </ul>
 * The menu reads/writes the live stacks (no snapshot), so items move, never copy. While bound the victim is held in
 * place (movement/jump attributes zeroed transiently + anchor teleport), cannot interact, attack, break blocks or open
 * containers, and cannot deal damage. Nothing is persisted, so a restart/relog can never leave a player trapped.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class RealityConfiscation implements Ported {
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final ResourceLocation FREEZE_ID = InfinityStones.id("reality_confiscation_bind");
    private static final double MAX_SESSION_DIST_SQR = 64D * 64D;

    private static final class Session {
        final ServerPlayer caster, target; final Vec3 anchor; final long expiresAt; final boolean eject; final int rgb;
        @Nullable Menu menu;
        Session(ServerPlayer caster, ServerPlayer target, Vec3 anchor, long expiresAt, boolean eject, int rgb) {
            this.caster = caster; this.target = target; this.anchor = anchor; this.expiresAt = expiresAt;
            this.eject = eject; this.rgb = rgb;
        }
    }

    public RealityConfiscation() {}

    @Override
    public Boolean press(ServerPlayer caster, StoneTree.Node n, boolean alt) {
        if (isFrozen(caster)) return Boolean.FALSE;
        float maxDistance = PortFx.f(n, "max_distance", 32F);
        var aimed = PortFx.aimed(caster, maxDistance, e -> e instanceof ServerPlayer sp && sp != caster
                && !sp.isPassenger() && !sp.isVehicle() && !sp.hasDisconnected() && PortFx.canAffect(caster, sp));
        if (!(aimed instanceof ServerPlayer target) || SESSIONS.containsKey(target.getUUID())) {
            caster.displayClientMessage(Component.translatable("ability.infinity.reality_stone.reality_confiscation.failed"), true);
            return Boolean.FALSE;
        }
        // Return both carried stacks normally before exposing the target inventory.
        caster.closeContainer();
        target.closeContainer();
        SESSIONS.entrySet().removeIf(en -> {
            if (en.getValue().caster != caster) return false;
            release(en.getValue());
            return true;
        });
        int rgb = n.stone().color() & 0xFFFFFF;
        ServerLevel level = target.serverLevel();
        Session session = new Session(caster, target, target.position(), level.getGameTime() + (alt ? 20L : 1200L), alt, rgb);
        SESSIONS.put(target.getUUID(), session);
        bind(target);
        target.setDeltaMovement(Vec3.ZERO);
        target.fallDistance = 0F;
        target.connection.teleport(target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot());

        DustParticleOptions ruby = PortFx.dust(rgb, 0.8F), glow = PortFx.dust(0xFF6699, 0.7F);
        Vec3 a = caster.getEyePosition(), b = target.getBoundingBox().getCenter();
        for (int i = 0; i <= 32; i++) {
            Vec3 pt = a.lerp(b, i / 32D);
            level.sendParticles(i % 3 == 0 ? glow : ruby, pt.x, pt.y, pt.z, 1, 0D, 0D, 0D, 0D);
        }
        PortFx.sound(level, target.position(), n, "infinity:reality_stone", 0.7F, 0.65F);
        PortFx.sound(level, target.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.7F, 0.8F);

        if (alt) {
            for (SlotAccess slot : slots(target)) {
                ItemStack stack = slot.get();
                if (stack.isEmpty()) continue;
                slot.set(ItemStack.EMPTY);
                if (target.drop(stack, true, false) == null) slot.set(stack);
            }
            target.getInventory().setChanged();
            target.inventoryMenu.broadcastChanges();
        } else {
            caster.openMenu(new SimpleMenuProvider((id, inv, player) -> {
                Menu menu = new Menu(id, caster, target, () -> SESSIONS.get(target.getUUID()) == session && valid(session));
                session.menu = menu;
                return menu;
            }, Component.translatable("ability.infinity.reality_stone.reality_confiscation.inventory", target.getName())));
            if (session.menu == null || caster.containerMenu != session.menu) {
                SESSIONS.remove(target.getUUID());
                release(session);
                return Boolean.FALSE;
            }
        }
        return Boolean.TRUE;
    }

    /** True while this player is bound by someone's confiscation (exposed so the wheel may block their abilities). */
    public static boolean isFrozen(@Nullable Entity entity) {
        if (SESSIONS.isEmpty() || !(entity instanceof ServerPlayer player)) return false;
        Session s = SESSIONS.get(player.getUUID());
        return s != null && s.target == player && valid(s);
    }

    private static boolean valid(Session s) {
        if (!s.caster.isAlive() || !s.target.isAlive() || s.caster.isRemoved() || s.target.isRemoved()
                || s.caster.hasDisconnected() || s.target.hasDisconnected()
                || s.caster.level() != s.target.level()
                || s.target.level().getGameTime() >= s.expiresAt
                || s.caster.distanceToSqr(s.target) > MAX_SESSION_DIST_SQR
                || !PortFx.canAffect(s.caster, s.target)
                || !Stone.REALITY.isHeldBy(s.caster)) return false;
        return s.eject || (s.menu != null && s.caster.containerMenu == s.menu);
    }

    // ---------------- bind ----------------

    private static void bind(ServerPlayer target) {
        AttributeModifier mod = new AttributeModifier(FREEZE_ID, -1D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        for (var attr : List.of(Attributes.MOVEMENT_SPEED, Attributes.JUMP_STRENGTH)) {
            AttributeInstance inst = target.getAttribute(attr);
            if (inst != null && !inst.hasModifier(FREEZE_ID)) inst.addTransientModifier(mod);
        }
    }

    private static void unbind(ServerPlayer target) {
        for (var attr : List.of(Attributes.MOVEMENT_SPEED, Attributes.JUMP_STRENGTH)) {
            AttributeInstance inst = target.getAttribute(attr);
            if (inst != null) inst.removeModifier(FREEZE_ID);
        }
    }

    private static void release(Session s) {
        unbind(s.target);
        if (s.menu != null && s.caster.containerMenu == s.menu) s.caster.closeContainer();
        if (!s.target.hasDisconnected() && s.target.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CHERRY_LEAVES, s.target.getX(), s.target.getY() + 1D, s.target.getZ(),
                    18, 0.4D, 0.7D, 0.4D, 0.01D);
            PortFx.sound(level, s.target.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.6F, 1.4F);
        }
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post e) {
        if (SESSIONS.isEmpty()) return;
        List<Session> ended = new ArrayList<>();
        SESSIONS.entrySet().removeIf(en -> {
            if (valid(en.getValue())) return false;
            ended.add(en.getValue());
            return true;
        });
        ended.forEach(RealityConfiscation::release);
        for (Session s : SESSIONS.values()) {
            ServerPlayer t = s.target;
            t.setDeltaMovement(Vec3.ZERO);
            t.fallDistance = 0F;
            if (t.isFallFlying()) t.stopFallFlying(); // no elytra escape
            if (t.containerMenu != t.inventoryMenu) t.closeContainer();
            if (t.tickCount % 5 == 0) {
                t.connection.teleport(s.anchor.x, s.anchor.y, s.anchor.z, t.getYRot(), t.getXRot());
                pulse(t, s.rgb);
            }
        }
    }

    private static void pulse(ServerPlayer t, int rgb) {
        ServerLevel level = t.serverLevel();
        DustParticleOptions ruby = PortFx.dust(rgb, 0.8F), glow = PortFx.dust(0xFF6699, 0.7F);
        double phase = level.getGameTime() * 0.12D;
        double r = t.getBbWidth() * 0.65D + 0.25D + Math.sin(phase) * 0.08D;
        for (int ring = 0; ring < 3; ring++) for (int i = 0; i < 16; i++) {
            double a = phase + i * Math.PI / 8D;
            level.sendParticles(ring == 1 ? glow : ruby, t.getX() + Math.cos(a) * r,
                    t.getY() + t.getBbHeight() * (0.15D + ring * 0.35D), t.getZ() + Math.sin(a) * r, 1, 0D, 0D, 0D, 0D);
        }
    }

    // A bound victim cannot act (the original rejected these packets with a mixin).
    private static void block(PlayerInteractEvent e) {
        if (e instanceof ICancellableEvent c && isFrozen(e.getEntity())) c.setCanceled(true);
    }
    @SubscribeEvent
    public static void useItem(PlayerInteractEvent.RightClickItem e) { block(e); }
    @SubscribeEvent
    public static void useBlock(PlayerInteractEvent.RightClickBlock e) { block(e); }
    @SubscribeEvent
    public static void hitBlock(PlayerInteractEvent.LeftClickBlock e) { block(e); }
    @SubscribeEvent
    public static void useEntity(PlayerInteractEvent.EntityInteract e) { block(e); }
    @SubscribeEvent
    public static void useEntityAt(PlayerInteractEvent.EntityInteractSpecific e) { block(e); }
    @SubscribeEvent
    public static void attack(AttackEntityEvent e) { if (isFrozen(e.getEntity())) e.setCanceled(true); }
    @SubscribeEvent
    public static void breakBlock(BlockEvent.BreakEvent e) { if (isFrozen(e.getPlayer())) e.setCanceled(true); }
    @SubscribeEvent
    public static void toss(net.neoforged.neoforge.event.entity.item.ItemTossEvent e) {
        if (!isFrozen(e.getPlayer())) return;
        e.setCanceled(true);
        e.getPlayer().getInventory().add(e.getEntity().getItem()); // put the thrown stack back
    }
    @SubscribeEvent
    public static void swapHands(net.neoforged.neoforge.event.entity.living.LivingSwapItemsEvent.Hands e) { if (isFrozen(e.getEntity())) e.setCanceled(true); }
    @SubscribeEvent
    public static void damage(LivingIncomingDamageEvent e) { if (isFrozen(e.getSource().getEntity())) e.setCanceled(true); }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        Player p = e.getEntity();
        if (p instanceof ServerPlayer sp) unbind(sp);
        List<Session> ended = new ArrayList<>();
        SESSIONS.entrySet().removeIf(en -> {
            Session s = en.getValue();
            if (s.caster != p && s.target != p) return false;
            ended.add(s);
            return true;
        });
        ended.forEach(RealityConfiscation::release);
    }

    // ---------------- live slot access ----------------

    /** One live slot of the victim (not a snapshot). */
    private record SlotAccess(Supplier<ItemStack> getter, Consumer<ItemStack> setter) {
        ItemStack get() { return getter.get(); }
        void set(ItemStack stack) { setter.accept(stack); }
    }

    /** Display order: main inventory, hotbar, armour (head → feet), offhand, then Curios accessories. */
    private static List<SlotAccess> slots(ServerPlayer target) {
        Inventory inv = target.getInventory();
        List<Integer> order = new ArrayList<>();
        for (int i = 9; i < 36; i++) order.add(i);
        for (int i = 0; i < 9; i++) order.add(i);
        for (int i = 39; i >= 36; i--) order.add(i);
        order.add(40);
        for (int i = 41; i < inv.getContainerSize(); i++) order.add(i);
        List<SlotAccess> out = new ArrayList<>();
        for (int index : order) {
            if (index >= inv.getContainerSize()) continue;
            out.add(new SlotAccess(() -> inv.getItem(index), stack -> inv.setItem(index, stack)));
        }
        IItemHandlerModifiable curios = Curios.equipped(target);
        if (curios != null) {
            for (int i = 0; i < curios.getSlots(); i++) {
                final int slot = i;
                out.add(new SlotAccess(() -> curios.getStackInSlot(slot), stack -> curios.setStackInSlot(slot, stack)));
            }
        }
        return List.copyOf(out);
    }

    /** Optional Curios accessories through reflection (compiles/runs without Curios). */
    private static final class Curios {
        private static java.lang.reflect.Method getInventory, getEquipped;
        private static boolean broken;

        static @Nullable IItemHandlerModifiable equipped(ServerPlayer player) {
            if (broken || !net.neoforged.fml.ModList.get().isLoaded("curios")) return null;
            try {
                if (getInventory == null) {
                    Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
                    getInventory = api.getMethod("getCuriosInventory", net.minecraft.world.entity.LivingEntity.class);
                    Class<?> handler = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
                    getEquipped = handler.getMethod("getEquippedCurios");
                }
                Optional<?> inv = (Optional<?>) getInventory.invoke(null, player);
                if (inv.isEmpty()) return null;
                Object h = getEquipped.invoke(inv.get());
                return h instanceof IItemHandlerModifiable m ? m : null;
            } catch (ReflectiveOperationException | LinkageError | ClassCastException ex) {
                broken = true;
                return null;
            }
        }
    }

    // ---------------- take-only menu ----------------

    /** Vanilla 9x6 chest screen backed by the victim's live slots; only extraction is possible. */
    private static final class Menu extends ChestMenu {
        private final LiveInventory live;
        private final ServerPlayer viewer;

        Menu(int id, ServerPlayer viewer, ServerPlayer target, java.util.function.BooleanSupplier valid) {
            this(id, viewer, new LiveInventory(viewer, target, valid));
        }

        private Menu(int id, ServerPlayer viewer, LiveInventory live) {
            super(MenuType.GENERIC_9x6, id, viewer.getInventory(), live, 6);
            this.live = live;
            this.viewer = viewer;
            // Same indices/positions as the vanilla slots so the client screen stays in sync.
            for (int i = 0; i < 54; i++) {
                Slot original = this.slots.get(i);
                Slot extraction = new Slot(live, i, original.x, original.y) {
                    @Override public boolean mayPlace(ItemStack stack) { return false; }
                    @Override public boolean mayPickup(Player player) { return getContainerSlot() < 45 && live.stillValid(player); }
                };
                extraction.index = i;
                this.slots.set(i, extraction);
            }
        }

        @Override
        public void clicked(int slot, int button, ClickType type, Player player) {
            if (player != viewer || !stillValid(player)) { viewer.closeContainer(); return; }
            if (slot >= 45 && slot < 54) {
                if (type == ClickType.PICKUP && (slot == 45 || slot == 46)) {
                    live.turnPage(slot == 45 ? -1 : 1);
                    broadcastChanges();
                }
                return;
            }
            super.clicked(slot, button, type, player);
            live.setChanged();
        }

        @Override
        public ItemStack quickMoveStack(Player player, int slot) {
            // Shift-clicking one's own inventory must never insert anything into the victim.
            if (player != viewer || !stillValid(player) || slot < 0 || slot >= 45) return ItemStack.EMPTY;
            ItemStack result = super.quickMoveStack(player, slot);
            live.setChanged();
            return result;
        }
    }

    private static final class LiveInventory implements Container {
        private final ServerPlayer viewer, target;
        private final java.util.function.BooleanSupplier valid;
        private final List<SlotAccess> source;
        private int page;

        LiveInventory(ServerPlayer viewer, ServerPlayer target, java.util.function.BooleanSupplier valid) {
            this.viewer = viewer; this.target = target; this.valid = valid;
            this.source = slots(target);
        }

        private int pageCount() { return Math.max(1, (source.size() + 44) / 45); }
        private int sourceIndex(int slot) {
            int index = page * 45 + slot;
            return slot >= 0 && slot < 45 && index < source.size() ? index : -1;
        }
        void turnPage(int dir) { page = Math.max(0, Math.min(pageCount() - 1, page + dir)); }

        @Override public int getContainerSize() { return 54; }
        @Override public boolean isEmpty() {
            for (SlotAccess s : source) if (!s.get().isEmpty()) return false;
            return true;
        }
        @Override public ItemStack getItem(int slot) {
            int index = sourceIndex(slot);
            if (index >= 0) return source.get(index).get();
            if (pageCount() > 1 && (slot == 45 || slot == 46) || slot == 49) {
                ItemStack control = new ItemStack(slot == 49 ? Items.NAME_TAG : Items.ARROW);
                control.set(DataComponents.CUSTOM_NAME, Component.translatable(
                        slot == 45 ? "ability.infinity.reality_stone.reality_confiscation.previous"
                                : slot == 46 ? "ability.infinity.reality_stone.reality_confiscation.next"
                                : "ability.infinity.reality_stone.reality_confiscation.page", page + 1, pageCount()));
                return control;
            }
            return ItemStack.EMPTY;
        }
        @Override public ItemStack removeItem(int slot, int amount) {
            int index = sourceIndex(slot);
            if (index < 0 || amount <= 0) return ItemStack.EMPTY;
            ItemStack current = source.get(index).get();
            ItemStack removed = current.split(amount);
            if (current.isEmpty()) source.get(index).set(ItemStack.EMPTY);
            setChanged();
            return removed;
        }
        @Override public ItemStack removeItemNoUpdate(int slot) {
            int index = sourceIndex(slot);
            if (index < 0) return ItemStack.EMPTY;
            ItemStack current = source.get(index).get();
            source.get(index).set(ItemStack.EMPTY);
            setChanged();
            return current;
        }
        @Override public void setItem(int slot, ItemStack stack) {
            int index = sourceIndex(slot);
            if (index < 0) return;
            ItemStack current = source.get(index).get();
            // Vanilla pickup/throw/swap may clear or shrink a stack, never grow or replace it: no duplication.
            if (stack.isEmpty() || (ItemStack.isSameItemSameComponents(current, stack) && stack.getCount() <= current.getCount())) {
                source.get(index).set(stack);
                setChanged();
            }
        }
        @Override public void setChanged() {
            target.getInventory().setChanged();
            target.inventoryMenu.broadcastChanges();
            if (target.containerMenu != target.inventoryMenu) target.containerMenu.broadcastChanges();
        }
        @Override public boolean stillValid(Player player) {
            return player == viewer && viewer.isAlive() && target.isAlive() && !viewer.hasDisconnected()
                    && !target.hasDisconnected() && viewer.level() == target.level() && valid.getAsBoolean();
        }
        @Override public boolean canPlaceItem(int slot, ItemStack stack) { return false; }
        @Override public void clearContent() { /* closing/replacing the menu must never wipe the victim's inventory */ }
    }
}
