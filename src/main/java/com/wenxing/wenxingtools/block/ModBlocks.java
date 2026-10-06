package com.wenxing.wenxingtools.block;

import com.wenxing.wenxingtools.WenXingTools;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, WenXingTools.MODID);

    public static final RegistryObject<Block> CUSTOM_SPAWNER = BLOCKS.register("custom_spawner",
            () -> new CustomSpawnerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(5.0F)
                    .sound(SoundType.METAL)

                    .noOcclusion()
                    .isViewBlocking((state, getter, pos) -> false)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> 0)));

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
