package io.github.n2ray.sall;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.opengl.EGL14;
import android.opengl.GLES20;

final class VideoSizeLimits {
    record Result(boolean textureSafe, boolean hardwareSupported) { }
    private static int cachedTextureLimit;

    static Result check(int width, int height) {
        int texture = textureLimit();
        boolean safe = textureFits(width, height, texture);
        boolean hardwareFound = false, supported = false;
        try {
            for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
                try {
                    if (info.isEncoder() || info.isAlias() || !info.isHardwareAccelerated()) continue;
                    MediaCodecInfo.VideoCapabilities caps = info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_HEVC).getVideoCapabilities();
                    hardwareFound = true;
                    int wa = caps.getWidthAlignment(), ha = caps.getHeightAlignment();
                    int w = (width + wa - 1) / wa * wa, h = (height + ha - 1) / ha * ha;
                    if (caps.isSizeSupported(w, h)) supported = true;
                } catch (IllegalArgumentException | IllegalStateException ignored) { }
            }
        } catch (RuntimeException ignored) { }
        return new Result(safe, !hardwareFound || supported);
    }

    static boolean textureFits(int width, int height, int texture) {
        // shortcut: allow 128 pixels of driver stride padding; revisit if a driver needs more.
        return width > 0 && height > 0 && width <= texture - 128 && height <= texture - 128;
    }

    private static synchronized int textureLimit() {
        if (cachedTextureLimit != 0) return cachedTextureLimit;
        return cachedTextureLimit = queryTextureLimit();
    }

    private static int queryTextureLimit() {
        android.opengl.EGLDisplay display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (display == EGL14.EGL_NO_DISPLAY) return 2048;
        android.opengl.EGLContext context = EGL14.EGL_NO_CONTEXT;
        android.opengl.EGLSurface surface = EGL14.EGL_NO_SURFACE;
        try {
            if (!EGL14.eglInitialize(display, new int[1], 0, new int[1], 0)) return 2048;
            android.opengl.EGLConfig[] configs = new android.opengl.EGLConfig[1]; int[] count = new int[1];
            int[] attributes = {EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_NONE};
            if (!EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) || count[0] == 0) return 2048;
            context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE}, 0);
            surface = EGL14.eglCreatePbufferSurface(display, configs[0], new int[]{EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE}, 0);
            if (context == EGL14.EGL_NO_CONTEXT || surface == EGL14.EGL_NO_SURFACE || !EGL14.eglMakeCurrent(display, surface, surface, context)) return 2048;
            int[] texture = new int[1], render = new int[1];
            GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, texture, 0);
            GLES20.glGetIntegerv(GLES20.GL_MAX_RENDERBUFFER_SIZE, render, 0);
            return texture[0] > 0 && render[0] > 0 ? Math.min(texture[0], render[0]) : 2048;
        } catch (RuntimeException ignored) { return 2048; }
        finally {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface);
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context);
        }
    }
}
