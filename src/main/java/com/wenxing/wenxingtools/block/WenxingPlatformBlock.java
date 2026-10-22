package com.wenxing.wenxingtools.block;

import com.wenxing.wenxingtools.event.PlatformBuildHandler;
import com.wenxing.wenxingtools.util.ChunkScope;
import com.wenxing.wenxingtools.util.PlatformGenerator;
import com.wenxing.wenxingtools.util.PlatformGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import com.wenxing.wenxingtools.item.ModItems;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * wenxing平台：手持 wenxing 镐右击切换生成范围（3×3 / 9×9 / 15×15 区块），
 * 蹲下右击以玩家所在区块为中心按当前范围生成平台。
 * 实际改写由 {@link PlatformBuildHandler} 分到多个 tick 完成，本类只做入口校验与去重。
 *
 * <p>潜行右击**不会**进入 {@link #use}：原版在潜行且手持非空物品时跳过方块交互
 * （`ServerPlayerGameMode#useItemOn` 的 flag1 分支），因此生成入口同时挂在
 * {@code WenxingPickaxeItem#useOn} 上，两处共用 {@link #tryBuild}。
 */
public class WenxingPlatformBlock extends Block {

    /** 生成范围档位；默认值即枚举首项，旧存档缺该属性时按 3×3 解析 */
    public static final EnumProperty<ChunkScope> SCOPE = EnumProperty.create("scope", ChunkScope.class);

    public WenxingPlatformBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SCOPE);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!player.getItemInHand(hand).is(ModItems.WENXING_PICKAXE.get())) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) {
            return tryBuild(state, level, pos, player);
        }
        if (!(level instanceof ServerLevel serverLevel) || !state.hasProperty(SCOPE)) {
            return InteractionResult.PASS;
        }
        ChunkScope next = state.getValue(SCOPE).next();
        serverLevel.setBlock(pos, state.setValue(SCOPE, next), Block.UPDATE_ALL);
        player.displayClientMessage(Component.literal("生成范围" + next.label() + "区块"), false);
        return InteractionResult.CONSUME;
    }

    /**
     * 生成入口：方块侧与物品侧共用，避免两处逻辑漂移。
     * 相同「维度 + 中心区块 + 锚点 Y」同时只允许一个任务，连点不会叠加。
     */
    public static InteractionResult tryBuild(BlockState state, Level level, BlockPos pos, Player player) {
        if (!(level instanceof ServerLevel serverLevel) || !state.hasProperty(SCOPE)) {
            return InteractionResult.PASS;
        }
        ChunkScope scope = state.getValue(SCOPE);
        ChunkPos center = player.chunkPosition();
        int anchorY = pos.getY();
        if (!PlatformBuildHandler.submit(serverLevel, center, anchorY, scope, player.getUUID())) {
            player.displayClientMessage(Component.literal("wenxing平台：该区域正在生成中"), false);
            return InteractionResult.CONSUME;
        }
        if (PlatformGenerator.playerEmbedded((int) Math.floor(player.getY()), anchorY)) {
            player.teleportTo(player.getX(), PlatformGeometry.topY(anchorY) + 1, player.getZ());
        }
        player.displayClientMessage(Component.literal("开始生成" + scope.label() + "区块平台"), false);
        return InteractionResult.CONSUME;
    }
}
