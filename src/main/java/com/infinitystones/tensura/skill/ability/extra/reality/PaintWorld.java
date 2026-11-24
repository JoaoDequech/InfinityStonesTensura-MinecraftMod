package com.infinitystones.tensura.skill.ability.extra.reality;

import com.infinitystones.tensura.skill.ability.extra.Terrain;
import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.ExtraSettings;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Ground;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Paint the World (Reality). Left (aim): the exposed surface of everything within 9 blocks of the aimed point is repainted,
 * in a rippling wave under layered red rune circles, with a themed palette chosen with Alt+scroll (candy, gold, crystal,
 * crimson). Right: wipe your paint now. It lasts 60 s and then washes off from the outside in. Every block returns with its
 * exact original state; blocks that were changed meanwhile (broken, replaced) are never touched again. Painted blocks drop
 * nothing (no free items from the palette), block entities / logs / ores / unbreakable blocks are never painted, and only
 * with mobGriefing on and inside what the player may modify. One paint per player.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class PaintWorld implements Ability {
    public static final PaintWorld INSTANCE = new PaintWorld();
    private static final String MODE = "reality.paint_world", K = "infinity.extra.paint.";
    private static final float R = 9F;
    private static final int MAX_BLOCKS = 1800, HOLD = 1200, GROW = 28, MAX_PER_TICK = 160, FLAGS = 2 | 16;
    private static final int RED = Stone.REALITY.color() & 0xFFFFFF;
    private static final int[] THEME_COLOR = {0xFF7EC8, 0xFFC83A, 0x6FE8FF, 0xC01830};
    private static final Block[][] PAL = {
            {Blocks.PINK_CONCRETE, Blocks.WHITE_CONCRETE, Blocks.LIGHT_BLUE_CONCRETE, Blocks.YELLOW_CONCRETE, Blocks.MAGENTA_CONCRETE},
            {Blocks.YELLOW_CONCRETE, Blocks.ORANGE_CONCRETE, Blocks.YELLOW_TERRACOTTA, Blocks.YELLOW_CONCRETE, Blocks.ORANGE_TERRACOTTA},
            {Blocks.LIGHT_BLUE_STAINED_GLASS, Blocks.CYAN_STAINED_GLASS, Blocks.WHITE_STAINED_GLASS, Blocks.PURPLE_STAINED_GLASS},
            {Blocks.RED_CONCRETE, Blocks.BLACK_CONCRETE, Blocks.RED_TERRACOTTA, Blocks.RED_CONCRETE, Blocks.MAGENTA_CONCRETE}
    };

    private record Painted(BlockState from, BlockState to) {}

    private static final class Job {
        final UUID owner; final ServerLevel level; final Vec3 c; final int theme;
        final List<BlockPos> order = new ArrayList<>();
        final Map<BlockPos, BlockState> scanned = new HashMap<>();
        final Map<BlockPos, Painted> done = new LinkedHashMap<>();
        int cursor, phase, holdStart, back;
        boolean revert;
        Job(UUID owner, ServerLevel level, Vec3 c, int theme) { this.owner = owner; this.level = level; this.c = c; this.theme = theme; }
    }

    private static final Map<UUID, Job> JOBS = new HashMap<>();
    /** Painted position → the painted state (so drops of painted blocks can be suppressed). */
    private static final Map<ResourceKey<Level>, Map<BlockPos, BlockState>> PAINTED = new HashMap<>();

    @Override public Stone stone() { return Stone.REALITY; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        Job live = JOBS.get(p.getUUID());
        if (alt) {
            if (live == null) return Fx.fail(p, K + "none");
            live.revert = true;
            Fx.bar(p, Stone.REALITY, K + "wipe");
            return true;
        }
        if (live != null) return Fx.fail(p, K + "busy");
        ServerLevel l = p.serverLevel();
        int theme = ExtraSettings.get(p, MODE, 1, 1, 4, 1) - 1;
        Vec3 c = Aim.point(p, 40);
        Job job = new Job(p.getUUID(), l, c, theme);
        scan(p, job);
        if (job.order.isEmpty()) return Fx.fail(p, K + "nothing");
        JOBS.put(p.getUUID(), job);
        open(l, job);
        Tasks.add(new Tasks.Task() {
            @Override public boolean tick(int age) { return PaintWorld.tick(job, age); }
            @Override public void end() { finish(job); }
        });
        Fx.bar(p, Stone.REALITY, K + "on", Component.translatable(K + "theme" + (theme + 1)), job.order.size());
        return true;
    }

    // ------------------------------------------------------------------ scan

    private static void scan(ServerPlayer p, Job job) {
        ServerLevel l = job.level;
        BlockPos o = BlockPos.containing(job.c);
        int r = (int) Math.ceil(R);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        List<BlockPos> out = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++)
            for (int dy = -r; dy <= r; dy++)
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > R * R) continue;
                    m.set(o.getX() + dx, o.getY() + dy, o.getZ() + dz);
                    if (!l.isInWorldBounds(m) || !l.isLoaded(m)) continue;
                    BlockState s = l.getBlockState(m);
                    if (!paintable(l, m, s) || isPainted(l, m) || !exposed(l, m, n)) continue;
                    if (!Terrain.may(p, l, m)) continue;
                    BlockPos im = m.immutable();
                    out.add(im);
                    job.scanned.put(im, s);
                }
        out.sort((a, b) -> Double.compare(Vec3.atCenterOf(a).distanceToSqr(job.c), Vec3.atCenterOf(b).distanceToSqr(job.c)));
        if (out.size() > MAX_BLOCKS) out = out.subList(0, MAX_BLOCKS);
        job.order.addAll(out);
    }

    private static boolean paintable(ServerLevel l, BlockPos pos, BlockState s) {
        if (s.isAir() || s.hasBlockEntity() || !s.getFluidState().isEmpty() || s.getDestroySpeed(l, pos) < 0) return false;
        if (s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.is(Tags.Blocks.ORES) || s.is(BlockTags.WITHER_IMMUNE)) return false;
        return s.isCollisionShapeFullBlock(l, pos);
    }

    private static boolean exposed(ServerLevel l, BlockPos pos, BlockPos.MutableBlockPos n) {
        for (Direction d : Direction.values()) {
            n.setWithOffset(pos, d);
            if (!l.isLoaded(n)) continue;
            if (!l.getBlockState(n).isCollisionShapeFullBlock(l, n)) return true;
        }
        return false;
    }

    private static boolean isPainted(ServerLevel l, BlockPos pos) {
        Map<BlockPos, BlockState> m = PAINTED.get(l.dimension());
        return m != null && m.containsKey(pos);
    }

    private static BlockState pick(int theme, BlockPos pos) {
        Block[] pal = PAL[theme];
        int h = pos.getX() * 73856093 ^ pos.getY() * 19349663 ^ pos.getZ() * 83492791;
        h ^= h >>> 13;
        int i = switch (theme) {
            case 0 -> Math.floorMod(Math.floorDiv(pos.getX() + pos.getY() * 2 + pos.getZ(), 2), pal.length);
            case 2 -> Math.floorMod(Math.floorDiv(pos.getY(), 2) + ((h >>> 5) & 1), pal.length);
            default -> Math.floorMod(h, pal.length);
        };
        return pal[i].defaultBlockState();
    }

    // ------------------------------------------------------------------ run

    private static void open(ServerLevel l, Job job) {
        Vec3 c = job.c;
        Vec3 g = Ground.below(l, c, 10).add(0, 0.06, 0);
        int tc = THEME_COLOR[job.theme];
        FxNet.runes(l, 0, g, R * 1.15F, RED, 60, 1F);
        FxNet.runes(l, 0, g.add(0, 0.02, 0), R * 0.8F, tc, 60, -1.6F);
        FxNet.runes(l, 0, g.add(0, 0.04, 0), R * 1.5F, 0xFFFFFF, 60, 0.5F);
        FxNet.runes(l, 0, g.add(0, 0.06, 0), R * 0.4F, tc, 60, 2.4F);
        FxNet.shockwave(l, g, R * 1.6F, RED, 20);
        FxNet.shockwave(l, g.add(0, 0.02, 0), R, tc, 26);
        FxNet.flare(l, c, R * 0.6F, tc, 12);
        FxNet.pillar(l, 0, g, 0.6F, 12, RED, 36);
        FxNet.shake(l, c, 30, 0.35F, 10);
        Fx.sound(l, c, SoundEvents.BEACON_ACTIVATE, 0.6F, 1.4F);
        Fx.sound(l, c, SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, 0.9F);
        Fx.sound(l, c, SoundEvents.ENDER_DRAGON_FLAP, 0.4F, 1.6F);
    }

    private static boolean tick(Job job, int age) {
        ServerLevel l = job.level;
        int tc = THEME_COLOR[job.theme];
        if (job.phase == 0 && job.revert) { job.phase = 2; job.back = job.cursor - 1; }
        if (job.phase == 0) {
            int target = (int) Math.min(job.order.size(), (long) job.order.size() * (age + 1) / GROW);
            int n = 0;
            int sparks = 0;
            while (job.cursor < target && n < MAX_PER_TICK) {
                BlockPos pos = job.order.get(job.cursor++);
                n++;
                if (paint(job, pos) && sparks < 36 && l.random.nextInt(3) == 0) {
                    sparks++;
                    Fx.at(l, Fx.dust(l.random.nextBoolean() ? tc : RED, 1F), Vec3.atCenterOf(pos), 2, 0.5, 0);
                }
            }
            double front = R * Math.min(1, (age + 1) / (double) GROW);
            Fx.shell(l, job.c, front, 40, Fx.dust(tc, 1.5F));
            Fx.shell(l, job.c, front, 20, Fx.dust(RED, 1.2F));
            if (age % 5 == 0) {
                FxNet.shockwave(l, Ground.below(l, job.c, 10).add(0, 0.1, 0), (float) front, age % 10 == 0 ? RED : tc, 14);
                Fx.sound(l, job.c, SoundEvents.AMETHYST_BLOCK_CHIME, 0.45F, 0.7F + age * 0.025F);
            }
            if (job.cursor >= job.order.size() && age >= GROW) { job.phase = 1; job.holdStart = age; }
            return true;
        }
        if (job.phase == 1) {
            ServerPlayer owner = l.getServer().getPlayerList().getPlayer(job.owner);
            if (job.revert || age - job.holdStart >= HOLD || owner == null && age - job.holdStart > 40) {
                job.phase = 2;
                job.back = job.order.size() - 1;
                FxNet.shockwave(l, Ground.below(l, job.c, 10).add(0, 0.1, 0), R * 1.5F, RED, 20);
                Fx.sound(l, job.c, SoundEvents.BEACON_DEACTIVATE, 0.6F, 1.4F);
                return true;
            }
            if (age % 20 == 0 && !job.done.isEmpty()) {
                BlockPos any = job.order.get(l.random.nextInt(job.order.size()));
                Fx.at(l, Fx.light(Stone.REALITY, 1F), Vec3.atCenterOf(any).add(0, 0.6, 0), 3, 0.3, 0.01);
                Fx.at(l, ParticleTypes.END_ROD, Vec3.atCenterOf(any).add(0, 0.7, 0), 1, 0.2, 0.02);
            }
            return true;
        }
        int per = Math.max(1, (int) Math.ceil(job.order.size() / (double) GROW));
        int sparks = 0;
        for (int i = 0; i < per && job.back >= 0; i++) {
            BlockPos pos = job.order.get(job.back--);
            if (unpaint(job, pos) && sparks < 30 && l.random.nextInt(3) == 0) {
                sparks++;
                Fx.at(l, Fx.dust(l.random.nextBoolean() ? tc : RED, 0.9F), Vec3.atCenterOf(pos), 2, 0.5, 0);
            }
        }
        if (age % 6 == 0) {
            double d = job.back >= 0 ? Math.sqrt(Vec3.atCenterOf(job.order.get(job.back)).distanceToSqr(job.c)) : 0;
            Fx.shell(l, job.c, d, 24, Fx.dust(tc, 1.2F));
            Fx.sound(l, job.c, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.4F, 1.6F);
        }
        return job.back >= 0;
    }

    private static boolean paint(Job job, BlockPos pos) {
        ServerLevel l = job.level;
        BlockState from = job.scanned.get(pos);
        if (from == null || !l.isLoaded(pos) || l.getBlockState(pos) != from || isPainted(l, pos)) return false;
        BlockState to = pick(job.theme, pos);
        l.setBlock(pos, to, FLAGS);
        PAINTED.computeIfAbsent(l.dimension(), k -> new HashMap<>()).put(pos, to);
        job.done.put(pos, new Painted(from, to));
        return true;
    }

    private static boolean unpaint(Job job, BlockPos pos) {
        ServerLevel l = job.level;
        Painted pt = job.done.remove(pos);
        if (pt == null) return false;
        Map<BlockPos, BlockState> m = PAINTED.get(l.dimension());
        if (m != null && m.get(pos) == pt.to) m.remove(pos);
        if (!l.isLoaded(pos) || l.getBlockState(pos) != pt.to) return false;
        l.setBlock(pos, pt.from, FLAGS);
        return true;
    }

    /** Whatever is left goes back at once (also on server stop). */
    private static void finish(Job job) {
        JOBS.remove(job.owner, job);
        for (BlockPos pos : new ArrayList<>(job.done.keySet())) unpaint(job, pos);
        Fx.at(job.level, Fx.light(Stone.REALITY, 1.2F), job.c, 24, 2.5, 0.02);
        Fx.sound(job.level, job.c, SoundEvents.BEACON_POWER_SELECT, 0.5F, 1.5F);
    }

    @SubscribeEvent
    public static void drops(BlockDropsEvent e) {
        Map<BlockPos, BlockState> m = PAINTED.get(e.getLevel().dimension());
        if (m == null) return;
        BlockState to = m.get(e.getPos());
        if (to != null && to == e.getState()) {
            e.getDrops().clear();
            e.setDroppedExperience(0);
        }
    }
}
