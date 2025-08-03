package com.app.nisisiafrica.Fragments.HomeFragments;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.app.nisisiafrica.MainActivity;
import com.app.nisisiafrica.Model.UserData;
import com.app.nisisiafrica.R;
import com.app.nisisiafrica.SharedUserViewModel;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.zen.overlapimagelistview.OverlapImageListView;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {
    private SharedUserViewModel viewModel;
    private UserData userData;
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        overlapImage(view);
        viewModel = new ViewModelProvider(requireActivity()).get(SharedUserViewModel.class);
        viewModel.getUserData().observe(getViewLifecycleOwner(), data -> {
            if (data != null) {
                Log.d("HomeFragment", "User First Name: " + data.getFirstName());
                userData = data;
                Toast.makeText(getActivity(), "Welcome " + data.getFirstName(), Toast.LENGTH_SHORT).show();
            }else Log.d("HomeFragment", "User data is null");
        });

        return view;
    }

    private void overlapImage(View view) {
        if (getActivity() == null) return;
        OverlapImageListView overlapImage = view.findViewById(R.id.overlapImage);

        ArrayList<Bitmap> imageList = new ArrayList<>();

        List<Integer> imageResourceList = new ArrayList<>();
        imageResourceList.add(R.drawable.ic_check_green);
        imageResourceList.add(R.drawable.ic_google);
        imageResourceList.add(R.drawable.ic_facebook);

        for (int i = 0; i < imageResourceList.size(); i++) {
            int resId = imageResourceList.get(i);
            Glide.with(getActivity().getApplicationContext())
                    .asBitmap()
                    .load(resId)
                    .apply(RequestOptions.circleCropTransform())
                    .into(new CustomTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(@NonNull Bitmap resource,
                                                    @Nullable Transition<? super Bitmap> transition) {
                            if (getActivity() == null) return;
                            imageList.add(resource);

                            // set the image after everything is loaded
                            if (imageList.size() == imageResourceList.size()) {
                                overlapImage.setImageList(imageList);
                            }
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {
                            // no-op
                        }
                    });
        }
    }
}