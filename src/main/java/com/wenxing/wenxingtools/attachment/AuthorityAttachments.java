package com.wenxing.wenxingtools.attachment;

import com.wenxing.wenxingtools.WenXingTools;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jetbrains.annotations.Nullable;

public final class AuthorityAttachments {
    private AuthorityAttachments() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, WenXingTools.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<AuthorityData>> AUTHORITY_DATA =
            ATTACHMENT_TYPES.register("authority_data", () ->
                    AttachmentType.builder(AuthorityData::new)
                            .serialize(new AuthorityDataSerializer())
                            .copyOnDeath()
                            .build());

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }

    @Nullable
    public static IAuthorityData getData(@Nullable ServerPlayer player) {
        if (player == null) {
            return null;
        }
        return player.getData(AUTHORITY_DATA.get());
    }

    @Nullable
    public static IAuthorityData getDataClient(@Nullable Player player) {
        if (player == null) {
            return null;
        }
        return player.getData(AUTHORITY_DATA.get());
    }
}
