package com.wenxing.wenxingtools.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.common.reflect.TypeToken;
import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.attachment.AuthorityAttachments;
import com.wenxing.wenxingtools.attachment.IAuthorityData;
import com.wenxing.wenxingtools.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WhitelistManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<UUID> WHITELIST = ConcurrentHashMap.newKeySet();
    private static final Object IO_LOCK = new Object();
    private static volatile Path configPath;
    private static final TypeToken<Set<UUID>> WHITELIST_TYPE_TOKEN = new TypeToken<Set<UUID>>() {};

    private static Path configPath() {
        Path path = configPath;
        if (path != null) {
            return path;
        }
        synchronized (IO_LOCK) {
            if (configPath == null) {
                configPath = FMLPaths.CONFIGDIR.get().resolve("wenxingtools_whitelist.json");
            }
            return configPath;
        }
    }

    public static void load() {
        synchronized (IO_LOCK) {
            Path path = configPath();
            if (!Files.exists(path)) {
                return;
            }
            try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                Set<UUID> loaded = GSON.fromJson(reader, WHITELIST_TYPE_TOKEN.getType());
                if (loaded != null) {
                    WHITELIST.clear();
                    WHITELIST.addAll(loaded);
                }
            } catch (Exception e) {
                WenXingTools.LOGGER.error("Failed to load whitelist", e);
                try {
                    Path bak = path.resolveSibling(path.getFileName() + ".bad");
                    Files.move(path, bak, StandardCopyOption.REPLACE_EXISTING);
                    WenXingTools.LOGGER.warn("[wenxingtools] moved broken whitelist to {}", bak);
                } catch (IOException ignored) {
                }
            }
        }
    }

    public static void save() {
        synchronized (IO_LOCK) {
            Path tmp = configPath().resolveSibling(configPath().getFileName() + ".tmp");
            try {
                try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                    GSON.toJson(WHITELIST, writer);
                }
                Files.move(tmp, configPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                WenXingTools.LOGGER.error("Failed to save whitelist", e);
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                }
            }
        }
    }

    public static boolean add(UUID uuid) {
        synchronized (IO_LOCK) {
            if (WHITELIST.add(uuid)) {
                save();
                return true;
            }
            return false;
        }
    }

    public static boolean remove(UUID uuid) {
        synchronized (IO_LOCK) {
            if (!WHITELIST.remove(uuid)) {
                return false;
            }
            save();
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (!player.getUUID().equals(uuid)) {
                        continue;
                    }
                    player.displayClientMessage(Component.translatable("msg.wenxingtools.whitelist.revoked").withStyle(ChatFormatting.RED), true);
                    IAuthorityData data = AuthorityAttachments.getData(player);
                    if (data != null) {
                        data.setInvincibleCharacteristic(false);
                        data.setResourceCharacteristic(false);
                        data.setFreedomCharacteristic(false);
                        data.setKillAuraCharacteristic(false);
                    }
                    ModNetwork.syncAuthorityData(player);
                    break;
                }
            }
            return true;
        }
    }

    public static boolean contains(UUID uuid) {
        return WHITELIST.contains(uuid);
    }

    public static Set<UUID> getListView() {
        if (WHITELIST.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new HashSet<>(WHITELIST));
    }
}
