package com.wenxing.wenxingtools.mixin;

import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * 暴露 {@link LootTable} 的私有 {@code pools}，供资源增幅「全掉落」遍历使用。
 * <p>
 * 不用反射：MC 自有成员在生产环境会被 reobf 成 SRG 名，反射字段会在真实服务器上静默失效。
 */
@Mixin(LootTable.class)
public interface LootTableAccessor {

    @Accessor("pools")
    List<LootPool> wenxingtools$getPools();
}
