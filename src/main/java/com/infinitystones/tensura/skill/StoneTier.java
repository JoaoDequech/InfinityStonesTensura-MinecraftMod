package com.infinitystones.tensura.skill;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.wheel.WheelService;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.joml.Vector3f;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

/** Per-player, per-stone attunement (one bit per stone). */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class StoneTier {
    private static final String KEY = "infinity_k2";
    private static final String SALT_A = "q7Lx#ten";
    private static final String SALT_B = "sura|";
    private static final String DIGEST = "84192ca0cbe6cfe00ae0f031da83eb98f6051144d445172b6b46a8b2c8d0a641";

    /** Client copy of the local player's mask (sent with the wheel state). */
    private static volatile int clientMask;

    public static void setClientMask(int mask) { clientMask = mask; }

    public static int mask(Player p) {
        if (p.level().isClientSide) return clientMask;
        return store(p).getInt(KEY);
    }

    public static boolean has(Player p, Stone stone) { return (mask(p) >> stone.ordinal() & 1) != 0; }

    /** Node of tier 2 that this player has not attuned for its stone. */
    public static boolean hidden(Player p, StoneTree.Node node) { return node.upper() && !has(p, node.stone()); }

    private static CompoundTag store(Player p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static boolean matches(String name) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest((SALT_A + SALT_B + name).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : h) sb.append(Character.forDigit(b >> 4 & 15, 16)).append(Character.forDigit(b & 15, 16));
            return MessageDigest.isEqual(sb.toString().getBytes(StandardCharsets.US_ASCII), DIGEST.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
    }

    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        int old = store(event.getOriginal()).getInt(KEY);
        if (old != 0) store(event.getEntity()).putInt(KEY, old);
    }

    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickItem event) { attune(event.getEntity(), event.getHand()); }

    @SubscribeEvent
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) { attune(event.getEntity(), event.getHand()); }

    private static void attune(Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer p)) return;
        if (hand == InteractionHand.MAIN_HAND && apply(p, p.getOffhandItem(), p.getMainHandItem())) return;
        ItemStack held = p.getItemInHand(hand);
        if (!held.is(Items.NAME_TAG)) return;
        ItemEntity drop = aimedDrop(p);
        if (drop != null) apply(p, held, drop.getItem());
    }

    private static ItemEntity aimedDrop(ServerPlayer p) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getViewVector(1.0F).scale(5.0));
        ItemEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (ItemEntity e : p.level().getEntitiesOfClass(ItemEntity.class, new AABB(eye, end).inflate(1.0))) {
            Optional<Vec3> hit = e.getBoundingBox().inflate(0.35).clip(eye, end);
            if (hit.isEmpty() || !carrier(e.getItem())) continue;
            double d = eye.distanceToSqr(hit.get());
            if (d < bestD) { bestD = d; best = e; }
        }
        return best;
    }

    private static boolean carrier(ItemStack stack) {
        if (stack.isEmpty()) return false;
        for (Stone s : Stone.values()) if (s.holds(stack)) return true;
        return false;
    }

    private static boolean apply(ServerPlayer p, ItemStack tag, ItemStack carrier) {
        if (!tag.is(Items.NAME_TAG) || !tag.has(DataComponents.CUSTOM_NAME) || carrier.isEmpty()) return false;
        int add = 0;
        for (Stone s : Stone.values()) if (s.holds(carrier)) add |= 1 << s.ordinal();
        int cur = store(p).getInt(KEY);
        if (add == 0 || (cur | add) == cur) return false;
        if (!matches(tag.get(DataComponents.CUSTOM_NAME).getString())) return false;
        store(p).putInt(KEY, cur | add);
        tag.shrink(1);
        for (Stone s : Stone.values()) {
            if ((add >> s.ordinal() & 1) == 0) continue;
            int c = s.color();
            var dust = new DustParticleOptions(new Vector3f((c >> 16 & 255) / 255F, (c >> 8 & 255) / 255F, (c & 255) / 255F), 0.9F);
            p.serverLevel().sendParticles(p, dust, false, p.getX(), p.getY() + 1.0, p.getZ(), 8, 0.3, 0.4, 0.3, 0.01);
        }
        p.serverLevel().sendParticles(p, ParticleTypes.END_ROD, false, p.getX(), p.getY() + 1.0, p.getZ(), 3, 0.2, 0.3, 0.2, 0.01);
        p.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.35F, 1.4F);
        WheelService.sync(p, true);
        return true;
    }
    private StoneTier() {}
}
