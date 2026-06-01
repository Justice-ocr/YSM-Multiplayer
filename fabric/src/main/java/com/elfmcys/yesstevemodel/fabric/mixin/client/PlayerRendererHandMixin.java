package com.elfmcys.yesstevemodel.fabric.mixin.client;

import com.elfmcys.yesstevemodel.client.event.ReplacePlayerHandRenderEvent;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererHandMixin {

    @Inject(method = "renderRightHand", at = @At("HEAD"), cancellable = true)
    private void ysm$onRenderRightHand(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, ResourceLocation skin, boolean sleeveVisible, CallbackInfo ci) {
        if (ReplacePlayerHandRenderEvent.onRenderArm(Minecraft.getInstance().player, HumanoidArm.RIGHT, poseStack, bufferSource, packedLight)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderLeftHand", at = @At("HEAD"), cancellable = true)
    private void ysm$onRenderLeftHand(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, ResourceLocation skin, boolean sleeveVisible, CallbackInfo ci) {
        if (ReplacePlayerHandRenderEvent.onRenderArm(Minecraft.getInstance().player, HumanoidArm.LEFT, poseStack, bufferSource, packedLight)) {
            ci.cancel();
        }
    }
}
