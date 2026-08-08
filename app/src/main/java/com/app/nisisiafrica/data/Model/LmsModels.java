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
        public Enrollment data;
        public String error;
    }

    public static class EnrollmentListEnvelope {
        public boolean ok;
        public String source;
        public List<Enrollment> data;
        public String error;
    }

    public static class ProgressEnvelope {
        public boolean ok;
        public String source;
        public Progress data;
        public String error;
    }

    public static class ProgressListEnvelope {
        public boolean ok;
        public String source;
        public List<Progress> data;
        public String error;
    }

    /** CourseItem-compatible track card + LMS extras. */
    public static class TrackCard {
        public String courseId;
        public String tutorId;
        public String courseImageUrl;
        public String tutorAvatarUrl;
        public String tutorName;
        public String courseTitle;
        public String duration;
        public String lessons;
        public String courseLink;
        public boolean isLiked;
        public String trackId;
        public String programSlug;
        public String does;
        public float trackPercent;
        public boolean enrolled;
        public List<String> audience;
        public int moduleCount;
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
        public String trackId;
        public long enrolledAt;
        public float progressPct;
    }

    public static class Progress {
        public String lessonId;
        public String trackId;
        public boolean opened;
        public float contentPct;
        public float quizPct;
        public String lastPlatform;
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
}
