package com.app.nisisiafrica;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.nisisiafrica.Adapters.ProgrammesAdapter;
import com.app.nisisiafrica.Utils.ProgrammeEnquiryDialog;
import com.app.nisisiafrica.data.remote.ProgrammesDataSource;

public class AllProgrammesActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_all_programmes);
        View headerContent = findViewById(R.id.headerContent);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            headerContent.setPadding(
                    headerContent.getPaddingLeft(),
                    bars.top + Math.round(12 * getResources().getDisplayMetrics().density),
                    headerContent.getPaddingRight(),
                    headerContent.getPaddingBottom());
            v.setPadding(bars.left, 0, bars.right, bars.bottom);
            return insets;
        });
        WindowInsetsControllerCompat barsCtrl =
                ViewCompat.getWindowInsetsController(getWindow().getDecorView());
        if (barsCtrl != null) barsCtrl.setAppearanceLightStatusBars(true);

        TextView tvCount = findViewById(R.id.tvResultCount);
        RecyclerView rv = findViewById(R.id.rvProgrammes);
        rv.setLayoutManager(new LinearLayoutManager(this));
        ProgrammesAdapter adapter = new ProgrammesAdapter(true);
        rv.setAdapter(adapter);
        adapter.setOnProgrammeClick(p ->
                ProgrammeEnquiryDialog.show(this, p != null ? p.title : null));

        ProgrammesDataSource.fetch(list -> {
            adapter.submit(list);
            int n = list == null ? 0 : list.size();
            if (tvCount != null) {
                tvCount.setText(n == 1
                        ? getString(R.string.programmes_count_one)
                        : getString(R.string.programmes_count_label, n));
            }
        });
    }
}
