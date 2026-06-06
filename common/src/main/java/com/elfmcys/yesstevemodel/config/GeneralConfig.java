package com.elfmcys.yesstevemodel.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class GeneralConfig {

    public static ForgeConfigSpec.BooleanValue DISCLAIMER_SHOW;

    public static ForgeConfigSpec.BooleanValue PRINT_ANIMATION_ROULETTE_MSG;

    public static ForgeConfigSpec.BooleanValue DISABLE_SELF_MODEL;

    public static ForgeConfigSpec.BooleanValue DISABLE_OTHER_MODEL;

    public static ForgeConfigSpec.BooleanValue DISABLE_SELF_HANDS;

    public static ForgeConfigSpec.BooleanValue DISABLE_PROJECTILE_MODEL;

    public static ForgeConfigSpec.BooleanValue DISABLE_VEHICLE_MODEL;

    public static ForgeConfigSpec.BooleanValue DISABLE_EXTERNAL_FP_ANIM;

    public static ForgeConfigSpec.BooleanValue USE_COMPATIBILITY_RENDERER;

    public static ForgeConfigSpec.BooleanValue USE_NATIVE_RENDERER;

    public static ForgeConfigSpec.BooleanValue USE_EXPERIMENTAL_GPU_RENDERER;

    public static ForgeConfigSpec.BooleanValue RENDER_PROFILING;

    public static ForgeConfigSpec.ConfigValue<String> SELF_PLAYER_RENDER_ORDER;

    public static ForgeConfigSpec.ConfigValue<String> OTHER_PLAYER_RENDER_ORDER;

    public static ForgeConfigSpec.DoubleValue SOUND_VOLUME;

    public static ForgeConfigSpec.BooleanValue SHOW_MODEL_ID_FIRST;

    public static ForgeConfigSpec.BooleanValue SOPHISTICATEDBACKPACK;

    public static ForgeConfigSpec.BooleanValue PARCOOL;

    /** 서버에 YSM 없을 때 자동으로 로컬 모델 적용 여부 */
    public static ForgeConfigSpec.BooleanValue OFFLINE_MODEL_ENABLED;

    /** 마지막으로 선택한 로컬 모델 ID */
    public static ForgeConfigSpec.ConfigValue<String> OFFLINE_MODEL_ID;

    /** 마지막으로 선택한 로컬 텍스처 ID */
    public static ForgeConfigSpec.ConfigValue<String> OFFLINE_TEXTURE_ID;

    public static ForgeConfigSpec buildSpec() {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        defineGeneral(builder);
        ExtraPlayerRenderConfig.define(builder);
        LoadingStateConfig.define(builder);
        return builder.build();
    }

    public static void defineGeneral(ForgeConfigSpec.Builder builder) {
        builder.push("general");
        builder.comment("Whether to display disclaimer GUI");
        DISCLAIMER_SHOW = builder.define("DisclaimerShow", true);
        builder.comment("Whether to print animation roulette play message");
        PRINT_ANIMATION_ROULETTE_MSG = builder.define("PrintAnimationRouletteMsg", false);
        builder.comment("Prevents rendering of self player's model");
        DISABLE_SELF_MODEL = builder.define("DisableSelfModel", false);
        builder.comment("Prevents rendering of other player's model");
        DISABLE_OTHER_MODEL = builder.define("DisableOtherModel", false);
        builder.comment("Prevents rendering of self player's hand");
        DISABLE_SELF_HANDS = builder.define("DisableSelfHands", false);
        builder.comment("Prevents rendering of projectile model");
        DISABLE_PROJECTILE_MODEL = builder.define("DisableProjectileModel", false);
        builder.comment("Prevents rendering of vehicle model");
        DISABLE_VEHICLE_MODEL = builder.define("DisableVehicleModel", false);
        builder.comment("Disable first person animation from other mods.");
        DISABLE_EXTERNAL_FP_ANIM = builder.define("DisableExternalFirstPersonAnim", false);
        builder.comment("If rendering errors occur, try turning on this.");
        USE_COMPATIBILITY_RENDERER = builder.define("UseCompatibilityRenderer", false);
        builder.comment("Experimental: use native SIMD model renderer when native cache is available.");
        USE_NATIVE_RENDERER = builder.define("UseNativeRenderer", false);
        builder.comment("Experimental: use the direct GPU model renderer when the native mesh and OpenGL SSBO path are available.");
        USE_EXPERIMENTAL_GPU_RENDERER = builder.define("UseExperimentalGpuRenderer", false);
        builder.comment("Print model renderer performance statistics to the log.");
        RENDER_PROFILING = builder.define("RenderProfiling", false);
        builder.comment("Comma separated self player render order. Supported values: VANILLA, LOCAL_YSM, SERVER_YSM.");
        SELF_PLAYER_RENDER_ORDER = builder.define("SelfPlayerRenderOrder", "LOCAL_YSM");
        builder.comment("Comma separated other player render order. Supported values: VANILLA, LOCAL_YSM, SERVER_YSM.");
        OTHER_PLAYER_RENDER_ORDER = builder.define("OtherPlayerRenderOrder", "SERVER_YSM");
        builder.comment("The amount of volume when the animation is played.");
        SOUND_VOLUME = builder.defineInRange("SoundVolume", 100.0d, 0.0d, 100.0d);
        builder.comment("Whether to display model ID first in the model selection screen, instead of the model name filled in by the model author.");
        SHOW_MODEL_ID_FIRST = builder.define("ShowModelIdFirst", false);
        builder.pop();
        builder.push("Integration");
        SOPHISTICATEDBACKPACK = builder.define("SophisticatedBackpack", true);
        PARCOOL = builder.define("Parcool", true);
        builder.pop();
        builder.push("OfflineModel");
        builder.comment("When connected to a server without YSM, automatically apply local model for self rendering.");
        OFFLINE_MODEL_ENABLED = builder.define("OfflineModelEnabled", true);
        builder.comment("Last selected local model ID (auto-saved).");
        OFFLINE_MODEL_ID = builder.define("OfflineModelId", "");
        builder.comment("Last selected local texture ID (auto-saved).");
        OFFLINE_TEXTURE_ID = builder.define("OfflineTextureId", "");
        builder.pop();
    }
}
