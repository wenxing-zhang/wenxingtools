package com.wenxing.wenxingtools.mixin;

import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Predicate;

/**
 * 暴露 {@link LootPool} 的包级私有 {@code entries} 与私有 {@code compositeCondition}，
 * 供资源增幅「全掉落」遍历使用。
 * <p>
 * entry 自身的条件（{@code canRun}）由 {@code expand} 内部尊重，不需要在这里取 conditions。
 */
@Mixin(LootPool.class)
public interface LootPoolAccessor {

    @Accessor("entries")
    LootPoolEntryContainer[] wenxingtools$getEntries();

    @Accessor("compositeCondition")
    Predicate<LootContext> wenxingtools$getCompositeCondition();
}
