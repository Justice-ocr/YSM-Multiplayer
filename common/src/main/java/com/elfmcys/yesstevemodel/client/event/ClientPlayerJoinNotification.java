package com.elfmcys.yesstevemodel.client.event;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.client.ClientModelManager;
import com.elfmcys.yesstevemodel.network.NetworkHandler;
import dev.architectury.event.events.client.ClientPlayerEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

public final class ClientPlayerJoinNotification {

    private static boolean notified = false;

    private ClientPlayerJoinNotification() {
    }

    public static void register() {
        ClientPlayerEvent.CLIENT_PLAYER_JOIN.register(ClientPlayerJoinNotification::onPlayerJoin);
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(ClientPlayerJoinNotification::onPlayerQuit);
    }

    private static void onPlayerJoin(LocalPlayer player) {
        if (notified) {
            return;
        }
        ClientModelManager.runPendingModelCallback();
        notified = true;
        if (!YesSteveModel.isAvailable()) {
            YesSteveModel.sendUnavailableMessage();
            return;
        }
        if (Minecraft.getInstance().isLocalServer()) {
            // 单人存档：服务端会主动触发 onSyncConnected，不需要额外处理
            return;
        }
        // 多人服务器：立即触发本地模型加载
        // 如果服务端有 YSM，后续收到 S2CVersionCheckPacket 会再次调用 onSyncConnected
        // 那时服务端模型会覆盖本地模型（正常流程）
        ClientModelManager.onSyncConnected();
        ClientModelManager.scheduleRememberedOfflineModelApply(player);
        // 60秒后如果服务端仍无 YSM，显示提示
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(60000L);
                Minecraft.getInstance().execute(() -> {
                    LocalPlayer localPlayer = Minecraft.getInstance().player;
                    if (localPlayer != null && localPlayer.connection.isAcceptingMessages()
                            && !NetworkHandler.isConnectionValid(localPlayer.connection.getConnection())) {
                        localPlayer.displayClientMessage(
                                Component.translatable("message.yes_steve_model.client.server_not_found"), false);
                    }
                });
            } catch (InterruptedException ignored) {
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private static void onPlayerQuit(LocalPlayer player) {
        if (notified) {
            notified = false;
            if (!YesSteveModel.isAvailable()) {
                return;
            }
            // 使用 resetSyncKeepModels 而非 resetSync，保留本地模型缓存
            // 避免子服切换时 modelAssemblyMap 被清空，导致公白期间 YSM 渲染中断、原版皮肤闪现
            ClientModelManager.resetSyncKeepModels();
        }
    }
}
