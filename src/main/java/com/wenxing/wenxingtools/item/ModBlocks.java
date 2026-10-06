package com.wenxing.wenxingtools.item;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.block.CustomSpawnerBlock;
import com.wenxing.wenxingtools.block.CustomSpawnerBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
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

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        BLOCK_ENTITIES.register(eventBus);
    }
}
