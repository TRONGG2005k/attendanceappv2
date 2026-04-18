package com.example.attendance_app.network;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

/**
 * Interface cho API chấm công.
 * Sử dụng Multipart để upload file ảnh khuôn mặt.
 */
public interface AttendanceApiService {

    @Multipart
    @POST("/api/attendance/scan")
    Call<AttendanceResponse> scanAttendance(@Part MultipartBody.Part file);
}
