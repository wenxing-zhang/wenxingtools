package com.wenxing.wenxingtools.menu;

import com.wenxing.wenxingtools.WenXingTools;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 菜单类型注册。
 *
 * <p>类型参数写成 {@code AnvilMenu} 而非 {@code WenxingAnvilMenu}：客户端复用原版
 * {@code AnvilScreen}，而 {@code MenuScreens.ScreenConstructor<M, U extends MenuAccess<M>>}
 * 要求 {@code U implements MenuAccess<M>}；{@code AnvilScreen} 是 {@code MenuAccess<AnvilMenu>}，
 * 泛型不变型，写成子类会编译失败。注册表按 id 找工厂，工厂返回子类实例即可。
 *
 * <p>工厂用 {@link IMenuTypeExtension#create} 读服务端附带的方块坐标：两端构造出的菜单都带
 * {@link ContainerLevelAccess}，{@code stillValid} 因而能按「玩家是否还在该方块附近」判定。
 */
public final class ModMenuTypes {
    private ModMenuTypes() {
    }

    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, WenXingTools.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<AnvilMenu>> WENXING_ANVIL =
            MENU_TYPES.register("wenxing_anvil",
                    () -> IMenuTypeExtension.create((containerId, inventory, data) ->
                            new WenxingAnvilMenu(containerId, inventory,
                                    ContainerLevelAccess.create(inventory.player.level(), data.readBlockPos()))));

    public static void register(IEventBus eventBus) {
        MENU_TYPES.register(eventBus);
    }
}
