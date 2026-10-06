package com.wenxing.wenxingtools.integration;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.entity.MultiStackItemEntity;
import com.wenxing.wenxingtools.util.DropStackUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Malum 精魂识别 / 收拢工具。
 * <p>
 * 死亡掉落合并入口在 {@code ResourceAmplificationHandler}：
 * 精魂与战利品合成同一个 {@link MultiStackItemEntity}。
 * 本类负责：世界内 SpiritItemEntity 收拢、CachedSpiritDrops 延迟缓存抽取、
 * 迟到精魂并入附近 MultiStack。
 */
public final class MalumSpiritAmplification {

    /** 精魂资源增幅固定倍率（与掉落物的 (1+level×100) 不同） */
    public static final int SPIRIT_MULTIPLIER = 10;

    private static final String SPIRIT_ITEM_ENTITY = "com.sammy.malum.common.entity.spirit.SpiritItemEntity";
    private static final String SPIRIT_ITEM_ENTITY_ALT = "com.sammy.malum.common.entity.SpiritItemEntity";
    private static final String FLOATING_ITEM_ENTITY = "com.sammy.malum.common.entity.FloatingItemEntity";
    private static final String CACHED_TYPE_CLASS = "com.sammy.malum.registry.common.MalumAttachmentTypes";
    private static final String CACHED_FIELD = "CACHED_SPIRIT_DROPS";

    private static Method getItemMethod;
    private static Method getDataMethod;
    private static Method setDataMethod;

    private MalumSpiritAmplification() {
    }

