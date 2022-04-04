package com.github.travcam;

import android.Manifest;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.view.TextureView;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import java.io.File;
import java.util.List;

@SuppressWarnings("FieldCanBeLocal")
public class CameraActivity extends BaseActivity implements TravCam.CameraHandlerListener {
    // Permissions
    private final static int PERMISSIONS_REQUEST_CODE = 70761;
    private final static String[] REQUIRED_PERMISSION_OLD = {
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO
    };
    private final static String[] REQUIRED_PERMISSION_NEW = {
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO
    };
    private final static String[] REQUIRED_PERMISSION = Build.VERSION.SDK_INT > Build.VERSION_CODES.Q ? REQUIRED_PERMISSION_NEW : REQUIRED_PERMISSION_OLD;

    // Views
    private TextureView mTextureView;
    private MaterialButton mBtnCapture;
    private ProgressBar mCaptureProgress;

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initViews();
        checkPermissions();
    }

    private void checkPermissions(){
        // Check if all required permissions are Granted
        List<String> MISSING_PERMISSIONS = getMissingPermissions(REQUIRED_PERMISSION);
        if(MISSING_PERMISSIONS.size() == 0) {
            startCamera();
        }else {
            requestPermissions(MISSING_PERMISSIONS.toArray(new String[0]), PERMISSIONS_REQUEST_CODE);
        }
    }

    private void startCamera(){
        TravCam.init(mTextureView, this, this);

        // Init Click Events
        mBtnCapture.setOnClickListener(v -> {
            if(TravCam.isVideoRecordingRunning()){
                TravCam.stopVideoRecording();
            }else{
                TravCam.captureImage();
            }
        });

        mBtnCapture.setOnLongClickListener(v -> {
            if(TravCam.isVideoRecordingRunning()){
                TravCam.stopVideoRecording();
            }else{
                TravCam.setUpMediaRecorder();
            }
            return true;
        });
    }

    @Override
    public void onImageCapture(@NonNull File imageFile) {
        // Add image preview fragment

    }

    @Override
    public void onVideoRecordingStarts() {
        // Change capture button design
        mBtnCapture.setIconTint(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.camera_button_video_recording)));

        // Show progress bar
        runOnUiThread(() -> mCaptureProgress.setVisibility(View.VISIBLE));
    }

    @Override
    public void onVideoRecordingEnds(@NonNull File videoFile) {
        // Return capture button design to default state
        mBtnCapture.setIconTint(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.camera_button_default)));

        // Hide progress bar
        runOnUiThread(() -> mCaptureProgress.setVisibility(View.GONE));
    }

    @Override
    public void onVideoRecordingLengthTicks(long maxProgress, long currentProgress) {
        // Update progress bar
        runOnUiThread(() -> {
            mCaptureProgress.setMax((int) maxProgress);
            mCaptureProgress.setProgress((int) currentProgress);
            if(currentProgress == maxProgress) mCaptureProgress.setVisibility(View.GONE);
        });
    }

    @Override
    public void onCloseCameraError() {
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == PERMISSIONS_REQUEST_CODE){
            final List<String> missingPermissions = getMissingPermissions(REQUIRED_PERMISSION);
            if(missingPermissions.size() == 0){
                finish();
                startActivity(new Intent(CameraActivity.this, CameraActivity.class));
            }else{
                Toast.makeText(this, "Missing permissions!: "+missingPermissions.size(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected int getActivityLayoutResourceId() {
        return R.layout.activity_camera;
    }

    @Override
    protected void onResume() {
        TravCam.onResume();
        super.onResume();
    }

    @Override
    protected void onPause() {
        TravCam.onPause();
        super.onPause();
    }

    private void initViews(){
        mBtnCapture = findViewById(R.id.share_story_activity_btn_capture);
        mTextureView = findViewById(R.id.share_story_activity_texture_view);
        mCaptureProgress = findViewById(R.id.share_story_activity_capture_progress);
    }
}