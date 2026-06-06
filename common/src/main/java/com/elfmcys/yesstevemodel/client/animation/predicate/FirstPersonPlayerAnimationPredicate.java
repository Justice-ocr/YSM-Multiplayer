package com.elfmcys.yesstevemodel.client.animation.predicate;

import com.elfmcys.yesstevemodel.client.animation.IAnimationPredicate;
import com.elfmcys.yesstevemodel.client.animation.PlayerStatePredicates;
import com.elfmcys.yesstevemodel.client.entity.IPreviewAnimatable;
import com.elfmcys.yesstevemodel.client.entity.PlayerGeoEntity;
import com.elfmcys.yesstevemodel.geckolib3.core.builder.ILoopType;
import com.elfmcys.yesstevemodel.geckolib3.core.enums.PlayState;
import com.elfmcys.yesstevemodel.geckolib3.core.event.predicate.AnimationEvent;
import com.elfmcys.yesstevemodel.molang.runtime.ExpressionEvaluator;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

public class FirstPersonPlayerAnimationPredicate implements IAnimationPredicate<PlayerGeoEntity> {
    @Override
    public PlayState predicate(AnimationEvent<PlayerGeoEntity> event, ExpressionEvaluator<?> evaluator) {
        Player player = event.getAnimatable().getEntity();
        if (player == null || event.getAnimatable() instanceof IPreviewAnimatable) {
            return PlayState.STOP;
        }
        PlayState playState = testHighest(player, event);
        if (playState != null) {
            return playState;
        }
        playState = testHigh(player, event);
        if (playState != null) {
            return playState;
        }
        playState = testNormal(player, event);
        if (playState != null) {
            return playState;
        }
        playState = testLow(player, event);
        if (playState != null) {
            return playState;
        }
        playState = playIfPresent(event, "idle", ILoopType.EDefaultLoopTypes.LOOP);
        return playState == null ? PlayState.STOP : playState;
    }

    private PlayState testHighest(Player player, AnimationEvent<PlayerGeoEntity> event) {
        if (player.isDeadOrDying()) {
            return playIfPresent(event, "death", ILoopType.EDefaultLoopTypes.PLAY_ONCE);
        }
        if (player.isAutoSpinAttack()) {
            return playIfPresent(event, "riptide", ILoopType.EDefaultLoopTypes.LOOP);
        }
        if (player.getPose() == Pose.SLEEPING) {
            return playIfPresent(event, "sleep", ILoopType.EDefaultLoopTypes.LOOP);
        }
        if (PlayerStatePredicates.isElytraFlying(player)) {
            return playIfPresent(event, "elytra_fly", ILoopType.EDefaultLoopTypes.LOOP);
        }
        if (player.isSwimming()) {
            return playIfPresent(event, "swim", ILoopType.EDefaultLoopTypes.LOOP);
        }
        if (player.getPose() == Pose.SWIMMING && PlayerStatePredicates.isMoving(player, event)) {
            return playIfPresent(event, "climb", ILoopType.EDefaultLoopTypes.LOOP);
        }
        if (player.getPose() == Pose.SWIMMING) {
            return playIfPresent(event, "climbing", ILoopType.EDefaultLoopTypes.LOOP);
        }
        if (player.onClimbable() && getVerticalSpeed(player) > 0.0f) {
            return playIfPresent(event, "ladder_up", ILoopType.EDefaultLoopTypes.LOOP);
        }
        if (player.onClimbable() && getVerticalSpeed(player) == 0.0f) {
            return playIfPresent(event, "ladder_stillness", ILoopType.EDefaultLoopTypes.LOOP);
        }
        if (player.onClimbable() && getVerticalSpeed(player) < 0.0f) {
            return playIfPresent(event, "ladder_down", ILoopType.EDefaultLoopTypes.LOOP);
        }
        return null;
    }

    private PlayState testHigh(Player player, AnimationEvent<PlayerGeoEntity> event) {
        if (!PlayerStatePredicates.isElytraFlying(player) && player.getAbilities().flying) {
            return playIfPresent(event, "fly", ILoopType.EDefaultLoopTypes.LOOP);
        }
        return null;
    }

    private PlayState testNormal(Player player, AnimationEvent<PlayerGeoEntity> event) {
        if (player.isInWater() && !player.onGround()) {
            return playIfPresent(event, "swim_stand", ILoopType.EDefaultLoopTypes.LOOP);
        }
        if (player.hurtTime > 0) {
            return playIfPresent(event, "attacked", ILoopType.EDefaultLoopTypes.PLAY_ONCE);
        }
        if (!player.onGround() && !player.isInWater()) {
            return playIfPresent(event, "jump", ILoopType.EDefaultLoopTypes.LOOP);
        }
        if (player.onGround() && player.getPose() == Pose.CROUCHING && PlayerStatePredicates.isMoving(player, event)) {
            return playIfPresent(event, "sneak", ILoopType.EDefaultLoopTypes.LOOP);
        }
        if (player.onGround() && player.getPose() == Pose.CROUCHING) {
            return playIfPresent(event, "sneaking", ILoopType.EDefaultLoopTypes.LOOP);
        }
        return null;
    }

    private PlayState testLow(Player player, AnimationEvent<PlayerGeoEntity> event) {
        if (player.onGround() && player.isSprinting() && PlayerStatePredicates.isMoving(player, event)) {
            return playIfPresent(event, "run", ILoopType.EDefaultLoopTypes.LOOP);
        }
        if (player.onGround() && PlayerStatePredicates.isMoving(player, event)) {
            return playIfPresent(event, "walk", ILoopType.EDefaultLoopTypes.LOOP);
        }
        return null;
    }

    private PlayState playIfPresent(AnimationEvent<PlayerGeoEntity> event, String animationName, ILoopType loopType) {
        if (event.getAnimatable().getAnimation(animationName) == null) {
            return null;
        }
        return IAnimationPredicate.playAnimationWithLoop(event, animationName, loopType);
    }

    private static float getVerticalSpeed(Player player) {
        return 20.0f * ((float) (player.position().y - player.yo));
    }
}
