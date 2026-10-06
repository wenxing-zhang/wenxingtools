package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.enchantment.ModEnchantments;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = WenXingTools.MODID)
public final class LifeAmplificationEvents {


    private static final Map<UUID, Integer> LIFE_AMP_LAST_LEVEL = new ConcurrentHashMap<>();

    private static final Map<UUID, Integer> LIFE_AMP_LEVEL_CACHE = new ConcurrentHashMap<>();

    private LifeAmplificationEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            Integer cached = LIFE_AMP_LEVEL_CACHE.get(player.getUUID());
            int totalLifeLevel = cached != null ? cached : AmpSupport.getTotalEnchantmentLevelOnArmor(player, ModEnchantments.LIFE_AMPLIFICATION.get());
            if (totalLifeLevel > 0) {

                float reduction = totalLifeLevel * AmpConstants.PERCENT_PER_LEVEL / 100.0f;
                if (reduction > 1.0f) {
                    reduction = 1.0f;
                }
                event.setAmount(event.getAmount() * (1.0f - reduction));
            }
        }
    }


    static void syncLifeAmplification(Player player, int totalLifeLevel) {


        LIFE_AMP_LEVEL_CACHE.put(player.getUUID(), totalLifeLevel);
        Integer lastLifeLevel = LIFE_AMP_LAST_LEVEL.get(player.getUUID());
        if (lastLifeLevel == null || lastLifeLevel.intValue() != totalLifeLevel) {
            boolean hadLifeAmplification = lastLifeLevel != null && lastLifeLevel.intValue() > 0;
            LIFE_AMP_LAST_LEVEL.put(player.getUUID(), totalLifeLevel);
            if (totalLifeLevel > 0) {
                applyLifeAmplificationEffects(player);
            } else if (hadLifeAmplification) {

                clearLifeAmplificationEffects(player);
            }
        } else if (totalLifeLevel > 0) {


            repairLifeAmplificationEquipment(player);
        }
    }


    static void clearLifeAmplificationLevelState(UUID playerId) {
        if (playerId == null) {
            return;
        }
        LIFE_AMP_LAST_LEVEL.remove(playerId);
        LIFE_AMP_LEVEL_CACHE.remove(playerId);
    }

    /**
     * 切维 / 重生 / 登录后强制重发夜视与幸运效果包。
     * 客户端 LocalPlayer 会被重建；服务端已有同档效果时 addEffect/update 不会发包，
     * 必须直接补发 ClientboundUpdateMobEffectPacket（对齐自由飞行的 onUpdateAbilities 强制刷新）。
     */
    static void forceResyncLifeAmplificationEffects(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || player.level().isClientSide) {
            return;
        }
        MobEffectInstance nightVision = player.getEffect(MobEffects.NIGHT_VISION);
        if (nightVision != null) {
            // 1.20.1 构造器为 (entityId, effect)，无 blend 参数
            serverPlayer.connection.send(new ClientboundUpdateMobEffectPacket(player.getId(), nightVision));
        }
        MobEffectInstance luck = player.getEffect(MobEffects.LUCK);
        if (luck != null) {
            serverPlayer.connection.send(new ClientboundUpdateMobEffectPacket(player.getId(), luck));
        }
    }

    static void applyLifeAmplificationEffects(Player player) {
        if (!player.hasEffect(MobEffects.NIGHT_VISION) || player.getEffect(MobEffects.NIGHT_VISION).getDuration() <= 200) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false));
        }


        if (!player.hasEffect(MobEffects.LUCK)) {
            player.addEffect(new MobEffectInstance(MobEffects.LUCK, Integer.MAX_VALUE, 3, false, false));
        }

        applyLifeLuckAttribute(player);


        repairLifeAmplificationEquipment(player);
    }


    static void repairLifeAmplificationEquipment(Player player) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isEmpty() && stack.isDamageableItem() && stack.getDamageValue() > 0) {
                stack.setDamageValue(0);
            }
        }
    }

    static void clearLifeAmplificationEffects(Player player) {
        clearLifeLuckAttribute(player);

        MobEffectInstance nightVision = player.getEffect(MobEffects.NIGHT_VISION);
        if (nightVision != null && nightVision.getDuration() == Integer.MAX_VALUE) {
            player.removeEffect(MobEffects.NIGHT_VISION);
        }
        MobEffectInstance luck = player.getEffect(MobEffects.LUCK);
        if (luck != null && luck.getDuration() == Integer.MAX_VALUE) {
            player.removeEffect(MobEffects.LUCK);
        }
    }

    private static void applyLifeLuckAttribute(Player player) {
        AttributeInstance luck = player.getAttribute(Attributes.LUCK);
        if (luck == null) {
            return;
        }

        double luckAmount = AmpConstants.LIFE_LUCK_AMOUNT;
        AttributeModifier existingModifier = luck.getModifier(AmpConstants.LIFE_LUCK_MODIFIER_ID);
        if (existingModifier == null) {
            luck.addTransientModifier(new AttributeModifier(AmpConstants.LIFE_LUCK_MODIFIER_ID, "wenxingtools_life_amplification_luck", luckAmount, AttributeModifier.Operation.ADDITION));
        } else if (existingModifier.getAmount() != luckAmount) {
            luck.removeModifier(AmpConstants.LIFE_LUCK_MODIFIER_ID);
            luck.addTransientModifier(new AttributeModifier(AmpConstants.LIFE_LUCK_MODIFIER_ID, "wenxingtools_life_amplification_luck", luckAmount, AttributeModifier.Operation.ADDITION));
        }
    }

    private static void clearLifeLuckAttribute(Player player) {
        AttributeInstance luck = player.getAttribute(Attributes.LUCK);
        if (luck == null) {
            return;
        }
        if (luck.getModifier(AmpConstants.LIFE_LUCK_MODIFIER_ID) != null) {
            luck.removeModifier(AmpConstants.LIFE_LUCK_MODIFIER_ID);
        }
    }
}
