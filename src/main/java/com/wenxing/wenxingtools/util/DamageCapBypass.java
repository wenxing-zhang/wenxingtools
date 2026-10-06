package com.wenxing.wenxingtools.util;

import com.wenxing.wenxingtools.WenXingTools;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通用「单次伤害上限 / 伤害冷却」旁路真伤。
 *
 * <p>Goety:Awaken 等模组会在 {@code setHealth}/{@code hurt} 上钳制单次入账：
 * 哞菇巨兽 {@code setHealth} 按最大生命 25% 钳制、{@code hurt}/{@code actuallyHurt} 二次钳制；
 * 无名者 {@code hurt} 20% 上限 + 命中冷却 + 动态减伤，仅 {@code GENERIC_KILL} / 创造免钳。
 * 「按最大生命百分比」的真伤会被削成上限值甚至 0，折叠进事件后仍走被钳制的 {@code hurt}。</p>
 *
 * <p>策略（按序降级，任一级真实掉血即累计）：</p>
 * <ol>
 *   <li>分片 {@code setHealth}：单次被钳制则继续扣下一片，直至扣满目标量</li>
 *   <li>反射无钳制 setter（{@code setHpoint}/{@code setVanillaHealth}/{@code setCombatHealth} 等）</li>
 *   <li>清伤害冷却字段后，以 {@code GENERIC_KILL} + 玩家归属 {@code hurt} 兜底</li>
 * </ol>
 */
public final class DamageCapBypass {
    private DamageCapBypass() {}

    private static final float EPS = 1.0e-4f;
    /** 单次结算最多分片数，防止病态钳制死循环。 */
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

    /**
     * 按可入账上限分片扣血，返回实际扣血量（getHealth 前后差）。
     * 不改事件金额、不叠伤；未掉血返回 0。
     */
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

        // 1) 分片 setHealth：适配 setHealth 钳制型（哞菇巨兽）
        totalDealt += drainViaChunkedSetHealth(target, remaining);
        remaining = Math.max(0.0f, amount - totalDealt);
        if (remaining <= EPS || !target.isAlive()) {
            return totalDealt;
        }

        // 2) 无钳制 setter 反射（setHpoint 等内部旁路）
        totalDealt += drainViaUnclampedSetters(target, remaining);
        remaining = Math.max(0.0f, amount - totalDealt);
        if (remaining <= EPS || !target.isAlive()) {
            return totalDealt;
        }

