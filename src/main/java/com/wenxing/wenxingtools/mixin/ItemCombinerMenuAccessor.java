package com.wenxing.wenxingtools.mixin;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ResultContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemCombinerMenu.class)
public interface ItemCombinerMenuAccessor {

    @Accessor("inputSlots")
    Container wenxingtools$getInputSlots();

    @Accessor("resultSlots")
    ResultContainer wenxingtools$getResultSlots();

    @Accessor("access")
    ContainerLevelAccess wenxingtools$getAccess();
}
