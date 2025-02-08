package com.app.nisisiafrica.Auth;

import android.animation.ObjectAnimator;
import android.graphics.Color;
import android.view.View;
import android.widget.TextView;

import androidx.viewpager2.widget.ViewPager2;

public class CustomTabLayout {
    private TextView tab1, tab2;
    private View indicator;

    public CustomTabLayout(TextView tab1, TextView tab2, View indicator, ViewPager2 viewPager) {
        this.tab1 = tab1;
        this.tab2 = tab2;
        this.indicator = indicator;

        // Set default selected tab
        updateTabState(0);

        // Initialize indicator width based on tab1 width after layout has been measured
        tab1.post(() -> {
            indicator.getLayoutParams().width = tab1.getWidth();
            indicator.requestLayout();
        });

        // Click Listeners for Tabs
        tab1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                viewPager.setCurrentItem(0);
            }
        });

        tab2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                viewPager.setCurrentItem(1);
            }
        });

        // ViewPager Listener
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                animateIndicator(position);
                updateTabState(position);
            }
        });
    }

    private void animateIndicator(int position) {
        float translationX = (position == 0) ? 0f : tab1.getWidth();
        ObjectAnimator animator = ObjectAnimator.ofFloat(indicator, "translationX", translationX);
        animator.setDuration(200);
        animator.start();
    }

    private void updateTabState(int position) {
        if (position == 0) {
            tab1.setTextColor(Color.BLACK);
            tab2.setTextColor(Color.GRAY);
        } else {
            tab1.setTextColor(Color.GRAY);
            tab2.setTextColor(Color.BLACK);
        }
    }
}
