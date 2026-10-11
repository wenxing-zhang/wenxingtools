package com.wenxing.wenxingtools.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public class WenxingTorchBlock extends TorchBlock implements EntityBlock {

    public static final MapCodec<WenxingTorchBlock> CODEC = simpleCodec(WenxingTorchBlock::new);

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

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WenxingTorchBlockEntity(pos, state);
    }
}
