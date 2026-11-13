package com.wenxing.wenxingtools.menu;

/**
 * wenxing铁砧的纯规则层：只依赖 java.*，不 import 任何 net.minecraft.*。
 *
 * <p>这样切分的目的是让「等级怎么叠、耐久怎么合、什么时候该出结果、名字该不该套用、代价多少」
 * 这几条不变量能被 T1 探针（jshell / 单文件 java）直接断言，而不用等真实客户端。
 * 菜单层只负责把 ItemStack 拆成这些函数要的整数再拼回去。
 */
public final class WenxingAnvilRules {

    /** 附魔或修复的代价：1 点经验（经验点，不是经验等级）。 */
    public static final int COST_XP_POINTS = 1;

    /** 改名长度上限，跟随原版 {@code AnvilMenu.MAX_NAME_LENGTH}（该常量是 public，改动时以原版为准）。 */
    public static final int MAX_NAME_LENGTH = 50;

    private WenxingAnvilRules() {
    }

    /**
     * 合并一条附魔：与源等级相同则升一级，否则取较大者，最后钳到该附魔的等级上限。
     * 不判断互斥、不判断物品能否附魔——「任意物品、无视冲突」由调用方跳过原版判定实现。
     *
     * <p>结果为 {@code max(currentLevel, 钳后等级)}：常规路径（现有等级不超上限）与此等价，
     * 差异只在「物品自带等级已高于该附魔 maxLevel」时——那时保留原等级而不是降级。
     * 本模组的内置附魔物品（资源增幅镐等）属于这种情况，降级会把已有能力削掉。
     */
    public static int mergeLevel(int currentLevel, int sourceLevel, int maxLevel) {
        int cap = Math.max(1, maxLevel);
        if (sourceLevel <= 0) {
            return clampLevel(currentLevel, cap);
        }
        int merged = (currentLevel == sourceLevel) ? currentLevel + 1 : Math.max(currentLevel, sourceLevel);
        // 保留项不可先钳：currentLevel 高于上限时钳成 cap 就等于降级了
        return Math.max(Math.max(0, currentLevel), clampLevel(merged, cap));
    }

    private static int clampLevel(int level, int cap) {
        if (level < 0) {
            return 0;
        }
        return Math.min(level, cap);
    }

    /**
     * 同物品合并后的剩余损伤：先算耐久和（两件残留耐久相加 + 上限 12% 的修补偿），再换算回损伤。
     * damage 越小越完好。
     *
     * <p>语义对齐原版 {@code AnvilMenu.createResult} 的字节码：
     * {@code (maxDamage - dA) + (maxDamage - dB) + maxDamage * 12 / 100}，12% 加在**耐久和**上。
     * 写成「损伤相加 − 12%」方向正好相反：两件半损合并会得到几乎全损。
     */
    public static int mergedDamage(int leftDamage, int rightDamage, int maxDamage) {
        if (maxDamage <= 0) {
            return 0;
        }
        int durability = (maxDamage - leftDamage) + (maxDamage - rightDamage) + maxDamage * 12 / 100;
        int damage = maxDamage - durability;
        if (damage < 0) {
            damage = 0;
        }
        return Math.min(damage, maxDamage - 1);
    }

    /**
     * 是否把菜单里的名字应用到输出。
     *
     * @param nameBoundToLeft        名字确实属于当前左槽物品（setItemName 时记录的快照仍匹配）
     * @param nameBlank              名字为空
     * @param nameEqualsCurrentName  名字与左件当前显示名相同
     *
     * <p>第一条是必需的：菜单里的名字不随左槽物品变化，
     * 玩家给 A 改过名再换成 B 时，旧名字不能套到 B 上（否则 B 被莫名改名还白扣代价）。
     */
    public static boolean shouldApplyName(boolean nameBoundToLeft, boolean nameBlank,
                                          boolean nameEqualsCurrentName) {
        return nameBoundToLeft && !nameBlank && !nameEqualsCurrentName;
    }

    /** 名字是否可接受：非 null 且不超过原版长度上限。 */
    public static boolean isNameAcceptable(String filteredName) {
        return filteredName != null && filteredName.length() <= MAX_NAME_LENGTH;
    }

    /**
     * 是否产出结果。
     *
     * @param leftUsable     左槽非空且单件
     * @param rightEmpty     右槽为空
     * @param rightIsBook    右槽是附魔书
     * @param rightMergeable 右槽是与左件相同的可损坏物品
     * @param changed        按规则算完后，结果与左件相比确有变化
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
