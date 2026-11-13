package com.wenxing.wenxingtools.block;

import com.mojang.serialization.MapCodec;
import com.wenxing.wenxingtools.menu.WenxingAnvilMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

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

    private static final VoxelShape BASE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 4.0D, 14.0D);
    private static final VoxelShape X_LEG1 = Block.box(3.0D, 4.0D, 4.0D, 13.0D, 5.0D, 12.0D);
    private static final VoxelShape X_LEG2 = Block.box(4.0D, 5.0D, 6.0D, 12.0D, 10.0D, 10.0D);
    private static final VoxelShape X_TOP = Block.box(0.0D, 10.0D, 3.0D, 16.0D, 16.0D, 13.0D);
    private static final VoxelShape Z_LEG1 = Block.box(4.0D, 4.0D, 3.0D, 12.0D, 5.0D, 13.0D);
    private static final VoxelShape Z_LEG2 = Block.box(6.0D, 5.0D, 4.0D, 10.0D, 10.0D, 12.0D);
    private static final VoxelShape Z_TOP = Block.box(3.0D, 10.0D, 0.0D, 13.0D, 16.0D, 16.0D);
    private static final VoxelShape X_AXIS_AABB = Shapes.or(BASE, X_LEG1, X_LEG2, X_TOP);
    private static final VoxelShape Z_AXIS_AABB = Shapes.or(BASE, Z_LEG1, Z_LEG2, Z_TOP);

    /** 1.21 起方块形状/属性由 Codec 参与序列化，抽象方法必须给出实现（本方块只用默认 Properties）。 */
    public static final MapCodec<WenxingAnvilBlock> CODEC = simpleCodec(WenxingAnvilBlock::new);

    public WenxingAnvilBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getClockWise());
    }

    // 遮挡形状必须跟随铁砧轮廓：默认满方块形状会让正下方方块的顶面被判定为完全遮挡而剔除，
    // 底座四周 2px 空隙失去几何填充，看起来就是下方方块顶面变透明。
    // getOcclusionShape 默认也走 state.getShape(...)，覆盖本方法即同时修好遮挡与碰撞箱。
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.X ? X_AXIS_AABB : Z_AXIS_AABB;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
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
        serverPlayer.openMenu(provider, data -> data.writeBlockPos(pos));
        return InteractionResult.CONSUME;
    }
}
