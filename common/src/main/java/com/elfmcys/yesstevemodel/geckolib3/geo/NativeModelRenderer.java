

package com.elfmcys.yesstevemodel.geckolib3.geo;

import com.elfmcys.yesstevemodel.NativeLibLoader;
import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.client.renderer.ModelPreviewRenderer;
import com.elfmcys.yesstevemodel.config.GeneralConfig;
import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.*;
import org.lwjgl.system.MemoryUtil;
import rip.ysm.compat.oculus.OculusCompat;
import rip.ysm.compat.optifine.OptiFineDetector;

import java.lang.Math;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.Locale;

public class NativeModelRenderer {
    private static final Matrix4f projectionModelViewMatrix = new Matrix4f();
    private static final ThreadLocal<RenderScratch> RENDER_SCRATCH = ThreadLocal.withInitial(RenderScratch::new);
    private static final long PROFILE_LOG_INTERVAL_NANOS = 5_000_000_000L;
    private static long profileLastLogTime = System.nanoTime();
    private static long profileRenderCalls;
    private static long profileNativeCalls;
    private static long profileVertices;
    private static long profileNanos;

    public static void renderMesh(VertexConsumer buffer, PoseStack.Pose pose, GeoModel model, float[] boneParams, float[] stateBuffer, int textureIndex, int renderPartMask, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        OculusCompat.updatePBRState();
        boolean isCompatMode = OptiFineDetector.isOptifinePresent() || GeneralConfig.USE_COMPATIBILITY_RENDERER.get();
        net.minecraft.client.Minecraft.getInstance().gameRenderer.getProjectionMatrix(net.minecraft.client.Minecraft.getInstance().options.fov().get()).mul(RenderSystem.getModelViewMatrix(), projectionModelViewMatrix);
        boolean isPreview = ModelPreviewRenderer.isPreview() || ModelPreviewRenderer.isExtraPlayer();
        boolean profiling = Boolean.TRUE.equals(GeneralConfig.RENDER_PROFILING.get());
        long startNanos = profiling ? System.nanoTime() : 0L;
        boolean useNativeRenderer = Boolean.TRUE.equals(GeneralConfig.USE_NATIVE_RENDERER.get())
                && NativeLibLoader.isLoaded();
        if (useNativeRenderer && model.nativeModelHandle == 0) {
            model.buildNativeCache();
        }
        boolean nativePath = useNativeRenderer && model.nativeModelHandle != 0;
        int vertexCount;
        if (nativePath) {
            vertexCount = nativeRenderModel(buffer, pose, projectionModelViewMatrix, isCompatMode, model, boneParams, stateBuffer, textureIndex, renderPartMask, packedLight, packedOverlay, red, green, blue, alpha, isPreview);
        } else {
            vertexCount = renderModel(buffer, pose, projectionModelViewMatrix, isCompatMode, model, boneParams, stateBuffer, textureIndex, renderPartMask, packedLight, packedOverlay, red, green, blue, alpha, isPreview);
        }
        if (profiling) {
            recordProfile(System.nanoTime() - startNanos, vertexCount, nativePath);
        }
    }

