package com.example.attendance_app.liveness;


import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PointF;
import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.example.attendance_app.liveness.analyzers.MotionAnalyzer;
import com.example.attendance_app.liveness.model.FrameData;
import com.example.attendance_app.liveness.model.LivenessResult;
import com.example.attendance_app.utils.BitmapUtils;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.face.FaceLandmark;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;

public class FrameAnalyzer implements ImageAnalysis.Analyzer {

    public interface LivenessCallback {
        void onResult(LivenessResult result);
        void onNoFace();
        void onProgress(int elapsedMs); // để update progress bar
    }

    private static final int DEADLINE_MS = 200;

    private final LivenessCallback callback;
    private final MotionAnalyzer motionAnalyzer = new MotionAnalyzer();
    private final FaceDetector faceDetector;

    // Frame buffer tối đa 12 frame
    private final Deque<FrameData> frameBuffer = new ArrayDeque<>(12);

    private long sessionStartTime = -1;
    private boolean isEvaluating = false;

    public FrameAnalyzer(LivenessCallback callback) {
        this.callback = callback;

        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setMinFaceSize(0.25f)
                .build();

        faceDetector = FaceDetection.getClient(options);
    }

    @Override
    public void analyze(@NonNull ImageProxy imageProxy) {
        Bitmap bitmap = BitmapUtils.imageProxyToBitmap(imageProxy);
        int rotation = imageProxy.getImageInfo().getRotationDegrees();

        InputImage inputImage = InputImage.fromBitmap(bitmap, rotation);

        faceDetector.process(inputImage)
                .addOnSuccessListener(faces -> {
                    if (faces.isEmpty()) {
                        // Reset khi mất mặt
                        resetSession();
                        callback.onNoFace();
                    } else {
                        Face face = faces.get(0);
                        processFrame(bitmap, face, rotation);
                    }
                })
                .addOnFailureListener(Throwable::printStackTrace)
                .addOnCompleteListener(task -> imageProxy.close());
    }

    private void processFrame(Bitmap bitmap, Face face, int rotation) {
        // Bắt đầu đếm thời gian từ frame đầu tiên có mặt
        if (sessionStartTime < 0) {
            sessionStartTime = System.currentTimeMillis();
            isEvaluating = true;
        }

        long elapsed = System.currentTimeMillis() - sessionStartTime;
        callback.onProgress((int) Math.min(elapsed, DEADLINE_MS));

        // Lấy landmark mũi để track chuyển động
        FaceLandmark noseLandmark = face.getLandmark(FaceLandmark.NOSE_BASE);
        if (noseLandmark != null) {
            motionAnalyzer.addLandmark(noseLandmark.getPosition());
        }

        // Crop khuôn mặt từ bitmap
        Bitmap faceCrop = BitmapUtils.cropFace(bitmap, face.getBoundingBox(), rotation);

        // Lưu vào buffer
        FrameData frameData = new FrameData(faceCrop,
                System.currentTimeMillis(),
                noseLandmark != null ? Collections.singletonList(noseLandmark.getPosition()) : null);

        frameBuffer.addLast(frameData);
        if (frameBuffer.size() > 12) frameBuffer.removeFirst();

        // Kiểm tra điều kiện kết luận
        if (isEvaluating) {
            float motionScore = motionAnalyzer.getScore();

            boolean deadlineReached = elapsed >= DEADLINE_MS;
            boolean earlyConclusion = motionAnalyzer.isReliable()
                    && (motionScore > 0.90f || motionScore < 0.15f);

            if (deadlineReached || earlyConclusion) {
                isEvaluating = false;

                // Kết hợp Motion + Texture/Blur analysis
                AntiSpoofingEngine.LivenessScore textureResult = AntiSpoofingEngine.analyze(faceCrop);
                
                float finalConfidence = (motionScore * 0.3f) + (textureResult.score * 0.7f);
                boolean isLive = finalConfidence > 0.60f;

                LivenessResult result = new LivenessResult(
                        isLive,
                        finalConfidence,
                        elapsed,
                        "motion=" + String.format("%.2f", motionScore)
                                + " | texture=" + textureResult.details,
                        faceCrop
                );

                callback.onResult(result);

                // Reset sau 2s để có thể check lại
                resetSessionDelayed();
            }
        }
    }

    private void resetSession() {
        sessionStartTime = -1;
        isEvaluating = false;
        motionAnalyzer.clear();
        frameBuffer.clear();
    }

    private void resetSessionDelayed() {
        new android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed(this::resetSession, 2000);
    }
}
