package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.util.CombatUtil;
import com.wenxing.wenxingtools.util.DamageCapBypass;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 攻击增幅：按最大生命百分比造成旁路真伤。
 * <p>
 * 对单次伤害钳制目标（Goety:Awaken 哞菇巨兽 setHealth 25% 钳制、
 * 无名者 hurt 20% 上限+命中冷却），经 {@link com.wenxing.wenxingtools.util.DamageCapBypass}
 * 分片扣满目标百分比，不再被削成上限值。
 * <p>
 * 入账通道互斥，避免叠伤：
 * <ol>
 *   <li>通用伤害上限旁路（分片 setHealth / 无钳 setter / GENERIC_KILL）</li>
 *   <li>{@code setHealth} 验证生效（原版血量快路径）</li>
 *   <li>反射 {@code addDamage}/{@code totalDamageTaken}（LM 等自定义血量）</li>
 *   <li>全穿透 {@code hurt} 兜底</li>
 *   <li>以上全失败时，并入 {@code LivingHurtEvent} 金额，走目标自带伤害通道</li>
 * </ol>
 */
@Mod.EventBusSubscriber(modid = WenXingTools.MODID)
public final class AttackAmplificationEvents {

    private static final ThreadLocal<Boolean> ATTACK_AMP_RUNNING = ThreadLocal.withInitial(() -> false);

    public static boolean isAttackAmpRunning() {
        return ATTACK_AMP_RUNNING.get();
    }

    private record AddDamageAccessor(Method method, boolean needsSource, boolean doubleArg) {
        float invoke(LivingEntity target, float amount, DamageSource source) throws Throwable {
            if (needsSource) {
                if (doubleArg) {
                    method.invoke(target, (double) amount, source);
                } else {
                    method.invoke(target, amount, source);
                }
            } else if (doubleArg) {
                method.invoke(target, (double) amount);
            } else {
                method.invoke(target, amount);
            }
            return amount;
        }
    }

    private static final ConcurrentMap<Class<?>, Optional<AddDamageAccessor>> ADD_DAMAGE_CACHE = new ConcurrentHashMap<>();

    private static final ConcurrentMap<Class<?>, Optional<Field>> TOTAL_DAMAGE_TAKEN_CACHE = new ConcurrentHashMap<>();

    private AttackAmplificationEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurtForAttackAmplification(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (event.getAmount() <= 0.0f) {
            return;
        }

        if (CombatUtil.isKillChainRunning()
                || EchoAmplificationEvents.isEchoChainRunning()
                || ATTACK_AMP_RUNNING.get()) {
            return;
        }

        Player player = AmpSupport.resolvePlayerAttacker(event.getSource());
        if (player == null || CombatUtil.isNotValidAttackerPlayer(player)) {
            return;
        }

        LivingEntity target = event.getEntity();
        if (target instanceof Player) {
            return;
        }
        if (!target.isAlive()) {
            return;
        }
        if (!CombatUtil.tryEnterAttackAmpHit(target)) {
            return;
        }

        int attackLevel = AmpSupport.resolveAttackAmplificationLevel(player);
        if (attackLevel > 0) {
            applyAttackAmplificationTrueDamage(player, target, attackLevel, event);
        }
    }

    private static void applyAttackAmplificationTrueDamage(Player player, LivingEntity target, int level, LivingHurtEvent event) {
        if (ATTACK_AMP_RUNNING.get()) {
            return;
        }
        ATTACK_AMP_RUNNING.set(true);
        try {
            float percentage = (level * AmpConstants.PERCENT_PER_LEVEL) / 100.0f;
            float percentDamage = target.getMaxHealth() * percentage;
            if (!(percentDamage > 0.0f) || Float.isNaN(percentDamage)) {
                return;
            }

            float healthBefore = target.getHealth();
            if (healthBefore <= 0.0f) {
                return;
            }

            target.invulnerableTime = 0;
            target.hurtTime = 0;

            if (healthBefore > percentDamage) {
                float dealt = applyBypassTrueDamage(player, target, percentDamage);
                if (dealt > 0.0f) {
                    healAttacker(player, dealt);
                } else {
                    foldIntoEvent(event, percentDamage);
                    healAttacker(player, percentDamage);
                }
            } else {
                float leftover = Math.max(0.0f, percentDamage - healthBefore);
                float dealt = 0.0f;
                if (player instanceof ServerPlayer sp) {
                    float healthBeforeKill = target.getHealth();
                    CombatUtil.applyDirectKill(sp, target);
                    if (target.isDeadOrDying() || target.isRemoved() || target.getHealth() <= 0.0f) {
                        dealt = healthBeforeKill;
                    } else {
                        dealt = applyBypassTrueDamage(player, target, percentDamage);
                        if (dealt <= 0.0f) {
                            foldIntoEvent(event, percentDamage);
                            dealt = percentDamage;
                        }
                    }
                } else {
                    dealt = applyBypassTrueDamage(player, target, percentDamage);
                    if (dealt <= 0.0f) {
                        foldIntoEvent(event, percentDamage);
                        dealt = percentDamage;
                    }
                }
                if (dealt > 0.0f) {
                    healAttacker(player, dealt + leftover);
                }
            }
        } finally {
            ATTACK_AMP_RUNNING.set(false);
        }
    }

    /** 旁路全失败：把百分比并入事件金额，由 LM addDamage / 原版 actuallyHurt 入账。与旁路真伤互斥。 */
    private static void foldIntoEvent(LivingHurtEvent event, float percentDamage) {
        if (event == null || !(percentDamage > 0.0f) || event.isCanceled()) {
            return;
        }
        float current = event.getAmount();
        if (current <= 0.0f) {
            return;
        }
        event.setAmount(current + percentDamage);
    }

