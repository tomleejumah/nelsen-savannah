package com.app.nisisiafrica.data.Model;

import java.util.List;
import java.util.Map;

/**
 * LMS wire models matching nelsen-savanna lms-api-contract.js envelopes.
 * Concrete envelope types (not generics) so Gson/Retrofit can deserialize.
 */
public final class LmsModels {
    private LmsModels() {}

    public static class TracksEnvelope {
        public boolean ok;
        public String source;
        public TracksData data;
        public String error;
    }

    public static class TrackDetailEnvelope {
        public boolean ok;
        public String source;
        public TrackDetailData data;
        public String error;
    }

    public static class ModuleDetailEnvelope {
        public boolean ok;
        public String source;
        public ModuleDetailData data;
        public String error;
    }

    public static class LessonDetailEnvelope {
        public boolean ok;
        public String source;
        public LessonDetailData data;
        public String error;
    }

    public static class MeEnvelope {
        public boolean ok;
        public String source;
        public Map<String, Object> data;
        public String error;
    }

    public static class MapEnvelope {
        public boolean ok;
        public String source;
        public Map<String, Object> data;
        public String error;
    }

    public static class EnrollmentEnvelope {
        public boolean ok;
        public String source;
        public EnrollmentData data;
        public String error;
    }

    public static class EnrollmentData {
        public Enrollment enrollment;
    }

    public static class EnrollmentListEnvelope {
        public boolean ok;
        public String source;
        public EnrollmentsData data;
        public String error;
    }

    public static class EnrollmentsData {
        public List<Enrollment> enrollments;
    }

    public static class ProgressEnvelope {
        public boolean ok;
        public String source;
        public ProgressData data;
        public String error;
    }

    public static class ProgressData {
        public Progress progress;
        public Enrollment enrollment;
    }

    public static class ProgressMapEnvelope {
        public boolean ok;
        public String source;
        public ProgressMapData data;
        public String error;
    }

    public static class ProgressMapData {
        public Map<String, Object> byLessonId;
        public Map<String, Object> byTrackId;
    }

    /** CourseItem-compatible track card + LMS extras. */
    public static class TrackCard {
        public String courseId;
        public String tutorId;
        public String courseImageUrl;
        public String tutorAvatarUrl;
        public String tutorName;
        public String courseTitle;
        /** API may send number or string — use [asString]. */
        public Object duration;
        public Object lessons;
        public String courseLink;
        public boolean isLiked;
        public String trackId;
        public String programSlug;
        public String does;
        public float trackPercent;
        public boolean enrolled;
        public List<String> audience;
        public int moduleCount;

        public String durationString() {
            return asString(duration);
        }

        public String lessonsString() {
            return asString(lessons);
        }

        private static String asString(Object v) {
            return v == null ? "" : String.valueOf(v);
        }
    }

    public static class TracksData {
        public List<TrackCard> tracks;
    }

    public static class TrackDetailData {
        public TrackCard track;
        public List<ModuleDto> modules;
        public Map<String, Object> enrollment;
    }

    public static class ModuleDto {
        public String moduleId;
        public String trackId;
        public String title;
        public String does;
        public int estimatedMinutes;
        public int lessonCount;
        public float modulePercent;
        public String status;
    }

    public static class ModuleDetailData {
        public ModuleDto module;
        public List<LessonDto> lessons;
    }

    public static class LessonDto {
        public String lessonId;
        public String moduleId;
        public String trackId;
        public String title;
        public String does;
        public String type;
        public int estimatedMinutes;
        public boolean hasQuiz;
        public boolean hasAssignment;
        public float lessonPercent;
        public String status;
        public String contentUrl;
        public String playbackUrl;
        public Long playbackExpiresAt;
    }

    public static class LessonDetailData {
        public LessonDto lesson;
    }

    public static class Enrollment {
        public String uid;
        public String trackId;
        public String status;
        public float trackPercent;
        public int modulesCompleted;
        public int modulesTotal;
        public int lessonsCompleted;
        public int lessonsTotal;
        public String mentorId;
        public long enrolledAt;
        public String platform;
        public String courseTitle;
        public String courseImageUrl;
        public String nextLessonId;
        public float progressPct;
    }

