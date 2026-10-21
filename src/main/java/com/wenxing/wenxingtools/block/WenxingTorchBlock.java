package com.wenxing.wenxingtools.block;

import com.mojang.serialization.MapCodec;
import com.wenxing.wenxingtools.util.ChunkScope;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public class WenxingTorchBlock extends TorchBlock implements EntityBlock {

    public static final MapCodec<WenxingTorchBlock> CODEC = simpleCodec(WenxingTorchBlock::new);

    /**
     * 抑制范围档位，存区块数（3 / 9 / 15）；默认 3 即枚举首项，旧存档缺该属性时按 3×3 解析。
     * 取值域由 {@link ScopeProperty} 收紧到恰好三档——见其类注释。
     */
    public static final Property<Integer> SCOPE = ScopeProperty.SCOPE;

    // 1.21 的 TorchBlock 构造只接受 SimpleParticleType，紫色粒子只能由 animateTick 自绘
    static final DustParticleOptions PURPLE_FLAME =
            new DustParticleOptions(new Vector3f(0.659f, 0.333f, 0.969f), 1.0f);

    public WenxingTorchBlock(Properties properties) {
        super(ParticleTypes.FLAME, properties);
    }

    @Override
    public MapCodec<? extends TorchBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SCOPE);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        emitTorchParticles(level, pos);
    }

    /** 站立/墙面火把共用的粒子自绘（外观对齐 Forge 1.20.1）。 */
    static void emitTorchParticles(Level level, BlockPos pos) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.7;
        double z = pos.getZ() + 0.5;
        level.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0, 0.0, 0.0);
        level.addParticle(PURPLE_FLAME, x, y, z, 0.0, 0.0, 0.0);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        return cycleScope(stack, state, level, pos, player);
    }

    /** 站立与墙面火把共用：木棍右击循环 3×3 → 9×9 → 15×15 区块 */
    static ItemInteractionResult cycleScope(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                            Player player) {
        if (!stack.is(Items.STICK)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel) || !state.hasProperty(SCOPE)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        ChunkScope next = ChunkScope.of(state.getValue(SCOPE)).next();
        serverLevel.setBlock(pos, state.setValue(SCOPE, next.chunks()), Block.UPDATE_ALL);
        if (serverLevel.getBlockEntity(pos) instanceof WenxingTorchBlockEntity torch) {
            torch.syncTo(next);
        }
        player.displayClientMessage(Component.literal("禁止范围" + next.label() + "区块"), false);
        return ItemInteractionResult.CONSUME;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WenxingTorchBlockEntity(pos, state);
    }
}
