package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.enchantment.EnchantUtil;
import com.wenxing.wenxingtools.enchantment.ModEnchantments;
import com.wenxing.wenxingtools.util.CombatReentry;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = WenXingTools.MODID)
public final class PotionAmplificationHandler {
    private PotionAmplificationHandler() {}

    private static final EquipmentSlot[] ARMOR_SLOTS = new EquipmentSlot[]{
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    private static final long POTION_AMPLIFICATION_HOUR_TICKS = 72000L;
    private static final int POTION_AMPLIFICATION_SAFE_DURATION_CAP = 48 * 72_000;
    private static final Map<UUID, EnumMap<EquipmentSlot, ItemStack>> POTION_AMP_LAST_EQUIPPED = new ConcurrentHashMap<>();

    public static void clearPotionAmplificationPlayerState(UUID playerId) {
        if (playerId != null) {
            POTION_AMP_LAST_EQUIPPED.remove(playerId);
        }
    }

    @SubscribeEvent
    public static void onLivingEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        EquipmentSlot slot = event.getSlot();
        if (!LifeAmplificationHandler.isArmorSlot(slot)) {
            return;
        }
        if (CombatReentry.isPotionAmpRunning()) {
            return;
        }
        CombatReentry.runPotionAmp(() -> {
            ItemStack to = event.getTo();
            boolean worn = getPotionAmplificationLevel(to, player) > 0;
            if (worn) {
                ItemStack prev = getLastEquippedPotionAmp(player, slot);
                if (!isSameEquipment(to, prev)) {
                    int maxLevel = getMaxPotionAmplificationLevelOnArmor(player);
                    if (maxLevel > 0) {
                        accruePotionAmplificationDuration(player, maxLevel);
                    }
                }
            }
            rememberPotionAmpArmor(player, slot, worn ? to : ItemStack.EMPTY);
        });
    }

    private static int getPotionAmplificationLevel(ItemStack stack, LivingEntity context) {
        return EnchantUtil.getLevel(ModEnchantments.POTION_AMPLIFICATION, stack, context);
    }

    private static void accruePotionAmplificationDuration(Player player, int maxLevel) {
        List<MobEffectInstance> effects = new ArrayList<>();
        player.getActiveEffects().forEach(effects::add);
        for (MobEffectInstance instance : effects) {
            if (!instance.getEffect().value().isBeneficial()) {
                continue;
            }
            if (instance.getDuration() >= POTION_AMPLIFICATION_SAFE_DURATION_CAP) {
                continue;
            }
            long newDurationLong = (long) instance.getDuration() + (long) maxLevel * POTION_AMPLIFICATION_HOUR_TICKS;
            int newDuration = (int) Math.min(newDurationLong, (long) POTION_AMPLIFICATION_SAFE_DURATION_CAP);
            player.addEffect(new MobEffectInstance(
                    instance.getEffect(),
                    newDuration,
                    instance.getAmplifier(),
                    instance.isAmbient(),
                    instance.isVisible(),
                    instance.showIcon()));
        }
    }

    private static int getMaxPotionAmplificationLevelOnArmor(Player player) {
        int max = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            int level = EnchantUtil.getLevelOnPlayerSlot(ModEnchantments.POTION_AMPLIFICATION, player, slot);
            if (level > max) {
                max = level;
            }
        }
        return max;
    }

    private static ItemStack getLastEquippedPotionAmp(Player player, EquipmentSlot slot) {
        EnumMap<EquipmentSlot, ItemStack> state = POTION_AMP_LAST_EQUIPPED.get(player.getUUID());
        if (state == null) {
            return ItemStack.EMPTY;
        }
        ItemStack last = state.get(slot);
        return last == null ? ItemStack.EMPTY : last;
    }

    private static void rememberPotionAmpArmor(Player player, EquipmentSlot slot, ItemStack stack) {
        POTION_AMP_LAST_EQUIPPED
                .computeIfAbsent(player.getUUID(), u -> new EnumMap<>(EquipmentSlot.class))
                .put(slot, stack.copy());
    }

    private static boolean isSameEquipment(ItemStack a, ItemStack b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a.isEmpty() || b.isEmpty()) {
            return a.isEmpty() && b.isEmpty();
        }
        return ItemStack.isSameItemSameComponents(a, b);
    }
}
