package com.elfmcys.yesstevemodel.client.renderer;

import com.elfmcys.yesstevemodel.client.entity.CustomPlayerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

public final class PreviewEntityRegistry {

    @FunctionalInterface
    public interface SceneryRenderer {
        void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight);
    }

    public record Entry(
            CustomPlayerEntity animatable,
            @Nullable SceneryRenderer beforeEntity,
            @Nullable SceneryRenderer afterEntity
    ) {
    }

    private static final Map<EntityRenderState, Entry> ENTRIES = new IdentityHashMap<>();

    private PreviewEntityRegistry() {
    }

    public static void register(EntityRenderState state, CustomPlayerEntity animatable) {
        ENTRIES.put(state, new Entry(animatable, null, null));
    }

    public static void register(
            EntityRenderState state,
            CustomPlayerEntity animatable,
            @Nullable SceneryRenderer beforeEntity,
            @Nullable SceneryRenderer afterEntity
    ) {
        ENTRIES.put(state, new Entry(animatable, beforeEntity, afterEntity));
    }

    @Deprecated
    @Nullable
    public static CustomPlayerEntity get(EntityRenderState state) {
        Entry entry = ENTRIES.get(state);
        return entry == null ? null : entry.animatable();
    }

    @Nullable
    public static Entry getEntry(EntityRenderState state) {
        return ENTRIES.get(state);
    }

    public static void remove(EntityRenderState state) {
        ENTRIES.remove(state);
    }
}
