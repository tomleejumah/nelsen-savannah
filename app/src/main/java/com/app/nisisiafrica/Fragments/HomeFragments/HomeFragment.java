package com.app.nisisiafrica.Fragments.HomeFragments;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import com.app.nisisiafrica.CoursesAdapter;
import com.app.nisisiafrica.Model.CourseItem;
import com.app.nisisiafrica.SearchHistoryAdapter;
import com.app.nisisiafrica.Model.UserData;
import com.app.nisisiafrica.R;
import com.app.nisisiafrica.SharedUserViewModel;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.zen.overlapimagelistview.OverlapImageListView;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {
    private SharedUserViewModel viewModel;
    private UserData userData;
    private SearchView searchView;
    private RecyclerView historyList,rcCourses;
    private SearchHistoryAdapter searchHistoryAdapter;
    private CoursesAdapter coursesAdapter;
    private List<CourseItem>courseItemsList = new ArrayList<>();
    private SharedPreferences prefs;
    private Gson gson = new Gson();
    private Type type = new TypeToken<List<String>>() {}.getType();
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_home, container, false);

//        overlapImage(view);
        viewModel = new ViewModelProvider(requireActivity()).get(SharedUserViewModel.class);
        viewModel.getUserData().observe(getViewLifecycleOwner(), data -> {
            if (data != null) {
                Log.d("HomeFragment", "User First Name: " + data.getFirstName());
                userData = data;
                Toast.makeText(getActivity(), "Welcome " + data.getFirstName(), Toast.LENGTH_SHORT).show();
            }else Log.d("HomeFragment", "User data is null");
        });
        TextView txtSeeAll = view.findViewById(R.id.seeAll);
        txtSeeAll.setPaintFlags(txtSeeAll.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        txtSeeAll.setOnClickListener(v -> {
            //todo Open view all
        });



        searchView = view.findViewById(R.id.search_view);
        historyList = view.findViewById(R.id.history_list);
        prefs = requireContext().getSharedPreferences("search_prefs", Context.MODE_PRIVATE);

        historyList.setLayoutManager(new LinearLayoutManager(getContext()));
        searchHistoryAdapter = new SearchHistoryAdapter(getSearchHistory(), query -> {
            searchView.setQuery(query, true); // Fill and submit
        });
        historyList.setAdapter(searchHistoryAdapter);

        searchView.setOnClickListener(v -> {
            //todo
        });

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                saveSearchHistory(query);
                searchHistoryAdapter.updateList(getSearchHistory());
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });

        //courses
        rcCourses = view.findViewById(R.id.rcCourses);
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false);
        rcCourses.setLayoutManager(layoutManager);
        //coursesList //todo fetch from db(firebase)
        courseItemsList.add(new CourseItem("heye","heye","Juma Tomlee","Data Structures",
                "4 hrs","4","heye"));
        courseItemsList.add(new CourseItem("heye","heye","Juma Tomlee","Data Structures",
                "4 hrs","4","heye"));
        courseItemsList.add(new CourseItem("heye","heye","Juma Tomlee","Data Structures",
                "4 hrs","4","heye"));
        courseItemsList.add(new CourseItem("heye","heye","Juma Tomlee","Data Structures",
                "4 hrs","4","heye"));
        courseItemsList.add(new CourseItem("heye","heye","Juma Tomlee","Data Structures",
                "4 hrs","4","heye"));
        courseItemsList.add(new CourseItem("heye","heye","Juma Tomlee","Data Structures",
                "4 hrs","4","heye"));
        coursesAdapter = new CoursesAdapter(false,courseItemsList,getContext());
        rcCourses.setAdapter(coursesAdapter);
//        coursesAdapter.notifyAll();


        return view;
    }

    private void saveSearchHistory(String query) {
        List<String> history = getSearchHistory();
        history.remove(query);
        history.add(0, query);
        if (history.size() > 5) {
            history = history.subList(0, 5);
        }
        prefs.edit().putString("history", gson.toJson(history)).apply();
    }
    private List<String> getSearchHistory() {
        String json = prefs.getString("history", "[]");
        return gson.fromJson(json, type);
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