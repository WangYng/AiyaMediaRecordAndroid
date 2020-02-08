package com.aiyaapp.record.video;

/**
 * Created by 汪洋 on 2020/2/8.
 * Copyright © 2019年 汪洋. All rights reserved.
 */
public interface AYCameraPreviewListener {
    void cameraVideoOutput(int texture, int width, int height, long timeStamp, long frameDelay);
}
