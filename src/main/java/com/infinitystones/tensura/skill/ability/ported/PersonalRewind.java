package com.infinitystones.tensura.skill.ability.ported;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.StoneProgress;
import com.infinitystones.tensura.skill.StoneTree;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Port of palladium:personal_rewind (Time, press). Players who carry the Time Stone with this node unlocked keep a
 * bounded personal history (a safe-position sample every 5 ticks, last 100 ticks, max 21 samples). Using it returns
 * the caster to the oldest safe sample between 5 s and 1 s ago, restores lost health (never lowers it) and removes
 * harmful effects acquired since then. No items, XP, world clock or NBT are rewound. Fizzles (no cooldown) without
 * at least 1 s of safe history.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class PersonalRewind implements Ported {
    private static final Map<UUID, History> HISTORIES = new HashMap<>();

    private record Snapshot(Vec3 position, float health, Set<Holder<MobEffect>> harmful, long at) {}
    private static final class History {
        final ServerLevel level;
        final ArrayDeque<Snapshot> samples = new ArrayDeque<>();
        long unlockCheckedAt = Long.MIN_VALUE;
        boolean unlocked;
        History(ServerLevel level) { this.level = level; }
    }

    public PersonalRewind() {}

    // ---------------- history recording ----------------

    @SubscribeEvent
    public static void record(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.tickCount % 5 != 0) return;
        if (!eligible(p) || !Stone.TIME.isHeldBy(p)) { HISTORIES.remove(p.getUUID()); return; }
        ServerLevel level = p.serverLevel();
        long now = level.getGameTime();
        History h = HISTORIES.get(p.getUUID());
        if (h == null || h.level != level) { h = new History(level); HISTORIES.put(p.getUUID(), h); }
        // the unlock check walks the tree, so refresh it only every 5 s
        if (now - h.unlockCheckedAt >= 100L) {
            h.unlocked = StoneProgress.isUnlocked(p, Stone.TIME, "personal_rewind");
            h.unlockCheckedAt = now;
        }
        if (!h.unlocked) { h.samples.clear(); return; }
        while (!h.samples.isEmpty() && h.samples.peekFirst().at < now - 100L) h.samples.removeFirst();
        if (safe(level, p, p.position())) {
            Set<Holder<MobEffect>> harmful = new HashSet<>();
            for (MobEffectInstance eff : p.getActiveEffects())
                if (eff.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) harmful.add(eff.getEffect());
            h.samples.addLast(new Snapshot(p.position(), p.getHealth(), Set.copyOf(harmful), now));
            while (h.samples.size() > 21) h.samples.removeFirst();
        }
    }

    private static boolean eligible(ServerPlayer p) {
        return p.isAlive() && !p.isRemoved() && !p.hasDisconnected() && !p.isSpectator()
                && !p.isPassenger() && !p.isVehicle() && !p.isSleeping();
    }

    private static boolean safe(ServerLevel level, ServerPlayer p, Vec3 dest) {
        AABB box = p.getBoundingBox().move(dest.subtract(p.position())).deflate(0.001D);
        if (box.minY < level.getMinBuildHeight() + 1 || box.maxY >= level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(box)) return false;
        for (double x : new double[]{box.minX, box.maxX}) for (double z : new double[]{box.minZ, box.maxZ})
            if (!level.hasChunkAt(BlockPos.containing(x, dest.y, z))) return false;
        BlockPos feet = BlockPos.containing(dest), floor = feet.below();
        return level.getFluidState(feet).isEmpty() && level.getFluidState(floor).isEmpty()
                && !level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()
                && !level.getBlockCollisions(p, box).iterator().hasNext();
    }

    // ---------------- use ----------------

    @Override
    public Boolean press(ServerPlayer p, StoneTree.Node n, boolean alt) {
        if (!eligible(p)) return Boolean.FALSE;
        History h = HISTORIES.get(p.getUUID());
        if (h == null || h.level != p.serverLevel()) return Boolean.FALSE;
        long now = h.level.getGameTime();
        Snapshot dest = null;
        for (Snapshot s : h.samples) {
            if (s.at >= now - 100L && s.at <= now - 20L && safe(h.level, p, s.position)) { dest = s; break; }
        }
        if (dest == null) return Boolean.FALSE;

        DustParticleOptions green = PortFx.dust(n.stone(), 0.85F), pale = PortFx.dust(0xB3FFBF, 0.6F);
        Vec3 origin = p.position();
        for (Snapshot s : h.samples) {
            if (s.at < dest.at) continue;
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4D;
                h.level.sendParticles(i % 2 == 0 ? green : pale, s.position.x + Math.cos(a) * 0.45D,
                        s.position.y + 0.3D + i * 0.18D, s.position.z + Math.sin(a) * 0.45D, 1, 0D, 0D, 0D, 0D);
            }
        }
        p.connection.teleport(dest.position.x, dest.position.y, dest.position.z, p.getYRot(), p.getXRot());
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0F;
        p.setHealth(Math.max(p.getHealth(), Math.min(p.getMaxHealth(), dest.health)));
        for (MobEffectInstance eff : new ArrayList<>(p.getActiveEffects())) {
            if (eff.getEffect().value().getCategory() == MobEffectCategory.HARMFUL && !dest.harmful.contains(eff.getEffect()))
                p.removeEffect(eff.getEffect());
        }
        h.samples.clear();
        h.level.sendParticles(ParticleTypes.REVERSE_PORTAL, origin.x, origin.y + 1D, origin.z, 24, 0.4D, 0.7D, 0.4D, 0.08D);
        PortFx.sound(h.level, p.getEyePosition(), n, "infinity:time_stone", 0.7F, 1.25F);
        PortFx.sound(h.level, p.position(), SoundEvents.BEACON_DEACTIVATE, 0.6F, 1.6F);
        return Boolean.TRUE;
    }

    // ---------------- cleanup ----------------

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { HISTORIES.remove(e.getEntity().getUUID()); }

    @SubscribeEvent
    public static void death(LivingDeathEvent e) { if (e.getEntity() instanceof ServerPlayer p) HISTORIES.remove(p.getUUID()); }

    @SubscribeEvent
    public static void changeDim(PlayerEvent.PlayerChangedDimensionEvent e) { HISTORIES.remove(e.getEntity().getUUID()); }
}
