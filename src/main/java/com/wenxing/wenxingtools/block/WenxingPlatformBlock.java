package com.wenxing.wenxingtools.block;

import com.wenxing.wenxingtools.event.PlatformBuildHandler;
import com.wenxing.wenxingtools.util.PlatformGenerator;
import com.wenxing.wenxingtools.util.PlatformGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * wenxing平台：放下后木棍右击，以玩家所在区块为中心铺 3×3 区块（48×48）双层平滑石平台，
 * 并把 54×54 范围内的平台层与上方一切方块按原版战利品表破坏（有掉落）。
 * 实际改写由 {@link PlatformBuildHandler} 分到多个 tick 完成，本类只做入口校验与去重。
 */
public class WenxingPlatformBlock extends Block {

    public WenxingPlatformBlock(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!player.getItemInHand(hand).is(Items.STICK)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }
        ChunkPos center = player.chunkPosition();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                serverLevel.getChunk(center.x + dx, center.z + dz);
            }
        }

        int anchorY = pos.getY();
        if (!PlatformBuildHandler.submit(serverLevel, center, anchorY, player.getUUID())) {
            player.displayClientMessage(Component.literal("wenxing平台：该区域正在生成中"), true);
            return InteractionResult.CONSUME;
        }

        if (PlatformGenerator.playerEmbedded((int) Math.floor(player.getY()), anchorY)) {
            player.teleportTo(player.getX(), PlatformGeometry.topY(anchorY) + 1, player.getZ());
        }
        player.displayClientMessage(Component.literal("wenxing平台开始生成：48×48×2"), true);
        return InteractionResult.CONSUME;
    }
}
