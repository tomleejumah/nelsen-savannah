package com.app.nisisiafrica;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.nisisiafrica.Adapters.ProgrammesAdapter;
import com.app.nisisiafrica.data.remote.ProgrammesDataSource;
import com.google.android.material.appbar.MaterialToolbar;

public class AllProgrammesActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_all_programmes);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        toolbar.setNavigationOnClickListener(v -> finish());

        RecyclerView rv = findViewById(R.id.rvProgrammes);
        rv.setLayoutManager(new LinearLayoutManager(this));
        ProgrammesAdapter adapter = new ProgrammesAdapter(true);
        rv.setAdapter(adapter);
        adapter.setOnProgrammeClick(p -> {
            Toast.makeText(this, "Apply: " + p.title, Toast.LENGTH_SHORT).show();
            // Web apply surface — open site programmes when available.
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://nelsensavanna.co.ke/")));
            } catch (Exception ignored) {}
        });

        ProgrammesDataSource.fetch(adapter::submit);
    }
}