    /**
     * 旁路真伤：优先通用伤害上限旁路（分片 setHealth / 无钳 setter / GENERIC_KILL），
     * 兼容 Goety:Awaken 哞菇巨兽、无名者等单次伤害钳制；再降级 LM 等自定义血量通道。
     */
    private static float applyBypassTrueDamage(Player player, LivingEntity target, float amount) {
        if (!(amount > 0.0f) || !target.isAlive()) {
            return 0.0f;
        }

        float before = target.getHealth();
        if (before <= 0.0f) {
            return 0.0f;
        }

        // 0) 通用伤害上限旁路（分片扣满，不受单次 cap/冷却限制）
        float capAwareLost = DamageCapBypass.applyDamageCapAwareTrueDamage(player, target, amount);
        if (capAwareLost > 0.0f) {
            return capAwareLost;
        }

        // 1) setHealth 验证生效（LM 自定义血量在服务端是空操作）
        float desired = Math.max(0.0f, before - amount);
        target.setHealth(desired);
        float after = target.getHealth();
        float hpLost = before - after;
        if (hpLost > 1.0e-4f) {
            return hpLost;
        }

        // 2) 反射 addDamage / totalDamageTaken
        DamageSource source = resolveBypassSource(player);
        AddDamageAccessor addDamage = resolveAddDamageAccessor(target.getClass());
        if (addDamage != null && source != null) {
            try {
                addDamage.invoke(target, amount, source);
                after = target.getHealth();
                float reflectLost = before - after;
                if (reflectLost > 1.0e-4f) {
                    return reflectLost;
                }
            } catch (Throwable ignored) {
                // 落到字段 / hurt
            }
        }

        Float fieldDealt = applyTotalDamageTakenField(target, amount);
        if (fieldDealt != null && fieldDealt > 1.0e-4f) {
            after = target.getHealth();
            if (after < before - 1.0e-4f) {
                return before - after;
            }
            return fieldDealt;
        }

        // 3) 全穿透 hurt 兜底
        if (source != null) {
            float beforeHurt = target.getHealth();
            target.invulnerableTime = 0;
            target.hurtTime = 0;
            target.hurt(source, amount);
            after = target.getHealth();
            if (after < beforeHurt - 1.0e-4f) {
                return beforeHurt - after;
            }
        }
        return 0.0f;
    }

    private static Float applyTotalDamageTakenField(LivingEntity target, float amount) {
        Field field = resolveTotalDamageTakenField(target.getClass());
        if (field == null) {
            return null;
        }
        try {
            Object current = field.get(target);
            if (current instanceof Float f) {
                float max = target.getMaxHealth();
                float next = Math.min(max, f + amount);
                if (!(next > f)) {
                    return 0.0f;
                }
                field.set(target, next);
                target.setHealth(Math.max(0.0f, max - next));
                return next - f;
            }
            if (current instanceof Double d) {
                double max = target.getMaxHealth();
                double next = Math.min(max, d + amount);
                if (!(next > d)) {
                    return 0.0f;
                }
                field.set(target, next);
                target.setHealth((float) Math.max(0.0f, max - next));
                return (float) (next - d);
            }
        } catch (Throwable ignored) {
            // 无字段或不可写
        }
        return null;
    }

    private static AddDamageAccessor resolveAddDamageAccessor(Class<?> type) {
        return ADD_DAMAGE_CACHE.computeIfAbsent(type, t -> {
            Class<?> c = t;
            while (c != null && c != Object.class) {
                AddDamageAccessor found = tryAddDamageSignature(c, float.class, DamageSource.class, true, false);
                if (found == null) {
                    found = tryAddDamageSignature(c, double.class, DamageSource.class, true, true);
                }
                if (found == null) {
                    found = tryAddDamageSignature(c, float.class, null, false, false);
                }
                if (found == null) {
                    found = tryAddDamageSignature(c, double.class, null, false, true);
                }
                if (found != null) {
                    return Optional.of(found);
                }
                c = c.getSuperclass();
            }
            return Optional.empty();
        }).orElse(null);
    }

    private static AddDamageAccessor tryAddDamageSignature(
            Class<?> type, Class<?> argType, Class<?> secondType, boolean needsSource, boolean doubleArg) {
        try {
            Method m = secondType == null
                    ? type.getDeclaredMethod("addDamage", argType)
                    : type.getDeclaredMethod("addDamage", argType, secondType);
            m.setAccessible(true);
            return new AddDamageAccessor(m, needsSource, doubleArg);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Field resolveTotalDamageTakenField(Class<?> type) {
        return TOTAL_DAMAGE_TAKEN_CACHE.computeIfAbsent(type, t -> {
            Class<?> c = t;
            while (c != null && c != Object.class) {
                try {
                    Field f = c.getDeclaredField("totalDamageTaken");
                    f.setAccessible(true);
                    return Optional.of(f);
                } catch (NoSuchFieldException ignored) {
                    c = c.getSuperclass();
                }
            }
            return Optional.empty();
        }).orElse(null);
    }

    private static DamageSource resolveBypassSource(Player player) {
        if (player instanceof ServerPlayer sp) {
            return CombatUtil.createInfinityKillSource(sp);
        }
        return player.damageSources().playerAttack(player);
    }

    private static void healAttacker(Player player, float amount) {
        if (!(amount > 0.0f) || !player.isAlive()) {
            return;
        }
        player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + amount));
    }
}
