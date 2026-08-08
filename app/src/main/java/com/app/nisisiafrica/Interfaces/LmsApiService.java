package com.app.nisisiafrica.Interfaces;

import com.app.nisisiafrica.data.Model.LmsModels;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * LMS endpoints on the same host as notifications
 * ({@code https://api.tommlyjumah.dev/nisisi-africa/}).
 */
public interface LmsApiService {

    @GET("lms/me")
    Call<LmsModels.MeEnvelope> me(@Header("Authorization") String bearer);

    @GET("lms/tracks")
    Call<LmsModels.TracksEnvelope> tracks(@Header("Authorization") String bearer);

    @GET("lms/tracks/{trackId}")
    Call<LmsModels.TrackDetailEnvelope> track(
            @Header("Authorization") String bearer,
            @Path("trackId") String trackId);

    @GET("lms/modules/{moduleId}")
    Call<LmsModels.ModuleDetailEnvelope> module(
            @Header("Authorization") String bearer,
            @Path("moduleId") String moduleId);

    @GET("lms/lessons/{lessonId}")
    Call<LmsModels.LessonDetailEnvelope> lesson(
            @Header("Authorization") String bearer,
            @Path("lessonId") String lessonId);

    @POST("lms/tracks/{trackId}/like")
    Call<LmsModels.MapEnvelope> likeTrack(
            @Header("Authorization") String bearer,
            @Path("trackId") String trackId);

    @POST("lms/enrollments")
    Call<LmsModels.EnrollmentEnvelope> enroll(
            @Header("Authorization") String bearer,
            @Body LmsModels.EnrollBody body);

    @GET("lms/enrollments/me")
    Call<LmsModels.EnrollmentListEnvelope> myEnrollments(@Header("Authorization") String bearer);

    @DELETE("lms/enrollments/{trackId}")
    Call<Void> unenroll(
            @Header("Authorization") String bearer,
            @Path("trackId") String trackId);

    @PATCH("lms/progress/{lessonId}")
    Call<LmsModels.ProgressEnvelope> patchProgress(
            @Header("Authorization") String bearer,
            @Path("lessonId") String lessonId,
            @Body LmsModels.ProgressBody body);

    @GET("lms/progress/me")
    Call<LmsModels.ProgressMapEnvelope> myProgress(
            @Header("Authorization") String bearer,
            @Query("trackId") String trackId);
}
