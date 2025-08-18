package com.trinitymirror.fabtobottomnavigation.util;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.ViewCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class BottomNavigationViewBehavior extends CoordinatorLayout.Behavior<View> {


    public interface Callback {
        void onSlideUp();

        void onSlideDown();
    }

    private @NonNull
    final Callback callback;

    public BottomNavigationViewBehavior(@NonNull Callback callback) {
        this.callback = callback;
    }

    @Override
    public boolean onStartNestedScroll(@NonNull CoordinatorLayout coordinatorLayout,
                                       View child, @NonNull
                                               View directTargetChild, @NonNull View target,
                                       int axes, int type) {
        return axes == ViewCompat.SCROLL_AXIS_VERTICAL;
    }

    @Override
    public void onNestedScroll(@NonNull CoordinatorLayout coordinatorLayout, @NonNull View child,
                               @NonNull View target, int dxConsumed, int dyConsumed,
                               int dxUnconsumed, int dyUnconsumed,
                               @ViewCompat.NestedScrollType int type) {
        if (dyConsumed > 0) {
            callback.onSlideUp();
        } else if (dyConsumed < 0) {
            callback.onSlideDown();
        }
    }

    @Override
    public void onStopNestedScroll(@NonNull CoordinatorLayout coordinatorLayout, @NonNull View child, @NonNull View target, int type) {
        super.onStopNestedScroll(coordinatorLayout, child, target, type);
    }
}
