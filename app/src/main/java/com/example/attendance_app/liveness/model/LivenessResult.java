package com.example.attendance_app.liveness.model;

import android.graphics.Bitmap;

public class LivenessResult {
    public final boolean isLive;
    public final float confidence;    // 0.0 - 1.0
    public final long elapsedMs;
    public final String debugInfo;
    public final Bitmap faceBitmap;   // Ảnh khuôn mặt đã capture

    public LivenessResult(boolean isLive, float confidence,
                          long elapsedMs, String debugInfo,
                          Bitmap faceBitmap) {
        this.isLive = isLive;
        this.confidence = confidence;
        this.elapsedMs = elapsedMs;
        this.debugInfo = debugInfo;
        this.faceBitmap = faceBitmap;
    }
}