        // 3) GENERIC_KILL hurt 兜底（无名者对 GENERIC_KILL 免钳）
        totalDealt += drainViaGenericKillHurt(attacker, target, remaining);
        return totalDealt;
    }

    /** 将目标血量压到 0（击杀路径）。尊重单次钳制，分片清空。返回是否已空血。 */
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
            // 最终硬清：部分自定义血量只信 setHealth(0) 分片后的残值
            target.setHealth(0.0f);
        }
        return target.getHealth() <= 0.0f || target.isDeadOrDying();
    }

    private static float drainViaChunkedSetHealth(LivingEntity target, float amount) {
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
            target.setHealth(intended);
            float after = target.getHealth();
            if (Float.isNaN(after)) {
                break;
            }
            float lost = before - after;
            if (lost <= EPS) {
                // 单次钳制为 0 或 setHealth 空操作：交给下一级
                break;
            }
            // 丢弃超额（防御性）：不超过本片请求
            if (lost > chunk + EPS) {
                lost = chunk;
            }
            totalDealt += lost;
            remaining -= lost;
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
            boolean invoked = false;
            for (UnclampedSetter setter : setters) {
                try {
                    setter.invoke(target, intended);
                    invoked = true;
                } catch (Throwable ignored) {
                    // 尝试下一个 setter
                }
                float after = target.getHealth();
                if (Float.isNaN(after)) {
                    continue;
                }
                float lost = before - after;
                if (lost > EPS) {
                    if (lost > chunk + EPS) {
                        lost = chunk;
                    }
                    totalDealt += lost;
                    remaining -= lost;
                    break;
                }
            }
            if (!invoked) {
                break;
            }
            // 全部 setter 空操作则退出，避免死循环
            if (target.getHealth() >= before - EPS) {
                break;
            }
        }
        return totalDealt;
    }

    private static float drainViaGenericKillHurt(Player attacker, LivingEntity target, float amount) {
        if (!(amount > 0.0f) || !target.isAlive()) {
            return 0.0f;
        }
        DamageSource source = createGenericKillSource(attacker, target);
        if (source == null) {
            return 0.0f;
        }
        clearDamageCooldowns(target);

        float totalDealt = 0.0f;
        float remaining = amount;
        int guard = MAX_CHUNKS;
        while (remaining > EPS && guard-- > 0 && target.isAlive()) {
            float before = target.getHealth();
            if (!(before > 0.0f) || Float.isNaN(before)) {
                break;
            }
            float chunk = Math.min(remaining, before);
            clearDamageCooldowns(target);
            target.invulnerableTime = 0;
            target.hurtTime = 0;
            target.hurt(source, chunk);
            float after = target.getHealth();
            if (Float.isNaN(after)) {
                break;
            }
            float lost = before - after;
            if (lost <= EPS) {
                break;
            }
            if (lost > chunk + EPS) {
                lost = chunk;
            }
            totalDealt += lost;
            remaining -= lost;
        }
        return totalDealt;
    }

    /**
     * GENERIC_KILL 玩家归属伤害源。无名者对
     * {@code BYPASSES_INVULNERABILITY + GENERIC_KILL} 放行全额；保留玩家实体以便击杀归属。
     */
    public static DamageSource createGenericKillSource(Player attacker, LivingEntity target) {
        try {
            if (attacker instanceof net.minecraft.server.level.ServerPlayer sp) {
                return sp.damageSources().source(DamageTypes.GENERIC_KILL, sp);
            }
            if (attacker != null) {
                return attacker.damageSources().source(DamageTypes.GENERIC_KILL, attacker);
            }
            if (target != null) {
                return target.damageSources().genericKill();
            }
        } catch (Throwable t) {
            WenXingTools.LOGGER.debug("[damage_cap_bypass] generic_kill source failed: {}", t.toString());
        }
        return null;
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
                // 只读或不可写：跳过
            }
        }
    }

    private record UnclampedSetter(Method method) {
        void invoke(LivingEntity target, float health) throws Throwable {
            method.invoke(target, health);
        }
    }

    private static UnclampedSetter[] settersFor(Class<?> type) {
        return SETTER_CACHE.computeIfAbsent(type, t -> {
            java.util.List<UnclampedSetter> found = new java.util.ArrayList<>();
            Class<?> c = t;
            while (c != null && c != Object.class && c != LivingEntity.class) {
                for (String name : UNCLAMPED_SETTER_NAMES) {
                    try {
                        Method m = c.getDeclaredMethod(name, float.class);
                        if (Modifier.isStatic(m.getModifiers())) {
                            continue;
                        }
                        m.setAccessible(true);
                        found.add(new UnclampedSetter(m));
                    } catch (NoSuchMethodException ignored) {
                        // 下一名称
                    }
                }
                c = c.getSuperclass();
            }
            return found.toArray(new UnclampedSetter[0]);
        });
    }

    private static Field[] cooldownFieldsFor(Class<?> type) {
        return COOLDOWN_CACHE.computeIfAbsent(type, t -> {
            java.util.List<Field> found = new java.util.ArrayList<>();
            Class<?> c = t;
            while (c != null && c != Object.class && c != LivingEntity.class) {
                for (String name : COOLDOWN_FIELD_NAMES) {
                    try {
                        Field f = c.getDeclaredField(name);
                        if (Modifier.isStatic(f.getModifiers())) {
                            continue;
                        }
                        f.setAccessible(true);
                        found.add(f);
                    } catch (NoSuchFieldException ignored) {
                        // 下一名称
                    }
                }
                c = c.getSuperclass();
            }
            return found.toArray(new Field[0]);
        });
    }
}
