package com.wenxing.wenxingtools.block;

import com.wenxing.wenxingtools.item.ModBlocks;
import com.wenxing.wenxingtools.util.ChunkScope;
import com.wenxing.wenxingtools.util.SpawnSuppressionRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 火把的抑制登记簿记：范围由 BlockState 上的 {@link WenxingTorchBlock#SCOPE} 决定，
 * 本类只负责把「当前 state 的档位」同步到 {@link SpawnSuppressionRegistry}。
 *
 * <p>切档会改 BlockState；注销必须用登记时缓存的 level/center/scope，不能现读 state——
 * 方块被替换时 state 已经是空气，范围会漏减（表现为挖掉火把后该区块永久不刷怪）。
 */
public class WenxingTorchBlockEntity extends BlockEntity {

    private ServerLevel registeredLevel;
    private ChunkPos registeredCenter;
    private ChunkScope registeredScope;

    public WenxingTorchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.WENXING_TORCH_BE.get(), pos, state);
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (level instanceof ServerLevel serverLevel) {
            applyRegistration(serverLevel, currentScope());
        }
    }

    /** 切档后同步登记：同一 level/center/scope 时为空操作，避免引用计数翻倍 */
    public void syncTo(ChunkScope scope) {
        if (level instanceof ServerLevel serverLevel) {
            applyRegistration(serverLevel, scope);
        }
    }

    private void applyRegistration(ServerLevel serverLevel, ChunkScope scope) {
        ChunkPos center = new ChunkPos(getBlockPos());
        if (registeredLevel == serverLevel && center.equals(registeredCenter) && registeredScope == scope) {
            return;
        }
        if (registeredLevel != null && registeredCenter != null && registeredScope != null) {
            SpawnSuppressionRegistry.unregister(registeredLevel, registeredCenter, registeredScope);
        }
        registeredLevel = serverLevel;
        registeredCenter = center;
        registeredScope = scope;
        SpawnSuppressionRegistry.register(serverLevel, center, scope);
        setChanged();
    }

    private ChunkScope currentScope() {
        BlockState state = getBlockState();
        return state.hasProperty(WenxingTorchBlock.SCOPE)
                ? ChunkScope.of(state.getValue(WenxingTorchBlock.SCOPE))
                : ChunkScope.THREE;
    }

    // 区块卸载时 LevelChunk 会先调 onChunkUnloaded 再调 setRemoved，注销只能放这一处，否则计数被双减
    @Override
    public void setRemoved() {
        if (registeredLevel != null && registeredCenter != null && registeredScope != null) {
            SpawnSuppressionRegistry.unregister(registeredLevel, registeredCenter, registeredScope);
            registeredLevel = null;
            registeredCenter = null;
            registeredScope = null;
        }
        super.setRemoved();
    }
}
