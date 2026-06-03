package com.elfmcys.yesstevemodel.geckolib3.geo.render.built;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.config.GeneralConfig;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.util.StringPool;
import com.elfmcys.yesstevemodel.geckolib3.geo.animated.AnimatedGeoModel;
import com.elfmcys.yesstevemodel.resource.models.GeometryDescription;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

/**
 * Bedrock的.geo模型文件
 */
public class GeoModel {
    private static boolean nativeInitFailureLogged;

    @NotNull
    public final List<GeoBone> bones;

    @NotNull
    public final IntList leftHandIds;

    @NotNull
    public final IntList rightHandIds;

    @NotNull
    public final IntList elytraIds;

    @NotNull
    public final IntList tacPistolIds;

    @NotNull
    public final IntList tacRifleIds;

    @NotNull
    public final IntList leftWaistIds;

    @NotNull
    public final IntList rightWaistIds;

    @NotNull
    public final IntList leftShoulderIds;

    @NotNull
    public final IntList rightShoulderIds;

    @NotNull
    public final IntList bladeIds;

    @NotNull
    public final IntList sheathIds;

    @NotNull
    public final IntList headIds;

    @NotNull
    public final IntList backpackIds;

    public final boolean hasCustomLeftHand;

    public final boolean hasCustomRightHand;

    public final boolean hasCustomLimbs;

    @NotNull
    private final GeometryDescription properties;

    public final float[] boneTransformData;

    private boolean[] translucentTexture;

    @NotNull
    public final List<IntList> extraLeftHandGroups = new ObjectArrayList<>();

    @NotNull
    public final List<IntList> extraRightHandGroups = new ObjectArrayList<>();

    @NotNull
    public final List<IntList> passengerGroups = new ObjectArrayList<>();

    public List<BakedBone> bakedBones;

    private FlattenedRenderData flattenedRenderData;

    public static class BakedBone {
        public String name;
        public boolean glow;
        public int parentIdx = -1;
        public float pivotX, pivotY, pivotZ;
        public float pivotX16, pivotY16, pivotZ16;
        public float rotX, rotY, rotZ;
        public List<BakedCube> cubes = new ObjectArrayList<>();
        public int partMask;
    }

    public static class BakedCube {
        public boolean cullable = false;
        //        public float pivotX, pivotY, pivotZ;
//        public float rotX, rotY, rotZ;
        public List<BakedQuad> quads = new ObjectArrayList<>();
    }

    public static class BakedQuad {
        public Vector3f[] positions = new Vector3f[4];
        public Vector2f[] uvs = new Vector2f[4];
        public Vector3f normal;
    }

    public static class FlattenedRenderData {
        public final List<BakedBone> source;
        public final BakedBone[] sourceArray;
        public final FlattenedBone[] bones;
        private final int[][] renderBoneIndicesByMask;
        private final int[][] computeBoneIndicesByMask;

        private FlattenedRenderData(List<BakedBone> source, BakedBone[] sourceArray, FlattenedBone[] bones, int[][] renderBoneIndicesByMask, int[][] computeBoneIndicesByMask) {
            this.source = source;
            this.sourceArray = sourceArray;
            this.bones = bones;
            this.renderBoneIndicesByMask = renderBoneIndicesByMask;
            this.computeBoneIndicesByMask = computeBoneIndicesByMask;
        }

        public int[] getRenderBoneIndices(int renderPartMask) {
            if (0 <= renderPartMask && renderPartMask < this.renderBoneIndicesByMask.length) {
                return this.renderBoneIndicesByMask[renderPartMask];
            }
            return this.renderBoneIndicesByMask[0];
        }

        public int[] getComputeBoneIndices(int renderPartMask) {
            if (0 <= renderPartMask && renderPartMask < this.computeBoneIndicesByMask.length) {
                return this.computeBoneIndicesByMask[renderPartMask];
            }
            return this.computeBoneIndicesByMask[0];
        }
    }

    public static class FlattenedBone {
        public boolean glow;
        public int partMask;
        public boolean hasCullable;
        public int quadCount;
        public int normalCount;
        public boolean[] cullable;
        public int[] normalIndices;
        public float[] positions;
        public float[] uvs;
        public float[] uniqueNormals;
    }

//    static {
//        System.load("test.dll");
//    }

    public long nativeModelHandle = 0;

    private boolean nativeCacheAttempted;

    public static native long nInitModelCache(ByteBuffer buffer);

    public static native long nBuildGpuMesh(ByteBuffer buffer, int[] metadata);

    public static native ByteBuffer nGetGpuMeshVertexBuffer(long handle);

