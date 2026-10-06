package com.wenxing.wenxingtools.util;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.event.EffectPurgeSupport;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CombatUtil {
    private CombatUtil() {}

    public static final float DIRECT_KILL_DAMAGE = 1.0e9f;
    private static final float DEFAULT_MAX_HEALTH = 20.0f;

    public static final ResourceKey<DamageType> INFINITY_KILL =
            ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(WenXingTools.MODID, "infinity_kill"));

    private static volatile MethodHandle DIE_HANDLE;
    private static volatile boolean DIE_HANDLE_RESOLVED;

    public static boolean isKillChainRunning() {
        return CombatReentry.isKillChainRunning();
    }

    public static boolean isNotValidAttackerPlayer(Entity entity) {
        if (entity == null) {
            return true;
        }
        if (!(entity instanceof ServerPlayer player)) {
            return true;
        }
        return player.connection == null;
    }

    /**
     * 解析玩家攻击者：兼容近战（causing=玩家）与 TACZ 等弹射物
     * （causing/direct 常为子弹本体，需回退 owner）。对齐 Forge 1.20.1 AmpSupport。
     */
    public static Player resolvePlayerAttacker(DamageSource source) {
        if (source == null) {
            return null;
        }
        if (source.getEntity() instanceof Player direct) {
            return direct;
        }
        if (source.getEntity() instanceof net.minecraft.world.entity.projectile.Projectile projectile
                && projectile.getOwner() instanceof Player owner) {
            return owner;
        }
        if (source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile projectile
                && projectile.getOwner() instanceof Player owner) {
            return owner;
        }
        return null;
    }

    public static float getMaxHealth(LivingEntity entity) {
        if (entity == null) {
            return DEFAULT_MAX_HEALTH;
        }
        float max = (float) entity.getAttributeValue(Attributes.MAX_HEALTH);
        return max > 0.0f ? max : DEFAULT_MAX_HEALTH;
    }

    /** 按实际扣血量回血；未掉血不回（修免费吸血）。净化窗内（含 255 核清窗）禁止直接改血。 */
    public static void healByActualDealt(Player player, float actualDealt) {
        if (player == null || !(actualDealt > 0.0f) || Float.isNaN(actualDealt)) {
            return;
        }
        // 禁疗须同时拦住 setHealth 旁路（LivingHealEvent 拦不住直接改血）
        if (EffectPurgeSupport.isSuppressed(player)) {
            return;
        }
        float playerMax = getMaxHealth(player);
        player.setHealth(Math.min(playerMax, player.getHealth() + actualDealt));
    }

    /**
     * 旁路真伤：优先走通用伤害上限旁路（分片 setHealth / 无钳 setter / GENERIC_KILL），
     * 兼容 Goety:Awaken 哞菇巨兽、无名者等单次伤害钳制目标；再降级 LM 等自定义血量通道。
     * 返回实际扣血量（getHealth 前后差，供吸血）。
     */
    public static float applyBypassTrueDamage(ServerPlayer attacker, LivingEntity target, float amount) {
        if (attacker == null || target == null || !(amount > 0.0f) || Float.isNaN(amount)) {
            return 0.0f;
        }
        if (!target.isAlive() && !(target.getHealth() > 0.0f)) {
            return 0.0f;
        }

        float before = target.getHealth();
        if (!(before > 0.0f) || Float.isNaN(before)) {
            return 0.0f;
        }

        // 0) 通用伤害上限旁路（分片扣满，不受单次 cap/冷却限制）
        float capAwareLost = DamageCapBypass.applyDamageCapAwareTrueDamage(attacker, target, amount);
        if (capAwareLost > 0.0f) {
            return capAwareLost;
        }

        // 1) setHealth 验证生效（只认真实掉血；空操作不得提前返回）
        float intended = Math.max(0.0f, before - amount);
        target.setHealth(intended);
        float after = target.getHealth();
        float hpLost = before - after;
        if (hpLost > 0.0f) {
            return hpLost;
        }

        // 2) 反射 addDamage / totalDamageTaken：invoke 后对比 getHealth
        DamageSource source = createInfinityKillSource(attacker);
        if (source == null) {
            source = attacker.damageSources().playerAttack(attacker);
        }
        ThirdPartyDamageAccess access = thirdPartyAccess(target.getClass());
        if (tryReflectThirdPartyDamage(target, amount, source, access)) {
            after = target.getHealth();
            float reflectLost = before - after;
            if (reflectLost > 0.0f) {
                return reflectLost;
            }
            // 仅字段写入（无 addDamage）且 getHealth 未同步时，按入账量计；
            // addDamage 调用过但无掉血则继续落到 hurt，避免假入账。
            if (access != null && access.addDamage == null && access.hasCustomHpMarker()) {
                return amount;
            }
        }

        // 3) 全穿透 hurt 兜底
        target.invulnerableTime = 0;
        target.hurtTime = 0;
        target.hurt(source, amount);
        after = target.getHealth();
        return Math.max(0.0f, before - after);
    }

    /** 第三方自定义血量旁路：addDamage(float/double[, DamageSource]) 或 totalDamageTaken 累加。失败静默。 */
    private static boolean tryReflectThirdPartyDamage(
            LivingEntity target, float amount, DamageSource source, ThirdPartyDamageAccess access) {
        if (access == null || access.isEmpty()) {
            return false;
        }
        try {
            if (access.addDamage != null) {
                if (access.addDamageNeedsSource) {
                    if (access.addDamageDoubleArg) {
                        access.addDamage.invoke(target, (double) amount, source);
                    } else {
                        access.addDamage.invoke(target, amount, source);
                    }
                } else if (access.addDamageDoubleArg) {
                    access.addDamage.invoke(target, (double) amount);
                } else {
                    access.addDamage.invoke(target, amount);
                }
                return true;
            }
            if (access.totalDamageTakenField != null) {
                Object current = access.totalDamageTakenField.get(target);
                double next;
                if (current instanceof Float f) {
                    next = f + amount;
                    access.totalDamageTakenField.set(target, (float) next);
                } else if (current instanceof Double d) {
                    next = d + amount;
                    access.totalDamageTakenField.set(target, next);
                } else if (current instanceof Integer i) {
                    next = i + amount;
                    access.totalDamageTakenField.set(target, (int) next);
                } else if (current instanceof Long l) {
                    next = l + amount;
                    access.totalDamageTakenField.set(target, (long) next);
                } else {
                    return false;
                }
                return true;
            }
        } catch (Throwable t) {
            return false;
        }
        return false;
    }

    private record ThirdPartyDamageAccess(
            MethodHandle addDamage,
            boolean addDamageNeedsSource,
            boolean addDamageDoubleArg,
            Field totalDamageTakenField) {
        static final ThirdPartyDamageAccess NONE = new ThirdPartyDamageAccess(null, false, false, null);

        boolean isEmpty() {
            return addDamage == null && totalDamageTakenField == null;
        }

        /** 第三方血量标记：存在 totalDamageTaken 类累计伤害字段（LM 等自定义血量）。 */
        boolean hasCustomHpMarker() {
            return totalDamageTakenField != null;
        }
    }

    private static final Map<Class<?>, ThirdPartyDamageAccess> THIRD_PARTY_DAMAGE_CACHE = new ConcurrentHashMap<>();

    private static ThirdPartyDamageAccess thirdPartyAccess(Class<?> type) {
        return THIRD_PARTY_DAMAGE_CACHE.computeIfAbsent(type, CombatUtil::resolveThirdPartyDamageAccess);
    }

    private static ThirdPartyDamageAccess resolveThirdPartyDamageAccess(Class<?> type) {
        MethodHandle addDamage = null;
        boolean addDamageNeedsSource = false;
        boolean addDamageDoubleArg = false;
        Field totalField = null;
        Class<?> cursor = type;
        while (cursor != null && cursor != Object.class && cursor != LivingEntity.class) {
            if (addDamage == null) {
                AddDamageHandle resolved = resolveAddDamageHandle(cursor);
                if (resolved != null) {
                    addDamage = resolved.handle;
                    addDamageNeedsSource = resolved.needsSource;
                    addDamageDoubleArg = resolved.doubleArg;
                }
            }
            if (totalField == null) {
                totalField = resolveField(cursor, "totalDamageTaken");
            }
            if (addDamage == null) {
                AddDamageHandle resolved = resolveAddDamageHandleInInterfaces(cursor);
                if (resolved != null) {
                    addDamage = resolved.handle;
                    addDamageNeedsSource = resolved.needsSource;
                    addDamageDoubleArg = resolved.doubleArg;
                }
            }
            if (addDamage != null && totalField != null) {
                break;
            }
            cursor = cursor.getSuperclass();
        }
        if (addDamage == null && totalField == null) {
            return ThirdPartyDamageAccess.NONE;
        }
        return new ThirdPartyDamageAccess(addDamage, addDamageNeedsSource, addDamageDoubleArg, totalField);
    }

    private record AddDamageHandle(MethodHandle handle, boolean needsSource, boolean doubleArg) {}

    private static AddDamageHandle resolveAddDamageHandleInInterfaces(Class<?> type) {
        for (Class<?> iface : type.getInterfaces()) {
            AddDamageHandle handle = resolveAddDamageHandle(iface);
            if (handle != null) {
                return handle;
            }
            handle = resolveAddDamageHandleInInterfaces(iface);
            if (handle != null) {
                return handle;
            }
        }
        return null;
    }

    /** 兼容 LM 双参 addDamage(float, DamageSource) 与常见单参 addDamage(float/double)。 */
    private static AddDamageHandle resolveAddDamageHandle(Class<?> type) {
        String[] names = {"addDamage", "totalDamageTaken"};
        Class<?>[] floatSrc = {float.class, DamageSource.class};
        Class<?>[] doubleSrc = {double.class, DamageSource.class};
        Class<?>[] oneFloat = {float.class};
        Class<?>[] oneDouble = {double.class};
        for (String name : names) {
            MethodHandle handle = unreflectMethod(type, name, floatSrc);
            if (handle != null) {
                return new AddDamageHandle(handle, true, false);
            }
            handle = unreflectMethod(type, name, doubleSrc);
            if (handle != null) {
                return new AddDamageHandle(handle, true, true);
            }
            handle = unreflectMethod(type, name, oneFloat);
            if (handle != null) {
                return new AddDamageHandle(handle, false, false);
            }
            handle = unreflectMethod(type, name, oneDouble);
            if (handle != null) {
                return new AddDamageHandle(handle, false, true);
            }
        }
        return null;
    }

    private static MethodHandle unreflectMethod(Class<?> type, String name, Class<?>[] signature) {
        try {
            Method method = type.getDeclaredMethod(name, signature);
            method.setAccessible(true);
            return MethodHandles.lookup().unreflect(method);
        } catch (Exception e) {
            return null;
        }
    }

    private static Field resolveField(Class<?> type, String name) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (Exception e) {
            return null;
        }
    }

    public static void anchorPlayerHealthByCommand(ServerPlayer player) {
        if (isNotValidAttackerPlayer(player)) {
            return;
        }

        float maxHealth = getMaxHealth(player);
        float health = player.getHealth();
        if (Float.isNaN(health) || Float.isInfinite(health) || health <= 0.0f) {
            health = maxHealth;
        }
        if (health > maxHealth) {
            health = maxHealth;
        }
        if (health < 0.0f) {
            health = 0.0f;
        }
        player.setHealth(health);
    }

    public static DamageSource createInfinityKillSource(ServerPlayer attacker) {
        if (attacker == null) {
            return null;
        }
        ServerLevel level = attacker.serverLevel();
        try {
            var holder = level.registryAccess()
                    .registryOrThrow(Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(INFINITY_KILL);
            // 必杀伤害源：自带虚空归属（is_out_of_world / always_most_significant_fatal），并保留攻击者为实体
            return new DamageSource(holder, attacker);
        } catch (Exception e) {
            WenXingTools.LOGGER.warn("[wenxingtools] infinity_kill DamageType missing, fallback to playerAttack: {}", e.getMessage());
            return attacker.damageSources().playerAttack(attacker);
        }
    }

    /**
     * 原版虚空伤害注册名（minecraft:out_of_world）。按注册名解析，兼容映射字段差异。
     */
    public static final ResourceKey<DamageType> VANILLA_OUT_OF_WORLD =
            ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("minecraft", "out_of_world"));

    /** 原版虚空伤害源（minecraft:out_of_world），用于必杀链的补充结算；保留攻击者实体以便击杀归属。 */
    public static DamageSource createVoidKillSource(ServerPlayer attacker) {
        return createVanillaOutOfWorldSource(attacker);
    }

    /**
     * 构建带玩家归属的原版虚空伤害源（minecraft:out_of_world）。
     * 用于净化持续窗受击改写 / 回响 / 必杀链补充；击杀归属、掉落与经验归玩家。
     */
    public static DamageSource createVanillaOutOfWorldSource(ServerPlayer attacker) {
        if (attacker == null) {
            return null;
        }
        return attacker.damageSources().source(DamageTypes.FELL_OUT_OF_WORLD, attacker);
    }

    /**
     * 构建无归属的原版虚空伤害源（minecraft:out_of_world）。
     * 用于净化持续窗内非玩家攻击者的伤害改写。
     */
    public static DamageSource createVanillaOutOfWorldSource(net.minecraft.world.level.Level level) {
        if (level == null) {
            return null;
        }
        try {
            var holder = level.registryAccess()
                    .registryOrThrow(Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(VANILLA_OUT_OF_WORLD);
            return new DamageSource(holder);
        } catch (Exception e) {
            WenXingTools.LOGGER.warn("[wenxingtools] plain out_of_world DamageType missing: {}", e.getMessage());
            return null;
        }
    }

    /** 是否已是原版虚空伤害（避免净化转虚空时二次改写）。 */
    public static boolean isVanillaOutOfWorld(DamageSource source) {
        if (source == null) {
            return false;
        }
        try {
            return source.typeHolder().is(VANILLA_OUT_OF_WORLD);
        } catch (Exception e) {
            return "outOfWorld".equals(source.getMsgId()) || "out_of_world".equals(source.getMsgId());
        }
    }

    public static void applyDirectKill(ServerPlayer damageSourcePlayer, LivingEntity target) {
        if (damageSourcePlayer == null || target == null) {
            return;
        }
        if (isNotValidAttackerPlayer(damageSourcePlayer)) {
            return;
        }
        if (target.isRemoved()) {
            return;
        }
        if (target.isDeadOrDying() && target.getHealth() <= 0.0f && target.deathTime > 0) {
            return;
        }
        if (CombatReentry.isKillChainRunning()
                || CombatReentry.isEchoChainRunning()
                || CombatReentry.isAttackAmpRunning()) {
            return;
        }

        CombatReentry.runKillChain(() -> {
            target.removeEffect(MobEffects.DAMAGE_RESISTANCE);
            target.removeEffect(MobEffects.ABSORPTION);
            target.removeEffect(MobEffects.REGENERATION);
            target.removeEffect(MobEffects.FIRE_RESISTANCE);

            target.invulnerableTime = 0;
            target.hurtTime = 0;

            DamageSource source = createInfinityKillSource(damageSourcePlayer);
            if (source == null) {
                return;
            }

            float damageAmount = target.getHealth();
            if (!(damageAmount > 0.0f) || Float.isNaN(damageAmount)) {
                damageAmount = getMaxHealth(target);
            }
            target.invulnerableTime = 0;
            target.hurtTime = 0;
            target.hurt(source, damageAmount);

            // 补充：原版虚空伤害（out_of_world），用于压过残留保护/特殊免死逻辑
            if (target.getHealth() > 0.0f || !target.isDeadOrDying()) {
                DamageSource voidSource = createVoidKillSource(damageSourcePlayer);
                if (voidSource != null) {
                    target.removeEffect(MobEffects.DAMAGE_RESISTANCE);
                    target.removeEffect(MobEffects.ABSORPTION);
                    target.removeEffect(MobEffects.REGENERATION);
                    target.removeEffect(MobEffects.FIRE_RESISTANCE);
                    target.invulnerableTime = 0;
                    target.hurtTime = 0;
                    float voidAmount = Math.max(damageAmount, DIRECT_KILL_DAMAGE);
                    target.hurt(voidSource, voidAmount);
                }
            }

            if (target.getHealth() > 0.0f || !target.isDeadOrDying()) {
                // 通用清空：分片压血，兼容 setHealth 单次钳制（如哞菇巨兽）
                DamageCapBypass.forceReduceHealthToZero(damageSourcePlayer, target);
                if (target.getHealth() > 0.0f) {
                    target.setHealth(0.0F);
                }
                invokeDie(target, source);
            }
        });
    }

    public static void forceKillEntityByCommandChain(MinecraftServer server, LivingEntity target, ServerPlayer damageSourcePlayer) {
        if (target == null || target.isRemoved()) {
            return;
        }
        if (target.level().isClientSide) {
            return;
        }
        if (CombatReentry.isKillChainRunning()
                || CombatReentry.isEchoChainRunning()
                || CombatReentry.isAttackAmpRunning()) {
            return;
        }
        if (damageSourcePlayer == null || isNotValidAttackerPlayer(damageSourcePlayer)) {
            return;
        }
        if (!PermissionUtil.hasPermission(damageSourcePlayer)) {
            return;
        }
        if (target.isDeadOrDying() && target.deathTime > 0) {
            return;
        }

        applyDirectKill(damageSourcePlayer, target);

        // 最多重试一次，避免重复触发掉落/死亡逻辑
        if (target.getHealth() > 0.0f && !target.isRemoved() && !(target.isDeadOrDying() && target.deathTime > 0)) {
            if (WenXingTools.LOGGER.isDebugEnabled()) {
                WenXingTools.LOGGER.debug(
                        "[wenxingtools] kill retry for {} (hp={})",
                        target.getType().getDescriptionId(),
                        target.getHealth());
            }
            applyDirectKill(damageSourcePlayer, target);
        }

        if (target.getHealth() > 0.0f && !target.isRemoved() && target.isAlive()
                && !(target.isDeadOrDying() && target.deathTime > 0)) {
            WenXingTools.LOGGER.warn(
                    "[wenxingtools] kill failed for {} (hp={}, removed={}, deadOrDying={})",
                    target.getType().getDescriptionId(),
                    target.getHealth(),
                    target.isRemoved(),
                    target.isDeadOrDying());
        }
    }

    private static void invokeDie(LivingEntity target, DamageSource source) {
        MethodHandle handle = resolveDieHandle();
        if (handle == null) {
            WenXingTools.LOGGER.debug("[wenxingtools] die() handle unavailable, rely on hurt()");
            return;
        }
        try {
            handle.invoke(target, source);
        } catch (Throwable t) {
            WenXingTools.LOGGER.warn("[wenxingtools] CombatUtil die() invoke failed: {}", t.toString());
        }
    }

    private static MethodHandle resolveDieHandle() {
        if (DIE_HANDLE_RESOLVED) {
            return DIE_HANDLE;
        }
        synchronized (CombatUtil.class) {
            if (DIE_HANDLE_RESOLVED) {
                return DIE_HANDLE;
            }
            try {
                Method method = LivingEntity.class.getDeclaredMethod("die", DamageSource.class);
                method.setAccessible(true);
                DIE_HANDLE = MethodHandles.lookup().unreflect(method);
            } catch (Exception e) {
                WenXingTools.LOGGER.warn("[wenxingtools] failed to resolve LivingEntity.die: {}", e.getMessage());
                DIE_HANDLE = null;
            }
            DIE_HANDLE_RESOLVED = true;
            return DIE_HANDLE;
        }
    }
}
