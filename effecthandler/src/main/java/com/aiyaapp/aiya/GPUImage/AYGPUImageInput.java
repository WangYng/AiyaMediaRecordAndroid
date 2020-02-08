package com.aiyaapp.aiya.GPUImage;

public interface AYGPUImageInput {
    void setInputSize(int width, int height);
    void setInputFramebuffer(AYGPUImageFramebuffer newInputFramebuffer);
    void newFrameReady();
}
