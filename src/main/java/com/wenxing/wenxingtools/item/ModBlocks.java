package com.wenxing.wenxingtools.item;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.block.CustomSpawnerBlock;
import com.wenxing.wenxingtools.block.CustomSpawnerBlockEntity;
import com.wenxing.wenxingtools.block.WenxingAnvilBlock;
import com.wenxing.wenxingtools.block.WenxingPlatformBlock;
import com.wenxing.wenxingtools.block.WenxingTorchBlock;
import com.wenxing.wenxingtools.block.WenxingTorchBlockEntity;
import com.wenxing.wenxingtools.block.WenxingWallTorchBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    private ModBlocks() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(WenXingTools.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, WenXingTools.MODID);

    public static final DeferredBlock<CustomSpawnerBlock> CUSTOM_SPAWNER = BLOCKS.register(
            "custom_spawner",
            () -> new CustomSpawnerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.5F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CustomSpawnerBlockEntity>> CUSTOM_SPAWNER_BE =
            BLOCK_ENTITIES.register("custom_spawner",
                    () -> BlockEntityType.Builder.of(CustomSpawnerBlockEntity::new, CUSTOM_SPAWNER.get()).build(null));

    private static BlockBehaviour.Properties torchProperties() {
        return BlockBehaviour.Properties.of()
                .noCollission()
                .instabreak()
                .lightLevel(state -> 14)
                .sound(SoundType.WOOD)
                .pushReaction(PushReaction.DESTROY);
    }

    public static final DeferredBlock<WenxingTorchBlock> WENXING_TORCH =
            BLOCKS.register("wenxing_torch", () -> new WenxingTorchBlock(torchProperties()));

    public static final DeferredBlock<WenxingWallTorchBlock> WENXING_WALL_TORCH =
            BLOCKS.register("wenxing_wall_torch", () -> new WenxingWallTorchBlock(torchProperties()));

    /** wenxing平台：外观沿用平滑石（属性整体复制原版方块，硬度/音效/工具与平滑石一致） */
    public static final DeferredBlock<WenxingPlatformBlock> WENXING_PLATFORM =
            BLOCKS.register("wenxing_platform",
                    () -> new WenxingPlatformBlock(BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.SMOOTH_STONE)));

    /** wenxing铁砧：属性整体复制原版铁砧（硬度 5、抗爆 1200、需正确工具、铁砧音效） */
    public static final DeferredBlock<WenxingAnvilBlock> WENXING_ANVIL =
            BLOCKS.register("wenxing_anvil",
                    () -> new WenxingAnvilBlock(BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.ANVIL)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WenxingTorchBlockEntity>> WENXING_TORCH_BE =
            BLOCK_ENTITIES.register("wenxing_torch",
                    () -> BlockEntityType.Builder.of(WenxingTorchBlockEntity::new,
                            WENXING_TORCH.get(), WENXING_WALL_TORCH.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        BLOCK_ENTITIES.register(eventBus);
    }
}
