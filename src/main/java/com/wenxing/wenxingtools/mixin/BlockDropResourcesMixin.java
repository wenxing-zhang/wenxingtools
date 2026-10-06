package com.wenxing.wenxingtools.mixin;

import com.wenxing.wenxingtools.event.ResourceAmplificationEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 资源增幅挂在 dropResources 7 参重载（Forge 补丁加入的 dropXp 参数版）上。
 * <p>
 * Forge 的 playerDestroy 直接调 7 参版，不会经过 6 参转发；
 * 6 参版只是委托给 7 参，因此只注入 7 参可避免双放大。
 */
@Mixin(Block.class)
public abstract class BlockDropResourcesMixin {

    @Inject(
            method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;Z)V",
            at = @At("TAIL"),
            require = 1,
            remap = false
    )
    private static void wenxingtools$ampOnDropResources7(BlockState state, Level level, BlockPos pos,
                                                         BlockEntity blockEntity, Entity entity, ItemStack stack,
                                                         boolean dropXp, CallbackInfo ci) {
        if (level == null || level.isClientSide || !(entity instanceof net.minecraft.server.level.ServerPlayer player)) {
            return;
        }
        ResourceAmplificationEvents.amplifyBlockDrops(level, pos, state, player, blockEntity, stack);
    }
}
