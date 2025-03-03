package com.app.nisisiafrica.Auth;

import android.os.Bundle;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.app.customsnackbarlib.CustomSnackbar;
import com.app.nisisiafrica.R;
import com.app.nisisiafrica.SnackbarHandler;

public class LoginSignUpActivity extends AppCompatActivity implements SnackbarHandler {
    private TextView tab1, tab2;
    private View indicator;
    private ViewPager2 viewPager;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootView), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        tab1 = findViewById(R.id.login);
        tab2 = findViewById(R.id.sigUp);
        indicator = findViewById(R.id.indicator);
        viewPager = findViewById(R.id.myViewPager);

        // Set Adapter for ViewPager2
        viewPager.setAdapter(new AuthTabsAdapter(this));
//        viewPager.setOverScrollMode(View.OVER_SCROLL_NEVER);
        viewPager.setUserInputEnabled(true);
        // Link ViewPager2 to the Custom Tab Layout
        new CustomTabLayout(tab1, tab2, indicator, viewPager);
    }

    @Override
    public void showSnackbar(String message, int duration, int type) {
        RelativeLayout rootView = findViewById(R.id.rootView);
        CustomSnackbar.show(rootView, message, duration, type);
    }
}