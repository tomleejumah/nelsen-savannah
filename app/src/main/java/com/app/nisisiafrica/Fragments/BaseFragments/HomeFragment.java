package com.app.nisisiafrica.Fragments.BaseFragments;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.paging.LoadState;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.app.nisisiafrica.Adapters.BannerAdapter;
import com.app.nisisiafrica.Adapters.CoursesAdapter;
import com.app.nisisiafrica.Adapters.EventAdapter;
import com.app.nisisiafrica.Adapters.ProgrammesAdapter;
import com.app.nisisiafrica.Adapters.MentorsAdapter;
import com.app.nisisiafrica.AllProgrammesActivity;
import com.app.nisisiafrica.Adapters.SearchHistoryAdapter;
import com.app.nisisiafrica.Auth.LoginSignUpActivity;
import com.app.nisisiafrica.Constants;
import com.app.nisisiafrica.CreateCommunityActivity;
import com.app.nisisiafrica.CreateEventActivity;
import com.app.nisisiafrica.CreateStoryActivity;
import com.app.nisisiafrica.Interfaces.FirebaseCallback;
import com.app.nisisiafrica.MentorApplicationActivity;
import com.app.nisisiafrica.NotificationsActivity;
import com.app.nisisiafrica.ProfileActivity;
import com.app.nisisiafrica.QuestionnaireActivity;
import com.app.nisisiafrica.R;
import com.app.nisisiafrica.Utils.CalendarBinder;
import com.app.nisisiafrica.Utils.NotificationCounter;
import com.app.nisisiafrica.Utils.Roles;
import com.app.nisisiafrica.data.remote.ProgrammesDataSource;
import com.app.nisisiafrica.Utils.Util;
import com.app.nisisiafrica.AllCoursesActivity;
import com.app.nisisiafrica.AllMentorsActivity;
import com.app.nisisiafrica.StoryViewerActivity;
import com.app.nisisiafrica.Adapters.StoryAdapter;
import com.app.nisisiafrica.data.Model.Story;
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
import com.google.android.material.card.MaterialCardView;
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
import java.util.Objects;
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
    private View fabCreateMain;
    private View speedDial;
    private boolean speedDialOpen = false;

    private ViewPager2 bannerViewPager;
    private BannerAdapter bannerAdapter;
    private List<String> bannerList = new ArrayList<>();
    private DatabaseReference bannersRef;
    private ValueEventListener bannerListener;
    private DatabaseReference storiesRef;
    private ValueEventListener storiesListener;
    private StoryAdapter storyAdapter;
    private final ArrayList<Story> storyList = new ArrayList<>();
    private View storiesContainer;
    private Handler autoScrollHandler;
    private Runnable autoScrollRunnable;
    private static final long SCROLL_DELAY = 4000;

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
        eventAdapter.setOnEventClick(this::showEventActions);
        rvUpcomingEvents = view.findViewById(R.id.rvUpcomingEvents);
        rvUpcomingEvents.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvUpcomingEvents.setAdapter(eventAdapter);

        btnBookMentor = view.findViewById(R.id.btnBookMentor);
        plusIcon = view.findViewById(R.id.plusIcon);
        setupCreateFab(view);

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

            // Floating speed-dial replaced by MainActivity's contextual bottom-bar FAB.
            if (fabCreateMain != null) fabCreateMain.setVisibility(View.GONE);
            collapseSpeedDial();
            if (plusIcon != null) {
                plusIcon.setVisibility(Roles.canCreate(userData.getUserRole()) ? View.VISIBLE : View.GONE);
            }

            // Mentors + Admins: no mentor browse/book — Chats for mentees.
            // Mentees: full mentor list + Book mentor.
            applyMentorListVisibility(!Roles.browsesMentors(userData.getUserRole()), view);

            getEvents(data, view);

        });

        bannerViewPager = view.findViewById(R.id.bannerViewPager);
        bannerAdapter = new BannerAdapter(getContext(), bannerList);
        bannerViewPager.setAdapter(bannerAdapter);

        // Initialize Realtime Database reference
        bannersRef = FirebaseDatabase.getInstance().getReference("banners");

        fetchBannersRealtime();
        setupAutoScroll();

        storiesContainer = view.findViewById(R.id.storiesContainer);
        RecyclerView rvStories = view.findViewById(R.id.rvStories);
        rvStories.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        storyAdapter = new StoryAdapter(getContext(), position -> {
            Intent storyIntent = new Intent(getActivity(), StoryViewerActivity.class);
            storyIntent.putParcelableArrayListExtra(StoryViewerActivity.EXTRA_STORIES, storyList);
            storyIntent.putExtra(StoryViewerActivity.EXTRA_START_INDEX, position);
            startActivity(storyIntent);
        });
        storyAdapter.setOnAddClick(() -> {
            if (Roles.canCreate()) {
                startActivity(new Intent(getActivity(), CreateStoryActivity.class));
            } else {
                Toast.makeText(getContext(), "Mentors create stories from Create", Toast.LENGTH_SHORT).show();
            }
        });
        rvStories.setAdapter(storyAdapter);
        storiesRef = FirebaseDatabase.getInstance().getReference("stories");
        fetchStoriesRealtime();


        btnBookMentor.setOnClickListener(v -> {
            if (!Roles.browsesMentors()) {
                showCreateSheet();
            } else {
                startActivity(new Intent(getActivity(), AllMentorsActivity.class));
            }
        });

        // Mentors/Admins never land on "Book a mentor" from the empty calendar state.
        View emptyBook = view.findViewById(R.id.emptyStateView).findViewById(R.id.btnBookMentor);
        if (emptyBook != null && !Roles.browsesMentors()) {
            emptyBook.setVisibility(View.GONE);
        }

        view.findViewById(R.id.imgNotification).setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), NotificationsActivity.class);
            startActivity(intent);
        });

        if (plusIcon != null) {
            plusIcon.setOnClickListener(v -> showCreateSheet());
        }
        View calendarLayout = view.findViewById(R.id.layoutCalendar);
        if (calendarLayout != null) {
        View monthHeader = calendarLayout.findViewById(R.id.layoutMonthHeader);
        TimelineView timelineView = calendarLayout.findViewById(R.id.timeline);
        if (timelineView != null) timelineView.setVisibility(View.GONE);
        TextView tvMonthTitle = monthHeader != null ? monthHeader.findViewById(R.id.tvMonthTitle) : null;
        TextView dateHeader = calendarLayout.findViewById(R.id.dateHeader);
        txtDateInfo = calendarLayout.findViewById(R.id.txtDateInfo);
        if (dateHeader != null) dateHeader.setText("Your Calendar");
        YearMonth currentMonth = YearMonth.now();
        if (tvMonthTitle != null) {
            tvMonthTitle.setText(currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")));
        }

        CalendarView calendarView = view.findViewById(R.id.calendarView);
        if (calendarView != null) {
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
        if (tvMonthTitle != null) binder.setup(calendarView, tvMonthTitle);
        }
        }

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

        view.findViewById(R.id.emptyStateView).findViewById(R.id.btnBookMentor).setOnClickListener(v -> {
            if (!Roles.browsesMentors()) {
                showCreateSheet();
            } else {
                startActivity(new Intent(getActivity(), AllMentorsActivity.class));
            }
        });

        view.findViewById(R.id.tvSeeMore).setOnClickListener(v -> {
            if (Roles.browsesMentors()) {
                startActivity(new Intent(getActivity(), AllMentorsActivity.class));
            }
        });

        rcCourses = view.findViewById(R.id.rcCourses);
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false);
        rcCourses.setLayoutManager(layoutManager);
        coursesAdapter = new CoursesAdapter(getContext());
        rcCourses.setAdapter(coursesAdapter);

        sharedViewModel.getCourses().observe(getViewLifecycleOwner(), pagingData -> {
            coursesAdapter.submitData(getLifecycle(), pagingData);
        });

        RecyclerView rcProgrammes = view.findViewById(R.id.rcProgrammes);
        if (rcProgrammes != null) {
            rcProgrammes.setLayoutManager(new LinearLayoutManager(getContext(),
                    LinearLayoutManager.HORIZONTAL, false));
            ProgrammesAdapter programmesAdapter = new ProgrammesAdapter(false);
            rcProgrammes.setAdapter(programmesAdapter);
            programmesAdapter.setOnProgrammeClick(p ->
                    startActivity(new Intent(getActivity(), AllProgrammesActivity.class)));
            ProgrammesDataSource.fetch(programmesAdapter::submit);
        }
        View seeAllProgrammes = view.findViewById(R.id.seeAllProgrammes);
        if (seeAllProgrammes != null) {
            seeAllProgrammes.setOnClickListener(v ->
                    startActivity(new Intent(getActivity(), AllProgrammesActivity.class)));
        }

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


        view.findViewById(R.id.scrollView).setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            scrollChangeListener.onParentScroll(oldScrollY, scrollY);
        });

        view.findViewById(R.id.txtRecognizeMe).setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), QuestionnaireActivity.class);
            startActivity(intent);
        });

        view.findViewById(R.id.imgDp).setOnClickListener(v -> {
            Log.d(TAG, "onCreateView: clicked ");
            PopupMenu popup = new PopupMenu(getContext(), v);
            popup.getMenu().add("Profile");
            popup.getMenu().add("Search");
            popup.getMenu().add("Our Programmes");
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
                    case "Our Programmes":
                        showToolsSheet();
                        break;
                    case "Logout":
                        prefs = requireContext().getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE);
                        prefs.edit().remove("initial_token_written").apply();
                        FirebaseRemoteDataSource.INSTANCE.signOutAll(getContext(), () -> {
                            startActivity(new Intent(getActivity(), LoginSignUpActivity.class));
                            return Unit.INSTANCE;
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

        TextView showAll = view.findViewById(R.id.showMoreMentors);
        TextView txtSeeAll = view.findViewById(R.id.seeAll);
        txtSeeAll.setPaintFlags(txtSeeAll.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        View.OnClickListener listener = this::goToViewAll;
        txtSeeAll.setOnClickListener(listener);
        showAll.setOnClickListener(listener);

        notifCounter = view.findViewById(R.id.notifCounter);
        imgNotification = view.findViewById(R.id.imgNotification);

        view.findViewById(R.id.btnRecMe).setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), QuestionnaireActivity.class);
            startActivity(intent);
        });

        view.findViewById(R.id.btnDonate).setOnClickListener(v -> {

        });

        view.findViewById(R.id.btnOurShop).setOnClickListener(v -> {

        });

        view.findViewById(R.id.btnBeMentor).setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), MentorApplicationActivity.class);
            startActivity(intent);
        });


        return view;
    }

    private void setupAutoScroll() {
        autoScrollHandler = new Handler(Looper.getMainLooper());
        autoScrollRunnable = new Runnable() {
            @Override
            public void run() {
                if (bannerAdapter.getItemCount() > 1) {
                    int nextItem = (bannerViewPager.getCurrentItem() + 1) % bannerAdapter.getItemCount();
                    bannerViewPager.setCurrentItem(nextItem, true);
                }
                autoScrollHandler.postDelayed(this, SCROLL_DELAY);
            }
        };
        autoScrollHandler.postDelayed(autoScrollRunnable, SCROLL_DELAY);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (autoScrollHandler != null && autoScrollRunnable != null) {
            autoScrollHandler.removeCallbacks(autoScrollRunnable);
        }
        if (bannersRef != null && bannerListener != null) {
            bannersRef.removeEventListener(bannerListener);
            bannerListener = null;
        }
        if (storiesRef != null && storiesListener != null) {
            storiesRef.removeEventListener(storiesListener);
            storiesListener = null;
        }
    }

    private void fetchStoriesRealtime() {
        if (storiesListener != null) {
            storiesRef.removeEventListener(storiesListener);
        }
        storiesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                storyList.clear();
                long now = System.currentTimeMillis();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Story story = child.getValue(Story.class);
                    boolean expired = story != null && story.expiresAt > 0 && now > story.expiresAt;
                    if (story != null && story.active && !expired && story.mediaUrl != null
                            && !story.mediaUrl.isEmpty()) {
                        story.storyId = child.getKey();
                        storyList.add(story);
                    }
                }
                if (storyAdapter != null) storyAdapter.submit(storyList);
                if (storiesContainer != null) {
                    storiesContainer.setVisibility(View.VISIBLE);
                }
                View emptyHint = getView() != null
                        ? getView().findViewById(R.id.tvStoriesEmptyHint) : null;
                if (emptyHint != null) {
                    emptyHint.setVisibility(storyList.isEmpty() ? View.VISIBLE : View.GONE);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Stories fetch failed: " + error.getMessage());
            }
        };
        storiesRef.addValueEventListener(storiesListener);
    }

    private void fetchBannersRealtime() {
        if (bannerListener != null) {
            bannersRef.removeEventListener(bannerListener);
        }
        bannerListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<String> finalUrls = new ArrayList<>();

                for (DataSnapshot child : snapshot.getChildren()) {
                    String url = child.child("url").getValue(String.class);
                    if (url != null && !url.isEmpty()) {
                        finalUrls.add(url);
                    }
                }

                if (finalUrls.isEmpty()) {
                    String packageName = requireContext().getPackageName();
                    finalUrls.add("android.resource://" + packageName + "/" + R.drawable.static_banner1);
                    finalUrls.add("android.resource://" + packageName + "/" + R.drawable.static_banner2);
                }

                if (bannerAdapter != null) {
                    bannerAdapter.updateBanners(finalUrls);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Banner fetch failed: " + error.getMessage());
            }
        };
        bannersRef.addValueEventListener(bannerListener);
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
    }

    private void setupCreateFab(View view) {
        fabCreateMain = view.findViewById(R.id.fabCreateMain);
        speedDial = view.findViewById(R.id.speedDial);

        fabCreateMain.setOnClickListener(v -> toggleSpeedDial());

        view.findViewById(R.id.fabEvent).setOnClickListener(v -> {
            collapseSpeedDial();
            startActivity(new Intent(getActivity(), CreateEventActivity.class));
        });
        view.findViewById(R.id.fabStory).setOnClickListener(v -> {
            collapseSpeedDial();
            startActivity(new Intent(getActivity(), CreateStoryActivity.class));
        });
        view.findViewById(R.id.fabCommunity).setOnClickListener(v -> {
            collapseSpeedDial();
            startActivity(new Intent(getActivity(), CreateCommunityActivity.class));
        });
    }

    private void toggleSpeedDial() {
        if (speedDialOpen) collapseSpeedDial();
        else expandSpeedDial();
    }

    private void expandSpeedDial() {
        if (speedDial == null || fabCreateMain == null) return;
        speedDialOpen = true;
        speedDial.setVisibility(View.VISIBLE);
        fabCreateMain.animate().rotation(45f).setDuration(200).start();
    }

    private void collapseSpeedDial() {
        if (speedDial == null || fabCreateMain == null) return;
        speedDialOpen = false;
        speedDial.setVisibility(View.GONE);
        fabCreateMain.animate().rotation(0f).setDuration(200).start();
    }

    public void showCreateSheet() {
        if (getContext() == null) return;
        BottomSheetDialog sheet = new BottomSheetDialog(getContext());
        View sheetView = getLayoutInflater().inflate(R.layout.sheet_create, null);
        sheet.setContentView(sheetView);

        sheetView.findViewById(R.id.optCreateEvent).setOnClickListener(v -> {
            sheet.dismiss();
            startActivity(new Intent(getActivity(), CreateEventActivity.class));
        });
        sheetView.findViewById(R.id.optCreateStory).setOnClickListener(v -> {
            sheet.dismiss();
            startActivity(new Intent(getActivity(), CreateStoryActivity.class));
        });
        sheetView.findViewById(R.id.optCreateCommunity).setOnClickListener(v -> {
            sheet.dismiss();
            startActivity(new Intent(getActivity(), CreateCommunityActivity.class));
        });

        sheet.show();
    }

    private void showToolsSheet() {
        BottomSheetDialog sheet = new BottomSheetDialog(getContext());
        View view = getLayoutInflater().inflate(R.layout.home_options_sheet, null);
        sheet.setContentView(view);
        sheet.show();

        MaterialCardView selaProgram = view.findViewById(R.id.selaProgram);
        MaterialCardView boysRoom = view.findViewById(R.id.boysRoom);
        MaterialCardView ScriptureSafari = view.findViewById(R.id.ScriptureSafari);
        MaterialCardView CodeLab = view.findViewById(R.id.CodeLab);
        //todo handle these clicks

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
        if (v.getId() == R.id.seeAll) {
            startActivity(new Intent(getActivity(), AllCoursesActivity.class));
        } else if (v.getId() == R.id.showMoreMentors) {
            if (Roles.browsesMentors()) {
                startActivity(new Intent(getActivity(), AllMentorsActivity.class));
            }
        }
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

    /**
     * Shows a bottom sheet of contextual actions for an event: add to calendar,
     * and either join the meeting (online, when it's time) or view the location.
     */
    private void showEventActions(Event event) {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext());
        View sheet = LayoutInflater.from(requireContext())
                .inflate(R.layout.sheet_event_actions, null);

        TextView title = sheet.findViewById(R.id.tvSheetTitle);
        TextView subtitle = sheet.findViewById(R.id.tvSheetSubtitle);
        View btnPrimary = sheet.findViewById(R.id.btnPrimaryAction);
        TextView btnPrimaryText = sheet.findViewById(R.id.btnPrimaryActionText);
        View btnCalendar = sheet.findViewById(R.id.btnAddCalendar);

        String t = event.getTitle();
        title.setText(t != null && !t.trim().isEmpty() ? t
                : ("Session with " + (event.getMentorName() != null ? event.getMentorName() : "")));

        boolean online = com.app.nisisiafrica.Utils.EventActions.isOnline(event);
        String place = online ? "Online meeting" : event.getLocation();
        subtitle.setText(event.getStartTime() + " - " + event.getEndTime()
                + (place != null && !place.isEmpty() ? "  \u2022  " + place : ""));

        boolean joinTime = online && com.app.nisisiafrica.Utils.EventActions.isJoinTime(event);
        if (online) {
            btnPrimaryText.setText(joinTime ? "Join Google Meet" : "Add to Google Calendar");
            btnPrimary.setOnClickListener(v -> {
                if (joinTime) {
                    com.app.nisisiafrica.Utils.EventActions.joinMeeting(requireContext(), event);
                } else {
                    com.app.nisisiafrica.Utils.EventActions.addToCalendar(requireContext(), event);
                }
                dialog.dismiss();
            });
            btnCalendar.setVisibility(joinTime ? View.VISIBLE : View.GONE);
            btnCalendar.setOnClickListener(v -> {
                com.app.nisisiafrica.Utils.EventActions.addToCalendar(requireContext(), event);
                dialog.dismiss();
            });
        } else {
            btnPrimaryText.setText("Add to Google Calendar");
            btnPrimary.setOnClickListener(v -> {
                com.app.nisisiafrica.Utils.EventActions.addToCalendar(requireContext(), event);
                dialog.dismiss();
            });
            btnCalendar.setVisibility(View.VISIBLE);
            ((TextView) sheet.findViewById(R.id.btnAddCalendarText)).setText("View location");
            btnCalendar.setOnClickListener(v -> {
                com.app.nisisiafrica.Utils.EventActions.openLocation(requireContext(), event);
                dialog.dismiss();
            });
        }

        dialog.setContentView(sheet);
        dialog.show();
    }

    private void applyMentorListVisibility(boolean isMentorOrAdmin, View view) {
        int vis = isMentorOrAdmin ? View.GONE : View.VISIBLE;
        View header = view.findViewById(R.id.mentorsSectionHeader);
        if (header != null) header.setVisibility(vis);
        if (rcMentors != null) rcMentors.setVisibility(vis);
        // Empty-state CTA: mentors get Create, mentees get Book.
        if (btnBookMentor != null && isMentorOrAdmin) {
            btnBookMentor.setText("Create");
        }
    }

    private void getEvents(UserData userData, View view) {
        eventViewModel.getEvents().observe(getViewLifecycleOwner(), events -> {

            if (events.isEmpty()) {
                view.findViewById(R.id.emptyStateView).setVisibility(View.VISIBLE);
                rvUpcomingEvents.setVisibility(View.GONE);
                btnBookMentor.setText(!Roles.browsesMentors(userData.getUserRole())
                        ? "Create" : "Book a mentor");
            } else {
                view.findViewById(R.id.emptyStateView).setVisibility(View.GONE);
                rvUpcomingEvents.setVisibility(View.VISIBLE);

                // Filter for upcoming events
                List<Event> upcoming = events.stream()
                        .filter(e -> e.getDate() >= System.currentTimeMillis())
                        .limit(3)
                        .collect(Collectors.toList());

                eventAdapter.submitList(upcoming);
                view.findViewById(R.id.tvSeeMore).setVisibility(events.size() <= 3 ? View.GONE : View.VISIBLE);
            }
        });

        // 2. Trigger the actual data fetch
        eventViewModel.fetchEvents(Util.getState(Constants.CURRENT_USER_ID, ""));
    }

    @Override
    public void onCoursesFetched(@NotNull List<@NotNull CourseItem> courses) {
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