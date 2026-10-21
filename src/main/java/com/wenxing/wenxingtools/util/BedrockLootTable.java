package com.wenxing.wenxingtools.util;

import com.wenxing.wenxingtools.WenXingTools;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.storage.loot.LootTable;

import java.lang.reflect.Field;

/**
 * 把基岩的战利品表指针从「空表」改回数据包里的 {@code minecraft:blocks/bedrock}。
 *
 * <p>原版基岩声明了 {@code noLootTable()}，它直接把 {@code drops} 置为「空表」标记；
 * 掉落取值处见到空表标记会**直接返回空列表、不查数据包**，所以只手写
 * {@code data/minecraft/loot_table/blocks/bedrock.json} 是无效的——文件永远不会被读取。
 * （强化深板岩没有这个标记，数据包文件照常生效，不需要本类处理。）
 *
 * <p>NeoForge 没有提供设置战利品表的公开入口，{@code getLootTable()} 又是 final，
 * 因此只能改内部字段。失败静默降级为「基岩不掉落」，与不安装本修正时一致，不影响启动。
 */
public final class BedrockLootTable {

    private static final String DROPS_FIELD = "drops";

    private static final ResourceKey<LootTable> BEDROCK_DROPS =
            ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.withDefaultNamespace("blocks/bedrock"));

    private BedrockLootTable() {
    }

    public static void install() {
        try {
            Field drops = BlockBehaviour.class.getDeclaredField(DROPS_FIELD);
            drops.setAccessible(true);
            drops.set(Blocks.BEDROCK, BEDROCK_DROPS);
            // 读回 getter 的实际返回值：只打印「我 set 了什么」证明不了取值链真的改过来了
            WenXingTools.LOGGER.info("[wenxing_pickaxe] bedrock loot table restored -> {}",
                    Blocks.BEDROCK.getLootTable().location());
        } catch (Throwable t) {
            // 字段改名或被封装时降级：基岩回到「无掉落」，不阻塞启动
            WenXingTools.LOGGER.warn("[wenxing_pickaxe] bedrock loot table restore failed: {}", t.toString());
        }
    }
}
