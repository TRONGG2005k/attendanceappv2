package com.example.attendance_app.utils;

import android.graphics.Bitmap;
import java.io.ByteArrayOutputStream;

public class ImageProcessUtils {

    /**
     * Chuyển đổi bitmap thành mảng byte định dạng JPEG với chất lượng 100% (tối đa).
     * Đảm bảo ảnh gửi lên server có độ sắc nét cao nhất có thể.
     */
    public static byte[] convertToJpeg(Bitmap bitmap) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
        return outputStream.toByteArray();
    }
}
