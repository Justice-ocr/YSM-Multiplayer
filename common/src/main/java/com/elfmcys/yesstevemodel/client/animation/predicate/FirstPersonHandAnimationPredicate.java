package com.elfmcys.yesstevemodel.client.animation.predicate;

import com.elfmcys.yesstevemodel.client.animation.IAnimationPredicate;
import com.elfmcys.yesstevemodel.client.animation.condition.ConditionHold;
import com.elfmcys.yesstevemodel.client.animation.condition.InnerClassify;
import com.elfmcys.yesstevemodel.client.animation.condition.ConditionManager;
import com.elfmcys.yesstevemodel.client.animation.condition.ConditionSwing;
import com.elfmcys.yesstevemodel.client.animation.condition.ConditionUse;
import com.elfmcys.yesstevemodel.client.entity.IPreviewAnimatable;
import com.elfmcys.yesstevemodel.client.entity.PlayerGeoEntity;
import com.elfmcys.yesstevemodel.geckolib3.core.builder.ILoopType;
import com.elfmcys.yesstevemodel.geckolib3.core.enums.PlayState;
import com.elfmcys.yesstevemodel.geckolib3.core.event.predicate.AnimationEvent;
import com.elfmcys.yesstevemodel.molang.runtime.ExpressionEvaluator;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.apache.commons.lang3.StringUtils;

public class FirstPersonHandAnimationPredicate implements IAnimationPredicate<PlayerGeoEntity> {
    private final Mode mode;

    private FirstPersonHandAnimationPredicate(Mode mode) {
        this.mode = mode;
    }

    public static FirstPersonHandAnimationPredicate holdMainhand() {
        return new FirstPersonHandAnimationPredicate(Mode.HOLD_MAINHAND);
    }

    public static FirstPersonHandAnimationPredicate holdOffhand() {
        return new FirstPersonHandAnimationPredicate(Mode.HOLD_OFFHAND);
    }

    public static FirstPersonHandAnimationPredicate swing() {
        return new FirstPersonHandAnimationPredicate(Mode.SWING);
    }

    public static FirstPersonHandAnimationPredicate use() {
        return new FirstPersonHandAnimationPredicate(Mode.USE);
    }

    @Override
    public PlayState predicate(AnimationEvent<PlayerGeoEntity> event, ExpressionEvaluator<?> evaluator) {
        Player player = event.getAnimatable().getEntity();
        if (player == null || event.getAnimatable() instanceof IPreviewAnimatable) {
            return PlayState.STOP;
        }
        return switch (this.mode) {
            case HOLD_MAINHAND -> hold(event, player, InteractionHand.MAIN_HAND);
            case HOLD_OFFHAND -> hold(event, player, InteractionHand.OFF_HAND);
            case SWING -> swing(event, player);
            case USE -> use(event, player);
        };
    }

    private PlayState hold(AnimationEvent<PlayerGeoEntity> event, Player player, InteractionHand hand) {
        if (!checkSwingAndUse(player, hand)) {
            return PlayState.PAUSE;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(Items.CROSSBOW) && CrossbowItem.isCharged(stack)) {
            PlayState chargedCrossbow = playIfPresent(event, hand == InteractionHand.MAIN_HAND ? "hold_mainhand:charged_crossbow" : "hold_offhand:charged_crossbow", ILoopType.EDefaultLoopTypes.LOOP);
            if (chargedCrossbow != null) {
                return chargedCrossbow;
            }
        }
        if (hand == InteractionHand.MAIN_HAND && player.fishing != null) {
            PlayState fishing = playIfPresent(event, "hold_mainhand:fishing", ILoopType.EDefaultLoopTypes.LOOP);
            if (fishing != null) {
                return fishing;
            }
        }
        ConditionHold conditionHold = hand == InteractionHand.MAIN_HAND ? getConditionManager(event).getHoldMainhand() : getConditionManager(event).getHoldOffhand();
        String animationName = conditionHold.doTest(player, hand);
        if (StringUtils.isNoneBlank(animationName)) {
            PlayState playState = playIfPresent(event, animationName, ILoopType.EDefaultLoopTypes.LOOP);
            if (playState != null) {
                return playState;
            }
        }
        PlayState fallback = playFallbackItemAnimation(event, stack, hand == InteractionHand.MAIN_HAND ? "hold_mainhand:" : "hold_offhand:", ILoopType.EDefaultLoopTypes.LOOP);
        if (fallback != null) {
            return fallback;
        }
        return PlayState.STOP;
    }

