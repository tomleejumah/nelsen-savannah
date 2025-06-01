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
import com.google.firebase.auth.ActionCodeSettings;

public class LoginSignUpActivity extends AppCompatActivity implements SnackbarHandler {

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
        TextView tab1 = findViewById(R.id.login);
        TextView tab2 = findViewById(R.id.sigUp);
        View indicator = findViewById(R.id.indicator);
        ViewPager2 viewPager = findViewById(R.id.myViewPager);

//        ActionCodeSettings

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