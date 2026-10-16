package com.wenxing.wenxingtools.util;

import com.wenxing.wenxingtools.WenXingTools;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通用「单次伤害上限 / 伤害冷却」旁路真伤。
 *
 * <p>Goety:Awaken 等模组会在 {@code setHealth}/{@code hurt} 上钳制单次入账：
 * 哞菇巨兽 {@code setHealth} 按最大生命 25% 钳制、{@code hurt}/{@code actuallyHurt} 二次钳制；
 * 无名者 {@code hurt} 20% 上限 + 命中冷却 + 动态减伤，仅 {@code GENERIC_KILL} / 创造免钳。
 * 「按最大生命百分比」的真伤会被削成上限值甚至 0，折叠进事件后仍走被钳制的 {@code hurt}。</p>
 *
 * <p>策略（按序降级，累计真实掉血）：</p>
 * <ol>
 *   <li>分片 {@code setHealth}：单次被钳制则继续扣下一片，直至扣满目标量</li>
 *   <li>反射无钳制 setter：先按已知名快速命中，未命中再按「无返回 + 单数值参 + 名字含血量词根」枚举候选</li>
 *   <li>清伤害冷却字段后，以 {@code GENERIC_KILL} + 玩家归属 {@code hurt} 兜底</li>
 * </ol>
 */
public final class DamageCapBypass {
    private DamageCapBypass() {}

    private static final float EPS = 1.0e-4f;
    private static final int MAX_CHUNKS = 64;

    private static final String[] UNCLAMPED_SETTER_NAMES = {
            "setHpoint",
            "setVanillaHealth",
            "setCombatHealth",
            "setRealHealth",
            "rawSetHealth",
            "setHealthBypass",
            "setHealthDirect",
    };

    private static final String[] COOLDOWN_FIELD_NAMES = {
            "damageCooldownTicks",
            "damageCooldown",
            "hurtCooldown",
            "hurtCooldownTicks",
            "damageReductionCooldown",
            "lastDamageProcessedTick",
    };

    private static final Map<Class<?>, UnclampedSetter[]> SETTER_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Field[]> COOLDOWN_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Optional<Field>> ACCUMULATED_FIELD_CACHE = new ConcurrentHashMap<>();

    /** 按可入账上限分片扣血，返回实际扣血量。不改事件金额、不叠伤。 */
    public static float applyDamageCapAwareTrueDamage(Player attacker, LivingEntity target, float amount) {
        if (target == null || !(amount > 0.0f) || Float.isNaN(amount)) {
            return 0.0f;
        }
        if (!target.isAlive() && !(target.getHealth() > 0.0f)) {
            return 0.0f;
        }

        float before = target.getHealth();
        if (!(before > 0.0f) || Float.isNaN(before)) {
            return 0.0f;
        }

        float remaining = Math.min(amount, before);
        float totalDealt = 0.0f;

        totalDealt += drainViaChunkedSetHealth(target, remaining);
        remaining = Math.max(0.0f, amount - totalDealt);
        if (remaining <= EPS || !target.isAlive()) {
            return totalDealt;
        }

        totalDealt += drainViaUnclampedSetters(target, remaining);
        remaining = Math.max(0.0f, amount - totalDealt);
        if (remaining <= EPS || !target.isAlive()) {
            return totalDealt;
        }

        totalDealt += drainViaGenericKillHurt(attacker, target, remaining);
        remaining = Math.max(0.0f, amount - totalDealt);
        if (remaining > EPS && target.isAlive()) {
            totalDealt += drainViaHealthFieldWrite(target, remaining);
        }

        if (totalDealt <= EPS && WenXingTools.LOGGER.isDebugEnabled()) {
            logHealthFieldDiagnostics(target);
        }
        return totalDealt;
    }

