package com.example.attendance_app.network;

import com.google.gson.annotations.SerializedName;

public class AttendanceResponse {
    @SerializedName("employeeId")
    public String employeeId;

    @SerializedName("employeeName")
    public String employeeName;

    @SerializedName("action")
    public String action; // CHECKIN, CHECKOUT, ALREADY_COMPLETED

    @SerializedName("message")
    public String message;

    @SerializedName("shift")
    public ShiftInfo shift;

    public static class ShiftInfo {
        @SerializedName("id")
        public String id;
        @SerializedName("name")
        public String name;
        @SerializedName("code")
        public String code;
    }
}
