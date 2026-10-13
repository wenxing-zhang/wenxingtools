package com.wenxing.wenxingtools.util;

import com.wenxing.wenxingtools.WenXingTools;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

import java.lang.reflect.Field;

/**
 * 抬高护甲 / 护甲韧性属性上限，使生命增幅胸甲的 1000 不被
 * {@code RangedAttribute.sanitizeValue} 静默截断（原版上限 30 / 20）。
 * <p>
 * 注册表冻结后无法替换 vanilla 条目，只能改写 {@code RangedAttribute} 的非 static final 字段；
 * 失败时保持原版上限并打 WARN，不崩溃。
 */
public final class ArmorAttributeUncap {
    private static final double TARGET_MAX = 10000.0D;

    private ArmorAttributeUncap() {}

    public static void apply() {
        boolean armorOk = raiseMax(Attributes.ARMOR, "generic.armor");
        boolean toughnessOk = raiseMax(Attributes.ARMOR_TOUGHNESS, "generic.armor_toughness");
        boolean applied = armorOk && toughnessOk;
        if (applied) {
            WenXingTools.LOGGER.info("[wenxingtools] armor attribute cap raised to {}", TARGET_MAX);
        } else {
            WenXingTools.LOGGER.warn("[wenxingtools] armor attribute uncap failed; armor stays clamped to 30/20");
        }
    }

    private static boolean raiseMax(Holder<Attribute> holder, String label) {
        Attribute attribute = holder.value();
        if (!(attribute instanceof RangedAttribute ranged)) {
            WenXingTools.LOGGER.warn("[wenxingtools] {} is not RangedAttribute; skip uncap", label);
            return false;
        }
        if (ranged.getMaxValue() >= TARGET_MAX) {
            return true;
        }
        try {
            Field field = RangedAttribute.class.getDeclaredField("maxValue");
            field.setAccessible(true);
            field.setDouble(ranged, TARGET_MAX);
            return ranged.getMaxValue() >= TARGET_MAX;
        } catch (Throwable t) {
            WenXingTools.LOGGER.warn("[wenxingtools] failed to raise {} max: {}", label, t.toString());
            return false;
        }
    }
}
