package com.wenxing.wenxingtools.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 「入口自动发现」与「分片收敛」的纯判定规则。
 *
 * <p>刻意不依赖任何 MC 类型：调用方在运行时把目标类的反射快照（字段 / 方法 / 取值）
 * 整理成本类的快照结构传入，由本类给出候选排序与分片序列，调用方再按候选顺序试调、
 * 用真实掉血闭环验证。这样「怎么排序、切几片、试到哪个源」这部分能脱离游戏运行时被独立断言，
 * 避免把全部判定埋进 {@code Entity.tick()} 或事件上下文而只能实机验收。</p>
 */
public final class HealthStateProbe {

    private HealthStateProbe() {
    }

    public static final float EPS = 1.0e-4f;
    public static final int MAX_CHUNKS = 64;

    /** 字段取值的可写数值类型。 */
    public enum NumericKind { FLOAT, DOUBLE, INT, LONG, OTHER }

    /** 目标类上一个字段的只读快照。{@code depth} 为沿继承链向上回溯的层数（0 = 最具体类）。 */
    public record FieldSnapshot(String name, NumericKind kind, double value, int depth) {
    }

    /** 目标类上一个方法的只读快照。 */
    public record MethodSnapshot(String name, NumericKind paramKind, boolean voidReturn, int depth) {
    }

    private static final String[] HEALTH_HINTS = {"hpoint", "health", "hp", "life", "vital"};

    private static final String[] NOT_HEALTH_HINTS = {"max", "absorb", "absorpt", "armor", "resist", "regen"};

    /** 可写回的数值类型（含 {@code long}——伤害冷却帧常以 long/int 记录）。 */
    public static boolean isNumeric(NumericKind kind) {
        return kind == NumericKind.FLOAT || kind == NumericKind.DOUBLE
                || kind == NumericKind.INT || kind == NumericKind.LONG;
    }

    /** 血量量纲的数值类型：血量与上限以 float/double/int 表示，{@code long} 视为非血量。 */
    public static boolean isHealthMagnitude(NumericKind kind) {
        return kind == NumericKind.FLOAT || kind == NumericKind.DOUBLE || kind == NumericKind.INT;
    }

    /** 反射得到的字段 / 参数类型 → 数值种类。只用到 {@code java.lang.Class} 的字面量比较，不依赖 MC。 */
    public static NumericKind numericKindOf(Class<?> type) {
        if (type == float.class || type == Float.class) {
            return NumericKind.FLOAT;
        }
        if (type == double.class || type == Double.class) {
            return NumericKind.DOUBLE;
        }
        if (type == int.class || type == Integer.class) {
            return NumericKind.INT;
        }
        if (type == long.class || type == Long.class) {
            return NumericKind.LONG;
        }
        return NumericKind.OTHER;
    }

