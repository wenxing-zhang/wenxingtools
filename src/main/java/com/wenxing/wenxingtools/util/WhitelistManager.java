package com.wenxing.wenxingtools.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.capability.AuthorityDataProvider;
import com.wenxing.wenxingtools.network.PacketHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.io.IOException;
import java.io.Reader;
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
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("wenxingtools_whitelist.json");
    private static final Object IO_LOCK = new Object();
    private static final com.google.common.reflect.TypeToken<Set<UUID>> WHITELIST_TYPE_TOKEN = new com.google.common.reflect.TypeToken<Set<UUID>>(){};

    public static void load() {
        synchronized (IO_LOCK) {
            Path path = CONFIG_PATH;
            if (!Files.exists(path)) {
                return;
            }
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                Set<UUID> loaded = GSON.fromJson(reader, WHITELIST_TYPE_TOKEN.getType());
                WHITELIST.clear();
                if (loaded != null) {
                    WHITELIST.addAll(loaded);
                }
            } catch (Exception e) {

                WenXingTools.LOGGER.error("Failed to load whitelist, backing up corrupt file", e);
                try {
                    Path bak = path.resolveSibling(path.getFileName() + ".corrupt.bak");
                    Files.move(path, bak, StandardCopyOption.REPLACE_EXISTING);
                    WenXingTools.LOGGER.warn("Corrupt whitelist moved to {}", bak.getFileName());
                } catch (IOException io) {
                    WenXingTools.LOGGER.warn("Could not backup corrupt whitelist", io);
                }
            }
        }
    }

    public static void save() {
        synchronized (IO_LOCK) {
            Path tmp = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName() + ".tmp");
            try {
                try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                    GSON.toJson(WHITELIST, writer);
                }
                try {
                    Files.move(tmp, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                    Files.move(tmp, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
                }
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
        boolean removed;
        synchronized (IO_LOCK) {
            removed = WHITELIST.remove(uuid);
            if (removed) {
                save();
            }
        }
        if (removed) {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (player.getUUID().equals(uuid)) {
                        player.displayClientMessage(Component.literal("权限被撤销：所有特性已关闭。").withStyle(ChatFormatting.RED), true);
                        player.getCapability(AuthorityDataProvider.AUTHORITY_DATA).ifPresent(data -> {
                            data.setInvincibleCharacteristic(false);
                            data.setResourceCharacteristic(false);
                            data.setFreedomCharacteristic(false);
                            data.setKillAuraCharacteristic(false);
                        });
                        PacketHandler.syncAuthorityData(player);
                        break;
                    }
                }
            }
        }
        return removed;
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

    public static Set<UUID> getList() {
        return new HashSet<>(WHITELIST);
    }
}
