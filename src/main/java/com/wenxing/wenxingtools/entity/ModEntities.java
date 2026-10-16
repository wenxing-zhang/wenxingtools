package com.wenxing.wenxingtools.entity;

import com.wenxing.wenxingtools.WenXingTools;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    private ModEntities() {}

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, WenXingTools.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<MultiStackItemEntity>> MULTI_STACK_ITEM =
            ENTITY_TYPES.register("multi_stack_item", () -> EntityType.Builder.<MultiStackItemEntity>of(
                            MultiStackItemEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(8)
                    .updateInterval(20)
                    .build("multi_stack_item"));

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}
