package com.aiyaapp.record.video;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.GLES11Ext;
import android.util.Log;

import com.aiyaapp.aiya.GPUImage.AYGLProgram;
import com.aiyaapp.aiya.GPUImage.AYGPUImageConstants;
import com.aiyaapp.aiya.GPUImage.AYGPUImageEGLContext;
import com.aiyaapp.aiya.GPUImage.AYGPUImageFilter;
import com.aiyaapp.aiya.GPUImage.AYGPUImageFramebuffer;

import java.io.IOException;
import java.nio.Buffer;
import java.util.concurrent.locks.ReentrantLock;

import static android.opengl.GLES20.GL_COLOR_BUFFER_BIT;
import static android.opengl.GLES20.GL_FLOAT;
import static android.opengl.GLES20.GL_TEXTURE2;
import static android.opengl.GLES20.GL_TEXTURE_2D;
import static android.opengl.GLES20.GL_TEXTURE_MAG_FILTER;
import static android.opengl.GLES20.GL_TEXTURE_MIN_FILTER;
import static android.opengl.GLES20.GL_TEXTURE_WRAP_S;
import static android.opengl.GLES20.GL_TEXTURE_WRAP_T;
import static android.opengl.GLES20.GL_TRIANGLE_STRIP;
import static android.opengl.GLES20.glActiveTexture;
import static android.opengl.GLES20.glBindTexture;
import static android.opengl.GLES20.glClear;
import static android.opengl.GLES20.glClearColor;
import static android.opengl.GLES20.glDisableVertexAttribArray;
import static android.opengl.GLES20.glDrawArrays;
import static android.opengl.GLES20.glEnableVertexAttribArray;
import static android.opengl.GLES20.glFinish;
import static android.opengl.GLES20.glGenTextures;
import static android.opengl.GLES20.glTexParameteri;
import static android.opengl.GLES20.glUniform1i;
import static android.opengl.GLES20.glVertexAttribPointer;

/**
 * Created by 汪洋 on 2020/2/8.
 * Copyright © 2019年 汪洋. All rights reserved.
 */
public class AYCameraPreviewWrap implements SurfaceTexture.OnFrameAvailableListener {
    public static final String TAG = "AYCameraPreviewWrap";

    public static final String kAYOESTextureFragmentShader = "" +
            "#extension GL_OES_EGL_image_external : require\n" +
            "\n" +
            "varying highp vec2 textureCoordinate;\n" +
            "\n" +
            "uniform samplerExternalOES inputImageTexture;\n" +
            "\n" +
            "void main() {\n" +
            "    gl_FragColor = texture2D(inputImageTexture, textureCoordinate);\n" +
            "}";

    private Camera mCamera;

    private AYGPUImageEGLContext eglContext;

    private SurfaceTexture surfaceTexture;

    private int oesTexture;

    private AYCameraPreviewListener previewListener;

    private AYGPUImageFramebuffer outputFramebuffer;

    private AYGPUImageConstants.AYGPUImageRotationMode rotateMode = AYGPUImageConstants.AYGPUImageRotationMode.kAYGPUImageNoRotation;

    private int inputWidth;
    private int inputHeight;

    private AYGLProgram filterProgram;

    private int filterPositionAttribute, filterTextureCoordinateAttribute;
    private int filterInputTextureUniform;

    private Buffer imageVertices = AYGPUImageConstants.floatArrayToBuffer(AYGPUImageConstants.imageVertices);
    private Buffer textureCoordinates = AYGPUImageConstants.floatArrayToBuffer(AYGPUImageConstants.noRotationTextureCoordinates);

    private ReentrantLock previewLock = new ReentrantLock(true);
    private boolean isStopPreview = true;

    public AYCameraPreviewWrap(Camera camera, AYGPUImageEGLContext eglContext) {
        mCamera = camera;
        this.eglContext = eglContext;
        init();
    }

    public void setCamera(Camera camera) {
        mCamera = camera;
        init();
    }

