package com.app.nisisiafrica.data.Model;

public class DiditSessionResponse {
    private String status;
    private Data data;
    private String message;

    public boolean isSuccess() {
        return "success".equals(status);  // Changed
    }

    public String getSessionToken() {
        return data != null ? data.sessionToken : null;
    }

    public static class Data {
        private String sessionToken;
        private String sessionId;
    }
}