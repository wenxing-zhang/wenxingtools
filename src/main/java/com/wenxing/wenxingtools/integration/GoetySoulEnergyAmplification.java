package com.wenxing.wenxingtools.integration;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.event.ResourceAmplificationHandler;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;

import java.lang.reflect.Method;
import java.util.function.Consumer;

public final class GoetySoulEnergyAmplification {

    private static final String GAIN_EVENT_CLASS = "com.Polarice3.Goety.common.events.spell.ChangeSoulEnergyEvent$Gain";
    private static final int SOUL_GAIN_CAP = 2_000_000_000;

    private static volatile Boolean ready;
    private static final Object INIT_LOCK = new Object();

    private static Method getSoulChange;
    private static Method setSoulChange;

    private GoetySoulEnergyAmplification() {
    }

    public static void register() {
        if (!ensureReady()) {
            return;
        }
        try {
            Class<?> eventClass = Class.forName(GAIN_EVENT_CLASS, false, GoetySoulEnergyAmplification.class.getClassLoader());
            Consumer<Object> handler = GoetySoulEnergyAmplification::onSoulEnergyGain;
            if (!tryAddListener(eventClass, handler)) {
                ready = Boolean.FALSE;
            }
        } catch (Throwable t) {
            ready = Boolean.FALSE;
            WenXingTools.LOGGER.debug("[resource_amp] Goety register failed: {}", t.toString());
        }
    }

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
                Class<?> eventClass = Class.forName(
                        GAIN_EVENT_CLASS, false, GoetySoulEnergyAmplification.class.getClassLoader());
                getSoulChange = eventClass.getMethod("getSoulChange");
                setSoulChange = eventClass.getMethod("setSoulChange", int.class);
                ready = Boolean.TRUE;
            } catch (Throwable t) {
                ready = Boolean.FALSE;
                WenXingTools.LOGGER.debug("[resource_amp] Goety init failed: {}", t.toString());
            }
            return ready;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean tryAddListener(Class<?> eventClass, Consumer<Object> handler) {
        try {
            IEventBus bus = NeoForge.EVENT_BUS;
            for (Method method : IEventBus.class.getMethods()) {
                if (!"addListener".equals(method.getName()) || method.getParameterCount() != 4) {
                    continue;
                }
                Class<?>[] params = method.getParameterTypes();
                if (params[0] == EventPriority.class
                        && params[1] == boolean.class
                        && params[2] == Class.class
                        && params[3] == Consumer.class) {
                    method.invoke(bus, EventPriority.HIGH, Boolean.FALSE, eventClass, handler);
                    return true;
                }
            }
            return false;
        } catch (Throwable t) {
            WenXingTools.LOGGER.debug("[resource_amp] Goety listener attach failed: {}", t.toString());
            return false;
        }
    }

    private static void onSoulEnergyGain(Object event) {
        try {
            if (!ensureReady()) {
                return;
            }
            Object playerObj = event.getClass().getMethod("getEntity").invoke(event);
            if (!(playerObj instanceof Player player) || player.level().isClientSide) {
                return;
            }
            int level = ResourceAmplificationHandler.resolveMainHandResourceLevel(player);
            if (level <= 0) {
                return;
            }
            int soulChange = (int) getSoulChange.invoke(event);
            if (soulChange <= 0) {
                return;
            }
            int factor = ResourceAmplificationHandler.computeResourceFactor(level);
            long amplified = (long) soulChange * (1L + (long) factor);
            int result = (int) Math.min(amplified, SOUL_GAIN_CAP);
            setSoulChange.invoke(event, result);
        } catch (Throwable t) {
            WenXingTools.LOGGER.debug("[resource_amp] Goety soul amplify failed: {}", t.toString());
        }
    }
}
