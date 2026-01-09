package com.app.nisisiafrica.Fragments.BaseFragments;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SearchView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.paging.LoadState;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.nisisiafrica.Adapters.CoursesAdapter;
import com.app.nisisiafrica.Adapters.EventAdapter;
import com.app.nisisiafrica.Adapters.MentorsAdapter;
import com.app.nisisiafrica.Adapters.SearchHistoryAdapter;
import com.app.nisisiafrica.Auth.LoginSignUpActivity;
import com.app.nisisiafrica.Constants;
import com.app.nisisiafrica.CreateEventActivity;
import com.app.nisisiafrica.Interfaces.FirebaseCallback;
import com.app.nisisiafrica.NotificationsActivity;
import com.app.nisisiafrica.ProfileActivity;
import com.app.nisisiafrica.QuestionnaireActivity;
import com.app.nisisiafrica.R;
import com.app.nisisiafrica.Utils.CalendarBinder;
import com.app.nisisiafrica.Utils.NotificationCounter;
import com.app.nisisiafrica.Utils.Util;
import com.app.nisisiafrica.ViewAllActivity;
import com.app.nisisiafrica.ViewModel.EventViewModel;
import com.app.nisisiafrica.ViewModel.EventViewModelFactory;
import com.app.nisisiafrica.ViewModel.SharedViewModel;
import com.app.nisisiafrica.ViewModel.UserViewModel;
import com.app.nisisiafrica.data.Model.CourseItem;
import com.app.nisisiafrica.data.Model.Event;
import com.app.nisisiafrica.data.Model.MentorItem;
import com.app.nisisiafrica.data.Model.UserData;
import com.app.nisisiafrica.data.Repository.EventRepository;
import com.app.nisisiafrica.data.remote.FirebaseRemoteDataSource;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.github.vipulasri.timelineview.TimelineView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.common.reflect.TypeToken;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.gson.Gson;
import com.kizitonwose.calendar.view.CalendarView;
import com.zen.overlapimagelistview.OverlapImageListView;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import de.hdodenhof.circleimageview.CircleImageView;
import kotlin.Unit;

