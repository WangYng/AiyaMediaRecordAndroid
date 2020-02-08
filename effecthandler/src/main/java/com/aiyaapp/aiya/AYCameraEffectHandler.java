package com.aiyaapp.aiya;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.util.Log;

import com.aiyaapp.aiya.GPUImageCustomFilter.AYGPUImageBeautyFilter;
import com.aiyaapp.aiya.GPUImageCustomFilter.AYGPUImageBrightnessFilter;
import com.aiyaapp.aiya.GPUImageCustomFilter.AYGPUImageLookupFilter;
import com.aiyaapp.aiya.GPUImageCustomFilter.AYGPUImageSaturationFilter;
import com.aiyaapp.aiya.GPUImageCustomFilter.inputOutput.AYGPUImageTextureInput;
import com.aiyaapp.aiya.GPUImageCustomFilter.inputOutput.AYGPUImageTextureOutput;
import com.aiyaapp.aiya.GPUImage.AYGPUImageConstants;
import com.aiyaapp.aiya.GPUImage.AYGPUImageFilter;
import com.aiyaapp.aiya.GPUImage.AYGPUImageEGLContext;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static android.opengl.GLES20.*;

/**
 * Created by 汪洋 on 2018/12/10.
 * Copyright © 2018年 汪洋. All rights reserved.
 */
public class AYCameraEffectHandler {

    private AYGPUImageTextureInput textureInput;
    private AYGPUImageTextureOutput textureOutput;

    private AYGPUImageFilter commonInputFilter;
    private AYGPUImageFilter commonOutputFilter;

    private AYGPUImageLookupFilter lookupFilter;
    private AYGPUImageBeautyFilter beautyFilter;
    private AYGPUImageBrightnessFilter brightnessFilter;
    private AYGPUImageSaturationFilter saturationFilter;

    private boolean initCommonProcess = false;
    private boolean initProcess = false;

    private int[] bindingFrameBuffer = new int[1];
    private int[] bindingRenderBuffer = new int[1];
    private int[] viewPoint = new int[4];
    private int vertexAttribEnableArraySize = 5;
    private ArrayList<Integer> vertexAttribEnableArray = new ArrayList(vertexAttribEnableArraySize);

    private AYGPUImageEGLContext eglContext;
    private SurfaceTexture surfaceTexture;

    public AYCameraEffectHandler(final Context context, AYGPUImageEGLContext eglContext) {

        if (eglContext == null) {
            this.eglContext = new AYGPUImageEGLContext();
            if (EGL14.eglGetCurrentContext() == null) {
                surfaceTexture = new SurfaceTexture(0);
                this.eglContext.initWithEGLWindow(surfaceTexture);
            }
        } else {
            this.eglContext = eglContext;
        }

        this.eglContext.syncRunOnRenderThread(new Runnable(){
            @Override
            public void run() {
                textureInput = new AYGPUImageTextureInput(AYCameraEffectHandler.this.eglContext);
                textureOutput = new AYGPUImageTextureOutput(AYCameraEffectHandler.this.eglContext);

                commonInputFilter = new AYGPUImageFilter(AYCameraEffectHandler.this.eglContext);
                commonOutputFilter = new AYGPUImageFilter(AYCameraEffectHandler.this.eglContext);

                try {
                    Bitmap lookupBitmap = BitmapFactory.decodeStream(context.getAssets().open("lookup.png"));
                    lookupFilter = new AYGPUImageLookupFilter(AYCameraEffectHandler.this.eglContext, lookupBitmap);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                beautyFilter = new AYGPUImageBeautyFilter(AYCameraEffectHandler.this.eglContext);
                brightnessFilter = new AYGPUImageBrightnessFilter(AYCameraEffectHandler.this.eglContext);
                saturationFilter = new AYGPUImageSaturationFilter(AYCameraEffectHandler.this.eglContext);
            }
        });
    }

    public void setStyle(Bitmap lookup) {
        if (lookupFilter != null) {
            lookupFilter.setLookup(lookup);
        }
    }

    public void setIntensityOfStyle(float intensity) {
        if (lookupFilter != null) {
            lookupFilter.setIntensity(intensity);
        }
    }

    public void setIntensityOfBeauty(float intensity) {
        if (beautyFilter != null) {
            beautyFilter.setIntensity(intensity);
        }
    }

    public void setIntensityOfBrightness(float intensity) {
        if (brightnessFilter != null) {
            brightnessFilter.setBrightnessIntensity(intensity);
        }
    }

    public void setIntensityOfSaturation(float intensity) {
        if (saturationFilter != null) {
            saturationFilter.setSaturationIntensity(intensity);
        }
    }

    public void setRotateMode(AYGPUImageConstants.AYGPUImageRotationMode rotateMode) {
        this.textureInput.setRotateMode(rotateMode);

        if (rotateMode == AYGPUImageConstants.AYGPUImageRotationMode.kAYGPUImageRotateLeft) {
            rotateMode = AYGPUImageConstants.AYGPUImageRotationMode.kAYGPUImageRotateRight;
        }else if (rotateMode == AYGPUImageConstants.AYGPUImageRotationMode.kAYGPUImageRotateRight) {
            rotateMode = AYGPUImageConstants.AYGPUImageRotationMode.kAYGPUImageRotateLeft;
        }

        this.textureOutput.setRotateMode(rotateMode);
    }

    public void commonProcess() {

        List<AYGPUImageFilter> filterChainArray = new ArrayList<AYGPUImageFilter>();

        if (lookupFilter != null) {
            filterChainArray.add(lookupFilter);
        }
        if (beautyFilter != null) {
            filterChainArray.add(beautyFilter);
        }
        if (brightnessFilter != null) {
            filterChainArray.add(brightnessFilter);
        }
        if(saturationFilter != null) {
            filterChainArray.add(saturationFilter);
        }

        if (!initCommonProcess) {

            if (filterChainArray.size() > 0) {
                commonInputFilter.addTarget(filterChainArray.get(0));
                for (int x = 0; x < filterChainArray.size() - 1; x++) {
                    filterChainArray.get(x).addTarget(filterChainArray.get(x+1));
                }
                filterChainArray.get(filterChainArray.size()-1).addTarget(commonOutputFilter);

            }else {
                commonInputFilter.addTarget(commonOutputFilter);
            }

            initCommonProcess = true;
        }
    }

    public void processWithTexture(final int texture, final int width, final int height) {
        eglContext.syncRunOnRenderThread(new Runnable() {
            @Override
            public void run() {

                saveOpenGLState();

                commonProcess();

                if (!initProcess) {
                    textureInput.addTarget(commonInputFilter);
                    commonOutputFilter.addTarget(textureOutput);
                    initProcess = true;
                }

                // 设置输出的Filter
                textureOutput.setOutputWithBGRATexture(texture, width, height);

                // 设置输入的Filter, 同时开始处理纹理数据
                textureInput.processWithBGRATexture(texture, width, height);

                restoreOpenGLState();
            }
        });
    }

    public Bitmap getCurrentImage(final int width, final int height) {
        final ByteBuffer byteBuffer = ByteBuffer.allocate(width*height*4);

        eglContext.syncRunOnRenderThread(new Runnable() {
            @Override
            public void run() {
                glReadPixels(0,0,width,height,GL_RGBA, GL_UNSIGNED_BYTE, byteBuffer);
            }
        });
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(byteBuffer);
        Matrix matrix = new Matrix();
        matrix.setScale(1, -1);
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);

        return bitmap;
    }

