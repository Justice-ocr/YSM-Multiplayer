

package com.elfmcys.yesstevemodel.geckolib3.geo;

import com.elfmcys.yesstevemodel.NativeLibLoader;
import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.client.renderer.ModelPreviewRenderer;
import com.elfmcys.yesstevemodel.config.GeneralConfig;
import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.*;
import rip.ysm.compat.oculus.OculusCompat;

import java.lang.Math;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Locale;

public class NativeModelRenderer {
    private static final ThreadLocal<RenderScratch> RENDER_SCRATCH = ThreadLocal.withInitial(RenderScratch::new);
    private static final int FULL_BRIGHT_LIGHT = LightTexture.pack(15, 15);
    private static final long PROFILE_LOG_INTERVAL_NANOS = 5_000_000_000L;
    private static long profileLastLogTime = System.nanoTime();
    private static long profileRenderCalls;
    private static long profileNativeCalls;
    private static long profileVertices;
    private static long profileNanos;
    private static boolean nativeComputeFallbackLogged;

    public static void renderMesh(VertexConsumer buffer, PoseStack.Pose pose, GeoModel model, float[] boneParams, float[] stateBuffer, int textureIndex, int renderPartMask, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        OculusCompat.updatePBRState();
        boolean isPreview = ModelPreviewRenderer.isPreview() || ModelPreviewRenderer.isExtraPlayer();
        boolean profiling = Boolean.TRUE.equals(GeneralConfig.RENDER_PROFILING.get());
        long startNanos = profiling ? System.nanoTime() : 0L;
        boolean requestedNativeRenderer = Boolean.TRUE.equals(GeneralConfig.USE_NATIVE_RENDERER.get());
        if (requestedNativeRenderer && NativeLibLoader.isLoaded() && !nativeComputeFallbackLogged) {
            nativeComputeFallbackLogged = true;
            YesSteveModel.LOGGER.warn("[YSM Render] Native compute renderer is disabled because it can drop model parts; using Java renderer for now");
        }
        int vertexCount = renderModel(buffer, pose, model, boneParams, renderPartMask, packedLight, packedOverlay, red, green, blue, alpha, isPreview);
        if (profiling) {
            recordProfile(System.nanoTime() - startNanos, vertexCount, false);
        }
    }

