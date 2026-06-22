package com.app.nisisiafrica.Utils;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.zen.overlapimagelistview.OverlapImageListView;

import java.util.ArrayList;
import java.util.List;

/**
 * Loads up to three circular avatars into an {@link OverlapImageListView}. Hides
 * the view when there's nothing to show. Used by mentor and community lists.
 */
public final class OverlapImages {

    private OverlapImages() {}

    public static void load(OverlapImageListView overlap, List<String> urls) {
        if (overlap == null) return;
        final List<String> clean = new ArrayList<>();
        if (urls != null) {
            for (String u : urls) {
                if (u != null && !u.isEmpty()) clean.add(u);
                if (clean.size() == 3) break;
            }
        }
        // Tag guards against recycled rows applying a stale async result.
        overlap.setTag(clean);
        if (clean.isEmpty()) {
            overlap.setVisibility(View.GONE);
            return;
        }
        overlap.setVisibility(View.VISIBLE);
        final ArrayList<Bitmap> bitmaps = new ArrayList<>();
        final int total = clean.size();
        for (int i = 0; i < total; i++) {
            Glide.with(overlap.getContext())
                    .asBitmap()
                    .load(clean.get(i))
                    .apply(RequestOptions.circleCropTransform())
                    .into(new CustomTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(@NonNull Bitmap resource,
                                                    @Nullable Transition<? super Bitmap> transition) {
                            bitmaps.add(resource);
                            if (bitmaps.size() == total && overlap.getTag() == clean) {
                                overlap.setImageList(bitmaps);
                            }
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {
                        }
                    });
        }
    }
}
