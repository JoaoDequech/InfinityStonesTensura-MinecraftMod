package com.infinitystones.tensura.skill.ability.extra.v2d;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.ExtraSettings;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.MenuNet;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.Terrain;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lucid Dream (Mind + Reality): an interactive dream-building tool. The wheel toggles it. While it is on, a golden ghost of the
 * current brush follows the aimed surface; left click places it (the blocks of light grow outward from the aimed point), right
 * click opens the shape menu (single block, line from the last point, wall, floor, stairs, bridge to the aimed point, dome
 * shelter), scroll sets the size, Sneak + left click undoes the last placement and middle click stops the dream. Blocks only
 * fill air that no entity occupies, never inside foreign claims, drop nothing, glow and fade away with a shimmer after 2
 * minutes. Up to 512 stand at once (the oldest fade first). The preview is drawn by the client from the same
 * {@link LucidShapes}; everything that really happens is decided here.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class LucidDream implements Ability {
    public static final LucidDream INSTANCE = new LucidDream();
    private static final String MODE = "mind.lucid_dream", NODE = "lucid_dream", K = "infinity.extra.v2d.lucid.";
    private static final int GOLD = 0xF2D02B, RED = 0xE22B3B, MAX_BLOCKS = 512, PER_TICK = 24;
    public static final double REACH = 24, FAR_POINT = 8, BUILD_RADIUS = 48;

    private record Q(BlockPos pos, List<BlockPos> group) {}

    private static final class Session {
        final ServerLevel level;
        final Deque<List<BlockPos>> groups = new ArrayDeque<>();
        final Deque<Q> queue = new ArrayDeque<>();
        @Nullable BlockPos anchor;
        final int crown = FxNet.newId();
        Vec3 lastSeen = Vec3.ZERO;
        Tasks.Handle handle;
        int lastAction = -10, drained;
        boolean dirty;
        Session(ServerLevel level) { this.level = level; }
        int total() {
            int n = queue.size();
            for (List<BlockPos> g : groups) n += g.size();
            return n;
        }
        int standing() { int n = 0; for (List<BlockPos> g : groups) n += g.size(); return n; }
    }

    private static final Map<UUID, Session> ACTIVE = new HashMap<>();

    @Override public Stone stone() { return Stone.MIND; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        if (!Stone.REALITY.isHeldBy(p)) return Fx.fail(p, K + "need_reality");
        if (alt) return MenuNet.open(p, Stone.MIND, NODE, this) || Fx.fail(p, K + "need_reality");
        Session old = ACTIVE.get(p.getUUID());
        if (old != null) {
            old.handle.cancel();
            Fx.bar(p, Stone.MIND, K + "off");
            return true;
        }
        begin(p);
        return true;
    }

    // ------------------------------------------------------------------ shape menu

    private static int brush(ServerPlayer p) { return Mth.clamp(ExtraSettings.choice(p, MODE, 0), 0, LucidShapes.NAMES.length - 1); }

    private static int size(ServerPlayer p) { return ExtraSettings.get(p, MODE, 1, 1, LucidShapes.MAX_SIZE, 1); }

    @Override
    public List<MenuNet.Option> menu(ServerPlayer p) {
        int cur = brush(p);
        MenuNet.Option[] o = new MenuNet.Option[LucidShapes.NAMES.length];
        for (int i = 0; i < o.length; i++) o[i] = MenuNet.opt(K + "brush." + LucidShapes.NAMES[i], LucidShapes.ICONS[i], i == cur);
        return List.of(o);
    }

    @Override
    public void pick(ServerPlayer p, int index) {
        if (index < 0 || index >= LucidShapes.NAMES.length) return;
        ExtraSettings.setChoice(p, MODE, index);
        Fx.bar(p, Stone.MIND, K + "brush", Component.translatable(K + "brush." + LucidShapes.NAMES[index]));
        Fx.sound(p.serverLevel(), p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.5F, 0.8F + index * 0.15F);
        Session s = ACTIVE.get(p.getUUID());
        if (s != null) sync(p, s);
    }

    // ------------------------------------------------------------------ session

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
                s.queue.clear();
                FxNet.kill(l, s.lastSeen, s.crown);
                ServerPlayer owner = l.getServer().getPlayerList().getPlayer(id);
                if (owner != null && !owner.hasDisconnected()) LucidNet.to(owner, new LucidNet.State(false, 0, 1, false, 0, 0, 0, 0));
            }
        });
        sync(p, s);
        Fx.bar(p, Stone.MIND, K + "on", Component.translatable(K + "brush." + LucidShapes.NAMES[brush(p)]));
    }

    private static void sync(ServerPlayer p, Session s) {
        BlockPos a = s.anchor;
        LucidNet.to(p, new LucidNet.State(true, brush(p), size(p), a != null, a == null ? 0 : a.getX(), a == null ? 0 : a.getY(), a == null ? 0 : a.getZ(), s.standing()));
        s.dirty = false;
    }

    private static boolean step(ServerPlayer p, Session s, int age) {
        ServerLevel l = s.level;
        s.lastSeen = p.position();
        if (age % 20 == 0) {
            FxNet.crown(l, s.crown, p, age % 40 == 0 ? GOLD : RED, 30);
            boolean changed = false;
            for (Iterator<List<BlockPos>> it = s.groups.iterator(); it.hasNext(); ) {
                List<BlockPos> g = it.next();
                int before = g.size();
                g.removeIf(pos -> !l.isLoaded(pos) || !l.getBlockState(pos).is(LucidLightBlock.block()));
                if (g.size() != before) changed = true;
                if (g.isEmpty() && s.queue.stream().noneMatch(q -> q.group() == g)) it.remove();
            }
            if (changed) s.dirty = true;
        }
        drain(p, s);
        if (s.dirty) sync(p, s);
        return true;
    }

    /** Grows the queued blocks outward, {@link #PER_TICK} per tick, each re-checked right before it appears. */
    private static void drain(ServerPlayer p, Session s) {
        if (s.queue.isEmpty()) { s.drained = 0; return; }
        ServerLevel l = s.level;
        Terrain.Session claims = new Terrain.Session(p);
        Vec3 first = null;
        int made = 0;
        for (int i = 0; i < PER_TICK && !s.queue.isEmpty(); i++) {
            Q q = s.queue.pollFirst();
            BlockPos pos = q.pos();
            if (!canFill(l, pos) || !claims.may(pos)) continue;
            if (!LucidLightBlock.place(l, pos)) continue;
            q.group().add(pos);
            made++;
            Vec3 c = Vec3.atCenterOf(pos);
            if (first == null) first = c;
            Fx.at(l, Fx.dust(l.random.nextBoolean() ? GOLD : RED, 1.2F), c, 3, 0.4, 0);
            if (made % 3 == 0) Fx.at(l, ParticleTypes.END_ROD, c, 1, 0.3, 0.02);
        }
        if (made > 0) {
            s.drained += made;
            s.dirty = true;
            Fx.sound(l, first, SoundEvents.AMETHYST_BLOCK_PLACE, 0.5F, 0.8F + l.random.nextFloat() * 0.3F + Math.min(0.6F, s.drained * 0.004F));
            if (made >= 12) FxNet.flare(l, first, 1.6F, GOLD, 6);
        }
        if (s.queue.isEmpty()) {
            s.drained = 0;
            s.dirty = true;
            if (first != null) Fx.sound(l, first, SoundEvents.AMETHYST_CLUSTER_PLACE, 0.5F, 1.4F);
        }
    }

    private static boolean canFill(ServerLevel l, BlockPos pos) {
        return l.isLoaded(pos) && pos.getY() >= l.getMinBuildHeight() && pos.getY() < l.getMaxBuildHeight() && l.getWorldBorder().isWithinBounds(pos)
                && l.getBlockState(pos).isAir() && l.isUnobstructed(LucidLightBlock.block().defaultBlockState(), pos, CollisionContext.empty());
    }

    // ------------------------------------------------------------------ input (left, right, scroll, sneak, middle)

    static void input(ServerPlayer p, LucidNet.Input in) {
        Session s = ACTIVE.get(p.getUUID());
        if (s == null || s.level != p.serverLevel()) return;
        switch (in.action()) {
            case 0 -> place(p, s);
            case 1 -> MenuNet.open(p, Stone.MIND, NODE, INSTANCE);
            case 2 -> undo(p, s);
            case 3 -> resize(p, s, in.value());
            case 4 -> { s.handle.cancel(); Fx.bar(p, Stone.MIND, K + "off"); }
            default -> { }
        }
    }

    /** The cell the dream builds from: the free cell on the aimed face, else a point ahead in the air. */
    public static BlockPos target(ServerPlayer p) {
        BlockHitResult hit = Aim.block(p, REACH);
        if (hit.getType() == HitResult.Type.MISS) return BlockPos.containing(p.getEyePosition().add(p.getLookAngle().scale(FAR_POINT)));
        return hit.getBlockPos().relative(hit.getDirection());
    }

    private static void place(ServerPlayer p, Session s) {
        if (p.tickCount - s.lastAction < 3) return;
        s.lastAction = p.tickCount;
        ServerLevel l = s.level;
        BlockPos t = target(p);
        List<BlockPos> cells = LucidShapes.shape(brush(p), size(p), t, p.getDirection(), s.anchor, p.blockPosition());
        Terrain.Session claims = new Terrain.Session(p);
        List<BlockPos> ok = new ArrayList<>();
        for (BlockPos pos : cells) {
            if (pos.distSqr(p.blockPosition()) > BUILD_RADIUS * BUILD_RADIUS) continue;
            if (canFill(l, pos) && claims.may(pos)) ok.add(pos);
        }
        if (ok.isEmpty()) {
            Fx.sound(l, p.position(), SoundEvents.NOTE_BLOCK_BASS.value(), 0.4F, 0.7F);
            Fx.fail(p, K + "blocked");
            return;
        }
        ok.sort(Comparator.comparingDouble(pos -> pos.distSqr(t)));
        List<BlockPos> group = new ArrayList<>();
        s.groups.addLast(group);
        for (BlockPos pos : ok) s.queue.addLast(new Q(pos, group));
        s.anchor = t;
        while (s.total() > MAX_BLOCKS && s.groups.size() > 1) fadeOldest(s);
        Vec3 c = Vec3.atCenterOf(t);
        FxNet.shockwave(l, c, 2.5F + ok.size() * 0.01F, GOLD, 12);
        Fx.sound(l, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.5F, 1.2F);
        s.dirty = true;
    }

    private static void fadeOldest(Session s) {
        List<BlockPos> g = s.groups.pollFirst();
        if (g == null) return;
        s.queue.removeIf(q -> q.group() == g);
        for (BlockPos pos : g) LucidLightBlock.vanish(s.level, pos);
    }

    private static void undo(ServerPlayer p, Session s) {
        List<BlockPos> g = s.groups.pollLast();
        if (g == null) { Fx.fail(p, K + "nothing_to_undo"); return; }
        s.queue.removeIf(q -> q.group() == g);
        for (BlockPos pos : g) LucidLightBlock.vanish(s.level, pos);
        if (s.groups.isEmpty()) s.anchor = null;
        Fx.sound(s.level, p.position(), SoundEvents.AMETHYST_BLOCK_BREAK, 0.6F, 1.1F);
        Fx.barGray(p, K + "undone", g.size());
        sync(p, s);
    }

    private static void resize(ServerPlayer p, Session s, int dir) {
        int v = Mth.clamp(size(p) + Integer.signum(dir), 1, LucidShapes.MAX_SIZE);
        ExtraSettings.set(p, MODE, v);
        var node = com.infinitystones.tensura.skill.StoneTree.of(Stone.MIND).nodes.get(NODE);
        if (node != null) com.infinitystones.tensura.wheel.AbilitySettings.set(p, node, v);
        Fx.sound(s.level, p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.4F, 0.7F + v * 0.15F);
        Fx.bar(p, Stone.MIND, K + "size", v);
        sync(p, s);
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
