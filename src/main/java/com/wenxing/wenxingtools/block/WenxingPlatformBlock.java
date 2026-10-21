package com.wenxing.wenxingtools.block;

import com.wenxing.wenxingtools.event.PlatformBuildHandler;
import com.wenxing.wenxingtools.util.ChunkScope;
import com.wenxing.wenxingtools.util.PlatformGenerator;
import com.wenxing.wenxingtools.util.PlatformGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * wenxing平台：手持木棍右击切换生成范围（3×3 / 9×9 / 15×15 区块），蹲下右击以玩家所在区块为中心
 * 按当前范围生成平台；平台正上方固定清除 16 层，破坏按原版战利品表结算（有掉落）。
 * <p>
 * 实际改写由 {@link PlatformBuildHandler} 分到多个 tick 完成，本类只做入口校验与去重。
 */
public class WenxingPlatformBlock extends Block {

    /**
     * 生成范围档位，存区块数（只写入 3 / 9 / 15 三个可达值），默认 3 即枚举首项，
     * 旧存档缺该属性时按 3×3 解析。
     * <p>用整数属性而非枚举属性，是为了让 {@link ChunkScope} 保持纯 JDK（见其类注释）：
     * {@code IntegerProperty} 只能声明连续区间，故取 [3,15] 覆盖三档，非法中间值由
     * {@link ChunkScope#of} 统一回退默认档。
     */
    public static final IntegerProperty SCOPE = IntegerProperty.create("scope", 3, 15);

    public WenxingPlatformBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SCOPE);
    }

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hitResult) {
        if (!stack.is(Items.STICK)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel) || !state.hasProperty(SCOPE)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        ChunkScope scope = ChunkScope.of(state.getValue(SCOPE));
        if (!player.isShiftKeyDown()) {
            ChunkScope next = scope.next();
            serverLevel.setBlock(pos, state.setValue(SCOPE, next.chunks()), Block.UPDATE_ALL);
            player.displayClientMessage(Component.literal("生成范围" + next.label() + "区块"), false);
            return ItemInteractionResult.CONSUME;
        }

        ChunkPos center = player.chunkPosition();
        int anchorY = pos.getY();
        if (!PlatformBuildHandler.submit(serverLevel, center, anchorY, scope, player.getUUID())) {
            player.displayClientMessage(Component.literal("wenxing平台：该区域正在生成中"), false);
            return ItemInteractionResult.CONSUME;
        }

        if (PlatformGenerator.playerEmbedded((int) Math.floor(player.getY()), anchorY)) {
            player.teleportTo(player.getX(), PlatformGeometry.topY(anchorY) + 1, player.getZ());
        }
        player.displayClientMessage(Component.literal("开始生成" + scope.label() + "区块平台"), false);
        return ItemInteractionResult.CONSUME;
    }
}
