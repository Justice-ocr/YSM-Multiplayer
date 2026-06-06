package com.elfmcys.yesstevemodel.geckolib3.geo;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL43;

final class NativeGpuGlShader {
    static final int BONE_SSBO_BINDING = 0;

    private static int program;
    private static int locProj = -1;
    private static int locColor = -1;
    private static int locOverlay = -1;
    private static int locFogStart = -1;
    private static int locFogEnd = -1;
    private static int locFogColor = -1;
    private static int locFogShape = -1;
    private static int locLight0 = -1;
    private static int locLight1 = -1;
    private static int locAlphaMode = -1;
    private static boolean failed;

    private NativeGpuGlShader() {
    }

    static synchronized boolean ensureCompiled() {
        if (program != 0) {
            return true;
        }
        if (failed) {
            return false;
        }
        RenderSystem.assertOnRenderThread();
        try {
            int vertex = NativeGpuGlShaderUtil.compileShaderFromResource(GL20.GL_VERTEX_SHADER, "/ysm_native_gpu_skin.vsh");
            int fragment = NativeGpuGlShaderUtil.compileShaderFromResource(GL20.GL_FRAGMENT_SHADER, "/ysm_native_gpu_skin.fsh");
            int linked = NativeGpuGlShaderUtil.linkProgramWith(programId -> {
                GL20.glBindAttribLocation(programId, 0, "a_position");
                GL20.glBindAttribLocation(programId, 1, "a_uv");
                GL20.glBindAttribLocation(programId, 2, "a_normal");
                GL20.glBindAttribLocation(programId, 3, "a_boneId");
                GL20.glBindAttribLocation(programId, 4, "a_cullable");
            }, vertex, fragment);

            int ssboBlock = GL43.glGetProgramResourceIndex(linked, GL43.GL_SHADER_STORAGE_BLOCK, "BoneBlock");
            if (ssboBlock != GL43.GL_INVALID_INDEX) {
                GL43.glShaderStorageBlockBinding(linked, ssboBlock, BONE_SSBO_BINDING);
            }

            locProj = GL20.glGetUniformLocation(linked, "u_proj");
            locColor = GL20.glGetUniformLocation(linked, "u_color");
            locOverlay = GL20.glGetUniformLocation(linked, "u_packedOverlay");
            locFogStart = GL20.glGetUniformLocation(linked, "u_fogStart");
            locFogEnd = GL20.glGetUniformLocation(linked, "u_fogEnd");
            locFogColor = GL20.glGetUniformLocation(linked, "u_fogColor");
            locFogShape = GL20.glGetUniformLocation(linked, "u_fogShape");
            locLight0 = GL20.glGetUniformLocation(linked, "u_light0");
            locLight1 = GL20.glGetUniformLocation(linked, "u_light1");
            locAlphaMode = GL20.glGetUniformLocation(linked, "u_alphaMode");

            GL20.glUseProgram(linked);
            setSampler(linked, "Sampler0", 0);
            setSampler(linked, "Sampler1", 1);
            setSampler(linked, "Sampler2", 2);
            GL20.glUseProgram(0);
            program = linked;
            return true;
        } catch (Throwable throwable) {
            failed = true;
            YesSteveModel.LOGGER.warn("[YSM GPU] Failed to compile GL GPU skin shader; falling back to native/Java renderer", throwable);
            return false;
        }
    }

    static int program() {
        return program;
    }

    static int locProj() {
        return locProj;
    }

    static int locColor() {
        return locColor;
    }

    static int locOverlay() {
        return locOverlay;
    }

    static int locFogStart() {
        return locFogStart;
    }

    static int locFogEnd() {
        return locFogEnd;
    }

    static int locFogColor() {
        return locFogColor;
    }

    static int locFogShape() {
        return locFogShape;
    }

    static int locLight0() {
        return locLight0;
    }

    static int locLight1() {
        return locLight1;
    }

    static int locAlphaMode() {
        return locAlphaMode;
    }

    private static void setSampler(int programId, String name, int unit) {
        int location = GL20.glGetUniformLocation(programId, name);
        if (location >= 0) {
            GL20.glUniform1i(location, unit);
        }
    }
}
