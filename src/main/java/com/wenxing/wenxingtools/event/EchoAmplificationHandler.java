package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.enchantment.EnchantUtil;
import com.wenxing.wenxingtools.enchantment.ModEnchantments;
import com.wenxing.wenxingtools.util.Attacker;
import com.wenxing.wenxingtools.util.CombatReentry;
import com.wenxing.wenxingtools.util.CombatUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * 回响增幅：命中后对目标追加 10×等级 次虚空伤害（单次 = 攻击者 ATTACK_DAMAGE）。
 * <p>
 * 结算方案 A：循环 {@code hurt(out_of_world, attackPower)}，每击重置无敌帧；
 * 玩家攻击者经 {@link CombatUtil#createVoidKillSource} 挂实体，击杀归属/掉落经验归玩家；
 * 生物攻击者退化为无归属虚空伤害（它没有玩家归属语义）。
 * 回响链 ThreadLocal 防重入；链内不触发攻击增幅/净化/回响。结束后再净化一次。
 */
@EventBusSubscriber(modid = WenXingTools.MODID)
public final class EchoAmplificationHandler {

    /** 每级回响次数系数：总伤倍率 = 10 × level（等级 3 = 30 次）。 */
    public static final int HITS_PER_LEVEL = 10;

    private EchoAmplificationHandler() {
    }

    public static boolean isEchoChainRunning() {
        return CombatReentry.isEchoChainRunning();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingIncomingForEchoAmplification(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (event.isCanceled() || event.getAmount() <= 0.0f) {
            return;
        }
        if (CombatUtil.isKillChainRunning()
                || CombatReentry.isEchoChainRunning()
                || CombatReentry.isAttackAmpRunning()) {
            return;
        }

        Attacker attacker = CombatUtil.resolveAttacker(event.getSource());
        if (attacker == null) {
            return;
        }

        LivingEntity target = event.getEntity();
        if (!target.isAlive()) {
            return;
        }
        if (target instanceof Player) {
            return;
        }

        int level = resolveEchoAmplificationLevel(attacker);
        if (level <= 0) {
            return;
        }

        float attackPower = (float) attacker.entity().getAttributeValue(Attributes.ATTACK_DAMAGE);
        if (!(attackPower > 0.0f) || Float.isNaN(attackPower)) {
            return;
        }

        if (!CombatReentry.tryEnterEchoHit(target)) {
            return;
        }

        applyEchoVoidDamage(attacker, target, level, attackPower);
    }

    static int resolveEchoAmplificationLevel(Attacker attacker) {
        if (attacker == null) {
            return 0;
        }
        return EnchantUtil.getMaxLevelAllSlots(ModEnchantments.ECHO_AMPLIFICATION, attacker.entity());
    }

    /**
     * 方案 A：循环虚空 hurt × (10×level)，每击清无敌帧；目标死亡立即停止。
     * 玩家攻击者的伤害源挂实体（out_of_world + source entity = player），击杀归属正确。
     * 结束后强制再净化，清掉连击/词条回写的效果。
     */
    private static void applyEchoVoidDamage(Attacker attacker, LivingEntity target, int level, float attackPower) {
        if (CombatReentry.isEchoChainRunning()) {
            return;
        }
        int hits = (int) Math.min((long) HITS_PER_LEVEL * (long) level, 256L);
        if (hits <= 0 || !(attackPower > 0.0f)) {
            return;
        }

        LivingEntity attackerEntity = attacker.entity();
        DamageSource resolved = attackerEntity instanceof ServerPlayer serverPlayer
                ? CombatUtil.createVoidKillSource(serverPlayer)
                : CombatUtil.createVanillaOutOfWorldSource(attackerEntity.level());
        final DamageSource voidSource = resolved != null
                ? resolved
                : attackerEntity.damageSources().mobAttack(attackerEntity);

        CombatReentry.runEchoChain(() -> {
            for (int i = 0; i < hits; i++) {
                if (target.isRemoved() || !target.isAlive()) {
                    break;
                }
                if (target.isDeadOrDying() && target.deathTime > 0) {
                    break;
                }
                target.invulnerableTime = 0;
                target.hurtTime = 0;
                target.hurt(voidSource, attackPower);
            }
        });

        // 回响/词条处理可能把效果写回来：结束后按需再清，并刷新净化持续窗/核清
        if (!target.isRemoved() && target.isAlive()) {
            int purgeLevel = EffectPurgeHandler.resolveEffectPurgeLevel(attacker);
            if (purgeLevel > 0) {
                EffectPurgeSupport.applyOnHit(target, target.level().getGameTime(), purgeLevel);
            } else if (EffectPurgeSupport.isSuppressed(target)) {
                EffectPurgeSupport.suppressEffects(target);
            }
        }
    }
}
