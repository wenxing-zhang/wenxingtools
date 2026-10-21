package com.wenxing.wenxingtools.mixin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 基岩与强化深板岩在构造时调了 {@code noLootTable()}，{@code getLootTable()} 返回 EMPTY；
 * 而 {@code getDrops} 第一句就是「EMPTY 直接返回空列表」，因此只放战利品表文件不会被读取。
 * 这里把两者的战利品表路径改回真实文件，让掉落走原版 dropResources 链。
 */
@Mixin(BlockBehaviour.class)
public abstract class BedrockLootTableMixin {

    @Inject(method = "getLootTable", at = @At("HEAD"), cancellable = true, require = 1)
    private void wenxingtools$restoreBedrockLootTable(CallbackInfoReturnable<ResourceLocation> cir) {
        Object self = this;
        if (!(self instanceof Block block)) {
            return;
        }
        if (block == Blocks.BEDROCK) {
            cir.setReturnValue(new ResourceLocation("minecraft", "blocks/bedrock"));
        } else if (block == Blocks.REINFORCED_DEEPSLATE) {
            cir.setReturnValue(new ResourceLocation("minecraft", "blocks/reinforced_deepslate"));
        }
    }
}
