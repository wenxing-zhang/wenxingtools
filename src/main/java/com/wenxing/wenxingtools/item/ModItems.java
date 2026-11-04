package com.wenxing.wenxingtools.item;

import com.mojang.serialization.Codec;
import com.wenxing.wenxingtools.WenXingTools;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModItems {
    private ModItems() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(WenXingTools.MODID);

    /** 通用刷怪蛋的绑定组件：实体 id（走 Data Components，1.21 不以 NBT 标签为权威） */
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, WenXingTools.MODID);

    public static final Supplier<DataComponentType<ResourceLocation>> GENERIC_EGG_ENTITY =
            DATA_COMPONENTS.registerComponentType("generic_egg_entity",
                    builder -> builder.persistent(ResourceLocation.CODEC)
                            .networkSynchronized(ResourceLocation.STREAM_CODEC));

    /**
     * 资源增幅附魔开关：跟着物品走，女仆与科技方块（假玩家）手持该物品即可生效。
     * <p>
     * 用 Data Components 而不是 NBT 标签：1.21 的组件是权威载体，且 {@code networkSynchronized} 让开关随
     * 物品同步到客户端——手持粒子不需要额外网络包。缺省（无该组件）即「关闭」。
     */
    public static final Supplier<DataComponentType<Boolean>> RESOURCE_AMP_ENABLED =
            DATA_COMPONENTS.registerComponentType("resource_amp_enabled",
                    builder -> builder.persistent(Codec.BOOL)
                            .networkSynchronized(ByteBufCodecs.BOOL));

    public static final DeferredItem<WenxingPickaxeItem> WENXING_PICKAXE =
            ITEMS.register("wenxing_pickaxe", WenxingPickaxeItem::new);

    public static final DeferredItem<WenxingChestplateItem> WENXING_CHESTPLATE =
            ITEMS.register("wenxing_chestplate", WenxingChestplateItem::new);

    public static final DeferredItem<CustomSpawnerItem> CUSTOM_SPAWNER =
            ITEMS.register("custom_spawner", () -> new CustomSpawnerItem(ModBlocks.CUSTOM_SPAWNER.get(), new Item.Properties()));

    public static final DeferredItem<WenxingTorchItem> WENXING_TORCH =
            ITEMS.register("wenxing_torch",
                    () -> new WenxingTorchItem(ModBlocks.WENXING_TORCH.get(), ModBlocks.WENXING_WALL_TORCH.get(),
                            new Item.Properties(), Direction.DOWN));

    public static final DeferredItem<WenxingPlatformItem> WENXING_PLATFORM =
            ITEMS.register("wenxing_platform",
                    () -> new WenxingPlatformItem(ModBlocks.WENXING_PLATFORM.get(), new Item.Properties()));

    /** 原版无蛋的 Boss/精英：末影龙、凋灵、监守者（掠蛋与刷笼改类型可用）。延迟解析实体类型，避免注册顺序依赖 */
    public static final DeferredItem<SpawnEggItem> ENDER_DRAGON_SPAWN_EGG = ITEMS.register(
            "spawn_egg_ender_dragon",
            () -> new DeferredSpawnEggItem(() -> EntityType.ENDER_DRAGON, 0x1A1A2E, 0xC8C8FF, new Item.Properties()));

    public static final DeferredItem<SpawnEggItem> WITHER_SPAWN_EGG = ITEMS.register(
            "spawn_egg_wither",
            () -> new DeferredSpawnEggItem(() -> EntityType.WITHER, 0x353535, 0x1A1A1A, new Item.Properties()));

    public static final DeferredItem<SpawnEggItem> WARDEN_SPAWN_EGG = ITEMS.register(
            "spawn_egg_warden",
            () -> new DeferredSpawnEggItem(() -> EntityType.WARDEN, 0x0F464C, 0x032121, new Item.Properties()));

    /** 文星生物蛋：绑定任意实体 id，承载「模组未注册刷怪蛋的生物」（掠蛋兜底 + 刷怪笼改类型 + 右键放生） */
    public static final DeferredItem<GenericSpawnEggItem> GENERIC_SPAWN_EGG = ITEMS.register(
            "generic_spawn_egg",
            () -> new GenericSpawnEggItem(new Item.Properties().stacksTo(64)));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
        DATA_COMPONENTS.register(eventBus);
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
