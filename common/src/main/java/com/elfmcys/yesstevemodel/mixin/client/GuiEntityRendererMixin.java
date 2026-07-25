package com.elfmcys.yesstevemodel.mixin.client;

import com.elfmcys.yesstevemodel.client.renderer.CustomPlayerRenderer;
import com.elfmcys.yesstevemodel.client.renderer.ModelPreviewRenderer;
import com.elfmcys.yesstevemodel.client.renderer.PreviewEntityRegistry;
import com.elfmcys.yesstevemodel.client.renderer.RenderContext;
import com.elfmcys.yesstevemodel.client.renderer.RendererManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.GuiEntityRenderer;
import net.minecraft.client.gui.render.state.pip.GuiEntityRenderState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiEntityRenderer.class)
public class GuiEntityRendererMixin {

    @Inject(method = "renderToTexture", at = @At("HEAD"))
    private void ysm$enterPreviewMode(GuiEntityRenderState state, PoseStack poseStack, CallbackInfo ci) {
        ModelPreviewRenderer.setPreviewMode(true);
    }

    @Inject(method = "renderToTexture", at = @At("RETURN"))
    private void ysm$exitPreviewMode(GuiEntityRenderState state, PoseStack poseStack, CallbackInfo ci) {
        ModelPreviewRenderer.setPreviewMode(false);
    }

    @Redirect(
            method = "renderToTexture*",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V"
            )
    )
    private void ysm$redirectPreviewDispatch(
            EntityRenderDispatcher dispatcher,
            EntityRenderState state,
            CameraRenderState cameraState,
            double x, double y, double z,
            PoseStack poseStack,
            SubmitNodeCollector collector
    ) {
        PreviewEntityRegistry.Entry entry = PreviewEntityRegistry.getEntry(state);
        if (entry != null && entry.animatable() != null && state instanceof AvatarRenderState playerState) {
            try {
                MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
                RenderContext.enter(collector, cameraState);
                try {
                    if (entry.beforeEntity() != null) {
                        entry.beforeEntity().render(poseStack, bufferSource, state.lightCoords);
                    }
                    CustomPlayerRenderer renderer = RendererManager.getPlayerRenderer();
                    float framePartialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
                    renderer.renderEntity(entry.animatable(), playerState, 0.0f, framePartialTick, poseStack, bufferSource, state.lightCoords);
                    if (entry.afterEntity() != null) {
                        entry.afterEntity().render(poseStack, bufferSource, state.lightCoords);
                    }
                } finally {
                    RenderContext.exit();
                }
            } finally {
                PreviewEntityRegistry.remove(state);
            }
            return;
        }
        dispatcher.submit(state, cameraState, x, y, z, poseStack, collector);
    }
}
