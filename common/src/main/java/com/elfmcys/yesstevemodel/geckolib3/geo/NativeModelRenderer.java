

package com.elfmcys.yesstevemodel.geckolib3.geo;

import com.elfmcys.yesstevemodel.NativeLibLoader;
import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.client.renderer.ModelPreviewRenderer;
import com.elfmcys.yesstevemodel.config.GeneralConfig;
import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
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
    private static final ThreadLocal<Integer> NATIVE_SUBMITTED_VERTICES = ThreadLocal.withInitial(() -> 0);
    private static final int FULL_BRIGHT_LIGHT = LightTexture.pack(15, 15);
    private static final int NATIVE_BONE_STRIDE_BYTES = 144;
    private static final int NATIVE_BONE_TRANSFORM_OFFSET_BYTES = 0;
    private static final int NATIVE_BONE_NORMAL_OFFSET_BYTES = 64;
    private static final long PROFILE_LOG_INTERVAL_NANOS = 5_000_000_000L;
    private static long profileLastLogTime = System.nanoTime();
    private static long profileRenderCalls;
    private static long profileNativeCalls;
    private static long profileVertices;
    private static long profileNanos;
    private static boolean nativeBoneMatrixFallbackLogged;

    public static void renderMesh(VertexConsumer buffer, PoseStack.Pose pose, GeoModel model, float[] boneParams, float[] stateBuffer, int textureIndex, int renderPartMask, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        renderMesh(buffer, pose, model, boneParams, stateBuffer, textureIndex, renderPartMask, packedLight, packedOverlay, red, green, blue, alpha, null);
    }

    public static void renderMesh(VertexConsumer buffer, PoseStack.Pose pose, GeoModel model, float[] boneParams, float[] stateBuffer, int textureIndex, int renderPartMask, int packedLight, int packedOverlay, float red, float green, float blue, float alpha, ResourceLocation textureLocation) {
        OculusCompat.updatePBRState();
        boolean isPreview = ModelPreviewRenderer.isPreview() || ModelPreviewRenderer.isExtraPlayer();
        boolean profiling = Boolean.TRUE.equals(GeneralConfig.RENDER_PROFILING.get());
        long startNanos = profiling ? System.nanoTime() : 0L;
        RenderScratch scratch = RENDER_SCRATCH.get();
        scratch.usedNativeBoneMatrices = false;
        if (NativeGpuGlRenderer.tryRender(model, pose, boneParams, stateBuffer, renderPartMask, packedLight, packedOverlay, red, green, blue, alpha, textureLocation, isPreview)) {
            if (profiling) {
                recordProfile(System.nanoTime() - startNanos, 0, true);
            }
            return;
        }
        if (!isPreview && Boolean.TRUE.equals(GeneralConfig.USE_NATIVE_RENDERER.get()) && NativeLibLoader.isLoaded()) {
            model.getOrUploadNativeGpuMesh();
            if (Boolean.TRUE.equals(GeneralConfig.USE_EXPERIMENTAL_GPU_RENDERER.get())) {
                NativeGpuRenderer.prepareStaticMesh(model, renderPartMask, false);
            }
        }
        int nativeVertexCount = tryRenderNativeVertices(buffer, pose, model, boneParams, renderPartMask, packedLight, packedOverlay, red, green, blue, alpha, isPreview);
        if (nativeVertexCount > 0) {
            if (profiling) {
                recordProfile(System.nanoTime() - startNanos, nativeVertexCount, true);
            }
            return;
        }
        int vertexCount = renderModel(buffer, pose, model, boneParams, renderPartMask, packedLight, packedOverlay, red, green, blue, alpha, isPreview);
        if (profiling) {
            recordProfile(System.nanoTime() - startNanos, vertexCount, scratch.usedNativeBoneMatrices);
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
        boolean useNativeBoneMatrices = tryComputeNativeBoneMatrices(mesh, pose, boneParams, renderPartMask, packedLight, boneCount, scratch);
        for (int computeIndex = 0; computeIndex < computeBoneIndices.length; computeIndex++) {
            int boneIndex = computeBoneIndices[computeIndex];
            if (useNativeBoneMatrices) {
                calculateBoneVisibility(boneIndex, bakedBoneArray, boneParams, scratch.boneVisible);
            } else {
                calculateBoneMatrix(boneIndex, bakedBoneArray, boneParams, scratch.boneLocalTransforms, scratch.boneVisible, scratch.identityMat);
            }
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
            float globalM00;
            float globalM01;
            float globalM02;
            float globalM10;
            float globalM11;
            float globalM12;
            float globalM20;
            float globalM21;
            float globalM22;
            float globalM30;
            float globalM31;
            float globalM32;
            if (useNativeBoneMatrices) {
                globalM00 = nativeBoneFloat(scratch.nativeBoneData, i, NATIVE_BONE_TRANSFORM_OFFSET_BYTES, 0);
                globalM01 = nativeBoneFloat(scratch.nativeBoneData, i, NATIVE_BONE_TRANSFORM_OFFSET_BYTES, 1);
                globalM02 = nativeBoneFloat(scratch.nativeBoneData, i, NATIVE_BONE_TRANSFORM_OFFSET_BYTES, 2);
                globalM10 = nativeBoneFloat(scratch.nativeBoneData, i, NATIVE_BONE_TRANSFORM_OFFSET_BYTES, 4);
                globalM11 = nativeBoneFloat(scratch.nativeBoneData, i, NATIVE_BONE_TRANSFORM_OFFSET_BYTES, 5);
                globalM12 = nativeBoneFloat(scratch.nativeBoneData, i, NATIVE_BONE_TRANSFORM_OFFSET_BYTES, 6);
                globalM20 = nativeBoneFloat(scratch.nativeBoneData, i, NATIVE_BONE_TRANSFORM_OFFSET_BYTES, 8);
                globalM21 = nativeBoneFloat(scratch.nativeBoneData, i, NATIVE_BONE_TRANSFORM_OFFSET_BYTES, 9);
                globalM22 = nativeBoneFloat(scratch.nativeBoneData, i, NATIVE_BONE_TRANSFORM_OFFSET_BYTES, 10);
                globalM30 = nativeBoneFloat(scratch.nativeBoneData, i, NATIVE_BONE_TRANSFORM_OFFSET_BYTES, 12);
                globalM31 = nativeBoneFloat(scratch.nativeBoneData, i, NATIVE_BONE_TRANSFORM_OFFSET_BYTES, 13);
                globalM32 = nativeBoneFloat(scratch.nativeBoneData, i, NATIVE_BONE_TRANSFORM_OFFSET_BYTES, 14);
            } else {
                scratch.globalBoneMat.set(rootPoseMat).mul(localBoneMat);
                globalM00 = scratch.globalBoneMat.m00();
                globalM01 = scratch.globalBoneMat.m01();
                globalM02 = scratch.globalBoneMat.m02();
                globalM10 = scratch.globalBoneMat.m10();
                globalM11 = scratch.globalBoneMat.m11();
                globalM12 = scratch.globalBoneMat.m12();
                globalM20 = scratch.globalBoneMat.m20();
                globalM21 = scratch.globalBoneMat.m21();
                globalM22 = scratch.globalBoneMat.m22();
                globalM30 = scratch.globalBoneMat.m30();
                globalM31 = scratch.globalBoneMat.m31();
                globalM32 = scratch.globalBoneMat.m32();
            }
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
                if (useNativeBoneMatrices) {
                    setNativeBoneMatrix(scratch.globalBoneMat, scratch.nativeBoneData, i, NATIVE_BONE_TRANSFORM_OFFSET_BYTES);
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
            float normalM00;
            float normalM01;
            float normalM02;
            float normalM10;
            float normalM11;
            float normalM12;
            float normalM20;
            float normalM21;
            float normalM22;
            if (useNativeBoneMatrices) {
                normalM00 = nativeBoneFloat(scratch.nativeBoneData, i, NATIVE_BONE_NORMAL_OFFSET_BYTES, 0);
                normalM01 = nativeBoneFloat(scratch.nativeBoneData, i, NATIVE_BONE_NORMAL_OFFSET_BYTES, 1);
                normalM02 = nativeBoneFloat(scratch.nativeBoneData, i, NATIVE_BONE_NORMAL_OFFSET_BYTES, 2);
                normalM10 = nativeBoneFloat(scratch.nativeBoneData, i, NATIVE_BONE_NORMAL_OFFSET_BYTES, 4);
                normalM11 = nativeBoneFloat(scratch.nativeBoneData, i, NATIVE_BONE_NORMAL_OFFSET_BYTES, 5);
                normalM12 = nativeBoneFloat(scratch.nativeBoneData, i, NATIVE_BONE_NORMAL_OFFSET_BYTES, 6);
                normalM20 = nativeBoneFloat(scratch.nativeBoneData, i, NATIVE_BONE_NORMAL_OFFSET_BYTES, 8);
                normalM21 = nativeBoneFloat(scratch.nativeBoneData, i, NATIVE_BONE_NORMAL_OFFSET_BYTES, 9);
                normalM22 = nativeBoneFloat(scratch.nativeBoneData, i, NATIVE_BONE_NORMAL_OFFSET_BYTES, 10);
            } else {
                localBoneMat.normal(scratch.localNormalMat);
                scratch.globalNormalMat.set(rootNormalMC).mul(scratch.localNormalMat);
                normalM00 = scratch.globalNormalMat.m00();
                normalM01 = scratch.globalNormalMat.m01();
                normalM02 = scratch.globalNormalMat.m02();
                normalM10 = scratch.globalNormalMat.m10();
                normalM11 = scratch.globalNormalMat.m11();
                normalM12 = scratch.globalNormalMat.m12();
                normalM20 = scratch.globalNormalMat.m20();
                normalM21 = scratch.globalNormalMat.m21();
                normalM22 = scratch.globalNormalMat.m22();
            }
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

    private static int tryRenderNativeVertices(VertexConsumer vertexConsumer, PoseStack.Pose pose, GeoModel mesh, float[] boneParams, int renderPartMask, int packedLight, int packedOverlay, float r, float g, float b, float a, boolean isPreview) {
        if (isPreview || renderPartMask != 0 || !Boolean.TRUE.equals(GeneralConfig.USE_NATIVE_RENDERER.get()) || !NativeLibLoader.isLoaded()) {
            return 0;
        }
        GeoModel.FlattenedRenderData flattenedRenderData = mesh.getFlattenedRenderData();
        if (flattenedRenderData == null || !canUseNativeVertexPath(flattenedRenderData, boneParams)) {
            return 0;
        }
        mesh.buildNativeCache();
        if (mesh.nativeModelHandle == 0) {
            return 0;
        }
        try {
            return nativeRenderModel(vertexConsumer, pose, null, false, mesh, boneParams, null, 0, 0, packedLight, packedOverlay, r, g, b, a, false);
        } catch (Throwable throwable) {
            if (!nativeBoneMatrixFallbackLogged) {
                nativeBoneMatrixFallbackLogged = true;
                YesSteveModel.LOGGER.warn("[YSM Render] Native vertex path failed; falling back to Java renderer", throwable);
            }
            return 0;
        }
    }

    private static boolean canUseNativeVertexPath(GeoModel.FlattenedRenderData flattenedRenderData, float[] boneParams) {
        for (GeoModel.FlattenedBone bone : flattenedRenderData.bones) {
            if (bone != null && bone.hasCullable) {
                return false;
            }
        }
        int boneCount = flattenedRenderData.bones.length;
        for (int boneIndex = 0; boneIndex < boneCount; boneIndex++) {
            int skipChildrenOffset = (boneIndex * 12) + 10;
            if (skipChildrenOffset < boneParams.length && boneParams[skipChildrenOffset] != 0.0f) {
                return false;
            }
        }
        return true;
    }

    private static boolean tryComputeNativeBoneMatrices(GeoModel mesh, PoseStack.Pose pose, float[] boneParams, int renderPartMask, int packedLight, int boneCount, RenderScratch scratch) {
        if (!Boolean.TRUE.equals(GeneralConfig.USE_NATIVE_RENDERER.get()) || !NativeLibLoader.isLoaded()) {
            return false;
        }
        mesh.buildNativeGpuMesh();
        if (mesh.nativeGpuMeshHandle == 0) {
            return false;
        }
        try {
            scratch.prepareNativeBoneData(boneCount);
            pose.pose().get(scratch.rootPoseTransfer);
            Matrix3f rootNormal = pose.normal();
            scratch.rootNormalTransfer[0] = rootNormal.m00();
            scratch.rootNormalTransfer[1] = rootNormal.m01();
            scratch.rootNormalTransfer[2] = rootNormal.m02();
            scratch.rootNormalTransfer[3] = rootNormal.m10();
            scratch.rootNormalTransfer[4] = rootNormal.m11();
            scratch.rootNormalTransfer[5] = rootNormal.m12();
            scratch.rootNormalTransfer[6] = rootNormal.m20();
            scratch.rootNormalTransfer[7] = rootNormal.m21();
            scratch.rootNormalTransfer[8] = rootNormal.m22();
            GeoModel.nComputeBoneMatrices(mesh.nativeGpuMeshHandle, scratch.rootPoseTransfer, scratch.rootNormalTransfer, boneParams, packedLight, scratch.nativeBoneData);
            if (mesh.nativeGpuMeshCache != null) {
                mesh.nativeGpuMeshCache.uploadBoneData(scratch.nativeBoneData, boneCount);
                if (Boolean.TRUE.equals(GeneralConfig.USE_EXPERIMENTAL_GPU_RENDERER.get())) {
                    NativeGpuRenderer.prepareBoneDraw(mesh, renderPartMask, boneCount);
                }
            }
            scratch.usedNativeBoneMatrices = true;
            return true;
        } catch (Throwable throwable) {
            if (!nativeBoneMatrixFallbackLogged) {
                nativeBoneMatrixFallbackLogged = true;
                YesSteveModel.LOGGER.warn("[YSM Render] Native bone matrix path failed; falling back to Java bone matrices", throwable);
            }
            return false;
        }
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

    private static void calculateBoneVisibility(int idx, GeoModel.BakedBone[] bones, float[] boneParams, boolean[] visibleCache) {
        GeoModel.BakedBone bone = bones[idx];
        boolean isVisible = true;
        if (bone.parentIdx != -1 && !visibleCache[bone.parentIdx]) {
            isVisible = false;
        }

        int pOffset = idx * 12;
        float animSx = boneParams[pOffset + 6];
        float animSy = boneParams[pOffset + 7];
        float animSz = boneParams[pOffset + 8];
        if (animSx == 0.0f && animSy == 0.0f && animSz == 0.0f) {
            isVisible = false;
        }
        visibleCache[idx] = isVisible;
    }

    private static float nativeBoneFloat(ByteBuffer buffer, int boneIndex, int sectionOffsetBytes, int floatOffset) {
        return buffer.getFloat((boneIndex * NATIVE_BONE_STRIDE_BYTES) + sectionOffsetBytes + (floatOffset * Float.BYTES));
    }

    private static void setNativeBoneMatrix(Matrix4f matrix, ByteBuffer buffer, int boneIndex, int sectionOffsetBytes) {
        matrix.set(
                nativeBoneFloat(buffer, boneIndex, sectionOffsetBytes, 0),
                nativeBoneFloat(buffer, boneIndex, sectionOffsetBytes, 1),
                nativeBoneFloat(buffer, boneIndex, sectionOffsetBytes, 2),
                nativeBoneFloat(buffer, boneIndex, sectionOffsetBytes, 3),
                nativeBoneFloat(buffer, boneIndex, sectionOffsetBytes, 4),
                nativeBoneFloat(buffer, boneIndex, sectionOffsetBytes, 5),
                nativeBoneFloat(buffer, boneIndex, sectionOffsetBytes, 6),
                nativeBoneFloat(buffer, boneIndex, sectionOffsetBytes, 7),
                nativeBoneFloat(buffer, boneIndex, sectionOffsetBytes, 8),
                nativeBoneFloat(buffer, boneIndex, sectionOffsetBytes, 9),
                nativeBoneFloat(buffer, boneIndex, sectionOffsetBytes, 10),
                nativeBoneFloat(buffer, boneIndex, sectionOffsetBytes, 11),
                nativeBoneFloat(buffer, boneIndex, sectionOffsetBytes, 12),
                nativeBoneFloat(buffer, boneIndex, sectionOffsetBytes, 13),
                nativeBoneFloat(buffer, boneIndex, sectionOffsetBytes, 14),
                nativeBoneFloat(buffer, boneIndex, sectionOffsetBytes, 15)
        );
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
            NATIVE_SUBMITTED_VERTICES.set(0);
            return;
        }

        FloatBuffer floats = vertexData.duplicate().order(ByteOrder.nativeOrder()).asFloatBuffer();
        IntBuffer ints = intData.duplicate().order(ByteOrder.nativeOrder()).asIntBuffer();
        int count = Math.min(vertexCount, Math.min(floats.remaining() / 12, ints.remaining() / 2));
        NATIVE_SUBMITTED_VERTICES.set(count);

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
        NATIVE_SUBMITTED_VERTICES.set(0);

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
        return NATIVE_SUBMITTED_VERTICES.get();
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
        private final float[] rootPoseTransfer = new float[16];
        private final float[] rootNormalTransfer = new float[9];
        private Matrix4f[] boneLocalTransforms = new Matrix4f[0];
        private boolean[] boneVisible = new boolean[0];
        private float[] transformedNormals = new float[0];
        private ByteBuffer nativeBoneData;
        private boolean usedNativeBoneMatrices;

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

        private void prepareNativeBoneData(int boneCount) {
            int required = boneCount * NATIVE_BONE_STRIDE_BYTES;
            if (this.nativeBoneData == null || this.nativeBoneData.capacity() < required) {
                this.nativeBoneData = ByteBuffer.allocateDirect(required).order(ByteOrder.nativeOrder());
            }
        }
    }
}
