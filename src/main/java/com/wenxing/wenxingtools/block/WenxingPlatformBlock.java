package com.wenxing.wenxingtools.block;

import com.wenxing.wenxingtools.entity.MultiStackItemEntity;
import com.wenxing.wenxingtools.util.DropStackUtil;
import com.wenxing.wenxingtools.util.PlatformGenerator;
import com.wenxing.wenxingtools.util.PlatformGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * wenxing平台：放下后木棍右击，以玩家所在区块为中心铺 3×3 区块（48×48）双层平滑石平台，
 * 并把 54×54 范围内的平台层与上方一切方块按原版战利品表破坏（有掉落）。
 */
public class WenxingPlatformBlock extends Block {
    private static final int DROP_BATCH_ENTRIES = 512;

    public WenxingPlatformBlock(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!player.getItemInHand(hand).is(Items.STICK)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }
        ChunkPos center = player.chunkPosition();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                serverLevel.getChunk(center.x + dx, center.z + dz);
            }
        }

        Map<DropStackUtil.StackKey, Long> totals = new LinkedHashMap<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        PlatformGenerator.BlockSetter fill = (x, y, z) -> {
            cursor.set(x, y, z);
            harvest(serverLevel, cursor, totals);
            // UPDATE_CLIENTS：4608+ 次写入若带邻居更新会连锁触发流体回灌与下落方块；光照由 LevelChunk#setBlockState 自身重算
            serverLevel.setBlock(cursor, Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        };
        PlatformGenerator.BlockSetter clear = (x, y, z) -> {
            cursor.set(x, y, z);
            harvest(serverLevel, cursor, totals);
            serverLevel.setBlock(cursor, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        };
        PlatformGenerator.AirProbe probe = (x, y, z) -> serverLevel.getBlockState(cursor.set(x, y, z)).isAir();

        int anchorY = pos.getY();
        PlatformGenerator.Counts counts = PlatformGenerator.run(center.x, center.z, anchorY,
                serverLevel.getMaxBuildHeight(), fill, clear, probe);

        popDrops(serverLevel, center, anchorY, totals);

        if (PlatformGenerator.playerEmbedded((int) Math.floor(player.getY()), anchorY)) {
            player.teleportTo(player.getX(), PlatformGeometry.topY(anchorY) + 1, player.getZ());
        }
        player.displayClientMessage(
                Component.literal("wenxing平台已生成：48×48×2，破坏 " + counts.destroyed() + " 格"), true);
        return InteractionResult.CONSUME;
    }

    private static void harvest(ServerLevel level, BlockPos pos, Map<DropStackUtil.StackKey, Long> totals) {
        BlockState old = level.getBlockState(pos);
        if (old.isAir()) {
            return;
        }
        BlockEntity blockEntity = old.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        List<ItemStack> drops = new ArrayList<>(Block.getDrops(old, level, pos, blockEntity));
        if (blockEntity instanceof Container container) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack slot = container.getItem(i);
                if (!slot.isEmpty()) {
                    drops.add(slot.copy());
                }
            }
        }
        if (drops.isEmpty()) {
            return;
        }
        DropStackUtil.mergeStacksMultiplied(drops, 1L)
                .forEach((key, amount) -> totals.merge(key, amount, Long::sum));
    }

    private static void popDrops(ServerLevel level, ChunkPos center, int anchorY,
                                 Map<DropStackUtil.StackKey, Long> totals) {
        List<ItemStack> stacks = DropStackUtil.totalsToStacks(totals);
        if (stacks.isEmpty()) {
            return;
        }
        double x = PlatformGeometry.minX(center.x) + PlatformGeometry.SIDE / 2.0;
        double y = PlatformGeometry.topY(anchorY) + 1.5;
        double z = PlatformGeometry.minZ(center.z) + PlatformGeometry.SIDE / 2.0;
        for (int i = 0; i < stacks.size(); i += DROP_BATCH_ENTRIES) {
            List<ItemStack> batch = new ArrayList<>(
                    stacks.subList(i, Math.min(i + DROP_BATCH_ENTRIES, stacks.size())));
            level.addFreshEntity(MultiStackItemEntity.fromStacks(level, x, y, z, batch));
        }
    }
}
