package com.elfmcys.yesstevemodel.client.event;

import com.elfmcys.yesstevemodel.config.GeneralConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PlayerRenderPolicy {

    private PlayerRenderPolicy() {
    }

    public enum Layer {
        VANILLA,
        LOCAL_YSM,
        SERVER_YSM
    }

    public static List<Layer> getOrder(Player player) {
        boolean self = isSelf(player);
        String rawOrder = self ? GeneralConfig.SELF_PLAYER_RENDER_ORDER.get() : GeneralConfig.OTHER_PLAYER_RENDER_ORDER.get();
        Layer fallback = self ? Layer.LOCAL_YSM : Layer.SERVER_YSM;
        return parseOrder(rawOrder, fallback);
    }

    public static boolean isSelf(Player player) {
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        return localPlayer != null && player != null && player.equals(localPlayer);
    }

    public static boolean isYsmLayerForPlayer(Layer layer, Player player) {
        return switch (layer) {
            case LOCAL_YSM -> isSelf(player);
            case SERVER_YSM -> !isSelf(player);
            default -> false;
        };
    }

    private static List<Layer> parseOrder(String rawOrder, Layer fallback) {
        ArrayList<Layer> layers = new ArrayList<>(3);
        if (StringUtils.isNotBlank(rawOrder)) {
            for (String token : rawOrder.split(",")) {
                String normalized = token.trim().toUpperCase(Locale.ROOT);
                if (StringUtils.isBlank(normalized)) {
                    continue;
                }
                try {
                    Layer layer = Layer.valueOf(normalized);
                    if (!layers.contains(layer)) {
                        layers.add(layer);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        if (layers.isEmpty()) {
            layers.add(fallback);
        }
        return layers;
    }
}
