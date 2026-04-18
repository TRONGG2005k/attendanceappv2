package com.example.attendance_app.liveness.model;

import android.graphics.Bitmap;
import android.graphics.PointF;

import java.util.List;

public class FrameData {
    public final Bitmap bitmap;
    public final long timestamp;
    public final List<PointF> landmarks; // null nếu chưa detect

    public FrameData(Bitmap bitmap, long timestamp, List<PointF> landmarks) {
        this.bitmap = bitmap;
        this.timestamp = timestamp;
        this.landmarks = landmarks;
    }
}