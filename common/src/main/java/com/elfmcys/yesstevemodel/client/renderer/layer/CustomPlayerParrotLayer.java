package com.elfmcys.yesstevemodel.client.renderer.layer;

import com.elfmcys.yesstevemodel.client.entity.CustomPlayerEntity;
import com.elfmcys.yesstevemodel.geckolib3.geo.GeoLayerRenderer;
import com.elfmcys.yesstevemodel.geckolib3.geo.animated.AnimatedGeoModel;
import com.elfmcys.yesstevemodel.geckolib3.util.RenderUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ParrotModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ParrotRenderer;
import net.minecraft.client.renderer.entity.state.ParrotRenderState;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.player.Player;
import com.mojang.math.Axis;

public class CustomPlayerParrotLayer extends GeoLayerRenderer<CustomPlayerEntity> {

    private static final String TAG_ID = "id";
    private static final String TAG_VARIANT = "Variant";

    private final ParrotModel parrotModel;
    private final ParrotRenderState parrotState = new ParrotRenderState();

    public CustomPlayerParrotLayer(EntityRendererProvider.Context context) {
        this.parrotModel = new ParrotModel(context.bakeLayer(ModelLayers.PARROT));
        this.parrotState.pose = ParrotModel.Pose.ON_SHOULDER;
    }

    @Override
    public void render(PlayerRenderState state, PoseStack poseStack, MultiBufferSource bufferSource, int packedLightIn, CustomPlayerEntity entityLivingBaseIn, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        Player player = entityLivingBaseIn.getEntity();
        AnimatedGeoModel model = entityLivingBaseIn.getCurrentModel();
        if (model == null) {
            return;
        }
        if (!model.leftShoulderBones().isEmpty()) {
            renderParrot(poseStack, state, bufferSource, model, packedLightIn, player, limbSwing, limbSwingAmount, netHeadYaw, headPitch, true);
        }
        if (!model.rightShoulderBones().isEmpty()) {
            renderParrot(poseStack, state, bufferSource, model, packedLightIn, player, limbSwing, limbSwingAmount, netHeadYaw, headPitch, false);
        }
    }

    private void renderParrot(PoseStack poseStack, PlayerRenderState state, MultiBufferSource bufferSource, AnimatedGeoModel model, int packedLightIn, Player player, float limbSwing, float limbSwingAmount, float netHeadYaw, float headPitch, boolean isLeftShoulder) {
        CompoundTag shoulderEntityLeft = isLeftShoulder ? player.getShoulderEntityLeft() : player.getShoulderEntityRight();
        Parrot.Variant variant = isLeftShoulder ? state.parrotOnLeftShoulder : state.parrotOnRightShoulder;

        if (variant == null)
            return;

        EntityType.byString(shoulderEntityLeft.getStringOr(TAG_ID, "")).filter(entityType -> entityType == EntityType.PARROT).ifPresent(entityType -> {
            poseStack.pushPose();
            applyParrotTransform(poseStack, model, isLeftShoulder);
//            poseStack.translate(0.0d, 1.5d, 0.0d);
            poseStack.mulPose(Axis.ZP.rotationDegrees(180.0f));
            this.renderOnShoulder(poseStack, bufferSource, packedLightIn, state, variant, headPitch, netHeadYaw, isLeftShoulder);
            poseStack.popPose();
        });
    }

    private void renderOnShoulder(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            PlayerRenderState state,
            Parrot.Variant variant,
            float pitch,
            float yaw,
            boolean leftShoulder
    ) {
        poseStack.pushPose();
        poseStack.translate(/*leftShoulder ? 0.2F : -0.2F*/0, state.isCrouching ? -1.3F : -1.5F, 0.0F);
        this.parrotState.ageInTicks = state.ageInTicks;
        this.parrotState.walkAnimationPos = state.walkAnimationPos;
        this.parrotState.walkAnimationSpeed = state.walkAnimationSpeed;
        this.parrotState.yRot = yaw;
        this.parrotState.xRot = pitch;
        this.parrotModel.setupAnim(this.parrotState);
        this.parrotModel
                .renderToBuffer(poseStack, bufferSource.getBuffer(this.parrotModel.renderType(ParrotRenderer.getVariantTexture(variant))), packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    public void applyParrotTransform(PoseStack poseStack, AnimatedGeoModel model, boolean isLeftShoulder) {
        if (isLeftShoulder) {
            RenderUtils.prepMatrixForLocator(poseStack, model.leftShoulderBones());
        } else {
            RenderUtils.prepMatrixForLocator(poseStack, model.rightShoulderBones());
        }
    }
}