package com.wenxing.wenxingtools.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

public class WenxingTorchItem extends StandingAndWallBlockItem {

    public WenxingTorchItem(Block standing, Block wall, Item.Properties properties, Direction direction) {
        super(standing, wall, properties, direction);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.wenxingtools.wenxing_torch.desc")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}