    /**
     * 所有通道都扣不动时的只读诊断：列出疑似承载血量的字段，供定位「需要主动适配」的目标。
     * <p>只在 debug 日志开启时执行——热路径不因诊断付反射开销。</p>
     */
    private static void logHealthFieldDiagnostics(LivingEntity target) {
        List<HealthStateProbe.FieldSnapshot> snapshots = new ArrayList<>();
        int depth = 0;
        for (Class<?> c = target.getClass(); c != null && c != Object.class && c != LivingEntity.class; c = c.getSuperclass(), depth++) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                HealthStateProbe.NumericKind kind = HealthStateProbe.numericKindOf(f.getType());
                if (!HealthStateProbe.isHealthMagnitude(kind)) {
                    continue;
                }
                snapshots.add(new HealthStateProbe.FieldSnapshot(f.getName(), kind, readFieldDouble(f, target), depth));
            }
        }
        double maxHealth = target.getMaxHealth();
        List<Integer> ordered = HealthStateProbe.selectHealthFieldCandidates(snapshots, maxHealth);
        List<String> suspects = new ArrayList<>();
        for (int i = 0; i < Math.min(ordered.size(), 5); i++) {
            suspects.add(snapshots.get(ordered.get(i)).name());
        }
        WenXingTools.LOGGER.debug(
                "[damage_cap_bypass] no progress on {} (health={}, maxHealth={}); suspect health fields={} strong={}",
                target.getClass().getName(), target.getHealth(), maxHealth,
                suspects, HealthStateProbe.strongHealthFieldCount(snapshots, maxHealth));
    }

    private static double readFieldDouble(Field field, Object owner) {
        try {
            field.setAccessible(true);
            Object value = field.get(owner);
            if (value instanceof Float f) {
                return f.doubleValue();
            }
            if (value instanceof Double d) {
                return d;
            }
            if (value instanceof Integer i) {
                return i.doubleValue();
            }
            if (value instanceof Long l) {
                return l.doubleValue();
            }
        } catch (Throwable ignored) {
            // 不可读
        }
        return 0.0d;
    }

    /**
     * 目标的「累计承受伤害」字段——部分模组的 {@code getHealth} 是滞后/展示值，真实受伤总量另记字段。
     * <p>用于旁路闭环的第二观测口：血量差为 0 时若该字段增加，仍判定「扣动了」。</p>
     */
    private static Field accumulatedFieldFor(Class<?> type) {
        return ACCUMULATED_FIELD_CACHE.computeIfAbsent(type, t -> {
            List<HealthStateProbe.FieldSnapshot> snapshots = new ArrayList<>();
            List<Field> fields = new ArrayList<>();
            int depth = 0;
            for (Class<?> c = t; c != null && c != Object.class && c != LivingEntity.class; c = c.getSuperclass(), depth++) {
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) {
                        continue;
                    }
                    HealthStateProbe.NumericKind kind = HealthStateProbe.numericKindOf(f.getType());
                    if (!HealthStateProbe.isNumeric(kind)) {
                        continue;
                    }
                    snapshots.add(new HealthStateProbe.FieldSnapshot(f.getName(), kind, 0.0d, depth));
                    fields.add(f);
                }
            }
            for (int idx : HealthStateProbe.selectAccumulatedDamageFieldCandidates(snapshots)) {
                Field f = fields.get(idx);
                try {
                    f.setAccessible(true);
                    return Optional.of(f);
                } catch (Throwable ignored) {
                    // 不可写，试下一个
                }
            }
            return Optional.empty();
        }).orElse(null);
    }

    /** 读累计承受伤害；无该字段或不可读返回 0（此时闭环只靠血量差与死亡判据）。 */
    public static double readAccumulated(LivingEntity target) {
        Field field = accumulatedFieldFor(target.getClass());
        if (field == null) {
            return 0.0d;
        }
        try {
            Object value = field.get(target);
            if (value instanceof Number n) {
                return n.doubleValue();
            }
        } catch (Throwable ignored) {
            // 不可读
        }
        return 0.0d;
    }

    private static float readFloatField(Field field, Object owner) {
        try {
            Object value = field.get(owner);
            if (value instanceof Float f) {
                return f;
            }
        } catch (Throwable ignored) {
            // 不可读
        }
        return 0.0f;
    }

    private static boolean writeFloatField(Field field, Object owner, float value) {
        try {
            field.set(owner, value);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 将目标血量压到 0（击杀路径）。尊重单次钳制，分片清空。 */
    public static boolean forceReduceHealthToZero(Player attacker, LivingEntity target) {
        if (target == null || target.isRemoved()) {
            return true;
        }
        float before = target.getHealth();
        if (!(before > 0.0f) || Float.isNaN(before)) {
            return target.getHealth() <= 0.0f;
        }

        drainViaChunkedSetHealth(target, before);
        if (target.getHealth() > 0.0f) {
            drainViaUnclampedSetters(target, target.getHealth());
        }
        if (target.getHealth() > 0.0f) {
            drainViaGenericKillHurt(attacker, target, target.getHealth());
        }
        if (target.getHealth() > 0.0f) {
            drainViaHealthFieldWrite(target, target.getHealth());
        }
        if (target.getHealth() > 0.0f) {
            target.setHealth(0.0f);
        }
        return target.getHealth() <= 0.0f || target.isDeadOrDying();
    }

    private static float drainViaChunkedSetHealth(LivingEntity target, float amount) {
        float totalDealt = 0.0f;
        float remaining = amount;
        double observedCap = 0.0d;
        int guard = MAX_CHUNKS;
        while (remaining > EPS && guard-- > 0 && target.isAlive()) {
            float before = target.getHealth();
            if (!(before > 0.0f) || Float.isNaN(before)) {
                break;
            }
            double accumulatedBefore = readAccumulated(target);
            double[] plan = HealthStateProbe.planChunks(remaining, observedCap);
            if (plan.length == 0) {
                break;
            }
            float chunk = (float) Math.min(plan[0], before);
            target.setHealth(before - chunk);
            double lost = HealthStateProbe.observedLoss(
                    before, accumulatedBefore, target.getHealth(), readAccumulated(target), !target.isAlive());
            if (!(lost > EPS)) {
                break;
            }
            if (lost > chunk + EPS) {
                lost = chunk;
            }
            totalDealt += (float) lost;
            remaining -= (float) lost;
            observedCap = HealthStateProbe.inferObservedCap(chunk, lost);
        }
        return totalDealt;
    }

    private static float drainViaUnclampedSetters(LivingEntity target, float amount) {
        UnclampedSetter[] setters = settersFor(target.getClass());
        if (setters.length == 0) {
            return 0.0f;
        }
        float totalDealt = 0.0f;
        float remaining = amount;
        int guard = MAX_CHUNKS;
        while (remaining > EPS && guard-- > 0 && target.isAlive()) {
            float before = target.getHealth();
            if (!(before > 0.0f) || Float.isNaN(before)) {
                break;
            }
            float chunk = Math.min(remaining, before);
            float intended = before - chunk;
            double accumulatedBefore = readAccumulated(target);
            double lost = 0.0d;
            for (UnclampedSetter setter : setters) {
                try {
                    setter.invoke(target, intended);
                } catch (Throwable ignored) {
                    continue;
                }
                lost = HealthStateProbe.observedLoss(
                        before, accumulatedBefore, target.getHealth(), readAccumulated(target), !target.isAlive());
                if (lost > EPS) {
                    break;
                }
            }
            if (!(lost > EPS)) {
                break;
            }
            if (lost > chunk + EPS) {
                lost = chunk;
            }
            totalDealt += (float) lost;
            remaining -= (float) lost;
        }
        return totalDealt;
    }

    /** 伤害源枚举总档数：generic_kill → out_of_world → infinity_kill。 */
    public static final int BYPASS_SOURCE_COUNT = 3;

    private static float drainViaGenericKillHurt(Player attacker, LivingEntity target, float amount) {
        if (!(amount > 0.0f) || !target.isAlive()) {
            return 0.0f;
        }
        float totalDealt = 0.0f;
        int tried = 0;
        int index = HealthStateProbe.nextUntriedSource(tried, BYPASS_SOURCE_COUNT);
        while (index >= 0) {
            tried |= (1 << index);
            float remaining = Math.max(0.0f, amount - totalDealt);
            if (remaining <= EPS || !target.isAlive()) {
                break;
            }
            DamageSource source = createBypassSource(attacker, target, index);
            if (source != null) {
                totalDealt += drainWithSource(target, source, remaining);
            }
            index = HealthStateProbe.nextUntriedSource(tried, BYPASS_SOURCE_COUNT);
        }
        return totalDealt;
    }

    /** 单源分片入账：每次清冷却后打一片，按真实掉血累计，直到扣满或不再掉血。 */
    private static float drainWithSource(LivingEntity target, DamageSource source, float amount) {
        clearDamageCooldowns(target);
        float totalDealt = 0.0f;
        float remaining = amount;
        int guard = MAX_CHUNKS;
        while (remaining > EPS && guard-- > 0 && target.isAlive()) {
            float before = target.getHealth();
            if (!(before > 0.0f) || Float.isNaN(before)) {
                break;
            }
            double accumulatedBefore = readAccumulated(target);
            float chunk = Math.min(remaining, before);
            clearDamageCooldowns(target);
            target.invulnerableTime = 0;
            target.hurtTime = 0;
            target.hurt(source, chunk);
            double lost = HealthStateProbe.observedLoss(
                    before, accumulatedBefore, target.getHealth(), readAccumulated(target), !target.isAlive());
            if (!(lost > EPS)) {
                break;
            }
            if (lost > chunk + EPS) {
                lost = chunk;
            }
            totalDealt += (float) lost;
            remaining -= (float) lost;
        }
        return totalDealt;
    }

    /**
     * 最后手段：直接写「疑似承载血量的实例字段」。
     *
     * <p>只在方法层三条通道（setHealth / 无钳 setter / hurt）全部无进展时启用——此时目标多半把
     * 血量藏在私有字段且拒写。只写名字含血量词根的强候选、且限 {@code float}；每次写入后用
     * {@link HealthStateProbe#observedLoss} 验证是否真的推动了血量，未推动立刻写回原值，
     * 避免把与血量无关的字段改坏。</p>
     */
    private static float drainViaHealthFieldWrite(LivingEntity target, float amount) {
        if (target == null || !(amount > 0.0f)) {
            return 0.0f;
        }
        List<HealthStateProbe.FieldSnapshot> snapshots = new ArrayList<>();
        List<Field> fields = new ArrayList<>();
        int depth = 0;
        for (Class<?> c = target.getClass(); c != null && c != Object.class && c != LivingEntity.class; c = c.getSuperclass(), depth++) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType() != float.class) {
                    continue;
                }
                snapshots.add(new HealthStateProbe.FieldSnapshot(
                        f.getName(), HealthStateProbe.NumericKind.FLOAT, readFloatField(f, target), depth));
                fields.add(f);
            }
        }
        if (snapshots.isEmpty()) {
            return 0.0f;
        }
        double maxHealth = target.getMaxHealth();
        int strong = HealthStateProbe.strongHealthFieldCount(snapshots, maxHealth);
        if (strong <= 0) {
            return 0.0f;
        }
        List<Integer> ordered = HealthStateProbe.selectHealthFieldCandidates(snapshots, maxHealth);
        float totalDealt = 0.0f;
        for (int i = 0; i < strong && i < ordered.size() && totalDealt <= EPS; i++) {
            Field f = fields.get(ordered.get(i));
            try {
                f.setAccessible(true);
            } catch (Throwable ignored) {
                continue;
            }
            totalDealt += writeFieldChunked(target, f, Math.max(0.0f, amount - totalDealt));
        }
        return totalDealt;
    }

    /** 对单个疑似血量字段分片写入；每次写入都闭环验证，无进展即写回原值。 */
    private static float writeFieldChunked(LivingEntity target, Field field, float amount) {
        float fieldValue = readFloatField(field, target);
        if (!(fieldValue > 0.0f) || !(amount > 0.0f)) {
            return 0.0f;
        }
        float totalDealt = 0.0f;
        float remaining = Math.min(amount, fieldValue);
        double observedCap = 0.0d;
        int guard = MAX_CHUNKS;
        while (remaining > EPS && guard-- > 0 && target.isAlive()) {
            float valueBefore = readFloatField(field, target);
            if (!(valueBefore > 0.0f)) {
                break;
            }
            float healthBefore = target.getHealth();
            double accumulatedBefore = readAccumulated(target);
            double[] plan = HealthStateProbe.planChunks(remaining, observedCap);
            if (plan.length == 0) {
                break;
            }
            float chunk = (float) Math.min(plan[0], valueBefore);
            if (!writeFloatField(field, target, Math.max(0.0f, valueBefore - chunk))) {
                break;
            }
            double lost = HealthStateProbe.observedLoss(
                    healthBefore, accumulatedBefore, target.getHealth(), readAccumulated(target), !target.isAlive());
            if (!(lost > EPS)) {
                writeFloatField(field, target, valueBefore);
                break;
            }
            if (lost > chunk + EPS) {
                lost = chunk;
            }
            totalDealt += (float) lost;
            remaining -= (float) lost;
            observedCap = HealthStateProbe.inferObservedCap(chunk, lost);
        }
        return totalDealt;
    }

    /** GENERIC_KILL 玩家归属伤害源：无名者对 GENERIC_KILL 免钳，且保留击杀归属。 */
    public static DamageSource createGenericKillSource(Player attacker, LivingEntity target) {
        return sourceFor(attacker, target, DamageTypes.GENERIC_KILL);
    }

    /**
     * 伤害源枚举：部分目标按 {@code DamageType} 白名单免钳，换源即可绕过。
     * <p>顺序固定为 generic_kill（已知对无名者免钳）→ out_of_world → 本 mod infinity_kill。</p>
     */
    public static DamageSource createBypassSource(Player attacker, LivingEntity target, int index) {
        return switch (index) {
            case 0 -> sourceFor(attacker, target, DamageTypes.GENERIC_KILL);
            case 1 -> sourceFor(attacker, target, CombatUtil.VANILLA_OUT_OF_WORLD);
            case 2 -> sourceFor(attacker, target, CombatUtil.INFINITY_KILL);
            default -> null;
        };
    }

    private static DamageSource sourceFor(Player attacker, LivingEntity target,
                                          net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> typeKey) {
        try {
            net.minecraft.world.level.Level level = attacker != null ? attacker.level()
                    : (target != null ? target.level() : null);
            if (level == null) {
                return null;
            }
            var holder = level.registryAccess()
                    .registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(typeKey);
            if (attacker != null) {
                return new DamageSource(holder, attacker);
            }
            return new DamageSource(holder);
        } catch (Throwable t) {
            WenXingTools.LOGGER.debug("[damage_cap_bypass] source {} failed: {}", typeKey.location(), t.toString());
            return null;
        }
    }

    private static void clearDamageCooldowns(LivingEntity target) {
        for (Field field : cooldownFieldsFor(target.getClass())) {
            try {
                Class<?> type = field.getType();
                if (type == int.class || type == Integer.class) {
                    field.set(target, 0);
                } else if (type == long.class || type == Long.class) {
                    field.set(target, 0L);
                } else if (type == float.class || type == Float.class) {
                    field.set(target, 0.0f);
                } else if (type == double.class || type == Double.class) {
                    field.set(target, 0.0d);
                } else if (type == short.class || type == Short.class) {
                    field.set(target, (short) 0);
                } else if (type == byte.class || type == Byte.class) {
                    field.set(target, (byte) 0);
                }
            } catch (Throwable ignored) {
                // 只读或不可写
            }
        }
    }

    private record UnclampedSetter(Method method) {
        void invoke(LivingEntity target, float health) throws Throwable {
            Class<?> param = method.getParameterTypes()[0];
            Object arg;
            if (param == float.class) {
                arg = health;
            } else if (param == double.class) {
                arg = (double) health;
            } else if (param == int.class) {
                arg = (int) Math.round(health);
            } else {
                return;
            }
            method.invoke(target, arg);
        }
    }

    private static UnclampedSetter[] settersFor(Class<?> type) {
        return SETTER_CACHE.computeIfAbsent(type, t -> {
            List<UnclampedSetter> found = new ArrayList<>();
            for (Class<?> c = t; c != null && c != Object.class && c != LivingEntity.class; c = c.getSuperclass()) {
                for (String name : UNCLAMPED_SETTER_NAMES) {
                    Method m = declaredNumericSetter(c, name);
                    if (m != null) {
                        found.add(new UnclampedSetter(m));
                    }
                }
            }
            if (!found.isEmpty()) {
                return found.toArray(new UnclampedSetter[0]);
            }
            found.addAll(discoveredSetters(t));
            return found.toArray(new UnclampedSetter[0]);
        });
    }

    /** 已知名快速路径：先试 {@code (float)} 再试 {@code (double)}。 */
    private static Method declaredNumericSetter(Class<?> owner, String name) {
        for (Class<?> param : new Class<?>[]{float.class, double.class}) {
            try {
                Method m = owner.getDeclaredMethod(name, param);
                if (!Modifier.isStatic(m.getModifiers())) {
                    m.setAccessible(true);
                    return m;
                }
            } catch (NoSuchMethodException ignored) {
                // 试下一种签名
            }
        }
        return null;
    }

    /**
     * 兜底：按类层次枚举「无返回 + 单数值参 + 名字含血量词根」的候选，由纯逻辑排序后逐个试调。
     * <p>只在已知名全部 miss 时启用，且调用方 {@code drainViaUnclampedSetters} 会逐候选闭环验证
     * （调用后 {@code getHealth} 未下降就换下一个），因此试调无副作用的候选不会被采用。</p>
     */
    private static List<UnclampedSetter> discoveredSetters(Class<?> type) {
        List<HealthStateProbe.MethodSnapshot> snapshots = new ArrayList<>();
        List<Method> methods = new ArrayList<>();
        int depth = 0;
        for (Class<?> c = type; c != null && c != Object.class && c != LivingEntity.class; c = c.getSuperclass(), depth++) {
            for (Method m : c.getDeclaredMethods()) {
                if (Modifier.isStatic(m.getModifiers()) || Modifier.isAbstract(m.getModifiers())) {
                    continue;
                }
                if (m.getReturnType() != void.class || m.getParameterCount() != 1) {
                    continue;
                }
                HealthStateProbe.NumericKind kind = HealthStateProbe.numericKindOf(m.getParameterTypes()[0]);
                if (!HealthStateProbe.isHealthMagnitude(kind)) {
                    continue;
                }
                snapshots.add(new HealthStateProbe.MethodSnapshot(m.getName(), kind, true, depth));
                methods.add(m);
            }
        }
        if (snapshots.isEmpty()) {
            return List.of();
        }
        List<UnclampedSetter> result = new ArrayList<>();
        for (int idx : HealthStateProbe.selectSetterCandidates(snapshots, UNCLAMPED_SETTER_NAMES)) {
            Method m = methods.get(idx);
            try {
                m.setAccessible(true);
            } catch (Throwable ignored) {
                continue;
            }
            result.add(new UnclampedSetter(m));
        }
        return result;
    }

    private static Field[] cooldownFieldsFor(Class<?> type) {
        return COOLDOWN_CACHE.computeIfAbsent(type, t -> {
            List<Field> found = new ArrayList<>();
            for (Class<?> c = t; c != null && c != Object.class && c != LivingEntity.class; c = c.getSuperclass()) {
                for (String name : COOLDOWN_FIELD_NAMES) {
                    try {
                        Field f = c.getDeclaredField(name);
                        if (Modifier.isStatic(f.getModifiers())) {
                            continue;
                        }
                        f.setAccessible(true);
                        found.add(f);
                    } catch (NoSuchFieldException ignored) {
                        // 试下一个名
                    }
                }
            }
            if (!found.isEmpty()) {
                return found.toArray(new Field[0]);
            }
            found.addAll(discoveredCooldownFields(t));
            return found.toArray(new Field[0]);
        });
    }

    /** 兜底：按「名字含冷却 / 无敌帧词根」枚举数值字段——清零是有副作用的写，故只认明确语义。 */
    private static List<Field> discoveredCooldownFields(Class<?> type) {
        List<HealthStateProbe.FieldSnapshot> snapshots = new ArrayList<>();
        List<Field> fields = new ArrayList<>();
        int depth = 0;
        for (Class<?> c = type; c != null && c != Object.class && c != LivingEntity.class; c = c.getSuperclass(), depth++) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                HealthStateProbe.NumericKind kind = HealthStateProbe.numericKindOf(f.getType());
                if (!HealthStateProbe.isNumeric(kind)) {
                    continue;
                }
                snapshots.add(new HealthStateProbe.FieldSnapshot(f.getName(), kind, 0.0d, depth));
                fields.add(f);
            }
        }
        if (snapshots.isEmpty()) {
            return List.of();
        }
        List<Field> result = new ArrayList<>();
        for (int idx : HealthStateProbe.selectCooldownFieldCandidates(snapshots)) {
            Field f = fields.get(idx);
            try {
                f.setAccessible(true);
            } catch (Throwable ignored) {
                continue;
            }
            result.add(f);
        }
        return result;
    }

    private static HealthStateProbe.NumericKind numericKindOf(Class<?> type) {
        return HealthStateProbe.numericKindOf(type);
    }
}
