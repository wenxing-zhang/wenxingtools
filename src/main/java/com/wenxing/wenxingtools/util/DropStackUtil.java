package com.wenxing.wenxingtools.util;

import com.wenxing.wenxingtools.entity.MultiStackItemEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 掉落物「按物品+NBT」合并，增幅落地时合成单个 MultiStackItemEntity。
 * 工具类：无事件监听，勿加 @Mod.EventBusSubscriber。
 */
public final class DropStackUtil {
    private DropStackUtil() {}

    /** 以「物品 + NBT」为键；计数不参与相等性 */
    public record StackKey(ItemStack stack) {
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof StackKey other)) {
                return false;
            }
            if (!ItemStack.isSameItem(this.stack, other.stack)) {
                return false;
            }
            var a = this.stack.getTag();
            var b = other.stack.getTag();
            return a == null ? b == null : a.equals(b);
        }

        @Override
        public int hashCode() {
            int h = this.stack.getItem().hashCode();
            if (this.stack.hasTag()) {
                h = 31 * h + this.stack.getTag().hashCode();
            }
            return h;
        }
    }

    public static ItemStack copyWithCount(ItemStack source, int count) {
        ItemStack copy = source.copy();
        copy.setCount(count);
        return copy;
    }

    public static Map<StackKey, Long> mergeStacksMultiplied(Collection<ItemStack> stacks, long multiplier) {
        Map<StackKey, Long> totals = new LinkedHashMap<>();
        if (stacks == null || stacks.isEmpty() || multiplier <= 0) {
            return totals;
        }
        for (ItemStack drop : stacks) {
            if (drop == null || drop.isEmpty()) {
                continue;
            }
            long extra = (long) drop.getCount() * multiplier;
            if (extra > 0) {
                ItemStack keyStack = drop.copy();
                keyStack.setCount(1);
                totals.merge(new StackKey(keyStack), extra, Long::sum);
            }
        }
        return totals;
    }

    public static List<ItemStack> totalsToStacks(Map<StackKey, Long> totals) {
        List<ItemStack> stacks = new ArrayList<>();
        if (totals == null || totals.isEmpty()) {
            return stacks;
        }
        for (Map.Entry<StackKey, Long> entry : totals.entrySet()) {
            long value = entry.getValue();
            if (value <= 0) {
                continue;
            }
            ItemStack key = entry.getKey().stack();
            long remaining = value;
            while (remaining > 0) {
                int maxStack = Math.max(1, key.getMaxStackSize());
                int chunk = (int) Math.min(remaining, maxStack);
                stacks.add(copyWithCount(key, chunk));
                remaining -= chunk;
            }
        }
        return stacks;
    }

    /** 增幅掉落合成单实体落地 */
    public static void popTotals(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, Map<StackKey, Long> totals) {
        if (level == null || level.isClientSide || totals == null || totals.isEmpty()) {
            return;
        }
        List<ItemStack> stacks = totalsToStacks(totals);
        if (stacks.isEmpty()) {
            return;
        }
        MultiStackItemEntity entity = MultiStackItemEntity.fromStacks(
                level,
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5,
                stacks);
        level.addFreshEntity(entity);
    }

    /** 击杀增幅：清空原 drops，写回单个 MultiStackItemEntity */
    public static void replaceWithMerged(
            net.minecraft.world.level.Level level,
            double x,
            double y,
            double z,
            Collection<ItemEntity> drops,
            Map<StackKey, Long> totals) {
        if (drops == null || totals == null) {
            return;
        }
        drops.clear();
        List<ItemStack> stacks = totalsToStacks(totals);
        if (stacks.isEmpty()) {
            return;
        }
        drops.add(MultiStackItemEntity.fromStacks(level, x, y, z, stacks));
    }
}
