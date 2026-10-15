package com.wenxing.wenxingtools.entity;

import com.wenxing.wenxingtools.WenXingTools;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    private ModEntities() {}

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, WenXingTools.MODID);

    public static final RegistryObject<EntityType<MultiStackItemEntity>> MULTI_STACK_ITEM =
            ENTITY_TYPES.register("multi_stack_item", () -> EntityType.Builder.<MultiStackItemEntity>of(
                            MultiStackItemEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(8)
                    .updateInterval(20)
                    .build("multi_stack_item"));

    // updateInterval(1)：位移每刻同步，避免高速飞行时的位置迟滞
    public static final RegistryObject<EntityType<FlightSeatEntity>> FLIGHT_SEAT =
            ENTITY_TYPES.register("flight_seat", () -> EntityType.Builder.<FlightSeatEntity>of(
                            FlightSeatEntity::new, MobCategory.MISC)
                    .sized(0.6F, 0.6F)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .fireImmune()
                    .build("flight_seat"));

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}
