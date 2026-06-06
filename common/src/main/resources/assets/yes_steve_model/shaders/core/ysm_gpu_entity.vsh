#version 150

#moj_import <minecraft:light.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec2 UV0;
in uint PackedNormal;
in uint BoneId;
in uvec2 PartFlags;

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

struct YsmBone {
    mat4 Transform;
    mat4 Normal;
    ivec4 Meta;
};

layout(std140) uniform YsmBones {
    YsmBone Bones[YSM_GPU_MAX_BONES];
};

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec4 lightMapColor;
out vec4 overlayColor;
out vec2 texCoord0;
flat out int hiddenBone;

float unpackSigned10(uint value) {
    int raw = int(value & 1023u);
    if (raw >= 512) {
        raw -= 1024;
    }
    return clamp(float(raw) / 511.0, -1.0, 1.0);
}

vec3 unpackNormal(uint packedNormal) {
    return normalize(vec3(
        unpackSigned10(packedNormal),
        unpackSigned10(packedNormal >> 10u),
        unpackSigned10(packedNormal >> 20u)
    ));
}

void main() {
    int boneIndex = int(BoneId);
    YsmBone bone = Bones[boneIndex];
    vec4 worldPosition = bone.Transform * vec4(Position, 1.0);
    vec3 normal = normalize((bone.Normal * vec4(unpackNormal(PackedNormal), 0.0)).xyz);
    int packedLight = bone.Meta.x;
    hiddenBone = bone.Meta.y;

    gl_Position = ProjMat * ModelViewMat * worldPosition;

    sphericalVertexDistance = fog_spherical_distance(worldPosition.xyz);
    cylindricalVertexDistance = fog_cylindrical_distance(worldPosition.xyz);
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, normal, vec4(1.0));
    ivec2 lightUv = ivec2(packedLight & 65535, (packedLight >> 16) & 65535) / 16;
    lightMapColor = texelFetch(Sampler2, lightUv, 0);
    overlayColor = vec4(0.0);
    texCoord0 = UV0;
}
