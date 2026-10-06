package com.wenxing.wenxingtools.util;

import com.wenxing.wenxingtools.WenXingTools;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class CombatUtil {
    private CombatUtil() {}

    public static final float DIRECT_KILL_DAMAGE = 1.0e9f;
    private static final float DEFAULT_MAX_HEALTH = 20.0f;


    public static final ResourceKey<DamageType> INFINITY_KILL =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(WenXingTools.MODID, "infinity_kill"));


    public static final ResourceKey<DamageType> VANILLA_OUT_OF_WORLD =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("minecraft", "out_of_world"));

    private static final ThreadLocal<Boolean> KILL_CHAIN_RUNNING = ThreadLocal.withInitial(() -> false);

    public static boolean isKillChainRunning() {
        return KILL_CHAIN_RUNNING.get();
    }

    public static boolean isNotValidAttackerPlayer(Entity entity) {
        if (entity == null) return true;
        if (!(entity instanceof ServerPlayer player)) return true;
        return player.connection == null;
    }

    private static final java.util.Map<java.util.UUID, Long> ATTACK_AMP_HIT_AT = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<java.util.UUID, Long> ECHO_HIT_AT = new java.util.concurrent.ConcurrentHashMap<>();

    public static boolean tryEnterAttackAmpHit(LivingEntity target) {
        return tryEnterTickGate(ATTACK_AMP_HIT_AT, target);
    }

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

    public static void anchorPlayerHealthByCommand(ServerPlayer player) {
        if (isNotValidAttackerPlayer(player)) return;

        float maxHealth = player.getMaxHealth();
        if (maxHealth <= 0.0f) maxHealth = DEFAULT_MAX_HEALTH;

        float health = player.getHealth();
        if (Float.isNaN(health) || Float.isInfinite(health) || health <= 0.0f) {
            health = maxHealth;
        }
        if (health > maxHealth) health = maxHealth;
        if (health < 0.0f) health = 0.0f;

        player.setHealth(health);
    }


    private static DamageSource createKillSourceWithPlayer(ServerPlayer attacker, ResourceKey<DamageType> typeKey) {
        if (attacker == null) return null;
        ServerLevel level = attacker.serverLevel();
        try {
            var holder = level.registryAccess()
                    .registryOrThrow(Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(typeKey);
            return new DamageSource(holder, attacker);
        } catch (Exception e) {
            WenXingTools.LOGGER.warn("[wenxingtools] {} DamageType missing, fallback to playerAttack: {}", typeKey.location(), e.getMessage());
            return attacker.damageSources().playerAttack(attacker);
        }
    }


    public static DamageSource createInfinityKillSource(ServerPlayer attacker) {
        return createKillSourceWithPlayer(attacker, INFINITY_KILL);
    }


    public static DamageSource createVanillaOutOfWorldSource(ServerPlayer attacker) {
        return createKillSourceWithPlayer(attacker, VANILLA_OUT_OF_WORLD);
    }


    public static DamageSource createVanillaOutOfWorldSource(net.minecraft.world.level.Level level) {
        if (level == null) return null;
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


    public static boolean isVanillaOutOfWorld(DamageSource source) {
        if (source == null) return false;
        try {
            return source.typeHolder().is(VANILLA_OUT_OF_WORLD);
        } catch (Exception e) {
            return "outOfWorld".equals(source.getMsgId());
        }
    }


    public static void applyDirectKill(ServerPlayer damageSourcePlayer, LivingEntity target) {
        applyDirectKill(damageSourcePlayer, target, createInfinityKillSource(damageSourcePlayer));
    }


    public static void applyDirectKill(ServerPlayer damageSourcePlayer, LivingEntity target, DamageSource source) {
        if (damageSourcePlayer == null || target == null) return;
        if (isNotValidAttackerPlayer(damageSourcePlayer)) return;
        if (target.isRemoved()) return;

        if (target.isDeadOrDying() && target.getHealth() <= 0.0f && target.deathTime > 0) return;
        if (KILL_CHAIN_RUNNING.get()) return;

        KILL_CHAIN_RUNNING.set(true);
        try {
            target.removeEffect(MobEffects.DAMAGE_RESISTANCE);
            target.removeEffect(MobEffects.ABSORPTION);
            target.removeEffect(MobEffects.REGENERATION);
            target.removeEffect(MobEffects.FIRE_RESISTANCE);

            target.invulnerableTime = 0;
            target.hurtTime = 0;

            DamageSource resolved = source != null ? source : createInfinityKillSource(damageSourcePlayer);
            if (resolved == null) return;


            float damageAmount = target.getHealth();
            if (!(damageAmount > 0.0f) || Float.isNaN(damageAmount)) {
                float max = target.getMaxHealth();
                damageAmount = max > 0.0f ? max : DEFAULT_MAX_HEALTH;
            }
            target.invulnerableTime = 0;
            target.hurtTime = 0;
            target.hurt(resolved, damageAmount);


            DamageSource deathSource = resolved;
            if (!target.isRemoved()
                    && target.getHealth() > 0.0f
                    && !(target.isDeadOrDying() && target.deathTime > 0)) {
                DamageSource voidSource = createVanillaOutOfWorldSource(damageSourcePlayer);
                if (voidSource != null) {
                    target.invulnerableTime = 0;
                    target.hurtTime = 0;
                    target.hurt(voidSource, DIRECT_KILL_DAMAGE);
                    deathSource = voidSource;
                }
            }


            // 通用清空：分片压血，兼容 setHealth 单次钳制（如哞菇巨兽）
            DamageCapBypass.forceReduceHealthToZero(damageSourcePlayer, target);
            if (target.getHealth() > 0.0f) {
                target.setHealth(0.0F);
            }

            target.die(deathSource);
        } finally {
            KILL_CHAIN_RUNNING.set(false);
        }
    }


    public static void forceKillEntityByCommandChain(MinecraftServer server, LivingEntity target, ServerPlayer damageSourcePlayer) {
        if (target == null || target.isRemoved()) return;
        if (target.level().isClientSide) return;
        if (KILL_CHAIN_RUNNING.get()) return;

        if (damageSourcePlayer == null || isNotValidAttackerPlayer(damageSourcePlayer)) return;
        if (!PermissionUtil.hasPermission(damageSourcePlayer)) return;

        if (target.isDeadOrDying() && target.deathTime > 0) return;

        DamageSource primary = createInfinityKillSource(damageSourcePlayer);
        applyDirectKill(damageSourcePlayer, target, primary);


        if (target.getHealth() > 0.0f && !target.isRemoved() && !(target.isDeadOrDying() && target.deathTime > 0)) {
            if (KILL_CHAIN_RUNNING.get()) {
                return;
            }
            KILL_CHAIN_RUNNING.set(true);
            try {
                DamageSource source = createVanillaOutOfWorldSource(damageSourcePlayer);
                if (source == null) {
                    source = damageSourcePlayer.damageSources().playerAttack(damageSourcePlayer);
                }
                target.invulnerableTime = 0;
                target.hurtTime = 0;
                target.hurt(source, DIRECT_KILL_DAMAGE);
                DamageCapBypass.forceReduceHealthToZero(damageSourcePlayer, target);
                if (target.getHealth() > 0.0f) {
                    target.setHealth(0.0F);
                }
                target.die(source);
            } finally {
                KILL_CHAIN_RUNNING.set(false);
            }
        }
    }
}
