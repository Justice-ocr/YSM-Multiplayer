package com.elfmcys.yesstevemodel.geckolib3.geo;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GL45;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class NativeGpuGlMeshCache implements AutoCloseable {
    private static boolean uploadFailureLogged;

    final int vao;
    final int vertexBuffer;
    final int indexBuffer;
    final int boneSsbo;
    final int vertexCount;
    final int indexCount;
    final int boneCount;
    final int partMask1Start;
    final int partMask1Count;
    final int partMask2Start;
    final int partMask2Count;
    final int partMask3Start;
    final int partMask3Count;
    final ByteBuffer boneUploadBuffer;

    private boolean closed;

    private NativeGpuGlMeshCache(int vao, int vertexBuffer, int indexBuffer, int boneSsbo, int[] metadata) {
        this.vao = vao;
        this.vertexBuffer = vertexBuffer;
        this.indexBuffer = indexBuffer;
        this.boneSsbo = boneSsbo;
        this.vertexCount = metadata[0];
        this.indexCount = metadata[1];
        this.boneCount = metadata[2];
        this.partMask1Start = metadata[3];
        this.partMask1Count = metadata[4];
        this.partMask2Start = metadata[5];
        this.partMask2Count = metadata[6];
        this.partMask3Start = metadata[7];
        this.partMask3Count = metadata[8];
        this.boneUploadBuffer = ByteBuffer.allocateDirect(this.boneCount * 144).order(ByteOrder.nativeOrder());
    }

    static NativeGpuGlMeshCache getOrCreate(GeoModel model) {
        if (model.nativeGpuGlMeshCache != null) {
            return model.nativeGpuGlMeshCache;
        }
        if (model.nativeGpuGlUploadAttempted) {
            return null;
        }
        model.nativeGpuGlUploadAttempted = true;
        model.nativeGpuGlMeshCache = upload(model);
        return model.nativeGpuGlMeshCache;
    }

    private static NativeGpuGlMeshCache upload(GeoModel model) {
        RenderSystem.assertOnRenderThread();
        model.buildNativeGpuMesh();
        if (model.nativeGpuMeshHandle == 0) {
            return null;
        }
        ByteBuffer vertexData = GeoModel.nGetGpuMeshVertexBuffer(model.nativeGpuMeshHandle);
        ByteBuffer indexData = GeoModel.nGetGpuMeshIndexBuffer(model.nativeGpuMeshHandle);
        if (vertexData == null || indexData == null) {
            return null;
        }
        vertexData.order(ByteOrder.nativeOrder());
        indexData.order(ByteOrder.nativeOrder());

        int vao = 0;
        int vbo = 0;
        int ibo = 0;
        int ssbo = 0;
        try {
            vao = GlStateManager._glGenVertexArrays();
            vbo = GlStateManager._glGenBuffers();
            ibo = GlStateManager._glGenBuffers();
            ssbo = GlStateManager._glGenBuffers();

            GlStateManager._glBindVertexArray(vao);
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GlStateManager._glBufferData(GL15.GL_ARRAY_BUFFER, vertexData, GL15.GL_STATIC_DRAW);
            GlStateManager._glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ibo);
            GlStateManager._glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indexData, GL15.GL_STATIC_DRAW);

            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 32, 0L);
            GL20.glEnableVertexAttribArray(1);
            GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 32, 12L);
            GL20.glEnableVertexAttribArray(2);
            GL20.glVertexAttribPointer(2, 4, GL33.GL_INT_2_10_10_10_REV, true, 32, 20L);
            GL20.glEnableVertexAttribArray(3);
            GL30.glVertexAttribIPointer(3, 1, GL11.GL_UNSIGNED_SHORT, 32, 24L);
            GL20.glEnableVertexAttribArray(4);
            GL20.glVertexAttribPointer(4, 1, GL11.GL_UNSIGNED_BYTE, false, 32, 27L);

            GlStateManager._glBindVertexArray(0);
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GlStateManager._glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);

            GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssbo);
            GL45.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, (long) model.nativeGpuMeshMetadata[2] * 144L, GL15.GL_DYNAMIC_DRAW);
            GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);

            GeoModel.nReleaseGpuMeshScratch(model.nativeGpuMeshHandle);
            return new NativeGpuGlMeshCache(vao, vbo, ibo, ssbo, model.nativeGpuMeshMetadata);
        } catch (Throwable throwable) {
            deleteGl(vao, vbo, ibo, ssbo);
            if (!uploadFailureLogged) {
                uploadFailureLogged = true;
                YesSteveModel.LOGGER.warn("[YSM GPU] GL mesh upload failed; falling back to native/Java renderer", throwable);
            }
            return null;
        }
    }

    int indexOffsetBytes(int renderPartMask) {
        return switch (renderPartMask) {
            case 1 -> partMask1Start * Integer.BYTES;
            case 2 -> partMask2Start * Integer.BYTES;
            case 3 -> partMask3Start * Integer.BYTES;
            default -> 0;
        };
    }

    int indexDrawCount(int renderPartMask) {
        return switch (renderPartMask) {
            case 1 -> partMask1Count + partMask3Count;
            case 2 -> partMask2Count + partMask3Count;
            case 3 -> partMask3Count;
            default -> indexCount;
        };
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
        deleteGl(vao, vertexBuffer, indexBuffer, boneSsbo);
    }

    private static void deleteGl(int vao, int vbo, int ibo, int ssbo) {
        try {
            if (vbo != 0) GlStateManager._glDeleteBuffers(vbo);
            if (ibo != 0) GlStateManager._glDeleteBuffers(ibo);
            if (ssbo != 0) GlStateManager._glDeleteBuffers(ssbo);
            if (vao != 0) GL45.glDeleteVertexArrays(vao);
        } catch (Throwable ignored) {
        }
    }
}
