package com.app.nisisiafrica.Fragments.BaseFragments;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.app.nisisiafrica.Constants;
import com.app.nisisiafrica.EditProfileActivity;
import com.app.nisisiafrica.NotificationsActivity;
import com.app.nisisiafrica.R;
import com.app.nisisiafrica.Utils.Util;
import com.app.nisisiafrica.ViewModel.UserViewModel;
import com.app.nisisiafrica.data.Model.UserData;
import com.bumptech.glide.Glide;

import java.util.Locale;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * Current-user profile screen. Hosts the existing SettingsFragment below an
 * enhanced profile header so all account actions stay in one place.
 */
public class ProfileFragment extends Fragment {

    private UserViewModel userViewModel;
    private boolean isMentor = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        CircleImageView avatar = view.findViewById(R.id.profileAvatar);
        TextView avatarInitial = view.findViewById(R.id.profileAvatarInitial);
        TextView name = view.findViewById(R.id.profileName);
        TextView role = view.findViewById(R.id.profileRole);
        TextView email = view.findViewById(R.id.profileEmail);

        view.findViewById(R.id.btnEditProfile).setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), EditProfileActivity.class);
            intent.putExtra(Constants.CURRENT_USER_ID, Util.getState(Constants.CURRENT_USER_ID, ""));
            intent.putExtra(Constants.IS_MENTOR, isMentor);
            startActivity(intent);
        });

        view.findViewById(R.id.btnNotifications).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), NotificationsActivity.class)));

        userViewModel = new ViewModelProvider(requireActivity()).get(UserViewModel.class);
        userViewModel.getUserData().observe(getViewLifecycleOwner(), user -> {
            if (user == null) return;
            bindHeader(user, avatar, avatarInitial, name, role, email);
        });

        UserData current = userViewModel.getUserData().getValue();
        if (current == null) {
            String uid = Util.getState(Constants.CURRENT_USER_ID, "");
            if (!uid.isEmpty()) {
                userViewModel.fetchingCurrentUserDataFromDB(uid);
            }
        } else {
            bindHeader(current, avatar, avatarInitial, name, role, email);
        }

        if (getChildFragmentManager().findFragmentById(R.id.profileSettingsContainer) == null) {
            getChildFragmentManager()
                    .beginTransaction()
                    .replace(R.id.profileSettingsContainer, new SettingsFragment())
                    .commit();
        }

        // Remove the nested Settings screen's own toolbar so there's no double header.
        view.findViewById(R.id.profileSettingsContainer).post(() -> {
            View nestedToolbar = view.findViewById(R.id.toolbar_settings);
            if (nestedToolbar != null) nestedToolbar.setVisibility(View.GONE);
        });
    }

    private void bindHeader(UserData user, CircleImageView avatar, TextView avatarInitial,
                            TextView name, TextView role, TextView email) {
        isMentor = "Mentor".equals(user.getUserRole());

        String displayName = user.getDisplayName();
        if (displayName == null || displayName.trim().isEmpty()) {
            String first = user.getFirstName() != null ? user.getFirstName() : "";
            String last = user.getLastName() != null ? user.getLastName() : "";
            displayName = (first + " " + last).trim();
        }
        name.setText(displayName.isEmpty() ? "Your Profile" : displayName);
        role.setText(user.getUserRole() != null ? user.getUserRole() : "Mentee");
        if (user.getEmail() != null) {
            email.setText(user.getEmail());
            email.setVisibility(View.VISIBLE);
        } else {
            email.setVisibility(View.GONE);
        }

        String photo = user.getPhotoUrl();
        String initial = initialFor(displayName, user.getFirstName());
        if (photo != null && !photo.isEmpty() && !"default".equals(photo)) {
            avatarInitial.setVisibility(View.GONE);
            Glide.with(this)
                    .load(photo)
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .into(avatar);
        } else {
            avatar.setImageDrawable(null);
            avatar.setCircleBackgroundColor(
                    androidx.core.content.ContextCompat.getColor(requireContext(), R.color.maroon_700));
            avatarInitial.setText(initial);
            avatarInitial.setVisibility(View.VISIBLE);
        }
    }

    private String initialFor(String displayName, String firstName) {
        if (!TextUtils.isEmpty(firstName)) {
            return firstName.substring(0, 1).toUpperCase(Locale.getDefault());
        }
        if (!TextUtils.isEmpty(displayName)) {
            return displayName.trim().substring(0, 1).toUpperCase(Locale.getDefault());
        }
        return "?";
    }
}
