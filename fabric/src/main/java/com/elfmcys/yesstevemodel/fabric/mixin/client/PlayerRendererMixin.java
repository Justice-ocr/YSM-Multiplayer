package com.elfmcys.yesstevemodel.fabric.mixin.client;

import com.elfmcys.yesstevemodel.client.event.ReplacePlayerRenderEvent;
import com.elfmcys.yesstevemodel.fabric.accessor.EntityRenderDispatcherAccessor;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;

/**
 * @author IzumiiKonata
 * Date: 2026/5/10 23:20
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin extends LivingEntityRenderer<AbstractClientPlayer, PlayerRenderState, PlayerModel> {

    public PlayerRendererMixin(EntityRendererProvider.Context context, PlayerModel entityModel, float f) {
        super(context, entityModel, f);
    }

    @Override
    public void render(PlayerRenderState livingEntityRenderState, PoseStack poseStack, MultiBufferSource multiBufferSource, int packedLight) {
        net.minecraft.world.entity.Entity lastEntity =
            ((EntityRenderDispatcherAccessor) Minecraft.getInstance().getEntityRenderDispatcher()).ysm$getLastRenderingEntity();

        if (lastEntity instanceof Player player && ReplacePlayerRenderEvent.onRenderPlayerPre(
                player, livingEntityRenderState,
                Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true),
                poseStack, multiBufferSource, packedLight)) {
            return;
        }

        super.render(livingEntityRenderState, poseStack, multiBufferSource, packedLight);
    }
}
