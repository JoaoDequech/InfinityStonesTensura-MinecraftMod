package com.infinitystones.tensura.skill.ability.extra.fear;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.ability.extra.Priv;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Voice, flashlight battery, victim HUD options, escape candles, audience invitations, joining victims and highlight replays. */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class FearExtras {
    private record Invite(UUID caster, UUID victim, int until) {}

    private static final class Replay {
        final float[] data; int i; int tick; final UUID victim;
        Replay(float[] data, UUID victim) { this.data = data; this.victim = victim; }
    }

    private static final Map<UUID, Invite> INVITES = new HashMap<>();
    private static final Map<UUID, Replay> REPLAYS = new HashMap<>();
    private static final int CANDLES = 5;

    // ------------------------------------------------------------------ voice

    private static final SoundEvent[] VOICES = {SoundEvents.WARDEN_NEARBY_CLOSE, SoundEvents.SOUL_ESCAPE.value(), SoundEvents.PLAYER_BREATH, SoundEvents.ENDERMAN_AMBIENT,
            SoundEvents.WARDEN_AMBIENT, SoundEvents.GHAST_AMBIENT, SoundEvents.ZOMBIE_VILLAGER_AMBIENT, SoundEvents.PHANTOM_AMBIENT};

    /** The typed text drifts on the victim's screen; every syllable is a layered, pitched-down breath around their head. */
    static void voice(FearLive live, ServerPlayer v, String raw) {
        String text = raw.replaceAll("[\\p{Cntrl}]", "").trim();
        if (text.isEmpty()) return;
        if (text.length() > 40) text = text.substring(0, 40);
        live.scare(12F);
        FearNet.send(v, new FearNet.Event(FearNet.SPEAK, v.getRandom().nextInt(), text.length(), 0, 0, 0, 0, text));
        int t = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == ' ') { t += 5; continue; }
            final int k = i, c = ch;
            live.after(t, p -> {
                var r = p.getRandom();
                double a = r.nextDouble() * Math.PI * 2, d = 0.5 + r.nextDouble() * 1.8;
                Vec3 at = p.getEyePosition().add(Math.cos(a) * d, (r.nextDouble() - 0.5) * 0.8, Math.sin(a) * d);
                float pitch = 0.32F + (c % 7) * 0.045F + r.nextFloat() * 0.08F;
                float vol = 0.5F + r.nextFloat() * 0.35F;
                Priv.sound(p, VOICES[(c + k) % VOICES.length], at, vol, pitch);
                Priv.sound(p, VOICES[(c * 3 + 1) % VOICES.length], at.add(r.nextGaussian() * 0.3, 0, r.nextGaussian() * 0.3), vol * 0.6F, pitch * 0.75F);
                if (k % 4 == 0) Priv.sound(p, SoundEvents.PLAYER_BREATH, at, 0.35F, 0.4F);
            });
            t += 3;
        }
    }

    // ------------------------------------------------------------------ battery and HUD options

    static void battery(FearLive live, ServerPlayer v, int mode) {
        switch (mode) {
            case 0 -> { live.battery = 0F; live.flash = false; FearNet.send(v, FearNet.FLASH_OFF, 400, 1); }
            case 1 -> live.battery = Math.max(0F, live.battery - 40F);
            default -> live.battery = 100F;
        }
        if (live.battery <= 0F) live.flash = false;
    }

    static void hud(FearLive live, ServerPlayer v, String what, boolean on) {
        int bit = switch (what) { case "meter" -> FearNet.F_METER; case "fake" -> FearNet.F_FAKE; case "real" -> FearNet.F_REAL; default -> 0; };
        if (bit == 0) return;
        if (on) live.hud |= bit; else live.hud &= ~bit;
        if (bit == FearNet.F_REAL) { if (on) placeCandles(live, v); else clearCandles(live, v.serverLevel()); }
    }

    // ------------------------------------------------------------------ real escape objective: light the candles

    static void placeCandles(FearLive live, ServerPlayer v) {
        ServerLevel l = v.serverLevel();
        if (l.dimension() != FearRealm.DIM) return;
        clearCandles(live, l);
        var r = v.getRandom();
        for (int i = 0; i < CANDLES; i++) {
            for (int tries = 0; tries < 12; tries++) {
                double a = (i + r.nextDouble() * 0.7) / CANDLES * Math.PI * 2, d = 14 + r.nextDouble() * 30;
                int x = Mth.floor(v.getX() + Math.cos(a) * d), z = Mth.floor(v.getZ() + Math.sin(a) * d);
                if (!l.hasChunkAt(new BlockPos(x, 64, z))) continue;
                BlockPos g = BlockPos.containing(x, FearDirector.ground(l, x, z, v.getY()), z);
                if (!l.getBlockState(g).isAir() || !l.getBlockState(g.below()).canOcclude()) continue;
                l.setBlock(g, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, false), 2 | 16);
                live.candles.add(g);
                break;
            }
        }
    }

    static void clearCandles(FearLive live, ServerLevel l) {
        for (BlockPos p : live.candles) if (l.getBlockState(p).is(Blocks.CANDLE)) l.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
        live.candles.clear();
        live.lit = 0;
    }

    @SubscribeEvent
    public static void candleUse(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        FearLive live = FearSessions.LIVE.get(p.getUUID());
        if (live == null || live.candles.isEmpty() || !live.candles.contains(e.getPos())) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        BlockState s = p.level().getBlockState(e.getPos());
        if (!s.is(Blocks.CANDLE) || s.getValue(CandleBlock.LIT)) return;
        p.level().setBlock(e.getPos(), s.setValue(CandleBlock.LIT, true), 2 | 16);
        p.level().playSound(null, e.getPos(), SoundEvents.FIRECHARGE_USE, net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 0.8F);
        live.lit++;
        live.scare(-6F);
        if (live.lit >= CANDLES) {
            ServerPlayer c = p.server.getPlayerList().getPlayer(live.caster);
            if (c != null) c.displayClientMessage(Component.translatable("infinity.fear.escaped", p.getDisplayName()), true);
            FearSessions.release(p.server, p.getUUID(), true);
        }
    }

    // ------------------------------------------------------------------ audience

    static void invite(ServerPlayer c, ServerPlayer who, UUID victim) {
        FearLive l = FearSessions.LIVE.get(victim);
        ServerPlayer v = l == null ? null : c.server.getPlayerList().getPlayer(victim);
        if (l == null || v == null || !l.caster.equals(c.getUUID()) || who == c || FearSessions.LIVE.containsKey(who.getUUID()) || who.level().dimension() == FearRealm.DIM) return;
        INVITES.put(who.getUUID(), new Invite(c.getUUID(), victim, c.server.getTickCount() + 1200));
        PacketDistributor.sendToPlayer(who, new FearNet.Invite(c.getUUID(), c.getGameProfile().getName(), v.getGameProfile().getName()));
        c.displayClientMessage(Component.translatable("infinity.fear.invited", who.getDisplayName()), true);
    }

    static void answer(ServerPlayer who, boolean accept) {
        Invite inv = INVITES.remove(who.getUUID());
        if (inv == null || !accept || inv.until < who.server.getTickCount()) return;
        FearLive l = FearSessions.LIVE.get(inv.victim);
        ServerPlayer v = l == null ? null : who.server.getPlayerList().getPlayer(inv.victim);
        if (l == null || !l.caster.equals(inv.caster) || v == null || l.state != FearLive.ACTIVE || FearSessions.LIVE.containsKey(who.getUUID())) return;
        FearSessions.startWatch(who, v, 3);
    }

    static boolean isAudience(UUID spectator, UUID caster) {
        FearSessions.WatchView w = FearSessions.watchOf(spectator);
        if (w == null || w.mode() != 3) return false;
        FearLive l = FearSessions.LIVE.get(w.victim());
        return l != null && l.caster.equals(caster);
    }

    // ------------------------------------------------------------------ join / split

    static void join(ServerPlayer c, FearLive a, FearLive b) {
        if (a == b || a.slot == b.slot || a.state != FearLive.ACTIVE || b.state != FearLive.ACTIVE) return;
        ServerPlayer va = c.server.getPlayerList().getPlayer(a.victim), vb = c.server.getPlayerList().getPlayer(b.victim);
        if (va == null || vb == null) return;
        FearApi.Creatures cr = FearApi.creatures();
        if (cr != null) cr.clear(b.victim);
        FearSessions.leaveSlot(b, vb);
        clearCandles(b, vb.serverLevel());
        b.slot = a.slot; b.owner = a.owner; b.scene = a.scene; b.follow = a.victim;
        b.spawn = va.blockPosition();
        b.hunt = false; b.timers.clear();
        b.state = FearLive.ENTERING; b.enterAge = 0; b.firstEntry = false;
        FearNet.send(vb, FearNet.SWAP, 0, 0);
    }

    static void split(ServerPlayer c, FearLive b) {
        ServerPlayer vb = c.server.getPlayerList().getPlayer(b.victim);
        if (vb == null || b.state != FearLive.ACTIVE) return;
        boolean shared = false;
        for (FearLive o : FearSessions.LIVE.values()) if (o != b && o.slot == b.slot) shared = true;
        if (!shared) return;
        FearApi.Creatures cr = FearApi.creatures();
        if (cr != null) cr.clear(b.victim);
        clearCandles(b, vb.serverLevel());
        b.follow = null; b.owner = b.victim;
        b.slot = FearData.get(c.server).slotFor(b.victim);
        b.hunt = false; b.timers.clear();
        b.state = FearLive.ENTERING; b.enterAge = 0; b.firstEntry = false;
        FearNet.send(vb, FearNet.SWAP, 0, 0);
        FearSessions.beginScene(b, vb, b.scene);
    }

    // ------------------------------------------------------------------ replay

    static void replay(ServerPlayer c, FearLive live, int index) {
        if (index < 0 || index >= live.rec.list.size() || !FearSessions.isWatching(c.getUUID())) return;
        float[] d = live.rec.list.get(index).data;
        if (d.length < 10) return;
        if (c.getCamera() != c) c.setCamera(c);
        REPLAYS.put(c.getUUID(), new Replay(d, live.victim));
        PacketDistributor.sendToPlayer(c, new FearNet.Replay(d));
    }

    static boolean replaying(UUID caster) { return REPLAYS.containsKey(caster); }

    /** Moves the (spectator) caster along the recorded path so the chunks around the camera stay loaded. */
    static void tick(net.minecraft.server.MinecraftServer s) {
        if (!INVITES.isEmpty()) INVITES.values().removeIf(i -> i.until < s.getTickCount());
        if (REPLAYS.isEmpty()) return;
        for (Map.Entry<UUID, Replay> en : new ArrayList<>(REPLAYS.entrySet())) {
            ServerPlayer c = s.getPlayerList().getPlayer(en.getKey());
            Replay r = en.getValue();
            if (c == null || !c.isAlive() || c.level().dimension() != FearRealm.DIM || r.i * 5 >= r.data.length) {
                REPLAYS.remove(en.getKey());
                FearSessions.resumeWatch(en.getKey());
                continue;
            }
            if (r.tick++ % 6 == 0) {
                int o = Math.min(r.i, r.data.length / 5 - 1) * 5;
                c.teleportTo(c.serverLevel(), r.data[o], r.data[o + 1] - 1.6, r.data[o + 2], EnumSet.of(net.minecraft.world.entity.RelativeMovement.X_ROT, net.minecraft.world.entity.RelativeMovement.Y_ROT), 0F, 0F);
            }
            if (r.tick % 2 == 0) r.i++;
        }
    }

    static void cleanup(UUID player) { INVITES.remove(player); REPLAYS.remove(player); }

    static void reset() { INVITES.clear(); REPLAYS.clear(); }

    private FearExtras() {}
}
