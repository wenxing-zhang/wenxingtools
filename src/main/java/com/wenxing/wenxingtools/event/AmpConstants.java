package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.WenXingTools;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.UUID;

public final class AmpConstants {


    public static final float PERCENT_PER_LEVEL = 1.0f;


    public static final int RESOURCE_XP_CAP = 2000000000;


    public static final long RESOURCE_XP_MULTIPLIER = 100L;


    /** 经验增幅与方块破坏的倍率系数：可堆叠物品每级 ×100（击杀掉落改用 {@link #KILL_FACTOR_STACKABLE_PER_COPY}） */
    public static final int RESOURCE_FACTOR_STACKABLE_PER_LEVEL = 100;


    /**
     * 经验增幅与方块破坏的倍率系数：不可堆叠物品每级 ×10。
     * <p>不可堆叠物品若吃满 ×100，会在单个 MultiStack 实体里塞进上百个单件 ItemStack，
     * NBT 与同步包体量失控，故单独降档。
     */
    public static final int RESOURCE_FACTOR_UNSTACKABLE_PER_LEVEL = 10;


    /**
     * 击杀掉落复制份数：一次击杀的产物按「相当于击杀 4 个生物」计。
     * <p>作用于 {@code LivingDropsEvent} 捕获到的<b>全部来源</b>（战利品表 / 硬编码 / 装备 / GLM / 数据包改写 / 事件追加），
     * 因此不能改为「重跑战利品表 N 次」——那样只有战利品表来源能拿到这份数。
     */
    public static final int KILL_DROP_COPIES = 4;


    /** 击杀掉落每份的倍率系数：可堆叠物品每级 ×25（4 份合计 level×100） */
    public static final int KILL_FACTOR_STACKABLE_PER_COPY = 25;


    /** 击杀掉落每份的倍率系数：不可堆叠物品每级 ×5（4 份合计 level×20） */
    public static final int KILL_FACTOR_UNSTACKABLE_PER_COPY = 5;


    public static final double LIFE_LUCK_AMOUNT = 1024.0D;


    public static final UUID LIFE_LUCK_MODIFIER_ID = UUID.fromString("d8a7b0d5-6b2b-4f10-93cd-6c1e6fb9a7d0");


    public static final long POTION_AMPLIFICATION_HOUR_TICKS = 72000L;


    public static final int POTION_AMPLIFICATION_SAFE_DURATION_CAP = 48 * 72_000;


    public static final String RESOURCE_AMPLIFICATION_PLAYER_TAG = WenXingTools.MODID + ":resource_amp_enabled";


    public static final int RESOURCE_AMPLIFICATION_SWITCH_COOLDOWN_MS = 250;


    public static final EquipmentSlot[] ARMOR_SLOTS = new EquipmentSlot[]{
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    private AmpConstants() {
    }
}
