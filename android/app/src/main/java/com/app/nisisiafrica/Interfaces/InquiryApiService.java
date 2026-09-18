package com.app.nisisiafrica.Interfaces;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface InquiryApiService {

    class InquiryBody {
        public String desk;
        public String subject;
        public String replyTo;
        public Map<String, String> fields;

        public InquiryBody(String desk, String subject, String replyTo, Map<String, String> fields) {
            this.desk = desk;
            this.subject = subject;
            this.replyTo = replyTo;
            this.fields = fields;
        }
    }

    class InquiryResponse {
        public boolean ok;
        public String error;
        public String code;
    }

    @POST("inquiries")
    Call<InquiryResponse> postInquiry(@Body InquiryBody body);
}
