package com.aiyaapp.record;

import android.net.Uri;
import android.os.Bundle;
import android.widget.MediaController;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Created by 汪洋 on 2020/2/8.
 * Copyright © 2019年 汪洋. All rights reserved.
 */
public class PlayVideoActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        VideoView videoView = new VideoView(this);
        setContentView(videoView);

        MediaController mediaController = new MediaController(this);
        mediaController.show();
        videoView.setMediaController(mediaController);
        Uri videoUri = Uri.parse(getIntent().getStringExtra("url"));
        videoView.setVideoURI(videoUri);
        videoView.start();
    }
}
