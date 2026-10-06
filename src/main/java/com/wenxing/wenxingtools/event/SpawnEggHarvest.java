package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.enchantment.EnchantUtil;
import com.wenxing.wenxingtools.enchantment.ModEnchantments;
import com.wenxing.wenxingtools.item.AutoSpawnEggs;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/**
 * 掠蛋附魔结算（规则对齐 Forge 1.20.1 {@code SpawnEggHarvest}）：
 * 主手附魔等级 = 掉落刷怪蛋数量（1–5）；跳过玩家与盔甲架；
 * 只掉落已注册 SpawnEggItem；无注册蛋则不掉；不受资源增幅/抢夺影响。
 * 直接入世界（掉落事件被取消时仍能出蛋）。
 * 无事件订阅：勿加 @EventBusSubscriber。
 */
public final class SpawnEggHarvest {
    private SpawnEggHarvest() {}

    static void dropSpawnEggHarvestLoot(LivingDropsEvent event, Player player) {
        int level = EnchantUtil.getLevel(ModEnchantments.SPAWN_EGG_HARVEST, player.getMainHandItem(), player);
        if (level <= 0) {
            return;
        }
        LivingEntity target = event.getEntity();
        if (target == null) {
            return;
        }
        EntityType<?> type = target.getType();
        if (type == EntityType.PLAYER || type == EntityType.ARMOR_STAND || target instanceof ArmorStand) {
            return;
        }

        SpawnEggItem egg = AutoSpawnEggs.getSpawnEggFor(type);
        if (egg == null) {
            return;
        }

        int count = Math.min(level, 5);
        for (int i = 0; i < count; i++) {
            ItemStack drop = new ItemStack(egg, 1);
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
