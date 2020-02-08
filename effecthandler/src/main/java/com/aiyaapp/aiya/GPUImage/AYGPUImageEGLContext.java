package com.aiyaapp.aiya.GPUImage;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

import static android.opengl.EGL14.EGL_ALPHA_SIZE;
import static android.opengl.EGL14.EGL_BLUE_SIZE;
import static android.opengl.EGL14.EGL_CONTEXT_CLIENT_VERSION;
import static android.opengl.EGL14.EGL_DEFAULT_DISPLAY;
import static android.opengl.EGL14.EGL_DEPTH_SIZE;
import static android.opengl.EGL14.EGL_GREEN_SIZE;
import static android.opengl.EGL14.EGL_NONE;
import static android.opengl.EGL14.EGL_NO_CONTEXT;
import static android.opengl.EGL14.EGL_NO_SURFACE;
import static android.opengl.EGL14.EGL_OPENGL_ES2_BIT;
import static android.opengl.EGL14.EGL_RED_SIZE;
import static android.opengl.EGL14.EGL_RENDERABLE_TYPE;
import static android.opengl.EGL14.EGL_STENCIL_SIZE;
import static android.opengl.EGL14.EGL_SURFACE_TYPE;
import static android.opengl.EGL14.EGL_WINDOW_BIT;
import static android.opengl.EGL14.eglChooseConfig;
import static android.opengl.EGL14.eglCreateContext;
import static android.opengl.EGL14.eglCreateWindowSurface;
import static android.opengl.EGL14.eglDestroyContext;
import static android.opengl.EGL14.eglDestroySurface;
import static android.opengl.EGL14.eglGetDisplay;
import static android.opengl.EGL14.eglGetError;
import static android.opengl.EGL14.eglInitialize;
import static android.opengl.EGL14.eglMakeCurrent;
import static android.opengl.EGL14.eglSwapBuffers;
import static com.aiyaapp.aiya.GPUImage.AYGPUImageConstants.TAG;

public class AYGPUImageEGLContext {

    private EGLContext eglContext;

    private Map<Object, EGLWindow> eglWindowMap = new HashMap<>();
    private EGLWindow currentEGLWindow;

    private HandlerThread handlerThread;
    private Handler glesHandler;

    /**
     * 创建EGLContext, 绑定window
     */
    public boolean initWithEGLWindow(final Object nativeWindow) {

        // 初始化GL执行线程
        handlerThread = new HandlerThread("com.aiyaapp.gpuimage");
        handlerThread.start();
        glesHandler = new Handler(handlerThread.getLooper());

        // 创建EGLWindow
        final boolean[] result = new boolean[1];

        final Semaphore semaphore = new Semaphore(0);

        glesHandler.post(new Runnable() {
            @Override
            public void run() {
                result[0] = createAndBindEGLWindow(nativeWindow);
                semaphore.release();
            }
        });

        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return result[0];
    }

    private boolean createAndBindEGLWindow(Object nativeWindow) {
        EGLDisplay eglDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
        if (eglDisplay == null) {
            Log.d(TAG, "eglGetDisplay error " + eglGetError());
            return false;
        }

        int[] versions = new int[2];
        if (!eglInitialize(eglDisplay, versions, 0, versions, 1)) {
            Log.d(TAG, "eglInitialize error " + eglGetError());
            return false;
        }

        int[] attrs = {
                EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
                EGL_RED_SIZE, 8,
                EGL_GREEN_SIZE, 8,
                EGL_BLUE_SIZE, 8,
                EGL_ALPHA_SIZE, 8,
                EGL_DEPTH_SIZE, 16,
                EGL_STENCIL_SIZE, 8,
                EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!eglChooseConfig(eglDisplay, attrs, 0, configs, 0, configs.length, numConfigs, 0)) {
            Log.d(TAG, "eglChooseConfig error " + eglGetError());
            return false;
        }

        EGLSurface surface = eglCreateWindowSurface(eglDisplay, configs[0], nativeWindow, new int[]{EGL_NONE}, 0);
        if (surface == EGL_NO_SURFACE) {
            Log.d(TAG, "eglCreateWindowSurface error " + eglGetError());
            return false;
        }

        int[] contextAttrs = {
                EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL_NONE
        };
        eglContext = eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT,
                contextAttrs, 0);
        if (eglContext == EGL_NO_CONTEXT) {
            Log.d(TAG, "eglCreateContext error " + eglGetError());
            return false;
        }

        if (!eglMakeCurrent(eglDisplay, surface, surface, eglContext)) {
            Log.d(TAG, "eglMakeCurrent error " + eglGetError());
            return false;
        }

        currentEGLWindow = new EGLWindow(eglDisplay, surface);
        eglWindowMap.put(nativeWindow, currentEGLWindow);