    public static int renderModel(
            VertexConsumer vertexConsumer,
            PoseStack.Pose pose,
            GeoModel mesh,
            float[] boneParams,
            int renderPartMask,
            int packedLight, int packedOverlay,
            float r, float g, float b, float a,
            boolean isPreview) {

        if (mesh.bakedBones == null || mesh.bakedBones.isEmpty()) return 0;
        java.util.List<GeoModel.BakedBone> bakedBones = mesh.bakedBones;
        int boneCount = bakedBones.size();
        GeoModel.FlattenedRenderData flattenedRenderData = mesh.getFlattenedRenderData();
        if (flattenedRenderData == null) return 0;
        GeoModel.BakedBone[] bakedBoneArray = flattenedRenderData.sourceArray;
        int[] renderBoneIndices = flattenedRenderData.getRenderBoneIndices(renderPartMask);
        int[] computeBoneIndices = flattenedRenderData.getComputeBoneIndices(renderPartMask);
        boolean dynamicRenderPartMask = renderPartMask < 0 || renderPartMask > 3;

        // TODO: 修復GC壓力
        Matrix4f rootPoseMat = pose.pose();
        Matrix3f rootNormalMC = pose.normal();
        Matrix4f projMat = null;

        RenderScratch scratch = RENDER_SCRATCH.get();
        scratch.prepare(boneCount);
        for (int computeIndex = 0; computeIndex < computeBoneIndices.length; computeIndex++) {
            calculateBoneMatrix(computeBoneIndices[computeIndex], bakedBoneArray, boneParams, scratch.boneLocalTransforms, scratch.boneVisible, scratch.identityMat);
        }

        int vertexCount = 0;
        for (int renderBoneIndex = 0; renderBoneIndex < renderBoneIndices.length; renderBoneIndex++) {
            int i = renderBoneIndices[renderBoneIndex];
            GeoModel.FlattenedBone bone = flattenedRenderData.bones[i];
            if (dynamicRenderPartMask && renderPartMask != 0 && bone.partMask != renderPartMask && bone.partMask != 3) {
                continue;
            }

            if (!scratch.boneVisible[i]) {
                continue;
            }

            Matrix4f localBoneMat = scratch.boneLocalTransforms[i];
            scratch.globalBoneMat.set(rootPoseMat).mul(localBoneMat);
            float globalM00 = scratch.globalBoneMat.m00();
            float globalM01 = scratch.globalBoneMat.m01();
            float globalM02 = scratch.globalBoneMat.m02();
            float globalM10 = scratch.globalBoneMat.m10();
            float globalM11 = scratch.globalBoneMat.m11();
            float globalM12 = scratch.globalBoneMat.m12();
            float globalM20 = scratch.globalBoneMat.m20();
            float globalM21 = scratch.globalBoneMat.m21();
            float globalM22 = scratch.globalBoneMat.m22();
            float globalM30 = scratch.globalBoneMat.m30();
            float globalM31 = scratch.globalBoneMat.m31();
            float globalM32 = scratch.globalBoneMat.m32();
            boolean canCullBone = !isPreview && bone.hasCullable;
            float projM00 = 0.0f;
            float projM01 = 0.0f;
            float projM03 = 0.0f;
            float projM10 = 0.0f;
            float projM11 = 0.0f;
            float projM13 = 0.0f;
            float projM20 = 0.0f;
            float projM21 = 0.0f;
            float projM23 = 0.0f;
            float projM30 = 0.0f;
            float projM31 = 0.0f;
            float projM33 = 0.0f;
            if (canCullBone) {
                if (projMat == null) {
                    projMat = net.minecraft.client.Minecraft.getInstance().gameRenderer.getProjectionMatrix(net.minecraft.client.Minecraft.getInstance().options.fov().get());
                }
                scratch.projBoneMat.set(projMat).mul(scratch.globalBoneMat);
                projM00 = scratch.projBoneMat.m00();
                projM01 = scratch.projBoneMat.m01();
                projM03 = scratch.projBoneMat.m03();
                projM10 = scratch.projBoneMat.m10();
                projM11 = scratch.projBoneMat.m11();
                projM13 = scratch.projBoneMat.m13();
                projM20 = scratch.projBoneMat.m20();
                projM21 = scratch.projBoneMat.m21();
                projM23 = scratch.projBoneMat.m23();
                projM30 = scratch.projBoneMat.m30();
                projM31 = scratch.projBoneMat.m31();
                projM33 = scratch.projBoneMat.m33();
            }

            // 法線全域矩陣
            localBoneMat.normal(scratch.localNormalMat);
            scratch.globalNormalMat.set(rootNormalMC).mul(scratch.localNormalMat);
            float normalM00 = scratch.globalNormalMat.m00();
            float normalM01 = scratch.globalNormalMat.m01();
            float normalM02 = scratch.globalNormalMat.m02();
            float normalM10 = scratch.globalNormalMat.m10();
            float normalM11 = scratch.globalNormalMat.m11();
            float normalM12 = scratch.globalNormalMat.m12();
            float normalM20 = scratch.globalNormalMat.m20();
            float normalM21 = scratch.globalNormalMat.m21();
            float normalM22 = scratch.globalNormalMat.m22();
            scratch.prepareTransformedNormals(bone.normalCount);
            float[] uniqueNormals = bone.uniqueNormals;
            float[] transformedNormals = scratch.transformedNormals;
            for (int normalIndex = 0; normalIndex < bone.normalCount; normalIndex++) {
                int normalOffset = normalIndex * 3;
                float x = uniqueNormals[normalOffset];
                float y = uniqueNormals[normalOffset + 1];
                float z = uniqueNormals[normalOffset + 2];
                float transformedX = (normalM00 * x) + (normalM10 * y) + (normalM20 * z);
                float transformedY = (normalM01 * x) + (normalM11 * y) + (normalM21 * z);
                float transformedZ = (normalM02 * x) + (normalM12 * y) + (normalM22 * z);
                float lengthSq = (transformedX * transformedX) + (transformedY * transformedY) + (transformedZ * transformedZ);
                if (lengthSq > 1.0E-12f) {
                    float invLength = (float) (1.0d / Math.sqrt(lengthSq));
                    transformedX *= invLength;
                    transformedY *= invLength;
                    transformedZ *= invLength;
                }
                transformedNormals[normalOffset] = transformedX;
                transformedNormals[normalOffset + 1] = transformedY;
                transformedNormals[normalOffset + 2] = transformedZ;
            }

            int currentPackedLight = bone.glow ? FULL_BRIGHT_LIGHT : packedLight;
            float[] positions = bone.positions;
            float[] uvs = bone.uvs;
            boolean[] cullable = bone.cullable;
            int[] normalIndices = bone.normalIndices;

            for (int quadIndex = 0; quadIndex < bone.quadCount; quadIndex++) {
                int positionOffset = quadIndex * 12;
                if (canCullBone && cullable[quadIndex]) {
                    float p1x = positions[positionOffset];
                    float p1y = positions[positionOffset + 1];
                    float p1z = positions[positionOffset + 2];
                    float p2x = positions[positionOffset + 3];
                    float p2y = positions[positionOffset + 4];
                    float p2z = positions[positionOffset + 5];
                    float p3x = positions[positionOffset + 6];
                    float p3y = positions[positionOffset + 7];
                    float p3z = positions[positionOffset + 8];

                    float cp1x = (projM00 * p1x) + (projM10 * p1y) + (projM20 * p1z) + projM30;
                    float cp1y = (projM01 * p1x) + (projM11 * p1y) + (projM21 * p1z) + projM31;
                    float cp1w = (projM03 * p1x) + (projM13 * p1y) + (projM23 * p1z) + projM33;
                    float cp2x = (projM00 * p2x) + (projM10 * p2y) + (projM20 * p2z) + projM30;
                    float cp2y = (projM01 * p2x) + (projM11 * p2y) + (projM21 * p2z) + projM31;
                    float cp2w = (projM03 * p2x) + (projM13 * p2y) + (projM23 * p2z) + projM33;
                    float cp3x = (projM00 * p3x) + (projM10 * p3y) + (projM20 * p3z) + projM30;
                    float cp3y = (projM01 * p3x) + (projM11 * p3y) + (projM21 * p3z) + projM31;
                    float cp3w = (projM03 * p3x) + (projM13 * p3y) + (projM23 * p3z) + projM33;
                    float det = cp1x * (cp2y * cp3w - cp3y * cp2w) - cp2x * (cp1y * cp3w - cp3y * cp1w) + cp3x * (cp1y * cp2w - cp2y * cp1w);
                    if (det <= 0.0f) {
                        continue;
                    }
                }

                int normalOffset = normalIndices[quadIndex] * 3;
                float normalX = transformedNormals[normalOffset];
                float normalY = transformedNormals[normalOffset + 1];
                float normalZ = transformedNormals[normalOffset + 2];

                int uvOffset = quadIndex * 8;
                for (int v = 0; v < 4; v++) {
                    int vertexPositionOffset = positionOffset + v * 3;
                    float x = positions[vertexPositionOffset];
                    float y = positions[vertexPositionOffset + 1];
                    float z = positions[vertexPositionOffset + 2];
                    int vertexUvOffset = uvOffset + v * 2;
                    vertexConsumer.addVertex(
                                    (globalM00 * x) + (globalM10 * y) + (globalM20 * z) + globalM30,
                                    (globalM01 * x) + (globalM11 * y) + (globalM21 * z) + globalM31,
                                    (globalM02 * x) + (globalM12 * y) + (globalM22 * z) + globalM32)
                            .setColor(r, g, b, a)
                            .setUv(uvs[vertexUvOffset], uvs[vertexUvOffset + 1])
                            .setOverlay(packedOverlay)
                            .setLight(currentPackedLight)
                            .setNormal(normalX, normalY, normalZ);
                    vertexCount++;
                }
            }
        }
        return vertexCount;
    }