    public static void register() {
        // 迟到精魂（CachedSpiritDrops 延迟生成 / 其它补发路径）并入附近 MultiStack
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                MalumSpiritAmplification::onEntityJoin);
        WenXingTools.LOGGER.debug("[resource_amp] Malum spirit helpers registered (amp x{})", SPIRIT_MULTIPLIER);
    }

    /**
     * 收拢死亡点附近精魂 + 本体 CachedSpiritDrops，并入 totals（不单独落地实体）。
     *
     * @param multiplier 无增幅 1，有增幅 {@link #SPIRIT_MULTIPLIER}
     */
    public static void contributeNearbySpirits(
            LivingEntity dead,
            int multiplier,
            java.util.Map<DropStackUtil.StackKey, Long> totals) {
        if (dead == null || totals == null || !(dead.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        int mul = Math.max(1, multiplier);

        for (ItemStack stack : drainCachedSpiritDrops(dead)) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            ItemStack key = stack.copy();
            key.setCount(1);
            totals.merge(new DropStackUtil.StackKey(key), (long) stack.getCount() * mul, Long::sum);
        }

        AABB box = dead.getBoundingBox().inflate(3.0D);
        try {
            collectWorldSpirits(serverLevel, box, mul, totals);
        } catch (Throwable t) {
            WenXingTools.LOGGER.warn("[resource_amp] Malum spirit merge failed: {}", t.toString(), t);
        }
    }

    private static void collectWorldSpirits(
            ServerLevel level,
            AABB box,
            int multiplier,
            java.util.Map<DropStackUtil.StackKey, Long> totals) {
        List<Entity> nearby = level.getEntities((Entity) null, box, MalumSpiritAmplification::isSpiritDropEntity);
        if (nearby.isEmpty()) {
            return;
        }
        for (Entity entity : nearby) {
            ItemStack stack = getEntityItem(entity);
            if (stack == null || stack.isEmpty()) {
                // 读不到 / 空栈：跳过且不 discard，避免吞精魂
                continue;
            }
            ItemStack key = stack.copy();
            key.setCount(1);
            totals.merge(new DropStackUtil.StackKey(key), (long) stack.getCount() * multiplier, Long::sum);
            discardQuietly(entity);
        }
    }

    /** 迟到入世的精魂并入 3 格内 MultiStack；没有则新建单实体。 */
    private static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.getEntity() == null || event.getEntity().isRemoved()) {
            return;
        }
        Entity entity = event.getEntity();
        if (entity instanceof MultiStackItemEntity) {
            return;
        }
        if (!isSpiritDropEntity(entity)) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        ItemStack stack = getEntityItem(entity);
        if (stack == null || stack.isEmpty()) {
            return;
        }

        try {
            // 延到下一 tick，避免 EntityJoinLevelEvent 内 addFreshEntity 重入。
            // 执行时重读当前栈：入世快照在部分拾取/改栈后会复制。
            serverLevel.getServer().execute(() -> {
                if (entity.isRemoved()) {
                    return;
                }
                ItemStack current = getEntityItem(entity);
                if (current == null || current.isEmpty()) {
                    return;
                }
                ItemStack toMerge = current.copy();
                try {
                    AABB box = entity.getBoundingBox().inflate(3.0D);
                    List<MultiStackItemEntity> multiStacks =
                            serverLevel.getEntitiesOfClass(MultiStackItemEntity.class, box);
                    if (!multiStacks.isEmpty()) {
                        multiStacks.get(0).mergeStack(toMerge);
                        discardQuietly(entity);
                        return;
                    }
                    MultiStackItemEntity merged = MultiStackItemEntity.fromStacks(
                            serverLevel, entity.getX(), entity.getY(), entity.getZ(), List.of(toMerge));
                    merged.setDefaultPickUpDelay();
                    serverLevel.addFreshEntity(merged);
                    discardQuietly(entity);
                } catch (Throwable t) {
                    WenXingTools.LOGGER.warn("[resource_amp] late spirit join merge failed: {}", t.toString(), t);
                }
            });
        } catch (Throwable t) {
            WenXingTools.LOGGER.warn("[resource_amp] late spirit join schedule failed: {}", t.toString(), t);
        }
    }

    /**
     * 抽取并清空死亡实体上的 Malum CachedSpiritDrops（itemAsSoul 延迟生成缓存）。
     * 读失败返回空列表，不抛。
     */
    @SuppressWarnings("unchecked")
    private static List<ItemStack> drainCachedSpiritDrops(LivingEntity dead) {
        try {
            Class<?> types = Class.forName(CACHED_TYPE_CLASS);
            Object supplierObj = types.getField(CACHED_FIELD).get(null);
            if (!(supplierObj instanceof java.util.function.Supplier<?> supplier)) {
                return List.of();
            }
            if (getDataMethod == null) {
                getDataMethod = findAttachmentMethod(dead.getClass(), "getData");
            }
            if (getDataMethod == null) {
                return List.of();
            }
            Object data = getDataMethod.invoke(dead, supplier);
            if (data == null) {
                return List.of();
            }
            Method getSpiritDrops = data.getClass().getMethod("getSpiritDrops");
            Object raw = getSpiritDrops.invoke(data);
            if (!(raw instanceof List<?> list)) {
                return List.of();
            }
            List<ItemStack> result = new java.util.ArrayList<>();
            for (Object o : list) {
                if (o instanceof ItemStack stack && !stack.isEmpty()) {
                    result.add(stack.copy());
                }
            }
            // 清空缓存，避免稍后再次生成
            if (setDataMethod == null) {
                setDataMethod = findAttachmentMethod(dead.getClass(), "setData");
            }
            if (setDataMethod != null) {
                Class<?> dataType = Class.forName("com.sammy.malum.common.data.attachment.CachedSpiritDropsData");
                Object empty = dataType.getConstructor().newInstance();
                setDataMethod.invoke(dead, supplier, empty);
            }
            return result;
        } catch (Throwable t) {
            return List.of();
        }
    }

    private static Method findAttachmentMethod(Class<?> type, String name) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals(name)) {
                    continue;
                }
                if (name.equals("getData") && m.getParameterCount() == 1) {
                    m.setAccessible(true);
                    return m;
                }
                if (name.equals("setData") && m.getParameterCount() == 2) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        return null;
    }

    public static boolean isSpiritDropEntity(Entity entity) {
        if (entity == null || entity.isRemoved()) {
            return false;
        }
        if (entity instanceof MultiStackItemEntity) {
            return false;
        }
        for (Class<?> c = entity.getClass(); c != null; c = c.getSuperclass()) {
            String name = c.getName();
            if (name.equals(SPIRIT_ITEM_ENTITY)
                    || name.equals(SPIRIT_ITEM_ENTITY_ALT)
                    || name.equals(FLOATING_ITEM_ENTITY)) {
                return true;
            }
        }
        // NO_FANCY_SPIRITS 时 Malum 直接掉原版 ItemEntity，按精魂物品识别
        return entity instanceof ItemEntity itemEntity && isSpiritStack(itemEntity.getItem());
    }

    public static boolean isSpiritStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        String name = stack.getItem().getClass().getName();
        return name.startsWith("com.sammy.malum.") && name.contains("Spirit");
    }

    /** 成功返回 stack（可为空）；读取失败返回 null，调用方不得 discard。 */
    public static ItemStack getEntityItem(Entity entity) {
        if (entity instanceof ItemEntity itemEntity) {
            return itemEntity.getItem();
        }
        try {
            if (getItemMethod == null || !getItemMethod.getDeclaringClass().isInstance(entity)) {
                getItemMethod = entity.getClass().getMethod("getItem");
            }
            Object result = getItemMethod.invoke(entity);
            return result instanceof ItemStack stack ? stack : ItemStack.EMPTY;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void discardQuietly(Entity entity) {
        try {
            if (entity instanceof ItemEntity itemEntity) {
                itemEntity.setItem(ItemStack.EMPTY);
            }
            entity.discard();
        } catch (Throwable ignored) {
        }
    }
}
