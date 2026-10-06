package com.wenxing.wenxingtools.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.attachment.AuthorityAttachments;
import com.wenxing.wenxingtools.attachment.IAuthorityData;
import com.wenxing.wenxingtools.enchantment.EnchantUtil;
import com.wenxing.wenxingtools.network.ModNetwork;
import com.wenxing.wenxingtools.util.ModEnums.KillAuraMode;
import com.wenxing.wenxingtools.util.PermissionUtil;
import com.wenxing.wenxingtools.util.WhitelistManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@EventBusSubscriber(modid = WenXingTools.MODID)
public class ModCommands {

    private static final SuggestionProvider<CommandSourceStack> ENCHANTMENT_SUGGESTIONS = (context, builder) -> {
        var server = context.getSource().getServer();
        var lookup = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        lookup.listElementIds().forEach(key -> builder.suggest(key.location().toString()));
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> WHITELIST_TARGET_SUGGESTIONS = (context, builder) -> {
        if (context.getSource().getServer() != null) {
            for (ServerPlayer player : context.getSource().getServer().getPlayerList().getPlayers()) {
                builder.suggest(player.getGameProfile().getName());
            }
        }
        for (UUID uuid : WhitelistManager.getListView()) {
            builder.suggest(uuid.toString());
        }
        return builder.buildFuture();
    };

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("wenxingtools")
                .requires(source -> {
                    // 控制台/命令方块/RCON：无实体时用原版 OP 等级，避免短路导致无法管理
                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                        return source.hasPermission(2);
                    }
                    try {
                        return PermissionUtil.hasPermission(player);
                    } catch (Exception e) {
                        return source.hasPermission(2);
                    }
                })
                .then(Commands.literal("invincibleCharacteristics")
                        .then(Commands.literal("on").executes(ctx -> setInvincible(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> setInvincible(ctx, false))))
                .then(Commands.literal("resourceCharacteristics")
                        .then(Commands.literal("on").executes(ctx -> setResource(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> setResource(ctx, false))))
                .then(Commands.literal("freedomCharacteristics")
                        .then(Commands.literal("on").executes(ctx -> setFreedom(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> setFreedom(ctx, false))))
                .then(Commands.literal("killAuraCharacteristics")
                        .then(Commands.literal("on").executes(ctx -> setKillAura(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> setKillAura(ctx, false))))
                .then(Commands.literal("setKillAuraMode")
                        .then(Commands.literal("all").executes(ctx -> setKillAuraMode(ctx, KillAuraMode.ALL)))
                        .then(Commands.literal("hostile").executes(ctx -> setKillAuraMode(ctx, KillAuraMode.HOSTILE))))
                .then(Commands.literal("setResourceCharacteristics")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 1000))
                                .executes(ctx -> setResourceMultiplier(ctx, IntegerArgumentType.getInteger(ctx, "amount")))))
                .then(Commands.literal("whitelist")
                        .then(Commands.literal("add")
                                .then(Commands.argument("target", StringArgumentType.word())
                                        .suggests(WHITELIST_TARGET_SUGGESTIONS)
                                        .executes(ctx -> addToWhitelist(ctx, StringArgumentType.getString(ctx, "target")))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("target", StringArgumentType.word())
                                        .suggests(WHITELIST_TARGET_SUGGESTIONS)
                                        .executes(ctx -> removeFromWhitelist(ctx, StringArgumentType.getString(ctx, "target")))))
                        .then(Commands.literal("list").executes(ModCommands::listWhitelist)))
                .then(Commands.literal("cenchant")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .then(Commands.argument("enchantment", ResourceLocationArgument.id())
                                        .suggests(ENCHANTMENT_SUGGESTIONS)
                                        .executes(ctx -> executeCEnchant(ctx,
                                                EntityArgument.getEntities(ctx, "targets"),
                                                ResourceLocationArgument.getId(ctx, "enchantment"),
                                                1))
                                        .then(Commands.argument("level", IntegerArgumentType.integer(1))
                                                .executes(ctx -> executeCEnchant(ctx,
                                                        EntityArgument.getEntities(ctx, "targets"),
                                                        ResourceLocationArgument.getId(ctx, "enchantment"),
                                                        IntegerArgumentType.getInteger(ctx, "level")))))))
        );
    }

    private static int setInvincible(CommandContext<CommandSourceStack> ctx, boolean enable) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        IAuthorityData data = AuthorityAttachments.getData(player);
        if (data != null) {
            data.setInvincibleCharacteristic(enable);
            if (enable) {
                data.setLockedHealth(player.getHealth());
            }
            sendToggleFeedback(player, "msg.wenxingtools.label.invincible", enable, null);
            ModNetwork.syncAuthorityData(player);
        }
        return 1;
    }

    private static int setResource(CommandContext<CommandSourceStack> ctx, boolean enable) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        IAuthorityData data = AuthorityAttachments.getData(player);
        if (data != null) {
            data.setResourceCharacteristic(enable);
            Component detail = enable ? Component.literal("x" + data.getResourceMultiplier()) : null;
            sendToggleFeedback(player, "msg.wenxingtools.label.resource", enable, detail);
            ModNetwork.syncAuthorityData(player);
        }
        return 1;
    }

    private static int setFreedom(CommandContext<CommandSourceStack> ctx, boolean enable) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        IAuthorityData data = AuthorityAttachments.getData(player);
        if (data != null) {
            data.setFreedomCharacteristic(enable);
            if (enable) {
                player.getAbilities().mayfly = true;
            } else if (!player.isCreative() && !player.isSpectator()) {
                player.getAbilities().mayfly = false;
                player.getAbilities().flying = false;
            }
            player.onUpdateAbilities();
            sendToggleFeedback(player, "msg.wenxingtools.label.freedom", enable, null);
            ModNetwork.syncAuthorityData(player);
        }
        return 1;
    }

    private static int setKillAura(CommandContext<CommandSourceStack> ctx, boolean enable) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        IAuthorityData data = AuthorityAttachments.getData(player);
        if (data != null) {
            data.setKillAuraCharacteristic(enable);
            Component modeText = Component.translatable(
                    KillAuraMode.fromPersistentId(data.getKillAuraMode()).translationKey());
            sendToggleFeedback(player, "msg.wenxingtools.label.kill_aura", enable, enable ? modeText : null);
            ModNetwork.syncAuthorityData(player);
        }
        return 1;
    }

    private static int setKillAuraMode(CommandContext<CommandSourceStack> ctx, KillAuraMode mode) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        IAuthorityData data = AuthorityAttachments.getData(player);
        if (data != null) {
            data.setKillAuraMode(mode.persistentId());
            player.sendSystemMessage(Component.translatable(
                    "msg.wenxingtools.kill_aura.mode_changed",
                    Component.translatable(mode.translationKey())
            ).withStyle(ChatFormatting.AQUA));
            ModNetwork.syncAuthorityData(player);
        }
        return 1;
    }

    private static int setResourceMultiplier(CommandContext<CommandSourceStack> ctx, int amount) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        IAuthorityData data = AuthorityAttachments.getData(player);
        if (data != null) {
            data.setResourceMultiplier(amount);
            sendStatusFeedback(player, "msg.wenxingtools.label.resource_multiplier",
                    Component.translatable("msg.wenxingtools.resource.multiplier_set", amount),
                    ChatFormatting.YELLOW);
            ModNetwork.syncAuthorityData(player);
        }
        return 1;
    }

    private static int addToWhitelist(CommandContext<CommandSourceStack> ctx, String targetInput) {
        CommandSourceStack source = ctx.getSource();
        WhitelistTarget target = resolveWhitelistTarget(source, targetInput);
        if (target == null) {
            source.sendFailure(Component.translatable("msg.wenxingtools.whitelist.invalid_target").withStyle(ChatFormatting.RED));
            return 0;
        }
        UUID uuid = target.uuid();
        String name = target.name();
        String display = name != null ? name + " (" + uuid + ")" : uuid.toString();
        if (WhitelistManager.add(uuid)) {
            source.sendSuccess(() -> Component.translatable("msg.wenxingtools.whitelist.added", display).withStyle(ChatFormatting.GREEN), true);
        } else {
            source.sendFailure(Component.translatable("msg.wenxingtools.whitelist.already", display).withStyle(ChatFormatting.RED));
        }
        return 1;
    }

    private static int removeFromWhitelist(CommandContext<CommandSourceStack> ctx, String targetInput) {
        CommandSourceStack source = ctx.getSource();
        WhitelistTarget target = resolveWhitelistTarget(source, targetInput);
        if (target == null) {
            source.sendFailure(Component.translatable("msg.wenxingtools.whitelist.invalid_target").withStyle(ChatFormatting.RED));
            return 0;
        }
        String display = target.name() != null ? target.name() + " (" + target.uuid() + ")" : target.uuid().toString();
        if (WhitelistManager.remove(target.uuid())) {
            source.sendSuccess(() -> Component.translatable("msg.wenxingtools.whitelist.removed", display).withStyle(ChatFormatting.GREEN), true);
        } else {
            source.sendFailure(Component.translatable("msg.wenxingtools.whitelist.not_in", display).withStyle(ChatFormatting.RED));
        }
        return 1;
    }

    private static int listWhitelist(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (WhitelistManager.getListView().isEmpty()) {
            source.sendSuccess(() -> Component.translatable("msg.wenxingtools.whitelist.empty").withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }
        source.sendSuccess(() -> Component.translatable("msg.wenxingtools.whitelist.header").withStyle(ChatFormatting.YELLOW), false);
        WhitelistManager.getListView().forEach(uuid -> {
            String name = null;
            if (source.getServer() != null && source.getServer().getProfileCache() != null) {
                Optional<GameProfile> optionalProfile = source.getServer().getProfileCache().get(uuid);
                if (optionalProfile.isPresent()) {
                    name = optionalProfile.get().getName();
                }
            }
            String display = name != null ? name + " (" + uuid + ")" : uuid.toString();
            source.sendSuccess(() -> Component.literal("- " + display), false);
        });
        return 1;
    }

    private static WhitelistTarget resolveWhitelistTarget(CommandSourceStack source, String input) {
        if (source.getServer() != null) {
            ServerPlayer onlinePlayer = source.getServer().getPlayerList().getPlayerByName(input);
            if (onlinePlayer != null) {
                return new WhitelistTarget(onlinePlayer.getUUID(), onlinePlayer.getGameProfile().getName());
            }
        }
        try {
            return new WhitelistTarget(UUID.fromString(input), null);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static void sendToggleFeedback(ServerPlayer player, String labelKey, boolean enabled, Component detail) {
        ChatFormatting color = enabled ? ChatFormatting.GREEN : ChatFormatting.RED;
        Component message = Component.translatable(labelKey)
                .append(": ")
                .append(Component.translatable(enabled ? "msg.wenxingtools.state.on" : "msg.wenxingtools.state.off"));
        if (detail != null) {
            message = message.copy().append(" (").append(detail).append(")");
        }
        player.displayClientMessage(message.copy().withStyle(color), true);
    }

    private static void sendStatusFeedback(ServerPlayer player, String labelKey, Component detail, ChatFormatting color) {
        player.displayClientMessage(Component.translatable(labelKey).append(": ").append(detail).withStyle(color), true);
    }

    private static int executeCEnchant(
            CommandContext<CommandSourceStack> ctx,
            Collection<? extends Entity> targets,
            ResourceLocation enchantmentId,
            int level) {
        var lookup = ctx.getSource().getServer().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Optional<Holder.Reference<Enchantment>> holderOpt =
                lookup.get(ResourceKey.create(Registries.ENCHANTMENT, enchantmentId));
        if (holderOpt.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("msg.wenxingtools.cenchant.invalid_id", enchantmentId.toString()).withStyle(ChatFormatting.RED));
            return 0;
        }

        int count = 0;
        int skippedNonLiving = 0;
        for (Entity entity : targets) {
            if (!(entity instanceof LivingEntity living)) {
                skippedNonLiving++;
                continue;
            }
            ItemStack stack = living.getMainHandItem();
            if (!stack.isEmpty()) {
                EnchantUtil.applyEnchantmentOnStack(
                        stack,
                        ResourceKey.create(Registries.ENCHANTMENT, enchantmentId),
                        level,
                        living);
                count++;
            }
        }

        final int finalCount = count;
        if (finalCount > 0) {
            Component msg = Component.translatable("msg.wenxingtools.cenchant.success", finalCount)
                    .withStyle(ChatFormatting.GREEN)
                    .append(enchantmentId.toString())
                    .append(Component.literal(" " + level).withStyle(ChatFormatting.GRAY));
            if (skippedNonLiving > 0) {
                msg = msg.copy().append(Component.translatable("msg.wenxingtools.cenchant.skipped_non_living", skippedNonLiving).withStyle(ChatFormatting.YELLOW));
            }
            final Component fMsg = msg;
            ctx.getSource().sendSuccess(() -> fMsg, true);
        } else {
            Component err = Component.translatable("msg.wenxingtools.cenchant.no_item").withStyle(ChatFormatting.RED);
            if (skippedNonLiving > 0 && targets.size() == skippedNonLiving) {
                err = Component.translatable("msg.wenxingtools.cenchant.all_non_living").withStyle(ChatFormatting.RED);
            }
            ctx.getSource().sendFailure(err);
        }
        return finalCount;
    }

    private record WhitelistTarget(UUID uuid, String name) {
    }
}
