package com.app.nisisiafrica.Interfaces;

import com.app.nisisiafrica.data.Model.DiditSessionRequest;
import com.app.nisisiafrica.data.Model.DiditSessionResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface KYCCallBack {
    @POST("didit/create-session")
    Call<DiditSessionResponse> createDiditSession(@Body DiditSessionRequest request);
}
