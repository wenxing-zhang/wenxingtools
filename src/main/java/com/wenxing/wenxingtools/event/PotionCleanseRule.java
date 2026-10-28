package com.wenxing.wenxingtools.event;

/**
 * 药水增幅净化的判定式，行为面不含任何 Minecraft 类型，便于隔离验证。
 * <p>
 * 判定只依赖两个输入：全身护甲上的最高药水增幅等级、目标效果是否属负面（HARMFUL）类别。
 * 正面与中性效果一律放行；无增幅时不干预任何效果。
 */
public final class PotionCleanseRule {

    private PotionCleanseRule() {
    }

    public static boolean shouldCleanse(int maxPotionAmplificationLevel, boolean harmful) {
        return maxPotionAmplificationLevel > 0 && harmful;
    }
}
