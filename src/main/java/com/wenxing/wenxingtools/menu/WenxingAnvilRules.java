package com.wenxing.wenxingtools.menu;

/**
 * wenxing铁砧的纯规则层：只依赖 java.*，不 import 任何 net.minecraft.*。
 *
 * <p>这样切分的目的是让「等级怎么叠、耐久怎么合、什么时候该出结果、代价多少」
 * 这几条不变量能被 T1 探针（jshell / 单文件 java）直接断言，而不用等真实客户端。
 * 菜单层只负责把 ItemStack 拆成这些函数要的整数再拼回去。
 */
public final class WenxingAnvilRules {

    /** 附魔或修复的代价：1 点经验（经验点，不是经验等级）。 */
    public static final int COST_XP_POINTS = 1;

    private WenxingAnvilRules() {
    }

    /**
     * 合并一条附魔：与源等级相同则升一级，否则取较大者，最后钳到该附魔的等级上限。
     * 不判断互斥、不判断物品能否附魔——「任意物品、无视冲突」由调用方跳过原版判定实现。
     */
    public static int mergeLevel(int currentLevel, int sourceLevel, int maxLevel) {
        int cap = Math.max(1, maxLevel);
        if (sourceLevel <= 0) {
            return clampLevel(currentLevel, cap);
        }
        int merged = (currentLevel == sourceLevel) ? currentLevel + 1 : Math.max(currentLevel, sourceLevel);
        return clampLevel(merged, cap);
    }

    private static int clampLevel(int level, int cap) {
        if (level < 0) {
            return 0;
        }
        return Math.min(level, cap);
    }

    /**
     * 同物品合并后的剩余损伤：两件损伤相加，再减去上限 12% 的修补偿，钳在 [0, maxDamage]。
     * damage 越小越完好，故结果是「合并后还剩多少损伤」。
     */
    public static int mergedDamage(int leftDamage, int rightDamage, int maxDamage) {
        if (maxDamage <= 0) {
            return 0;
        }
        int bonus = (int) Math.ceil(maxDamage * 0.12);
        int combined = leftDamage + rightDamage - bonus;
        if (combined < 0) {
            combined = 0;
        }
        return Math.min(combined, maxDamage);
    }

    /**
     * 是否产出结果。
     *
     * @param leftUsable    左槽非空且单件
     * @param rightEmpty    右槽为空
     * @param rightIsBook   右槽是附魔书
     * @param rightMergeable 右槽是与左件相同的可损坏物品
     * @param changed       按规则算完后，结果与左件相比确有变化
     *
     * <p>右槽是「其它任何物品」时一律不产出：本方块修复不需要材料，
     * 若此时仍产出结果，取件会把材料一起带走——宁可没结果，也不吞材料。
     */
    public static boolean shouldProduce(boolean leftUsable, boolean rightEmpty, boolean rightIsBook,
                                        boolean rightMergeable, boolean changed) {
        if (!leftUsable || !changed) {
            return false;
        }
        return rightEmpty || rightIsBook || rightMergeable;
    }

    /** 界面成本位：有结果显示 1，无结果显示 0。实际扣除的是 {@link #COST_XP_POINTS} 点经验。 */
    public static int costFor(boolean hasOutput) {
        return hasOutput ? COST_XP_POINTS : 0;
    }
}
