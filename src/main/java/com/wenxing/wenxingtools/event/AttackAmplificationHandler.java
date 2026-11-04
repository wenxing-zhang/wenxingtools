package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.enchantment.EnchantUtil;
import com.wenxing.wenxingtools.enchantment.ModEnchantments;
import com.wenxing.wenxingtools.util.AmpRules;
import com.wenxing.wenxingtools.util.Attacker;
import com.wenxing.wenxingtools.util.CombatReentry;
import com.wenxing.wenxingtools.util.CombatUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * 攻击增幅：按最大生命百分比造成旁路真伤。
 * <p>
 * 对单次伤害钳制目标（Goety:Awaken 哞菇巨兽 setHealth 25% 钳制、
 * 无名者 hurt 20% 上限+命中冷却），经 {@link com.wenxing.wenxingtools.util.DamageCapBypass}
 * 分片扣满目标百分比，不再被削成上限值。
 * <p>
 * 等级取装备槽最大值（副手与护甲槽都算），跨槽不再累加；攻击者接受任意生物（女仆、假玩家），
 * 但吸血只给真实玩家——假玩家没有血量语义，生物吸血等于永久无敌。
 * <p>
 * 入账互斥：旁路真伤成功则不再改事件金额；全失败时把百分比并入事件，
 * 由目标自带伤害通道（如 LM addDamage）入账。
 */
@EventBusSubscriber(modid = WenXingTools.MODID)
public final class AttackAmplificationHandler {
    private AttackAmplificationHandler() {}

    private static final float PERCENT_PER_LEVEL = 1.0f;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingIncomingForAttackAmplification(LivingIncomingDamageEvent event) {
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
        // 现查而不缓存：装备变更事件与就地改 NBT 都可能绕过缓存，读错等级就是多打或少打一截真伤
        int attackLevel = EnchantUtil.getMaxLevelAllSlots(ModEnchantments.ATTACK_AMPLIFICATION, attacker.entity());
        if (attackLevel <= 0) {
            return;
        }
        if (!CombatReentry.tryEnterAttackAmpHit(target)) {
            return;
        }
        applyAttackAmplificationTrueDamage(attacker, target, attackLevel, event);
    }

    private static void applyAttackAmplificationTrueDamage(
            Attacker attacker, LivingEntity target, int level, LivingIncomingDamageEvent event) {
        if (CombatReentry.isAttackAmpRunning()) {
            return;
        }
        LivingEntity attackerEntity = attacker.entity();
        float percentage = (level * PERCENT_PER_LEVEL) / 100.0f;
        float percentDamage = CombatUtil.getMaxHealth(target) * percentage;
        float before = target.getHealth();
        if (!(before > 0.0f) || Float.isNaN(before)) {
            return;
        }

        if (before > percentDamage) {
            final float[] dealtHolder = new float[1];
            CombatReentry.runAttackAmp(() -> {
                dealtHolder[0] = CombatUtil.applyBypassTrueDamage(attackerEntity, target, percentDamage);
            });
            if (dealtHolder[0] > 0.0f) {
                healAttacker(attacker, dealtHolder[0]);
            } else {
                foldIntoEvent(event, percentDamage);
                healAttacker(attacker, percentDamage);
            }
            return;
        }

        // 低血：走必杀链；未死再退回旁路真伤（姊妹端口径：溢出量一并计入吸血）
        // 必杀链须在增幅闸门外调用（applyDirectKill 自身首段查闸门，闸门内会被直接 return），
        // 旁路真伤则须在闸门内——否则其 hurt 以「无闸门上下文」触发回响 / 净化 / 无敌特性链，造成级联。
        float healthBeforeKill = target.getHealth();
        float dealt;
        if (attackerEntity instanceof ServerPlayer serverPlayer) {
            CombatUtil.applyDirectKill(serverPlayer, target);
            if (target.isDeadOrDying() || target.isRemoved() || target.getHealth() <= 0.0f) {
                dealt = healthBeforeKill;
            } else {
                dealt = runBypassInGate(attackerEntity, target, percentDamage);
                if (dealt <= 0.0f) {
                    foldIntoEvent(event, percentDamage);
                    dealt = percentDamage;
                }
            }
        } else {
            dealt = runBypassInGate(attackerEntity, target, percentDamage);
            if (dealt <= 0.0f) {
                foldIntoEvent(event, percentDamage);
                dealt = percentDamage;
            }
        }
        if (dealt > 0.0f) {
            float leftover = Math.max(0.0f, percentDamage - before);
            healAttacker(attacker, dealt + leftover);
        }
    }

    private static float runBypassInGate(LivingEntity attackerEntity, LivingEntity target, float percentDamage) {
        final float[] dealtHolder = new float[1];
        CombatReentry.runAttackAmp(() -> {
            dealtHolder[0] = CombatUtil.applyBypassTrueDamage(attackerEntity, target, percentDamage);
        });
        return dealtHolder[0];
    }

    /** 旁路全失败：并入事件金额，与旁路真伤互斥，避免叠伤。 */
    private static void foldIntoEvent(LivingIncomingDamageEvent event, float percentDamage) {
        if (event == null || !(percentDamage > 0.0f) || event.isCanceled()) {
            return;
        }
        float current = event.getAmount();
        if (current <= 0.0f) {
            return;
        }
        event.setAmount(current + percentDamage);
    }

    /** 吸血只给真实玩家；走 {@link CombatUtil#healByActualDealt} 以复用「净化窗内禁改血」的判据。 */
    private static void healAttacker(Attacker attacker, float amount) {
        if (!(amount > 0.0f) || !AmpRules.shouldHeal(attacker.kind())) {
            return;
        }
        Player player = attacker.asPlayer();
        if (player == null) {
            return;
        }
        CombatUtil.healByActualDealt(player, amount);
    }
}