    public void setCameraSize(int width, int height) {
        try {
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setPreviewSize(width, height);
            mCamera.setParameters(parameters);
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "设置预览比例出错: " + e.getMessage());
        }
    }

    private void init() {
        eglContext.syncRunOnRenderThread(new Runnable() {
            @Override
            public void run() {
                oesTexture = createOESTextureID();
                surfaceTexture = new SurfaceTexture(oesTexture);
                surfaceTexture.setOnFrameAvailableListener(AYCameraPreviewWrap.this);

                filterProgram = new AYGLProgram(AYGPUImageFilter.kAYGPUImageVertexShaderString, kAYOESTextureFragmentShader);
                filterProgram.link();

                filterPositionAttribute = filterProgram.attributeIndex("position");
                filterTextureCoordinateAttribute = filterProgram.attributeIndex("inputTextureCoordinate");
                filterInputTextureUniform = filterProgram.uniformIndex("inputImageTexture");
                filterProgram.use();
            }
        });
    }

    public void startPreview() {
        boolean isLocked = previewLock.isLocked();
        if (!isLocked) {
            previewLock.lock();
        }

        try {
            mCamera.setPreviewTexture(surfaceTexture);
        } catch (IOException ignored) {
        }

        Camera.Size s = mCamera.getParameters().getPreviewSize();
        inputWidth = s.width;
        inputHeight = s.height;

        setRotateMode(rotateMode);

        mCamera.startPreview();

        isStopPreview = false;
        if (!isLocked) {
            previewLock.unlock();
        }
    }

    public void stopPreview() {
        boolean isLocked = previewLock.isLocked();
        if (!isLocked) {
            previewLock.lock();
        }

        destroy();
        mCamera.stopPreview();

        isStopPreview = true;
        if (!isLocked) {
            previewLock.unlock();
        }
    }

    @Override
    public void onFrameAvailable(final SurfaceTexture surfaceTexture) {
        previewLock.lock();
        if (isStopPreview) {
            previewLock.unlock();
            return;
        }

        eglContext.syncRunOnRenderThread(new Runnable() {
            @Override
            public void run() {
                surfaceTexture.updateTexImage();

                long timestamp = surfaceTexture.getTimestamp();

                glFinish();

                // 因为在shader中处理oes纹理需要使用到扩展类型, 必须要先转换为普通纹理再传给下一级
                renderToFramebuffer(oesTexture);

                if (previewListener != null) {
                    previewListener.cameraVideoOutput(outputFramebuffer.texture[0], inputWidth, inputHeight, timestamp, surfaceTexture.getTimestamp() - timestamp);
                }
            }
        });

        previewLock.unlock();
    }

    private void renderToFramebuffer(int oesTexture) {
        filterProgram.use();

        if (outputFramebuffer != null) {
            if (inputWidth != outputFramebuffer.width || inputHeight != outputFramebuffer.height) {
                outputFramebuffer.destroy();
                outputFramebuffer = null;
            }
        }

        if (outputFramebuffer == null) {
            outputFramebuffer = new AYGPUImageFramebuffer(inputWidth, inputHeight);
        }

        outputFramebuffer.activateFramebuffer();

        glClearColor(0, 0, 0, 0);
        glClear(GL_COLOR_BUFFER_BIT);

        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture);

        glUniform1i(filterInputTextureUniform, 2);

        glEnableVertexAttribArray(filterPositionAttribute);
        glEnableVertexAttribArray(filterTextureCoordinateAttribute);

        glVertexAttribPointer(filterPositionAttribute, 2, GL_FLOAT, false, 0, imageVertices);
        glVertexAttribPointer(filterTextureCoordinateAttribute, 2, GL_FLOAT, false, 0, AYGPUImageConstants.floatArrayToBuffer(AYGPUImageConstants.textureCoordinatesForRotation(rotateMode)));

        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

        glDisableVertexAttribArray(filterPositionAttribute);
        glDisableVertexAttribArray(filterTextureCoordinateAttribute);
    }

    public void setPreviewListener(AYCameraPreviewListener previewListener) {
        this.previewListener = previewListener;
    }

    public void setRotateMode(AYGPUImageConstants.AYGPUImageRotationMode rotateMode) {
        this.rotateMode = rotateMode;

        if (AYGPUImageConstants.needExchangeWidthAndHeightWithRotation(rotateMode)) {
            int temp = inputWidth;
            inputWidth = inputHeight;
            inputHeight = temp;
        }
    }

    private int createOESTextureID() {
        int[] texture = new int[1];
        glGenTextures(1, texture, 0);
        glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture[0]);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_TEXTURE_MIN_FILTER);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_TEXTURE_MAG_FILTER);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_TEXTURE_WRAP_S);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_TEXTURE_WRAP_T);

        return texture[0];
    }

    public void destroy() {
        eglContext.syncRunOnRenderThread(new Runnable() {
            @Override
            public void run() {
                filterProgram.destroy();

                if (outputFramebuffer != null) {
                    outputFramebuffer.destroy();
                }
            }
        });
    }

    /**
     * 切换闪光灯模式
     */
    public void switchCameraFlashMode() {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            String flashMode = parameters.getFlashMode();

            if (Camera.Parameters.FLASH_MODE_TORCH.equals(flashMode)) {
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                Log.d(TAG, "关闭闪光灯");
            } else if (Camera.Parameters.FLASH_MODE_OFF.equals(flashMode)) {
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                Log.d(TAG, "打开闪光灯");
            }
            mCamera.setParameters(parameters);
        }
    }
}

