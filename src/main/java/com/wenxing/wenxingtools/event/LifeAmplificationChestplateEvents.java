package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.item.WenxingChestplateItem;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * 生命增幅胸甲的等效减伤层。
 * <p>
 * 护甲属性被原版钳位在 30/20（护甲层封顶 80%），这里在伤害终值层补足「护甲 1000」的意图：
 * 额外削到 0.1% 残留（不取 0 是避免完全免疫，保留受伤事件链与坠落/虚空外的伤害语义）。
 */
@EventBusSubscriber(modid = WenXingTools.MODID)
public final class LifeAmplificationChestplateEvents {

    private static final float EXTRA_DAMAGE_FACTOR = 0.001F;

    private LifeAmplificationChestplateEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }
        if (event.getSource().is(DamageTypeTags.BYPASSES_ARMOR)) {
            return;
        }
        ItemStack chest = entity.getItemBySlot(EquipmentSlot.CHEST);
        if (!(chest.getItem() instanceof WenxingChestplateItem)) {
            return;
        }
        float amount = event.getNewDamage();
        if (Float.isNaN(amount) || Float.isInfinite(amount)) {
            return;
        }
        event.setNewDamage(amount * EXTRA_DAMAGE_FACTOR);
    }
}
