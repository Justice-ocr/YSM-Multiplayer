package com.elfmcys.yesstevemodel.client.input;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.util.InputUtil;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.client.ClientRawInputEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import rip.ysm.api.PlatformAPI;

public class InputStateKey {

    public static volatile boolean[] keyStates = new boolean[349];

    public static volatile boolean[] mouseStates = new boolean[8];

    private static final int SWING_PULSE_TICKS = 10;

    private static volatile InteractionHand swingPulseHand = InteractionHand.MAIN_HAND;

    private static volatile int swingPulseTicks;

    private static volatile int swingPulseAge;

    private static volatile boolean lastAttackKeyDown;

    private InputStateKey() {
    }

    public static void register() {
        if (PlatformAPI.isServer()) {
            return;
        }
        ClientRawInputEvent.KEY_PRESSED.register((client, action, event) -> {
            onKeyInput(event.key(), action);
            return EventResult.pass();
        });
        ClientRawInputEvent.MOUSE_CLICKED_PRE.register((client, buttonInfo, action) -> {
            onMouseInput(buttonInfo.button(), action);
            return EventResult.pass();
        });
    }

    private static void onKeyInput(int keyCode, int action) {
        if (YesSteveModel.isAvailable() && InputUtil.isPlayerReady() && 32 <= keyCode && keyCode <= 348) {
            if (action == 1) {
                keyStates[keyCode] = true;
            } else if (action == 0) {
                keyStates[keyCode] = false;
            }
        }
    }

    private static void onMouseInput(int button, int action) {
        if (YesSteveModel.isAvailable() && InputUtil.isPlayerReady() && 0 <= button && button <= 7) {
            if (action == 1) {
                mouseStates[button] = true;
            } else if (action == 0) {
                mouseStates[button] = false;
            }
            if (button == 0 && action == 1) {
                recordSwingPulse(InteractionHand.MAIN_HAND);
            }
        }
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player != null && minecraft.screen == null && InputUtil.isPlayerReady()) {
            boolean attackDown = minecraft.options.keyAttack.isDown();
            if (attackDown && !lastAttackKeyDown) {
                recordSwingPulse(InteractionHand.MAIN_HAND);
            }
            lastAttackKeyDown = attackDown;
        } else {
            lastAttackKeyDown = false;
        }
        if (swingPulseTicks > 0) {
            swingPulseTicks--;
            swingPulseAge++;
        }
    }

    public static boolean isSwinging(LivingEntity entity, InteractionHand hand) {
        if (entity == null || entity.isSleeping()) {
            return false;
        }
        if (entity.swinging && entity.swingingArm == hand) {
            return true;
        }
        if (!isLocalPlayer(entity) && hand == InteractionHand.MAIN_HAND && entity.getAttackAnim(0.0f) > 0.0f) {
            return true;
        }
        return isLocalPlayer(entity) && swingPulseTicks > 0 && swingPulseHand == hand;
    }

    public static boolean isAnyHandSwinging(LivingEntity entity) {
        return isSwinging(entity, InteractionHand.MAIN_HAND) || isSwinging(entity, InteractionHand.OFF_HAND);
    }

    public static InteractionHand getSwingingHand(LivingEntity entity) {
        if (entity != null && entity.swinging) {
            return entity.swingingArm;
        }
        return swingPulseHand;
    }

    public static float getSwingTicks(LivingEntity entity, float partialTick) {
        if (entity == null || entity.isSleeping()) {
            return 0.0f;
        }
        if (entity.swinging) {
            return Math.max(0.0f, entity.swingTime + partialTick);
        }
        if (isLocalPlayer(entity) && swingPulseTicks > 0) {
            return Math.max(1.0f, swingPulseAge + partialTick);
        }
        return 0.0f;
    }

    public static float getAttackProgress(LivingEntity entity, float partialTick) {
        if (entity == null || entity.isSleeping()) {
            return 0.0f;
        }
        float attackProgress = entity.getAttackAnim(partialTick);
        if (attackProgress > 0.0f) {
            return attackProgress;
        }
        if (isLocalPlayer(entity) && swingPulseTicks > 0 && swingPulseHand == InteractionHand.MAIN_HAND) {
            return Math.min(1.0f, getSwingTicks(entity, partialTick) / 6.0f);
        }
        return 0.0f;
    }

    public static boolean isLocalSwinging(InteractionHand hand) {
        return swingPulseTicks > 0 && swingPulseHand == hand;
    }

    public static boolean isLocalPlayerEntity(LivingEntity entity) {
        return isLocalPlayer(entity);
    }

    public static int getLocalSwingPulseAge() {
        return swingPulseAge;
    }

    private static void recordSwingPulse(InteractionHand hand) {
        swingPulseHand = hand;
        swingPulseTicks = SWING_PULSE_TICKS;
        swingPulseAge = 1;
    }

    private static boolean isLocalPlayer(LivingEntity entity) {
        LocalPlayer player = Minecraft.getInstance().player;
        return entity == player || (player != null && entity instanceof Player other && other.getUUID().equals(player.getUUID()));
    }
}
