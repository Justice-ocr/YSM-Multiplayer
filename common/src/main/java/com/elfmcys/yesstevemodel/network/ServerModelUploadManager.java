package com.elfmcys.yesstevemodel.network;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.config.ServerConfig;
import com.elfmcys.yesstevemodel.model.ServerModelManager;
import com.elfmcys.yesstevemodel.network.message.C2SModelUploadChunkPacket;
import com.elfmcys.yesstevemodel.network.message.C2SModelUploadFinishPacket;
import com.elfmcys.yesstevemodel.network.message.C2SModelUploadStartPacket;
import com.elfmcys.yesstevemodel.network.message.S2CModelUploadResultPacket;
import com.elfmcys.yesstevemodel.network.message.S2CModelUploadStartPacket;
import com.elfmcys.yesstevemodel.resource.YSMBinaryDeserializer;
import com.elfmcys.yesstevemodel.resource.YSMFolderDeserializer;
import com.elfmcys.yesstevemodel.resource.pojo.RawYsmModel;
import com.elfmcys.yesstevemodel.util.DigestUtil;
import net.minecraft.server.level.ServerPlayer;
import org.apache.commons.lang3.StringUtils;
import rip.ysm.legacy.YesModelUtils;
import rip.ysm.security.YsmCrypt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerModelUploadManager {
    private static final int CHUNK_SIZE = 32_000;
    private static final long SESSION_TTL_MS = 5 * 60 * 1000L;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Map<UUID, UploadState> SESSIONS = new ConcurrentHashMap<>();

    private ServerModelUploadManager() {
    }

    public static void handleStart(ServerPlayer player, C2SModelUploadStartPacket packet) {
        cleanupExpiredSessions();

        int maxBytes = ServerConfig.MAX_UPLOAD_BYTES.get();
        int chunksPerTick = ServerConfig.UPLOAD_CHUNKS_PER_TICK.get();
        String modelId = normalizeModelId(packet.modelId());
        if (!ServerConfig.CAN_UPLOAD_MODEL.get()) {
            sendStart(player, 0L, (byte) 6, maxBytes, chunksPerTick, "Uploads disabled");
            return;
        }
        if (modelId == null || !isSha256(packet.sha256())) {
            sendStart(player, 0L, (byte) 5, maxBytes, chunksPerTick, "Invalid model ID or hash");
            return;
        }
        if (packet.totalBytes() <= 0 || packet.totalBytes() > maxBytes) {
            sendStart(player, 0L, (byte) 2, maxBytes, chunksPerTick, "File exceeds server limit");
            return;
        }

        Path destination = resolveDestination(modelId);
        if (destination == null) {
            sendStart(player, 0L, (byte) 5, maxBytes, chunksPerTick, "Invalid model path");
            return;
        }
        if (Files.exists(destination) || ServerModelManager.getServerModelInfo().containsKey(modelId)) {
            sendStart(player, 0L, (byte) 1, maxBytes, chunksPerTick, "Model ID already exists");
            return;
        }

        long uploadId = RANDOM.nextLong();
        if (uploadId == 0L) {
            uploadId = 1L;
        }
        SESSIONS.put(player.getUUID(), new UploadState(uploadId, modelId, destination, packet.totalBytes(), packet.sha256()));
        sendStart(player, uploadId, (byte) 0, maxBytes, chunksPerTick, "");
    }

    public static void handleChunk(ServerPlayer player, C2SModelUploadChunkPacket packet) {
        UploadState state = SESSIONS.get(player.getUUID());
        if (state == null || state.uploadId != packet.uploadId()) {
            return;
        }
        state.lastTouched = System.currentTimeMillis();
        byte[] chunk = packet.data();
        if (packet.offset() < 0 || chunk.length == 0 || packet.offset() + chunk.length > state.data.length) {
            fail(player, state, (byte) 5, "Invalid upload chunk");
            return;
        }
        System.arraycopy(chunk, 0, state.data, packet.offset(), chunk.length);
        state.receivedBytes += chunk.length;
    }

    public static void handleFinish(ServerPlayer player, C2SModelUploadFinishPacket packet) {
        UploadState state = SESSIONS.get(player.getUUID());
        if (state == null || state.uploadId != packet.uploadId()) {
            NetworkHandler.sendToClientPlayer(new S2CModelUploadResultPacket(packet.uploadId(), (byte) 4, "", 0L, 0L, "Session expired"), player);
            return;
        }

        if (state.receivedBytes < state.data.length) {
            fail(player, state, (byte) 5, "Incomplete upload");
            return;
        }
        if (!StringUtils.equalsIgnoreCase(DigestUtil.sha256Hex(state.data), state.sha256)) {
            fail(player, state, (byte) 1, "Hash mismatch");
            return;
        }

        try {
            validateYsmFile(state.data);
        } catch (Exception e) {
            YesSteveModel.LOGGER.warn("[YSM Upload] Failed to parse uploaded model {}: {}", state.modelId, e.getMessage());
            fail(player, state, (byte) 2, "Server failed to parse model");
            return;
        }

        try {
            Files.createDirectories(state.destination.getParent());
            Files.write(state.destination, state.data);
        } catch (IOException e) {
            YesSteveModel.LOGGER.error("[YSM Upload] Failed to save uploaded model {}", state.modelId, e);
            fail(player, state, (byte) 3, "Server storage error");
            return;
        }

        SESSIONS.remove(player.getUUID());
        ServerModelManager.loadModels(null, null);
        NetworkHandler.sendToClientPlayer(new S2CModelUploadResultPacket(state.uploadId, (byte) 0, state.modelId, 0L, 0L, ""), player);
    }

    private static void sendStart(ServerPlayer player, long uploadId, byte status, int maxBytes, int chunksPerTick, String message) {
        NetworkHandler.sendToClientPlayer(new S2CModelUploadStartPacket(uploadId, status, CHUNK_SIZE, maxBytes, chunksPerTick, message), player);
    }

    private static void fail(ServerPlayer player, UploadState state, byte status, String message) {
        SESSIONS.remove(player.getUUID());
        NetworkHandler.sendToClientPlayer(new S2CModelUploadResultPacket(state.uploadId, status, state.modelId, 0L, 0L, message), player);
    }

    private static void validateYsmFile(byte[] data) throws Exception {
        int cryptoVersion = YesModelUtils.getYsmCryptoVersion(data);
        if (cryptoVersion == -1) {
            throw new IllegalStateException("Unknown YSM crypto version");
        }
        if (cryptoVersion == 1 || cryptoVersion == 2) {
            try (YSMFolderDeserializer deserializer = new YSMFolderDeserializer(YesModelUtils.input(data))) {
                deserializer.deserialize();
            }
            return;
        }
        byte[] decrypted = YsmCrypt.decryptYsmFile(data);
        try (YSMBinaryDeserializer deserializer = new YSMBinaryDeserializer(decrypted)) {
            RawYsmModel rawModel = deserializer.deserializeKeepOpen();
            deserializer.parseYSMFooter(rawModel);
        }
    }

    private static String normalizeModelId(String raw) {
        String modelId = StringUtils.trimToNull(raw);
        if (modelId == null) {
            return null;
        }
        modelId = modelId.replace('\\', '/');
        if (!modelId.endsWith(".ysm")) {
            modelId = modelId + ".ysm";
        }
        if (modelId.length() > 160 || modelId.startsWith("/") || modelId.contains("..") || !modelId.matches("[A-Za-z0-9_./-]+\\.ysm")) {
            return null;
        }
        return modelId;
    }

    private static Path resolveDestination(String modelId) {
        Path customRoot = ServerModelManager.CUSTOM.toAbsolutePath().normalize();
        Path destination = customRoot.resolve(modelId).normalize();
        if (!destination.startsWith(customRoot)) {
            return null;
        }
        return destination;
    }

    private static boolean isSha256(String value) {
        return value != null && value.matches("(?i)[0-9a-f]{64}");
    }

    private static void cleanupExpiredSessions() {
        long now = System.currentTimeMillis();
        SESSIONS.entrySet().removeIf(entry -> now - entry.getValue().lastTouched > SESSION_TTL_MS);
    }

    private static final class UploadState {
        private final long uploadId;
        private final String modelId;
        private final Path destination;
        private final byte[] data;
        private final String sha256;
        private int receivedBytes;
        private long lastTouched = System.currentTimeMillis();

        private UploadState(long uploadId, String modelId, Path destination, int totalBytes, String sha256) {
            this.uploadId = uploadId;
            this.modelId = modelId;
            this.destination = destination;
            this.data = new byte[totalBytes];
            this.sha256 = sha256;
        }
    }
}
