package com.example.attendance_app.utils;


import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import androidx.camera.core.ImageProxy;
import java.io.ByteArrayOutputStream;
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

    public static Bitmap cropFace(Bitmap source, Rect boundingBox, int rotation) {
        // Đảm bảo rect nằm trong bitmap
        int left   = Math.max(0, boundingBox.left);
        int top    = Math.max(0, boundingBox.top);
        int right  = Math.min(source.getWidth(), boundingBox.right);
        int bottom = Math.min(source.getHeight(), boundingBox.bottom);

        if (left >= right || top >= bottom) return source;

        Bitmap cropped = Bitmap.createBitmap(
                source, left, top, right - left, bottom - top);

        // Rotate nếu cần
        if (rotation != 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(rotation);
            cropped = Bitmap.createBitmap(
                    cropped, 0, 0,
                    cropped.getWidth(), cropped.getHeight(),
                    matrix, true);
        }

        // Resize về 224x224 cho model
        return Bitmap.createScaledBitmap(cropped, 224, 224, true);
    }
}