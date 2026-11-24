package com.infinitystones.tensura.skill.ability.extra.v2d;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.compat.ClaimsCompat;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.ExtraSettings;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lucid Dream (Mind + Reality). Left starts / stops the channel: while it runs, golden-red blocks of light appear on the
 * aimed face as you sweep your gaze (bridges, stairs, shelters). They drop nothing and vanish with a shimmer after 60 s
 * (their own scheduled tick, so they also expire after a restart). At most 64 at a time (the oldest fade first).
 * Right: next brush (single / line / wall / stairs). Alt+scroll: brush size 1-3.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class LucidDream implements Ability {
    public static final LucidDream INSTANCE = new LucidDream();
    private static final String MODE = "mind.lucid_dream";
    private static final String[] BRUSHES = {"single", "line", "wall", "stairs"};
    private static final int GOLD = 0xF2D02B, RED = 0xE22B3B, MAX_BLOCKS = 64;

    private static final class Session {
        final ServerLevel level; final Deque<BlockPos> placed = new ArrayDeque<>(); BlockPos lastTarget; final int crown = FxNet.newId();
        Vec3 lastSeen = Vec3.ZERO;
        Tasks.Handle handle;
        Session(ServerLevel level) { this.level = level; }
    }

    private static final Map<UUID, Session> ACTIVE = new HashMap<>();

    @Override public Stone stone() { return Stone.MIND; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        if (!Stone.REALITY.isHeldBy(p)) return Fx.fail(p, "infinity.extra.v2d.lucid.need_reality");
        if (alt) {
            int brush = (ExtraSettings.choice(p, MODE, 0) + 1) % BRUSHES.length;
            ExtraSettings.setChoice(p, MODE, brush);
            Fx.bar(p, Stone.MIND, "infinity.extra.v2d.lucid.brush", Component.translatable("infinity.extra.v2d.lucid.brush." + BRUSHES[brush]));
            Fx.sound(p.serverLevel(), p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.4F, 1.0F + brush * 0.2F);
            return true;
        }
        Session old = ACTIVE.get(p.getUUID());
        if (old != null) {
            old.handle.cancel();
            Fx.bar(p, Stone.MIND, "infinity.extra.v2d.lucid.off");
            return true;
        }
        begin(p);
        return true;
    }

    private static void begin(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        Session s = new Session(l);
        UUID id = p.getUUID();
        ACTIVE.put(id, s);
        FxNet.shockwave(l, p.position().add(0, 0.1, 0), 4F, GOLD, 18);
        FxNet.shockwave(l, p.position().add(0, 0.2, 0), 3F, RED, 14);
        FxNet.runes(l, 0, p.position().add(0, 0.05, 0), 2.5F, RED, 30, 3F);
        Fx.sound(l, p.position(), SoundEvents.BEACON_ACTIVATE, 0.5F, 1.4F);
        Fx.sound(l, p.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6F, 0.8F);
        s.handle = Tasks.add(new Tasks.Task() {
            @Override public boolean tick(int age) {
                ServerPlayer owner = l.getServer().getPlayerList().getPlayer(id);
                if (owner == null || owner.isRemoved() || !owner.isAlive() || owner.serverLevel() != l) return false;
                if (age % 10 == 0 && !(Stone.MIND.isHeldBy(owner) && Stone.REALITY.isHeldBy(owner))) return false;
                return step(owner, s, age);
            }
            @Override public void end() {
                if (ACTIVE.get(id) == s) ACTIVE.remove(id);
                FxNet.kill(l, s.lastSeen, s.crown);
            }
        });
        Fx.bar(p, Stone.MIND, "infinity.extra.v2d.lucid.on", Component.translatable("infinity.extra.v2d.lucid.brush." + BRUSHES[ExtraSettings.choice(p, MODE, 0)]));
    }

    private static boolean step(ServerPlayer p, Session s, int age) {
        ServerLevel l = s.level;
        s.lastSeen = p.position();
        if (age % 20 == 0) {
            FxNet.crown(l, s.crown, p, age % 40 == 0 ? GOLD : RED, 30);
            s.placed.removeIf(pos -> !l.isLoaded(pos) || !l.getBlockState(pos).is(LucidLightBlock.block()));
        }
        if (age % 2 != 0) return true;
        Target t = target(p);
        Vec3 preview = Vec3.atCenterOf(t.pos);
        if (age % 4 == 0) {
            Fx.at(l, Fx.dust(age % 8 == 0 ? GOLD : RED, 1F), preview, 2, 0.35, 0);
            l.sendParticles(p, ParticleTypes.END_ROD, true, preview.x, preview.y, preview.z, 1, 0.2, 0.2, 0.2, 0.01);
        }
        if (t.pos.equals(s.lastTarget)) return true;
        s.lastTarget = t.pos;
        int brush = ExtraSettings.choice(p, MODE, 0);
        int size = ExtraSettings.get(p, MODE, 1, 1, 3, 1);
        int made = 0;
        Map<Long, Boolean> claim = new HashMap<>();
        Vec3 first = null;
        for (BlockPos pos : shape(t, brush, size, p.getDirection())) {
            if (!l.isLoaded(pos) || pos.getY() < l.getMinBuildHeight() || pos.getY() >= l.getMaxBuildHeight()) continue;
            if (pos.distSqr(p.blockPosition()) > 18 * 18) continue;
            if (!l.getBlockState(pos).isAir()) continue;
            if (!l.isUnobstructed(LucidLightBlock.block().defaultBlockState(), pos, CollisionContext.empty())) continue;
            if (!claim.computeIfAbsent(new net.minecraft.world.level.ChunkPos(pos).toLong(), k -> ClaimsCompat.canModify(p, l, pos))) continue;
            if (!LucidLightBlock.place(l, pos)) continue;
            s.placed.addLast(pos);
            made++;
            if (first == null) first = Vec3.atCenterOf(pos);
            Vec3 c = Vec3.atCenterOf(pos);
            Fx.at(l, Fx.dust(l.random.nextBoolean() ? GOLD : RED, 1.2F), c, 4, 0.4, 0);
            if (made % 3 == 0) Fx.at(l, ParticleTypes.END_ROD, c, 1, 0.3, 0.02);
        }
        while (s.placed.size() > MAX_BLOCKS) LucidLightBlock.vanish(l, s.placed.pollFirst());
        if (made > 0) {
            Fx.sound(l, first, SoundEvents.AMETHYST_BLOCK_PLACE, 0.5F, 0.8F + l.random.nextFloat() * 0.5F);
            if (made >= 6) FxNet.flare(l, first, 1.6F, GOLD, 6);
        }
        return true;
    }

    private record Target(BlockPos pos, Direction face) {}

    private static Target target(ServerPlayer p) {
        BlockHitResult hit = Aim.block(p, 7);
        if (hit.getType() == HitResult.Type.MISS) {
            Vec3 v = p.getEyePosition().add(p.getLookAngle().scale(4.5));
            return new Target(BlockPos.containing(v), p.getDirection());
        }
        return new Target(hit.getBlockPos().relative(hit.getDirection()), hit.getDirection());
    }

    private static List<BlockPos> shape(Target t, int brush, int size, Direction fwd) {
        List<BlockPos> out = new ArrayList<>();
        Direction right = fwd.getClockWise();
        BlockPos a = t.pos;
        switch (brush) {
            case 1 -> { for (int i = 0; i < 4 * size; i++) out.add(a.relative(fwd, i)); }
            case 2 -> {
                int half = size;
                for (int w = -half; w <= half; w++) for (int h = 0; h < 2 * size; h++) out.add(a.relative(right, w).above(h));
            }
            case 3 -> {
                int steps = 3 * size + 1, lo = -(size - 1) / 2;
                for (int i = 0; i < steps; i++) for (int w = 0; w < size; w++) out.add(a.relative(fwd, i).above(i).relative(right, lo + w));
            }
            default -> {
                int lo = -(size - 1) / 2;
                for (int x = 0; x < size; x++) for (int y = 0; y < size; y++) for (int z = 0; z < size; z++) out.add(a.offset(lo + x, lo + y, lo + z));
            }
        }
        return out;
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        Session s = ACTIVE.get(e.getEntity().getUUID());
        if (s != null) s.handle.cancel();
    }

    @SubscribeEvent
    public static void changeDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        Session s = ACTIVE.get(e.getEntity().getUUID());
        if (s != null) s.handle.cancel();
    }

    private LucidDream() {}
}
