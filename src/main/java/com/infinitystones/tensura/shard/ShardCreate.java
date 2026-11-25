package com.infinitystones.tensura.shard;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.registry.ModSounds;
import com.infinitystones.tensura.skill.GauntletStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.StoneProgress;
import com.infinitystones.tensura.skill.StoneTier;
import com.infinitystones.tensura.skill.StoneTree;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The "Borrowed Shard" power: validates a request from the menu, plays the creation sequence (energy leaves the
 * gauntlet, a crystal forms, condenses and flies into the caster) and, at the end of it, registers the gem and puts it
 * straight into the caster's inventory. Nothing exists before that moment; the stones and the gauntlet are untouched.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class ShardCreate {
    /** Ticks of the sequence. The client timeline (ShardFxClient) uses the same numbers. */
    public static final int FORM = 18, CONDENSE = 50, FLY = 56, TOTAL = 66;

    private record Task(int stone, List<String> powers, int seconds, boolean infinite, int[] age) {}

    private static final Map<UUID, Task> TASKS = new HashMap<>();

    /** Where the crystal hovers: in front of the chest, on the body direction. Mirrored by the client. */
    public static Vec3 crystalPos(ServerPlayer p) {
        double yaw = Math.toRadians(p.yBodyRot);
        return p.position().add(-Math.sin(yaw) * 0.85, 1.3, Math.cos(yaw) * 0.85);
    }

    public static void begin(ServerPlayer p, ShardNet.Create m) {
        if (TASKS.containsKey(p.getUUID())) return;
        String err = validate(p, m.stone(), m.powers(), m.seconds(), m.infinite());
        if (err != null) {
            deny(p, err);
            return;
        }
        Set<String> uniq = new LinkedHashSet<>(m.powers());
        TASKS.put(p.getUUID(), new Task(m.stone(), List.copyOf(uniq), m.infinite() ? 0 : m.seconds(), m.infinite(), new int[1]));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, new ShardNet.FxMsg(ShardNet.FX_START, p.getId(), m.stone()));
        ServerLevel l = p.serverLevel();
        l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.9F, 0.55F);
        l.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.INFINITY_STONE_EQUIP.get(), SoundSource.PLAYERS, 0.45F, 1.15F);
    }

    /** Error lang key, or null when the request is fine. */
    static String validate(ServerPlayer p, int stoneOrdinal, List<String> powers, int seconds, boolean infinite) {
        if (!p.isAlive() || p.isSpectator()) return "infinity.shard.err.state";
        if (stoneOrdinal < 0 || stoneOrdinal >= Stone.values().length) return "infinity.shard.err.state";
        Stone stone = Stone.values()[stoneOrdinal];
        if (!GauntletStones.wearsComplete(p)) return "infinity.shard.err.gauntlet";
        if (powers.isEmpty() || powers.size() > 32) return "infinity.shard.err.powers";
        if (!infinite && (seconds < ShardRules.MIN_SECONDS || seconds > ShardRules.MAX_SECONDS)) return "infinity.shard.err.time";
        ShardRegistry reg = ShardRegistry.get();
        if (reg == null) return "infinity.shard.err.state";
        if (reg.byCreator(p.getUUID()).size() >= ShardRules.MAX_PER_CREATOR) return "infinity.shard.err.max";
        boolean ok = ShardGrants.without(() -> {
            if (!stone.ownedBy(p)) return false;
            Set<String> seen = new HashSet<>();
            for (String id : powers) {
                StoneTree.Node n = StoneTree.of(stone).nodes.get(id);
                if (!seen.add(id) || n == null || !ShardRules.shardable(n)) return false;
                if (StoneTier.hidden(p, n) || !StoneProgress.isUnlocked(p, stone, id)) return false;
            }
            return true;
        });
        if (!ok) return "infinity.shard.err.powers";
        if (p.getInventory().getFreeSlot() < 0) return "infinity.shard.err.full";
        return null;
    }

    private static void deny(ServerPlayer p, String key) {
        p.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
        p.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.6F, 0.6F);
    }

    private static void cancel(ServerPlayer p, String key) {
        TASKS.remove(p.getUUID());
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, new ShardNet.FxMsg(ShardNet.FX_CANCEL, p.getId(), 0));
        if (key != null) deny(p, key);
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (TASKS.isEmpty() || !(event.getEntity() instanceof ServerPlayer p)) return;
        Task t = TASKS.get(p.getUUID());
        if (t == null) return;
        if (!p.isAlive() || !GauntletStones.wearsComplete(p)) { cancel(p, null); return; }
        int age = ++t.age[0];
        ServerLevel l = p.serverLevel();
        Vec3 c = crystalPos(p);
        int rgb = Stone.values()[t.stone].color() & 0xFFFFFF;
        if (age == FORM) l.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_CLUSTER_STEP, SoundSource.PLAYERS, 1.0F, 1.3F);
        if (age == 34) l.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.12F, 2.0F);
        if (age > FORM && age < CONDENSE && age % 6 == 0) l.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.4F, 0.8F + (age - FORM) * 0.02F);
        if (age == CONDENSE) {
            l.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.1F, 1.9F);
            l.playSound(null, c.x, c.y, c.z, SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.PLAYERS, 0.5F, 1.6F);
            var dust = new DustParticleOptions(new Vector3f((rgb >> 16 & 255) / 255F, (rgb >> 8 & 255) / 255F, (rgb & 255) / 255F), 1.2F);
            l.sendParticles(dust, c.x, c.y, c.z, 30, 0.1, 0.1, 0.1, 0.18);
            l.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 14, 0.05, 0.05, 0.05, 0.22);
        }
        if (age == FLY) l.playSound(null, c.x, c.y, c.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.35F, 2.0F);
        if (age >= TOTAL) finish(p, t);
    }

    private static void finish(ServerPlayer p, Task t) {
        TASKS.remove(p.getUUID());
        String err = validate(p, t.stone, t.powers, Math.max(t.seconds, ShardRules.MIN_SECONDS), t.infinite);
        ShardRegistry reg = ShardRegistry.get();
        if (err != null || reg == null) {
            cancel(p, err == null ? "infinity.shard.err.state" : err);
            return;
        }
        ShardRegistry.Shard s = reg.create(p.getUUID(), p.getGameProfile().getName(), t.stone, t.powers, t.seconds * 20L, t.infinite);
        ItemStack stack = new ItemStack(ShardRegistries.MINI_JOIA.get());
        stack.set(ShardRegistries.SHARD.get(), s.data());
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(t.stone + 1));
        if (!p.getInventory().add(stack)) {
            reg.revoke(s.id);
            cancel(p, "infinity.shard.err.full");
            return;
        }
        ServerLevel l = p.serverLevel();
        Vec3 pos = p.position().add(0, 1.1, 0);
        l.playSound(null, pos.x, pos.y, pos.z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.35F, 2.0F);
        l.playSound(null, pos.x, pos.y, pos.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9F, 1.7F);
        int rgb = Stone.values()[t.stone].color() & 0xFFFFFF;
        var dust = new DustParticleOptions(new Vector3f((rgb >> 16 & 255) / 255F, (rgb >> 8 & 255) / 255F, (rgb & 255) / 255F), 1.0F);
        l.sendParticles(dust, pos.x, pos.y, pos.z, 18, 0.3, 0.4, 0.3, 0.05);
        p.displayClientMessage(Component.translatable("infinity.shard.created", Component.literal("#" + s.data().code())).withStyle(ChatFormatting.AQUA), true);
        ShardNet.sendList(p);
        ShardNet.syncWheel(p, true);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { TASKS.remove(event.getEntity().getUUID()); }

    private ShardCreate() {}
}
