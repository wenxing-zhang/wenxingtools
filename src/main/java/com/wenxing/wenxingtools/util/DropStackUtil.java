package com.wenxing.wenxingtools.util;

import com.wenxing.wenxingtools.WenXingTools;
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

    /**
     * 摊分条目上限，与 {@link MultiStackItemEntity} 的存储预算同量级。
     * <p>
     * 没有它时，倍率被异常调高（如 NBT 改出高等级附魔）会让 {@code count × multiplier}
     * 在摊分阶段展开成上千万个 ItemStack，聚合体自身的条目预算来不及生效。
     */
    private static final int MAX_TOTAL_STACKS = 4096;

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

    /**
     * 按「每个 stack 各自的额外倍率」合并——倍率随物品属性变化（如可堆叠与否）时用这个入口。
     * 返回的计数为<b>额外</b>数量（不含原版已掉落的那一份），与 {@link #mergeStacksMultiplied} 语义一致。
     */
    public static Map<StackKey, Long> mergeStacksMultipliedBy(Collection<ItemStack> stacks,
                                                              java.util.function.ToLongFunction<ItemStack> multiplierFor) {
        Map<StackKey, Long> totals = new LinkedHashMap<>();
        if (stacks == null || stacks.isEmpty() || multiplierFor == null) {
            return totals;
        }
        for (ItemStack drop : stacks) {
            if (drop == null || drop.isEmpty()) {
                continue;
            }
            long multiplier = multiplierFor.applyAsLong(drop);
            if (multiplier <= 0) {
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
        long discarded = 0L;
        for (Map.Entry<StackKey, Long> entry : totals.entrySet()) {
            long value = entry.getValue();
            if (value <= 0) {
                continue;
            }
            ItemStack key = entry.getKey().stack();
            long remaining = value;
            int maxStack = Math.max(1, key.getMaxStackSize());
            while (remaining > 0) {
                if (stacks.size() >= MAX_TOTAL_STACKS) {
                    discarded += remaining;
                    break;
                }
                int chunk = (int) Math.min(remaining, maxStack);
                stacks.add(copyWithCount(key, chunk));
                remaining -= chunk;
            }
            if (discarded > 0) {
                break;
            }
        }
        if (discarded > 0) {
            WenXingTools.LOGGER.warn(
                    "[resource_amp] drop stack budget exhausted: {} entries kept, {} remaining discarded",
                    stacks.size(), discarded);
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
        List<ItemStack> stacks = totalsToStacks(totals);
        if (stacks.isEmpty()) {
            drops.clear();
            return;
        }
        MultiStackItemEntity merged = MultiStackItemEntity.fromStacks(level, x, y, z, stacks);
        drops.clear();
        drops.add(merged);
    }
}
