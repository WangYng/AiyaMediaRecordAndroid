package com.aiyaapp.record.audio;

import java.nio.ByteBuffer;

/**
 * Created by 汪洋 on 2020/2/8.
 * Copyright © 2019年 汪洋. All rights reserved.
 */
public interface AYAudioRecordListener {
    void audioRecordOutput(ByteBuffer byteBuffer, long timestamp);
}
