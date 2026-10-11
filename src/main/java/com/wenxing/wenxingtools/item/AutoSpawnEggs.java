package com.wenxing.wenxingtools.item;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 掠蛋用「生物 → 已注册刷怪蛋」查找（结果缓存）。
 * 与 Forge 1.20.1 规则对齐：只使用物品注册表中已存在的 SpawnEggItem（含神化捕捉等第三方蛋），
 * 无注册蛋则不掉落。不做命名启发式 / 实体组件猜测。
 */
public final class AutoSpawnEggs {
    private AutoSpawnEggs() {}

    private static volatile Map<EntityType<?>, SpawnEggItem> EGG_INDEX;

    /** 解析生物对应已注册 SpawnEggItem；无则 null。 */
    public static SpawnEggItem getSpawnEggFor(EntityType<?> type) {
        if (type == null) {
            return null;
        }
        return eggIndex().get(type);
    }

    private static Map<EntityType<?>, SpawnEggItem> eggIndex() {
        Map<EntityType<?>, SpawnEggItem> idx = EGG_INDEX;
        if (idx != null) {
            return idx;
        }
        synchronized (AutoSpawnEggs.class) {
            if (EGG_INDEX != null) {
                return EGG_INDEX;
            }
            Map<EntityType<?>, SpawnEggItem> map = new HashMap<>();
            for (Item item : BuiltInRegistries.ITEM) {
                if (!(item instanceof SpawnEggItem eggItem)) {
                    continue;
                }
                try {
                    EntityType<?> t = eggItem.getType(ItemStack.EMPTY);
                    if (t != null) {
                        map.putIfAbsent(t, eggItem);
                    }
                } catch (Throwable ignored) {
                }
            }
            EGG_INDEX = Collections.unmodifiableMap(map);
            return EGG_INDEX;
        }
    }
}
