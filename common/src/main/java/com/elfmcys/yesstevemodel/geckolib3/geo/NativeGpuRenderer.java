package com.elfmcys.yesstevemodel.geckolib3.geo;

import com.elfmcys.yesstevemodel.NativeLibLoader;
import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.config.GeneralConfig;
import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

public final class NativeGpuRenderer {
    public static final int MAX_GPU_BONES = 256;

    private static final long LOG_INTERVAL_NANOS = 5_000_000_000L;
    private static final VertexFormatElement PACKED_NORMAL = VertexFormatElement.register(20, 0, VertexFormatElement.Type.UINT, VertexFormatElement.Usage.GENERIC, 1);
    private static final VertexFormatElement BONE_ID = VertexFormatElement.register(21, 1, VertexFormatElement.Type.USHORT, VertexFormatElement.Usage.GENERIC, 1);
    private static final VertexFormatElement PART_FLAGS = VertexFormatElement.register(22, 2, VertexFormatElement.Type.UBYTE, VertexFormatElement.Usage.GENERIC, 2);

    public static final VertexFormat VERTEX_FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("UV0", VertexFormatElement.UV0)
            .add("PackedNormal", PACKED_NORMAL)
            .add("BoneId", BONE_ID)
            .add("PartFlags", PART_FLAGS)
            .padding(4)
            .build();

    public static final RenderPipeline PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation(ResourceLocation.fromNamespaceAndPath(YesSteveModel.MOD_ID, "pipeline/ysm_gpu_entity"))
            .withVertexShader(ResourceLocation.fromNamespaceAndPath(YesSteveModel.MOD_ID, "core/ysm_gpu_entity"))
            .withFragmentShader(ResourceLocation.fromNamespaceAndPath(YesSteveModel.MOD_ID, "core/ysm_gpu_entity"))
            .withShaderDefine("YSM_GPU_MAX_BONES", MAX_GPU_BONES)
            .withUniform("YsmBones", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(VERTEX_FORMAT, VertexFormat.Mode.TRIANGLES)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withCull(false)
            .build());

    private static boolean pipelinePrecompileAttempted;
    private static boolean pipelineReady;
    private static boolean pipelineFailureLogged;
    private static long lastLogTime = System.nanoTime();
    private static long preparedStaticMeshes;
    private static long preparedBoneBuffers;
    private static long preparedIndices;
    private static long submittedDraws;

    private NativeGpuRenderer() {
    }

    public static void prepareStaticMesh(GeoModel mesh, int renderPartMask, boolean isPreview) {
        if (!isEnabled() || isPreview || !RenderSystem.isOnRenderThread()) {
            return;
        }
        NativeGpuMeshCache cache = mesh.getOrUploadNativeGpuMesh();
        if (cache == null || cache.indexCount <= 0 || !ensurePipelineReady()) {
            return;
        }
        DrawRange range = DrawRange.forMask(cache, renderPartMask);
        if (range.indexCount <= 0) {
            return;
        }
        preparedStaticMeshes++;
        preparedIndices += range.indexCount;
        logProgress();
    }

    public static boolean drawPrepared(RenderPass renderPass, GpuTextureView textureView, NativeGpuMeshCache cache, int renderPartMask) {
        if (!isEnabled() || renderPass == null || textureView == null || cache == null || cache.boneBuffer == null || cache.boneBuffer.isClosed()) {
            return false;
        }
        if (!ensurePipelineReady()) {
            return false;
        }
        DrawRange range = DrawRange.forMask(cache, renderPartMask);
        if (range.indexCount <= 0) {
            return false;
        }

        renderPass.pushDebugGroup(() -> "YSM GPU model");
        try {
            renderPass.setPipeline(PIPELINE);
            renderPass.bindSampler("Sampler0", textureView);
            renderPass.setUniform("YsmBones", cache.boneBuffer);
            renderPass.setVertexBuffer(0, cache.vertexBuffer);
            renderPass.setIndexBuffer(cache.indexBuffer, VertexFormat.IndexType.INT);
            renderPass.drawIndexed(range.firstIndex, range.indexCount, 0, 1);
            submittedDraws++;
            return true;
        } finally {
            renderPass.popDebugGroup();
        }
    }

    public static void prepareBoneDraw(GeoModel mesh, int renderPartMask, int boneCount) {
        if (!isEnabled() || !RenderSystem.isOnRenderThread()) {
            return;
        }
        NativeGpuMeshCache cache = mesh.nativeGpuMeshCache;
        if (cache == null || cache.boneBuffer == null || cache.boneBuffer.isClosed() || boneCount <= 0 || boneCount > MAX_GPU_BONES || !ensurePipelineReady()) {
            return;
        }
        DrawRange range = DrawRange.forMask(cache, renderPartMask);
        if (range.indexCount <= 0) {
            return;
        }
        preparedBoneBuffers++;
        preparedIndices += range.indexCount;
        logProgress();
    }

    private static boolean isEnabled() {
        return Boolean.TRUE.equals(GeneralConfig.USE_EXPERIMENTAL_GPU_RENDERER.get())
                && Boolean.TRUE.equals(GeneralConfig.USE_NATIVE_RENDERER.get())
                && NativeLibLoader.isLoaded();
    }

    private static boolean ensurePipelineReady() {
        if (pipelineReady) {
            return true;
        }
        if (pipelinePrecompileAttempted) {
            return false;
        }
        pipelinePrecompileAttempted = true;
        try {
            RenderSystem.getDevice().precompilePipeline(PIPELINE);
            pipelineReady = true;
            YesSteveModel.LOGGER.info("[YSM GPU] Pipeline ready: format={} bytes", VERTEX_FORMAT.getVertexSize());
            return true;
        } catch (Throwable throwable) {
            if (!pipelineFailureLogged) {
                pipelineFailureLogged = true;
                YesSteveModel.LOGGER.warn("[YSM GPU] Pipeline precompile failed; keeping native/Java renderer active", throwable);
            }
            return false;
        }
    }

    private static void logProgress() {
        if (!Boolean.TRUE.equals(GeneralConfig.RENDER_PROFILING.get())) {
            return;
        }
        long now = System.nanoTime();
        if (now - lastLogTime < LOG_INTERVAL_NANOS) {
            return;
        }
        lastLogTime = now;
        YesSteveModel.LOGGER.info("[YSM GPU] prepared static={}, bones={}, indices={}, mode={}",
                preparedStaticMeshes,
                preparedBoneBuffers,
                preparedIndices,
                pipelineReady ? "pipeline-ready" : "pipeline-pending");
        if (submittedDraws > 0) {
            YesSteveModel.LOGGER.info("[YSM GPU] submitted draws={}", submittedDraws);
        }
        preparedStaticMeshes = 0;
        preparedBoneBuffers = 0;
        preparedIndices = 0;
        submittedDraws = 0;
    }

    private record DrawRange(int firstIndex, int indexCount) {
        private static DrawRange forMask(NativeGpuMeshCache cache, int renderPartMask) {
            return switch (renderPartMask) {
                case 1 -> new DrawRange(cache.partMask1Start, cache.partMask1Count + cache.partMask3Count);
                case 2 -> new DrawRange(cache.partMask2Start, cache.partMask2Count + cache.partMask3Count);
                case 3 -> new DrawRange(cache.partMask3Start, cache.partMask3Count);
                default -> new DrawRange(0, cache.indexCount);
            };
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "DrawRange[first=%d,count=%d]", firstIndex, indexCount);
        }
    }
}
