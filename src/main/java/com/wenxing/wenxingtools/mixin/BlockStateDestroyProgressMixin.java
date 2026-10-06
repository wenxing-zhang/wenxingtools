package com.wenxing.wenxingtools.mixin;

import com.wenxing.wenxingtools.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateDestroyProgressMixin {

    @Inject(method = "getDestroyProgress", at = @At("RETURN"), cancellable = true, require = 1)
    private void wenxingtools$fixedQuarterSecondBreak(Player player, BlockGetter level, BlockPos pos,
                                                       CallbackInfoReturnable<Float> cir) {
        if (player == null || level == null || pos == null
                || !player.getMainHandItem().is(ModItems.RESOURCE_AMPLIFICATION_PICKAXE.get())) {
            return;
        }
        BlockState state = level.getBlockState(pos);

        if (state.is(Blocks.BEDROCK)) {
            return;
        }
        float progress = cir.getReturnValue();
        if (Float.isNaN(progress)) {
            return;
        }

        cir.setReturnValue(1.0F / 5.0F);
    }
}
