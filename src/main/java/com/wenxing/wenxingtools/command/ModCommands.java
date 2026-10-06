package com.wenxing.wenxingtools.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.capability.AuthorityDataProvider;
import com.wenxing.wenxingtools.capability.IAuthorityData;
import com.wenxing.wenxingtools.network.PacketHandler;
import com.wenxing.wenxingtools.util.ModEnums;
import com.wenxing.wenxingtools.util.ModEnums.KillAuraMode;
import com.wenxing.wenxingtools.util.PermissionUtil;
import com.wenxing.wenxingtools.util.WhitelistManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = WenXingTools.MODID)
public class ModCommands {

    private static final SuggestionProvider<CommandSourceStack> ENCHANTMENT_SUGGESTIONS = (context, builder) -> {
        for (ResourceLocation id : BuiltInRegistries.ENCHANTMENT.keySet()) {
            builder.suggest(id.toString());
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> WHITELIST_TARGET_SUGGESTIONS = (context, builder) -> {
        if (context.getSource().getServer() != null) {
            for (ServerPlayer player : context.getSource().getServer().getPlayerList().getPlayers()) {
                builder.suggest(player.getGameProfile().getName());
            }
        }
        for (UUID uuid : WhitelistManager.getList()) {
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
                    Entity entity = source.getEntity();
                    if (entity instanceof ServerPlayer player) {
                        return PermissionUtil.hasPermission(player);
                    }
                    return source.hasPermission(2);
                })


                .then(Commands.literal("invincibleCharacteristics")
                        .then(Commands.literal("on").executes(ctx -> setInvincible(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> setInvincible(ctx, false)))
                )


                .then(Commands.literal("resourceCharacteristics")
                        .then(Commands.literal("on").executes(ctx -> setResource(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> setResource(ctx, false)))
                )


                .then(Commands.literal("freedomCharacteristics")
                        .then(Commands.literal("on").executes(ctx -> setFreedom(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> setFreedom(ctx, false)))
                )


                .then(Commands.literal("killAuraCharacteristics")
                        .then(Commands.literal("on").executes(ctx -> setKillAura(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> setKillAura(ctx, false)))
                )


                .then(Commands.literal("setKillAuraMode")
                        .then(Commands.literal("all").executes(ctx -> setKillAuraMode(ctx, KillAuraMode.ALL)))
                        .then(Commands.literal("hostile").executes(ctx -> setKillAuraMode(ctx, KillAuraMode.HOSTILE)))
                )


                .then(Commands.literal("setResourceCharacteristics")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 1000))
                                .executes(ctx -> setResourceMultiplier(ctx, IntegerArgumentType.getInteger(ctx, "amount")))
                        )
                )


                .then(Commands.literal("whitelist")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("add")
                                .then(Commands.argument("target", StringArgumentType.word())
                                        .suggests(WHITELIST_TARGET_SUGGESTIONS)
                                        .executes(ctx -> addToWhitelist(ctx, StringArgumentType.getString(ctx, "target")))
                                )
                        )
                        .then(Commands.literal("remove")
                                .then(Commands.argument("target", StringArgumentType.word())
                                        .suggests(WHITELIST_TARGET_SUGGESTIONS)
                                        .executes(ctx -> removeFromWhitelist(ctx, StringArgumentType.getString(ctx, "target")))
                                )
                        )

                        .then(Commands.literal("list")
                                .executes(ModCommands::listWhitelist)
                        )
                )


                .then(Commands.literal("cenchant")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .then(Commands.argument("enchantment", ResourceLocationArgument.id())
                                        .suggests(ENCHANTMENT_SUGGESTIONS)
                                        .executes(ctx -> executeCEnchant(ctx, EntityArgument.getEntities(ctx, "targets"), ResourceLocationArgument.getId(ctx, "enchantment"), 1))
                                        .then(Commands.argument("level", IntegerArgumentType.integer(1))
                                                .executes(ctx -> executeCEnchant(ctx, EntityArgument.getEntities(ctx, "targets"), ResourceLocationArgument.getId(ctx, "enchantment"), IntegerArgumentType.getInteger(ctx, "level")))
                                        )
                                )
                        )
                )
        );
    }

    private static boolean requireAuthorityData(ServerPlayer player) {
        if (AuthorityDataProvider.getData(player) == null) {
            player.sendSystemMessage(Component.literal("特性数据不可用，请重新登录后再试。").withStyle(ChatFormatting.RED));
            return false;
        }
        return true;
    }

    private static int setInvincible(CommandContext<CommandSourceStack> ctx, boolean enable) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!requireAuthorityData(player)) return 0;
        IAuthorityData data = AuthorityDataProvider.getData(player);
        data.setInvincibleCharacteristic(enable);
        if (enable) {
            data.setLockedHealth(player.getHealth());
        }
        sendToggleFeedback(player, "无敌特性", enable, null);
        PacketHandler.syncAuthorityData(player);
        return 1;
    }

    private static int setResource(CommandContext<CommandSourceStack> ctx, boolean enable) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!requireAuthorityData(player)) return 0;
        IAuthorityData data = AuthorityDataProvider.getData(player);
        data.setResourceCharacteristic(enable);
        String detail = enable ? "倍率: " + data.getResourceMultiplier() : null;
        sendToggleFeedback(player, "资源特性", enable, detail);
        PacketHandler.syncAuthorityData(player);
        return 1;
    }

    private static int setFreedom(CommandContext<CommandSourceStack> ctx, boolean enable) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!requireAuthorityData(player)) return 0;
        IAuthorityData data = AuthorityDataProvider.getData(player);
        data.setFreedomCharacteristic(enable);
        if (enable) {
            player.getAbilities().mayfly = true;
        } else if (!player.isCreative() && !player.isSpectator()) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
        }
        player.onUpdateAbilities();
        sendToggleFeedback(player, "自由特性", enable, null);
        PacketHandler.syncAuthorityData(player);
        return 1;
    }

    private static int setKillAura(CommandContext<CommandSourceStack> ctx, boolean enable) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!requireAuthorityData(player)) return 0;
        IAuthorityData data = AuthorityDataProvider.getData(player);
        data.setKillAuraCharacteristic(enable);
        String modeText = ModEnums.KillAuraMode.fromPersistentId(data.getKillAuraMode()).displayName();
        String detail = enable ? "当前模式: " + modeText : null;
        sendToggleFeedback(player, "杀戮光环特性", enable, detail);
        PacketHandler.syncAuthorityData(player);
        return 1;
    }

    private static int setKillAuraMode(CommandContext<CommandSourceStack> ctx, KillAuraMode mode) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!requireAuthorityData(player)) return 0;
        IAuthorityData data = AuthorityDataProvider.getData(player);
        data.setKillAuraMode(mode.persistentId());
        player.sendSystemMessage(Component.literal("杀戮光环模式已切换：" + mode.displayName()).withStyle(ChatFormatting.AQUA));
        PacketHandler.syncAuthorityData(player);
        return 1;
    }

    private static int setResourceMultiplier(CommandContext<CommandSourceStack> ctx, int amount) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!requireAuthorityData(player)) return 0;
        IAuthorityData data = AuthorityDataProvider.getData(player);
        data.setResourceMultiplier(amount);
        sendStatusFeedback(player, "资源特性倍率", "已设置为 " + amount, ChatFormatting.YELLOW);
        PacketHandler.syncAuthorityData(player);
        return 1;
    }

    private static int addToWhitelist(CommandContext<CommandSourceStack> ctx, String targetInput) {
        CommandSourceStack source = ctx.getSource();
        WhitelistTarget target = resolveWhitelistTarget(source, targetInput);
        if (target == null) {
            source.sendFailure(Component.literal("白名单目标必须是在线玩家名或 UUID。").withStyle(ChatFormatting.RED));
            return 0;
        }

        UUID uuid = target.uuid();
        String name = target.name();
        String display = name != null ? name + " (" + uuid + ")" : uuid.toString();
        if (WhitelistManager.add(uuid)) {
            source.sendSuccess(() -> Component.literal("已将 " + display + " 加入白名单。").withStyle(ChatFormatting.GREEN), true);
        } else {
            source.sendFailure(Component.literal(display + " 已在白名单中。").withStyle(ChatFormatting.RED));
        }
        return 1;
    }

    private static int removeFromWhitelist(CommandContext<CommandSourceStack> ctx, String targetInput) {
        CommandSourceStack source = ctx.getSource();
        WhitelistTarget target = resolveWhitelistTarget(source, targetInput);
        if (target == null) {
            source.sendFailure(Component.literal("白名单目标必须是在线玩家名或 UUID。").withStyle(ChatFormatting.RED));
            return 0;
        }

        UUID uuid = target.uuid();
        String name = target.name();
        return removeFromWhitelistInternal(ctx.getSource(), uuid, name);
    }

    private static int removeFromWhitelistInternal(CommandSourceStack source, UUID uuid, String name) {
        String display = name != null ? name + " (" + uuid + ")" : uuid.toString();
        if (WhitelistManager.remove(uuid)) {
            source.sendSuccess(() -> Component.literal("已将 " + display + " 从白名单移除。").withStyle(ChatFormatting.GREEN), true);
        } else {
            source.sendFailure(Component.literal(display + " 不在白名单中。").withStyle(ChatFormatting.RED));
        }
        return 1;
    }

    private static int listWhitelist(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (WhitelistManager.getListView().isEmpty()) {
            source.sendSuccess(() -> Component.literal("当前白名单为空。").withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("当前白名单：").withStyle(ChatFormatting.YELLOW), false);
        WhitelistManager.getListView().forEach(uuid -> {
            String name = null;
            if (source.getServer() != null && source.getServer().getProfileCache() != null) {
                var optionalProfile = source.getServer().getProfileCache().get(uuid);
                if (optionalProfile.isPresent()) {
                    GameProfile profile = optionalProfile.get();
                    name = profile.getName();
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

    private static void sendToggleFeedback(ServerPlayer player, String characteristicName, boolean enabled, String detail) {
        ChatFormatting color = enabled ? ChatFormatting.GREEN : ChatFormatting.RED;
        String message = characteristicName + "：" + (enabled ? "已开启" : "已关闭");
        if (detail != null && !detail.isEmpty()) {
            message += "（" + detail + "）";
        }
        player.displayClientMessage(Component.literal(message).withStyle(color), true);
    }

    private static void sendStatusFeedback(ServerPlayer player, String label, String detail, ChatFormatting color) {
        player.displayClientMessage(Component.literal(label + "：" + detail).withStyle(color), true);
    }

    private static int executeCEnchant(CommandContext<CommandSourceStack> ctx, Collection<? extends Entity> targets, ResourceLocation enchantmentId, int level) {
        Enchantment enchantment = BuiltInRegistries.ENCHANTMENT.get(enchantmentId);
        if (enchantment == null) {
            ctx.getSource().sendFailure(Component.literal("无效的附魔 ID: " + enchantmentId).withStyle(ChatFormatting.RED));
            return 0;
        }

        final int actualLevel = level;
        int count = 0;
        int skippedNonLiving = 0;
        for (Entity entity : targets) {
            if (!(entity instanceof LivingEntity living)) {
                skippedNonLiving++;
                continue;
            }
            ItemStack stack = living.getMainHandItem();
            if (!stack.isEmpty()) {
                Map<Enchantment, Integer> enchants = EnchantmentHelper.getEnchantments(stack);
                if (actualLevel > 0) {
                    enchants.put(enchantment, actualLevel);
                } else {
                    enchants.remove(enchantment);
                }
                EnchantmentHelper.setEnchantments(enchants, stack);
                count++;
            }
        }

        final int finalCount = count;
        if (finalCount > 0) {
            Component msg = Component.literal("已对 " + finalCount + " 个主手物品应用附魔：").withStyle(ChatFormatting.GREEN)
                    .append(enchantment.getFullname(actualLevel));
            if (skippedNonLiving > 0) {
                msg = msg.copy().append(Component.literal("（已跳过 " + skippedNonLiving + " 个非生物实体，只支持 LivingEntity）").withStyle(ChatFormatting.YELLOW));
            }
            final Component fMsg = msg;
            ctx.getSource().sendSuccess(() -> fMsg, true);
        } else {
            Component err;
            if (skippedNonLiving > 0 && targets.size() == skippedNonLiving) {
                err = Component.literal("目标全部是非生物实体，cenchant 只支持 LivingEntity（生物/玩家）。").withStyle(ChatFormatting.RED);
            } else {
                err = Component.literal("目标没有可附魔的主手物品。").withStyle(ChatFormatting.RED);
            }
            ctx.getSource().sendFailure(err);
        }

        return finalCount;
    }

    private record WhitelistTarget(UUID uuid, String name) {
    }
}
