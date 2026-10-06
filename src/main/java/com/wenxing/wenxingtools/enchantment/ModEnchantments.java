package com.wenxing.wenxingtools.enchantment;

import com.wenxing.wenxingtools.WenXingTools;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEnchantments {
    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, WenXingTools.MODID);

    public static final RegistryObject<Enchantment> LIFE_AMPLIFICATION =
            ENCHANTMENTS.register("life_amplification", LifeAmplificationEnchantment::new);

    public static final RegistryObject<Enchantment> ATTACK_AMPLIFICATION =
            ENCHANTMENTS.register("attack_amplification", AttackAmplificationEnchantment::new);

    public static final RegistryObject<Enchantment> RESOURCE_AMPLIFICATION =
            ENCHANTMENTS.register("resource_amplification", ResourceAmplificationEnchantment::new);

    public static final RegistryObject<Enchantment> POTION_AMPLIFICATION =
            ENCHANTMENTS.register("potion_amplification", PotionAmplificationEnchantment::new);

    public static final RegistryObject<Enchantment> SPAWN_EGG_HARVEST =
            ENCHANTMENTS.register("spawn_egg_harvest", SpawnEggHarvestEnchantment::new);

    public static final RegistryObject<Enchantment> EFFECT_PURGE =
            ENCHANTMENTS.register("effect_purge", EffectPurgeEnchantment::new);

    public static final RegistryObject<Enchantment> ECHO_AMPLIFICATION =
            ENCHANTMENTS.register("echo_amplification", EchoAmplificationEnchantment::new);

    public static void register(IEventBus eventBus) {
        ENCHANTMENTS.register(eventBus);
    }
}
