package com.app.nisisiafrica.Fragments.BaseFragments;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.nisisiafrica.Adapters.ChatAdapter;
import com.app.nisisiafrica.Adapters.ChatRoomAdapter;
import com.app.nisisiafrica.Adapters.ChatSearchAdapter;
import com.app.nisisiafrica.Adapters.PinnedChatAdapter;
import com.app.nisisiafrica.Constants;
import com.app.nisisiafrica.DataBase.AppDatabase;
import com.app.nisisiafrica.Interfaces.NotificationApiService;
import com.app.nisisiafrica.Interfaces.SwipeToReplyCallback;
import com.app.nisisiafrica.ProfileActivity;
import com.app.nisisiafrica.MainActivity;
import com.app.nisisiafrica.R;
import com.app.nisisiafrica.Utils.Util;
import com.app.nisisiafrica.ViewModel.ChatRoomViewModel;
import com.app.nisisiafrica.ViewModel.ChatRoomViewModelFactory;
import com.app.nisisiafrica.ViewModel.ChatViewModel;
import com.app.nisisiafrica.data.Model.ChatMessageEntity;
import com.app.nisisiafrica.data.Model.ChatNotificationRequest;
import com.app.nisisiafrica.data.Model.Chatroom;
import com.app.nisisiafrica.data.Model.NotificationResponse;
import com.app.nisisiafrica.data.Model.UserData;
import com.app.nisisiafrica.data.Repository.ChatRepository;
import com.app.nisisiafrica.data.Repository.ChatRoomRepository;
import com.app.nisisiafrica.data.remote.ApiClient;
import com.app.nisisiafrica.data.remote.FirebaseRemoteDataSource;
import com.app.nisisiafrica.databinding.FragmentChatBinding;
import com.bumptech.glide.Glide;
import com.discord.panels.PanelState;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import kotlin.Unit;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatFragment extends Fragment {
    private ChatViewModel viewModel;
    private ChatAdapter adapter;
    private String chatroomId;
    private ChatRepository chatRepository;

    private FragmentChatBinding binding;
    private ChatRoomViewModel chatRoomViewModel;
    private UserData userData;
    private String joinedAT, role;
    private boolean isPanelOpen, isMentor;
    private View chipNavigationBar;
    private UserData myUserData;
    private String selectedOtherUserId;
    private OnBackPressedCallback backCallback;
    private ConcatAdapter concatAdapter;
    private ChatSearchAdapter searchAdapter;
    private final java.util.List<Chatroom> loadedRooms = new ArrayList<>();
    private java.util.List<Chatroom> pinnedRooms = new ArrayList<>();
    private ChatRoomAdapter pagedAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentChatBinding.inflate(inflater, container, false);

        initConfiguration();
        setupRecyclerView();
        setupGlobalClickListeners();
        setupBackNavigation();

        // Initialize user metadata and global rooms
        handleUserMetadata();
        chatRoomViewModel.initPinnedChats();
        loadMyProfile();

        return binding.getRoot();
    }

    private void initConfiguration() {
        joinedAT = Util.getState(Constants.USER_CREATED_AT, "");
        role = Util.getState(Constants.USER_ROLE, "Mentee");

        ChatRoomRepository repository = new ChatRoomRepository();
        ChatRoomViewModelFactory factory = new ChatRoomViewModelFactory(repository);
        chatRoomViewModel = new ViewModelProvider(this, factory).get(ChatRoomViewModel.class);
    }

    private void setupRecyclerView() {
        // 1. Adapters (Method references for cleaner lambda)
        PinnedChatAdapter pinnedAdapter = new PinnedChatAdapter(chatroom -> {
            openChat(chatroom);
            return Unit.INSTANCE;
        });
        pagedAdapter = new ChatRoomAdapter(chatroom -> {
            openChat(chatroom);
            return Unit.INSTANCE;
        });

        // 2. ConcatAdapter to pin items to top
        concatAdapter = new ConcatAdapter(pinnedAdapter, pagedAdapter);
        binding.rvChats.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvChats.setAdapter(concatAdapter);

        searchAdapter = new ChatSearchAdapter(this::openChat);

        // 3. Observers
        chatRoomViewModel.getPinnedChatRooms().observe(getViewLifecycleOwner(), rooms -> {
            pinnedAdapter.submitList(rooms);
            pinnedRooms = rooms != null ? rooms : new ArrayList<>();
            rebuildLoadedRooms();
        });

        chatRoomViewModel.getChatRooms().observe(getViewLifecycleOwner(), pagingData -> {
            pagedAdapter.submitData(getViewLifecycleOwner().getLifecycle(), pagingData);
        });

        pagedAdapter.addOnPagesUpdatedListener(() -> {
            rebuildLoadedRooms();
            return Unit.INSTANCE;
        });

        setupChatSearch();
    }

    private void rebuildLoadedRooms() {
        loadedRooms.clear();
        loadedRooms.addAll(pinnedRooms);
        if (pagedAdapter != null) {
            for (Chatroom room : pagedAdapter.snapshot().getItems()) {
                if (room != null) loadedRooms.add(room);
            }
        }
    }

    private void setupChatSearch() {
        binding.etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim().toLowerCase(Locale.getDefault());
                if (query.isEmpty()) {
                    binding.rvChats.setAdapter(concatAdapter);
                    return;
                }
                String currentUserId = FirebaseAuth.getInstance().getUid();
                ArrayList<Chatroom> filtered = new ArrayList<>();
                for (Chatroom room : loadedRooms) {
                    String name = room.getOtherUserName(currentUserId);
                    if (name != null && name.toLowerCase(Locale.getDefault()).contains(query)) {
                        filtered.add(room);
                    }
                }
                if (binding.rvChats.getAdapter() != searchAdapter) {
                    binding.rvChats.setAdapter(searchAdapter);
                }
                searchAdapter.submit(filtered);
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });
    }

    private void openChat(Chatroom chatroom) {
        if (chatroom == null) return;

        String currentUserId = FirebaseAuth.getInstance().getUid();
        String chatId = chatroom.getChatroomId();
        String type = chatroom.getType() != null ? chatroom.getType() : "direct";

        binding.overlappingPanels.openEndPanel();
        isPanelOpen = true;
        binding.etMessage.setText("");

        updateChatHeader(chatroom, type, currentUserId);

        // Access Control: only admins may post announcements; everyone else reads.
        if ("system".equals(type)) {
            binding.bottomChatBar.setVisibility("Admin".equals(role) ? View.VISIBLE : View.GONE);
        } else {
            binding.bottomChatBar.setVisibility(View.VISIBLE);
        }

        startRealtimeMessages(chatId);
        setupSendAction(chatId, chatroom);
    }

    private void updateChatHeader(Chatroom chatroom, String type, String currentUserId) {
        binding.btnViewProfile.setVisibility(View.GONE);

        switch (type) {
            case "system":
                binding.tvChatName.setText("Announcements");
                binding.tvChatRole.setText("Official Updates");
                Glide.with(this).load(R.drawable.nisisi_logo).circleCrop().into(binding.tvHeaderAvatar);
                // No partner profile for system rooms — keep the start panel showing "me".
                userData = null;
                selectedOtherUserId = null;
                bindProfilePanel(myUserData);
                break;
            case "ai":
                binding.tvChatName.setText("Nisisi AI Assistant");
                binding.tvChatRole.setText("Virtual Help");
                //todo update
                Glide.with(this).load(R.drawable.cyborg).circleCrop().into(binding.tvHeaderAvatar);
                userData = null;
                selectedOtherUserId = null;
                bindProfilePanel(myUserData);
                break;
            default: // Direct Chat
                binding.btnViewProfile.setVisibility(View.VISIBLE);
                String otherUserId = chatroom.getOtherUserId(currentUserId);
                String otherName = chatroom.getOtherUserName(currentUserId);
                binding.tvChatName.setText(otherName);

                // Reset immediately so the profile panel never shows the previously
                // opened person while the new one is still loading.
                userData = null;
                selectedOtherUserId = otherUserId;
                bindProfilePanelMinimal(otherName);

                if (otherUserId != null) {
                    FirebaseRemoteDataSource.INSTANCE.getRemoteUserData(otherUserId, user -> {
                        if (user == null) return Unit.INSTANCE;
                        // Ignore late callbacks from a previously opened chat.
                        if (!otherUserId.equals(selectedOtherUserId)) return Unit.INSTANCE;
                        userData = user;
                        Glide.with(this).load(user.getPhotoUrl()).circleCrop().into(binding.tvHeaderAvatar);
                        binding.tvChatRole.setText(user.getUserRole());
                        bindProfilePanel(user);
                        return Unit.INSTANCE;
                    }, e -> Unit.INSTANCE);

                    binding.btnViewProfile.setOnClickListener(v -> {
                        Intent intent = new Intent(getActivity(), ProfileActivity.class);
                        intent.putExtra(Constants.USER_ID, otherUserId);
                        startActivity(intent);
                    });
                }
                break;
        }
    }

    private void startRealtimeMessages(String chatId) {

        if (chatRepository != null) {
            chatRepository.clearSyncListener();
        }

        AppDatabase db = AppDatabase.getInstance(requireContext());
        chatRepository = new ChatRepository(db);
        viewModel = new ViewModelProvider(this, new ChatViewModel.Factory(chatRepository))
                .get(ChatViewModel.class);
        adapter = new ChatAdapter();


        // swipe to reply
        // in startRealtimeMessages, after setting adapter
        binding.rvMessages.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
            private float startX, startY;

            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = e.getX();
                        startY = e.getY();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        float dx = Math.abs(e.getX() - startX);
                        float dy = Math.abs(e.getY() - startY);
                        if (dx > dy && dx > 10) {
                            // horizontal swipe — block panels from stealing it
                            rv.getParent().requestDisallowInterceptTouchEvent(true);
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        rv.getParent().requestDisallowInterceptTouchEvent(false);
                        break;
                }
                return false;
            }

            @Override
            public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {}

            @Override
            public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {}
        });
        SwipeToReplyCallback swipeCallback = new SwipeToReplyCallback(
                requireContext(),
                position -> {
                    if (position >= 0) {
                        Object item = adapter.getItemAt(position);
                        if (item instanceof ChatMessageEntity) {
                            triggerReply((ChatMessageEntity) item);
                        }
                    }
                    return Unit.INSTANCE;
                }
        );
        new ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.rvMessages);
         binding.rvMessages.setAdapter(adapter);
         binding.rvMessages.setLayoutManager(new LinearLayoutManager(requireContext()));

        chatRepository.syncMessages(chatId);
        viewModel.loadMessages(chatId);
        viewModel.getMessages().observe(getViewLifecycleOwner(), messages -> {
            adapter.submitList(messages);
            if (!messages.isEmpty())
                binding.rvMessages.smoothScrollToPosition(messages.size() - 1);
        });
    }

    private ChatMessageEntity replyingTo = null;

    private void triggerReply(ChatMessageEntity message) {
        replyingTo = message;
        binding.replyPreview.setVisibility(View.VISIBLE);
        binding.tvReplyText.setText(message.getMessage());
        binding.tvReplySender.setText(message.getSenderName());
        binding.btnCancelReply.setOnClickListener(v -> {
            replyingTo = null;
            binding.replyPreview.setVisibility(View.GONE);
        });
    }

    private void setupSendAction(String chatId, Chatroom chatroom) {
        binding.btnSend.setOnClickListener(v -> {
            String msg = binding.etMessage.getText().toString().trim();
            if (!msg.isEmpty()) {
                binding.etMessage.setText("");
                if (replyingTo != null) {
                    replyingTo = null;
                    binding.replyPreview.setVisibility(View.GONE);
                }
                viewModel.sendMessage(chatId, msg, success -> {
                    if (!success) {
                        Toast.makeText(requireContext(), "Message not sent", Toast.LENGTH_SHORT).show();
                    }
                    String currentUserId = FirebaseAuth.getInstance().getUid();
                    String receiverId = chatroom.getOtherUserId(currentUserId); // correct
                    if (receiverId != null) {
                        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                        user.getIdToken(false).addOnSuccessListener(result -> {
                            String token = "Bearer " + result.getToken();
                            ChatNotificationRequest request = new ChatNotificationRequest(
                                    receiverId,
                                    msg,
                                    chatId
                            );
                            NotificationApiService apiService = ApiClient.getClient().create(NotificationApiService.class);
                            apiService.sendChatNotification(token, request)
                                    .enqueue(new Callback<NotificationResponse>() {
                                        @Override
                                        public void onResponse(Call<NotificationResponse> call, Response<NotificationResponse> response) {}
                                        @Override
                                        public void onFailure(Call<NotificationResponse> call, Throwable t) {
                                            Log.e("ChatNotif", "Failed", t);
                                        }
                                    });
                        });
                    }
                    return Unit.INSTANCE;
                });
            }
        });
    }

