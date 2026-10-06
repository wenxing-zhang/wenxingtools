package com.wenxing.wenxingtools.item;

import com.wenxing.wenxingtools.WenXingTools;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = WenXingTools.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModSpawnEggs {
    private static final Map<EntityType<?>, SpawnEggItem> EXTRA_EGGS = new HashMap<>();


    private static volatile Map<EntityType<?>, SpawnEggItem> EGG_INDEX;


    private static final String[][] EXTRA_BOSS_EGGS = {
            {"ender_dragon", "1A0A2E", "8B00FF"},
            {"wither", "1A1A1A", "4A4A4A"},
            {"warden", "0A2A2A", "00CFC1"},
    };

    private ModSpawnEggs() {
    }

    @SubscribeEvent
    public static void onRegisterItems(RegisterEvent event) {
        if (!event.getRegistryKey().equals(ForgeRegistries.Keys.ITEMS)) {
            return;
        }
        EGG_INDEX = null;
        for (String[] row : EXTRA_BOSS_EGGS) {
            String path = row[0];
            int primary = Integer.parseInt(row[1], 16);
            int secondary = Integer.parseInt(row[2], 16);
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(
                    new ResourceLocation("minecraft", path));
            if (type == null) {
                continue;
            }
            if (SpawnEggItem.byId(type) != null) {
                continue;
            }
            @SuppressWarnings("unchecked")
            EntityType<? extends Mob> mobType = (EntityType<? extends Mob>) type;
            event.register(ForgeRegistries.Keys.ITEMS,
                    new ResourceLocation(WenXingTools.MODID, "spawn_egg_" + path),
                    () -> {
                        SpawnEggItem item = new SpawnEggItem(mobType, primary, secondary, new Item.Properties());
                        EXTRA_EGGS.put(mobType, item);
                        EGG_INDEX = null;
                        return item;
                    });
        }
    }


    private static Map<EntityType<?>, SpawnEggItem> eggIndex() {
        Map<EntityType<?>, SpawnEggItem> idx = EGG_INDEX;
        if (idx != null) {
            return idx;
        }
        synchronized (ModSpawnEggs.class) {
            if (EGG_INDEX != null) {
                return EGG_INDEX;
            }
            Map<EntityType<?>, SpawnEggItem> map = new HashMap<>();
            for (Item item : ForgeRegistries.ITEMS.getValues()) {
                if (!(item instanceof SpawnEggItem eggItem)) {
                    continue;
                }
                try {
                    EntityType<?> t = eggItem.getType(null);
                    if (t != null) {
                        map.putIfAbsent(t, eggItem);
                    }
                } catch (Throwable ignored) {
                }
            }
            for (Map.Entry<EntityType<?>, SpawnEggItem> e : EXTRA_EGGS.entrySet()) {
                map.putIfAbsent(e.getKey(), e.getValue());
            }
            EGG_INDEX = Collections.unmodifiableMap(map);
            return EGG_INDEX;
        }
    }


    public static SpawnEggItem getSpawnEggFor(EntityType<?> type) {
        if (type == null) {
            return null;
        }
        SpawnEggItem cached = eggIndex().get(type);
        if (cached != null) {
            return cached;
        }
        return SpawnEggItem.byId(type);
    }


    public static ItemStack resolveHarvestEggStack(EntityType<?> type) {
        SpawnEggItem egg = getSpawnEggFor(type);
        if (egg == null) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(egg, 1);
    }

    public static boolean hasSpawnEgg(EntityType<?> type) {
        return getSpawnEggFor(type) != null;
    }
}
