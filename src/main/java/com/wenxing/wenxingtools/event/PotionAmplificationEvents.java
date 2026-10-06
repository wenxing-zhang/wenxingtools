package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.enchantment.ModEnchantments;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = WenXingTools.MODID)
public final class PotionAmplificationEvents {


    private static final ThreadLocal<Boolean> POTION_AMP_RUNNING = ThreadLocal.withInitial(() -> false);

    private static final Map<UUID, EnumMap<EquipmentSlot, ItemStack>> POTION_AMP_LAST_EQUIPPED = new ConcurrentHashMap<>();

    private PotionAmplificationEvents() {
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
        boolean shouldSync = slot == EquipmentSlot.MAINHAND
                || slot == EquipmentSlot.OFFHAND
                || isPotionArmorSlot(slot);
        if (shouldSync) {
            AmpSupport.syncPlayerAmplifications(player);
        }
        boolean potionArmorSlot = isPotionArmorSlot(slot);
        if (!potionArmorSlot) {
            return;
        }
        if (POTION_AMP_RUNNING.get()) {
            return;
        }
        POTION_AMP_RUNNING.set(true);
        try {
            ItemStack to = event.getTo();
            boolean worn = getPotionAmplificationLevel(to) > 0;

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
        } finally {
            POTION_AMP_RUNNING.set(false);
        }
    }


    static void clearPotionAmplificationPlayerState(UUID playerId) {
        if (playerId == null) {
            return;
        }
        POTION_AMP_LAST_EQUIPPED.remove(playerId);
    }

    private static boolean isPotionArmorSlot(EquipmentSlot slot) {
        return slot == EquipmentSlot.HEAD
                || slot == EquipmentSlot.CHEST
                || slot == EquipmentSlot.LEGS
                || slot == EquipmentSlot.FEET;
    }

    private static int getPotionAmplificationLevel(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        return EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.POTION_AMPLIFICATION.get(), stack);
    }


    private static void accruePotionAmplificationDuration(Player player, int maxLevel) {

        List<MobEffectInstance> effects = new ArrayList<>();
        player.getActiveEffects().forEach(effects::add);
        for (MobEffectInstance instance : effects) {
            MobEffect effect = instance.getEffect();
            if (!effect.isBeneficial()) {
                continue;
            }


            if (instance.getDuration() >= AmpConstants.POTION_AMPLIFICATION_SAFE_DURATION_CAP) {
                continue;
            }
            long newDurationLong = (long) instance.getDuration() + (long) maxLevel * AmpConstants.POTION_AMPLIFICATION_HOUR_TICKS;
            int newDuration = (int) Math.min(newDurationLong, (long) AmpConstants.POTION_AMPLIFICATION_SAFE_DURATION_CAP);
            player.addEffect(new MobEffectInstance(effect, newDuration, instance.getAmplifier(),
                    instance.isAmbient(), instance.isVisible(), instance.showIcon()));
        }
    }

    private static int getMaxPotionAmplificationLevelOnArmor(Player player) {
        int max = 0;
        for (EquipmentSlot slot : AmpConstants.ARMOR_SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            int level = getPotionAmplificationLevel(stack);
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
        boolean aEmpty = a.isEmpty();
        boolean bEmpty = b.isEmpty();
        if (aEmpty || bEmpty) {
            return aEmpty && bEmpty;
        }
        return a.getItem() == b.getItem() && java.util.Objects.equals(a.getTag(), b.getTag());
    }
}
