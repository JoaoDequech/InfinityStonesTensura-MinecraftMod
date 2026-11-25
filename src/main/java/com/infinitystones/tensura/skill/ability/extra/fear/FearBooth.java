package com.infinitystones.tensura.skill.ability.extra.fear;

import com.infinitystones.tensura.InfinityStones;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The physical control room: a dark command booth built once in the fear realm (far from every arena) with consoles and a
 * wall of monitors. The caster goes there from the panel and returns to his exact origin when leaving; right-clicking a
 * console opens the same control panel. The client draws the monitors (see FearWorld.drawBooth).
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class FearBooth {
    public static final int CX = 448, CZ = 448, Y = FearTerrain.BASE, HX = 15, HZ = 11, H = 9;
    public static final BlockPos CONSOLE = new BlockPos(CX, Y + 1, CZ - 3);

    public static boolean inside(double x, double y, double z) {
        return Math.abs(x - CX) <= HX + 1 && Math.abs(z - CZ) <= HZ + 1 && y >= Y - 1 && y <= Y + H + 2;
    }

    static void go(ServerPlayer c) {
        ServerLevel realm = FearRealm.level(c.server);
        if (realm == null || !FearSessions.canCast(c) || FearSessions.selfLocked(c) || c.level().dimension() == FearRealm.DIM) {
            c.displayClientMessage(net.minecraft.network.chat.Component.translatable("infinity.fear.cannot"), true);
            return;
        }
        ensure(realm);
        FearData d = FearData.get(c.server);
        d.watchers.put(c.getUUID(), FearSessions.origin(c));
        d.setDirty();
        c.teleportTo(realm, CX + 0.5, Y + 1, CZ + 7.5, 180F, 0F);
    }

    static void ensure(ServerLevel l) {
        for (int cx = (CX - 20) >> 4; cx <= (CX + 20) >> 4; cx++) for (int cz = (CZ - 16) >> 4; cz <= (CZ + 16) >> 4; cz++) l.getChunk(cx, cz);
        if (l.getBlockState(CONSOLE).is(Blocks.LECTERN)) return;
        BlockState black = Blocks.BLACK_CONCRETE.defaultBlockState(), floorA = Blocks.POLISHED_BLACKSTONE.defaultBlockState(), floorB = Blocks.BLACK_CONCRETE.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = -HX - 1; x <= HX + 1; x++) {
            for (int z = -HZ - 1; z <= HZ + 1; z++) {
                boolean wall = Math.abs(x) == HX + 1 || Math.abs(z) == HZ + 1;
                for (int y = 0; y <= H + 1; y++) {
                    BlockState s;
                    if (y == 0) s = ((x + z) & 1) == 0 ? floorA : floorB;
                    else if (y == H + 1) s = black;
                    else s = wall ? black : air;
                    set(l, x, y, z, s);
                }
            }
        }
        for (int x = -HX; x <= HX; x += 6) for (int z = -HZ + 2; z <= HZ - 2; z += 6) set(l, x, H, z, Blocks.SHROOMLIGHT.defaultBlockState());
        // red guide strips along the floor edges and under the monitors
        for (int x = -HX; x <= HX; x++) { set(l, x, 0, -HZ, Blocks.RED_NETHER_BRICKS.defaultBlockState()); set(l, x, 0, HZ, Blocks.RED_NETHER_BRICKS.defaultBlockState()); }
        for (int z = -HZ; z <= HZ; z++) { set(l, -HX, 0, z, Blocks.RED_NETHER_BRICKS.defaultBlockState()); set(l, HX, 0, z, Blocks.RED_NETHER_BRICKS.defaultBlockState()); }
        // monitor wall (north): frames of gray concrete, the screens are drawn by the client
        for (int i = 0; i < 5; i++) {
            int mx = -12 + i * 6;
            for (int x = mx - 3; x <= mx + 3; x++) for (int y = 1; y <= 7; y++) {
                boolean frame = x == mx - 3 || x == mx + 3 || y == 1 || y == 7;
                set(l, x, y, -HZ - 1, frame ? Blocks.GRAY_CONCRETE.defaultBlockState() : Blocks.BLACK_CONCRETE.defaultBlockState());
            }
            set(l, mx, 1, -HZ, Blocks.CRYING_OBSIDIAN.defaultBlockState());
        }
        // consoles: two rows of lecterns on blackstone desks with button strips, and chairs
        for (int row = 0; row < 2; row++) {
            int z = -3 + row * 5;
            for (int x = -9; x <= 9; x += 3) {
                set(l, x, 1, z, row == 0 ? Blocks.LECTERN.defaultBlockState() : Blocks.LECTERN.defaultBlockState());
                set(l, x - 1, 1, z, Blocks.BLACKSTONE_SLAB.defaultBlockState());
                set(l, x + 1, 1, z, Blocks.BLACKSTONE_SLAB.defaultBlockState());
                set(l, x - 1, 2, z, Blocks.CRIMSON_BUTTON.defaultBlockState().setValue(net.minecraft.world.level.block.ButtonBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR));
                set(l, x + 1, 2, z, Blocks.WARPED_BUTTON.defaultBlockState().setValue(net.minecraft.world.level.block.ButtonBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR));
                set(l, x, 1, z + 2, Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
            }
        }
        for (int[] c : new int[][]{{-HX, -HZ}, {HX, -HZ}, {-HX, HZ}, {HX, HZ}}) set(l, c[0], H - 1, c[1], Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
    }

    private static void set(ServerLevel l, int x, int y, int z, BlockState s) {
        l.setBlock(new BlockPos(CX + x, Y + y, CZ + z), s, 2 | 16);
    }

    @SubscribeEvent
    public static void console(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.level().dimension() != FearRealm.DIM) return;
        if (!inside(e.getPos().getX(), e.getPos().getY(), e.getPos().getZ()) || !p.level().getBlockState(e.getPos()).is(Blocks.LECTERN)) return;
        if (!FearSessions.canCast(p) || FearSessions.selfLocked(p)) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        if (e.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
        FearNet.send(p, FearNet.OPEN, 0, 0);
        FearSessions.request(p, true);
    }

    private FearBooth() {}
}
