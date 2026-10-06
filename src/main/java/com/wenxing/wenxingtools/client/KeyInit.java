package com.wenxing.wenxingtools.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
public final class KeyInit {
    private KeyInit() {}

    public static final String KEY_CATEGORY_WENXING = "key.category.wenxingtools.wenxing";

    public static final KeyMapping KEY_INVINCIBLE = new KeyMapping(
            "key.wenxingtools.invincible",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_KP_1,
            KEY_CATEGORY_WENXING
    );

    public static final KeyMapping KEY_RESOURCE = new KeyMapping(
            "key.wenxingtools.resource",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_KP_2,
            KEY_CATEGORY_WENXING
    );

    /** 资源增幅附魔开关（主手持有附魔物品时切换） */
    public static final KeyMapping KEY_RESOURCE_AMP = new KeyMapping(
            "key.wenxingtools.resource_amp",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_KP_0,
            KEY_CATEGORY_WENXING
    );

    public static final KeyMapping KEY_FREEDOM = new KeyMapping(
            "key.wenxingtools.freedom",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_KP_3,
            KEY_CATEGORY_WENXING
    );

    public static final KeyMapping KEY_KILL_AURA = new KeyMapping(
            "key.wenxingtools.kill_aura",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_KP_4,
            KEY_CATEGORY_WENXING
    );
}