    private static Matrix4f calculateBoneMatrix(int idx, GeoModel.BakedBone[] bones, float[] boneParams, Matrix4f[] cache, boolean[] visibleCache, Matrix4f rootPose) {
        GeoModel.BakedBone bone = bones[idx];
        Matrix4f parentMatrix = rootPose;
        boolean isVisible = true;

        if (bone.parentIdx != -1) {
            parentMatrix = cache[bone.parentIdx];
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

        if (animSx == 0.0f && animSy == 0.0f && animSz == 0.0f) {
            isVisible = false;
        }/* else if (unk1 == 1 || unk2 == 1) isVisible = false;*/

        localMat.translate(
                bone.pivotX16 - (animTx * 0.0625f),
                bone.pivotY16 + (animTy * 0.0625f),
                bone.pivotZ16 + (animTz * 0.0625f)
        );
        if (animRz != 0.0f) {
            localMat.rotateZ(animRz);
        }
        if (animRy != 0.0f) {
            localMat.rotateY(animRy);
        }
        if (animRx != 0.0f) {
            localMat.rotateX(animRx);
        }

        if (animSx != 1.0f || animSy != 1.0f || animSz != 1.0f) {
            localMat.scale(animSx, animSy, animSz);
        }

        localMat.translate(-bone.pivotX16, -bone.pivotY16, -bone.pivotZ16);

        visibleCache[idx] = isVisible; // 保存當前骨骼的可見性
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
        private Matrix4f[] boneLocalTransforms = new Matrix4f[0];
        private boolean[] boneVisible = new boolean[0];
        private float[] transformedNormals = new float[0];

        private void prepare(int boneCount) {
            this.identityMat.identity();
            if (this.boneLocalTransforms.length < boneCount) {
                this.boneLocalTransforms = new Matrix4f[boneCount];
                this.boneVisible = new boolean[boneCount];
            }
        }

        private void prepareTransformedNormals(int normalCount) {
            int required = normalCount * 3;
            if (this.transformedNormals.length < required) {
                this.transformedNormals = new float[required];
            }
        }
    }
}
