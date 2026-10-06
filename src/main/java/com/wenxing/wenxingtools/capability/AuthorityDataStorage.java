package com.wenxing.wenxingtools.capability;

import com.wenxing.wenxingtools.WenXingTools;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

public final class AuthorityDataStorage {
    private AuthorityDataStorage() {}

    private static final String BACKUP_FOLDER_NAME = "wenxingtools_playerdata";
    private static final String BACKUP_FILE_EXT = ".wenxingtools.dat";
    private static final Object IO_LOCK = new Object();
    private static volatile Path cachedBackupFolder;

    private static Path getBackupFolder() {
        Path dir = cachedBackupFolder;
        if (dir != null) return dir;
        synchronized (IO_LOCK) {
            dir = cachedBackupFolder;
            if (dir != null) return dir;
            dir = FMLPaths.GAMEDIR.get().resolve(BACKUP_FOLDER_NAME);
            try {
                if (!Files.isDirectory(dir)) {
                    Files.createDirectories(dir);
                }
            } catch (IOException e) {
                WenXingTools.LOGGER.error("Failed to create authority data backup folder: {}", dir, e);
            }
            cachedBackupFolder = dir;
            return dir;
        }
    }

    private static Path getBackupFile(UUID uuid) {
        return getBackupFolder().resolve(uuid.toString() + BACKUP_FILE_EXT);
    }

    public static void saveBackup(ServerPlayer player) {
        if (player == null) {
            return;
        }
        player.getCapability(AuthorityDataProvider.AUTHORITY_DATA).ifPresent(data -> {
            if (!data.isDirty()) {
                return;
            }
            CompoundTag tag = data.serializeNBT();
            if (tag == null) {
                return;
            }
            Path file = getBackupFile(player.getUUID());
            synchronized (IO_LOCK) {
                Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
                try {
                    NbtIo.writeCompressed(tag, tmp.toFile());
                    try {
                        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
                    }
                    data.clearDirty();
                } catch (IOException e) {
                    WenXingTools.LOGGER.warn("Failed to save authority data backup for {}", player.getUUID(), e);
                    try {
                        Files.deleteIfExists(tmp);
                    } catch (IOException ignored) {
                    }
                }
            }
        });
    }

    public static boolean loadBackupIfMissing(ServerPlayer player, boolean primaryAlreadyLoaded) {
        if (player == null) {
            return false;
        }
        if (primaryAlreadyLoaded) {
            return false;
        }
        UUID uuid = player.getUUID();
        Path file = getBackupFile(uuid);
        synchronized (IO_LOCK) {
            if (!Files.isRegularFile(file)) {
                return false;
            }
            try {
                CompoundTag tag = NbtIo.readCompressed(file.toFile());
                if (tag == null || tag.isEmpty()) {
                    return false;
                }
                final boolean[] loaded = {false};
                player.getCapability(AuthorityDataProvider.AUTHORITY_DATA).ifPresent(data -> {
                    data.deserializeNBT(tag);
                    loaded[0] = true;
                });
                if (loaded[0]) {
                    WenXingTools.LOGGER.info("Restored authority data from backup for player {}", uuid);
                }
                return loaded[0];
            } catch (IOException e) {
                WenXingTools.LOGGER.warn("Failed to load authority data backup for {}", uuid, e);
                return false;
            }
        }
    }
}