    public static int renderModel(
            VertexConsumer vertexConsumer,
            PoseStack.Pose pose,
            Matrix4f projectionModelViewMatrix,
            boolean isCompatMode,
            GeoModel mesh,
            float[] boneParams,
            float[] stateBuffer,
            int textureIndex, int renderPartMask,
            int packedLight, int packedOverlay,
            float r, float g, float b, float a,
            boolean isPreview) {

        if (mesh.bakedBones == null || mesh.bakedBones.isEmpty()) return 0;

        // TODO: 修復GC壓力
        Matrix4f rootPoseMat = pose.pose();
        Matrix3f rootNormalMC = pose.normal();
        Matrix4f projMat = net.minecraft.client.Minecraft.getInstance().gameRenderer.getProjectionMatrix(net.minecraft.client.Minecraft.getInstance().options.fov().get());

        RenderScratch scratch = RENDER_SCRATCH.get();
        scratch.prepare(mesh.bakedBones.size());

        for (int i = 0; i < mesh.bakedBones.size(); i++) {
            calculateBoneMatrix(i, mesh.bakedBones, boneParams, scratch.boneLocalTransforms, scratch.boneVisible, scratch.boneComputed, scratch.identityMat);
        }

        int vertexCount = 0;
        for (int i = 0; i < mesh.bakedBones.size(); i++) {
            if (!scratch.boneVisible[i]) {
                continue;
            }

            GeoModel.BakedBone bone = mesh.bakedBones.get(i);
            if (renderPartMask != 0 && bone.partMask != renderPartMask && bone.partMask != 3) {
                continue;
            }

            Matrix4f localBoneMat = scratch.boneLocalTransforms[i];
            scratch.globalBoneMat.set(rootPoseMat).mul(localBoneMat);
            scratch.projBoneMat.set(projMat).mul(scratch.globalBoneMat);

            // 法線全域矩陣
            localBoneMat.normal(scratch.localNormalMat);
            scratch.globalNormalMat.set(rootNormalMC).mul(scratch.localNormalMat);

            int currentPackedLight = bone.glow ? LightTexture.pack(15, 15) : packedLight;

            for (GeoModel.BakedCube cube : bone.cubes) {
                for (GeoModel.BakedQuad quad : cube.quads) {
                    // Skip CPU back-face culling in preview/GUI contexts: the cached
                    // projMat above is the world perspective matrix, but PIP rendering
                    // (inventory, paper-doll, preview screens) uses an orthographic
                    // projection set on RenderSystem at draw time. Culling against the
                    // wrong matrix drops visible quads.
                    if (cube.cullable && !isPreview) {
                        scratch.p1.set(quad.positions[0].x(), quad.positions[0].y(), quad.positions[0].z(), 1.0f).mul(scratch.projBoneMat);
                        scratch.p2.set(quad.positions[1].x(), quad.positions[1].y(), quad.positions[1].z(), 1.0f).mul(scratch.projBoneMat);
                        scratch.p3.set(quad.positions[2].x(), quad.positions[2].y(), quad.positions[2].z(), 1.0f).mul(scratch.projBoneMat);
                        float det = scratch.p1.x() * (scratch.p2.y() * scratch.p3.w() - scratch.p3.y() * scratch.p2.w()) - scratch.p2.x() * (scratch.p1.y() * scratch.p3.w() - scratch.p3.y() * scratch.p1.w()) + scratch.p3.x() * (scratch.p1.y() * scratch.p2.w() - scratch.p2.y() * scratch.p1.w());
                        if (det <= 0.0f) {
                            continue;
                        }
                    }
                    scratch.tempNorm.set(quad.normal).mul(scratch.globalNormalMat).normalize();
                    for (int v = 0; v < 4; v++) {
                        scratch.tempPos.set(quad.positions[v].x(), quad.positions[v].y(), quad.positions[v].z(), 1.0f).mul(scratch.globalBoneMat);
                        vertexConsumer.addVertex(scratch.tempPos.x(), scratch.tempPos.y(), scratch.tempPos.z())
                                .setColor(r, g, b, a)
                                .setUv(quad.uvs[v].x(), quad.uvs[v].y())
                                .setOverlay(packedOverlay)
                                .setLight(currentPackedLight)
                                .setNormal(scratch.tempNorm.x(), scratch.tempNorm.y(), scratch.tempNorm.z());
                        vertexCount++;
                    }
                }
            }
        }
        return vertexCount;
    }