    public static class Progress {
        public String lessonId;
        public String moduleId;
        public String trackId;
        public boolean opened;
        public float contentPct;
        public float quizPct;
        public float assignmentPct;
        public float lessonPercent;
        public float trackPercent;
        public float modulePercent;
        public String status;
        public String lastPlatform;
        public long updatedAt;
    }

    public static class EnrollBody {
        public String trackId;
        public EnrollBody(String trackId) { this.trackId = trackId; }
    }

    public static class ProgressBody {
        public boolean opened;
        public float contentPct;
        public float quizPct;
        public String lastPlatform = "android";
        public ProgressBody(boolean opened, float contentPct, float quizPct) {
            this.opened = opened;
            this.contentPct = contentPct;
            this.quizPct = quizPct;
        }
    }

    public static class QuizBody {
        public Integer score;
        public Boolean passed;
        public String lastPlatform = "android";
        public QuizBody(int score, boolean passed) {
            this.score = score;
            this.passed = passed;
        }
    }

    public static class QuizData {
        public String lessonId;
        public float quizPct;
        public Float lessonPercent;
        public Float trackPercent;
    }

    public static class QuizEnvelope {
        public boolean ok;
        public QuizData data;
        public String error;
    }

    public static class SubmissionBody {
        public String lessonId;
        public String text;
        public String platform = "android";
        public SubmissionBody(String lessonId, String text) {
            this.lessonId = lessonId;
            this.text = text;
        }
    }

    public static class SubmissionDto {
        public String id;
        public String lessonId;
        public String trackId;
        public String status;
        public String text;
        public Float score;
        public String feedback;
        public long submittedAt;
    }

    public static class SubmissionData {
        public SubmissionDto submission;
    }

    public static class SubmissionEnvelope {
        public boolean ok;
        public SubmissionData data;
        public String error;
    }

    public static class SubmissionListData {
        public java.util.List<SubmissionDto> submissions;
    }

    public static class SubmissionListEnvelope {
        public boolean ok;
        public SubmissionListData data;
        public String error;
    }

    public static class CertificateDto {
        public String trackId;
        public String courseTitle;
        public long issuedAt;
        public String verifyUrl;
        public float trackPercent;
    }

    public static class CertificatesData {
        public java.util.List<CertificateDto> certificates;
    }

    public static class CertificatesEnvelope {
        public boolean ok;
        public CertificatesData data;
        public String error;
    }

    public static class QueueItemDto {
        public String id;
        public String lessonId;
        public String lessonTitle;
        public String trackId;
        public String menteeId;
        public String menteeName;
        public String text;
        public long submittedAt;
    }

    public static class QueueData {
        public java.util.List<QueueItemDto> queue;
    }

    public static class QueueEnvelope {
        public boolean ok;
        public QueueData data;
        public String error;
    }

    public static class MarkBody {
        public int score;
        public Boolean passed;
        public String feedback;
        public MarkBody(int score, boolean passed, String feedback) {
            this.score = score;
            this.passed = passed;
            this.feedback = feedback;
        }
    }

    public static class MarkEnvelope {
        public boolean ok;
        public String error;
    }

    public static class MenteeProgressDto {
        public String uid;
        public String displayName;
        public String trackId;
        public float trackPercent;
        public long lastActiveAt;
    }

    public static class MenteesData {
        public java.util.List<MenteeProgressDto> mentees;
    }

    public static class MenteesEnvelope {
        public boolean ok;
        public MenteesData data;
        public String error;
    }

    public static class AssignmentDto {
        public String id;
        public String title;
        public String prompt;
        public String trackId;
        public String lessonId;
        public String assigneeUid;
        public long createdAt;
    }

    public static class AssignmentBody {
        public String title;
        public String prompt;
        public String trackId;
        public String assigneeUid;
        public AssignmentBody(String title, String prompt, String trackId, String assigneeUid) {
            this.title = title;
            this.prompt = prompt;
            this.trackId = trackId;
            this.assigneeUid = assigneeUid;
        }
    }

    public static class AssignmentsData {
        public java.util.List<AssignmentDto> assignments;
    }

    public static class AssignmentsEnvelope {
        public boolean ok;
        public AssignmentsData data;
        public String error;
    }

    public static class AssignmentData {
        public AssignmentDto assignment;
    }

    public static class AssignmentEnvelope {
        public boolean ok;
        public AssignmentData data;
        public String error;
    }
}