    public static native ByteBuffer nGetGpuMeshIndexBuffer(long handle);

    public static native void nReleaseGpuMeshScratch(long handle);

    public static native void nFreeGpuMesh(long handle);

    public static native void nDestroyModelCache(long handle);

    public static native void nComputeModelVertices(
            long handle, Object vertexConsumer,
            float[] matrixTransfer, float[] animTransfer,
            int renderPartMask, int packedLight, int packedOverlay,
            float r, float g, float b, float a);

    public static native void nComputeBoneMatrices(
            long handle, float[] matrixTransfer, float[] animTransfer, float[] output,
            int renderPartMask, ByteBuffer scratch);

    public static native void nComputeBoneMatricesLocal(
            long handle, float[] animTransfer, int renderPartMask, ByteBuffer scratch);

    public void buildNativeCache() {
        if (bakedBones == null || bakedBones.isEmpty()) return;
        if (!Boolean.TRUE.equals(GeneralConfig.USE_NATIVE_RENDERER.get())) return;
        if (nativeCacheAttempted || nativeModelHandle != 0) return;
        nativeCacheAttempted = true;

        int totalBones = bakedBones.size();
        int totalCubes = 0;
        int totalQuads = 0;

        for (BakedBone bone : bakedBones) {
            totalCubes += bone.cubes.size();
            for (BakedCube cube : bone.cubes) {
                totalQuads += cube.quads.size();
            }
        }

        int initBufferSize = 4 + (totalBones * 25) + (totalCubes * 5) + (totalQuads * 92);
        ByteBuffer buffer = ByteBuffer.allocateDirect(initBufferSize).order(ByteOrder.nativeOrder());

        buffer.putInt(bakedBones.size());
        for (BakedBone bone : bakedBones) {
            buffer.putInt(bone.parentIdx);
            buffer.putInt(bone.partMask);
            buffer.put((byte) (bone.glow ? 1 : 0));
            buffer.putFloat(bone.pivotX);
            buffer.putFloat(bone.pivotY);
            buffer.putFloat(bone.pivotZ);

            buffer.putInt(bone.cubes.size());
            for (BakedCube cube : bone.cubes) {
                buffer.put((byte) (cube.cullable ? 1 : 0));
                buffer.putInt(cube.quads.size());
                for (BakedQuad quad : cube.quads) {
                    for (int v = 0; v < 4; v++) {
                        buffer.putFloat(quad.positions[v].x());
                        buffer.putFloat(quad.positions[v].y());
                        buffer.putFloat(quad.positions[v].z());
                    }
                    for (int v = 0; v < 4; v++) {
                        buffer.putFloat(quad.uvs[v].x());
                        buffer.putFloat(quad.uvs[v].y());
                    }
                    // 3 floats *4=12
                    buffer.putFloat(quad.normal.x());
                    buffer.putFloat(quad.normal.y());
                    buffer.putFloat(quad.normal.z());
                }
            }
        }

        buffer.position(0);
        try {
            this.nativeModelHandle = nInitModelCache(buffer);
        } catch (Throwable throwable) {
            if (!nativeInitFailureLogged) {
                nativeInitFailureLogged = true;
                YesSteveModel.LOGGER.warn("[YSM Render] Native model cache is unavailable; falling back to Java renderer", throwable);
            }
            this.nativeModelHandle = 0;
        }
    }

    public void freeNativeCache() {
        if (nativeModelHandle != 0) {
            nDestroyModelCache(nativeModelHandle);
            nativeModelHandle = 0;
        }
        nativeCacheAttempted = false;
    }

    public FlattenedRenderData getFlattenedRenderData() {
        if (bakedBones == null || bakedBones.isEmpty()) return null;
        if (flattenedRenderData == null || flattenedRenderData.source != bakedBones || flattenedRenderData.bones.length != bakedBones.size()) {
            flattenedRenderData = buildFlattenedRenderData();
        }
        return flattenedRenderData;
    }

