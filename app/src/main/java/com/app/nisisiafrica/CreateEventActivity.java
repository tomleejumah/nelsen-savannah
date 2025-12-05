package com.app.nisisiafrica;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.ozcanalasalvar.datepicker.view.datepicker.DatePicker;
import com.ozcanalasalvar.datepicker.view.timepicker.TimePicker;

public class CreateEventActivity extends AppCompatActivity {
    TextView activeTarget = null;
    LinearLayout pickerPanel;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_create_event);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        pickerPanel = findViewById(R.id.pickerPanel);

        TextView tvStart = findViewById(R.id.tvStartTime);
        TextView tvEnd = findViewById(R.id.tvEndTime);
        TextView tvDate = findViewById(R.id.tvDate);

        DatePicker datePicker = findViewById(R.id.datePicker);
        TimePicker timePicker = findViewById(R.id.timePicker);

        Button btnDone = findViewById(R.id.btnPickerDone);

        tvStart.setOnClickListener(v -> showPicker(tvStart));
        tvEnd.setOnClickListener(v -> showPicker(tvEnd));
        tvDate.setOnClickListener(v -> showPicker(tvDate));


        btnDone.setOnClickListener(v -> {
            if (activeTarget == null) return;

            // extract values
//            int day = datePicker .getDay();
//            int month = datePicker.getMonth();
//            int year = datePicker.getYear();
//
//            int hour = timePicker.getHour();
//            int min = timePicker.getMinute();
//
//            String dateStr = day + "/" + (month + 1) + "/" + year;
//            String timeStr = String.format("%02d:%02d", hour, min);

            // assign back to selected target
//            if (activeTarget == tvStart || activeTarget == tvEnd) {
////                activeTarget.setText(timeStr);
//            } else if (activeTarget == tvDate) {
//                activeTarget.setText(dateStr);
//            }

            pickerPanel.setVisibility(View.GONE);
        });
    }

    private void showPicker(TextView target) {
        activeTarget = target;
        pickerPanel.setVisibility(View.VISIBLE);
    }
}