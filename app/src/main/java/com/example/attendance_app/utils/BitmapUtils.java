package com.example.attendance_app.utils;


import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import androidx.camera.core.ImageProxy;
import java.nio.ByteBuffer;

public class BitmapUtils {

    public static Bitmap imageProxyToBitmap(ImageProxy imageProxy) {
        ImageProxy.PlaneProxy plane = imageProxy.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();

        int width = imageProxy.getWidth();
        int height = imageProxy.getHeight();

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);
        return bitmap;
    }

    /**
     * Crop khuôn mặt với logic được cải tiến:
     * 1. Xoay source bitmap về hướng thẳng đứng trước.
     * 2. Mở rộng vùng bao (padding) để lấy được toàn bộ đầu/trán.
     * 3. Thực hiện crop trên ảnh đã xoay để khớp tọa độ hoàn toàn.
     */
    public static Bitmap cropFace(Bitmap source, Rect boundingBox, int rotation) {
        // Hướng camera trước thường bị xoay, ta xoay toàn bộ bitmap trước để tọa độ khớp
        Bitmap rotatedSource = source;
        if (rotation != 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(rotation);
            // Đối với camera trước (front), ảnh thường bị mirrored
            matrix.postScale(-1, 1, source.getWidth() / 2f, source.getHeight() / 2f);
            
            rotatedSource = Bitmap.createBitmap(
                    source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
        }

        // Sau khi xoay, tọa độ ML Kit trả về sẽ khớp với rotatedSource
        int width = rotatedSource.getWidth();
        int height = rotatedSource.getHeight();

        // Mở rộng Rect (Padding) để lấy thêm trán và tóc (Portrait mode)
        int rectWidth = boundingBox.width();
        int rectHeight = boundingBox.height();

        // Thêm padding: Top +40%, Bottom +20%, Sides +20%
        int left = (int) (boundingBox.left - rectWidth * 0.20f);
        int top = (int) (boundingBox.top - rectHeight * 0.40f);
        int right = (int) (boundingBox.right + rectWidth * 0.20f);
        int bottom = (int) (boundingBox.bottom + rectHeight * 0.20f);

        // Ràng buộc trong kích thước ảnh
        left = Math.max(0, left);
        top = Math.max(0, top);
        right = Math.min(width, right);
        bottom = Math.min(height, bottom);

        if (left >= right || top >= bottom) return rotatedSource;

        Bitmap cropped = Bitmap.createBitmap(
                rotatedSource, left, top, right - left, bottom - top);

        // Giải phóng rotatedSource nếu nó được tạo mới (tránh leak)
        if (rotatedSource != source) {
            // rotatedSource.recycle(); // Cẩn thận: Nếu source đang dùng ở chỗ khác thì không được recycle ở đây
        }

        return cropped;
    }

    /**
     * Resize bitmap về kích thước mong muốn (ví dụ 640x640).
     */
    public static Bitmap resize(Bitmap source, int newWidth, int newHeight) {
        if (source == null) return null;
        return Bitmap.createScaledBitmap(source, newWidth, newHeight, true);
    }
}