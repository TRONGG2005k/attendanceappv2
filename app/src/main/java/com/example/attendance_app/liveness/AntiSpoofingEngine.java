package com.example.attendance_app.liveness;

import android.graphics.Bitmap;
import android.graphics.Color;

/**
 * AntiSpoofingEngine: Mã nguồn xử lý liveness cực nhẹ bằng Computer Vision thuần (Java version).
 * Tốc độ xử lý: ~15-40ms trên mobile.
 */
public class AntiSpoofingEngine {

    /**
     * LivenessScore: Kết quả trả về từ engine.
     */
    public static class LivenessScore {
        public final boolean isLive;
        public final float score;
        public final String details;

        public LivenessScore(boolean isLive, float score, String details) {
            this.isLive = isLive;
            this.score = score;
            this.details = details;
        }
    }

    /**
     * Hàm chính kiểm tra liveness từ face crop bitmap.
     */
    public static LivenessScore analyze(Bitmap faceBitmap) {
        long startTime = System.currentTimeMillis();
        
        // 1. Laplacian Variance (Phát hiện ảnh mờ/ảnh chụp lại)
        double laplacianVar = calculateLaplacianVariance(faceBitmap);
        
        // 2. Texture Score (Phân tích cấu trúc bề mặt da)
        float textureScore = calculateTextureScore(faceBitmap);
        
        // 3. Screen Artifact Detection (Phát hiện vân Moiré/Pixel lưới)
        float screenArtifactScore = detectScreenArtifacts(faceBitmap);
        
        // Logic chấm điểm tổng hợp (Weight-based)
        float lapWeight = 0.35f;
        float texWeight = 0.45f;
        float artWeight = 0.20f;
        
        // Chuẩn hóa Laplacian (thường > 50 là thật)
        float normLap = (float) Math.min(1.0, laplacianVar / 100.0);
        
        // ArtScale: screenArtifactScore cao -> Khả năng FAKE cao -> Điểm trừ (1 - score)
        float normArt = Math.max(0.0f, 1.0f - screenArtifactScore);
        
        float finalScore = (normLap * lapWeight) + (textureScore * texWeight) + (normArt * artWeight);
        
        boolean isLive = finalScore > 0.62f; 
        
        long elapsed = System.currentTimeMillis() - startTime;
        String debugInfo = String.format("L:%.1f, T:%.2f, A:%.2f, Time:%dms", 
            laplacianVar, textureScore, screenArtifactScore, elapsed);
        
        return new LivenessScore(isLive, finalScore, debugInfo);
    }

    /**
     * Tính toán phương sai Laplacian để phát hiện ảnh mờ.
     */
    private static double calculateLaplacianVariance(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        double[] grayscale = new double[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            grayscale[i] = (Color.red(p) * 0.299 + Color.green(p) * 0.587 + Color.blue(p) * 0.114);
        }

        double[] laplacian = new double[pixels.length];
        double sum = 0;
        
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                int idx = y * width + x;
                double value = (grayscale[idx - 1] + grayscale[idx + 1] +
                             grayscale[idx - width] + grayscale[idx + width] -
                             4 * grayscale[idx]);
                laplacian[idx] = value;
                sum += value;
            }
        }

        double mean = sum / pixels.length;
        double variance = 0;
        for (double v : laplacian) {
            variance += Math.pow(v - mean, 2);
        }
        
        return variance / pixels.length;
    }

    /**
     * Local Binary Patterns (LBP) đơn giản hóa.
     */
    private static float calculateTextureScore(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int step = 4;
        
        int faceComplexity = 0;
        int totalSamples = 0;
        
        for (int y = height / 4; y < 3 * height / 4; y += step) {
            for (int x = width / 4; x < 3 * width / 4; x += step) {
                int center = getGrayscaleValue(bitmap.getPixel(x, y));
                
                int pattern = 0;
                if (getGrayscaleValue(bitmap.getPixel(x, y - 1)) > center) pattern += 1;
                if (getGrayscaleValue(bitmap.getPixel(x, y + 1)) > center) pattern += 2;
                if (getGrayscaleValue(bitmap.getPixel(x - 1, y)) > center) pattern += 4;
                if (getGrayscaleValue(bitmap.getPixel(x + 1, y)) > center) pattern += 8;
                
                if (pattern > 0 && pattern < 15) {
                    faceComplexity++;
                }
                totalSamples++;
            }
        }
        
        return totalSamples > 0 ? (float) faceComplexity / totalSamples : 0f;
    }

    /**
     * Nhận diện vân Moiré hoặc nhiễu pixel đặc trưng.
     */
    private static float detectScreenArtifacts(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        
        int artifactCount = 0;
        int totalSamples = 0;
        
        for (int y = height / 3; y < 2 * height / 3; y += 2) {
            for (int x = width / 3; x < 2 * width / 3; x += 2) {
                int idx = y * width + x;
                int current = getGrayscaleValue(pixels[idx]);
                int right = getGrayscaleValue(pixels[idx + 1]);
                
                if (Math.abs(current - right) > 45) {
                    artifactCount++;
                }
                totalSamples++;
            }
        }
        
        return totalSamples > 0 ? (float) artifactCount / totalSamples : 0f;
    }

    private static int getGrayscaleValue(int pixel) {
        return (int) (Color.red(pixel) * 0.299 + Color.green(pixel) * 0.587 + Color.blue(pixel) * 0.114);
    }
}
