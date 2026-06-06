package com.elfmcys.yesstevemodel.geckolib3.util;

import com.elfmcys.yesstevemodel.geckolib3.core.EntityFrameStateTracker;
import com.elfmcys.yesstevemodel.geckolib3.core.event.predicate.AnimationEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class MovementQuery {
    public static final float EPSILON = 1.0E-4f;

    private MovementQuery() {
    }

    public static float getTimeDelta(EntityFrameStateTracker<?> tracker) {
        float timeDelta = tracker.getTimeDelta();
        return Float.isFinite(timeDelta) && timeDelta > EPSILON ? timeDelta : 0.0f;
    }

    public static Vec3 getPositionDelta(Entity entity, EntityFrameStateTracker<?> tracker) {
        Vec3 trackerDelta = sanitize(tracker.getPositionDelta());
        if (hasMovement(trackerDelta)) {
            return trackerDelta;
        }

        Vec3 tickDelta = sanitize(new Vec3(entity.getX() - entity.xo, entity.getY() - entity.yo, entity.getZ() - entity.zo));
        if (hasMovement(tickDelta)) {
            return tickDelta;
        }

        Vec3 velocity = sanitize(entity.getDeltaMovement());
        if (hasMovement(velocity)) {
            float timeDelta = getTimeDelta(tracker);
            return velocity.scale(timeDelta > EPSILON ? timeDelta : 1.0f);
        }
        return Vec3.ZERO;
    }

    public static float getMeasuredGroundSpeed(Entity entity, EntityFrameStateTracker<?> tracker) {
        Vec3 trackerDelta = sanitize(tracker.getPositionDelta());
        float trackerSpeed = getHorizontalSpeedFromDelta(trackerDelta, tracker);
        if (isUsable(trackerSpeed)) {
            return trackerSpeed;
        }

        Vec3 velocity = sanitize(entity.getDeltaMovement());
        float velocitySpeed = 20.0f * horizontalLength(velocity);
        if (isUsable(velocitySpeed)) {
            return velocitySpeed;
        }

        Vec3 tickDelta = sanitize(new Vec3(entity.getX() - entity.xo, entity.getY() - entity.yo, entity.getZ() - entity.zo));
        float tickSpeed = 20.0f * horizontalLength(tickDelta);
        return isUsable(tickSpeed) ? tickSpeed : 0.0f;
    }

    public static float getGroundSpeed(Entity entity, EntityFrameStateTracker<?> tracker, @Nullable AnimationEvent<?> event) {
        float measuredSpeed = getMeasuredGroundSpeed(entity, tracker);
        if (isUsable(measuredSpeed)) {
            return measuredSpeed;
        }

        if (event != null) {
            float limbSwingAmount = Math.abs(event.getLimbSwingAmount());
            if (isUsable(limbSwingAmount)) {
                return limbSwingAmount;
            }
        }

        if (entity instanceof LivingEntity livingEntity) {
            float partialTick = event != null ? event.getPartialTick() : 1.0f;
            float walkSpeed = Math.abs(livingEntity.walkAnimation.speed(partialTick));
            return isUsable(walkSpeed) ? walkSpeed : 0.0f;
        }
        return 0.0f;
    }

    public static boolean isGroundMoving(Entity entity, EntityFrameStateTracker<?> tracker, @Nullable AnimationEvent<?> event, float minSpeed) {
        float measuredSpeed = getMeasuredGroundSpeed(entity, tracker);
        if (measuredSpeed > minSpeed) {
            return true;
        }
        return measuredSpeed > EPSILON && event != null && Math.abs(event.getLimbSwingAmount()) > minSpeed;
    }

    public static float getVerticalSpeed(Entity entity, EntityFrameStateTracker<?> tracker) {
        Vec3 trackerDelta = sanitize(tracker.getPositionDelta());
        float timeDelta = getTimeDelta(tracker);
        if (timeDelta > EPSILON && Math.abs(trackerDelta.y) > EPSILON) {
            float trackerSpeed = (20.0f * (float) trackerDelta.y) / timeDelta;
            if (Float.isFinite(trackerSpeed)) {
                return trackerSpeed;
            }
        }

        Vec3 velocity = sanitize(entity.getDeltaMovement());
        if (Math.abs(velocity.y) > EPSILON) {
            float velocitySpeed = 20.0f * (float) velocity.y;
            if (Float.isFinite(velocitySpeed)) {
                return velocitySpeed;
            }
        }

        float tickSpeed = 20.0f * (float) (entity.getY() - entity.yo);
        return Float.isFinite(tickSpeed) ? tickSpeed : 0.0f;
    }

    private static float getHorizontalSpeedFromDelta(Vec3 delta, EntityFrameStateTracker<?> tracker) {
        float timeDelta = getTimeDelta(tracker);
        if (timeDelta <= EPSILON) {
            return 0.0f;
        }
        return (20.0f * horizontalLength(delta)) / timeDelta;
    }

    private static float horizontalLength(Vec3 vec3) {
        return Mth.sqrt((float) ((vec3.x * vec3.x) + (vec3.z * vec3.z)));
    }

    private static boolean hasMovement(Vec3 vec3) {
        return vec3.lengthSqr() > EPSILON * EPSILON;
    }

    private static boolean isUsable(float value) {
        return Float.isFinite(value) && value > EPSILON;
    }

    private static Vec3 sanitize(Vec3 vec3) {
        return Double.isFinite(vec3.x) && Double.isFinite(vec3.y) && Double.isFinite(vec3.z) ? vec3 : Vec3.ZERO;
    }
}
