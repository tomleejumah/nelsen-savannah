package com.app.nisisiafrica.Auth;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.app.nisisiafrica.Auth.Fragments.LoginFragment;
import com.app.nisisiafrica.Auth.Fragments.SignUpFragment;

public class AuthTabsAdapter extends FragmentStateAdapter {
    public AuthTabsAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return switch (position) {
            case 1 -> new SignUpFragment();
            default -> new LoginFragment();
        };
    }

    @Override
    public int getItemCount() {
        return 2;
    }
}
