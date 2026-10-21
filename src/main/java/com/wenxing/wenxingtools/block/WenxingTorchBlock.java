package com.wenxing.wenxingtools.block;

import com.wenxing.wenxingtools.block.entity.WenxingTorchBlockEntity;
import com.wenxing.wenxingtools.util.ChunkScope;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import com.wenxing.wenxingtools.item.ModItems;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class WenxingTorchBlock extends TorchBlock implements EntityBlock {

    /** 抑制范围档位；默认值即枚举首项，旧存档缺该属性时按 3×3 解析 */
    public static final EnumProperty<ChunkScope> SCOPE = EnumProperty.create("scope", ChunkScope.class);

    public WenxingTorchBlock(Properties properties, ParticleOptions particle) {
        super(properties, particle);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SCOPE);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WenxingTorchBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        return cycleScope(state, level, pos, player, hand);
    }

    /** 站立与墙面火把共用：木棍右击循环 3×3 → 9×9 → 15×15 区块 */
    static InteractionResult cycleScope(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand) {
        if (!player.getItemInHand(hand).is(ModItems.WENXING_PICKAXE.get())) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel) || !state.hasProperty(SCOPE)) {
            return InteractionResult.PASS;
        }
        ChunkScope next = state.getValue(SCOPE).next();
        serverLevel.setBlock(pos, state.setValue(SCOPE, next), Block.UPDATE_ALL);
        if (serverLevel.getBlockEntity(pos) instanceof WenxingTorchBlockEntity torch) {
            torch.syncTo(next);
        }
        player.displayClientMessage(Component.literal("禁止范围" + next.label() + "区块"), false);
        return InteractionResult.CONSUME;
    }
}
