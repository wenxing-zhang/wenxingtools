package com.wenxing.wenxingtools.block;

import com.wenxing.wenxingtools.block.entity.WenxingTorchBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class WenxingTorchBlock extends TorchBlock implements EntityBlock {

    public WenxingTorchBlock(Properties properties) {
        super(properties, ParticleTypes.FLAME);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WenxingTorchBlockEntity(pos, state);
    }
}