    private FlattenedRenderData buildFlattenedRenderData() {
        FlattenedBone[] flattenedBones = new FlattenedBone[bakedBones.size()];
        int[][] renderBoneIndexBuffers = new int[4][bakedBones.size()];
        int[] renderBoneIndexCounts = new int[4];
        for (int i = 0; i < bakedBones.size(); i++) {
            BakedBone sourceBone = bakedBones.get(i);
            FlattenedBone flattenedBone = new FlattenedBone();
            flattenedBone.glow = sourceBone.glow;
            flattenedBone.partMask = sourceBone.partMask;

            int quadCount = 0;
            for (BakedCube cube : sourceBone.cubes) {
                quadCount += cube.quads.size();
            }

            flattenedBone.quadCount = quadCount;
            flattenedBone.cullable = new boolean[quadCount];
            flattenedBone.normalIndices = new int[quadCount];
            flattenedBone.positions = new float[quadCount * 12];
            flattenedBone.uvs = new float[quadCount * 8];
            float[] uniqueNormals = new float[quadCount * 3];
            int uniqueNormalCount = 0;

            int quadIndex = 0;
            for (BakedCube cube : sourceBone.cubes) {
                for (BakedQuad quad : cube.quads) {
                    flattenedBone.cullable[quadIndex] = cube.cullable;
                    if (cube.cullable) {
                        flattenedBone.hasCullable = true;
                    }

                    int positionOffset = quadIndex * 12;
                    for (int vertexIndex = 0; vertexIndex < 4; vertexIndex++) {
                        Vector3f position = quad.positions[vertexIndex];
                        int offset = positionOffset + vertexIndex * 3;
                        flattenedBone.positions[offset] = position.x();
                        flattenedBone.positions[offset + 1] = position.y();
                        flattenedBone.positions[offset + 2] = position.z();
                    }

                    int uvOffset = quadIndex * 8;
                    for (int vertexIndex = 0; vertexIndex < 4; vertexIndex++) {
                        Vector2f uv = quad.uvs[vertexIndex];
                        int offset = uvOffset + vertexIndex * 2;
                        flattenedBone.uvs[offset] = uv.x();
                        flattenedBone.uvs[offset + 1] = uv.y();
                    }

                    int normalIndex = findOrAddNormal(uniqueNormals, uniqueNormalCount, quad.normal);
                    if (normalIndex == uniqueNormalCount) {
                        uniqueNormalCount++;
                    }
                    flattenedBone.normalIndices[quadIndex] = normalIndex;

                    quadIndex++;
                }
            }
            flattenedBone.normalCount = uniqueNormalCount;
            flattenedBone.uniqueNormals = Arrays.copyOf(uniqueNormals, uniqueNormalCount * 3);
            flattenedBones[i] = flattenedBone;
            if (quadCount > 0) {
                addRenderBoneIndex(renderBoneIndexBuffers, renderBoneIndexCounts, 0, i);
                if (sourceBone.partMask == 1 || sourceBone.partMask == 3) {
                    addRenderBoneIndex(renderBoneIndexBuffers, renderBoneIndexCounts, 1, i);
                }
                if (sourceBone.partMask == 2 || sourceBone.partMask == 3) {
                    addRenderBoneIndex(renderBoneIndexBuffers, renderBoneIndexCounts, 2, i);
                }
                if (sourceBone.partMask == 3) {
                    addRenderBoneIndex(renderBoneIndexBuffers, renderBoneIndexCounts, 3, i);
                }
            }
        }
        int[][] renderBoneIndicesByMask = new int[renderBoneIndexBuffers.length][];
        int[][] computeBoneIndicesByMask = new int[renderBoneIndexBuffers.length][];
        BakedBone[] sourceArray = bakedBones.toArray(new BakedBone[0]);
        for (BakedBone bone : sourceArray) {
            bone.pivotX16 = bone.pivotX * 0.0625f;
            bone.pivotY16 = bone.pivotY * 0.0625f;
            bone.pivotZ16 = bone.pivotZ * 0.0625f;
        }
        for (int mask = 0; mask < renderBoneIndexBuffers.length; mask++) {
            renderBoneIndicesByMask[mask] = Arrays.copyOf(renderBoneIndexBuffers[mask], renderBoneIndexCounts[mask]);
            computeBoneIndicesByMask[mask] = buildComputeBoneIndices(sourceArray, renderBoneIndicesByMask[mask]);
        }
        return new FlattenedRenderData(bakedBones, sourceArray, flattenedBones, renderBoneIndicesByMask, computeBoneIndicesByMask);
    }

    private static void addRenderBoneIndex(int[][] buffers, int[] counts, int mask, int boneIndex) {
        buffers[mask][counts[mask]++] = boneIndex;
    }

    private static int[] buildComputeBoneIndices(BakedBone[] bones, int[] renderBoneIndices) {
        boolean[] added = new boolean[bones.length];
        int[] indices = new int[bones.length];
        int count = 0;
        for (int renderBoneIndex : renderBoneIndices) {
            count = addBoneWithParents(bones, renderBoneIndex, added, indices, count);
        }
        return Arrays.copyOf(indices, count);
    }

