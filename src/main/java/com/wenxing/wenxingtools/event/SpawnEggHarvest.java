package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.enchantment.ModEnchantments;
import com.wenxing.wenxingtools.item.ModSpawnEggs;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingDropsEvent;

final class SpawnEggHarvest {

    private SpawnEggHarvest() {
    }


    static void dropSpawnEggHarvestLoot(LivingDropsEvent event, Player player) {
        int level = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.SPAWN_EGG_HARVEST.get(), player.getMainHandItem());
        if (level <= 0) {
            return;
        }
        LivingEntity target = event.getEntity();
        EntityType<?> type = target.getType();
        if (type == EntityType.PLAYER || type == EntityType.ARMOR_STAND) {
            return;
        }

        ItemStack template = ModSpawnEggs.resolveHarvestEggStack(type);
        if (template.isEmpty()) {

            return;
        }

        for (int i = 0; i < level; i++) {
            ItemStack drop = template.copy();
            drop.setCount(1);
            ItemEntity itemEntity = new ItemEntity(
                    target.level(),
                    target.getX(), target.getY(), target.getZ(),
                    drop
            );
            itemEntity.setDefaultPickUpDelay();

            target.level().addFreshEntity(itemEntity);
        }
    }
}
