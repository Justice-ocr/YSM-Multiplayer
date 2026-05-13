package com.elfmcys.yesstevemodel.mixin.client;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.client.renderer.*;
import com.elfmcys.yesstevemodel.config.GeneralConfig;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.FishingHookRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({EntityRenderDispatcher.class})
public class EntityRenderDispatcherMixin {
    @WrapWithCondition(method = {"render(Lnet/minecraft/world/entity/Entity;DDDFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"}, at = {@At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;render(Lnet/minecraft/world/entity/Entity;DDDFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/EntityRenderer;)V")})
    private <E extends Entity> boolean render(EntityRenderDispatcher instance, E entity, double d, double e, double f, float partialTicks, PoseStack poseStack, MultiBufferSource multiBufferSource, int packedLight, EntityRenderer<? super E, ?> entityRenderer) {
        if (!YesSteveModel.isAvailable()) {
            return true;
        }
        if (entity instanceof Projectile projectile) {
            if (!GeneralConfig.DISABLE_PROJECTILE_MODEL.get()) {
                EntityRenderState state = new EntityRenderState();
                RendererManager.getProjectileRenderer().extractRenderState(projectile, state, partialTicks);
                if (projectile instanceof FishingHook fishingHook) {
                    return CustomFishingHookRenderer.tryRenderCustomHook(fishingHook, state, partialTicks, poseStack, multiBufferSource, packedLight);
                }
                return CustomProjectileRenderer.renderProjectile(projectile, state, partialTicks, poseStack, multiBufferSource, packedLight);
            }
        }
        if (!GeneralConfig.DISABLE_VEHICLE_MODEL.get()) {
            EntityRenderState state = new EntityRenderState();
            VehicleRenderer renderer = RendererManager.getVehicleRenderer();
            renderer.extractRenderState(entity, state, partialTicks);
            ModelPreviewRenderer.renderVehicleModel(entity, poseStack, partialTicks);
            return CustomVehicleRenderer.renderVehicle(entity, state, entity.getRotationVector().x, partialTicks, poseStack, multiBufferSource, packedLight);
        }
        return true;
    }
}
