package com.infinitystones.tensura.skill.ability.ported;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.registry.ModSounds;
import com.infinitystones.tensura.skill.StoneTree;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * corewithstuff:telekinesis (Space "Grab Entity", held). Grabs the aimed entity within "range" and keeps it floating
 * in front of the caster at the distance it was grabbed (clamped to the original push/pull limits 2..30) while held.
 * The original's scroll push/pull and left-click throw become: look up/down to pull/push is not needed — sneaking
 * when releasing throws the target forward with the original throw_strength (5). Players only with PvP.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class Telekinesis implements Ported {
    private record Grab(UUID target, double distance) {}
    private static final Map<UUID, Grab> GRABS = new HashMap<>();

    @Override
    public void start(ServerPlayer p, StoneTree.Node n) {
        float range = PortFx.f(n, "range", 20);
        boolean players = PortFx.b(n, "affect_players", true);
        LivingEntity t = PortFx.aimed(p, range, e -> PortFx.canAffect(p, e) && !(e instanceof EnderDragon) && !com.infinitystones.tensura.skill.ability.extra.space.SpiritualGathering.isTrapped(e)
                && (players || !(e instanceof ServerPlayer)) && !e.isPassenger());
        PortFx.sound(p.serverLevel(), p.getEyePosition(), ModSounds.USE_INFINITY_GAUNTLET, 0.5F, 1F);
        if (t == null) {
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("ability.infinity.port.no_target")
                    .withStyle(net.minecraft.ChatFormatting.GRAY), true);
            return;
        }
        double d = Mth.clamp(p.getEyePosition().distanceTo(t.position()), 2, 30);
        GRABS.put(p.getUUID(), new Grab(t.getUUID(), d));
        Levels.set(p, n.modeId(), 2, 30, (int) Math.round(d)); // wheel scroll = push / pull (original grab_push / grab_pull 2..30)
        PortFx.sound(p.serverLevel(), t.position(), ModSounds.SPACE_STONE_GRAB, 0.5F, 1F);
        PolishFx.grabStart(p, t);
    }

    @Override
    public void tick(ServerPlayer p, StoneTree.Node n, int ticks) {
        Grab g = GRABS.get(p.getUUID());
        if (g == null) return;
        ServerLevel level = p.serverLevel();
        Entity t = level.getEntity(g.target());
        if (t == null || !t.isAlive() || t.distanceTo(p) > 40 || com.infinitystones.tensura.skill.ability.extra.space.SpiritualGathering.isTrapped(t)) { GRABS.remove(p.getUUID()); return; }
        double dist = Levels.get(p, n.modeId(), 2, 30, (int) Math.round(g.distance()));
        Vec3 want = p.getEyePosition().add(p.getViewVector(1F).scale(dist)).subtract(0, t.getBbHeight() / 2, 0);
        Vec3 delta = want.subtract(t.position());
        float strength = PortFx.f(n, "strength", 1);
        Vec3 vel = delta.scale(0.45 * strength);
        if (vel.length() > 2.5) vel = vel.normalize().scale(2.5);
        t.setDeltaMovement(vel);
        t.fallDistance = 0;
        t.hurtMarked = true;
        if (t instanceof Mob m) m.getNavigation().stop();
        // original dust "0.345 0.714 0.89" x5 per tick
        level.sendParticles(PortFx.dust(0x58B6E3, 1F), t.getX(), t.getY(0.5), t.getZ(), 5, t.getBbWidth() * 0.4, t.getBbHeight() * 0.4, t.getBbWidth() * 0.4, 0);
        if (ticks % 4 == 0) PortFx.line(level, p.getEyePosition().add(p.getViewVector(1F).scale(0.7)), t.position().add(0, t.getBbHeight() / 2, 0),
                PortFx.dust(n.stone(), 0.6F), 0.8);
        PolishFx.grabHold(p, t, ticks);
    }

    @Override
    public void stop(ServerPlayer p, StoneTree.Node n) {
        Grab g = GRABS.remove(p.getUUID());
        if (g == null) return;
        Entity t = p.serverLevel().getEntity(g.target());
        if (t == null) return;
        PolishFx.grabEnd(p, t, p.isShiftKeyDown());
        if (p.isShiftKeyDown()) { // throw (original telekinesis_throw, throw_strength 5)
            t.setDeltaMovement(p.getViewVector(1F).scale(5 * 0.5));
            t.hurtMarked = true;
            PortFx.sound(p.serverLevel(), t.position(), ModSounds.SPACE_STONE_RANDOM, 0.5F, 1.2F);
        } else if (PortFx.b(n, "reset", true)) {
            t.setDeltaMovement(t.getDeltaMovement().scale(0.2));
            t.hurtMarked = true;
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { GRABS.remove(e.getEntity().getUUID()); }
}
