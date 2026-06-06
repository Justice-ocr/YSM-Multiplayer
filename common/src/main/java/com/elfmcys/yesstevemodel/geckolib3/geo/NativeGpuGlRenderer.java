package com.elfmcys.yesstevemodel.geckolib3.geo;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.config.GeneralConfig;
import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL43;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Locale;

public final class NativeGpuGlRenderer {
    private static final float[] ROOT_POSE = new float[16];
    private static final float[] ROOT_NORMAL = new float[9];
    private static final float[] PROJECTION = new float[16];
    private static final Matrix4f PROJECTION_MODEL_VIEW = new Matrix4f();
    private static final Matrix4f PIVOT_ABS_SCRATCH = new Matrix4f();
    private static int[] pivotAbsPathScratch = new int[64];
    private static boolean unavailableLogged;
    private static boolean drawDisabledLogged;
    private static boolean failureLogged;
    private static long lastLogTime = System.nanoTime();
    private static long submittedDraws;
    private static long submittedIndices;

    private NativeGpuGlRenderer() {
    }

    public static boolean tryRender(
            GeoModel model,
            PoseStack.Pose pose,
            float[] boneParams,
            float[] stateBuffer,
            int renderPartMask,
            int packedLight,
            int packedOverlay,
            float red,
            float green,
            float blue,
            float alpha,
            ResourceLocation textureLocation,
            boolean isPreview
    ) {
        if (!isEnabled() || isPreview || textureLocation == null || renderPartMask != 0) {
            return false;
        }
        if (!isDrawEnabled()) {
            if (!drawDisabledLogged) {
                drawDisabledLogged = true;
                YesSteveModel.LOGGER.info("[YSM GPU] GL draw path is paused; keeping native/Java renderer active while GPU buffers are prepared");
            }
            return false;
        }
        if (!RenderSystem.isOnRenderThread()) {
            return false;
        }
        if (!NativeGpuGlCapability.isAvailable() || !NativeGpuGlShader.ensureCompiled()) {
            if (!unavailableLogged) {
                unavailableLogged = true;
                YesSteveModel.LOGGER.info("[YSM GPU] GL renderer unavailable: {}", NativeGpuGlCapability.reason());
            }
            return false;
        }
        NativeGpuGlMeshCache mesh = NativeGpuGlMeshCache.getOrCreate(model);
        if (mesh == null || mesh.boneCount <= 0 || mesh.boneCount > NativeGpuRenderer.MAX_GPU_BONES) {
            return false;
        }
        int drawCount = mesh.indexDrawCount(renderPartMask);
        if (drawCount <= 0) {
            return false;
        }

        try {
            Minecraft minecraft = Minecraft.getInstance();
            int modelTexture = textureId(minecraft.getTextureManager().getTexture(textureLocation).getTextureView());
            if (modelTexture == 0) {
                return false;
            }

            pose.pose().get(ROOT_POSE);
            Matrix3f normal = pose.normal();
            ROOT_NORMAL[0] = normal.m00();
            ROOT_NORMAL[1] = normal.m01();
            ROOT_NORMAL[2] = normal.m02();
            ROOT_NORMAL[3] = normal.m10();
            ROOT_NORMAL[4] = normal.m11();
            ROOT_NORMAL[5] = normal.m12();
            ROOT_NORMAL[6] = normal.m20();
            ROOT_NORMAL[7] = normal.m21();
            ROOT_NORMAL[8] = normal.m22();

            ByteBuffer boneBuffer = mesh.boneUploadBuffer;
            boneBuffer.clear();
            updatePivotAbsStateBuffer(model, boneParams, stateBuffer);
            GeoModel.nComputeBoneMatrices(model.nativeGpuMeshHandle, ROOT_POSE, ROOT_NORMAL, boneParams, packedLight, boneBuffer);
            boneBuffer.position(0);
            boneBuffer.limit(mesh.boneCount * 144);

            Matrix4f projection = minecraft.gameRenderer.getProjectionMatrix(minecraft.options.fov().get());
            projection.mul(RenderSystem.getModelViewMatrix(), PROJECTION_MODEL_VIEW);
            PROJECTION_MODEL_VIEW.get(PROJECTION);

            GlStateManager._disableCull();
            GlStateManager._enableDepthTest();
            GlStateManager._depthMask(true);
            GlStateManager._disableBlend();

            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + 2);
            minecraft.gameRenderer.lightTexture().turnOnLightLayer();
            bindTextureView(minecraft.gameRenderer.lightTexture().getTextureView());

            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + 1);
            minecraft.gameRenderer.overlayTexture().setupOverlayColor();
            bindTextureView(RenderSystem.getShaderTexture(1));

            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            GlStateManager._bindTexture(modelTexture);

            GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, mesh.boneSsbo);
            GlStateManager._glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0, boneBuffer);
            GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, NativeGpuGlShader.BONE_SSBO_BINDING, mesh.boneSsbo);

            GlStateManager._glUseProgram(NativeGpuGlShader.program());
            setUniforms(packedOverlay, red, green, blue, alpha);
            GlStateManager._glBindVertexArray(mesh.vao);

            int offsetBytes = mesh.indexOffsetBytes(renderPartMask);
            if (NativeGpuGlShader.locAlphaMode() >= 0) {
                GL20.glUniform1i(NativeGpuGlShader.locAlphaMode(), 1);
            }
            GL11.glDrawElements(GL11.GL_TRIANGLES, drawCount, GL11.GL_UNSIGNED_INT, offsetBytes);

            GlStateManager._enableBlend();
            GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            if (NativeGpuGlShader.locAlphaMode() >= 0) {
                GL20.glUniform1i(NativeGpuGlShader.locAlphaMode(), 2);
            }
            GL11.glDrawElements(GL11.GL_TRIANGLES, drawCount, GL11.GL_UNSIGNED_INT, offsetBytes);
            GlStateManager._disableBlend();

            submittedDraws += 2L;
            submittedIndices += drawCount * 2L;
            logProgress();
            return true;
        } catch (Throwable throwable) {
            if (!failureLogged) {
                failureLogged = true;
                YesSteveModel.LOGGER.warn("[YSM GPU] GL draw failed; falling back to native/Java renderer", throwable);
            }
            return false;
        } finally {
            try {
                GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, NativeGpuGlShader.BONE_SSBO_BINDING, 0);
                GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
                GlStateManager._glUseProgram(0);
                GlStateManager._glBindVertexArray(0);
                Minecraft.getInstance().gameRenderer.overlayTexture().teardownOverlayColor();
                Minecraft.getInstance().gameRenderer.lightTexture().turnOffLightLayer();
                GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            } catch (Throwable ignored) {
            }
        }
    }

    private static boolean isEnabled() {
        return Boolean.TRUE.equals(GeneralConfig.USE_NATIVE_RENDERER.get())
                && Boolean.TRUE.equals(GeneralConfig.USE_EXPERIMENTAL_GPU_RENDERER.get());
    }

    private static boolean isDrawEnabled() {
        return Boolean.getBoolean("ysm.experimentalGpuDraw");
    }

    private static void setUniforms(int packedOverlay, float red, float green, float blue, float alpha) {
        if (NativeGpuGlShader.locProj() >= 0) {
            GL20.glUniformMatrix4fv(NativeGpuGlShader.locProj(), false, PROJECTION);
        }
        if (NativeGpuGlShader.locColor() >= 0) {
            GL20.glUniform4f(NativeGpuGlShader.locColor(), red, green, blue, alpha);
        }
        if (NativeGpuGlShader.locOverlay() >= 0) {
            GL20.glUniform1i(NativeGpuGlShader.locOverlay(), packedOverlay);
        }
        if (NativeGpuGlShader.locFogStart() >= 0) {
            GL20.glUniform1f(NativeGpuGlShader.locFogStart(), 1000000.0f);
        }
        if (NativeGpuGlShader.locFogEnd() >= 0) {
            GL20.glUniform1f(NativeGpuGlShader.locFogEnd(), 1000001.0f);
        }
        if (NativeGpuGlShader.locFogColor() >= 0) {
            GL20.glUniform4f(NativeGpuGlShader.locFogColor(), 0.0f, 0.0f, 0.0f, 0.0f);
        }
        if (NativeGpuGlShader.locFogShape() >= 0) {
            GL20.glUniform1i(NativeGpuGlShader.locFogShape(), 0);
        }
        if (NativeGpuGlShader.locLight0() >= 0) {
            GL20.glUniform3f(NativeGpuGlShader.locLight0(), 0.2f, 1.0f, -0.7f);
        }
        if (NativeGpuGlShader.locLight1() >= 0) {
            GL20.glUniform3f(NativeGpuGlShader.locLight1(), -0.2f, 1.0f, 0.7f);
        }
    }

    private static void bindTextureView(GpuTextureView textureView) {
        int id = textureId(textureView);
        if (id != 0) {
            GlStateManager._bindTexture(id);
        }
    }

    private static int textureId(GpuTextureView textureView) {
        if (textureView != null && textureView.texture() instanceof GlTexture glTexture && !glTexture.isClosed()) {
            return glTexture.glId();
        }
        return 0;
    }

    private static void updatePivotAbsStateBuffer(GeoModel model, float[] boneParams, float[] stateBuffer) {
        if (stateBuffer == null || boneParams == null || model.bakedBones == null || model.bakedBones.isEmpty()) {
            return;
        }
        int boneCount = model.bakedBones.size();
        for (int boneIndex = 0; boneIndex < boneCount; boneIndex++) {
            int paramOffset = boneIndex * 12;
            if (paramOffset + 11 >= boneParams.length) {
                break;
            }
            if (boneParams[paramOffset + 11] != 1.0f) {
                continue;
            }
            int stateOffset = boneIndex * 4;
            if (stateOffset + 2 >= stateBuffer.length) {
                continue;
            }
            computePivotAbs(boneIndex, model.bakedBones, boneParams, stateBuffer, stateOffset);
        }
    }

    private static void computePivotAbs(int targetIndex, List<GeoModel.BakedBone> bones, float[] boneParams, float[] stateBuffer, int stateOffset) {
        int depth = 0;
        int index = targetIndex;
        while (index != -1) {
            if (depth >= pivotAbsPathScratch.length) {
                int[] expanded = new int[pivotAbsPathScratch.length * 2];
                System.arraycopy(pivotAbsPathScratch, 0, expanded, 0, pivotAbsPathScratch.length);
                pivotAbsPathScratch = expanded;
            }
            pivotAbsPathScratch[depth++] = index;
            index = bones.get(index).parentIdx;
        }

        Matrix4f localMatrix = PIVOT_ABS_SCRATCH.identity();
        boolean visible = true;
        for (int pathIndex = depth - 1; pathIndex >= 0; pathIndex--) {
            int boneIndex = pivotAbsPathScratch[pathIndex];
            GeoModel.BakedBone bone = bones.get(boneIndex);
            int paramOffset = boneIndex * 12;
            if (paramOffset + 11 >= boneParams.length) {
                return;
            }

            float animRx = boneParams[paramOffset];
            float animRy = boneParams[paramOffset + 1];
            float animRz = boneParams[paramOffset + 2];
            float animTx = boneParams[paramOffset + 3];
            float animTy = boneParams[paramOffset + 4];
            float animTz = boneParams[paramOffset + 5];
            float animSx = boneParams[paramOffset + 6];
            float animSy = boneParams[paramOffset + 7];
            float animSz = boneParams[paramOffset + 8];
            if (animSx == 0.0f && animSy == 0.0f && animSz == 0.0f) {
                visible = false;
            }
            if (!visible) {
                return;
            }

            localMatrix.translate((bone.pivotX - animTx) * 0.0625f, (bone.pivotY + animTy) * 0.0625f, (bone.pivotZ + animTz) * 0.0625f);
            localMatrix.rotateZ(animRz);
            localMatrix.rotateY(animRy);
            localMatrix.rotateX(animRx);
            if (animSx != 1.0f || animSy != 1.0f || animSz != 1.0f) {
                localMatrix.scale(animSx, animSy, animSz);
            }
            if (boneIndex == targetIndex) {
                stateBuffer[stateOffset] = -localMatrix.m30() * 16.0f;
                stateBuffer[stateOffset + 1] = localMatrix.m31() * 16.0f;
                stateBuffer[stateOffset + 2] = localMatrix.m32() * 16.0f;
                return;
            }
            localMatrix.translate(-bone.pivotX / 16.0f, -bone.pivotY / 16.0f, -bone.pivotZ / 16.0f);
        }
    }

    private static void logProgress() {
        if (!Boolean.TRUE.equals(GeneralConfig.RENDER_PROFILING.get())) {
            return;
        }
        long now = System.nanoTime();
        if (now - lastLogTime < 5_000_000_000L) {
            return;
        }
        YesSteveModel.LOGGER.info("[YSM GPU] GL draws={}, indices={}, capability={}",
                submittedDraws,
                submittedIndices,
                String.format(Locale.ROOT, "%s", NativeGpuGlCapability.reason()));
        submittedDraws = 0L;
        submittedIndices = 0L;
        lastLogTime = now;
    }
}