public class HomeFragment extends Fragment implements FirebaseCallback {
    private static final String TAG = "HomeFragment";
    private final Gson gson = new Gson();
    private final Type type = new TypeToken<List<String>>() {
    }.getType();
    private Set<LocalDate> mySchedule = new HashSet<>();
    private UserData userData;
    private SearchView searchView;
    private int pendingRequests = 0;
    private RecyclerView historyList, rcCourses, rcMentors;
    private SearchHistoryAdapter searchHistoryAdapter;
    private CoursesAdapter coursesAdapter;
    private MentorsAdapter mentorsAdapter;
    private List<CourseItem> courseItemsList = new ArrayList<>();
    private List<MentorItem> mentorItemsList = new ArrayList<>();
    private onScrollChangeListener scrollChangeListener;
    private SharedPreferences prefs;
    private String role;
    private TextView txtDateInfo;
    private TextView notifCounter;
    private ImageView imgNotification;
    private Button btnBookMentor;
    private EventViewModel eventViewModel;
    private RecyclerView rvUpcomingEvents;
    private EventAdapter eventAdapter;
    private ImageView plusIcon;
//todo make a init views method!!!!!!!

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof onScrollChangeListener) {
            scrollChangeListener = (onScrollChangeListener) context;
        } else {
            throw new RuntimeException(context
                    + " must implement OnScrollChangeListener");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        UserViewModel userViewModel = new ViewModelProvider(requireActivity()).get(UserViewModel.class);
        EventRepository repository = new EventRepository();
        EventViewModelFactory factory = new EventViewModelFactory(repository);
        eventViewModel = new ViewModelProvider(this, factory).get(EventViewModel.class);

        eventAdapter = new EventAdapter();
        rvUpcomingEvents = view.findViewById(R.id.rvUpcomingEvents);
        rvUpcomingEvents.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvUpcomingEvents.setAdapter(eventAdapter);

        btnBookMentor = view.findViewById(R.id.btnBookMentor);
        plusIcon = view.findViewById(R.id.plusIcon);

        CircleImageView imgDp = view.findViewById(R.id.imgDp);
        userViewModel.getUserData().observe(getViewLifecycleOwner(), data -> {
            if (data == null) return;

            userData = data;
            onUserDataReceived(data);
            Glide.with(this)
                    .load(data.getPhotoUrl())
//                    .placeholder(R.drawable.donation)
//                        .error(R.drawable.ic_error)
                    .into(imgDp);

            if (userData.getUserRole().equals("Mentor")) {
                plusIcon.setVisibility(View.VISIBLE);
            } else {
                plusIcon.setVisibility(View.GONE);
            }

            getEvents(data, view);

        });

        btnBookMentor.setOnClickListener(v -> {
            if (userData.getUserRole().equals("Mentor")) {
                Intent intent = new Intent(getActivity(), CreateEventActivity.class);
                startActivity(intent);
            } else {
                Intent intent = new Intent(getActivity(), ViewAllActivity.class);
                intent.putExtra("isCourses", false);
                startActivity(intent);
            }
        });

        view.findViewById(R.id.imgNotification).setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), NotificationsActivity.class);
            startActivity(intent);
        });

        view.findViewById(R.id.plusIcon).setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), CreateEventActivity.class);
            startActivity(intent);
        });

        View calendarLayout = view.findViewById(R.id.layoutCalendar);
        View monthHeader = calendarLayout.findViewById(R.id.layoutMonthHeader);
        TimelineView timelineView = calendarLayout.findViewById(R.id.timeline);
        timelineView.setVisibility(View.GONE);
        TextView tvMonthTitle = monthHeader.findViewById(R.id.tvMonthTitle);
        TextView dateHeader = calendarLayout.findViewById(R.id.dateHeader);
        txtDateInfo = calendarLayout.findViewById(R.id.txtDateInfo);
        dateHeader.setText("Your Calender");
        YearMonth currentMonth = YearMonth.now();
        tvMonthTitle.setText(currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")));

        CalendarView calendarView = view.findViewById(R.id.calendarView);
        userViewModel.getBookedDates(Util.
                        getState(Constants.CURRENT_USER_ID, ""))
                .observe(getViewLifecycleOwner(), bookings -> {
                    if (bookings != null) {
                        Set<LocalDate> dates = bookings.stream()
                                .map(b -> LocalDate.parse(b.getDate()))
                                .collect(Collectors.toSet());
                        mySchedule.addAll(dates);
                        calendarView.notifyCalendarChanged();
                    }
                });

        CalendarBinder binder = new CalendarBinder(
                requireContext(),
                null,
                mySchedule,
                false,
                date -> {
                    // Handle date selection in fragment
                    calendarView.notifyCalendarChanged();
                }
        );
        binder.setup(calendarView, tvMonthTitle);

        searchView = view.findViewById(R.id.search_view);
        historyList = view.findViewById(R.id.history_list);
        prefs = requireContext().getSharedPreferences("search_prefs", Context.MODE_PRIVATE);

        historyList.setLayoutManager(new LinearLayoutManager(getContext()));
        searchHistoryAdapter = new SearchHistoryAdapter(getSearchHistory(), query -> {
            searchView.setQuery(query, true);
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
        SharedViewModel sharedViewModel = new ViewModelProvider(this).get(SharedViewModel.class);

        //todo update
        view.findViewById(R.id.emptyStateView).findViewById(R.id.btnBookMentor).setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), ViewAllActivity.class);
            intent.putExtra("isCourses", false);
            startActivity(intent);
        });

        view.findViewById(R.id.tvSeeMore).setOnClickListener(v -> {
                    Intent intent = new Intent(getActivity(), ViewAllActivity.class);
                    intent.putExtra("isCourses", false);
                    startActivity(intent);
                }
        );

        rcCourses = view.findViewById(R.id.rcCourses);
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false);
        rcCourses.setLayoutManager(layoutManager);
        coursesAdapter = new CoursesAdapter(getContext());
        rcCourses.setAdapter(coursesAdapter);

        sharedViewModel.getCourses().observe(getViewLifecycleOwner(), pagingData -> {
            coursesAdapter.submitData(getLifecycle(), pagingData);
        });
        //todo mentee url
        List<String> studentimages = new ArrayList<>();
        studentimages.add("url");
        studentimages.add("url");
        studentimages.add("url");

        //getting mentors from db
        rcMentors = view.findViewById(R.id.rcMentorList);
        LinearLayoutManager layoutManager1 = new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false);
        rcMentors.setLayoutManager(layoutManager1);
        mentorsAdapter = new MentorsAdapter(false, getContext());
        rcMentors.setAdapter(mentorsAdapter);

        sharedViewModel.getMentors().observe(getViewLifecycleOwner(), pagingData -> {
            mentorsAdapter.submitData(getLifecycle(), pagingData);
        });
        mentorsAdapter.addLoadStateListener(loadState -> {
            if (loadState.getRefresh() instanceof LoadState.NotLoading) {
                rcMentors.post(() -> {
                    mentorsAdapter.notifyDataSetChanged(); // Force rebind
                    rcMentors.measure(
                            View.MeasureSpec.makeMeasureSpec(rcMentors.getWidth(), View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                    );
                    ViewGroup.LayoutParams params = rcMentors.getLayoutParams();
                    params.height = rcMentors.getMeasuredHeight();
                    rcMentors.setLayoutParams(params);
                });
            }
            return Unit.INSTANCE;
        });


       /* mentorsAdapter.addLoadStateListener(loadState -> {
            if (loadState.getRefresh() instanceof LoadState.NotLoading) {
                rcMentors.post(() -> {
                    ViewGroup.LayoutParams params = rcMentors.getLayoutParams();
                    params.height = calculateRecyclerViewHeight();
                    rcMentors.setLayoutParams(params);
                });
            }
            return Unit.INSTANCE;
        });




        mentorsAdapter.addLoadStateListener(loadState -> {
            if (loadState.getRefresh() instanceof LoadState.NotLoading) {
                rcMentors.post(() -> {
                    rcMentors.requestLayout();
                });
            }
            return Unit.INSTANCE;
        });

        */

        view.findViewById(R.id.main).setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            scrollChangeListener.onParentScroll(oldScrollY, scrollY);
        });

        view.findViewById(R.id.txtRecorgnizeMe).setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), QuestionnaireActivity.class);
            startActivity(intent);
        });

        view.findViewById(R.id.imgDp).setOnClickListener(v -> {
            Log.d(TAG, "onCreateView: clicked ");
            PopupMenu popup = new PopupMenu(getContext(), v);
            popup.getMenu().add("Profile");
            popup.getMenu().add("Search");
            popup.getMenu().add("More");
            popup.getMenu().add("Logout");

            popup.setOnMenuItemClickListener(item -> {
                String title = item.getTitle().toString();

                switch (title) {
                    case "Profile":
                        Intent intent = new Intent(getActivity(), ProfileActivity.class);
                        intent.putExtra(Constants.IS_MENTOR, false);
                        intent.putExtra(Constants.CURRENT_USER_ID, userData.getId());
                        startActivity(intent);
                        break;
                    case "Search":
                        //open search activity
                        break;
                    case "More":
                        showToolsSheet();
                        break;
                    case "Logout":
                        FirebaseRemoteDataSource.INSTANCE.signOutAll(getContext(), () -> {
                            startActivity(new Intent(getActivity(), LoginSignUpActivity.class));
                            return null;
                        });
                        break;
                }

                return true;
            });

            popup.show();

        });

        view.findViewById(R.id.imgNotification).setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), NotificationsActivity.class);
            startActivity(intent);
        });

        TextView showAll = view.findViewById(R.id.ShowALl);
        TextView txtSeeAll = view.findViewById(R.id.seeAll);
        txtSeeAll.setPaintFlags(txtSeeAll.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        View.OnClickListener listener = this::goToViewAll;
        txtSeeAll.setOnClickListener(listener);
        showAll.setOnClickListener(listener);

        notifCounter = view.findViewById(R.id.notifCounter);
        imgNotification = view.findViewById(R.id.imgNotification);

//        fetchMentors();
//        fetchCourses();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        setupNotificationCounter();
    }

    @Override
    public void onPause() {
        super.onPause();
        NotificationCounter.stopListening();
    }

    private void setupNotificationCounter() {
        NotificationCounter.startListening(count -> {
            if (count > 0) {
                notifCounter.setVisibility(View.VISIBLE);
                notifCounter.setText(count > 99 ? "99+" : String.valueOf(count));
            } else {
                notifCounter.setVisibility(View.GONE);
            }
        });

        imgNotification.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), NotificationsActivity.class);
            startActivity(intent);
        });
    }

    private void showToolsSheet() {
        BottomSheetDialog sheet = new BottomSheetDialog(getContext());
        View view = getLayoutInflater().inflate(R.layout.home_options_sheet, null);
        sheet.setContentView(view);
        sheet.show();
    }


    private void fetchMentors() {
        FirebaseRemoteDataSource.INSTANCE.fetchMentors(this);
    }

    private void fetchCourses() {
        FirebaseRemoteDataSource.INSTANCE.fetchCourses(this);
    }

    private void getMentorsID() {
        DatabaseReference rolesRef = FirebaseDatabase.getInstance()
                .getReference("roles");

        List<String> mentorIds = new ArrayList<>();

        rolesRef.orderByValue().equalTo("Mentor")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        mentorIds.clear();
                        for (DataSnapshot child : snapshot.getChildren()) {
                            String userId = child.getKey(); // get the userId
                            mentorIds.add(userId);
                        }
                        onMentorsIDFetched(mentorIds);
                        Log.d("FirebaseQuery", "Mentors: " + mentorIds);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        onError(error.toException());
                    }
                });

    }

    private void goToViewAll(View v) {
        Intent intent = new Intent(getActivity(), ViewAllActivity.class);
        if (v.getId() == R.id.seeAll) {
            // going to view Courses
            intent.putExtra("isCourses", true);

        } else if (v.getId() == R.id.ShowALl) {
            // going to view Mentors
            intent.putExtra("isCourses", false);
        }
        startActivity(intent);
    }

    private int calculateRecyclerViewHeight() {
        int itemCount = Math.min(mentorItemsList.size(), 3);
        return itemCount * 250;
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


    @Override
    public void onMentorsIDFetched(@org.jetbrains.annotations.Nullable List<@org.jetbrains.annotations.Nullable String> mentorIds) {
        if (mentorIds == null) return;
        pendingRequests = mentorIds.size();
        for (String mentorId : mentorIds) {
            if (mentorId == null) break;

//            FirebaseDataBaseHelper.INSTANCE.getMentorData(mentorId,this );
        }
    }

    @Override
    public void onMentorsFetched(@NotNull List<@NotNull MentorItem> mentors) {
   /*     Log.d("DEBUG", "Mentors received: " + mentors.size());
        mentorItemsList.clear();
        Collections.shuffle(mentors);
        mentorItemsList.addAll(mentors);
        Log.d("DEBUG", "List size after add: " + mentorItemsList.size());
        mentorsAdapter.notifyDataSetChanged();

        ViewGroup.LayoutParams params = rcMentors.getLayoutParams();
        params.height = calculateRecyclerViewHeight();
        rcMentors.setLayoutParams(params);

    */
    }
//    @Override
//    public void onMentorsFetched(@NotNull List<@NotNull MentorItem> mentors) {

    /// /        List<MentorItem> mentorItems = new ArrayList<>();
    /// /        mentorItems.add(mentors);
    /// /        pendingRequests--;
    /// /        if (pendingRequests == 0) {
//        mentorItemsList.clear();
//        mentorItemsList.addAll(mentors);
//        mentorsAdapter.notifyDataSetChanged();

//          }
//    }
    @Override
    public void onError(@org.jetbrains.annotations.Nullable Exception e) {
        Log.e("FirebaseQuery", "Error: ", e);
    }

    @Override
    public void onMentorDataFetched(@org.jetbrains.annotations.Nullable MentorItem mentors) {
    }

    @Override
    public void onUserDataReceived(@org.jetbrains.annotations.Nullable UserData userData) {
        if (userData == null) return;
//        getEvents(userData);
//        role = userData.getUserRole();
//        if (role.equals("Mentor")) {
//            txtDateInfo.setText("• RED Underline: Your Schedules");
//
//        } else {
//            txtDateInfo.setText("• Blue Underline: Your schedules");
//        }

    }

    private void getEvents(UserData userData, View view) {
        eventViewModel.getUserEvents().observe(getViewLifecycleOwner(), events -> {

            if (events.isEmpty()) {
                view.findViewById(R.id.emptyStateView).setVisibility(View.VISIBLE);
                rvUpcomingEvents.setVisibility(View.GONE);
                btnBookMentor.setText(Objects.equals(userData.getUserRole(), "Mentor") ? "Add Notification" : "Book a mentor");

            } else {
                view.findViewById(R.id.emptyStateView).setVisibility(View.GONE);
                rvUpcomingEvents.setVisibility(View.VISIBLE);
//                adapter.submitList(events);
                List<Event> upcoming = events.stream()
                        .filter(e -> e.getDate() >= System.currentTimeMillis())
                        .limit(3)
                        .collect(Collectors.toList());
                eventAdapter.submitList(upcoming);
            }
            view.findViewById(R.id.tvSeeMore).setVisibility(events.size() <= 3 ? View.GONE : View.VISIBLE);
        });
    }

    @Override
    public void onCoursesFetched(@NotNull List<@NotNull CourseItem> courses) {
//    Collections.shuffle(courses);
//        courseItemsList.addAll(courses);
//        coursesAdapter.notifyDataSetChanged();
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

    public interface onScrollChangeListener {
        void onParentScroll(int oldY, int newY);
    }
}