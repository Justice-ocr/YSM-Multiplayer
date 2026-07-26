package com.elfmcys.yesstevemodel.fabric.mixin.client;

import com.elfmcys.yesstevemodel.client.event.ReplacePlayerRenderEvent;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author IzumiiKonata
 * Date: 2026/5/10 23:20
 */
@Mixin(LivingEntityRenderer.class)
public abstract class PlayerRendererMixin {

    @Inject(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void ysm$onSubmit(LivingEntityRenderState renderState, PoseStack poseStack, SubmitNodeCollector collector,
                              CameraRenderState cameraState, CallbackInfo ci) {
        if (!(renderState instanceof AvatarRenderState avatarState)) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !(minecraft.level.getEntity(avatarState.id) instanceof AbstractClientPlayer player)) {
            return;
        }

        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        if (ReplacePlayerRenderEvent.onRenderPlayerPre(player, avatarState, partialTick, poseStack, collector, cameraState)) {
            ci.cancel();
        }
    }
}
