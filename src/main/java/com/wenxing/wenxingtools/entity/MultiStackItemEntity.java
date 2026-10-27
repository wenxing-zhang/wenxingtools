package com.wenxing.wenxingtools.entity;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.util.EntryBudget;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * 多物品合一掉落实体：一个实体承载多条 ItemStack，数量不截断。
 * getItem() 返回首键同款合计（完整可插入数量），第三方磁铁/管道读 getItem() 可得到增幅后总量；
 * setItem() 将外部改动写回 stacks。
 */
public class MultiStackItemEntity extends ItemEntity {

    private static final String TAG_STACKS = "WTStacks";
    private static final String TAG_COUNT = "WTCount";

    // 条目上限只限「不同种类」的条数：同款同 NBT 合并进一条、计数不受最大堆叠限制，
    // 百万级倍率因此由计数承载，不会展开成百万个 ItemStack；损坏 NBT 也仍是有界解析。
    private static final int MAX_STORED_ENTRIES = 4096;

    private final List<ItemStack> stacks = new ArrayList<>();
    private boolean syncingFromStacks;
    private long cachedFirstKindTotal;

    public MultiStackItemEntity(EntityType<? extends ItemEntity> type, Level level) {
        super(type, level);
    }

    public static MultiStackItemEntity fromStacks(Level level, double x, double y, double z, List<ItemStack> stacksIn) {
        MultiStackItemEntity entity = new MultiStackItemEntity(ModEntities.MULTI_STACK_ITEM.get(), level);
        entity.setPos(x, y, z);
        entity.setStacks(stacksIn);
        entity.setDefaultPickUpDelay();
        return entity;
    }

    public void setStacks(List<ItemStack> stacksIn) {
        stacks.clear();
        if (stacksIn != null) {
            for (ItemStack stack : stacksIn) {
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                splitInto(stacks, stack);
            }
        }
        refreshDisplay();
    }

    public List<ItemStack> getStacks() {
        return stacks;
    }

