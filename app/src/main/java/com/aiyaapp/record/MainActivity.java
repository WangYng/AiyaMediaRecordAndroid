package com.aiyaapp.record;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.aiyaapp.aiya.AYCameraEffectHandler;
import com.aiyaapp.aiya.AYPreviewView;
import com.aiyaapp.aiya.GPUImage.AYGPUImageConstants;
import com.aiyaapp.aiya.GPUImage.AYGPUImageEGLContext;
import com.aiyaapp.record.audio.AYAudioRecordListener;
import com.aiyaapp.record.audio.AYAudioRecordWrap;
import com.aiyaapp.record.codec.AYMediaCodec;
import com.aiyaapp.record.codec.AYMediaCodecHelper;
import com.aiyaapp.record.video.AYCameraPreviewListener;
import com.aiyaapp.record.video.AYCameraPreviewWrap;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import static com.aiyaapp.record.codec.AYMediaCodecHelper.getAvcSupportedFormatInfo;

/**
 * Created by 汪洋 on 2020/2/8.
 * Copyright © 2019年 汪洋. All rights reserved.
 */
public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback, AYCameraPreviewListener, AYAudioRecordListener, View.OnClickListener {

    private static final String TAG = "RecordActivity";

    // 请求权限
    int CHECK_PERMISSION_REQUEST_CODE = 1000;

    // 相机
    Camera camera;
    AYCameraPreviewWrap cameraPreviewWrap;
    int mCurrentCameraID = FRONT_CAMERA_ID;

    // 相机参数
    public static final int FRONT_CAMERA_ID = 1;
    public static final int BACK_CAMERA_ID = 0;

    // 麦克风
    AudioRecord audioRecord;
    AYAudioRecordWrap audioRecordWrap;

    // 相机处理
    AYCameraEffectHandler effectHandler;

    // 预览的surface
    AYPreviewView surfaceView;

    // 音视频硬编码
    AYMediaCodec mediaCodec;
    boolean videoCodecConfigResult = false;
    boolean audioCodecConfigResult = false;

    // 音频编码固定参数
    public static final int audioSampleRate = 16000;   //音频采样率
    public static final int audioChannel = AudioFormat.CHANNEL_IN_STEREO;   //双声道
    public static final int audioFormat = AudioFormat.ENCODING_PCM_16BIT; //音频录制格式
    public static final int audioBitrate = 128 * 1024;// default 128Kbps

    // 录制参数
    public static final int videoWidth = 1280;
    public static final int videoHeight = 720;
    public static final int videoFrameRate = 30; // 帧率
    public static final int videoBitrate = 4 * 1024 * 1024;// default 4Mbps
    public static final int videoIFrameInterval = 1; // GOP

    private String path;
    private AYGPUImageEGLContext eglContext;
    private long videoFrameDelayTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        // 创建EGLContext
        eglContext = new AYGPUImageEGLContext();
        eglContext.initWithEGLWindow(new SurfaceTexture(0));

        // 预览View
        surfaceView = findViewById(R.id.preview);
        surfaceView.setEglContext(eglContext);
        surfaceView.setContentMode(AYGPUImageConstants.AYGPUImageContentMode.kAYGPUImageScaleAspectFill);
        surfaceView.getHolder().addCallback(this);

        findViewById(R.id.startBtn).setOnClickListener(this);
        findViewById(R.id.stopBtn).setOnClickListener(this);
        findViewById(R.id.switchBtn).setOnClickListener(this);

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        boolean cameraGrantResult = false;
        boolean recordAudioGrantResult = false;

        if (requestCode == CHECK_PERMISSION_REQUEST_CODE) {
            for (int x = 0; x < permissions.length; x++) {
                String permission = permissions[x];
                if (permissions[x].equals(Manifest.permission.CAMERA) && grantResults[x] == PackageManager.PERMISSION_GRANTED) {
                    cameraGrantResult = true;
                } else if (permissions[x].equals(Manifest.permission.RECORD_AUDIO) && grantResults[x] == PackageManager.PERMISSION_GRANTED) {
                    recordAudioGrantResult = true;
                }
            }

            if (cameraGrantResult && recordAudioGrantResult) {
                openHardware();
            } else {
                Toast.makeText(getBaseContext(), "权限请求失败", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * 打开硬件设备
     */
    private void openHardware() {
        // 打开前置相机
        Log.d(TAG, "打开前置相机");
        openFrontCamera();

        // 打开后置相机
//        openBackCamera();

        // 打开麦克风
        Log.d(TAG, "打开麦克风");
        int bufferSize = AudioRecord.getMinBufferSize(audioSampleRate, audioChannel, audioFormat);
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, audioSampleRate, audioChannel,
                audioFormat, bufferSize);
        if (audioRecordWrap == null) {
            audioRecordWrap = new AYAudioRecordWrap(audioRecord, bufferSize);
            audioRecordWrap.setAudioRecordListener(this);
        }
        audioRecordWrap.startRecording();
    }

    /**
     * 打开前置相机
     */
    private void openFrontCamera() {
        if (camera != null) {
            camera.release();
            camera = null;
        }
        mCurrentCameraID = FRONT_CAMERA_ID;
        camera = Camera.open(mCurrentCameraID); // TODO 省略判断是否有前置相机
        if (cameraPreviewWrap == null) {
            cameraPreviewWrap = new AYCameraPreviewWrap(camera, eglContext);
            cameraPreviewWrap.setPreviewListener(this);
        } else {
            cameraPreviewWrap.setCamera(camera);
        }
        cameraPreviewWrap.setCameraSize(1920, 1080);
        cameraPreviewWrap.setRotateMode(AYGPUImageConstants.AYGPUImageRotationMode.kAYGPUImageRotateRight); // TODO 如果画面方向不对, 修改此值
        cameraPreviewWrap.startPreview();
    }

    /**
     * 打开后置相机
     */
    private void openBackCamera() {
        if (camera != null) {
            camera.release();
            camera = null;
        }
        Log.d(TAG, "打开后置相机");
        mCurrentCameraID = BACK_CAMERA_ID;
        camera = Camera.open(mCurrentCameraID);
        if (cameraPreviewWrap == null) {
            cameraPreviewWrap = new AYCameraPreviewWrap(camera, eglContext);
            cameraPreviewWrap.setPreviewListener(this);
        } else {
            cameraPreviewWrap.setCamera(camera);
        }
        cameraPreviewWrap.setCameraSize(1920, 1080);
        cameraPreviewWrap.setRotateMode(AYGPUImageConstants.AYGPUImageRotationMode.kAYGPUImageRotateRightFlipHorizontal);
        cameraPreviewWrap.startPreview();
    }

    /**
     * 关闭硬件设备
     */
    private void closeHardware() {
        // 关闭相机
        if (camera != null) {
            Log.d(TAG, "关闭相机");
            cameraPreviewWrap.stopPreview();
            cameraPreviewWrap = null;
            camera.release();
            camera = null;
        }

        // 关闭麦克风
        if (audioRecord != null) {
            Log.d(TAG, "关闭麦克风");
            audioRecordWrap.stop();
            audioRecordWrap = null;
            audioRecord.release();
            audioRecord = null;
        }
    }

    /**
     * 相机数据回调
     */
    @Override
    public void cameraVideoOutput(int texture, int width, int height, long timeStamp, long frameDelay) {

        long startTime = SystemClock.elapsedRealtime();

        // 加入特效
        if (effectHandler != null) {
            effectHandler.processWithTexture(texture, width, height);
        }

        // 渲染到surfaceView
        surfaceView.render(texture, width, height);

        videoFrameDelayTime = SystemClock.elapsedRealtime() - startTime + frameDelay;

        // 进行视频编码
        if (mediaCodec != null && videoCodecConfigResult) {
            mediaCodec.writeImageTexture(texture, width, height, timeStamp);
        }
    }

    /**
     * 麦克风数据回调
     */
    @Override
    public void audioRecordOutput(ByteBuffer byteBuffer, long timestamp) {

        // 进行音频编码
        if (mediaCodec != null && audioCodecConfigResult) {
            mediaCodec.writePCMByteBuffer(byteBuffer, timestamp);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, CHECK_PERMISSION_REQUEST_CODE);
            return;
        }

        openHardware();

        // 初始化特效处理
        effectHandler = new AYCameraEffectHandler(getApplicationContext(), eglContext);

        try {
            // 添加滤镜
            effectHandler.setStyle(BitmapFactory.decodeStream(getApplicationContext().getAssets().open("FilterResources/filter/03桃花.JPG")));
        } catch (IOException e) {
            e.printStackTrace();
        }
        // 设置美颜程度
        effectHandler.setIntensityOfBeauty(0.8f);

        // 设置滤镜程度
        effectHandler.setIntensityOfStyle(0.8f);

        // 设置饱和度
        effectHandler.setIntensityOfSaturation(1.0f);

        // 设置亮度
        effectHandler.setIntensityOfBrightness(1.0f);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

        closeHardware();

        // 销毁特效处理
        if (effectHandler != null) {
            effectHandler.destroy();
            effectHandler = null;
        }
    }

    public void startRecord() {
        startMediaCodec();
    }

    public void stopRecord() {
        closeMediaCodec();

        // 关闭编码器标志
        videoCodecConfigResult = false;
        audioCodecConfigResult = false;

        Intent intent = new Intent(this, PlayVideoActivity.class);
        intent.putExtra("url", "file://" + new File(getExternalCacheDir(), "temp.mp4").getAbsolutePath());
        startActivity(intent);
    }

    public void switchCamera() {
        if (mCurrentCameraID == FRONT_CAMERA_ID) {
            Log.d(TAG, "switch camera to back");
            openBackCamera();
        } else if (mCurrentCameraID == BACK_CAMERA_ID) {
            Log.d(TAG, "switch camera to front");
            openFrontCamera();
        }
    }

    public void switchCameraFlash() {
        cameraPreviewWrap.switchCameraFlashMode();
    }

    /**
     * 启动编码器
     */
    private void startMediaCodec() {

        // 编码器信息
        AYMediaCodecHelper.CodecInfo codecInfo = getAvcSupportedFormatInfo();
        if (codecInfo == null) {
            Log.d(TAG, "不支持硬编码");
            return;
        }

        // 设置给编码器的参数不能超过其最大值
        if (videoWidth > codecInfo.maxWidth) {
            Log.d(TAG, "不支持编码 视频宽度");
            return;
        }
        if (videoHeight > codecInfo.maxHeight) {
            Log.d(TAG, "不支持硬编码 视频高度");
            return;
        }
        if (videoBitrate > codecInfo.bitRate) {
            Log.d(TAG, "不支持硬编码 视频码率");
            return;
        }
        if (videoFrameRate > codecInfo.fps) {
            Log.d(TAG, "不支持硬编码 视频帧率");
            return;
        }

        // 启动编码
        mediaCodec = new AYMediaCodec(new File(getExternalCacheDir(), "temp.mp4").getAbsolutePath(), 2);
        mediaCodec.setEglContext(eglContext);
        mediaCodec.setContentMode(AYGPUImageConstants.AYGPUImageContentMode.kAYGPUImageScaleAspectFill);
        mediaCodec.setVideoFrameDelayTime(videoFrameDelayTime);
        audioCodecConfigResult = mediaCodec.configureAudioCodecAndStart(audioBitrate, audioSampleRate, audioRecord.getChannelCount());
        videoCodecConfigResult = mediaCodec.configureVideoCodecAndStart(videoWidth, videoHeight, videoBitrate, videoFrameRate, videoIFrameInterval);
    }

    /**
     * 关闭编码器
     */
    private void closeMediaCodec() {
        // 关闭编码
        if (mediaCodec != null) {
            Log.d(TAG, "关闭编码器");
            if (videoCodecConfigResult || audioCodecConfigResult) {
                mediaCodec.finish();
                mediaCodec = null;
            }
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.startBtn:
                startRecord();
                break;
            case R.id.stopBtn:
                stopRecord();
                break;
            case R.id.switchBtn:
                switchCamera();
                break;
        }
    }
}
