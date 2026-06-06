package com.elfmcys.yesstevemodel.geckolib3.geo;

import org.lwjgl.opengl.GL20;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

final class NativeGpuGlShaderUtil {
    private NativeGpuGlShaderUtil() {
    }

    static int compileShaderFromResource(int glType, String resourcePath) throws IOException {
        return compileShader(glType, loadResource(resourcePath), resourcePath);
    }

    static int linkProgramWith(IntConsumer preLink, int... shaderIds) {
        int program = GL20.glCreateProgram();
        for (int shaderId : shaderIds) {
            GL20.glAttachShader(program, shaderId);
        }
        if (preLink != null) {
            preLink.accept(program);
        }
        GL20.glLinkProgram(program);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
            String log = GL20.glGetProgramInfoLog(program);
            GL20.glDeleteProgram(program);
            for (int shaderId : shaderIds) {
                GL20.glDeleteShader(shaderId);
            }
            throw new IllegalStateException("Link failed: " + log);
        }
        for (int shaderId : shaderIds) {
            GL20.glDetachShader(program, shaderId);
            GL20.glDeleteShader(shaderId);
        }
        return program;
    }

    private static String loadResource(String path) throws IOException {
        try (InputStream in = NativeGpuGlShaderUtil.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("resource not found: " + path);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        }
    }

    private static int compileShader(int glType, String source, String name) {
        int shader = GL20.glCreateShader(glType);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException("Compile failed (" + name + "): " + log);
        }
        return shader;
    }
}
