package com.wenxing.wenxingtools.block.entity;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.block.ModBlocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, WenXingTools.MODID);

    public static final RegistryObject<BlockEntityType<CustomSpawnerBlockEntity>> CUSTOM_SPAWNER =
            BLOCK_ENTITIES.register("custom_spawner",
                    () -> BlockEntityType.Builder.of(CustomSpawnerBlockEntity::new, ModBlocks.CUSTOM_SPAWNER.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
