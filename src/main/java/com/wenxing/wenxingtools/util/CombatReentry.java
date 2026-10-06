package com.wenxing.wenxingtools.util;

import net.minecraft.world.entity.LivingEntity;

import java.util.HashSet;
import java.util.Set;

/**
 * 战斗/增幅路径防重入与掉落上下文，统一 ThreadLocal 收口。
 * 工具类：无事件监听，勿加 @EventBusSubscriber。
 */
public final class CombatReentry {
    private CombatReentry() {}

    private static final ThreadLocal<Boolean> KILL_CHAIN = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> ATTACK_AMP = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> ECHO_CHAIN = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> POTION_AMP = ThreadLocal.withInitial(() -> false);
    /** 净化持续窗虚空伤害改写防重入（计数型，支持嵌套）。 */
    private static final ThreadLocal<java.util.concurrent.atomic.AtomicInteger> VOID_CONVERT_DEPTH =
            ThreadLocal.withInitial(java.util.concurrent.atomic.AtomicInteger::new);
    private static final ThreadLocal<Set<LivingEntity>> RESOURCE_DROP = ThreadLocal.withInitial(HashSet::new);
    private static final ThreadLocal<Set<LivingEntity>> RESOURCE_XP = ThreadLocal.withInitial(HashSet::new);
    private static final java.util.Map<java.util.UUID, Long> ATTACK_AMP_HIT_AT = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<java.util.UUID, Long> ECHO_HIT_AT = new java.util.concurrent.ConcurrentHashMap<>();

    public static boolean isKillChainRunning() {
        return KILL_CHAIN.get();
    }

    public static void runKillChain(Runnable action) {
        runFlag(KILL_CHAIN, action);
    }

    public static boolean isAttackAmpRunning() {
        return ATTACK_AMP.get();
    }

    public static void runAttackAmp(Runnable action) {
        runFlag(ATTACK_AMP, action);
    }

    public static boolean isEchoChainRunning() {
        return ECHO_CHAIN.get();
    }

    public static void runEchoChain(Runnable action) {
        runFlag(ECHO_CHAIN, action);
    }

    public static boolean isPotionAmpRunning() {
        return POTION_AMP.get();
    }

    public static void runPotionAmp(Runnable action) {
        runFlag(POTION_AMP, action);
    }

    public static boolean isVoidConverting() {
        return VOID_CONVERT_DEPTH.get().get() > 0;
    }

    public static void setVoidConverting(boolean converting) {
        java.util.concurrent.atomic.AtomicInteger depth = VOID_CONVERT_DEPTH.get();
        if (converting) {
            depth.incrementAndGet();
        } else {
            depth.updateAndGet(v -> v > 0 ? v - 1 : 0);
        }
    }

    /** 同一目标每 game tick 只允许一次攻击增幅命中结算（连射防级联）。 */
    public static boolean tryEnterAttackAmpHit(LivingEntity target) {
        return tryEnterTickGate(ATTACK_AMP_HIT_AT, target);
    }

    /** 同一目标每 game tick 只允许一次回响命中结算（连射防级联）。 */
    public static boolean tryEnterEchoHit(LivingEntity target) {
        return tryEnterTickGate(ECHO_HIT_AT, target);
    }

    private static boolean tryEnterTickGate(java.util.Map<java.util.UUID, Long> gate, LivingEntity target) {
        if (target == null) {
            return false;
        }
        long now = target.level().getGameTime();
        java.util.UUID id = target.getUUID();
        Long previous = gate.put(id, now);
        if (gate.size() > 256) {
            gate.entrySet().removeIf(e -> e.getValue() < now - 1L);
        }
        return previous == null || previous.longValue() != now;
    }

    /** 资源掉落防重入：进入成功返回 true，finally 须调用 {@link #exitResourceDrop}。 */
    public static boolean enterResourceDrop(LivingEntity entity) {
        return RESOURCE_DROP.get().add(entity);
    }

    public static void exitResourceDrop(LivingEntity entity) {
        exitContext(RESOURCE_DROP, entity);
    }

    public static boolean enterResourceXp(LivingEntity entity) {
        return RESOURCE_XP.get().add(entity);
    }

    public static void exitResourceXp(LivingEntity entity) {
        exitContext(RESOURCE_XP, entity);
    }

    private static void runFlag(ThreadLocal<Boolean> flag, Runnable action) {
        if (flag.get()) {
            return;
        }
        flag.set(true);
        try {
            action.run();
        } finally {
            flag.set(false);
        }
    }

    private static void exitContext(ThreadLocal<Set<LivingEntity>> holder, LivingEntity entity) {
        Set<LivingEntity> set = holder.get();
        if (set != null) {
            set.remove(entity);
            if (set.isEmpty()) {
                holder.remove();
            }
        }
    }
}
