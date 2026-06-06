package com.elfmcys.yesstevemodel.client.animation.molang.functions.ysm;

import com.elfmcys.yesstevemodel.client.animation.PlayerStatePredicates;
import com.elfmcys.yesstevemodel.client.animation.molang.functions.physics.SecondOrder;
import com.elfmcys.yesstevemodel.client.animation.molang.PhysicsManager;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.context.IContext;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.util.StringPool;
import com.elfmcys.yesstevemodel.client.animation.molang.functions.physics.IPhysics;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.funciton.entity.EntityFunction;
import com.elfmcys.yesstevemodel.molang.runtime.ExecutionContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Locale;

public class SecondOrderFunction extends EntityFunction {
    @Override
    public Object eval(ExecutionContext<IContext<Entity>> context, ArgumentCollection arguments) {
        int name = arguments.getStringId(context, 0);
        if (name == StringPool.EMPTY_ID) {
            return 0;
        }
        float input = arguments.getAsFloat(context, 1);
        int size = arguments.size();
        float frequency = 1.0f;
        float coefficient = 1.0f;
        float response = 1.0f;
        if (size >= 3) {
            frequency = arguments.getAsFloat(context, 2);
        }
        if (size >= 4) {
            coefficient = arguments.getAsFloat(context, 3);
        }
        if (size >= 5) {
            response = arguments.getAsFloat(context, 4);
        }
        PhysicsManager physicsManager = context.entity().geoInstance().getPhysicsManager();
        if (shouldClampStationaryGroundSpeed(context.entity(), name, input)) {
            physicsManager.put(name, new SecondOrder(0.0f, frequency, coefficient, response));
            return 0.0f;
        }
        IPhysics physics = physicsManager.get(name);
        if (physics == null) {
            physicsManager.put(name, new SecondOrder(input, frequency, coefficient, response));
            return input;
        }
        physics.setArgs(input, frequency, coefficient, response);
        return physics.getValue();
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size >= 2;
    }

    private static boolean shouldClampStationaryGroundSpeed(IContext<Entity> context, int name, float input) {
        Entity entity = context.entity();
        if (!(entity instanceof LivingEntity livingEntity) || Math.abs(input) > 1.0E-4f) {
            return false;
        }
        if (!PlayerStatePredicates.isStationaryLocalPlayer(livingEntity, context.animationEvent())) {
            return false;
        }
        String key = StringPool.getString(name);
        String lowerKey = key.toLowerCase(Locale.ROOT);
        return key.contains("\u5730\u901f")
                || lowerKey.contains("ground_speed")
                || (lowerKey.contains("ground") && lowerKey.contains("speed"));
    }
}
