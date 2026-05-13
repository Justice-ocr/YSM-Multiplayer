package com.elfmcys.yesstevemodel.capability.fabric.client;

import com.elfmcys.yesstevemodel.capability.PlayerCapability;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class PlayerCapabilityClientStore {

    private static final ConcurrentMap<UUID, PlayerCapability> STORE = new ConcurrentHashMap<>();

    private PlayerCapabilityClientStore() {
    }

    public static Optional<PlayerCapability> get(Player player) {
        if (!(player instanceof AbstractClientPlayer)) {
            return Optional.empty();
        }
        UUID uuid = player.getUUID();
        PlayerCapability existing = STORE.get(uuid);
        if (existing != null && existing.entity == player) {
            return Optional.of(existing);
        }
        PlayerCapability fresh = new PlayerCapability(player);
        // 子服/维度切换时 entity 对象会重建，但 UUID 相同
        // 把旧 cap 的模型状态复制到新 cap，避免模型变回 default
        if (existing != null && existing.isModelInitialized()) {
            fresh.initModelWithTexture(existing.getModelId(), existing.getCurrentTextureName());
            fresh.setForceDisabled(existing.isForceDisabled());
        }
        STORE.put(uuid, fresh);
        return Optional.of(fresh);
    }

    public static void clear() {
        STORE.clear();
    }
}
