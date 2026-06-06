package com.elfmcys.yesstevemodel.client.renderer;

import com.elfmcys.yesstevemodel.geckolib3.geo.NativeModelRenderer;
import com.elfmcys.yesstevemodel.capability.PlayerCapability;
import com.elfmcys.yesstevemodel.client.entity.PlayerGeoEntity;
import com.elfmcys.yesstevemodel.event.api.SpecialPlayerRenderEvent;
import com.elfmcys.yesstevemodel.geckolib3.geo.animated.AnimatedGeoModel;
import com.elfmcys.yesstevemodel.geckolib3.geo.LayerTypeConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;

public class HandItemRenderer {

    private static final double FIRST_PERSON_ARM_Y_OFFSET = 1.8d;

    private PlayerGeoEntity armGeoModel = null;

    public void renderHandItem(LocalPlayer localPlayer, PlayerCapability capability, HumanoidArm arm, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, float partialTick) {
        if (this.armGeoModel == null || this.armGeoModel.getEntity() != localPlayer) {
            this.armGeoModel = new PlayerGeoEntity(localPlayer, capability);
        }
        AnimatedGeoModel model = processFirstPersonModel(this.armGeoModel, partialTick);
        if (model == null) {
            return;
        }

        SpecialPlayerRenderEvent event = new SpecialPlayerRenderEvent(localPlayer, capability, capability.getModelId());
        if (SpecialPlayerRenderEvent.post(event).isFalse()) {
            return;
        }
        ResourceLocation resourceLocation = event.getTextureLocation() == null ? capability.getTextureLocation() : event.getTextureLocation();
        int textureIndex = event.getTextureLocation() == null ? capability.getTextureIndex() : 0;
        VertexConsumer buffer = bufferSource.getBuffer(CustomEntityTranslucentRenderType.get(resourceLocation));
        int renderPartMask = arm == HumanoidArm.LEFT ? LayerTypeConstants.TYPE_LEFT : LayerTypeConstants.TYPE_RIGHT;
        poseStack.pushPose();
        applyFirstPersonHandTransform(poseStack, arm);
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        NativeModelRenderer.renderMesh(buffer, poseStack.last(), model.getGeoModel(), model.getMatrixData(), model.getAbsPivotData(), textureIndex, renderPartMask, packedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
        poseStack.popPose();
    }

    private void applyFirstPersonHandTransform(PoseStack poseStack, HumanoidArm arm) {
        if (arm == HumanoidArm.LEFT) {
            poseStack.translate(0.25d, FIRST_PERSON_ARM_Y_OFFSET, 0.0d);
        } else {
            poseStack.translate(-0.25d, FIRST_PERSON_ARM_Y_OFFSET, 0.0d);
        }
    }

    private AnimatedGeoModel processFirstPersonModel(PlayerGeoEntity geoModel, float partialTick) {
        geoModel.tickModel();
        boolean wasFirstPersonMode = ModelPreviewRenderer.isFirstPersonModeEnabled();
        ModelPreviewRenderer.setFirstPersonMode(true);
        try {
            if (geoModel.processAnimation(partialTick) == null) {
                return null;
            }
            return geoModel.getCurrentModel();
        } finally {
            ModelPreviewRenderer.setFirstPersonMode(wasFirstPersonMode);
        }
    }

}
