package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.item.LifeAmplificationChestplateItem;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = WenXingTools.MODID)
public final class LifeAmplificationChestplateEvents {

    // 护甲属性被原版钳位在 30/20（护甲层封顶 80%），这里在终值层补足「护甲 1000」的意图：
    // 额外削到 0.1% 残留（不取 0 是避免完全免疫，保留受伤事件链与坠落/虚空外的伤害语义）。
    private static final float EXTRA_DAMAGE_FACTOR = 0.001F;

    private LifeAmplificationChestplateEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }
        if (event.getSource().is(DamageTypeTags.BYPASSES_ARMOR)) {
            return;
        }
        ItemStack chest = entity.getItemBySlot(EquipmentSlot.CHEST);
        if (!(chest.getItem() instanceof LifeAmplificationChestplateItem)) {
            return;
        }
        event.setAmount(event.getAmount() * EXTRA_DAMAGE_FACTOR);
    }
}
