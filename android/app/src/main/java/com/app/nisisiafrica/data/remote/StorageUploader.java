package com.app.nisisiafrica.data.remote;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;

import com.app.nisisiafrica.Interfaces.MediaApiService;
import com.app.nisisiafrica.data.Model.MediaUploadResponse;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.UUID;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Uploads picked media to the Nelsen Savannah API ({@code POST /media/upload})
 * so files live on our server under {@code /uploads/app/...}.
 */
public final class StorageUploader {

    private static final String TAG = "StorageUploader";

    public interface UploadCallback {
        void onComplete(boolean success, String url);
    }

    private StorageUploader() {}

    public static void upload(Uri uri, String folder, @NonNull UploadCallback cb) {
        if (uri == null) {
            cb.onComplete(false, null);
            return;
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            cb.onComplete(false, null);
            return;
        }
        Context ctx = FirebaseApp.getInstance().getApplicationContext();
        user.getIdToken(false).addOnSuccessListener(result -> {
            try {
                byte[] bytes = readAll(ctx.getContentResolver(), uri);
                if (bytes == null || bytes.length == 0) {
                    cb.onComplete(false, null);
                    return;
                }
                String mime = ctx.getContentResolver().getType(uri);
                if (mime == null || mime.isEmpty()) mime = "image/jpeg";
                String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
                if (ext == null || ext.isEmpty()) ext = "jpg";
                String filename = UUID.randomUUID() + "." + ext;

                RequestBody fileBody = RequestBody.create(bytes, MediaType.parse(mime));
                MultipartBody.Part filePart =
                        MultipartBody.Part.createFormData("file", filename, fileBody);
                RequestBody folderBody =
                        RequestBody.create(folder != null ? folder : "misc",
                                MediaType.parse("text/plain"));

                MediaApiService service = ApiClient.getClient().create(MediaApiService.class);
                service.upload("Bearer " + result.getToken(), filePart, folderBody)
                        .enqueue(new Callback<MediaUploadResponse>() {
                            @Override
                            public void onResponse(@NonNull Call<MediaUploadResponse> call,
                                                   @NonNull Response<MediaUploadResponse> response) {
                                MediaUploadResponse body = response.body();
                                if (response.isSuccessful() && body != null
                                        && body.getUrl() != null && !body.getUrl().isEmpty()) {
                                    cb.onComplete(true, body.getUrl());
                                } else {
                                    Log.e(TAG, "upload failed code=" + response.code());
                                    cb.onComplete(false, null);
                                }
                            }

                            @Override
                            public void onFailure(@NonNull Call<MediaUploadResponse> call,
                                                  @NonNull Throwable t) {
                                Log.e(TAG, "upload failed", t);
                                cb.onComplete(false, null);
                            }
                        });
            } catch (Exception e) {
                Log.e(TAG, "upload prepare failed", e);
                cb.onComplete(false, null);
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "token failed", e);
            cb.onComplete(false, null);
        });
    }

    private static byte[] readAll(ContentResolver cr, Uri uri) throws Exception {
        try (InputStream in = cr.openInputStream(uri);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) return null;
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }
}
