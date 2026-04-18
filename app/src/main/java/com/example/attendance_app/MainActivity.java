package com.example.attendance_app;


import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
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
import com.example.attendance_app.network.AttendanceApiService;
import com.example.attendance_app.network.AttendanceResponse;
import com.example.attendance_app.utils.ImageProcessUtils;
import com.example.attendance_app.utils.BitmapUtils;

import android.content.ContentValues;
import android.net.Uri;
import android.provider.MediaStore;
import java.io.OutputStream;
import java.io.IOException;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.util.Log;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity
        implements FrameAnalyzer.LivenessCallback {

    private static final int REQUEST_CAMERA = 100;

    private PreviewView previewView;
    private ProgressBar progressBar;
    private TextView tvResult;
    private View faceOverlay;

    private CameraManager cameraManager;
    private AttendanceApiService apiService;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    
    // Flag debounce để tránh gửi nhiều request cùng lúc
    private boolean isProcessing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.previewView);
        progressBar = findViewById(R.id.progressBar);
        tvResult    = findViewById(R.id.tvResult);
        faceOverlay = findViewById(R.id.faceOverlay);

        initRetrofit();

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
                
                // Trigger upload nếu chưa bận xử lý
                if (!isProcessing) {
                    uploadAttendance(result.faceBitmap);
                }
            } else {
                tvResult.setText("✗ Phát hiện giả mạo");
                tvResult.setTextColor(0xFFF44336);
                faceOverlay.setBackgroundResource(R.drawable.face_oval_fail);
                tvResult.append("\n" + result.debugInfo);
                tvResult.postDelayed(this::resetUI, 2000);
            }
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

    private void initRetrofit() {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://tn0964755528-dn-hrm-nextjs.hf.space")
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build();

        apiService = retrofit.create(AttendanceApiService.class);
    }

    private void uploadAttendance(Bitmap bitmap) {
        if (bitmap == null) return;
        isProcessing = true;
        
        runOnUiThread(() -> {
            tvResult.setText("⌛ Đang chấm công...");
            tvResult.setTextColor(0xFFFFA500); // Orange
        });

        executorService.execute(() -> {
            try {
                // 0. Resize ảnh về 640x640 (Yêu cầu mới)
                Bitmap resizedBitmap = BitmapUtils.resize(bitmap, 640, 640);
                if (resizedBitmap == null) resizedBitmap = bitmap;

                // 1. Lưu ảnh cục bộ để debug (Yêu cầu của bạn)
                String debugPath = saveBitmapForDebug(resizedBitmap);
                Log.d("AttendanceDebug", "Ảnh đã được lưu tại: " + debugPath);

                // 2. Process image (chuyển sang JPEG chuẩn)
                byte[] jpegData = ImageProcessUtils.convertToJpeg(resizedBitmap);
                
                // 3. Prepare multipart request
                RequestBody requestFile = RequestBody.create(
                        MediaType.parse("image/jpeg"), jpegData);
                MultipartBody.Part body = MultipartBody.Part.createFormData(
                        "file", "attendance.jpg", requestFile);

                // 3. Call API
                Response<AttendanceResponse> response = apiService.scanAttendance(body).execute();

                runOnUiThread(() -> {
                    if (response.isSuccessful() && response.body() != null) {
                        handleApiResponse(response.body());
                    } else {
                        tvResult.setText("⚠ Lỗi kết nối server (" + response.code() + ")");
                        tvResult.setTextColor(0xFFF44336);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    tvResult.setText("⚠ Hệ thống bận: " + e.getMessage());
                    tvResult.setTextColor(0xFFF44336);
                });
            } finally {
                // Giữ trạng thái hiển thị 3s rồi reset để scan tiếp
                tvResult.postDelayed(() -> {
                    isProcessing = false;
                    resetUI();
                }, 3000);
            }
        });
    }

    private String saveBitmapForDebug(Bitmap bitmap) {
        String fileName = "attendance_debug_" + System.currentTimeMillis() + ".jpg";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AttendanceApp");

        Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri != null) {
            try {
                OutputStream out = getContentResolver().openOutputStream(uri);
                if (out != null) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
                    out.close();
                    return "Đã lưu vào bộ sưu tập: Pictures/AttendanceApp/" + fileName;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return "Lỗi khi lưu ảnh vào bộ sưu tập";
    }

    private void handleApiResponse(AttendanceResponse response) {
        if ("ALREADY_COMPLETED".equals(response.action)) {
            tvResult.setText("💡 " + response.message);
            tvResult.setTextColor(0xFF2196F3); // Blue
        } else {
            String actionName = "CHECKIN".equals(response.action) ? "Vào ca" : "Tan ca";
            tvResult.setText("✅ " + actionName + " thành công!");
            tvResult.append("\n" + response.employeeName);
            if (response.shift != null) {
                tvResult.append("\n" + response.shift.name);
            }
            tvResult.setTextColor(0xFF4CAF50); // Green
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraManager != null) cameraManager.shutdown();
        executorService.shutdown();
    }
}