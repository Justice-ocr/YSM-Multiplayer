package com.elfmcys.yesstevemodel.fabric.mixin.client;

import com.elfmcys.yesstevemodel.client.event.ReplacePlayerRenderEvent;
import com.elfmcys.yesstevemodel.client.renderer.EntityRenderStateBindings;
import com.elfmcys.yesstevemodel.util.ItemTagsConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandLayer.class)
public abstract class PlayerItemInHandLayerMixin {

    @Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
    private void ysm$hideVanillaHeldItems(ArmedEntityRenderState renderState, ItemStackRenderState itemRenderState,
                                          ItemStack itemStack, net.minecraft.world.entity.HumanoidArm arm,
                                          PoseStack poseStack, SubmitNodeCollector collector, int packedLight, CallbackInfo ci) {
        if (!(renderState instanceof AvatarRenderState avatarRenderState)) {
            return;
        }
        Entity entity = EntityRenderStateBindings.get(avatarRenderState);
        if (entity instanceof Player player
                && !itemStack.is(ItemTagsConstants.SWORDS)
                && ReplacePlayerRenderEvent.shouldReplacePlayer(player)) {
            ci.cancel();
        }
    }
}
