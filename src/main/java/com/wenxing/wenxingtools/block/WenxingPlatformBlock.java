package com.wenxing.wenxingtools.block;

import com.wenxing.wenxingtools.event.PlatformBuildHandler;
import com.wenxing.wenxingtools.item.ModItems;
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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;

/**
 * wenxing平台：手持 wenxing 镐右击切换生成范围（3×3 / 9×9 / 15×15 区块），蹲下右击以玩家所在区块为中心
 * 按当前范围生成平台；平台正上方固定清除 16 层，破坏按原版战利品表结算（有掉落）。
 * <p>
 * 实际改写由 {@link PlatformBuildHandler} 分到多个 tick 完成，本类只做入口校验与去重。
 * <p>
 * 潜行右击**不会**进入本类的 {@code useItemOn}：原版在潜行且手持非空物品时跳过方块交互
 * （`ServerPlayerGameMode#useItemOn` 的 `useBlock.isDefault() && !sneakBlocked` 分支），
 * 因此生成入口同时挂在 wenxing 镐的方块交互上，两处共用 {@link #tryBuild}。
 */
public class WenxingPlatformBlock extends Block {

    /**
     * 生成范围档位，存区块数（3 / 9 / 15）；默认 3 即枚举首项，旧存档缺该属性时按 3×3 解析。
     * 取值域由 {@link ScopeProperty} 收紧到恰好三档——见其类注释。
     */
    public static final Property<Integer> SCOPE = ScopeProperty.SCOPE;

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
        if (!stack.is(ModItems.WENXING_PICKAXE.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel) || !state.hasProperty(SCOPE)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (player.isShiftKeyDown()) {
            tryBuild(serverLevel, pos, state, player);
            return ItemInteractionResult.CONSUME;
        }

        ChunkScope next = ChunkScope.of(state.getValue(SCOPE)).next();
        serverLevel.setBlock(pos, state.setValue(SCOPE, next.chunks()), Block.UPDATE_ALL);
        player.displayClientMessage(Component.literal("生成范围" + next.label() + "区块"), false);
        return ItemInteractionResult.CONSUME;
    }

    /**
     * 生成入口：方块侧（非潜行以外的路径）与物品侧（潜行时原版跳过方块交互）共用，避免两处逻辑漂移。
     * 相同「维度 + 中心区块 + 锚点 Y」同时只允许一个任务，连点不会叠加。
     *
     * @return 是否已提交生成任务（false 表示该区域正在生成中，提示已发）
     */
    public static boolean tryBuild(ServerLevel level, BlockPos pos, BlockState state, Player player) {
        if (!state.hasProperty(SCOPE)) {
            return false;
        }
        ChunkScope scope = ChunkScope.of(state.getValue(SCOPE));
        ChunkPos center = player.chunkPosition();
        int anchorY = pos.getY();
        PlatformBuildHandler.SubmitResult result =
                PlatformBuildHandler.submit(level, center, anchorY, scope, player.getUUID());
        if (result != PlatformBuildHandler.SubmitResult.ACCEPTED) {
            player.displayClientMessage(Component.literal(result == PlatformBuildHandler.SubmitResult.TOO_MANY
                    ? "wenxing平台：生成任务过多，请等待当前任务完成"
                    : "wenxing平台：该区域正在生成中"), false);
            return false;
        }

        if (PlatformGenerator.playerEmbedded((int) Math.floor(player.getY()), anchorY)) {
            player.teleportTo(player.getX(), PlatformGeometry.topY(anchorY) + 1, player.getZ());
        }
        player.displayClientMessage(Component.literal("开始生成" + scope.label() + "区块平台"), false);
        return true;
    }
}
