package com.wenxing.wenxingtools.util;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

/**
 * 矿石矩形生成：按 {@link OreRectangleGeometry} 的 99 格偏移逐格覆盖为矿石，单 tick 完成。
 *
 * <p>被替换方块不结算战利品（语义是「生成矿石」，产出即收益），因此这里刻意不走 dropResources；
 * 容器方块（箱子/熔炉/漏斗/潜影盒等）的内容物仍由原版 onRemove 弹出，本类不干预——与 wenxing 平台同口径。
 */
public final class OreRectangleGenerator {
    private static final Block[] ORE_POOL = {
            Blocks.COAL_ORE,
            Blocks.IRON_ORE,
            Blocks.COPPER_ORE,
            Blocks.GOLD_ORE,
            Blocks.REDSTONE_ORE,
            Blocks.LAPIS_ORE,
            Blocks.DIAMOND_ORE,
            Blocks.EMERALD_ORE,
            Blocks.ANCIENT_DEBRIS,
            Blocks.NETHER_QUARTZ_ORE,
            Blocks.GLOWSTONE
    };

    static {
        // 池长度与层数脱钩会让 setBlock 读到 null 状态并在世界层抛 NPE，宁可启动期直接失败
        if (ORE_POOL.length != OreRectangleGeometry.ORE_KINDS) {
            throw new IllegalStateException("矿石池长度 " + ORE_POOL.length
                    + " 与 OreRectangleGeometry.ORE_KINDS " + OreRectangleGeometry.ORE_KINDS + " 不一致");
        }
    }

    private OreRectangleGenerator() {
    }

    /** @return 实际写入的格数（贴世界上下边界时会被裁剪） */
    public static int generate(ServerLevel level, BlockPos anchor, @Nullable Player player) {
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        int placed = 0;
        for (int i = 0; i < OreRectangleGeometry.cellCount(); i++) {
            BlockPos pos = anchor.offset(
                    OreRectangleGeometry.xOffset(i),
                    OreRectangleGeometry.yOffset(i),
                    OreRectangleGeometry.zOffset(i));
            if (pos.getY() < minY || pos.getY() >= maxY) {
                continue;
            }
            Block block = ORE_POOL[OreRectangleGeometry.oreIndexOf(i)];
            // UPDATE_CLIENTS：只同步客户端、不做邻居更新，避免范围内红石元件与依赖支撑的方块被惊动（与平台同口径）
            if (level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_CLIENTS)) {
                placed++;
            }
        }
        if (player != null) {
            player.displayClientMessage(Component.literal("已生成矿石矩形：3×3×" + OreRectangleGeometry.ORE_KINDS
                    + "，共 " + placed + " 格"), false);
        }
        return placed;
    }
}
