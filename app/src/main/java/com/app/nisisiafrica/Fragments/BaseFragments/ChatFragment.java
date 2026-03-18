package com.app.nisisiafrica.Fragments.BaseFragments;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.app.nisisiafrica.Adapters.ChatAdapter;
import com.app.nisisiafrica.Adapters.ChatRoomAdapter;
import com.app.nisisiafrica.Adapters.PinnedChatAdapter;
import com.app.nisisiafrica.Constants;
import com.app.nisisiafrica.DataBase.AppDatabase;
import com.app.nisisiafrica.Interfaces.NotificationApiService;
import com.app.nisisiafrica.ProfileActivity;
import com.app.nisisiafrica.R;
import com.app.nisisiafrica.Utils.Util;
import com.app.nisisiafrica.ViewModel.ChatRoomViewModel;
import com.app.nisisiafrica.ViewModel.ChatRoomViewModelFactory;
import com.app.nisisiafrica.ViewModel.ChatViewModel;
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

    private FragmentChatBinding binding;
    private ChatRoomViewModel chatRoomViewModel;
    private UserData userData;
    private String joinedAT, role;
    private boolean isPanelOpen, isMentor;
    private View chipNavigationBar;

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
        ChatRoomAdapter pagedAdapter = new ChatRoomAdapter(chatroom -> {
            openChat(chatroom);
            return Unit.INSTANCE;
        });

        // 2. ConcatAdapter to pin items to top
        ConcatAdapter concatAdapter = new ConcatAdapter(pinnedAdapter, pagedAdapter);
        binding.rvChats.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvChats.setAdapter(concatAdapter);

        // 3. Observers
        chatRoomViewModel.getPinnedChatRooms().observe(getViewLifecycleOwner(), pinnedAdapter::submitList);

        chatRoomViewModel.getChatRooms().observe(getViewLifecycleOwner(), pagingData -> {
            pagedAdapter.submitData(getViewLifecycleOwner().getLifecycle(), pagingData);
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

        // Access Control: Mentees can't talk in system channel
        if ("system".equals(type) && "Mentee".equals(role)) {
            binding.bottomChatBar.setVisibility(View.GONE);
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
                break;
            case "ai":
                binding.tvChatName.setText("Nisisi AI Assistant");
                binding.tvChatRole.setText("Virtual Help");
                //todo update
                Glide.with(this).load(R.drawable.cyborg).circleCrop().into(binding.tvHeaderAvatar);
                break;
            default: // Direct Chat
                binding.btnViewProfile.setVisibility(View.VISIBLE);
                String otherUserId = chatroom.getOtherUserId(currentUserId);
                binding.tvChatName.setText(chatroom.getOtherUserName(currentUserId));

                if (otherUserId != null) {
                    FirebaseRemoteDataSource.INSTANCE.getRemoteUserData(otherUserId, user -> {
                        userData = user;
                        Glide.with(this).load(user.getPhotoUrl()).circleCrop().into(binding.tvHeaderAvatar);
                        binding.tvChatRole.setText(user.getUserRole());
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

        AppDatabase db = AppDatabase.getInstance(requireContext());
        ChatRepository repo = new ChatRepository(db);
        viewModel = new ViewModelProvider(this, new ChatViewModel.Factory(repo))
                .get(ChatViewModel.class);
        adapter = new ChatAdapter();
         binding.rvMessages.setAdapter(adapter);
         binding.rvMessages.setLayoutManager(new LinearLayoutManager(requireContext()));

        repo.syncMessages(chatId);
        viewModel.loadMessages(chatId);
        viewModel.getMessages().observe(getViewLifecycleOwner(), messages -> {
            adapter.submitList(messages);
            if (!messages.isEmpty())
                binding.rvMessages.smoothScrollToPosition(messages.size() - 1);
        });
    }
    private void setupSendAction(String chatId, Chatroom chatroom) {
        binding.btnSend.setOnClickListener(v -> {
            String msg = binding.etMessage.getText().toString().trim();
            if (!msg.isEmpty()) {
                binding.etMessage.setText("");
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
            if (userData == null) return;
            Util.saveState(Constants.IS_MENTOR, true);
            isMentor = true;

            binding.tvProfileName.setText(userData.getFirstName() + " " + userData.getLastName());
            binding.tvProfileRole.setText(userData.getUserRole());
            binding.tvAbout.setText(userData.getBio());
            binding.email.setText(userData.getEmail());
            Glide.with(requireContext()).load(userData.getPhotoUrl()).circleCrop().into(binding.tvProfileAvatar);

            binding.joinedTittle.setText("LAST LOGIN");
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            binding.tvJoined.setText(sdf.format(new Date(userData.getLastLogin())));

            binding.overlappingPanels.openStartPanel();
        });

        binding.icBack.setOnClickListener(v -> {
            binding.overlappingPanels.closePanels();
            Util.saveState(Constants.IS_MENTOR, false);
        });
    }

    private void handleUserMetadata() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && joinedAT.isEmpty()) {
            Date createdAtDate = new Date(user.getMetadata().getCreationTimestamp());
            Util.saveState(Constants.USER_CREATED_AT, createdAtDate.toString());
        }
    }

    private void setupBackNavigation() {
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isPanelOpen) {
                    binding.overlappingPanels.closePanels();
                    Util.saveState(Constants.IS_MENTOR, false);
                } else {
                    NavHostFragment.findNavController(ChatFragment.this).navigate(R.id.homeFragment);
                }
            }
        });
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if (getActivity() != null) {
            chipNavigationBar = getActivity().findViewById(R.id.chipNavigationBar);
        }

        binding.overlappingPanels.registerEndPanelStateListeners(newState -> {
            if (newState instanceof PanelState.Opening || newState instanceof PanelState.Opened) {
                chipNavigationBar.setVisibility(View.GONE);
                isPanelOpen = true;
            } else if (newState instanceof PanelState.Closing || newState instanceof PanelState.Closed) {
                chipNavigationBar.setVisibility(View.VISIBLE);
                isPanelOpen = false;
                Util.saveState(Constants.IS_MENTOR, false);
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
}