    /** 将一条外部堆叠并入本实体（按物品+NBT累计，再按最大堆叠拆条）。 */
    public void mergeStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        for (ItemStack existing : stacks) {
            if (isSameKind(existing, stack)) {
                existing.setCount(EntryBudget.merge(existing.getCount(), stack.getCount()));
                refreshDisplay();
                return;
            }
        }
        splitInto(stacks, stack.copy());
        refreshDisplay();
    }

    private static void splitInto(List<ItemStack> out, ItemStack stack) {
        if (!splitInto(out, stack, MAX_STORED_ENTRIES - out.size())) {
            WenXingTools.LOGGER.warn("[multi_stack_item] entry budget exhausted, discarded remaining {}", stack.getItem());
        }
    }

    /**
     * 同款同 NBT 合并进同一条；否则新增一条并把全部计数放进去——不按最大堆叠拆条。
     * 百万级数量因此只占一条 ItemStack，对象数与数量无关。
     */
    private static boolean splitInto(List<ItemStack> out, ItemStack stack, int budget) {
        if (budget <= 0) {
            return false;
        }
        int count = EntryBudget.saturate(stack.getCount());
        for (ItemStack existing : out) {
            if (isSameKind(existing, stack)) {
                existing.setCount(EntryBudget.merge(existing.getCount(), count));
                return true;
            }
        }
        ItemStack piece = stack.copy();
        piece.setCount(count);
        out.add(piece);
        return true;
    }

    private static boolean isSameKind(ItemStack a, ItemStack b) {
        if (a == null || b == null) {
            return false;
        }
        if (!ItemStack.isSameItem(a, b)) {
            return false;
        }
        return Objects.equals(a.getTag(), b.getTag());
    }

    private long totalOfKind(ItemStack kind) {
        long total = 0L;
        for (ItemStack s : stacks) {
            if (isSameKind(s, kind)) {
                total += s.getCount();
            }
        }
        return total;
    }

    /**
     * 对外完整数量：首键同款合计已写入 DATA_ITEM（refreshDisplay）。
     * 第三方（磁铁/管道）读 getItem() 拿到增幅后总量；原地 shrink 由 tick 对账写回。
     */
    @Override
    public ItemStack getItem() {
        return super.getItem();
    }

    @Override
    public void setItem(ItemStack stack) {
        if (syncingFromStacks) {
            super.setItem(stack == null ? ItemStack.EMPTY : stack);
            return;
        }
        if (!this.level().isClientSide) {
            applyExternalItem(stack);
            return;
        }
        super.setItem(stack == null ? ItemStack.EMPTY : stack);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide || this.isRemoved() || syncingFromStacks) {
            return;
        }
        if (stacks.isEmpty()) {
            return;
        }
        ItemStack ext = super.getItem();
        ItemStack kind = stacks.get(0);
        if (ext == null || ext.isEmpty()) {
            applyExternalItem(ItemStack.EMPTY);
            return;
        }
        if (!isSameKind(ext, kind) || ext.getCount() != (int) Math.min(cachedFirstKindTotal, Integer.MAX_VALUE)) {
            applyExternalItem(ext);
        }
    }

    private void applyExternalItem(ItemStack external) {
        if (external == null || external.isEmpty()) {
            if (!stacks.isEmpty()) {
                ItemStack kind = stacks.get(0);
                stacks.removeIf(s -> isSameKind(s, kind));
            }
            refreshDisplay();
            return;
        }
        if (!stacks.isEmpty()) {
            ItemStack firstKind = stacks.get(0);
            stacks.removeIf(s -> isSameKind(s, firstKind));
        }
        mergeStack(external.copy());
    }

    private void refreshDisplayWith(ItemStack external) {
        syncingFromStacks = true;
        try {
            super.setItem(external == null ? ItemStack.EMPTY : external);
        } finally {
            syncingFromStacks = false;
        }
    }

    private void refreshDisplay() {
        if (stacks.isEmpty()) {
            cachedFirstKindTotal = 0L;
            refreshDisplayWith(ItemStack.EMPTY);
            return;
        }
        ItemStack first = stacks.get(0);
        long total = totalOfKind(first);
        cachedFirstKindTotal = total;
        ItemStack display = first.copy();
        display.setCount((int) Math.min(total, Integer.MAX_VALUE));
        refreshDisplayWith(display);
    }

    @Override
    public void playerTouch(Player player) {
        if (this.level().isClientSide || this.hasPickUpDelay() || stacks.isEmpty()) {
            return;
        }

        boolean any = false;
        Iterator<ItemStack> it = stacks.iterator();
        while (it.hasNext()) {
            ItemStack stack = it.next();
            if (stack.isEmpty()) {
                it.remove();
                continue;
            }
            int max = Math.max(1, stack.getMaxStackSize());
            int remaining = stack.getCount();
            int given = 0;
            while (remaining > 0) {
                int chunk = Math.min(remaining, max);
                ItemStack piece = stack.copy();
                piece.setCount(chunk);
                player.getInventory().add(piece);
                int leftover = piece.isEmpty() ? 0 : piece.getCount();
                given += (chunk - leftover);
                if (leftover > 0) {
                    break;
                }
                remaining -= chunk;
            }
            any = any || given > 0;
            int left = stack.getCount() - given;
            if (left <= 0) {
                it.remove();
            } else {
                stack.setCount(left);
            }
        }

        if (!any) {
            return;
        }

        if (stacks.isEmpty()) {
            this.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2F,
                    ((this.random.nextFloat() - this.random.nextFloat()) * 0.7F + 1.0F) * 2.0F);
            player.onItemPickup(this);
            this.discard();
        } else {
            refreshDisplay();
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        ListTag list = new ListTag();
        for (ItemStack stack : stacks) {
            CompoundTag entry = new CompoundTag();
            ItemStack one = stack.copy();
            one.setCount(1);
            entry.put("stack", one.save(new CompoundTag()));
            entry.putInt(TAG_COUNT, stack.getCount());
            list.add(entry);
        }
        tag.put(TAG_STACKS, list);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        syncingFromStacks = true;
        try {
            super.readAdditionalSaveData(tag);
        } finally {
            syncingFromStacks = false;
        }
        stacks.clear();
        ListTag list = tag.getList(TAG_STACKS, Tag.TAG_COMPOUND);
        // 存档条目数不设预截断时，损坏/被构造的 NBT 会让循环长度与逐条 warn 条数随文件大小线性增长
        int limit = Math.min(list.size(), MAX_STORED_ENTRIES);
        int skippedEntries = 0;
        for (int i = 0; i < limit; i++) {
            CompoundTag entry = list.getCompound(i);
            ItemStack stack = ItemStack.of(entry.getCompound("stack"));
            if (!stack.isEmpty()) {
                int count = entry.getInt(TAG_COUNT);
                if (count > 0) {
                    stack.setCount(count);
                }
                if (!splitInto(stacks, stack, MAX_STORED_ENTRIES - stacks.size())) {
                    skippedEntries++;
                }
            }
        }
        int droppedEntries = list.size() - limit;
        if (skippedEntries > 0 || droppedEntries > 0) {
            WenXingTools.LOGGER.warn(
                    "[multi_stack_item] entry budget exhausted: kept={}, overBudgetEntries={}, droppedEntries={}",
                    stacks.size(), skippedEntries, droppedEntries);
        }
        if (stacks.isEmpty()) {
            ItemStack fallback = super.getItem();
            if (!fallback.isEmpty()) {
                splitInto(stacks, fallback.copy());
            }
        }
        refreshDisplay();
    }

    @Override
    public ItemEntity copy() {
        MultiStackItemEntity copied = new MultiStackItemEntity(ModEntities.MULTI_STACK_ITEM.get(), this.level());
        copied.setPos(this.getX(), this.getY(), this.getZ());
        copied.setDeltaMovement(this.getDeltaMovement());
        copied.age = this.age;
        copied.pickupDelay = this.pickupDelay;
        copied.health = this.health;
        copied.lifespan = this.lifespan;
        copied.thrower = this.thrower;
        copied.target = this.target;
        copied.setStacks(this.stacks);
        return copied;
    }
}
