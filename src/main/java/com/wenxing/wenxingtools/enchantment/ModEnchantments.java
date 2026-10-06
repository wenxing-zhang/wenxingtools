package com.wenxing.wenxingtools.enchantment;

import com.wenxing.wenxingtools.WenXingTools;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;

public final class ModEnchantments {
    private ModEnchantments() {}

    public static final ResourceKey<Enchantment> LIFE_AMPLIFICATION = key("life_amplification");
    public static final ResourceKey<Enchantment> ATTACK_AMPLIFICATION = key("attack_amplification");
    public static final ResourceKey<Enchantment> RESOURCE_AMPLIFICATION = key("resource_amplification");
    public static final ResourceKey<Enchantment> POTION_AMPLIFICATION = key("potion_amplification");
    /** 掠蛋：击杀掉落对应刷怪蛋，数量 = 等级（与 Forge 1.20.1 同 ID） */
    public static final ResourceKey<Enchantment> SPAWN_EGG_HARVEST = key("spawn_egg_harvest");
    /** 净化增幅：命中清药水并开 10×等级 秒持续窗；255 为核清档 */
    public static final ResourceKey<Enchantment> EFFECT_PURGE = key("effect_purge");
    /** 回响增幅：命中后追加 10×等级 次虚空伤害 */
    public static final ResourceKey<Enchantment> ECHO_AMPLIFICATION = key("echo_amplification");

    private static ResourceKey<Enchantment> key(String name) {
        return ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.fromNamespaceAndPath(WenXingTools.MODID, name));
    }
}
