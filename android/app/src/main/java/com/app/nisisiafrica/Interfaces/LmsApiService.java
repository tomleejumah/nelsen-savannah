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

    @POST("lms/lessons/{lessonId}/quiz")
    Call<LmsModels.QuizEnvelope> submitQuiz(
            @Header("Authorization") String bearer,
            @Path("lessonId") String lessonId,
            @Body LmsModels.QuizBody body);

    @POST("lms/submissions")
    Call<LmsModels.SubmissionEnvelope> submitAssignment(
            @Header("Authorization") String bearer,
            @Body LmsModels.SubmissionBody body);

    @GET("lms/submissions/me")
    Call<LmsModels.SubmissionListEnvelope> mySubmissions(
            @Header("Authorization") String bearer,
            @Query("trackId") String trackId);

    @GET("lms/certificates/me")
    Call<LmsModels.CertificatesEnvelope> myCertificates(
            @Header("Authorization") String bearer);

    @GET("lms/submissions/queue")
    Call<LmsModels.QueueEnvelope> submissionQueue(@Header("Authorization") String bearer);

    @PATCH("lms/submissions/{id}/mark")
    Call<LmsModels.MarkEnvelope> markSubmission(
            @Header("Authorization") String bearer,
            @Path("id") String submissionId,
            @Body LmsModels.MarkBody body);

    @GET("lms/admin/mentees/{mentorId}/progress")
    Call<LmsModels.MenteesEnvelope> menteeProgress(
            @Header("Authorization") String bearer,
            @Path("mentorId") String mentorId);

    @GET("lms/assignments/me")
    Call<LmsModels.AssignmentsEnvelope> myAssignments(
            @Header("Authorization") String bearer);

    @POST("lms/assignments")
    Call<LmsModels.AssignmentEnvelope> createAssignment(
            @Header("Authorization") String bearer,
            @Body LmsModels.AssignmentBody body);

    @GET("lms/events/public")
    Call<LmsModels.HubEventsEnvelope> publicHubEvents();

    @POST("lms/events")
    Call<LmsModels.HubEventEnvelope> createHubEvent(
            @Header("Authorization") String bearer,
            @Body LmsModels.CreateHubEventBody body);

    @POST("lms/events/{eventId}/reserve")
    Call<LmsModels.ReserveEventEnvelope> reserveEvent(
            @Header("Authorization") String bearer,
            @Path("eventId") String eventId,
            @Body LmsModels.ReserveEventBody body);
}
