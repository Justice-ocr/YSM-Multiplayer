package com.elfmcys.yesstevemodel.network.message;

import com.elfmcys.yesstevemodel.capability.PlayerCapability;
import com.elfmcys.yesstevemodel.client.ClientModelManager;
import com.elfmcys.yesstevemodel.event.EntityJoinCallbackEvent;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import rip.ysm.api.network.PacketContext;

public class S2CSetModelAndTexturePacket {

    private final int entityId;

    private final String modelId;

    private final String textureId;

    private final boolean disabled;

    private final S2CSyncPlayerStatePacket entityModelSync;

    public S2CSetModelAndTexturePacket(int entityId, String modelId, String textureId, boolean disabled, S2CSyncPlayerStatePacket playerState) {
        this.entityId = entityId;
        this.modelId = modelId;
        this.textureId = textureId;
        this.entityModelSync = playerState;
        this.disabled = disabled;
    }

    public static void encode(S2CSetModelAndTexturePacket other, FriendlyByteBuf friendlyByteBuf) {
        friendlyByteBuf.writeVarInt(other.entityId);
        friendlyByteBuf.writeUtf(other.modelId);
        friendlyByteBuf.writeUtf(other.textureId);
        friendlyByteBuf.writeBoolean(other.disabled);
        S2CSyncPlayerStatePacket.encode(other.entityModelSync, friendlyByteBuf);
    }

    public static S2CSetModelAndTexturePacket decode(FriendlyByteBuf friendlyByteBuf) {
        return new S2CSetModelAndTexturePacket(friendlyByteBuf.readVarInt(), friendlyByteBuf.readUtf(), friendlyByteBuf.readUtf(), friendlyByteBuf.readBoolean(), S2CSyncPlayerStatePacket.decode(friendlyByteBuf));
    }

    public static void handle(S2CSetModelAndTexturePacket other, PacketContext ctx) {
        if (ctx.isClientSide()) {
            EntityJoinCallbackEvent.addCallback(other.entityId, entity -> applyOnClient(entity, other));
        }
    }

    @Environment(EnvType.CLIENT)
    public static void applyOnClient(Entity entity, S2CSetModelAndTexturePacket other) {
        PlayerCapability.get(entity).ifPresent(cap -> {
            LocalPlayer localPlayer = Minecraft.getInstance().player;
            if (ClientModelManager.isLocalPlayerEntity(entity)) {
                // 本地玩家：完全忽略服务端推送的 disabled 和模型重置
                // 原因：服务端版本不兼容时会推送 disabled=true，导致 isModelActive()=false，闪现原版皮肤
                // 无论服务端发什么，本地玩家始终由客户端自行管理模型和 disabled 状态
                cap.setForceDisabled(false);
                if (localPlayer != null) {
                    ClientModelManager.scheduleRememberedOfflineModelApply(localPlayer);
                } else if (entity instanceof LocalPlayer player) {
                    ClientModelManager.scheduleRememberedOfflineModelApply(player);
                }
                return;
            }
            // 非本地玩家（其他玩家的模型）正常同步
            cap.initModelWithTexture(other.modelId, other.textureId);
            cap.setForceDisabled(other.disabled);
            S2CSyncPlayerStatePacket.handleCapability(entity, other.entityModelSync);
        });
    }
}
