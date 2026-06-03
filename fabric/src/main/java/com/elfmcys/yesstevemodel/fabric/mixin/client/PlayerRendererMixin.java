package com.elfmcys.yesstevemodel.fabric.mixin.client;

import com.elfmcys.yesstevemodel.client.event.PlayerRenderPolicy;
import com.elfmcys.yesstevemodel.client.event.ReplacePlayerRenderEvent;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * @author IzumiiKonata
 * Date: 2026/5/10 23:20
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin extends LivingEntityRenderer<AbstractClientPlayer, PlayerRenderState, PlayerModel> {

    @Unique
    private final Map<PlayerRenderState, Player> ysm$playersByRenderState = Collections.synchronizedMap(new WeakHashMap<>());

    public PlayerRendererMixin(EntityRendererProvider.Context context, PlayerModel entityModel, float f) {
        super(context, entityModel, f);
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;F)V", at = @At("RETURN"))
    private void ysm$rememberRenderStatePlayer(AbstractClientPlayer player, PlayerRenderState renderState, float partialTick, CallbackInfo ci) {
        ysm$playersByRenderState.put(renderState, player);
    }

    @Override
    public void render(PlayerRenderState livingEntityRenderState, PoseStack poseStack, MultiBufferSource multiBufferSource, int packedLight) {
        Player player = ysm$getPlayer(livingEntityRenderState);
        if (player == null) {
            super.render(livingEntityRenderState, poseStack, multiBufferSource, packedLight);
            return;
        }

        boolean attemptedYsm = false;
        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        List<PlayerRenderPolicy.Layer> renderOrder = PlayerRenderPolicy.getOrder(player);
        for (PlayerRenderPolicy.Layer layer : renderOrder) {
            if (layer == PlayerRenderPolicy.Layer.VANILLA) {
                super.render(livingEntityRenderState, poseStack, multiBufferSource, packedLight);
                return;
            } else {
                attemptedYsm = true;
                if (ReplacePlayerRenderEvent.renderYsmLayer(layer, player, livingEntityRenderState, partialTick, poseStack, multiBufferSource, packedLight)) {
                    return;
                }
            }
        }

        if (!attemptedYsm) {
            super.render(livingEntityRenderState, poseStack, multiBufferSource, packedLight);
        }
    }

    @Unique
    private Player ysm$getPlayer(PlayerRenderState renderState) {
        Player player = ysm$playersByRenderState.get(renderState);
        if (player != null) {
            return player;
        }

        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null
                && renderState.id == localPlayer.getId()
                && Objects.equals(renderState.name, localPlayer.getGameProfile().getName())) {
            return localPlayer;
        }
        return null;
    }
}
