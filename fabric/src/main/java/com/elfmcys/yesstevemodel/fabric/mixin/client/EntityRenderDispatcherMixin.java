package com.elfmcys.yesstevemodel.fabric.mixin.client;

import com.elfmcys.yesstevemodel.fabric.accessor.EntityRenderDispatcherAccessor;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin implements EntityRenderDispatcherAccessor {

    @Unique
    private Entity ysm$lastRenderingEntity;

    @Inject(
            method = "render(Lnet/minecraft/world/entity/Entity;DDDFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/EntityRenderer;)V",
            at = @At("HEAD")
    )
    private <E extends Entity, S extends EntityRenderState> void ysm$onRenderPlayerPre(E entity, double d, double e, double f, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, EntityRenderer<? super E, S> entityRenderer, CallbackInfo ci) {
        ysm$lastRenderingEntity = entity;
    }

    @Inject(
            method = "render(Lnet/minecraft/world/entity/Entity;DDDFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/EntityRenderer;)V",
            at = @At("RETURN")
    )
    private <E extends Entity, S extends EntityRenderState> void ysm$onRenderPlayerPost(E entity, double d, double e, double f, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, EntityRenderer<? super E, S> entityRenderer, CallbackInfo ci) {
        // render() 완료 후 null로 리셋
        // 이렇게 하면 EntityCulling 등이 render()를 건너뛸 때
        // lastRenderingEntity가 이전 프레임 값으로 남아있지 않음
        ysm$lastRenderingEntity = null;
    }

    @Inject(method = "getRenderer(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/client/renderer/entity/EntityRenderer;", at = @At("HEAD"))
    private <T extends Entity> void ysm$onGetRenderer(T entity, CallbackInfoReturnable<EntityRenderer<? super T, ?>> cir) {
        ysm$lastRenderingEntity = entity;
    }

    @Unique
    @Override
    public Entity ysm$getLastRenderingEntity() {
        return ysm$lastRenderingEntity;
    }
}
