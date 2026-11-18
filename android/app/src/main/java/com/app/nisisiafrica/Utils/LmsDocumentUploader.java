package com.app.nisisiafrica.Utils;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.app.nisisiafrica.data.Model.LmsModels;
import com.app.nisisiafrica.data.remote.ApiClient;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response as RetrofitResponse;

/** Uploads private LMS documents through the existing presigned media pipeline. */
public final class LmsDocumentUploader {
    private static final long MAX_DOCUMENT_BYTES = 100L * 1024L * 1024L;

    public interface Listener {
        void onSuccess(String mediaId, String filename, String mimeType, long sizeBytes);
        void onError(String message);
    }

    private LmsDocumentUploader() {}

    public static void upload(Context context, String bearer, Uri uri, Listener listener) {
        Context app = context.getApplicationContext();
        String filename = displayName(app, uri);
        String mime = app.getContentResolver().getType(uri);
        if (mime == null || mime.trim().isEmpty()) mime = "application/octet-stream";
        long size = fileSize(app, uri);
        if (size > MAX_DOCUMENT_BYTES) {
            listener.onError("Documents must be 100 MB or smaller.");
            return;
        }
        final String contentType = mime;
        final long sizeBytes = size;
        Map<String, Object> request = new HashMap<>();
        request.put("filename", filename);
        request.put("contentType", contentType);
        request.put("sizeBytes", sizeBytes);
        request.put("scope", "misc");

        ApiClient.getLmsService().createMediaUploadUrl(bearer, request)
                .enqueue(new Callback<LmsModels.MapEnvelope>() {
                    @Override public void onResponse(Call<LmsModels.MapEnvelope> call,
                                                     retrofit2.Response<LmsModels.MapEnvelope> response) {
                        LmsModels.MapEnvelope envelope = response.body();
                        if (!response.isSuccessful() || envelope == null || !envelope.ok || envelope.data == null) {
                            listener.onError(envelope != null ? envelope.error : "Could not prepare upload.");
                            return;
                        }
                        Map<String, Object> data = envelope.data;
                        String mediaId = string(data.get("mediaId"));
                        String uploadUrl = string(data.get("uploadUrl"));
                        String method = string(data.get("method"));
                        Object rawHeaders = data.get("headers");
                        if (mediaId.isEmpty() || uploadUrl.isEmpty()) {
                            listener.onError("Upload ticket was incomplete.");
                            return;
                        }
                        Map<String, String> headers = new HashMap<>();
                        if (rawHeaders instanceof Map) {
                            for (Map.Entry<?, ?> entry : ((Map<?, ?>) rawHeaders).entrySet()) {
                                headers.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                            }
                        }
                        putAndFinalize(app, bearer, uri, mediaId, uploadUrl,
                                method.isEmpty() ? "PUT" : method, headers,
                                filename, contentType, sizeBytes, listener);
                    }
                    @Override public void onFailure(Call<LmsModels.MapEnvelope> call, Throwable error) {
                        listener.onError("Could not prepare upload: " + error.getMessage());
                    }
                });
    }

    private static void putAndFinalize(Context context, String bearer, Uri uri, String mediaId,
                                       String uploadUrl, String method, Map<String, String> headers,
                                       String filename, String mime, long size, Listener listener) {
        new Thread(() -> {
            try {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                    if (input == null) throw new IllegalStateException("Could not read selected document.");
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                }
                byte[] bytes = output.toByteArray();
                if (bytes.length > MAX_DOCUMENT_BYTES) throw new IllegalStateException("Documents must be 100 MB or smaller.");
                RequestBody body = RequestBody.create(bytes, MediaType.parse(mime));
                Request.Builder builder = new Request.Builder().url(uploadUrl).method(method, body);
                for (Map.Entry<String, String> entry : headers.entrySet()) builder.header(entry.getKey(), entry.getValue());
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .writeTimeout(5, TimeUnit.MINUTES)
                        .readTimeout(5, TimeUnit.MINUTES)
                        .build();
                try (Response put = client.newCall(builder.build()).execute()) {
                    if (!put.isSuccessful()) throw new IllegalStateException("Storage rejected upload (HTTP " + put.code() + ").");
                }
                ApiClient.getLmsService().finalizeMediaUpload(bearer, mediaId, new HashMap<>())
                        .enqueue(new Callback<LmsModels.MapEnvelope>() {
                            @Override public void onResponse(Call<LmsModels.MapEnvelope> call,
                                                             retrofit2.Response<LmsModels.MapEnvelope> response) {
                                LmsModels.MapEnvelope result = response.body();
                                if (response.isSuccessful() && result != null && result.ok) {
                                    listener.onSuccess(mediaId, filename, mime, bytes.length);
                                } else {
                                    listener.onError(result != null ? result.error : "Could not finalize document upload.");
                                }
                            }
                            @Override public void onFailure(Call<LmsModels.MapEnvelope> call, Throwable error) {
                                listener.onError("Could not finalize document: " + error.getMessage());
                            }
                        });
            } catch (Exception error) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(
                        () -> listener.onError(error.getMessage() == null ? "Document upload failed." : error.getMessage()));
            }
        }, "lms-document-upload").start();
    }

    private static String displayName(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) {}
        String last = uri.getLastPathSegment();
        return last == null ? "document" : last;
    }

    private static long fileSize(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (index >= 0) return cursor.getLong(index);
            }
        } catch (Exception ignored) {}
        return -1L;
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
