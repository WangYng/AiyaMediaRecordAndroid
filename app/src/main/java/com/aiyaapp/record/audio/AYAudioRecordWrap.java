package com.aiyaapp.record.audio;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.os.SystemClock;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by 汪洋 on 2020/2/8.
 * Copyright © 2019年 汪洋. All rights reserved.
 */
public class AYAudioRecordWrap {

    private AYAudioRecordListener audioRecordListener;

    private AudioRecord audioRecord;
    private int bufferSize;

    private boolean isStop;

    private Lock lock = new ReentrantLock();

    private ExecutorService singleThreadPool = Executors.newSingleThreadExecutor();

    public AYAudioRecordWrap(AudioRecord audioRecord, int bufferSize) {
        this.audioRecord = audioRecord;
        this.bufferSize = bufferSize;
    }

    public void startRecording() {
        audioRecord.startRecording();
        isStop = false;

        new Thread() {
            @Override
            public void run() {
                final ByteBuffer audioBuffer = ByteBuffer.allocateDirect(bufferSize);

                long timestamp = 0;

                while (true) {

                    lock.lock();

                    if (isStop) {
                        lock.unlock();
                        return;
                    }
                    audioBuffer.clear();

                    int readSize = audioRecord.read(audioBuffer, bufferSize);
                    if (readSize != AudioRecord.ERROR_INVALID_OPERATION) {

                        int perframeSize = 1;
                        if (audioRecord.getAudioFormat() == AudioFormat.ENCODING_PCM_FLOAT) {
                            perframeSize = 4;
                        } else if (audioRecord.getAudioFormat() == AudioFormat.ENCODING_PCM_16BIT) {
                            perframeSize = 2;
                        } else if (audioRecord.getAudioFormat() == AudioFormat.ENCODING_PCM_8BIT) {
                            perframeSize = 1;
                        }

                        float preFrameSize = (float) audioRecord.getChannelCount() * (float) perframeSize * (float) audioRecord.getSampleRate();

                        float timeInterval = readSize / preFrameSize;

                        timestamp = (long) (timestamp + (timeInterval * 1000 * 1000));

                        if (audioRecordListener != null) {
                            final long finalTimestamp = timestamp;
                            singleThreadPool.execute(new Runnable() {
                                @Override
                                public void run() {
                                    audioRecordListener.audioRecordOutput(audioBuffer, finalTimestamp);
                                }
                            });
                        }
                    }

                    lock.unlock();

                    SystemClock.sleep(10);
                }
            }
        }.start();
    }

    public void stop() {
        lock.lock();

        audioRecord.stop();
        isStop = true;

        lock.unlock();
    }

    public void setAudioRecordListener(AYAudioRecordListener audioRecordListener) {
        this.audioRecordListener = audioRecordListener;
    }
}

