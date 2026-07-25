package com.elfmcys.yesstevemodel.client;

import com.elfmcys.yesstevemodel.YesSteveModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientOnlySelection {

    private static final Map<String, Selection> selections = new ConcurrentHashMap<>();
    private static final Object lock = new Object();
    private static volatile boolean loaded;

    private record Selection(String modelId, String textureId) {
    }

    private ClientOnlySelection() {
    }

    public static void save(String modelId, String textureId) {
        String sessionKey = getSessionKey();
        if (sessionKey == null || StringUtils.isBlank(modelId)) return;
        load();
        selections.put(sessionKey, new Selection(modelId, StringUtils.defaultString(textureId)));
        persist();
    }

    public static boolean hasSelection() {
        return getSelection() != null;
    }

    public static String getModelId() {
        Selection selection = getSelection();
        return selection == null ? "" : selection.modelId();
    }

    public static String getTextureId() {
        Selection selection = getSelection();
        return selection == null ? "" : selection.textureId();
    }

    @Nullable
    private static Selection getSelection() {
        load();
        String sessionKey = getSessionKey();
        return sessionKey == null ? null : selections.get(sessionKey);
    }

    @Nullable
    private static String getSessionKey() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isLocalServer()) return "singleplayer";
        ServerData serverData = minecraft.getCurrentServer();
        if (serverData != null && StringUtils.isNotBlank(serverData.ip)) {
            return "server:" + normalize(serverData.ip);
        }
        if (minecraft.getConnection() != null) {
            SocketAddress address = minecraft.getConnection().getConnection().getRemoteAddress();
            if (address instanceof InetSocketAddress inetAddress) {
                return "server:" + normalize(inetAddress.getHostString() + ":" + inetAddress.getPort());
            }
        }
        return null;
    }

    private static String normalize(String address) {
        return StringUtils.removeStart(StringUtils.trimToEmpty(address).toLowerCase(Locale.ROOT), "/");
    }

    private static Path path() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config")
                .resolve("yes_steve_model_client_only.properties");
    }

    private static void load() {
        if (loaded) return;
        synchronized (lock) {
            if (loaded) return;
            Path path = path();
            if (Files.isRegularFile(path)) {
                Properties properties = new Properties();
                try (InputStream input = Files.newInputStream(path)) {
                    properties.load(input);
                    for (String key : properties.stringPropertyNames()) {
                        if (!key.endsWith(".model")) continue;
                        String sessionKey = key.substring(0, key.length() - 6);
                        String modelId = properties.getProperty(key, "");
                        if (!StringUtils.isBlank(modelId)) {
                            selections.put(sessionKey, new Selection(modelId, properties.getProperty(sessionKey + ".texture", "")));
                        }
                    }
                } catch (IOException exception) {
                    YesSteveModel.LOGGER.warn("[YSM] Failed to load client-only model selections", exception);
                }
            }
            loaded = true;
        }
    }

    private static void persist() {
        synchronized (lock) {
            Properties properties = new Properties();
            selections.forEach((key, selection) -> {
                properties.setProperty(key + ".model", selection.modelId());
                properties.setProperty(key + ".texture", selection.textureId());
            });
            Path path = path();
            try {
                Files.createDirectories(path.getParent());
                try (OutputStream output = Files.newOutputStream(path)) {
                    properties.store(output, "Yes Steve Model client-only selections");
                }
            } catch (IOException exception) {
                YesSteveModel.LOGGER.warn("[YSM] Failed to save client-only model selection", exception);
            }
        }
    }
}
