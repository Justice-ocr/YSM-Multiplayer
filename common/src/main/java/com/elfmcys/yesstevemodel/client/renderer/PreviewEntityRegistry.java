package com.elfmcys.yesstevemodel.client.renderer;

import com.elfmcys.yesstevemodel.client.entity.CustomPlayerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Associates an in-flight {@link EntityRenderState} with the YSM animatable
 * (e.g. {@code PlayerCapability} for the local player, or a
 * {@code PlayerPreviewEntity} for a button thumbnail) that should drive its
 * deferred PIP render, plus optional scenery callbacks that run before/after
 * the entity render inside the same PIP texture.
 *
 * <p>1.21.8 GUI rendering is deferred: {@code submitEntityRenderState} captures
 * a state and the actual draw runs later in
 * {@code GuiEntityRenderer.renderToTexture}. The redirect in
 * {@code GuiEntityRendererMixin} looks up the entry keyed on the state identity
 * and routes the render through {@link CustomPlayerRenderer#renderEntity}
 * directly, bypassing the vanilla dispatcher and the
 * {@code PlayerCapability.get(player)} lookup that
 * {@code ReplacePlayerRenderEvent} would otherwise perform. The scenery
 * callbacks share the PIP {@link PoseStack} / {@link MultiBufferSource} so
 * ground / bed / vehicle props appear in the same texture, with correct
 * depth ordering against the player.
 */
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

    /**
     * @deprecated Prefer {@link #getEntry(EntityRenderState)} which also exposes scenery callbacks.
     */
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
