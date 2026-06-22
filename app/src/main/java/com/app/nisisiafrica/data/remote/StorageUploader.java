package com.app.nisisiafrica.data.remote;

import android.net.Uri;

import androidx.annotation.NonNull;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.UUID;

/**
 * Tiny helper that uploads a picked image {@link Uri} to Firebase Storage and
 * returns the public download URL. Used by stories, community posts/avatars and
 * chat image messages.
 */
public final class StorageUploader {

    public interface UploadCallback {
        void onComplete(boolean success, String url);
    }

    private StorageUploader() {}

    public static void upload(Uri uri, String folder, @NonNull UploadCallback cb) {
        if (uri == null) {
            cb.onComplete(false, null);
            return;
        }
        String path = folder + "/" + UUID.randomUUID();
        StorageReference ref = FirebaseStorage.getInstance().getReference(path);
        ref.putFile(uri)
                .continueWithTask(task -> {
                    if (!task.isSuccessful() && task.getException() != null) {
                        throw task.getException();
                    }
                    return ref.getDownloadUrl();
                })
                .addOnSuccessListener(downloadUri -> cb.onComplete(true, downloadUri.toString()))
                .addOnFailureListener(e -> cb.onComplete(false, null));
    }
}
