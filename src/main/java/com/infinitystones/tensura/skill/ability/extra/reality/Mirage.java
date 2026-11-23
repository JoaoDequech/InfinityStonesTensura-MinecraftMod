package com.infinitystones.tensura.skill.ability.extra.reality;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.ExtraSettings;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Priv;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mirage. Left (aim a block): the N×N square on the aimed face (Alt+scroll 1–5) LOOKS like the block in your off hand
 * to every other player (empty off hand = invisible); the real blocks do not change. You see a red outline.
 * 2 min, up to 64 blocks per player (oldest go first). Right: undo all your mirages.
 * Fake blocks are re-sent every 10 ticks (block updates / chunk reloads would reveal them).
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class Mirage implements Ability {
    public static final Mirage INSTANCE = new Mirage();
    static final String MODE = "reality.mirage";
    private static final String K = "infinity.extra.mirage.";
    private static final int MAX = 64;

    /** One disguised block. */
    private static final class Spot {
        final ServerLevel level; final BlockPos pos; final BlockState look; final long until;
        final int outline = FxNet.newId();
        Spot(ServerLevel level, BlockPos pos, BlockState look, long until) { this.level = level; this.pos = pos; this.look = look; this.until = until; }
    }

    private static final class Owner {
        final UUID id;
        final Map<BlockPos, Spot> spots = new LinkedHashMap<>();
        final Set<ServerPlayer> viewers = Collections.newSetFromMap(new WeakHashMap<>());
        Tasks.Handle task;
        Owner(UUID id) { this.id = id; }
    }

    private static final Map<UUID, Owner> OWNERS = new ConcurrentHashMap<>();

    @Override public Stone stone() { return Stone.REALITY; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        if (alt) {
            Owner o = OWNERS.get(p.getUUID());
            if (o == null || o.spots.isEmpty()) return Fx.fail(p, K + "none");
            int n = o.spots.size();
            clearAll(o, true);
            Fx.bar(p, Stone.REALITY, K + "undone", n);
            return true;
        }
        BlockHitResult hit = Aim.block(p, 48);
        if (hit.getType() == HitResult.Type.MISS) return Fx.fail(p, K + "no_block");
        ItemStack off = p.getOffhandItem();
        BlockState look;
        if (off.isEmpty()) look = Blocks.AIR.defaultBlockState();
        else if (off.getItem() instanceof BlockItem bi) look = bi.getBlock().defaultBlockState();
        else return Fx.fail(p, K + "not_block");

        ServerLevel l = p.serverLevel();
        int size = ExtraSettings.get(p, MODE, 3, 1, 5, 1);
        float mult = Rules.mult(p, "reality");
        long until = l.getGameTime() + (long) (20 * 120 * mult);
        Direction face = hit.getDirection();
        Direction.Axis ax = face.getAxis();
        BlockPos center = hit.getBlockPos();
        int lo = -(size - 1) / 2, hi = size / 2;

        Owner o = OWNERS.computeIfAbsent(p.getUUID(), Owner::new);
        List<BlockPos> added = new ArrayList<>();
        for (int a = lo; a <= hi; a++) for (int b = lo; b <= hi; b++) {
            BlockPos pos = switch (ax) {
                case X -> center.offset(0, a, b);
                case Y -> center.offset(a, 0, b);
                default -> center.offset(a, b, 0);
            };
            if (!l.isLoaded(pos) || l.getBlockState(pos).isAir() && look.isAir()) continue;
            Spot old = o.spots.remove(pos);
            if (old != null) reveal(o, old);
            o.spots.put(pos.immutable(), new Spot(l, pos.immutable(), look, until));
            added.add(pos);
        }
        if (added.isEmpty()) return Fx.fail(p, K + "no_block");
        while (o.spots.size() > MAX) {
            Spot first = o.spots.values().iterator().next();
            o.spots.remove(first.pos);
            reveal(o, first);
        }
        refresh(p.server, o, true);
        if (o.task == null || !o.task.alive()) o.task = Tasks.add(new Tasks.Task() {
            @Override public boolean tick(int age) { return ownerTick(o, age); }
            @Override public void end() { clearAll(o, false); }
        });

        // casting: the face shimmers and rewrites itself
        int c = Stone.REALITY.color();
        Vec3 mid = Vec3.atCenterOf(center).add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.52));
        FxNet.beam(l, p.getEyePosition().add(0, -0.3, 0), mid, c, 0.12F, 10);
        FxNet.panel(l, FxNet.newId(), mid, Vec3.atLowerCornerOf(face.getNormal()), size / 2F + 0.2F, c, 18);
        FxNet.flare(l, mid, 1.6F, c, 10);
        for (BlockPos pos : added) {
            Vec3 v = Vec3.atCenterOf(pos);
            Fx.at(l, Fx.dust(Stone.REALITY, 1F), v, 4, 0.4, 0);
        }
        Fx.sound(l, mid, SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.6F, 0.8F);
        Fx.sound(l, mid, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.5F, 1.4F);
        Fx.bar(p, Stone.REALITY, K + "made", added.size(), o.spots.size(), MAX);
        return true;
    }

    private static boolean ownerTick(Owner o, int age) {
        if (o.spots.isEmpty()) return false;
        var server = o.spots.values().iterator().next().level.getServer();
        List<Spot> expired = new ArrayList<>();
        for (Spot s : o.spots.values()) if (s.level.getGameTime() >= s.until) expired.add(s);
        for (Spot s : expired) { o.spots.remove(s.pos); reveal(o, s); }
        if (o.spots.isEmpty()) return false;
        if (age % 10 == 0) refresh(server, o, age % 40 == 0);
        return true;
    }

    /** Fake blocks to every other player near; outline (packet only to the owner) every 40 ticks. */
    private static void refresh(net.minecraft.server.MinecraftServer server, Owner o, boolean outline) {
        ServerPlayer owner = server.getPlayerList().getPlayer(o.id);
        for (Spot s : o.spots.values()) {
            Vec3 v = Vec3.atCenterOf(s.pos);
            for (ServerPlayer sp : s.level.players()) {
                if (sp == owner || sp.distanceToSqr(v) > 96 * 96) continue;
                Priv.fakeBlock(sp, s.pos, s.look);
                o.viewers.add(sp);
            }
            if (outline && owner != null && owner.level() == s.level && owner.distanceToSqr(v) < 96 * 96) {
                PacketDistributor.sendToPlayer(owner, new FxNet.Fx(FxNet.CUBE, s.outline, Stone.REALITY.color() & 0xFFFFFF, 60, 0.51F, 0,
                        v.x, v.y, v.z, 0, 0, 0, -1, -1));
            }
        }
        if (owner != null && outline) {
            for (Spot s : o.spots.values()) {
                if (owner.level() != s.level || owner.distanceToSqr(Vec3.atCenterOf(s.pos)) > 32 * 32) continue;
                Vec3 v = Vec3.atCenterOf(s.pos);
                s.level.sendParticles(owner, Fx.dust(Stone.REALITY, 0.8F), true, v.x, v.y + 0.5, v.z, 2, 0.35, 0.05, 0.35, 0);
            }
        }
    }

    private static void reveal(Owner o, Spot s) {
        for (ServerPlayer v : o.viewers) if (!v.hasDisconnected() && v.level() == s.level) Priv.realBlock(v, s.pos);
        ServerPlayer owner = s.level.getServer().getPlayerList().getPlayer(o.id);
        if (owner != null) PacketDistributor.sendToPlayer(owner, new FxNet.Fx(FxNet.KILL, s.outline, 0, 1, 0, 0, s.pos.getX(), s.pos.getY(), s.pos.getZ(), 0, 0, 0, -1, -1));
    }

    private static void clearAll(Owner o, boolean fx) {
        for (Spot s : new ArrayList<>(o.spots.values())) {
            reveal(o, s);
            if (fx) Fx.at(s.level, Fx.dust(Stone.REALITY, 1F), Vec3.atCenterOf(s.pos), 3, 0.4, 0);
        }
        o.spots.clear();
        if (o.task != null) o.task.cancel();
        OWNERS.remove(o.id, o);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        Owner o = OWNERS.get(e.getEntity().getUUID());
        if (o != null) clearAll(o, false);
    }
}