    private static Matrix4f calculateBoneMatrix(int idx, java.util.List<GeoModel.BakedBone> bones, float[] boneParams, Matrix4f[] cache, boolean[] visibleCache, boolean[] computedCache, Matrix4f rootPose) {
        if (computedCache[idx]) return cache[idx];

        GeoModel.BakedBone bone = bones.get(idx);
        Matrix4f parentMatrix = rootPose;
        boolean isVisible = true;

        if (bone.parentIdx != -1) {
            parentMatrix = calculateBoneMatrix(bone.parentIdx, bones, boneParams, cache, visibleCache, computedCache, rootPose);
            // 如果父骨骼不可見，子骨骼必然跟著不可見
            if (!visibleCache[bone.parentIdx]) {
                isVisible = false;
            }
        }

        Matrix4f localMat = cache[idx];
        if (localMat == null) {
            localMat = new Matrix4f();
            cache[idx] = localMat;
        }
        localMat.set(parentMatrix);

        int pOffset = idx * 12;
        float animRx = boneParams[pOffset];
        float animRy = boneParams[pOffset + 1];
        float animRz = boneParams[pOffset + 2];
        float animTx = boneParams[pOffset + 3];
        float animTy = boneParams[pOffset + 4];
        float animTz = boneParams[pOffset + 5];
        float animSx = boneParams[pOffset + 6];
        float animSy = boneParams[pOffset + 7];
        float animSz = boneParams[pOffset + 8];

        float unk1 = boneParams[pOffset + 9];
        float unk2 = boneParams[pOffset + 10];
        float unk3 = boneParams[pOffset + 11];

        if (unk1 != 0.0F && unk2 != 0.0F && unk3 != 0.0F) {
            //"".hashCode();
        }

        if (animSx == 0.0f && animSy == 0.0f && animSz == 0.0f) {
            isVisible = false;
        }/* else if (unk1 == 1 || unk2 == 1) isVisible = false;*/

        localMat.translate(
                (bone.pivotX - animTx) * 0.0625f,
                (bone.pivotY + animTy) * 0.0625f,
                (bone.pivotZ + animTz) * 0.0625f
        );
        localMat.rotateZ(animRz);
        localMat.rotateY(animRy);
        localMat.rotateX(animRx);

        if (bone.name.equals("gun")) {
            //"".hashCode();
        }

        if (animSx != 1.0f || animSy != 1.0f || animSz != 1.0f) {
            localMat.scale(animSx, animSy, animSz);
        }

        localMat.translate(-bone.pivotX / 16f, -bone.pivotY / 16f, -bone.pivotZ / 16f);

        visibleCache[idx] = isVisible; // 保存當前骨骼的可見性
        computedCache[idx] = true;
        return localMat;
    }

    private static final float[] matrixTransferArray = new float[48];
    @SuppressWarnings("unused") // TODO: native中直接往VertexConsumer中的buffer写入顶点
    public static void submitVertices(VertexConsumer vertexConsumer, int vertexCount, float[] fArr, int[] iArr) {
        int floatIndex = 0;
        int intIndex = 0;

        for (int i = 0; i < vertexCount; i++) {
            vertexConsumer.addVertex(fArr[floatIndex + 0], fArr[floatIndex + 1], fArr[floatIndex + 2])
                    .setColor(fArr[floatIndex + 3], fArr[floatIndex + 4], fArr[floatIndex + 5], fArr[floatIndex + 6])
                    .setUv(fArr[floatIndex + 7], fArr[floatIndex + 8])
                    .setOverlay(iArr[intIndex + 0])
                    .setLight(iArr[intIndex + 1])
                    .setNormal(fArr[floatIndex + 9], fArr[floatIndex + 10], fArr[floatIndex + 11]);
            floatIndex += 12;
            intIndex += 2;
        }
    }

    @SuppressWarnings("unused") // Called from ysm-core.dll.
    public static void submitVertices(Object vertexConsumer, int vertexCount, ByteBuffer vertexData, ByteBuffer intData) {
        if (!(vertexConsumer instanceof VertexConsumer consumer) || vertexData == null || intData == null || vertexCount <= 0) {
            return;
        }

        FloatBuffer floats = vertexData.duplicate().order(ByteOrder.nativeOrder()).asFloatBuffer();
        IntBuffer ints = intData.duplicate().order(ByteOrder.nativeOrder()).asIntBuffer();
        int count = Math.min(vertexCount, Math.min(floats.remaining() / 12, ints.remaining() / 2));

        for (int i = 0; i < count; i++) {
            int floatIndex = i * 12;
            int intIndex = i * 2;
            consumer.addVertex(floats.get(floatIndex), floats.get(floatIndex + 1), floats.get(floatIndex + 2))
                    .setColor(floats.get(floatIndex + 3), floats.get(floatIndex + 4), floats.get(floatIndex + 5), floats.get(floatIndex + 6))
                    .setUv(floats.get(floatIndex + 7), floats.get(floatIndex + 8))
                    .setOverlay(ints.get(intIndex))
                    .setLight(ints.get(intIndex + 1))
                    .setNormal(floats.get(floatIndex + 9), floats.get(floatIndex + 10), floats.get(floatIndex + 11));
        }
    }


