package com.aiyaapp.aiya.GPUImageCustomFilter;

import com.aiyaapp.aiya.GPUImage.AYGLProgram;
import com.aiyaapp.aiya.GPUImage.AYGPUImageConstants;
import com.aiyaapp.aiya.GPUImage.AYGPUImageEGLContext;
import com.aiyaapp.aiya.GPUImage.AYGPUImageFilter;
import com.aiyaapp.aiya.GPUImage.AYGPUImageFramebuffer;

import java.nio.Buffer;

import static android.opengl.GLES20.GL_COLOR_BUFFER_BIT;
import static android.opengl.GLES20.GL_FLOAT;
import static android.opengl.GLES20.GL_TEXTURE2;
import static android.opengl.GLES20.GL_TEXTURE_2D;
import static android.opengl.GLES20.GL_TRIANGLE_STRIP;
import static android.opengl.GLES20.glActiveTexture;
import static android.opengl.GLES20.glBindTexture;
import static android.opengl.GLES20.glClear;
import static android.opengl.GLES20.glClearColor;
import static android.opengl.GLES20.glDisableVertexAttribArray;
import static android.opengl.GLES20.glDrawArrays;
import static android.opengl.GLES20.glEnableVertexAttribArray;
import static android.opengl.GLES20.glUniform1i;
import static android.opengl.GLES20.glUniformMatrix4fv;
import static android.opengl.GLES20.glVertexAttribPointer;

/**
 * Created by 汪洋 on 2019/4/18.
 * Copyright © 2019年 汪洋. All rights reserved.
 */
public class AYGPUImageBrightnessFilter extends AYGPUImageFilter {

    public static final String kAYGPUImageColorMatrixFragmentShaderString = "" +
            "varying highp vec2 textureCoordinate;\n" +
            "\n" +
            "uniform sampler2D inputImageTexture;\n" +
            "\n" +
            "uniform lowp mat4 colorMatrix;\n" +
            "\n" +
            "void main()\n" +
            "{\n" +
            "  lowp vec4 textureColor = texture2D(inputImageTexture, textureCoordinate);\n" +
            "  lowp vec4 outputColor = textureColor * colorMatrix;\n" +
            "  \n" +
            "  gl_FragColor = outputColor;\n" +
            "}";

    private float[] colorMatrix = {
            1.f, 0.f, 0.f, 0.f,
            0.f, 1.f, 0.f, 0.f,
            0.f, 0.f, 1.f, 0.f,
            0.f, 0.f, 0.f, 1.f};

    private int colorMatrixUniform;

    private float brightnessIntensity = 1.0f;

    public AYGPUImageBrightnessFilter(AYGPUImageEGLContext context) {
        super(context);
        context.syncRunOnRenderThread(new Runnable() {
            @Override
            public void run() {
                filterProgram = new AYGLProgram(kAYGPUImageVertexShaderString, kAYGPUImageColorMatrixFragmentShaderString);
                filterProgram.link();

                filterPositionAttribute = filterProgram.attributeIndex("position");
                filterTextureCoordinateAttribute = filterProgram.attributeIndex("inputTextureCoordinate");
                filterInputTextureUniform = filterProgram.uniformIndex("inputImageTexture");
                colorMatrixUniform = filterProgram.uniformIndex("colorMatrix");
                filterProgram.use();
            }
        });
    }

    @Override
    protected void renderToTexture(final Buffer vertices, final Buffer textureCoordinates) {
        context.syncRunOnRenderThread(new Runnable() {
            @Override
            public void run() {
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
                glBindTexture(GL_TEXTURE_2D, firstInputFramebuffer.texture[0]);

                glUniform1i(filterInputTextureUniform, 2);

                glUniformMatrix4fv(colorMatrixUniform, 1, false, colorMatrix, 0);

                glEnableVertexAttribArray(filterPositionAttribute);
                glEnableVertexAttribArray(filterTextureCoordinateAttribute);

                glVertexAttribPointer(filterPositionAttribute, 2, GL_FLOAT, false, 0, vertices);
                glVertexAttribPointer(filterTextureCoordinateAttribute, 2, GL_FLOAT, false, 0, textureCoordinates);

                glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

                glDisableVertexAttribArray(filterPositionAttribute);
                glDisableVertexAttribArray(filterTextureCoordinateAttribute);
            }
        });
    }

    public void setBrightnessIntensity(float intensity) {
        brightnessIntensity = intensity;

        processBrightnessAndSaturation();
    }

    private void processBrightnessAndSaturation() {
        // reset
        colorMatrix = new float[]{
                1.f, 0.f, 0.f, 0.f,
                0.f, 1.f, 0.f, 0.f,
                0.f, 0.f, 1.f, 0.f,
                0.f, 0.f, 0.f, 1.f};

        // 处理亮度
        float intensity = brightnessIntensity;
        float mmat[] = new float[16];

        int idx = 0;
        mmat[idx++] = intensity;
        mmat[idx++] = 0.0f;
        mmat[idx++] = 0.0f;
        mmat[idx++] = 0.0f;

        mmat[idx++] = 0.0f;
        mmat[idx++] = intensity;
        mmat[idx++] = 0.0f;
        mmat[idx++] = 0.0f;

        mmat[idx++] = 0.0f;
        mmat[idx++] = 0.0f;
        mmat[idx++] = intensity;
        mmat[idx++] = 0.0f;

        mmat[idx++] = 0.0f;
        mmat[idx++] = 0.0f;
        mmat[idx++] = 0.0f;
        mmat[idx] = 1.0f;

        colorMatrix = AYGPUImageConstants.matrix4fvMult(mmat, colorMatrix);
    }
}
