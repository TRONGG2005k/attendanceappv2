package com.example.attendance_app.liveness.analyzers;


import android.graphics.PointF;
import java.util.ArrayList;
import java.util.List;

public class MotionAnalyzer {

    // Lưu lịch sử vị trí mũi qua các frame
    private final List<PointF> noseHistory = new ArrayList<>();
    private static final int MAX_HISTORY = 12;

    // Thêm landmark từ frame mới
    public void addLandmark(PointF noseTip) {
        noseHistory.add(noseTip);
        if (noseHistory.size() > MAX_HISTORY) {
            noseHistory.remove(0);
        }
    }

    public void clear() {
        noseHistory.clear();
    }

    public int getFrameCount() {
        return noseHistory.size();
    }

    // Trả về score 0.0 - 1.0
    // Người thật: micro-movement tự nhiên → score cao
    // Ảnh tĩnh / video loop: gần như không đổi → score thấp
    public float getScore() {
        if (noseHistory.size() < 4) return 0.5f; // chưa đủ frame

        float xVariance = variance(getXList());
        float yVariance = variance(getYList());
        float total = xVariance + yVariance;

        if (total < 0.2f)  return 0.05f; // hoàn toàn tĩnh → ảnh giả
        if (total < 0.8f)  return 0.55f; // rất ít chuyển động → nghi ngờ
        if (total < 10.0f) return 1.00f; // micro-movement tự nhiên → thật
        if (total < 30.0f) return 0.65f; // hơi nhiều nhưng chấp nhận
        return 0.20f;                     // rung quá → môi trường xấu
    }

    // Kiểm tra xem có đủ frames để kết luận chưa
    public boolean isReliable() {
        return noseHistory.size() >= 6;
    }

    private List<Float> getXList() {
        List<Float> list = new ArrayList<>();
        for (PointF p : noseHistory) list.add(p.x);
        return list;
    }

    private List<Float> getYList() {
        List<Float> list = new ArrayList<>();
        for (PointF p : noseHistory) list.add(p.y);
        return list;
    }

    private float variance(List<Float> values) {
        if (values.isEmpty()) return 0f;
        float mean = 0f;
        for (float v : values) mean += v;
        mean /= values.size();

        float sum = 0f;
        for (float v : values) sum += (v - mean) * (v - mean);
        return sum / values.size();
    }
}
