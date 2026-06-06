package com.elfmcys.yesstevemodel.geckolib3.geo;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;

import java.nio.ByteBuffer;

public final class NativeGpuMeshCache implements AutoCloseable {
    private static final int BONE_DATA_STRIDE_BYTES = 144;

    private static boolean uploadFailureLogged;
    private static boolean boneUploadFailureLogged;
    private static boolean firstUploadLogged;
    private static boolean firstBoneUploadLogged;

    public final GpuBuffer vertexBuffer;
    public final GpuBuffer indexBuffer;
    public final int vertexCount;
    public final int indexCount;
    public final int boneCount;
    public final int partMask1Start;
    public final int partMask1Count;
    public final int partMask2Start;
    public final int partMask2Count;
    public final int partMask3Start;
    public final int partMask3Count;

    public GpuBuffer boneBuffer;
    public int boneBufferSize;

    private boolean closed;

    private NativeGpuMeshCache(GpuBuffer vertexBuffer, GpuBuffer indexBuffer, int[] metadata) {
        this.vertexBuffer = vertexBuffer;
        this.indexBuffer = indexBuffer;
        this.vertexCount = metadata[0];
        this.indexCount = metadata[1];
        this.boneCount = metadata[2];
        this.partMask1Start = metadata[3];
        this.partMask1Count = metadata[4];
        this.partMask2Start = metadata[5];
        this.partMask2Count = metadata[6];
        this.partMask3Start = metadata[7];
        this.partMask3Count = metadata[8];
    }

    public static NativeGpuMeshCache upload(GeoModel mesh) {
        RenderSystem.assertOnRenderThread();
        mesh.buildNativeGpuMesh();
        if (mesh.nativeGpuMeshHandle == 0) {
            return null;
        }

        ByteBuffer vertexData = GeoModel.nGetGpuMeshVertexBuffer(mesh.nativeGpuMeshHandle);
        ByteBuffer indexData = GeoModel.nGetGpuMeshIndexBuffer(mesh.nativeGpuMeshHandle);
        if (vertexData == null || indexData == null) {
            return null;
        }

        GpuBuffer vertexBuffer = null;
        GpuBuffer indexBuffer = null;
        try {
            GpuDevice device = RenderSystem.getDevice();
            vertexBuffer = device.createBuffer(() -> "YSM native mesh vertices", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, vertexData);
            indexBuffer = device.createBuffer(() -> "YSM native mesh indices", GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_DST, indexData);
            NativeGpuMeshCache cache = new NativeGpuMeshCache(vertexBuffer, indexBuffer, mesh.nativeGpuMeshMetadata);
            GeoModel.nReleaseGpuMeshScratch(mesh.nativeGpuMeshHandle);
            if (!firstUploadLogged) {
                firstUploadLogged = true;
                YesSteveModel.LOGGER.info("[YSM Render] Uploaded native GPU mesh: vertices={}, indices={}, bones={}",
                        cache.vertexCount, cache.indexCount, cache.boneCount);
            }
            return cache;
        } catch (Throwable throwable) {
            closeQuietly(vertexBuffer);
            closeQuietly(indexBuffer);
            if (!uploadFailureLogged) {
                uploadFailureLogged = true;
                YesSteveModel.LOGGER.warn("[YSM Render] Native GPU buffer upload failed; continuing with CPU/native vertex renderer", throwable);
            }
            return null;
        }
    }

    public boolean uploadBoneData(ByteBuffer boneData, int usedBoneCount) {
        if (closed || boneData == null || usedBoneCount <= 0) {
            return false;
        }
        if (usedBoneCount > NativeGpuRenderer.MAX_GPU_BONES) {
            return false;
        }
        RenderSystem.assertOnRenderThread();
        int requiredBytes = usedBoneCount * BONE_DATA_STRIDE_BYTES;
        int bufferBytes = NativeGpuRenderer.MAX_GPU_BONES * BONE_DATA_STRIDE_BYTES;
        if (requiredBytes <= 0 || boneData.capacity() < requiredBytes) {
            return false;
        }

        try {
            if (boneBuffer == null || boneBuffer.isClosed() || boneBufferSize < bufferBytes) {
                closeQuietly(boneBuffer);
                boneBuffer = RenderSystem.getDevice().createBuffer(
                        () -> "YSM native bone data",
                        GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                        bufferBytes
                );
                boneBufferSize = bufferBytes;
            }

            ByteBuffer uploadData = boneData.duplicate();
            uploadData.position(0);
            uploadData.limit(requiredBytes);
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(boneBuffer.slice(0, requiredBytes), uploadData);
            if (!firstBoneUploadLogged) {
                firstBoneUploadLogged = true;
                YesSteveModel.LOGGER.info("[YSM Render] Uploaded native GPU bone buffer: bones={}, bytes={}", usedBoneCount, requiredBytes);
            }
            return true;
        } catch (Throwable throwable) {
            if (!boneUploadFailureLogged) {
                boneUploadFailureLogged = true;
                YesSteveModel.LOGGER.warn("[YSM Render] Native GPU bone buffer upload failed; continuing without GPU draw path", throwable);
            }
            closeQuietly(boneBuffer);
            boneBuffer = null;
            boneBufferSize = 0;
            return false;
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (RenderSystem.isOnRenderThread()) {
            closeNow();
        } else {
            RenderSystem.queueFencedTask(this::closeNow);
        }
    }

    private void closeNow() {
        closeQuietly(vertexBuffer);
        closeQuietly(indexBuffer);
        closeQuietly(boneBuffer);
        boneBuffer = null;
        boneBufferSize = 0;
    }

    private static void closeQuietly(GpuBuffer buffer) {
        if (buffer == null || buffer.isClosed()) {
            return;
        }
        try {
            buffer.close();
        } catch (Throwable ignored) {
        }
    }
}