    private static int addBoneWithParents(BakedBone[] bones, int boneIndex, boolean[] added, int[] indices, int count) {
        if (boneIndex == -1 || added[boneIndex]) {
            return count;
        }
        count = addBoneWithParents(bones, bones[boneIndex].parentIdx, added, indices, count);
        added[boneIndex] = true;
        indices[count++] = boneIndex;
        return count;
    }

    private static int findOrAddNormal(float[] uniqueNormals, int normalCount, Vector3f normal) {
        int normalX = Float.floatToIntBits(normal.x());
        int normalY = Float.floatToIntBits(normal.y());
        int normalZ = Float.floatToIntBits(normal.z());
        for (int i = 0; i < normalCount; i++) {
            int offset = i * 3;
            if (Float.floatToIntBits(uniqueNormals[offset]) == normalX
                    && Float.floatToIntBits(uniqueNormals[offset + 1]) == normalY
                    && Float.floatToIntBits(uniqueNormals[offset + 2]) == normalZ) {
                return i;
            }
        }
        int offset = normalCount * 3;
        uniqueNormals[offset] = normal.x();
        uniqueNormals[offset + 1] = normal.y();
        uniqueNormals[offset + 2] = normal.z();
        return normalCount;
    }

    public GeoModel(GeoBone[] geoBones, String[][] strArr, boolean[] zArr, @NotNull GeometryDescription properties, boolean[] zArr2) {
        this.bones = ObjectLists.unmodifiable(ObjectArrayList.wrap(geoBones));
        this.leftHandIds = resolveBoneIds(strArr[0]);
        this.rightHandIds = resolveBoneIds(strArr[1]);
        this.elytraIds = resolveBoneIds(strArr[2]);
        this.tacPistolIds = resolveBoneIds(strArr[3]);
        this.tacRifleIds = resolveBoneIds(strArr[4]);
        this.leftWaistIds = resolveBoneIds(strArr[5]);
        this.rightWaistIds = resolveBoneIds(strArr[6]);
        this.leftShoulderIds = resolveBoneIds(strArr[7]);
        this.rightShoulderIds = resolveBoneIds(strArr[8]);
        this.bladeIds = resolveBoneIds(strArr[9]);
        this.sheathIds = resolveBoneIds(strArr[10]);
        this.headIds = resolveBoneIds(strArr[11]);
        this.backpackIds = resolveBoneIds(strArr[12]);
        for (int i = 13; i <= 19; i++) {
            String[] strArr2 = strArr[i];
            if (strArr2.length > 0) {
                this.extraLeftHandGroups.add(resolveBoneIds(strArr2));
            }
        }
        for (int i = 20; i <= 26; i++) {
            String[] strArr3 = strArr[i];
            if (strArr3.length > 0) {
                this.extraRightHandGroups.add(resolveBoneIds(strArr3));
            }
        }
        for (int i = 27; i <= 34; i++) {
            String[] strArr4 = strArr[i];
            if (strArr4.length > 0) {
                this.passengerGroups.add(resolveBoneIds(strArr4));
            }
        }
        this.hasCustomLeftHand = zArr[0]; // has left hand?
        this.hasCustomRightHand = zArr[1]; // has right hand?
        this.hasCustomLimbs = zArr[2]; // has background
        this.translucentTexture = zArr2;
        this.properties = properties;
        this.boneTransformData = new AnimatedGeoModel(this).getMatrixData();
    }

    private static IntList resolveBoneIds(String[] strArr) {
        IntArrayList intArrayList = new IntArrayList(strArr.length);
        for (String str : strArr) {
            intArrayList.add(StringPool.computeIfAbsent(str));
        }
        return IntLists.unmodifiable(intArrayList);
    }

    @NotNull
    public List<GeoBone> topLevelBones() {
        return this.bones;
    }

    public float[] getBoneTransformData() {
        return this.boneTransformData;
    }

    @NotNull
    public GeometryDescription getProperties() {
        return this.properties;
    }

    public boolean isTranslucentTexture(int i) {
        if (i < 0 || i >= this.translucentTexture.length) {
            return false;
        }
        return this.translucentTexture[i];
    }

    public void setTranslucentTexture(int i, boolean translucent) {
        ensureTranslucentTextureCapacity(i);
        this.translucentTexture[i] = translucent;
    }

    private void ensureTranslucentTextureCapacity(int maxIndex) {
        if (maxIndex >= this.translucentTexture.length) {
            boolean[] expanded = new boolean[maxIndex + 1];
            System.arraycopy(this.translucentTexture, 0, expanded, 0, this.translucentTexture.length);
            this.translucentTexture = expanded;
        }
    }
}
