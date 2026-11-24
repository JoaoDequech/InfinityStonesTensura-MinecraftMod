package com.infinitystones.tensura.mixin.port;

import com.infinitystones.tensura.skill.ability.ported.Intangibility;
import com.infinitystones.tensura.skill.ability.ported.WaterWalking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mind Stone intangibility: same rule as the original Palladium mixin (walls always, floors only when "vertical").
 * Time Stone water walking: water and waterlogged blocks get a solid top for the walker (original Palladium mixin).
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin {

    @Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At("RETURN"), cancellable = true)
    private void infinity$intangible(BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (!(context instanceof EntityCollisionContext ctx)) return;
        Entity entity = ctx.getEntity();
        // both rules only ever apply to players; this is the hottest collision path, so mobs and items leave at once
        if (!(entity instanceof net.minecraft.world.entity.player.Player)) return;
        BlockState self = (BlockState) (Object) this;
        FluidState fluid = self.getFluidState();
        if (!fluid.isEmpty() && fluid.is(FluidTags.WATER) && WaterWalking.canWalk(entity)) {
            if (self.getBlock() instanceof LiquidBlockContainer) {
                cir.setReturnValue(Shapes.or(cir.getReturnValue(), Block.box(0, 0, 0, 16, 15, 16)));
            } else if (self.getBlock() instanceof LiquidBlock) {
                cir.setReturnValue(Block.box(0, 0, 0, 16, Math.min(16, (int) (fluid.getHeight(level, pos) * 15) + 2), 16));
            }
            return;
        }
        VoxelShape shape = cir.getReturnValue();
        if (shape.isEmpty() || !Intangibility.isIntangible(entity)) return;
        if (!Intangibility.canPass(entity, (BlockState) (Object) this, level, pos)) return;
        boolean above = Intangibility.isAbove(entity, shape.max(Direction.Axis.Y), pos);
        if (!above || Intangibility.vertical(entity)) cir.setReturnValue(Shapes.empty());
    }

    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void infinity$phasing(Level level, BlockPos pos, Entity entity, CallbackInfo ci) {
        if (Intangibility.isIntangible(entity) && Intangibility.canPass(entity, (BlockState) (Object) this, level, pos)) ci.cancel();
    }
}
