package com.app.nisisiafrica;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.nisisiafrica.Utils.Roles;
import com.app.nisisiafrica.data.remote.StorageUploader;
import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin-only management of the rotating home-screen banners stored at
 * {@code banners/{key}/url} in Realtime Database. HomeFragment falls back to
 * bundled images when the node is empty.
 */
public class BannerAdminActivity extends AppCompatActivity {

    private final List<Banner> banners = new ArrayList<>();
    private BannerAdapter adapter;
    private DatabaseReference bannersRef;
    private ValueEventListener bannersListener;

    private ProgressBar progress;
    private TextView tvEmpty;
    private ActivityResultLauncher<PickVisualMediaRequest> imagePicker;

    /** A banner row: the RTDB key plus its image URL. */
    private static final class Banner {
        final String key;
        final String url;
        Banner(String key, String url) { this.key = key; this.url = url; }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_banner_admin);

        if (!Roles.canManageApp()) {
            Toast.makeText(this, "Admins only", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        progress = findViewById(R.id.progress);
        tvEmpty = findViewById(R.id.tvEmpty);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        adapter = new BannerAdapter();
        RecyclerView rv = findViewById(R.id.rvBanners);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        imagePicker = registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(),
                uri -> { if (uri != null) uploadBanner(uri); });

        ExtendedFloatingActionButton fab = findViewById(R.id.fabAddBanner);
        fab.setOnClickListener(v -> imagePicker.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build()));

        bannersRef = FirebaseDatabase.getInstance().getReference("banners");
        observeBanners();
    }

    private void observeBanners() {
        bannersListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                banners.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    String url = child.child("url").getValue(String.class);
                    if (url != null && !url.isEmpty()) {
                        banners.add(new Banner(child.getKey(), url));
                    }
                }
                adapter.notifyDataSetChanged();
                tvEmpty.setVisibility(banners.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(BannerAdminActivity.this,
                        "Couldn't load banners", Toast.LENGTH_SHORT).show();
            }
        };
        bannersRef.addValueEventListener(bannersListener);
    }

    private void uploadBanner(Uri uri) {
        progress.setVisibility(View.VISIBLE);
        StorageUploader.upload(uri, "banners", (success, url) -> {
            if (isFinishing() || isDestroyed()) return;
            if (!success || url == null) {
                progress.setVisibility(View.GONE);
                Toast.makeText(this, "Upload failed", Toast.LENGTH_SHORT).show();
                return;
            }
            Map<String, Object> value = new HashMap<>();
            value.put("url", url);
            value.put("createdAt", System.currentTimeMillis());
            bannersRef.push().setValue(value)
                    .addOnCompleteListener(task -> {
                        if (isFinishing() || isDestroyed()) return;
                        progress.setVisibility(View.GONE);
                        if (!task.isSuccessful()) {
                            Toast.makeText(this, "Couldn't save banner", Toast.LENGTH_SHORT).show();
                        }
                    });
        });
    }

    private void confirmDelete(Banner banner) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Remove banner?")
                .setMessage("It will stop showing on the home screen.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (d, w) -> bannersRef.child(banner.key).removeValue())
                .show();
    }

    @Override
    protected void onDestroy() {
        if (bannersRef != null && bannersListener != null) {
            bannersRef.removeEventListener(bannersListener);
            bannersListener = null;
        }
        super.onDestroy();
    }

    private class BannerAdapter extends RecyclerView.Adapter<BannerAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_banner_admin, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Banner banner = banners.get(position);
            holder.url.setText(banner.url);
            Glide.with(holder.itemView.getContext())
                    .load(banner.url)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .into(holder.image);
            holder.delete.setOnClickListener(v -> confirmDelete(banner));
        }

        @Override
        public int getItemCount() {
            return banners.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final ImageView image;
            final TextView url;
            final ImageButton delete;

            VH(@NonNull View itemView) {
                super(itemView);
                image = itemView.findViewById(R.id.ivBanner);
                url = itemView.findViewById(R.id.tvBannerUrl);
                delete = itemView.findViewById(R.id.btnDeleteBanner);
            }
        }
    }
}