    public static int nativeRenderModel( // TODO:
            VertexConsumer vertexConsumer, PoseStack.Pose pose, Matrix4f projectionModelViewMatrix,
            boolean isCompatMode, GeoModel mesh, float[] boneVertex, float[] stateBuffer,
            int textureIndex, int renderPartMask, int packedLight, int packedOverlay,
            float r, float g, float b, float a, boolean isPreview) {

        if (mesh.nativeModelHandle == 0) return 0;

        Matrix4f projMat = net.minecraft.client.Minecraft.getInstance().gameRenderer.getProjectionMatrix(net.minecraft.client.Minecraft.getInstance().options.fov().get());

        pose.pose().get(matrixTransferArray, 0);
        pose.normal().get(matrixTransferArray, 16);
        projMat.get(matrixTransferArray, 32);

        GeoModel.nComputeModelVertices(
                mesh.nativeModelHandle,
                vertexConsumer,
                matrixTransferArray,
                boneVertex,
                renderPartMask,
                packedLight, packedOverlay,
                r, g, b, a
        );
        return 0;
    }

    private static void recordProfile(long nanos, int vertexCount, boolean nativePath) {
        profileRenderCalls++;
        if (nativePath) {
            profileNativeCalls++;
        }
        profileVertices += Math.max(0, vertexCount);
        profileNanos += nanos;

        long now = System.nanoTime();
        if (now - profileLastLogTime < PROFILE_LOG_INTERVAL_NANOS) {
            return;
        }

        long calls = profileRenderCalls;
        double avgMs = calls == 0L ? 0.0d : (profileNanos / 1_000_000.0d) / calls;
        YesSteveModel.LOGGER.info("[YSM Render] calls={}, native={}, vertices={}, avg={} ms",
                calls,
                profileNativeCalls,
                profileVertices,
                String.format(Locale.ROOT, "%.3f", avgMs));
        profileRenderCalls = 0L;
        profileNativeCalls = 0L;
        profileVertices = 0L;
        profileNanos = 0L;
        profileLastLogTime = now;
    }

    private static class RenderScratch {
        private final Matrix4f identityMat = new Matrix4f();
        private final Matrix4f globalBoneMat = new Matrix4f();
        private final Matrix4f projBoneMat = new Matrix4f();
        private final Matrix3f localNormalMat = new Matrix3f();
        private final Matrix3f globalNormalMat = new Matrix3f();
        private final Vector4f p1 = new Vector4f();
        private final Vector4f p2 = new Vector4f();
        private final Vector4f p3 = new Vector4f();
        private final Vector4f tempPos = new Vector4f();
        private final Vector3f tempNorm = new Vector3f();
        private Matrix4f[] boneLocalTransforms = new Matrix4f[0];
        private boolean[] boneVisible = new boolean[0];
        private boolean[] boneComputed = new boolean[0];

        private void prepare(int boneCount) {
            this.identityMat.identity();
            if (this.boneLocalTransforms.length < boneCount) {
                this.boneLocalTransforms = new Matrix4f[boneCount];
                this.boneVisible = new boolean[boneCount];
                this.boneComputed = new boolean[boneCount];
                return;
            }
            Arrays.fill(this.boneVisible, 0, boneCount, false);
            Arrays.fill(this.boneComputed, 0, boneCount, false);
        }
    }
}
