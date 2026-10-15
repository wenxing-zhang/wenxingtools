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
 * 主手资源增幅镐：除基岩外全部方块固定约 0.25 秒（5 tick）破坏，与硬度、效率附魔无关。
 * 注入声明类 BlockBehaviour.BlockStateBase（getDestroyProgress 在父类，不在 BlockState）。
 * 硬度 &lt; 0 的「不可破坏」方块（屏障 / 命令方块等）一并真实破坏——产品裁定有意设计。
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateDestroyProgressMixin implements MixinMarkers.FixedMining {

    @Inject(
            method = "getDestroyProgress(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F",
            at = @At("HEAD"),
            cancellable = true
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
        cir.setReturnValue(1.0F / ResourceAmplificationPickaxeItem.FIXED_MINE_TICKS);
    }
}