    private void saveOpenGLState() {
        // 获取当前绑定的FrameBuffer
        glGetIntegerv(GL_FRAMEBUFFER_BINDING, bindingFrameBuffer, 0);

        // 获取当前绑定的RenderBuffer
        glGetIntegerv(GL_RENDERBUFFER_BINDING, bindingRenderBuffer, 0);

        // 获取viewpoint
        glGetIntegerv(GL_VIEWPORT, viewPoint, 0);

        // 获取顶点数据
        vertexAttribEnableArray.clear();
        for (int x = 0 ; x < vertexAttribEnableArraySize; x++) {
            int[] vertexAttribEnable = new int[1];
            glGetVertexAttribiv(x, GL_VERTEX_ATTRIB_ARRAY_ENABLED, vertexAttribEnable, 0);
            if (vertexAttribEnable[0] != 0) {
                vertexAttribEnableArray.add(x);
            }
        }
    }

    private void restoreOpenGLState() {
        // 还原当前绑定的FrameBuffer
        glBindFramebuffer(GL_FRAMEBUFFER, bindingFrameBuffer[0]);

        // 还原当前绑定的RenderBuffer
        glBindRenderbuffer(GL_RENDERBUFFER, bindingRenderBuffer[0]);

        // 还原viewpoint
        glViewport(viewPoint[0], viewPoint[1], viewPoint[2], viewPoint[3]);

        // 还原顶点数据
        for (int x = 0 ; x < vertexAttribEnableArray.size(); x++) {
            glEnableVertexAttribArray(vertexAttribEnableArray.get(x));
        }
    }

    public void destroy() {
        if (eglContext != null) {
            eglContext.syncRunOnRenderThread(new Runnable() {
                @Override
                public void run() {
                    eglContext.makeCurrent();

                    textureInput.destroy();
                    textureOutput.destroy();
                    commonInputFilter.destroy();
                    commonOutputFilter.destroy();

                    if (lookupFilter != null) {
                        lookupFilter.destroy();
                    }
                    if (beautyFilter != null) {
                        beautyFilter.destroy();
                    }
                    if (brightnessFilter != null) {
                        brightnessFilter.destroy();
                    }
                    if (saturationFilter != null) {
                        saturationFilter.destroy();
                    }

                    if (surfaceTexture != null) {
                        eglContext.destroyEGLWindow(surfaceTexture);
                        surfaceTexture.release();
                    }
                }
            });
        }
    }
}
