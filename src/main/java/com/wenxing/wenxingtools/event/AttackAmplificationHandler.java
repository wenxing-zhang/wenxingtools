package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.enchantment.EnchantUtil;
import com.wenxing.wenxingtools.enchantment.ModEnchantments;
import com.wenxing.wenxingtools.util.CombatReentry;
import com.wenxing.wenxingtools.util.CombatUtil;
import net.minecraft.world.entity.EquipmentSlot;
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
 * 入账互斥：旁路真伤成功则不再改事件金额；全失败时把百分比并入事件，
 * 由目标自带伤害通道（如 LM addDamage）入账。
 */
@EventBusSubscriber(modid = WenXingTools.MODID)
public final class AttackAmplificationHandler {
    private AttackAmplificationHandler() {}

    public static final String ATTACK_AMPLIFICATION_LEVEL_TAG = WenXingTools.MODID + ":attack_amp_level";
    private static final float PERCENT_PER_LEVEL = 1.0f;

    public static void syncAttackAmplification(Player player) {
        if (player == null || player.level().isClientSide) {
            return;
        }
        int totalAttackLevel = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            totalAttackLevel += EnchantUtil.getLevelOnPlayerSlot(ModEnchantments.ATTACK_AMPLIFICATION, player, slot);
        }
        player.getPersistentData().putInt(ATTACK_AMPLIFICATION_LEVEL_TAG, totalAttackLevel);
    }

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
        Player player = CombatUtil.resolvePlayerAttacker(event.getSource());
        if (player == null || CombatUtil.isNotValidAttackerPlayer(player)) {
            return;
        }
        LivingEntity target = event.getEntity();
        if (!target.isAlive()) {
            return;
        }
        if (target instanceof Player) {
            return;
        }
        int attackLevel = player.getPersistentData().getInt(ATTACK_AMPLIFICATION_LEVEL_TAG);
        if (attackLevel <= 0) {
            attackLevel = EnchantUtil.getTotalLevelAllSlots(ModEnchantments.ATTACK_AMPLIFICATION, player);
            if (attackLevel > 0) {
                player.getPersistentData().putInt(ATTACK_AMPLIFICATION_LEVEL_TAG, attackLevel);
            }
        }
        if (attackLevel <= 0) {
            return;
        }
        if (!CombatReentry.tryEnterAttackAmpHit(target)) {
            return;
        }
        applyAttackAmplificationTrueDamage(player, target, attackLevel, event);
    }

    private static void applyAttackAmplificationTrueDamage(
            Player player, LivingEntity target, int level, LivingIncomingDamageEvent event) {
        if (CombatReentry.isAttackAmpRunning()) {
            return;
        }
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return;
        }
        float percentage = (level * PERCENT_PER_LEVEL) / 100.0f;
        float percentDamage = CombatUtil.getMaxHealth(target) * percentage;
        float before = target.getHealth();
        if (!(before > 0.0f) || Float.isNaN(before)) {
            return;
        }

        if (before > percentDamage) {
            final float[] dealtHolder = new float[1];
            CombatReentry.runAttackAmp(() -> {
                dealtHolder[0] = CombatUtil.applyBypassTrueDamage(serverPlayer, target, percentDamage);
            });
            if (dealtHolder[0] > 0.0f) {
                CombatUtil.healByActualDealt(player, dealtHolder[0]);
            } else {
                foldIntoEvent(event, percentDamage);
                CombatUtil.healByActualDealt(player, percentDamage);
            }
            return;
        }

        CombatUtil.applyDirectKill(serverPlayer, target);
        float after = target.getHealth();
        float actualDealt = Math.max(0.0f, before - after);
        if (actualDealt <= 0.0f) {
            foldIntoEvent(event, percentDamage);
            actualDealt = percentDamage;
        }
        CombatUtil.healByActualDealt(player, actualDealt);
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
}
