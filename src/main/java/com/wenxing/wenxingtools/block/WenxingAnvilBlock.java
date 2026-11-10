package com.wenxing.wenxingtools.block;

import com.wenxing.wenxingtools.menu.WenxingAnvilMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

/**
 * wenxing铁砧：紫色的原版铁砧外观，右击打开自带规则的重命名/附魔界面。
 *
 * <p>不复刻原版铁砧的重力下落与三态损坏：本方块是普通固体方块，没有 BlockEntity，
 * 破损与掉落在产品上属未实现范围（设计文档 §6.7）。
 *
 * <p>界面规则全部落在 {@link WenxingAnvilMenu}，本类只做朝向与开门，
 * 因此原版 {@code AnvilMenu} 上不需要任何 mixin。
 */
public class WenxingAnvilBlock extends HorizontalDirectionalBlock {

    public WenxingAnvilBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getClockWise());
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        MenuProvider provider = new SimpleMenuProvider(
                (containerId, inventory, ignored) -> new WenxingAnvilMenu(
                        containerId, inventory, ContainerLevelAccess.create(serverLevel, pos)),
                Component.translatable("container.wenxingtools.wenxing_anvil"));
        NetworkHooks.openScreen(serverPlayer, provider);
        return InteractionResult.CONSUME;
    }
}