//    private void setupSendAction(String chatId) {
//        binding.btnSend.setOnClickListener(v -> {
//            String msg = binding.etMessage.getText().toString().trim();
//            if (!msg.isEmpty()) {
//                binding.etMessage.setText("");
//                viewModel.sendMessage(chatId, msg, success -> {
//                    if (!success) {
//                        Toast.makeText(requireContext(), "Message not sent", Toast.LENGTH_SHORT).show();
//                    }
//                    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
//                    user.getIdToken(false).addOnSuccessListener(result -> {
//                        String token = "Bearer " + result.getToken();
//                        Chatroom chatroom = new Chatroom();
//                        ChatNotificationRequest request = new ChatNotificationRequest(
//                                chatroom.getOtherUserId(chatId),
//                                msg,      // the message content
//                                chatId    // your conversation/chat room ID
//                        );
//
//                        NotificationApiService apiService = ApiClient.getClient().create(NotificationApiService.class);
//
//                        apiService.sendChatNotification(token, request)
//                                .enqueue(new Callback<>() {
//                                    @Override
//                                    public void onResponse(Call<NotificationResponse> call,
//                                                           Response<NotificationResponse> response) {
//                                        // silent, no need to handle
//                                    }
//
//                                    @Override
//                                    public void onFailure(Call<NotificationResponse> call, Throwable t) {
//                                        Log.e("ChatNotif", "Failed to send notification", t);
//                                    }
//                                });
//                    });
//                    return Unit.INSTANCE;
//                });
//
//            }
//        });
//    }

    private void setupGlobalClickListeners() {
        binding.allChatInfo.setOnClickListener(v -> {
            // Panel content is already kept in sync by updateChatHeader (selected
            // chat = their profile; otherwise mine). Just reveal it.
            if (selectedOtherUserId == null && myUserData != null) {
                bindProfilePanel(myUserData);
            }
            binding.overlappingPanels.openStartPanel();
        });

        binding.icBack.setOnClickListener(v -> {
            binding.overlappingPanels.closePanels();
            Util.saveState(Constants.IS_MENTOR, false);
        });
    }

    /**
     * Loads the signed-in user's profile so the start panel always defaults to
     * "me" until another profile is explicitly selected.
     */
    private void loadMyProfile() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        FirebaseRemoteDataSource.INSTANCE.getRemoteUserData(uid, u -> {
            myUserData = u;
            if (userData == null) bindProfilePanel(myUserData);
            return Unit.INSTANCE;
        }, e -> Unit.INSTANCE);
    }

    /** Shows the selected person's name instantly while their full data loads. */
    private void bindProfilePanelMinimal(String name) {
        if (binding == null) return;
        binding.tvProfileName.setText(name != null ? name : "");
        binding.tvProfileRole.setText("");
        binding.tvAbout.setText("");
        binding.email.setText("");
        binding.tvJoined.setText("-");
        Glide.with(requireContext()).load(R.drawable.ic_person).circleCrop().into(binding.tvProfileAvatar);
    }

    private void bindProfilePanel(UserData u) {
        if (u == null || binding == null) return;
        String first = u.getFirstName() != null ? u.getFirstName() : "";
        String last = u.getLastName() != null ? u.getLastName() : "";
        binding.tvProfileName.setText((first + " " + last).trim());
        binding.tvProfileRole.setText(u.getUserRole() != null ? u.getUserRole() : "");
        binding.tvAbout.setText(u.getBio() != null ? u.getBio() : "");
        binding.email.setText(u.getEmail() != null ? u.getEmail() : "");
        Glide.with(requireContext())
                .load(u.getPhotoUrl())
                .placeholder(R.drawable.ic_person)
                .circleCrop()
                .into(binding.tvProfileAvatar);

        binding.joinedTittle.setText("LAST LOGIN");
        if (u.getLastLogin() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            binding.tvJoined.setText(sdf.format(new Date(u.getLastLogin())));
        } else {
            binding.tvJoined.setText("-");
        }
    }

    private void handleUserMetadata() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && joinedAT.isEmpty()) {
            Date createdAtDate = new Date(user.getMetadata().getCreationTimestamp());
            Util.saveState(Constants.USER_CREATED_AT, createdAtDate.toString());
        }
    }

    private void setupBackNavigation() {
        // Disabled by default; only intercepts back while the chat tab is the
        // visible fragment. Otherwise Home's back press would never exit the app.
        backCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                if (isPanelOpen) {
                    binding.overlappingPanels.closePanels();
                    Util.saveState(Constants.IS_MENTOR, false);
                } else if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).navigateToHomeTab();
                }
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), backCallback);
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (backCallback != null) backCallback.setEnabled(!hidden);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if (getActivity() != null) {
            chipNavigationBar = getActivity().findViewById(R.id.chipNavigationBar);
        }

        // Sync back-handling to actual visibility once the preload show/hide settles.
        view.post(() -> {
            if (backCallback != null) backCallback.setEnabled(!isHidden());
        });

        binding.overlappingPanels.registerEndPanelStateListeners(newState -> {
            if (newState instanceof PanelState.Opening || newState instanceof PanelState.Opened) {
                chipNavigationBar.setVisibility(View.GONE);
                isPanelOpen = true;
            } else if (newState instanceof PanelState.Closing || newState instanceof PanelState.Closed) {
                chipNavigationBar.setVisibility(View.VISIBLE);
                isPanelOpen = false;
                Util.saveState(Constants.IS_MENTOR, false);
                // No conversation selected anymore -> start panel defaults back to me.
                userData = null;
                selectedOtherUserId = null;
                bindProfilePanel(myUserData);
            }
            hideKeyboard();
        });
    }

    private void hideKeyboard() {
        View view = getActivity() != null ? getActivity().getCurrentFocus() : null;
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    @Override
    public void onDestroyView() {
        if (chatRepository != null) {
            chatRepository.clearSyncListener();
        }
        super.onDestroyView();
    }
}
