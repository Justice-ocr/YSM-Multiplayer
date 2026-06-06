package com.elfmcys.yesstevemodel.geckolib3.geo;

import com.elfmcys.yesstevemodel.NativeLibLoader;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLCapabilities;

final class NativeGpuGlCapability {
    private static boolean checked;
    private static boolean available;
    private static String reason = "not checked";

    private NativeGpuGlCapability() {
    }

    static boolean isAvailable() {
        if (!checked) {
            check();
        }
        return available;
    }

    static String reason() {
        if (!checked) {
            check();
        }
        return reason;
    }

    private static synchronized void check() {
        if (checked) {
            return;
        }
        checked = true;
        if (!NativeLibLoader.isLoaded()) {
            reason = "native ysm-core not loaded";
            return;
        }
        String osName = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        if (osName.contains("mac") || osName.contains("darwin")) {
            reason = "macOS OpenGL does not expose the required SSBO path";
            return;
        }
        try {
            RenderSystem.assertOnRenderThread();
            GLCapabilities caps = GL.getCapabilities();
            String glVersion = GL11.glGetString(GL11.GL_VERSION);
            boolean hasSsbo = caps.OpenGL43 || caps.GL_ARB_shader_storage_buffer_object;
            boolean hasIfaceQuery = caps.OpenGL43 || caps.GL_ARB_program_interface_query;
            boolean hasLayoutBinding = caps.OpenGL42 || caps.GL_ARB_shading_language_420pack;
            boolean hasExplicitAttrib = caps.OpenGL33 || caps.GL_ARB_explicit_attrib_location;
            boolean hasPackedNormal = caps.OpenGL33 || caps.GL_ARB_vertex_type_2_10_10_10_rev;
            if (!caps.OpenGL30) {
                reason = "OpenGL 3.0 not supported (" + glVersion + ")";
                return;
            }
            if (!hasSsbo || !hasIfaceQuery || !hasLayoutBinding || !hasExplicitAttrib || !hasPackedNormal) {
                reason = "required OpenGL extensions are missing (" + glVersion + ")";
                return;
            }
            available = true;
            reason = "ok (" + glVersion + ")";
        } catch (Throwable throwable) {
            reason = "OpenGL capability check failed: " + throwable.getMessage();
        }
    }
}
