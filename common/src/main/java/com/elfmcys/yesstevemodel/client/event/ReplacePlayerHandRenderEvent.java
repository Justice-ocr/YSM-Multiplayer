package com.elfmcys.yesstevemodel.client.event;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.capability.PlayerCapability;
import com.elfmcys.yesstevemodel.client.model.ModelAssembly;
import com.elfmcys.yesstevemodel.client.renderer.RendererManager;
import com.elfmcys.yesstevemodel.config.GeneralConfig;
import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

public class ReplacePlayerHandRenderEvent {

    private static boolean renderedLeftArmThisFrame;

    private static boolean renderedRightArmThisFrame;

    private ReplacePlayerHandRenderEvent() {
    }

    public static void resetFirstPersonArmFrame() {
        renderedLeftArmThisFrame = false;
        renderedRightArmThisFrame = false;
    }

    public static boolean onRenderArm(Player player, HumanoidArm arm, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        if (!useCustomFirstPersonArms()) {
            return false;
        }
        if (!YesSteveModel.isAvailable() || GeneralConfig.DISABLE_SELF_MODEL.get() || GeneralConfig.DISABLE_SELF_HANDS.get()) {
            return false;
        }
        if (!(player instanceof LocalPlayer localPlayer)) {
            return false;
        }
        if (wasArmRenderedThisFrame(arm)) {
            return true;
        }
        boolean[] cancelled = {false};
        PlayerCapability.get(localPlayer).ifPresent(cap -> {
            if (!cap.isModelActive()) {
                return;
            }
            ModelAssembly context = cap.getModelAssembly();
            if (context == null || !hasArmBone(arm, context.getAnimationBundle().getArmModel())) {
                return;
            }
            RendererManager.getHandRenderer().renderHandItem(localPlayer, cap, arm, poseStack, bufferSource, packedLight, Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
            markArmRenderedThisFrame(arm);
            cancelled[0] = true;
        });
        return cancelled[0];
    }

    private static boolean hasArmBone(HumanoidArm humanoidArm, GeoModel meshData) {
        if (humanoidArm == HumanoidArm.LEFT) {
            return meshData.hasCustomLeftHand;
        }
        return meshData.hasCustomRightHand;
    }

    private static boolean useCustomFirstPersonArms() {
        return true;
    }

    private static boolean wasArmRenderedThisFrame(HumanoidArm arm) {
        return arm == HumanoidArm.LEFT ? renderedLeftArmThisFrame : renderedRightArmThisFrame;
    }

    private static void markArmRenderedThisFrame(HumanoidArm arm) {
        if (arm == HumanoidArm.LEFT) {
            renderedLeftArmThisFrame = true;
        } else {
            renderedRightArmThisFrame = true;
        }
    }
}
