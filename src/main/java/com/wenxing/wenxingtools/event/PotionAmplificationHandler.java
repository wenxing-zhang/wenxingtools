package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.enchantment.EnchantUtil;
import com.wenxing.wenxingtools.enchantment.ModEnchantments;
import com.wenxing.wenxingtools.util.CombatReentry;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

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
    /** 是否有任一护甲槽带药水增幅（装备变化时刷新），供每 tick 兜底短路。 */
    private static final Map<UUID, Boolean> POTION_AMP_PRESENT = new ConcurrentHashMap<>();
    /** 上次见到的效果数量，用于捕获绕过准入判定的效果表写入。 */
    private static final Map<UUID, Integer> POTION_AMP_EFFECT_COUNT = new ConcurrentHashMap<>();

    public static void clearPotionAmplificationPlayerState(UUID playerId) {
        if (playerId != null) {
            POTION_AMP_LAST_EQUIPPED.remove(playerId);
            POTION_AMP_PRESENT.remove(playerId);
            POTION_AMP_EFFECT_COUNT.remove(playerId);
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
                        cleanseHarmfulEffects(player, maxLevel);
                    }
                }
            }
            rememberPotionAmpArmor(player, slot, worn ? to : ItemStack.EMPTY);
        });
        POTION_AMP_PRESENT.put(player.getUUID(), getMaxPotionAmplificationLevelOnArmor(player) > 0);
    }

    // 净化只在「身上效果发生变化」时判定：Applicable 由 addEffect / forceAddEffect 的第一步发出，
    // 结果为 DO_NOT_APPLY 时效果根本不进 activeEffects，也不再同步给客户端。
    // 优先级 LOWEST：排在净化增幅（HIGHEST）之后收尾，且从不设 APPLY，绝不把它的拒绝翻成允许。
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
                instance.getEffect().value().getCategory() == MobEffectCategory.HARMFUL,
                EffectPurgeSupport.isProtectedEffect(instance))) {
            return;
        }
        event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
    }

    // 兜底：第三方绕过 addEffect / forceAddEffect 直接写效果表时，准入判定完全看不到。
    // 只比数量（O(1)，不遍历集合），数量一变才做一次全量清扫；未穿护甲者不入缓存、开销为零。
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        UUID playerId = serverPlayer.getUUID();
        Boolean present = POTION_AMP_PRESENT.get(playerId);
        if (present == null) {
            present = getMaxPotionAmplificationLevelOnArmor(serverPlayer) > 0;
            POTION_AMP_PRESENT.put(playerId, present);
        }
        if (!present) {
            POTION_AMP_EFFECT_COUNT.remove(playerId);
            return;
        }
        int count = serverPlayer.getActiveEffects().size();
        Integer previous = POTION_AMP_EFFECT_COUNT.put(playerId, count);
        if (previous != null && previous == count) {
            return;
        }
        cleanseHarmfulEffects(serverPlayer, getMaxPotionAmplificationLevelOnArmor(serverPlayer));
        POTION_AMP_EFFECT_COUNT.put(playerId, serverPlayer.getActiveEffects().size());
    }

    // 读档（登录/重生/跨维度）直接写 activeEffects、不经过 addEffect，事件路径盯不到——进入生效态时补扫一次。
    public static void cleanseHarmfulEffectsIfWorn(Player player) {
        if (player == null || player.level().isClientSide) {
            return;
        }
        cleanseHarmfulEffects(player, getMaxPotionAmplificationLevelOnArmor(player));
    }

    private static void cleanseHarmfulEffects(Player player, int maxLevel) {
        if (maxLevel <= 0) {
            return;
        }
        List<Holder<MobEffect>> harmful = null;
        for (MobEffectInstance instance : player.getActiveEffects()) {
            if (!PotionCleanseRule.shouldCleanse(
                    maxLevel,
                    instance.getEffect().value().getCategory() == MobEffectCategory.HARMFUL,
                    EffectPurgeSupport.isProtectedEffect(instance))) {
                continue;
            }
            if (harmful == null) {
                harmful = new ArrayList<>();
            }
            harmful.add(instance.getEffect());
        }
        if (harmful == null) {
            return;
        }
        for (Holder<MobEffect> effect : harmful) {
            player.removeEffect(effect);
        }
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
