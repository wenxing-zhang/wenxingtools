package com.wenxing.wenxingtools.integration;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.event.AmpSupport;
import com.wenxing.wenxingtools.util.CombatUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Goety 灵魂能量增幅：在 Gain 事件内按主手等级现算。
 * 不用死亡 ThreadLocal，避免取消死亡/清理时序导致泄漏或读空。
 */
public final class GoetySoulEnergyIntegration {

    private static final String GAIN_EVENT_CLASS =
            "com.Polarice3.Goety.common.events.spell.ChangeSoulEnergyEvent$Gain";
    private static final String CHANGE_EVENT_CLASS =
            "com.Polarice3.Goety.common.events.spell.ChangeSoulEnergyEvent";
    private static final int SOUL_GAIN_CAP = 2_000_000_000;

    private static volatile Boolean ready;
    private static final Object INIT_LOCK = new Object();

    private static Method getSoulChange;
    private static Method setSoulChange;

    private GoetySoulEnergyIntegration() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean ensureReady() {
        Boolean cached = ready;
        if (cached != null) {
            return cached;
        }
        synchronized (INIT_LOCK) {
            cached = ready;
            if (cached != null) {
                return cached;
            }
            try {
                Class<?> changeClass = Class.forName(CHANGE_EVENT_CLASS);
                Class<?> gainClass = Class.forName(GAIN_EVENT_CLASS);
                getSoulChange = changeClass.getMethod("getSoulChange");
                setSoulChange = changeClass.getMethod("setSoulChange", int.class);

                Consumer consumer = event -> onSoulEnergyGain(event);
                MinecraftForge.EVENT_BUS.addListener(
                        EventPriority.HIGH,
                        true,
                        (Class) gainClass,
                        consumer);
                ready = Boolean.TRUE;
            } catch (Throwable t) {
                ready = Boolean.FALSE;
                WenXingTools.LOGGER.debug("[resource_amp] Goety soul energy init failed: {}", t.toString());
            }
            return ready;
        }
    }

    public static void register() {
        ensureReady();
    }

    public static void clearFactorContext() {
        // 兼容旧调用：已不再使用 ThreadLocal
    }

    private static void onSoulEnergyGain(Object event) {
        if (!ensureReady() || event == null) {
            return;
        }
        try {
            Object playerObj = event.getClass().getMethod("getEntity").invoke(event);
            if (!(playerObj instanceof Player player) || player.level().isClientSide) {
                return;
            }
            if (CombatUtil.isNotValidAttackerPlayer(player)) {
                return;
            }
            int resourceLevel = AmpSupport.resolveResourceLevel(player, null);
            if (resourceLevel <= 0) {
                return;
            }
            int change = (Integer) getSoulChange.invoke(event);
            if (change <= 0) {
                return;
            }
            int factor = AmpSupport.computeResourceFactor(resourceLevel);
            long amplified = (long) change * (1L + factor);
            setSoulChange.invoke(event, (int) Math.min(amplified, SOUL_GAIN_CAP));
        } catch (Throwable t) {
            WenXingTools.LOGGER.debug("[resource_amp] Goety soul energy amplify failed: {}", t.toString());
        }
    }
}
