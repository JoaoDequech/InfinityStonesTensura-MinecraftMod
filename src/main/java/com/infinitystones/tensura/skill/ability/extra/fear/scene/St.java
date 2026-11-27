package com.infinitystones.tensura.skill.ability.extra.fear.scene;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;

/** Block state builders for the oriented / parameterised blocks the scenes use. */
public final class St {
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static BlockState st(Block b, Object... kv) {
        BlockState s = b.defaultBlockState();
        for (int i = 0; i < kv.length; i += 2) s = s.setValue((Property) kv[i], (Comparable) kv[i + 1]);
        return s;
    }

    public static BlockState stair(Block b, Direction facing, boolean top) {
        return st(b, BlockStateProperties.HORIZONTAL_FACING, facing, BlockStateProperties.HALF, top ? Half.TOP : Half.BOTTOM);
    }

    public static BlockState slab(Block b, boolean top) {
        return st(b, BlockStateProperties.SLAB_TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
    }

    public static BlockState trap(Block b, Direction facing, boolean top, boolean open) {
        return st(b, BlockStateProperties.HORIZONTAL_FACING, facing, BlockStateProperties.HALF, top ? Half.TOP : Half.BOTTOM, BlockStateProperties.OPEN, open);
    }

    public static BlockState axis(Block b, Direction.Axis axis) {
        return st(b, BlockStateProperties.AXIS, axis);
    }

    public static BlockState leaf(Block b) {
        return st(b, BlockStateProperties.PERSISTENT, true, BlockStateProperties.DISTANCE, 1);
    }

    public static BlockState candle(Block b, int n, boolean lit) {
        return st(b, BlockStateProperties.CANDLES, Math.max(1, Math.min(4, n)), BlockStateProperties.LIT, lit);
    }

    public static BlockState lantern(Block b, boolean hanging) {
        return st(b, BlockStateProperties.HANGING, hanging);
    }

    public static BlockState chain(Direction.Axis axis) {
        return st(Blocks.CHAIN, BlockStateProperties.AXIS, axis);
    }

    public static BlockState light(int level) {
        return st(Blocks.LIGHT, BlockStateProperties.LEVEL, Math.max(0, Math.min(15, level)));
    }

    public static BlockState lamp(boolean lit) {
        return st(Blocks.REDSTONE_LAMP, BlockStateProperties.LIT, lit);
    }

    public static BlockState skull(Block b, int rot) {
        return st(b, BlockStateProperties.ROTATION_16, rot & 15);
    }

    public static BlockState wallSkull(Block b, Direction facing) {
        return st(b, BlockStateProperties.HORIZONTAL_FACING, facing);
    }

    public static BlockState facing(Block b, Direction d) {
        return st(b, BlockStateProperties.HORIZONTAL_FACING, d);
    }

    public static BlockState carpetLike(Block b) { return b.defaultBlockState(); }

    public static BlockState doorLower(Block b, Direction facing, boolean open, boolean right) {
        return st(b, BlockStateProperties.HORIZONTAL_FACING, facing, BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER,
                BlockStateProperties.DOOR_HINGE, right ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT, BlockStateProperties.OPEN, open);
    }

    public static BlockState doorUpper(Block b, Direction facing, boolean open, boolean right) {
        return st(b, BlockStateProperties.HORIZONTAL_FACING, facing, BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER,
                BlockStateProperties.DOOR_HINGE, right ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT, BlockStateProperties.OPEN, open);
    }

    public static BlockState bedPart(Block b, Direction facing, boolean head) {
        return st(b, BlockStateProperties.HORIZONTAL_FACING, facing, BlockStateProperties.BED_PART, head ? BedPart.HEAD : BedPart.FOOT);
    }

    public static BlockState layers(int n) {
        return st(Blocks.SNOW, BlockStateProperties.LAYERS, Math.max(1, Math.min(8, n)));
    }

    private St() {}
}
