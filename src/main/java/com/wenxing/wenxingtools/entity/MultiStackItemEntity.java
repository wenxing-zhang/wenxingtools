package com.wenxing.wenxingtools.entity;

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

    private final List<ItemStack> stacks = new ArrayList<>();
    private boolean syncingFromStacks;

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
                long total = (long) existing.getCount() + stack.getCount();
                existing.setCount((int) Math.min(total, Integer.MAX_VALUE));
                int max = Math.max(1, existing.getMaxStackSize());
                if (existing.getCount() > max) {
                    long overflow = existing.getCount() - max;
                    existing.setCount(max);
                    splitInto(stacks, copyWithCountSafe(stack, (int) Math.min(overflow, Integer.MAX_VALUE)));
                }
                refreshDisplay();
                return;
            }
        }
        splitInto(stacks, stack.copy());
        refreshDisplay();
    }

    private static ItemStack copyWithCountSafe(ItemStack source, int count) {
        ItemStack copy = source.copy();
        copy.setCount(count);
        return copy;
    }

    private static void splitInto(List<ItemStack> out, ItemStack stack) {
        int max = Math.max(1, stack.getMaxStackSize());
        int remaining = stack.getCount();
        while (remaining > 0) {
            int chunk = Math.min(remaining, max);
            ItemStack piece = stack.copy();
            piece.setCount(chunk);
            out.add(piece);
            remaining -= chunk;
        }
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
        long total = totalOfKind(kind);
        if (!isSameKind(ext, kind) || ext.getCount() != (int) Math.min(total, Integer.MAX_VALUE)) {
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
            refreshDisplayWith(ItemStack.EMPTY);
            return;
        }
        ItemStack first = stacks.get(0);
        long total = totalOfKind(first);
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
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ItemStack stack = ItemStack.of(entry.getCompound("stack"));
            if (!stack.isEmpty()) {
                int count = entry.getInt(TAG_COUNT);
                if (count > 0) {
                    stack.setCount(count);
                }
                splitInto(stacks, stack);
            }
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
        if (this.hasPickUpDelay()) {
            copied.setPickUpDelay(40);
        }
        copied.setStacks(this.stacks);
        return copied;
    }
}
