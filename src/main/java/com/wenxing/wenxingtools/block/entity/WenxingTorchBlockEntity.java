package com.wenxing.wenxingtools.block.entity;

import com.wenxing.wenxingtools.util.SpawnSuppressionRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class WenxingTorchBlockEntity extends BlockEntity {

    private ServerLevel registeredLevel;
    private ChunkPos registeredCenter;

    public WenxingTorchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WENXING_TORCH.get(), pos, state);
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (registeredLevel == null && level instanceof ServerLevel serverLevel) {
            registeredLevel = serverLevel;
            registeredCenter = new ChunkPos(getBlockPos());
            SpawnSuppressionRegistry.register(serverLevel, registeredCenter);
        }
    }

    // 区块卸载时 LevelChunk 会先调 onChunkUnloaded 再调 setRemoved，注销只能放这一处，否则计数被双减
    @Override
    public void setRemoved() {
        if (registeredLevel != null) {
            SpawnSuppressionRegistry.unregister(registeredLevel, registeredCenter);
            registeredLevel = null;
            registeredCenter = null;
        }
        super.setRemoved();
    }
}
