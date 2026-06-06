#version 430 core

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform int u_packedOverlay;
uniform float u_fogStart;
uniform float u_fogEnd;
uniform vec4 u_fogColor;
uniform int u_alphaMode;

in vec2 v_uv;
in vec4 v_color;
in float v_vertexDistance;
in float v_cullable;
flat in int v_packedLight;

out vec4 fragColor;

vec4 linearFog(vec4 inColor, float distance, float start, float end, vec4 fogColor) {
    if (distance <= start) {
        return inColor;
    }
    float amount = distance < end ? smoothstep(start, end, distance) : 1.0;
    return vec4(mix(inColor.rgb, fogColor.rgb, amount * fogColor.a), inColor.a);
}

void main() {
    if (u_alphaMode != 2 && v_cullable > 0.5 && !gl_FrontFacing) {
        discard;
    }

    vec4 texColor = texture(Sampler0, v_uv);
    if (texColor.a < 0.1) {
        discard;
    }
    if (u_alphaMode == 1 && texColor.a < 0.99) {
        discard;
    }
    if (u_alphaMode == 2 && texColor.a >= 0.99) {
        discard;
    }

    vec4 color = texColor * v_color;
    int overlayU = u_packedOverlay & 0xFFFF;
    int overlayV = (u_packedOverlay >> 16) & 0xFFFF;
    vec4 overlayColor = texelFetch(Sampler1, ivec2(overlayU, overlayV), 0);
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);

    int blockUv = (v_packedLight & 0xFFFF) / 16;
    int skyUv = ((v_packedLight >> 16) & 0xFFFF) / 16;
    color.rgb *= texelFetch(Sampler2, ivec2(blockUv, skyUv), 0).rgb;
    fragColor = linearFog(color, v_vertexDistance, u_fogStart, u_fogEnd, u_fogColor);
}
