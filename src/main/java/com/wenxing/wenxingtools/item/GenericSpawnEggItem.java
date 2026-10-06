package com.wenxing.wenxingtools.item;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class GenericSpawnEggItem extends Item {

    public static final String ENTITY_TYPE_TAG = "WenXingEntityType";

    public GenericSpawnEggItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(EntityType<?> type, int count) {
        ItemStack stack = new ItemStack(ModItems.GENERIC_SPAWN_EGG.get(), Math.max(1, count));
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
        if (id != null) {
            stack.getOrCreateTag().putString(ENTITY_TYPE_TAG, id.toString());
        }
        return stack;
    }

    @Nullable
    public static EntityType<?> getEntityType(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTag()) {
            return null;
        }
        String raw = stack.getTag().getString(ENTITY_TYPE_TAG);
        if (raw.isEmpty()) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) {
            return null;
        }
        return ForgeRegistries.ENTITY_TYPES.getValue(id);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        EntityType<?> type = getEntityType(stack);
        if (type != null) {
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
            tooltip.add(Component.translatable("item.wenxingtools.generic_spawn_egg.entity",
                    id != null ? id.toString() : type.toString()));
            tooltip.add(Component.translatable("item.wenxingtools.generic_spawn_egg.spawner_hint"));
        } else {
            tooltip.add(Component.translatable("item.wenxingtools.generic_spawn_egg.unbound"));
        }
    }
}