        Log.d(TAG, "创建 eglCreateContext");
        return true;
    }

    /**
     * 绑定EGLWindow
     */
    public boolean bindEGLWindow(Object nativeWindow) {

        if (eglContext == null) {
            Log.d(TAG, "eglContext null");
            return false;
        }

        EGLWindow eglWindow = eglWindowMap.get(nativeWindow);
        if (eglWindow != null) {

            if (currentEGLWindow == eglWindow) {
                return true;
            }

            if (!eglMakeCurrent(eglWindow.eglDisplay, eglWindow.surface, eglWindow.surface, eglContext)) {
                Log.d(TAG, "eglMakeCurrent error " + eglGetError());
                return false;
            } else {
                currentEGLWindow = eglWindow;
                return true;
            }
        }

        EGLDisplay eglDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
        if (eglDisplay == null) {
            Log.d(TAG, "eglGetDisplay error " + eglGetError());
            return false;
        }

        int[] versions = new int[2];
        if (!eglInitialize(eglDisplay, versions, 0, versions, 1)) {
            Log.d(TAG, "eglInitialize error " + eglGetError());
            return false;
        }

        int[] attrs = {
                EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
                EGL_RED_SIZE, 8,
                EGL_GREEN_SIZE, 8,
                EGL_BLUE_SIZE, 8,
                EGL_ALPHA_SIZE, 8,
                EGL_DEPTH_SIZE, 16,
                EGL_STENCIL_SIZE, 8,
                EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!eglChooseConfig(eglDisplay, attrs, 0, configs, 0, configs.length, numConfigs, 0)) {
            Log.d(TAG, "eglChooseConfig error " + eglGetError());
            return false;
        }

        EGLSurface surface = eglCreateWindowSurface(eglDisplay, configs[0], nativeWindow, new int[]{EGL_NONE}, 0);
        if (surface == EGL_NO_SURFACE) {
            Log.d(TAG, "eglCreateWindowSurface error " + eglGetError());
            return false;
        }

        if (!eglMakeCurrent(eglDisplay, surface, surface, eglContext)) {
            Log.d(TAG, "eglMakeCurrent error " + eglGetError());
            return false;
        }

        currentEGLWindow = new EGLWindow(eglDisplay, surface);
        eglWindowMap.put(nativeWindow, currentEGLWindow);

        Log.d(TAG, "绑定 eglCreateContext");
        return true;
    }

    public boolean makeCurrent() {
        if (currentEGLWindow != null && eglContext != null) {
            return eglMakeCurrent(currentEGLWindow.eglDisplay, currentEGLWindow.surface, currentEGLWindow.surface, eglContext);
        } else {
            return false;
        }
    }

    public boolean swapBuffers() {
        if (currentEGLWindow != null) {
            return eglSwapBuffers(currentEGLWindow.eglDisplay, currentEGLWindow.surface);
        } else {
            return false;
        }
    }

    public void destroyEGLWindow(Object nativeWindow) {
        EGLWindow eglWindow = eglWindowMap.get(nativeWindow);

        if (eglWindow == null) {
            return;
        }

        if (eglWindow.eglDisplay != null && eglWindow.surface != null) {
            eglDestroySurface(eglWindow.eglDisplay, eglWindow.surface);
        }

        if (eglWindowMap.keySet().size() == 1) {
            if (eglContext != null) {
                eglDestroyContext(eglWindow.eglDisplay, eglContext);
                Log.d(TAG, "销毁 eglContext");
            }

            if (eglWindow.eglDisplay != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    eglMakeCurrent(eglWindow.eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
                }
            }

            if (handlerThread != null) {
                handlerThread.quit();
            }
        }

        eglWindowMap.remove(nativeWindow);
    }

    public void setTimeStemp(long time) {
        if (currentEGLWindow != null) {
            EGLExt.eglPresentationTimeANDROID(currentEGLWindow.eglDisplay, currentEGLWindow.surface, time);
        }
    }

    public void syncRunOnRenderThread(final Runnable runnable) {

        if (eglContext != null && glesHandler != null) {
            Thread thread = Thread.currentThread();

            if (thread == glesHandler.getLooper().getThread()) {
                runnable.run();
            } else {

                final Semaphore semaphore = new Semaphore(0);

                glesHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        runnable.run();
                        semaphore.release();
                    }
                });

                try {
                    semaphore.acquire();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } else {
            runnable.run();
        }
    }

    // EGL环境
    private static class EGLWindow {
        EGLDisplay eglDisplay;
        EGLSurface surface;

        EGLWindow(EGLDisplay eglDisplay, EGLSurface surface) {
            this.eglDisplay = eglDisplay;
            this.surface = surface;
        }
    }
}