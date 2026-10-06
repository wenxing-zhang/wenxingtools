package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = WenXingTools.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ResourcePickaxeMiningHandler {
    private ResourcePickaxeMiningHandler() {
    }


    private static final float TARGET_TICKS = 5.0F;

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (player == null || !player.getMainHandItem().is(ModItems.RESOURCE_AMPLIFICATION_PICKAXE.get())) {
            return;
        }
        BlockState state = event.getState();
        if (state == null) {
            return;
        }

        if (state.is(Blocks.BEDROCK)) {
            return;
        }
        BlockPos pos = event.getPosition().orElse(null);
        float hardness = pos != null
                ? state.getDestroySpeed(player.level(), pos)
                : state.getDestroySpeed(player.level(), BlockPos.ZERO);

        if (hardness <= 0.0F) {
            return;
        }
        boolean canHarvest = !state.requiresCorrectToolForDrops()
                || player.getMainHandItem().isCorrectToolForDrops(state);
        float divisor = canHarvest ? 30.0F : 100.0F;

        event.setNewSpeed(hardness * divisor / TARGET_TICKS);
    }
}
