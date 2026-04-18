package com.example.attendance_app.liveness.model;

public class LivenessResult {
    public final boolean isLive;
    public final float confidence;    // 0.0 - 1.0
    public final long elapsedMs;
    public final String debugInfo;

    public LivenessResult(boolean isLive, float confidence,
                          long elapsedMs, String debugInfo) {
        this.isLive = isLive;
        this.confidence = confidence;
        this.elapsedMs = elapsedMs;
        this.debugInfo = debugInfo;
    }
}