    private static boolean containsAny(String name, String[] hints) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        for (String hint : hints) {
            if (lower.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    private static boolean nameEqualsAny(String name, String[] names) {
        if (name == null || names == null) {
            return false;
        }
        for (String n : names) {
            if (name.equals(n)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从字段快照里挑出「可能是当前血量」的候选，按可信度降序返回下标。
     *
     * <p>满血时当前血量与最大生命同值，单靠数值无法区分，因此规则保守、宁少不错：
     * 可写数值类型 + 取值 ∈ (0, maxHealth]；名字含 {@code max}/{@code absorb} 等语义词直接排除；
     * 未满血（取值显著小于 maxHealth）与名字含血量词根者加权提前，继承链越深越降权。</p>
     */
    public static List<Integer> selectHealthFieldCandidates(List<FieldSnapshot> fields, double maxHealth) {
        List<Integer> result = new ArrayList<>();
        if (fields == null || fields.isEmpty() || !(maxHealth > 0.0d) || Double.isNaN(maxHealth)) {
            return result;
        }
        List<int[]> scored = new ArrayList<>();
        for (int i = 0; i < fields.size(); i++) {
            FieldSnapshot f = fields.get(i);
            if (f == null || !isHealthMagnitude(f.kind())) {
                continue;
            }
            double v = f.value();
            if (!(v > 0.0d) || Double.isNaN(v)) {
                continue;
            }
            if (v > maxHealth + 1.0d) {
                continue;
            }
            if (containsAny(f.name(), NOT_HEALTH_HINTS)) {
                continue;
            }
            boolean strong = containsAny(f.name(), HEALTH_HINTS);
            int score = 0;
            if (v < maxHealth - 1.0e-3d) {
                score += 2;
            }
            score -= Math.min(Math.max(f.depth(), 0), 4);
            scored.add(new int[]{i, strong ? 1 : 0, score, Math.max(f.depth(), 0)});
        }
        scored.sort(Comparator.<int[]>comparingInt(a -> -a[1])
                .thenComparingInt(a -> -a[2])
                .thenComparingInt(a -> a[3])
                .thenComparingInt(a -> a[0]));
        for (int[] s : scored) {
            result.add(s[0]);
        }
        return result;
    }

    /**
     * 返回结果中「强候选」的数量——强候选名字命中血量词根（{@code health}/{@code hp}/{@code hpoint} 等），
     * 其分数恒高于仅靠「未满血」加权的弱候选，因此在 {@link #selectHealthFieldCandidates} 的结果里必然连续居首。
     * 调用方据此把「试写字段」限制在强候选范围内，弱候选仅在强候选全失败时才考虑。
     */
    public static int strongHealthFieldCount(List<FieldSnapshot> fields, double maxHealth) {
        List<Integer> all = selectHealthFieldCandidates(fields, maxHealth);
        int count = 0;
        for (int idx : all) {
            FieldSnapshot f = fields.get(idx);
            if (f == null || !containsAny(f.name(), HEALTH_HINTS)) {
                break;
            }
            count++;
        }
        return count;
    }

    /**
     * 从方法快照里挑出「可能是无钳制血量 setter」的候选，按可信度降序返回下标。
     *
     * <p>原先的白名单名（{@code preferredNames}）拿最高权重作为快速路径，其余
     * 「无返回 + 单数值参 + 名字含血量词根」的候选排在其后，供快速路径 miss 时兜底。</p>
     */
    public static List<Integer> selectSetterCandidates(List<MethodSnapshot> methods, String[] preferredNames) {
        List<Integer> result = new ArrayList<>();
        if (methods == null || methods.isEmpty()) {
            return result;
        }
        List<int[]> scored = new ArrayList<>();
        for (int i = 0; i < methods.size(); i++) {
            MethodSnapshot m = methods.get(i);
            if (m == null || !m.voidReturn() || !isHealthMagnitude(m.paramKind())) {
                continue;
            }
            if (!containsAny(m.name(), HEALTH_HINTS)) {
                continue;
            }
            int score = 0;
            if (nameEqualsAny(m.name(), preferredNames)) {
                score += 8;
            }
            if (m.name() != null && m.name().toLowerCase(Locale.ROOT).startsWith("set")) {
                score += 1;
            }
            scored.add(new int[]{i, score, Math.max(m.depth(), 0)});
        }
        scored.sort(Comparator.<int[]>comparingInt(a -> -a[1])
                .thenComparingInt(a -> a[2])
                .thenComparingInt(a -> a[0]));
        for (int[] s : scored) {
            result.add(s[0]);
        }
        return result;
    }

    /**
     * 按「观测到的单次可入账上限」把待扣量切成若干片。
     *
     * <p>{@code observedCap <= 0}（尚未观测到上限）返回单片全额，交由调用方闭环决定是否继续；
     * 否则每片不超过 cap，片数封顶 {@link #MAX_CHUNKS}，末片把余数一次给足。</p>
     */
    public static double[] planChunks(double remaining, double observedCap) {
        if (!(remaining > 0.0d) || Double.isNaN(remaining)) {
            return new double[0];
        }
        if (!(observedCap > 0.0d) || observedCap >= remaining) {
            return new double[]{remaining};
        }
        int count = (int) Math.ceil(remaining / observedCap);
        if (count > MAX_CHUNKS) {
            count = MAX_CHUNKS;
        }
        double[] chunks = new double[count];
        double left = remaining;
        for (int i = 0; i < count; i++) {
            double chunk;
            if (i == count - 1) {
                chunk = Math.max(0.0d, left);
            } else {
                chunk = Math.min(observedCap, left);
            }
            chunks[i] = chunk;
            left = Math.max(0.0d, left - chunk);
        }
        return chunks;
    }

    /** 由「意图量 vs 实际掉血」反推当前单次上限：没被削返回 0（无需分片），被削返回实际值。 */
    public static double inferObservedCap(double intended, double actualLost) {
        if (!(intended > 0.0d) || !(actualLost > 0.0d) || Double.isNaN(intended) || Double.isNaN(actualLost)) {
            return 0.0d;
        }
        if (actualLost >= intended - EPS) {
            return 0.0d;
        }
        return actualLost;
    }

    private static final String[] COOLDOWN_HINTS = {
            "cooldown", "immun", "invuln", "hurtresist", "damagereduc", "lastdamage", "lasthurt"
    };

    private static final String[] ACCUMULATED_DAMAGE_HINTS = {"damagetaken"};

    /** 名字命中这些词根的字段是「造成的伤害」而非「承受的伤害」，不能当观测口。 */
    private static final String[] ACCUMULATED_DAMAGE_EXCLUDES = {"dealt", "output", "inflicted"};

    private static final String[] ADD_DAMAGE_HINTS = {
            "adddamage", "applydamage", "damagedirect", "realdamage"
    };

    /**
     * 从字段快照里挑出「伤害冷却 / 无敌帧」语义的候选下标，保持传入顺序。
     *
     * <p>清零字段是有副作用的写操作，故只认名字含明确语义词根者——宁可漏，不可错清。</p>
     */
    public static List<Integer> selectCooldownFieldCandidates(List<FieldSnapshot> fields) {
        return selectNumericFieldsByHints(fields, COOLDOWN_HINTS);
    }

    /**
     * 从字段快照里挑出「累计承受伤害」语义的候选下标，供血量不落在 {@code getHealth} 上的目标降级使用。
     *
     * <p>只认含 {@code damageTaken} 的名字，并排除 {@code dealt}/{@code output}/{@code inflicted}
     * ——误选「造成的伤害」会让闭环把攻击行为本身当成扣血成功，虚增结算量。</p>
     */
    public static List<Integer> selectAccumulatedDamageFieldCandidates(List<FieldSnapshot> fields) {
        List<Integer> picked = selectNumericFieldsByHints(fields, ACCUMULATED_DAMAGE_HINTS);
        if (picked.isEmpty()) {
            return picked;
        }
        List<Integer> filtered = new ArrayList<>();
        for (int idx : picked) {
            FieldSnapshot f = fields.get(idx);
            if (f != null && !containsAny(f.name(), ACCUMULATED_DAMAGE_EXCLUDES)) {
                filtered.add(idx);
            }
        }
        return filtered;
    }

    /** 从方法快照里挑出「直接加伤」语义的候选下标（第三方自定义血量通道）。 */
    public static List<Integer> selectAddDamageMethodCandidates(List<MethodSnapshot> methods) {
        List<Integer> result = new ArrayList<>();
        if (methods == null) {
            return result;
        }
        for (int i = 0; i < methods.size(); i++) {
            MethodSnapshot m = methods.get(i);
            if (m == null || !isNumeric(m.paramKind())) {
                continue;
            }
            if (containsAny(m.name(), ADD_DAMAGE_HINTS)) {
                result.add(i);
            }
        }
        return result;
    }

    private static List<Integer> selectNumericFieldsByHints(List<FieldSnapshot> fields, String[] hints) {
        List<Integer> result = new ArrayList<>();
        if (fields == null) {
            return result;
        }
        for (int i = 0; i < fields.size(); i++) {
            FieldSnapshot f = fields.get(i);
            if (f == null || !isNumeric(f.kind())) {
                continue;
            }
            if (containsAny(f.name(), hints)) {
                result.add(i);
            }
        }
        return result;
    }

    /**
     * 两个观测点之间的「实际掉血」。
     *
     * <p>优先用血量差；血量未动但累计受伤增加时以累计增量为准（血量不落在 {@code getHealth} 上的目标）；
     * 两者皆无但目标已进入死亡时，视为把剩余血量一次扣尽——低血走必杀链后靠这条结账。</p>
     */
    public static double observedLoss(double healthBefore, double accumulatedBefore,
                                      double healthAfter, double accumulatedAfter, boolean deadAfter) {
        if (deadAfter && healthBefore > 0.0d) {
            return healthBefore;
        }
        double healthDelta = healthBefore - healthAfter;
        if (healthDelta > (double) EPS) {
            return healthDelta;
        }
        double accumulatedDelta = accumulatedAfter - accumulatedBefore;
        if (accumulatedDelta > (double) EPS) {
            return accumulatedDelta;
        }
        return 0.0d;
    }

    /** 依次返回下一个尚未尝试的源下标；全试过返回 -1。 */
    public static int nextUntriedSource(int triedMask, int total) {
        if (total <= 0 || total > 31) {
            return -1;
        }
        for (int i = 0; i < total; i++) {
            if ((triedMask & (1 << i)) == 0) {
                return i;
            }
        }
        return -1;
    }
}
