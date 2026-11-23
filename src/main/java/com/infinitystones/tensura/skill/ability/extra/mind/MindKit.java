package com.infinitystones.tensura.skill.ability.extra.mind;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Rotations;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Shared helpers of the Mind abilities: illusion bodies (armor stands wearing marked copies that can never drop),
 * hiding a player's equipment from other viewers, private camera effects, movement locks.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class MindKit {
    public static final Stone MIND = Stone.MIND;
    public static final int GOLD = 0xF2D02B;
    public static final int PALE = 0xFFF2A8;
    /** Tag on illusion entities (body shells, decoys); leftovers from a crash are removed when they load. */
    public static final String BODY_TAG = "infinity_mind_illusion";
    /** Custom-data flag on the visual item copies worn by illusions: such stacks are never allowed to exist as items. */
    public static final String ITEM_FLAG = "infinity_illusion";

    private static final Set<Entity> LIVE = Collections.newSetFromMap(new WeakHashMap<>());

    // ------------------------------------------------------------------ illusion bodies

    public static ItemStack illusion(ItemStack s) {
        if (s.isEmpty()) return ItemStack.EMPTY;
        ItemStack c = s.copyWithCount(1);
        CustomData.update(DataComponents.CUSTOM_DATA, c, t -> t.putBoolean(ITEM_FLAG, true));
        return c;
    }

    public static boolean isIllusion(ItemStack s) {
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        return d != null && d.contains(ITEM_FLAG);
    }

    public static ItemStack head(ServerPlayer p) {
        ItemStack h = new ItemStack(Items.PLAYER_HEAD);
        h.set(DataComponents.PROFILE, new ResolvableProfile(p.getGameProfile()));
        return illusion(h);
    }

    /** An armor stand dressed as p (head, armour, held items), not yet posed. */
    public static ArmorStand body(ServerPlayer p, Vec3 pos, float yaw, boolean invulnerable, boolean gravity) {
        ServerLevel l = p.serverLevel();
        ArmorStand s = EntityType.ARMOR_STAND.create(l);
        if (s == null) return null;
        CompoundTag t = new CompoundTag();
        s.saveWithoutId(t);
        t.putBoolean("ShowArms", true);
        t.putBoolean("NoBasePlate", true);
        t.putInt("DisabledSlots", 0x1F1F1F);
        t.putBoolean("Invulnerable", invulnerable);
        t.putBoolean("NoGravity", !gravity);
        t.putBoolean("Silent", true);
        s.load(t);
        s.moveTo(pos.x, pos.y, pos.z, yaw, 0);
        s.setYHeadRot(yaw);
        s.setYBodyRot(yaw);
        s.setItemSlot(EquipmentSlot.HEAD, head(p));
        for (EquipmentSlot sl : new EquipmentSlot[]{EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND})
            s.setItemSlot(sl, illusion(p.getItemBySlot(sl)));
        s.addTag(BODY_TAG);
        LIVE.add(s);
        l.addFreshEntity(s);
        return s;
    }

    /** Golden outline (glowing through a yellow scoreboard team that only holds illusions). */
    public static void goldGlow(Entity e) {
        var sb = e.level().getScoreboard();
        net.minecraft.world.scores.PlayerTeam team = sb.getPlayerTeam(TEAM);
        if (team == null) {
            team = sb.addPlayerTeam(TEAM);
            team.setColor(net.minecraft.ChatFormatting.YELLOW);
            team.setCollisionRule(net.minecraft.world.scores.Team.CollisionRule.NEVER);
            team.setNameTagVisibility(net.minecraft.world.scores.Team.Visibility.NEVER);
        }
        sb.addPlayerToTeam(e.getScoreboardName(), team);
        e.setGlowingTag(true);
    }

    private static final String TEAM = "infinity_mind_gold";

    private static void unteam(Entity e) {
        var sb = e.level().getScoreboard();
        net.minecraft.world.scores.PlayerTeam t = sb.getPlayersTeam(e.getScoreboardName());
        if (t != null && TEAM.equals(t.getName())) sb.removePlayerFromTeam(e.getScoreboardName(), t);
    }

    public static boolean isBody(Entity e) { return e instanceof ArmorStand && e.getTags().contains(BODY_TAG); }

    /** Removes an illusion without dropping anything. */
    public static void discard(Entity e) {
        if (e == null) return;
        if (e instanceof ArmorStand s) for (EquipmentSlot sl : EquipmentSlot.values()) s.setItemSlot(sl, ItemStack.EMPTY);
        LIVE.remove(e);
        unteam(e);
        e.discard();
    }

    public static void pose(ArmorStand s, float hx, float hy, float lax, float laz, float rax, float raz, float llx, float rlx) {
        s.setHeadPose(new Rotations(hx, hy, 0));
        s.setLeftArmPose(new Rotations(lax, 0, laz));
        s.setRightArmPose(new Rotations(rax, 0, raz));
        s.setLeftLegPose(new Rotations(llx, 0, 0));
        s.setRightLegPose(new Rotations(rlx, 0, 0));
    }

    /** Running animation frame. */
    public static void runPose(ArmorStand s, int age) {
        float w = Mth.sin(age * 0.6F) * 40F;
        pose(s, 0, 0, w, -6, -w, 6, -w, w);
    }

    @SubscribeEvent
    public static void join(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide()) return;
        Entity en = e.getEntity();
        if (en instanceof ItemEntity ie && isIllusion(ie.getItem())) { e.setCanceled(true); return; }
        if (en.getTags().contains(BODY_TAG) && !LIVE.contains(en)) e.setCanceled(true);
    }

    /** Nobody may take/put items on an illusion. */
    @SubscribeEvent
    public static void interact(PlayerInteractEvent.EntityInteractSpecific e) { if (isBody(e.getTarget())) e.setCanceled(true); }

    @SubscribeEvent
    public static void interact2(PlayerInteractEvent.EntityInteract e) { if (isBody(e.getTarget())) e.setCanceled(true); }

    // ------------------------------------------------------------------ hidden equipment (invisible casters)

    private static final Map<UUID, Integer> HIDDEN = new HashMap<>();

    /** Other players see p without armour/held items for ticks (refreshed); 0 = show again now. */
    public static void hideEquipment(ServerPlayer p, int ticks) {
        if (ticks <= 0) {
            if (HIDDEN.remove(p.getUUID()) != null) sendEquipment(p, false);
            return;
        }
        HIDDEN.merge(p.getUUID(), ticks, Math::max);
        sendEquipment(p, true);
    }

    public static void sendEquipment(ServerPlayer p, boolean empty) {
        List<Pair<EquipmentSlot, ItemStack>> list = new ArrayList<>();
        for (EquipmentSlot sl : EquipmentSlot.values()) {
            if (sl.getType() == EquipmentSlot.Type.ANIMAL_ARMOR) continue;
            list.add(Pair.of(sl, empty ? ItemStack.EMPTY : p.getItemBySlot(sl).copy()));
        }
        p.serverLevel().getChunkSource().broadcast(p, new ClientboundSetEquipmentPacket(p.getId(), list));
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post e) {
        if (HIDDEN.isEmpty()) return;
        var server = e.getServer();
        HIDDEN.entrySet().removeIf(en -> {
            ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
            if (p == null) return true;
            int left = en.getValue() - 1;
            en.setValue(left);
            if (left <= 0) { sendEquipment(p, false); return true; }
            if (left % 10 == 0) sendEquipment(p, true);
            return false;
        });
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent e) {
        for (Entity d : LIVE.toArray(new Entity[0])) discard(d);
        LIVE.clear();
        HIDDEN.clear();
    }

    // ------------------------------------------------------------------ private camera effects

    /** Camera shake for one player only. */
    public static void shake(ServerPlayer p, float strength, int ticks) {
        FxNet.to(p, new FxNet.Fx(FxNet.SHAKE, 0, 0, ticks, strength, 0, p.getX(), p.getY(), p.getZ(), 0, 0, 0, -1, -1));
    }

    /** Turns the player's camera (absolute yaw/pitch), position untouched. */
    public static void look(ServerPlayer p, float yaw, float pitch) {
        p.connection.teleport(0, 0, 0, yaw, Mth.clamp(pitch, -90, 90), EnumSet.of(RelativeMovement.X, RelativeMovement.Y, RelativeMovement.Z));
    }

    /** Turns the player's camera by a delta. */
    public static void nudge(ServerPlayer p, float dyaw, float dpitch) {
        p.connection.teleport(0, 0, 0, dyaw, dpitch, EnumSet.allOf(RelativeMovement.class));
    }

    public static float yawTo(Entity from, Vec3 to) {
        Vec3 d = to.subtract(from.getEyePosition());
        return (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F;
    }

    public static float pitchTo(Entity from, Vec3 to) {
        Vec3 d = to.subtract(from.getEyePosition());
        return (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
    }

    /** Pushes a living entity (players get the motion packet). */
    public static void push(LivingEntity e, Vec3 v) {
        e.setDeltaMovement(e.getDeltaMovement().add(v));
        e.hurtMarked = true;
        if (e instanceof ServerPlayer sp) sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
    }

    // ------------------------------------------------------------------ movement lock (Freeze / Sleep on players)

    private static final net.minecraft.resources.ResourceLocation LOCK = InfinityStones.id("mind_lock");

    public static void lock(LivingEntity e, boolean on) {
        lockAttr(e.getAttribute(Attributes.MOVEMENT_SPEED), on);
        lockAttr(e.getAttribute(Attributes.JUMP_STRENGTH), on);
        lockAttr(e.getAttribute(Attributes.FLYING_SPEED), on);
    }

    private static void lockAttr(AttributeInstance a, boolean on) {
        if (a == null) return;
        a.removeModifier(LOCK);
        if (on) a.addTransientModifier(new AttributeModifier(LOCK, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    // ------------------------------------------------------------------ small shared FX

    /** Golden "mind spark" burst used by several powers. */
    public static void spark(ServerLevel l, Vec3 c, int n) {
        Fx.at(l, Fx.dust(MIND, 1.2F), c, n, 0.4, 0.02);
        Fx.at(l, Fx.light(MIND, 0.8F), c, n / 2, 0.6, 0.04);
        Fx.at(l, net.minecraft.core.particles.ParticleTypes.END_ROD, c, n / 4, 0.3, 0.05);
    }

    public static boolean isPlayer(Entity e) { return e instanceof Player; }

    private MindKit() {}
}
