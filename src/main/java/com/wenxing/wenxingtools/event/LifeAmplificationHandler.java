package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.enchantment.EnchantUtil;
import com.wenxing.wenxingtools.enchantment.ModEnchantments;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = WenXingTools.MODID)
public final class LifeAmplificationHandler {
    private LifeAmplificationHandler() {}

    private static final ResourceLocation LIFE_LUCK_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(WenXingTools.MODID, "life_amplification_luck");
    /** 任意护甲栏有生命增幅即固定 +1024 LUCK，不按件数叠加 */
    private static final double LIFE_LUCK_AMOUNT = 1024.0D;
    private static final float PERCENT_PER_LEVEL = 1.0f;

    private static final EquipmentSlot[] ARMOR_SLOTS = new EquipmentSlot[]{
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    private static final Map<UUID, Integer> LIFE_AMP_LAST_LEVEL = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> LIFE_AMP_LEVEL_CACHE = new ConcurrentHashMap<>();

    public static void syncLifeAmplification(Player player) {
        if (player == null || player.level().isClientSide) {
            return;
        }
        int totalLifeLevel = getTotalEnchantmentLevelOnArmor(player);
        LIFE_AMP_LEVEL_CACHE.put(player.getUUID(), totalLifeLevel);
        Integer lastLifeLevel = LIFE_AMP_LAST_LEVEL.get(player.getUUID());
        if (lastLifeLevel == null || lastLifeLevel != totalLifeLevel) {
            boolean hadLifeAmplification = lastLifeLevel != null && lastLifeLevel > 0;
            LIFE_AMP_LAST_LEVEL.put(player.getUUID(), totalLifeLevel);
            if (totalLifeLevel > 0) {
                applyLifeAmplificationEffects(player);
            } else if (hadLifeAmplification) {
                clearLifeAmplificationEffects(player);
            }
        } else if (totalLifeLevel > 0) {
            // 等级未变但仍生效：换主手/副手等不改护甲等级，须单独修耐久（对齐 1.20.1）
            repairLifeAmplificationEquipment(player);
        }
    }

    public static void clearLifeAmplificationLevelState(UUID playerId) {
        if (playerId != null) {
            LIFE_AMP_LAST_LEVEL.remove(playerId);
            LIFE_AMP_LEVEL_CACHE.remove(playerId);
        }
    }

    /**
     * 切维 / 重生 / 登录后强制重发夜视与幸运效果包。
     * 客户端 LocalPlayer 会被重建；服务端已有同档效果时 addEffect/update 不会发包，
     * 必须直接补发 ClientboundUpdateMobEffectPacket（对齐自由飞行的 onUpdateAbilities 强制刷新）。
     */
    public static void forceResyncLifeAmplificationEffects(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || player.level().isClientSide) {
            return;
        }
        MobEffectInstance nightVision = player.getEffect(MobEffects.NIGHT_VISION);
        if (nightVision != null) {
            serverPlayer.connection.send(new ClientboundUpdateMobEffectPacket(player.getId(), nightVision, false));
        }
        MobEffectInstance luck = player.getEffect(MobEffects.LUCK);
        if (luck != null) {
            serverPlayer.connection.send(new ClientboundUpdateMobEffectPacket(player.getId(), luck, false));
        }
    }

    private static int getTotalEnchantmentLevelOnArmor(Player player) {
        int totalLevel = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            totalLevel += EnchantUtil.getLevelOnPlayerSlot(ModEnchantments.LIFE_AMPLIFICATION, player, slot);
        }
        return totalLevel;
    }

    private static void applyLifeAmplificationEffects(Player player) {
        if (!player.hasEffect(MobEffects.NIGHT_VISION)
                || player.getEffect(MobEffects.NIGHT_VISION).getDuration() <= 200) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false));
        }
        if (!player.hasEffect(MobEffects.LUCK)) {
            player.addEffect(new MobEffectInstance(MobEffects.LUCK, Integer.MAX_VALUE, 3, false, false));
        }
        applyLifeLuckAttribute(player);
        repairLifeAmplificationEquipment(player);
    }

    /** 生命增幅生效时：把全身可损耗且已有损耗的装备耐久修回 0。 */
    static void repairLifeAmplificationEquipment(Player player) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isEmpty() && stack.isDamageableItem() && stack.getDamageValue() > 0) {
                stack.setDamageValue(0);
            }
        }
    }

    private static void clearLifeAmplificationEffects(Player player) {
        clearLifeLuckAttribute(player);
        // 脱下附魔甲时移除本特性挂上的超长效果，避免残留（不改生效期数值）
        MobEffectInstance nightVision = player.getEffect(MobEffects.NIGHT_VISION);
        if (nightVision != null && nightVision.getDuration() >= Integer.MAX_VALUE - 1000) {
            player.removeEffect(MobEffects.NIGHT_VISION);
        }
        MobEffectInstance luck = player.getEffect(MobEffects.LUCK);
        if (luck != null && luck.getDuration() >= Integer.MAX_VALUE - 1000 && luck.getAmplifier() == 3) {
            player.removeEffect(MobEffects.LUCK);
        }
    }

    private static void applyLifeLuckAttribute(Player player) {
        AttributeInstance luck = player.getAttribute(Attributes.LUCK);
        if (luck == null) {
            return;
        }
        double luckAmount = LIFE_LUCK_AMOUNT;
        AttributeModifier existingModifier = luck.getModifier(LIFE_LUCK_MODIFIER_ID);
        if (existingModifier == null) {
            luck.addTransientModifier(new AttributeModifier(
                    LIFE_LUCK_MODIFIER_ID, luckAmount, AttributeModifier.Operation.ADD_VALUE));
        } else if (existingModifier.amount() != luckAmount) {
            luck.removeModifier(LIFE_LUCK_MODIFIER_ID);
            luck.addTransientModifier(new AttributeModifier(
                    LIFE_LUCK_MODIFIER_ID, luckAmount, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    private static void clearLifeLuckAttribute(Player player) {
        AttributeInstance luck = player.getAttribute(Attributes.LUCK);
        if (luck != null && luck.getModifier(LIFE_LUCK_MODIFIER_ID) != null) {
            luck.removeModifier(LIFE_LUCK_MODIFIER_ID);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingDamagePreForLifeAmplification(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Integer cached = LIFE_AMP_LEVEL_CACHE.get(player.getUUID());
        int totalLifeLevel = cached != null
                ? cached
                : getTotalEnchantmentLevelOnArmor(player);
        if (totalLifeLevel > 0) {
            float reduction = totalLifeLevel * PERCENT_PER_LEVEL / 100.0f;
            if (reduction > 1.0f) {
                reduction = 1.0f;
            }
            event.setNewDamage(event.getNewDamage() * (1.0f - reduction));
        }
    }

    static boolean isArmorSlot(EquipmentSlot slot) {
        return slot == EquipmentSlot.HEAD
                || slot == EquipmentSlot.CHEST
                || slot == EquipmentSlot.LEGS
                || slot == EquipmentSlot.FEET;
    }
}
