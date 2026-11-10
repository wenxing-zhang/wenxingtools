package com.wenxing.wenxingtools.menu;

import com.wenxing.wenxingtools.WenXingTools;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 菜单类型注册。
 *
 * <p>类型参数写成 {@code AnvilMenu} 而非 {@code WenxingAnvilMenu}：客户端复用原版
 * {@code AnvilScreen}，而 {@code MenuScreens.ScreenConstructor<M, U extends MenuAccess<M>>}
 * 要求 {@code U implements MenuAccess<M>}；{@code AnvilScreen} 是 {@code MenuAccess<AnvilMenu>}，
 * 泛型不变型，写成子类会编译失败。注册表按 id 找工厂，工厂返回子类实例即可。
 */
public final class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, WenXingTools.MODID);

    public static final RegistryObject<MenuType<AnvilMenu>> WENXING_ANVIL =
            MENU_TYPES.register("wenxing_anvil",
                    () -> new MenuType<>((containerId, inventory) -> new WenxingAnvilMenu(containerId, inventory),
                            FeatureFlags.DEFAULT_FLAGS));

    private ModMenuTypes() {
    }

    public static void register(IEventBus eventBus) {
        MENU_TYPES.register(eventBus);
    }
}
