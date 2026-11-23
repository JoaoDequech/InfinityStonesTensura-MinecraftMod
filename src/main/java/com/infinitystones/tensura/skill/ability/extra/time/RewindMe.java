package com.infinitystones.tensura.skill.ability.extra.time;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Rewind Me (Time). While you carry the Time Stone your last 6 s are remembered (position, view, health, hunger, fire).
 * Left: you rewind 5 s — in about a second you fly backwards along your own footsteps, leaving green afterimages, with a
 * reversed clock under you — and arrive with the health, hunger and fire you had then (never less than you have now) and
 * every harmful effect wiped. Right: a quick 1.5 s blink back to dodge. Resistance protects you during the trip. Nothing
 * else in the world is touched. The memory is cleared on death, dimension change, logout and after each rewind.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class RewindMe implements Ability {
    public static final RewindMe INSTANCE = new RewindMe();
    private static final String K = "infinity.extra.rewind.";
    private static final int GREEN = Stone.TIME.color() & 0xFFFFFF, STEP = 2, KEEP = 60;

    private record Snap(ResourceKey<Level> dim, Vec3 pos, float yaw, float pitch, float health, int food, float sat, int fire) {}

    private static final class Mem {
        final ArrayDeque<Snap> snaps = new ArrayDeque<>();
        long checked = -1000; boolean holds;
    }

    private static final Map<UUID, Mem> MEM = new HashMap<>();
    private static final Set<UUID> BUSY = new HashSet<>();

    @Override public Stone stone() { return Stone.TIME; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        if (BUSY.contains(p.getUUID())) return false;
        Mem m = MEM.get(p.getUUID());
        int ticks = alt ? 30 : 100;
        if (m == null || m.snaps.size() < 8) return Fx.fail(p, K + "nothing");
        List<Snap> all = new ArrayList<>(m.snaps);
        int back = Math.min(all.size() - 1, ticks / STEP);
        ServerLevel l = p.serverLevel();
        List<Snap> path = new ArrayList<>();
        for (int i = all.size() - 1; i >= all.size() - 1 - back; i--) {
            Snap s = all.get(i);
            if (s.dim() != l.dimension()) break;
            path.add(s);
        }
        if (path.size() < 4) return Fx.fail(p, K + "nothing");
        Snap goal = path.get(path.size() - 1);
        BUSY.add(p.getUUID());
        m.snaps.clear();
        start(p, l, path, goal, alt);
        return true;
    }

    private static void start(ServerPlayer p, ServerLevel l, List<Snap> path, Snap goal, boolean quick) {
        Vec3 from = p.position();
        int perTick = Math.max(1, (int) Math.ceil(path.size() / (quick ? 8.0 : 20.0)));
        FxNet.clock(l, 0, from.add(0, 0.05, 0), 2.6F, GREEN, 30, -8F);
        FxNet.spiral(l, from, p.getBbWidth() + 0.6F, -(p.getBbHeight() + 1.5F), GREEN, 20, p.getId());
        FxNet.flash(p, 0xBFFFD6, 0.4F, 10);
        FxNet.shake(l, from, 6, 0.4F, 8);
        Fx.sound(l, from, SoundEvents.BEACON_DEACTIVATE, 0.6F, 1.5F);
        Fx.sound(l, from, SoundEvents.BELL_RESONATE, 0.5F, 1.2F);
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, (path.size() / perTick) + 30, 4, false, false));
        Fx.bar(p, Stone.TIME, K + "on", quick ? "1,5" : "5");
        final int[] idx = {0};
        Tasks.add(new Tasks.Task() {
            @Override public boolean tick(int age) {
                if (!p.isAlive() || p.hasDisconnected() || p.level() != l) return false;
                idx[0] = Math.min(path.size() - 1, idx[0] + perTick);
                Snap s = path.get(idx[0]);
                p.teleportTo(l, s.pos().x, s.pos().y, s.pos().z, s.yaw(), s.pitch());
                p.setDeltaMovement(Vec3.ZERO);
                p.hurtMarked = true;
                p.fallDistance = 0;
                Vec3 mid = s.pos().add(0, p.getBbHeight() / 2, 0);
                if (age % 2 == 0) TimeNet.ghost(l, s.pos(), p.getBbWidth(), p.getBbHeight(), 0, 12, s.yaw());
                Fx.at(l, ParticleTypes.REVERSE_PORTAL, mid, 6, 0.3, 0.1);
                Fx.at(l, Fx.dust(GREEN, 1.1F), mid, 4, 0.3, 0);
                if (age % 3 == 0) Fx.sound(l, mid, SoundEvents.NOTE_BLOCK_CHIME.value(), 0.3F, 0.6F + age * 0.06F);
                return idx[0] < path.size() - 1;
            }
            @Override public void end() {
                BUSY.remove(p.getUUID());
                if (!p.isAlive()) return;
                Vec3 to = goal.pos();
                p.setHealth(Math.min(p.getMaxHealth(), Math.max(p.getHealth(), goal.health())));
                p.getFoodData().setFoodLevel(Math.max(p.getFoodData().getFoodLevel(), goal.food()));
                p.getFoodData().setSaturation(Math.max(p.getFoodData().getSaturationLevel(), goal.sat()));
                if (p.getRemainingFireTicks() > goal.fire()) p.setRemainingFireTicks(goal.fire());
                for (MobEffectInstance e : new ArrayList<>(p.getActiveEffects()))
                    if (e.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) p.removeEffect(e.getEffect());
                p.fallDistance = 0;
                FxNet.shockwave(l, to.add(0, 0.1, 0), 4F, GREEN, 18);
                FxNet.clock(l, 0, to.add(0, 0.05, 0), 2F, GREEN, 30, -8F);
                FxNet.flare(l, to.add(0, p.getBbHeight() / 2, 0), 2.6F, GREEN, 10);
                FxNet.flash(p, 0xBFFFD6, 0.45F, 10);
                Fx.at(l, ParticleTypes.END_ROD, to.add(0, p.getBbHeight() / 2, 0), 24, 0.4, 0.12);
                Fx.sound(l, to, SoundEvents.ENDERMAN_TELEPORT, 0.6F, 0.6F);
                Fx.sound(l, to, SoundEvents.BELL_RESONATE, 0.6F, 1.6F);
                Fx.bar(p, Stone.TIME, K + "done");
            }
        });
    }

    // ------------------------------------------------------------------ memory

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post e) {
        long now = e.getServer().getTickCount();
        if (now % STEP != 0) return;
        for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) {
            if (!p.isAlive() || p.isSpectator() || BUSY.contains(p.getUUID())) continue;
            Mem m = MEM.computeIfAbsent(p.getUUID(), k -> new Mem());
            if (now - m.checked >= 40) { m.checked = now; m.holds = Stone.TIME.isHeldBy(p); }
            if (!m.holds) { if (!m.snaps.isEmpty()) m.snaps.clear(); continue; }
            Snap last = m.snaps.peekLast();
            if (last != null && last.dim() != p.serverLevel().dimension()) m.snaps.clear();
            m.snaps.addLast(new Snap(p.serverLevel().dimension(), p.position(), p.getYRot(), p.getXRot(), p.getHealth(),
                    p.getFoodData().getFoodLevel(), p.getFoodData().getSaturationLevel(), p.getRemainingFireTicks()));
            while (m.snaps.size() > KEEP) m.snaps.removeFirst();
        }
    }

    @SubscribeEvent
    public static void died(LivingDeathEvent e) { if (e.getEntity() instanceof ServerPlayer p) MEM.remove(p.getUUID()); }

    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { MEM.remove(e.getEntity().getUUID()); }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent e) { MEM.remove(e.getEntity().getUUID()); }

    @SubscribeEvent
    public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent e) { MEM.remove(e.getEntity().getUUID()); BUSY.remove(e.getEntity().getUUID()); }
}
