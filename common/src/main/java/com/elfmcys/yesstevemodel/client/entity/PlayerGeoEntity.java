package com.elfmcys.yesstevemodel.client.entity;

import com.elfmcys.yesstevemodel.client.model.ModelAssembly;
import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.elfmcys.yesstevemodel.client.animation.condition.ArmorConditions;
import com.elfmcys.yesstevemodel.capability.PlayerCapability;
import com.elfmcys.yesstevemodel.geckolib3.core.builder.AnimationController;
import com.elfmcys.yesstevemodel.geckolib3.core.builder.Animation;
import com.elfmcys.yesstevemodel.geckolib3.core.event.predicate.AnimationEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public class PlayerGeoEntity extends GeoEntity<LocalPlayer> {

    private final PlayerCapability playerCapability;

    private final boolean firstPersonAttachmentModel;

    public PlayerGeoEntity(LocalPlayer player, PlayerCapability capability) {
        this(player, capability, false);
    }

    public PlayerGeoEntity(LocalPlayer player, PlayerCapability capability, boolean firstPersonAttachmentModel) {
        super(player, false);
        this.playerCapability = capability;
        this.firstPersonAttachmentModel = firstPersonAttachmentModel;
        setModelId(capability.getModelId());
    }

    @Override
    public void registerAnimationControllers() {
        getModelAssembly().getAnimationBundle().getArmControllerInstaller().accept(this);
    }

    public PlayerCapability getPlayerCapability() {
        return this.playerCapability;
    }

    @Override
    public boolean shouldSkipAnimation(AnimationEvent<?> event) {
        return true;
    }

    @Override
    public void tickModel() {
        if (this.playerCapability.getModelAssembly() != getModelAssembly()) {
            setModelId(this.playerCapability.getModelId());
        }
    }

    @Override
    @Nullable
    public GeoEntity.ModelWrapper buildRenderShape(ModelAssembly modelAssembly, boolean isDefault) {
        return this.playerCapability.getRenderShape();
    }

    @Override
    @Nullable
    public AnimationController getAnimationEntries(String str) {
        AnimationController controller = getModelAssembly().getAnimationBundle().getAnimationEntries().get(str);
        if (controller == null) {
            controller = getModelAssembly().getAnimationBundle().getAnimationEntries().get("controller.animation." + str);
        }
        return controller;
    }

    @Override
    public ResourceLocation getTextureLocation() {
        return this.playerCapability.getTextureLocation();
    }

    @Override
    public float getHeightScale() {
        return getModelAssembly().getModelData().getModelProperties().getHeightScale();
    }

    @Override
    public float getWidthScale() {
        return getModelAssembly().getModelData().getModelProperties().getWidthScale();
    }

    @Override
    @Nullable
    public Animation getAnimation(String str) {
        if (this.firstPersonAttachmentModel) {
            Animation armAnimation = getModelAssembly().getAnimationBundle().getArmAnimations().get(str);
            return armAnimation != null ? armAnimation : getModelAssembly().getAnimationBundle().getMainAnimations().get(str);
        }
        return getModelAssembly().getAnimationBundle().getArmAnimations().get(str);
    }

    public ArmorConditions getArmModelProcessor() {
        return getModelAssembly().getAnimationBundle().getModelProcessor();
    }

    @Override
    public GeoModel getAnimationProcessor() {
        if (this.firstPersonAttachmentModel) {
            return getModelAssembly().getAnimationBundle().getMainModel();
        }
        return getModelAssembly().getAnimationBundle().getArmModel();
    }

    @Override
    public void setupAnim(float seekTime, boolean isFirstPerson) {
        super.setupAnim(seekTime, isFirstPerson);
        getEvaluationContext().setRoamingProperties(this.playerCapability.getServerVarContainer());
    }
}
