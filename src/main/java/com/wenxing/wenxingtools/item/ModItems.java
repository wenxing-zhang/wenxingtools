package com.wenxing.wenxingtools.item;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.block.ModBlocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, WenXingTools.MODID);

    public static final RegistryObject<Item> RESOURCE_AMPLIFICATION_PICKAXE =
            ITEMS.register("resource_amplification_pickaxe", ResourceAmplificationPickaxeItem::new);

    public static final RegistryObject<Item> LIFE_AMPLIFICATION_CHESTPLATE =
            ITEMS.register("life_amplification_chestplate", LifeAmplificationChestplateItem::new);

    public static final RegistryObject<Item> CUSTOM_SPAWNER =
            ITEMS.register("custom_spawner",
                    () -> new BlockItem(ModBlocks.CUSTOM_SPAWNER.get(), new Item.Properties()));


    public static final RegistryObject<Item> GENERIC_SPAWN_EGG =
            ITEMS.register("generic_spawn_egg",
                    () -> new GenericSpawnEggItem(new Item.Properties().stacksTo(64)));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
