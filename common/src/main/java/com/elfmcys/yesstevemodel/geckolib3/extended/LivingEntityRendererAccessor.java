package com.elfmcys.yesstevemodel.geckolib3.extended;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;

public interface LivingEntityRendererAccessor {
    void tlm$renderNameTag(LivingEntityRenderState state, PoseStack pPoseStack, MultiBufferSource pBuffer, int pPackedLight);
}