package com.wenxing.wenxingtools.mixin;

import com.wenxing.wenxingtools.item.ResourceAmplificationPickaxeItem;
import com.wenxing.wenxingtools.util.MixinSelfCheck;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 主手资源增幅镐：可挖方块固定约 0.25 秒（5 tick）破坏，与硬度、效率附魔无关。
 * 注入声明类 BlockBehaviour.BlockStateBase（getDestroyProgress 在父类，不在 BlockState）。
 * 硬度 &lt; 0 的不可破坏方块返回 0，不推进破坏。
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateDestroyProgressMixin {

    @Inject(
            method = "getDestroyProgress(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void wenxingtools$fixedQuarterSecondMining(
            Player player,
            BlockGetter level,
            BlockPos pos,
            CallbackInfoReturnable<Float> cir) {
        if (!MixinSelfCheck.isFixedMiningEnabled()) {
            return;
        }
        if (player == null || !(player.getMainHandItem().getItem() instanceof ResourceAmplificationPickaxeItem)) {
            return;
        }
        if (!((Object) this instanceof BlockState state)) {
            return;
        }
        if (!ResourceAmplificationPickaxeItem.canHarvest(state)) {
            return;
        }
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0.0F) {
            cir.setReturnValue(0.0F);
            return;
        }
        cir.setReturnValue(1.0F / ResourceAmplificationPickaxeItem.FIXED_MINE_TICKS);
    }
}
