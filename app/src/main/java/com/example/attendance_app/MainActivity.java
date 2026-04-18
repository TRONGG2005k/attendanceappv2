package com.example.attendance_app;


import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.attendance_app.camera.CameraManager;
import com.example.attendance_app.liveness.FrameAnalyzer;
import com.example.attendance_app.liveness.model.LivenessResult;

public class MainActivity extends AppCompatActivity
        implements FrameAnalyzer.LivenessCallback {

    private static final int REQUEST_CAMERA = 100;

    private PreviewView previewView;
    private ProgressBar progressBar;
    private TextView tvResult;
    private View faceOverlay;

    private CameraManager cameraManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.previewView);
        progressBar = findViewById(R.id.progressBar);
        tvResult    = findViewById(R.id.tvResult);
        faceOverlay = findViewById(R.id.faceOverlay);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startLiveness();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA);
        }
    }

    private void startLiveness() {
        FrameAnalyzer analyzer = new FrameAnalyzer(this);

        cameraManager = new CameraManager(
                this, this, previewView, analyzer);

        cameraManager.startCamera();
    }

    // --- LivenessCallback ---

    @Override
    public void onResult(LivenessResult result) {
        runOnUiThread(() -> {
            progressBar.setProgress(200);

            if (result.isLive) {
                tvResult.setText("✓ Xác thực thành công");
                tvResult.setTextColor(0xFF4CAF50);
                faceOverlay.setBackgroundResource(R.drawable.face_oval_success);
            } else {
                tvResult.setText("✗ Phát hiện giả mạo");
                tvResult.setTextColor(0xFFF44336);
                faceOverlay.setBackgroundResource(R.drawable.face_oval_fail);
            }

            // Debug: hiện confidence
            tvResult.append("\n" + String.format("%.0f%%", result.confidence * 100)
                    + " | " + result.elapsedMs + "ms"
                    + " | " + result.debugInfo);

            // Reset UI sau 2s
            tvResult.postDelayed(this::resetUI, 2000);
        });
    }

    @Override
    public void onNoFace() {
        runOnUiThread(() -> {
            tvResult.setText("Đưa mặt vào khung");
            tvResult.setTextColor(0xFFFFFFFF);
            progressBar.setProgress(0);
            faceOverlay.setBackgroundResource(R.drawable.face_oval);
        });
    }

    @Override
    public void onProgress(int elapsedMs) {
        runOnUiThread(() -> progressBar.setProgress(elapsedMs));
    }

    private void resetUI() {
        tvResult.setText("Đưa mặt vào khung");
        tvResult.setTextColor(0xFFFFFFFF);
        progressBar.setProgress(0);
        faceOverlay.setBackgroundResource(R.drawable.face_oval);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLiveness();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraManager != null) cameraManager.shutdown();
    }
}