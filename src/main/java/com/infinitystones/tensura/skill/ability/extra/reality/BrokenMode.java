package com.infinitystones.tensura.skill.ability.extra.reality;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Ground;
import com.infinitystones.tensura.skill.ability.extra.Priv;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Broken Mode (toggle). Reality glitches around you: nearby blocks flicker into other blocks (fake blocks seen by every
 * player close by), your screen gets the red chromatic tint, your melee hits glitch the target (short random teleport,
 * +30% damage) and every Reality ability is ×1.5 ({@link Rules#mult}). Price: +25% damage taken and fast hunger.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class BrokenMode implements Ability {
    public static final BrokenMode INSTANCE = new BrokenMode();
    private static final Set<UUID> ON = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Integer> AURA = new ConcurrentHashMap<>();
    private static final BlockState[] GLITCH = {
            Blocks.RED_CONCRETE.defaultBlockState(), Blocks.CRYING_OBSIDIAN.defaultBlockState(), Blocks.REDSTONE_BLOCK.defaultBlockState(),
            Blocks.MAGENTA_GLAZED_TERRACOTTA.defaultBlockState(), Blocks.RED_STAINED_GLASS.defaultBlockState(), Blocks.NETHERRACK.defaultBlockState(),
            Blocks.CRIMSON_NYLIUM.defaultBlockState(), Blocks.BLACK_CONCRETE.defaultBlockState(), Blocks.SHROOMLIGHT.defaultBlockState(),
            Blocks.STRUCTURE_VOID.defaultBlockState(), Blocks.AIR.defaultBlockState(), Blocks.TINTED_GLASS.defaultBlockState()};
    private static boolean glitching;

    @Override public Stone stone() { return Stone.REALITY; }

    public static boolean isOn(Player p) { return ON.contains(p.getUUID()); }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        if (isOn(p)) stop(p); else start(p);
        return true;
    }

    @Override
    public void start(ServerPlayer p) {
        if (!ON.add(p.getUUID())) return;
        ServerLevel l = p.serverLevel();
        int c = Stone.REALITY.color();
        Vec3 at = p.position();
        int aura = FxNet.newId();
        AURA.put(p.getUUID(), aura);
        FxNet.screen(p, FxNet.BROKEN_TINT, 40);
        FxNet.flash(p, c, 0.45F, 10);
        FxNet.shockwave(l, at.add(0, 0.1, 0), 9F, c, 22);
        FxNet.shockwave(l, at.add(0, 1.0, 0), 5F, 0xFFFFFF, 14);
        FxNet.runes(l, FxNet.newId(), at.add(0, 0.05, 0), 4F, c, 40, 4F);
        FxNet.shake(l, at, 16, 0.6F, 14);
        Fx.shell(l, at.add(0, 1, 0), 2.2, 60, Fx.dust(Stone.REALITY, 1.4F));
        Fx.sound(l, at, SoundEvents.GLASS_BREAK, 0.7F, 0.5F);
        Fx.sound(l, at, SoundEvents.ENDERMAN_SCREAM, 0.35F, 0.5F);
        Fx.sound(l, at, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 0.6F, 0.6F);
        burst(l, p, 14);
        Fx.bar(p, Stone.REALITY, "infinity.extra.broken_mode.on");
    }

    @Override
    public void tick(ServerPlayer p) {
        if (!isOn(p)) { start(p); return; }
        ServerLevel l = p.serverLevel();
        int t = p.tickCount;
        if (t % 10 == 0) FxNet.screen(p, FxNet.BROKEN_TINT, 30);
        if (t % 30 == 0) {
            Integer aura = AURA.get(p.getUUID());
            if (aura != null) FxNet.cube(l, aura, p.position().add(0, p.getBbHeight() / 2, 0), 1.2F, Stone.REALITY.color(), 45, p.getId());
        }
        if (t % 3 == 0) burst(l, p, 2 + l.random.nextInt(3));
        if (t % 2 == 0) {
            Vec3 v = p.position().add((l.random.nextDouble() - 0.5) * 2.4, l.random.nextDouble() * 2.2, (l.random.nextDouble() - 0.5) * 2.4);
            Fx.at(l, Fx.dust(l.random.nextBoolean() ? 0xE22B3B : 0x2BE2D2, 0.9F), v, 1, 0.05, 0);
        }
        if (t % 23 == 0 && l.random.nextInt(3) == 0) {
            Fx.sound(l, p.position(), l.random.nextBoolean() ? SoundEvents.ENDERMAN_TELEPORT : SoundEvents.AMETHYST_BLOCK_BREAK, 0.25F, 0.3F + l.random.nextFloat() * 0.4F);
        }
        if (t % 40 == 0 && l.random.nextInt(3) == 0) FxNet.shockwave(l, p.position().add(0, 0.1, 0), 4F, Stone.REALITY.color(), 12);
        p.causeFoodExhaustion(0.05F);
    }

    @Override
    public void stop(ServerPlayer p) {
        if (!ON.remove(p.getUUID())) return;
        FxNet.screen(p, FxNet.BROKEN_TINT, 0);
        Integer aura = AURA.remove(p.getUUID());
        ServerLevel l = p.serverLevel();
        if (aura != null) FxNet.kill(l, p.position(), aura);
        FxNet.flare(l, p.position().add(0, 1, 0), 2F, Stone.REALITY.color(), 10);
        Fx.sound(l, p.position(), SoundEvents.BEACON_DEACTIVATE, 0.5F, 0.8F);
        if (!p.hasDisconnected()) Fx.barGray(p, "infinity.extra.broken_mode.off");
    }

    /** n nearby solid blocks briefly turn into something else for every player around (the real block is unchanged). */
    private static void burst(ServerLevel l, ServerPlayer p, int n) {
        List<ServerPlayer> viewers = new ArrayList<>();
        for (ServerPlayer sp : l.players()) if (sp.distanceToSqr(p) < 48 * 48) viewers.add(sp);
        BlockPos c = p.blockPosition();
        for (int i = 0; i < n; i++) {
            BlockPos pos = c.offset(l.random.nextInt(13) - 6, l.random.nextInt(7) - 3, l.random.nextInt(13) - 6);
            if (!l.isLoaded(pos)) continue;
            BlockState real = l.getBlockState(pos);
            if (real.isAir() || l.getBlockEntity(pos) != null || !real.isCollisionShapeFullBlock(l, pos)) continue;
            if (pos.closerThan(c, 1.5) || pos.equals(c.below())) continue; // never under the caster's feet
            BlockState fake = GLITCH[l.random.nextInt(GLITCH.length)];
            for (ServerPlayer v : viewers) Priv.fakeBlock(v, pos, fake);
            Vec3 mid = Vec3.atCenterOf(pos);
            Fx.at(l, Fx.dust(Stone.REALITY, 1F), mid, 3, 0.45, 0);
            int life = 3 + l.random.nextInt(8);
            Tasks.add(new Tasks.Task() {
                @Override public boolean tick(int age) { return age < life; }
                @Override public void end() { for (ServerPlayer v : viewers) if (!v.hasDisconnected()) Priv.realBlock(v, pos); }
            });
        }
    }

    @SubscribeEvent
    public static void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) {
        ON.remove(e.getEntity().getUUID());
        AURA.remove(e.getEntity().getUUID());
    }

    // ------------------------------------------------------------------ combat

    @SubscribeEvent
    public static void incoming(LivingIncomingDamageEvent e) {
        if (glitching) return;
        LivingEntity victim = e.getEntity();
        if (victim instanceof ServerPlayer sp && isOn(sp)) e.setAmount(e.getAmount() * 1.25F);
        if (e.getSource().getEntity() instanceof ServerPlayer attacker && e.getSource().getDirectEntity() == attacker
                && isOn(attacker) && victim != attacker) {
            e.setAmount(e.getAmount() * 1.3F);
        }
    }

    @SubscribeEvent
    public static void dealt(LivingDamageEvent.Post e) {
        if (glitching) return;
        LivingEntity victim = e.getEntity();
        if (!(e.getSource().getEntity() instanceof ServerPlayer attacker) || e.getSource().getDirectEntity() != attacker || !isOn(attacker)) return;
        if (!victim.isAlive() || victim == attacker || !(victim.level() instanceof ServerLevel l) || Rules.isBoss(victim)) return;
        glitching = true;
        try {
            Vec3 from = victim.position();
            for (int i = 0; i < 8; i++) {
                Vec3 to = from.add((l.random.nextDouble() - 0.5) * 4, 0.5, (l.random.nextDouble() - 0.5) * 4);
                Vec3 floor = Ground.below(l, to, 3);
                if (Ground.fits(l, victim, floor)) {
                    victim.teleportTo(floor.x, floor.y, floor.z);
                    break;
                }
            }
            Vec3 mid = from.add(0, victim.getBbHeight() / 2, 0);
            FxNet.cube(l, FxNet.newId(), mid, Math.max(0.6F, victim.getBbWidth() * 0.7F), Stone.REALITY.color(), 8, -1);
            FxNet.flare(l, victim.position().add(0, victim.getBbHeight() / 2, 0), 1.2F, Stone.REALITY.color(), 8);
            Fx.at(l, Fx.dust(0x2BE2D2, 1F), mid, 10, 0.35, 0);
            Fx.at(l, Fx.dust(Stone.REALITY, 1.2F), victim.position().add(0, victim.getBbHeight() / 2, 0), 14, 0.35, 0);
            Fx.at(l, ParticleTypes.REVERSE_PORTAL, mid, 12, 0.3, 0.05);
            Fx.sound(l, mid, SoundEvents.ENDERMAN_TELEPORT, 0.4F, 0.4F);
        } finally { glitching = false; }
    }
}
