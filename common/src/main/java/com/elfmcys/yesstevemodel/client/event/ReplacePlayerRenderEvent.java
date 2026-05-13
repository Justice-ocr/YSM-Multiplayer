package com.elfmcys.yesstevemodel.client.event;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.capability.PlayerCapability;
import com.elfmcys.yesstevemodel.client.renderer.ModelPreviewRenderer;
import com.elfmcys.yesstevemodel.client.renderer.RendererManager;
import com.elfmcys.yesstevemodel.config.GeneralConfig;
import com.elfmcys.yesstevemodel.util.CameraUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.world.entity.player.Player;
import rip.ysm.compat.firstperson.FirstPersonCompat;
import rip.ysm.compat.playeranimator.PlayerAnimatorCompat;
import rip.ysm.compat.realcamera.RealCameraCompat;

public class ReplacePlayerRenderEvent {

    private ReplacePlayerRenderEvent() {
    }

    private static int _dbgSkipCount = 0;
    private static long _dbgLastSkipMs = 0;

    public static boolean onRenderPlayerPre(Player entity, PlayerRenderState renderState, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        if (!YesSteveModel.isAvailable()) {
            return false;
        }
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (entity.equals(localPlayer) && GeneralConfig.DISABLE_SELF_MODEL.get().booleanValue()) {
            return false;
        }
        if ((!entity.equals(localPlayer) && GeneralConfig.DISABLE_OTHER_MODEL.get().booleanValue()) || entity.isSpectator()) {
            return false;
        }
        boolean[] cancelled = {false};
        PlayerCapability.get(entity).ifPresent(cap -> {
            // 提前调用 tickModel()，确保 currentModel 在 isModelActive() 判断前已初始化
            cap.tickModel();
            if (cap.isModelActive()) {
                // 无论 currentModel 是否初始化完毕，都拦截原版渲染（cancelled=true）
                // 若 currentModel 为 null（初始化中），跳过 YSM 渲染，宁可透明也不显示原版皮肤
                // 这修复了 EntityCulling 深度渲染时用旧 entity 对象导致的原版皮肤闪现
                if (!CameraUtil.isFirstPerson(cap)
                        || FirstPersonCompat.isFirstPersonActive()
                        || RealCameraCompat.isActive()
                        || GeneralConfig.DISABLE_EXTERNAL_FP_ANIM.get().booleanValue()
                        || !PlayerAnimatorCompat.isPlayerAnimated(localPlayer)) {
                    cancelled[0] = true;
                    if (cap.getCurrentModel() != null) {
                        RendererManager.getPlayerRenderer().render(entity, renderState, entity.getYRot(), ModelPreviewRenderer.isPreview() ? 1.0f : partialTick, poseStack, bufferSource, packedLight);
                    }
                }
            }
        });
        // 诊断：本地玩家渲染被跳过时记录原因
        if (!cancelled[0] && entity.equals(Minecraft.getInstance().player)) {
            long now = System.currentTimeMillis();
            if (now - _dbgLastSkipMs > 200) { // 防止每帧刷屏，200ms内只打一次
                _dbgLastSkipMs = now;
                _dbgSkipCount++;
                com.elfmcys.yesstevemodel.YesSteveModel.LOGGER.warn(
                    "[YSM DBG] vanilla shown! count={} active={} model_id={} model_asm={} current_model={} force_disabled={}",
                    _dbgSkipCount,
                    PlayerCapability.get(entity).map(c -> c.isModelActive()).orElse(false),
                    PlayerCapability.get(entity).map(c -> c.getModelId()).orElse("N/A"),
                    PlayerCapability.get(entity).map(c -> c.getModelAssembly() != null ? "non-null" : "NULL").orElse("no-cap"),
                    PlayerCapability.get(entity).map(c -> c.getCurrentModel() != null ? "non-null" : "NULL").orElse("no-cap"),
                    PlayerCapability.get(entity).map(c -> c.isForceDisabled()).orElse(false)
                );
            }
        }
        return cancelled[0];
    }
}
