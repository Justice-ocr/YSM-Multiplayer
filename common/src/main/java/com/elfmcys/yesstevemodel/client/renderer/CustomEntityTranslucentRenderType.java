package com.elfmcys.yesstevemodel.client.renderer;

import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.function.Function;

public class CustomEntityTranslucentRenderType extends RenderType {

    private static final Function<ResourceLocation, CustomEntityTranslucentRenderType> CACHE = Util.memoize(CustomEntityTranslucentRenderType::new);

    private final RenderType delegate;
    private final boolean useBlend;
    private final Optional<RenderType> outlineType;

    private CustomEntityTranslucentRenderType(ResourceLocation resourceLocation) {
        this(RenderType.entityTranslucent(resourceLocation));
    }

    private CustomEntityTranslucentRenderType(RenderType renderType) {
        super("entity_translucent_ysm", renderType.bufferSize(), renderType.affectsCrumbling(), false, renderType::setupRenderState, renderType::clearRenderState);
        this.delegate = renderType;
        this.useBlend = renderType.isOutline();
        this.outlineType = renderType.outline();
    }

    @Override
    public void draw(MeshData meshData) {
        this.delegate.draw(meshData);
    }

    @Override
    public VertexFormat format() {
        return this.delegate.format();
    }

    @Override
    public VertexFormat.Mode mode() {
        return this.delegate.mode();
    }

    @Override
    public boolean isOutline() {
        return this.useBlend;
    }

    @Override
    @NotNull
    public Optional<RenderType> outline() {
        return this.outlineType;
    }

    public static CustomEntityTranslucentRenderType get(ResourceLocation resourceLocation) {
        return CACHE.apply(resourceLocation);
    }
}
