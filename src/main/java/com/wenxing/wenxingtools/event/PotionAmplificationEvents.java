package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.enchantment.ModEnchantments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
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

    /** 是否有任一护甲槽带药水增幅（装备变化时刷新），供每 tick 兜底短路。 */
    private static final Map<UUID, Boolean> POTION_AMP_PRESENT = new ConcurrentHashMap<>();

    /** 上次见到的效果数量，用于捕获绕过准入判定的效果表写入。 */
    private static final Map<UUID, Integer> POTION_AMP_EFFECT_COUNT = new ConcurrentHashMap<>();

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
                        cleanseHarmfulEffects(player, maxLevel);
                    }
                }
            }
            rememberPotionAmpArmor(player, slot, worn ? to : ItemStack.EMPTY);
        } finally {
            POTION_AMP_RUNNING.set(false);
        }
        POTION_AMP_PRESENT.put(player.getUUID(), getMaxPotionAmplificationLevelOnArmor(player) > 0);
    }


    // 净化只在「身上效果发生变化」时判定：Applicable 由 LivingEntity#canBeAffected 发出，
    // 而 addEffect 的第一步就是 canBeAffected——DENY 时效果根本不进 activeEffects，也不再同步给客户端。
    // 优先级 LOWEST：排在净化增幅（HIGHEST）之后收尾，且从不设 ALLOW，绝不把它的拒绝翻成允许。
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MobEffectInstance instance = event.getEffectInstance();
        if (instance == null) {
            return;
        }
        if (!PotionCleanseRule.shouldCleanse(
                getMaxPotionAmplificationLevelOnArmor(player),
                instance.getEffect().getCategory() == MobEffectCategory.HARMFUL,
                EffectPurgeSupport.isProtectedEffect(instance))) {
            return;
        }
        event.setResult(Event.Result.DENY);
    }


    // 兜底：第三方绕过 addEffect / forceAddEffect 直接写效果表时，准入判定完全看不到。
    // 只比数量（O(1)，不遍历集合），数量一变才做一次全量清扫；未穿护甲者不入缓存、开销为零。
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        UUID playerId = player.getUUID();
        Boolean present = POTION_AMP_PRESENT.get(playerId);
        if (present == null) {
            present = getMaxPotionAmplificationLevelOnArmor(player) > 0;
            POTION_AMP_PRESENT.put(playerId, present);
        }
        if (!present) {
            POTION_AMP_EFFECT_COUNT.remove(playerId);
            return;
        }
        int count = player.getActiveEffects().size();
        Integer previous = POTION_AMP_EFFECT_COUNT.put(playerId, count);
        if (previous != null && previous == count) {
            return;
        }
        cleanseHarmfulEffects(player, getMaxPotionAmplificationLevelOnArmor(player));
        POTION_AMP_EFFECT_COUNT.put(playerId, player.getActiveEffects().size());
    }


    // 读档（登录/重生/跨维度）直接写 activeEffects、不经过 addEffect，事件路径盯不到——进入生效态时补扫一次。
    static void cleanseHarmfulEffectsIfWorn(Player player) {
        if (player == null || player.level().isClientSide) {
            return;
        }
        cleanseHarmfulEffects(player, getMaxPotionAmplificationLevelOnArmor(player));
    }


    private static void cleanseHarmfulEffects(Player player, int maxLevel) {
        if (maxLevel <= 0) {
            return;
        }
        List<MobEffect> harmful = null;
        for (MobEffectInstance instance : player.getActiveEffects()) {
            MobEffect effect = instance.getEffect();
            if (!PotionCleanseRule.shouldCleanse(maxLevel,
                    effect.getCategory() == MobEffectCategory.HARMFUL,
                    EffectPurgeSupport.isProtectedEffect(instance))) {
                continue;
            }
            if (harmful == null) {
                harmful = new ArrayList<>();
            }
            harmful.add(effect);
        }
        if (harmful == null) {
            return;
        }
        for (MobEffect effect : harmful) {
            player.removeEffect(effect);
        }
    }


    static void clearPotionAmplificationPlayerState(UUID playerId) {
        if (playerId == null) {
            return;
        }
        POTION_AMP_LAST_EQUIPPED.remove(playerId);
        POTION_AMP_PRESENT.remove(playerId);
        POTION_AMP_EFFECT_COUNT.remove(playerId);
    }

    private static boolean isPotionArmorSlot(EquipmentSlot slot) {
        return slot == EquipmentSlot.HEAD
                || slot == EquipmentSlot.CHEST
                || slot == EquipmentSlot.LEGS
                || slot == EquipmentSlot.FEET;
    }

    private static int getPotionAmplificationLevel(ItemStack stack) {
        // isEnchanted 已覆盖 isEmpty；无附魔数据时 getItemEnchantmentLevel 必然返回 0 却仍会构造一次 HashMap
        if (stack == null || !stack.isEnchanted()) {
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
