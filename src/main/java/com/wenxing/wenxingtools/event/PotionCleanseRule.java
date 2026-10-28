package com.wenxing.wenxingtools.event;

/**
 * 药水增幅净化的判定式，行为面不含任何 Minecraft 类型，便于隔离验证。
 * <p>
 * 三个输入：全身护甲上的最高药水增幅等级、目标效果是否属负面（HARMFUL）类别、目标是否属第三方保护类效果。
 * 正面与中性效果一律放行；无增幅时不干预；第三方保护类效果（如护盾）不清——与净化共用同一口径。
 */
public final class PotionCleanseRule {

    private PotionCleanseRule() {
    }

    public static boolean shouldCleanse(int maxPotionAmplificationLevel, boolean harmful, boolean protectedEffect) {
        return maxPotionAmplificationLevel > 0 && harmful && !protectedEffect;
    }
}