    private PlayState swing(AnimationEvent<PlayerGeoEntity> event, Player player) {
        if (!player.swinging || player.isSleeping()) {
            return PlayState.CONTINUE;
        }
        ConditionSwing conditionSwing = player.swingingArm == InteractionHand.MAIN_HAND ? getConditionManager(event).getSwingMainhand() : getConditionManager(event).getSwingOffhand();
        String animationName = conditionSwing.doTest(player, player.swingingArm);
        if (StringUtils.isNoneBlank(animationName)) {
            PlayState playState = playIfPresent(event, animationName, ILoopType.EDefaultLoopTypes.PLAY_ONCE);
            if (playState != null) {
                return playState;
            }
        }
        PlayState fallback = playFallbackItemAnimation(event, player.getItemInHand(player.swingingArm), player.swingingArm == InteractionHand.MAIN_HAND ? "swing:" : "swing_offhand:", ILoopType.EDefaultLoopTypes.PLAY_ONCE);
        if (fallback != null) {
            return fallback;
        }
        return playIfPresentOrContinue(event, player.swingingArm == InteractionHand.MAIN_HAND ? "swing_hand" : "swing_offhand", ILoopType.EDefaultLoopTypes.PLAY_ONCE);
    }

    private PlayState use(AnimationEvent<PlayerGeoEntity> event, Player player) {
        if (!player.isUsingItem() || player.isSleeping()) {
            return PlayState.STOP;
        }
        InteractionHand hand = player.getUsedItemHand();
        ConditionUse conditionUse = hand == InteractionHand.MAIN_HAND ? getConditionManager(event).getUseMainhand() : getConditionManager(event).getUseOffhand();
        String animationName = conditionUse.doTest(player, hand);
        if (StringUtils.isNoneBlank(animationName)) {
            PlayState playState = playIfPresent(event, animationName, ILoopType.EDefaultLoopTypes.LOOP);
            if (playState != null) {
                return playState;
            }
        }
        PlayState fallback = playFallbackItemAnimation(event, player.getItemInHand(hand), hand == InteractionHand.MAIN_HAND ? "use_mainhand:" : "use_offhand:", ILoopType.EDefaultLoopTypes.LOOP);
        if (fallback != null) {
            return fallback;
        }
        return playIfPresentOrStop(event, hand == InteractionHand.MAIN_HAND ? "use_mainhand" : "use_offhand", ILoopType.EDefaultLoopTypes.LOOP);
    }

    private ConditionManager getConditionManager(AnimationEvent<PlayerGeoEntity> event) {
        return event.getAnimatable().getModelAssembly().getAnimationBundle().getConditionManager();
    }

    private PlayState playIfPresentOrContinue(AnimationEvent<PlayerGeoEntity> event, String animationName, ILoopType loopType) {
        PlayState playState = playIfPresent(event, animationName, loopType);
        return playState == null ? PlayState.CONTINUE : playState;
    }

    private PlayState playIfPresentOrStop(AnimationEvent<PlayerGeoEntity> event, String animationName, ILoopType loopType) {
        PlayState playState = playIfPresent(event, animationName, loopType);
        return playState == null ? PlayState.STOP : playState;
    }

    private PlayState playIfPresent(AnimationEvent<PlayerGeoEntity> event, String animationName, ILoopType loopType) {
        if (event.getAnimatable().getAnimation(animationName) == null) {
            return null;
        }
        return IAnimationPredicate.playAnimationWithLoop(event, animationName, loopType);
    }

    private PlayState playFallbackItemAnimation(AnimationEvent<PlayerGeoEntity> event, ItemStack stack, String prefix, ILoopType loopType) {
        String itemType = InnerClassify.getItemType(stack);
        if (itemType.isEmpty()) {
            return null;
        }
        PlayState playState = playIfPresent(event, prefix + itemType, loopType);
        if (playState != null) {
            return playState;
        }
        if ("spear".equals(itemType)) {
            return playIfPresent(event, prefix + "trident", loopType);
        }
        return null;
    }

    private boolean checkSwingAndUse(Player player, InteractionHand hand) {
        if (player.swinging && player.swingingArm == hand) {
            return false;
        }
        return !player.isUsingItem() || player.getUsedItemHand() != hand;
    }

    private enum Mode {
        HOLD_MAINHAND,
        HOLD_OFFHAND,
        SWING,
        USE
    }
}
