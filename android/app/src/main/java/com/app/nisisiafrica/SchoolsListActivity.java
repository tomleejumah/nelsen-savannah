package com.app.nisisiafrica;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.nisisiafrica.data.Model.LmsModels;
import com.app.nisisiafrica.data.remote.ApiClient;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * School picker for mentees — Enroll Schools.
 * Lists membership schools first, then Explore others from GET /lms/schools/catalog.
 */
public class SchoolsListActivity extends AppCompatActivity {

    public static final String EXTRA_EXPLORE = "explore";

    private final List<Row> rows = new ArrayList<>();
    private SchoolsAdapter adapter;
    private TextView tvEmpty;
    private TextView tvHint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_schools_list);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        MaterialToolbar bar = findViewById(R.id.topAppBar);
        bar.setNavigationOnClickListener(v -> finish());
        tvEmpty = findViewById(R.id.tvSchoolsEmpty);
        tvHint = findViewById(R.id.tvSchoolsHint);

        RecyclerView rv = findViewById(R.id.rvSchools);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SchoolsAdapter();
        rv.setAdapter(adapter);

        load();
    }

    private void load() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Sign in to browse schools", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        user.getIdToken(false).addOnSuccessListener(tokenResult -> {
            String bearer = "Bearer " + tokenResult.getToken();
            ApiClient.getLmsService().me(bearer).enqueue(new Callback<>() {
                @Override
                public void onResponse(@NonNull Call<LmsModels.MeEnvelope> call,
                                       @NonNull Response<LmsModels.MeEnvelope> response) {
                    Set<String> mine = new HashSet<>();
                    List<Row> mineRows = new ArrayList<>();
                    LmsModels.MeEnvelope body = response.body();
                    if (response.isSuccessful() && body != null && body.ok && body.data != null) {
                        Object mem = body.data.get("memberships");
                        if (mem instanceof List<?> list) {
                            for (Object o : list) {
                                if (!(o instanceof Map<?, ?> m)) continue;
                                Object status = m.get("status");
                                if (status != null && !"active".equals(String.valueOf(status))) continue;
                                String sid = str(m.get("schoolId"));
                                String name = str(m.get("schoolName"));
                                if (sid.isEmpty()) continue;
                                mine.add(sid);
                                mineRows.add(new Row(sid, name.isEmpty() ? sid : name, true));
                            }
                        }
                    }
                    loadCatalog(bearer, mine, mineRows);
                }

                @Override
                public void onFailure(@NonNull Call<LmsModels.MeEnvelope> call, @NonNull Throwable t) {
                    loadCatalog(bearer, new HashSet<>(), new ArrayList<>());
                }
            });
        });
    }

    private void loadCatalog(String bearer, Set<String> mineIds, List<Row> mineRows) {
        ApiClient.getLmsService().schoolsCatalog(bearer).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<LmsModels.SchoolsEnvelope> call,
                                   @NonNull Response<LmsModels.SchoolsEnvelope> response) {
                rows.clear();
                rows.addAll(mineRows);
                LmsModels.SchoolsEnvelope body = response.body();
                if (response.isSuccessful() && body != null && body.ok
                        && body.data != null && body.data.schools != null) {
                    for (LmsModels.SchoolDto s : body.data.schools) {
                        if (s == null || s.schoolId == null || s.schoolId.isEmpty()) continue;
                        if (mineIds.contains(s.schoolId)) continue;
                        rows.add(new Row(s.schoolId, s.name != null ? s.name : s.schoolId, false));
                    }
                }
                if (mineRows.isEmpty()) {
                    tvHint.setText("Choose a school to see what they offer. Enrolling joins that school.");
                } else {
                    tvHint.setText("Your schools first — Explore others below.");
                }
                adapter.notifyDataSetChanged();
                tvEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onFailure(@NonNull Call<LmsModels.SchoolsEnvelope> call, @NonNull Throwable t) {
                rows.clear();
                rows.addAll(mineRows);
                adapter.notifyDataSetChanged();
                tvEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
                Toast.makeText(SchoolsListActivity.this,
                        "Could not load schools", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private void openSchool(Row row) {
        Intent i = new Intent(this, AllCoursesActivity.class);
        i.putExtra(AllCoursesActivity.EXTRA_SCHOOL_ID, row.schoolId);
        i.putExtra(AllCoursesActivity.EXTRA_SCHOOL_NAME, row.name);
        startActivity(i);
    }

    private static final class Row {
        final String schoolId;
        final String name;
        final boolean mine;

        Row(String schoolId, String name, boolean mine) {
            this.schoolId = schoolId;
            this.name = name;
            this.mine = mine;
        }
    }

    private class SchoolsAdapter extends RecyclerView.Adapter<SchoolsAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_school_row, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Row row = rows.get(position);
            holder.name.setText(row.name);
            holder.meta.setText(row.mine ? "Your school" : "Explore");
            holder.itemView.setOnClickListener(v -> openSchool(row));
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final TextView name;
            final TextView meta;

            VH(View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.tvSchoolName);
                meta = itemView.findViewById(R.id.tvSchoolMeta);
            }
        }
    }
}
