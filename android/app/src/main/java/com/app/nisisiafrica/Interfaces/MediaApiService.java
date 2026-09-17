package com.app.nisisiafrica.Interfaces;

import com.app.nisisiafrica.data.Model.MediaUploadResponse;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Header;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public interface MediaApiService {
    @Multipart
    @POST("media/upload")
    Call<MediaUploadResponse> upload(
            @Header("Authorization") String bearer,
            @Part MultipartBody.Part file,
            @Part("folder") RequestBody folder
    );
}
