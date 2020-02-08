package com.aiyaapp.aiya;

import android.content.Context;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.aiyaapp.aiya.GPUImage.AYGLProgram;
import com.aiyaapp.aiya.GPUImage.AYGPUImageConstants;
import com.aiyaapp.aiya.GPUImage.AYGPUImageEGLContext;
import com.aiyaapp.aiya.GPUImage.AYGPUImageFilter;

import java.nio.Buffer;

import static android.opengl.GLES20.GL_COLOR_BUFFER_BIT;
import static android.opengl.GLES20.GL_FLOAT;
import static android.opengl.GLES20.GL_FRAMEBUFFER;
import static android.opengl.GLES20.GL_TEXTURE2;
import static android.opengl.GLES20.GL_TEXTURE_2D;
import static android.opengl.GLES20.GL_TRIANGLE_STRIP;
import static android.opengl.GLES20.glActiveTexture;
import static android.opengl.GLES20.glBindFramebuffer;
import static android.opengl.GLES20.glBindTexture;
import static android.opengl.GLES20.glClear;
import static android.opengl.GLES20.glClearColor;
import static android.opengl.GLES20.glDisableVertexAttribArray;
import static android.opengl.GLES20.glDrawArrays;
import static android.opengl.GLES20.glEnableVertexAttribArray;
import static android.opengl.GLES20.glUniform1i;
import static android.opengl.GLES20.glVertexAttribPointer;
import static android.opengl.GLES20.glViewport;

/**
 * Created by 汪洋 on 2019/2/11.
 * Copyright © 2019年 汪洋. All rights reserved.
 */
public class AYPreviewView extends SurfaceView implements SurfaceHolder.Callback {

    private AYGPUImageEGLContext eglContext;

    private int boundingWidth;
    private int boundingHeight;

    private AYGLProgram filterProgram;

    private int filterPositionAttribute, filterTextureCoordinateAttribute;
    private int filterInputTextureUniform;

    private Buffer textureCoordinates = AYGPUImageConstants.floatArrayToBuffer(AYGPUImageConstants.noRotationTextureCoordinates);

    private AYGPUImageConstants.AYGPUImageContentMode contentMode = AYGPUImageConstants.AYGPUImageContentMode.kAYGPUImageScaleAspectFit;

    public AYPreviewView(Context context) {
        super(context);
        commonInit();
    }

    public AYPreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        commonInit();
    }

    public AYPreviewView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        commonInit();
    }

    private void commonInit() {
        getHolder().addCallback(this);
    }

    public void setEglContext(AYGPUImageEGLContext eglContext) {
        this.eglContext = eglContext;
    }

    /**
     * 设置窗口缩放方式
     */
    public void setContentMode(AYGPUImageConstants.AYGPUImageContentMode contentMode) {
        this.contentMode = contentMode;
    }

    /**
     * 渲染纹理图像到surface上
     */
    public void render(final int texture, final int width, final int height) {
        eglContext.syncRunOnRenderThread(new Runnable() {
            @Override
            public void run() {
                eglContext.bindEGLWindow(AYPreviewView.this);
                eglContext.makeCurrent();

                filterProgram.use();

                glBindFramebuffer(GL_FRAMEBUFFER, 0);
                glViewport(0, 0, boundingWidth, boundingHeight);

                glClearColor(0, 0, 0, 0);
                glClear(GL_COLOR_BUFFER_BIT);

                glActiveTexture(GL_TEXTURE2);
                glBindTexture(GL_TEXTURE_2D, texture);

                glUniform1i(filterInputTextureUniform, 2);

                PointF insetSize = AYGPUImageConstants.getAspectRatioInsideSize(new PointF(width, height), new PointF(boundingWidth, boundingHeight));

                float widthScaling = 0.0f, heightScaling = 0.0f;

                switch (contentMode) {
                    case kAYGPUImageScaleToFill:
                        widthScaling = 1.0f;
                        heightScaling = 1.0f;
                        break;
                    case kAYGPUImageScaleAspectFit:
                        widthScaling = insetSize.x / boundingWidth;
                        heightScaling = insetSize.y / boundingHeight;
                        break;
                    case kAYGPUImageScaleAspectFill:
                        widthScaling = boundingHeight / insetSize.y;
                        heightScaling = boundingWidth / insetSize.x;
                        break;
                }

                float squareVertices[] = new float[8];
                squareVertices[0] = -widthScaling;
                squareVertices[1] = -heightScaling;
                squareVertices[2] = widthScaling;
                squareVertices[3] = -heightScaling;
                squareVertices[4] = -widthScaling;
                squareVertices[5] = heightScaling;
                squareVertices[6] = widthScaling;
                squareVertices[7] = heightScaling;

                glEnableVertexAttribArray(filterPositionAttribute);
                glEnableVertexAttribArray(filterTextureCoordinateAttribute);

                Buffer imageVertices = AYGPUImageConstants.floatArrayToBuffer(squareVertices);

                glVertexAttribPointer(filterPositionAttribute, 2, GL_FLOAT, false, 0, imageVertices);
                glVertexAttribPointer(filterTextureCoordinateAttribute, 2, GL_FLOAT, false, 0, textureCoordinates);

                glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

                glDisableVertexAttribArray(filterPositionAttribute);
                glDisableVertexAttribArray(filterTextureCoordinateAttribute);

                eglContext.swapBuffers();
            }
        });
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        createEGLContext(holder);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        this.boundingWidth = width;
        this.boundingHeight = height;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        destroyEGLContext();
    }

    /**
     * 创建EGL 和 GLES 环境
     *
     * @param object SurfaceView、SurfaceTexture、SurfaceHolder 或 Surface
     */
    private void createEGLContext(final Object object) {
        eglContext.syncRunOnRenderThread(new Runnable() {
            @Override
            public void run() {
                eglContext.bindEGLWindow(AYPreviewView.this);
                eglContext.makeCurrent();

                filterProgram = new AYGLProgram(AYGPUImageFilter.kAYGPUImageVertexShaderString, AYGPUImageFilter.kAYGPUImagePassthroughFragmentShaderString);
                filterProgram.link();

                filterPositionAttribute = filterProgram.attributeIndex("position");
                filterTextureCoordinateAttribute = filterProgram.attributeIndex("inputTextureCoordinate");
                filterInputTextureUniform = filterProgram.uniformIndex("inputImageTexture");
                filterProgram.use();
            }
        });
    }

    /**
     * 销毁EGL 和 GLES 环境
     */
    private void destroyEGLContext() {
        eglContext.syncRunOnRenderThread(new Runnable() {
            @Override
            public void run() {
                filterProgram.destroy();
                eglContext.destroyEGLWindow(AYPreviewView.this);
            }
        });
    }
}
