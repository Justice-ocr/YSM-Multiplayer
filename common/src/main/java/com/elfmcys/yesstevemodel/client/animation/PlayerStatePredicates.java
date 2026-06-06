package com.elfmcys.yesstevemodel.client.animation;

import com.elfmcys.yesstevemodel.capability.PlayerCapability;
import com.elfmcys.yesstevemodel.client.entity.CustomPlayerEntity;
import com.elfmcys.yesstevemodel.geckolib3.core.AnimatableEntity;
import com.elfmcys.yesstevemodel.geckolib3.core.event.predicate.AnimationEvent;
import com.elfmcys.yesstevemodel.geckolib3.util.MovementQuery;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

public final class PlayerStatePredicates {
    public static final float MIN_MOVEMENT_SPEED = 0.05f;
    private static final float PLAYER_INPUT_EPSILON = 1.0E-4f;

    private PlayerStatePredicates() {
    }

    public static boolean isElytraFlying(LivingEntity entity) {
        return entity.isFallFlying() || entity.getPose() == Pose.FALL_FLYING || entity.getFallFlyingTicks() > 0;
    }

    public static boolean isMoving(LivingEntity entity, AnimationEvent<?> event) {
        if (entity instanceof Player) {
            float inputSpeedSqr = getPlayerInputSpeedSqr(entity, event);
            if (inputSpeedSqr > PLAYER_INPUT_EPSILON) {
                return true;
            }
            if (isLocalPlayerModel(event)) {
                return false;
            }
        }
        return MovementQuery.isGroundMoving(entity, event.getAnimatable().getPositionTracker(), event, MIN_MOVEMENT_SPEED);
    }

    public static float getVerticalSpeed(LivingEntity entity, AnimationEvent<?> event) {
        return MovementQuery.getVerticalSpeed(entity, event.getAnimatable().getPositionTracker());
    }

    public static float getGroundSpeed(LivingEntity entity, AnimationEvent<?> event) {
        if (isStationaryLocalPlayer(entity, event)) {
            return 0.0f;
        }
        float speed = MovementQuery.getGroundSpeed(entity, event.getAnimatable().getPositionTracker(), event);
        return speed > MovementQuery.EPSILON ? speed : 0.0f;
    }

    public static boolean isStationaryLocalPlayer(LivingEntity entity, AnimationEvent<?> event) {
        return isStationaryLocalPlayerModel(entity, event.getAnimatable(), getPlayerInputSpeedSqr(entity, event));
    }

    public static boolean isStationaryLocalPlayerModel(LivingEntity entity, AnimatableEntity<?> animatable) {
        return isStationaryLocalPlayerModel(entity, animatable, getPlayerInputSpeedSqr(entity, animatable));
    }

    private static boolean isStationaryLocalPlayerModel(LivingEntity entity, AnimatableEntity<?> animatable, float inputSpeedSqr) {
        if (!(entity instanceof Player) || !(animatable instanceof CustomPlayerEntity customPlayer) || !customPlayer.isLocalPlayerModel()) {
            return false;
        }
        return inputSpeedSqr <= PLAYER_INPUT_EPSILON;
    }

    private static float getPlayerInputSpeedSqr(LivingEntity entity, AnimationEvent<?> event) {
        if (event.getAnimatable() instanceof PlayerCapability cap && !cap.isLocalPlayerModel()) {
            float strafe = cap.getPositionTracker().getStrafeInput();
            float forward = cap.getPositionTracker().getForwardInput();
            return (strafe * strafe) + (forward * forward);
        }
        return (entity.xxa * entity.xxa) + (entity.zza * entity.zza);
    }

    private static float getPlayerInputSpeedSqr(LivingEntity entity, AnimatableEntity<?> animatable) {
        if (animatable instanceof PlayerCapability cap && !cap.isLocalPlayerModel()) {
            float strafe = cap.getPositionTracker().getStrafeInput();
            float forward = cap.getPositionTracker().getForwardInput();
            return (strafe * strafe) + (forward * forward);
        }
        return (entity.xxa * entity.xxa) + (entity.zza * entity.zza);
    }

    private static boolean isLocalPlayerModel(AnimationEvent<?> event) {
        return event.getAnimatable() instanceof CustomPlayerEntity customPlayer && customPlayer.isLocalPlayerModel();
    }
}
