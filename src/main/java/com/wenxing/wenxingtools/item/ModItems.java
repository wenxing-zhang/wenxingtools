package com.wenxing.wenxingtools.item;

import com.wenxing.wenxingtools.WenXingTools;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    private ModItems() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(WenXingTools.MODID);

    public static final DeferredItem<ResourceAmplificationPickaxeItem> RESOURCE_AMPLIFICATION_PICKAXE =
            ITEMS.register("resource_amplification_pickaxe", ResourceAmplificationPickaxeItem::new);

    public static final DeferredItem<BlockItem> CUSTOM_SPAWNER =
            ITEMS.register("custom_spawner", () -> new BlockItem(ModBlocks.CUSTOM_SPAWNER.get(), new Item.Properties()));

    /** 原版无蛋的 Boss/精英：末影龙、凋灵、监守者（掠蛋与刷笼改类型可用） */
    public static final DeferredItem<SpawnEggItem> ENDER_DRAGON_SPAWN_EGG = ITEMS.register(
            "spawn_egg_ender_dragon",
            () -> new SpawnEggItem(EntityType.ENDER_DRAGON, 0x1A1A2E, 0xC8C8FF, new Item.Properties()));

    public static final DeferredItem<SpawnEggItem> WITHER_SPAWN_EGG = ITEMS.register(
            "spawn_egg_wither",
            () -> new SpawnEggItem(EntityType.WITHER, 0x353535, 0x1A1A1A, new Item.Properties()));

    public static final DeferredItem<SpawnEggItem> WARDEN_SPAWN_EGG = ITEMS.register(
            "spawn_egg_warden",
            () -> new SpawnEggItem(EntityType.WARDEN, 0x0F464C, 0x032121, new Item.Properties()));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }

    @EventBusSubscriber(modid = WenXingTools.MODID)
    public static class CreativeTabEvents {
        /** 刷怪笼/镐/附魔书已移入专属创造栏「文星工具」；此处只保留专属刷怪蛋 */
        @SubscribeEvent
        public static void onBuildTabs(BuildCreativeModeTabContentsEvent event) {
            if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
                event.accept(ENDER_DRAGON_SPAWN_EGG);
                event.accept(WITHER_SPAWN_EGG);
                event.accept(WARDEN_SPAWN_EGG);
            }
        }
    }
}
