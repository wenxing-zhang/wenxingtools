package com.wenxing.wenxingtools.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class WenxingTorchItem extends StandingAndWallBlockItem {

    public WenxingTorchItem(Block standing, Block wall, Properties properties, Direction direction) {
        super(standing, wall, properties, direction);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("item.wenxingtools.wenxing_torch.desc")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}
