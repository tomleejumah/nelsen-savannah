package com.app.nisisiafrica.Fragments.BaseFragments;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.paging.Pager;
import androidx.paging.PagingConfig;
import androidx.paging.PagingLiveData;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.app.nisisiafrica.Adapters.ChatAdapter;
import com.app.nisisiafrica.Adapters.ChatRoomAdapter;
import com.app.nisisiafrica.Constants;
import com.app.nisisiafrica.ProfileActivity;
import com.app.nisisiafrica.R;
import com.app.nisisiafrica.Utils.Util;
import com.app.nisisiafrica.ViewModel.ChatViewModel;
import com.app.nisisiafrica.ViewModel.ChatViewModelFactory;
import com.app.nisisiafrica.ViewModel.UserViewModel;
import com.app.nisisiafrica.data.Model.ChatMessage;
import com.app.nisisiafrica.data.Model.UserData;
import com.app.nisisiafrica.data.Repository.ChatRepository;
import com.app.nisisiafrica.data.remote.FirebaseRemoteDataSource;
import com.app.nisisiafrica.databinding.FragmentChatBinding;
import com.bumptech.glide.Glide;
import com.discord.panels.PanelState;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import kotlin.Unit;

public class ChatFragment extends Fragment {
    View chipNavigationBar;
    String chatroomId;
    String otherUserId;
    String otherUserRole;
    String joinedAT;
    private FragmentChatBinding binding;
    private ChatRoomAdapter adapter;
    private ChatViewModel viewModel;
    private String role;
    private UserData userData;
    private boolean isPanelOpen, isMentor;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentChatBinding.inflate(inflater, container, false);
        joinedAT = Util.getState(Constants.USER_CREATED_AT, "");
        ChatRepository repository = new ChatRepository();
        ChatViewModelFactory factory = new ChatViewModelFactory(repository);
        viewModel = new ViewModelProvider(this, factory).get(ChatViewModel.class);
        role = Util.getState(Constants.USER_ROLE, "Mentee");

        adapter = new ChatRoomAdapter(chatroom -> {
            chatroomId = chatroom.getChatroomId().isEmpty() ? "announcements" : chatroom.getChatroomId();
            String currentUserId = Util.getState(Constants.CURRENT_USER_ID, "");
            binding.overlappingPanels.openEndPanel();
            isPanelOpen = true;

            ChatAdapter messageAdapter = new ChatAdapter();
            binding.rvMessages.setLayoutManager(new LinearLayoutManager(requireContext()));
            binding.rvMessages.setAdapter(messageAdapter);

            // Real-time listener
            FirebaseRemoteDataSource.INSTANCE.getMessagesRealtime(chatroomId, messages -> {
                messageAdapter.submitList(messages);
                if (!messages.isEmpty()) {
                    binding.rvMessages.scrollToPosition(messages.size() - 1);
                }
                return Unit.INSTANCE;
            });

            // Send button
            binding.btnSend.setOnClickListener(v -> {
                String msg = binding.etMessage.getText().toString().trim();
                if (!msg.isEmpty()) {
                    FirebaseRemoteDataSource.INSTANCE.sendMessage(chatroomId, msg, success -> {
                        if (success) {
                            binding.etMessage.setText("");
                            binding.rvMessages.postDelayed(() -> {
                                int count = messageAdapter.getItemCount();
                                if (count > 0) {
                                    binding.rvMessages.smoothScrollToPosition(count - 1);
                                }
                            }, 100);
                        }
                        return Unit.INSTANCE;
                    });
                }
            });

            // Header setup
            if (chatroomId.equals("announcements")) {
                binding.tvChatName.setText("Announcements");
                Glide.with(getContext())
                        .load(R.drawable.nisisi_logo)
                        .circleCrop()
                        .into(binding.tvHeaderAvatar);
            } else {
                binding.tvChatName.setText(chatroom.getOtherUserName(currentUserId));
                FirebaseRemoteDataSource.INSTANCE.getRemoteUserData(
                        Objects.requireNonNull(chatroom.getOtherUserId(currentUserId)),
                        user -> {
                            userData = user;
                            Glide.with(requireContext())
                                    .load(user.getPhotoUrl())
                                    .circleCrop()
                                    .into(binding.tvHeaderAvatar);
                            binding.tvChatRole.setText(user.getUserRole());
                            return Unit.INSTANCE;
                        }, e -> {
                            e.printStackTrace();
                            return Unit.INSTANCE;
                        });

                binding.btnViewProfile.setOnClickListener(v->{
                    Intent intent = new Intent(getActivity(), ProfileActivity.class);
                    intent.putExtra(Constants.USER_ID, chatroom.getOtherUserId(Util.getState(Constants.CURRENT_USER_ID, "")));
                    startActivity(intent);
                });
            }
            return Unit.INSTANCE;
        });

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            if (joinedAT.isEmpty()) {
                long createdAtMillis = user.getMetadata().getCreationTimestamp();
                Date createdAtDate = new Date(createdAtMillis);
                Util.saveState(Constants.USER_CREATED_AT, createdAtDate.toString());
            }
        }

        binding.allChatInfo.setOnClickListener(v -> {
            Util.saveState(Constants.IS_MENTOR, true);
            isMentor = true;
            binding.tvProfileName.setText(userData.getFirstName() + " " + userData.getLastName());
            binding.tvProfileRole.setText(userData.getUserRole());
            binding.tvAbout.setText(userData.getBio());
            binding.email.setText(userData.getEmail());
            Glide.with(requireContext())
                    .load(userData.getPhotoUrl())
                    .circleCrop()
                    .into(binding.tvProfileAvatar);
            binding.joinedTittle.setText("LAST LOGIN");
            long lastLoginMillis = userData.getLastLogin();
            Date lastLoginDate = new Date(lastLoginMillis);
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            String formattedDate = sdf.format(lastLoginDate);
            binding.tvJoined.setText(formattedDate);
            binding.overlappingPanels.openStartPanel();
        });

        binding.rvChats.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvChats.setAdapter(adapter);

        // Single call - announcements auto-pinned by adapter
        viewModel.getChatRooms().observe(getViewLifecycleOwner(), pagingData -> {
            adapter.submitData(getLifecycle(), pagingData);
        });

        requireActivity()
                .getOnBackPressedDispatcher()
                .addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (isPanelOpen) {
                            binding.overlappingPanels.closePanels();
                            Util.saveState(Constants.IS_MENTOR, false);
                        } else {
                            NavHostFragment.findNavController(ChatFragment.this)
                                    .navigate(R.id.homeFragment);
                        }
                    }
                });

        viewModel.createAnnounceChatRoom();

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if (getActivity() != null) {
            chipNavigationBar = getActivity().findViewById(R.id.chipNavigationBar);
        }

        binding.overlappingPanels.registerEndPanelStateListeners(newState -> {
            if (newState instanceof PanelState.Opening) {
                chipNavigationBar.setVisibility(View.GONE);
            } else if (newState instanceof PanelState.Opened) {
                chipNavigationBar.setVisibility(View.GONE);
                isPanelOpen = true;
            } else if (newState instanceof PanelState.Closing) {
                adapter.refresh();
                chipNavigationBar.setVisibility(View.VISIBLE);
            } else if (newState instanceof PanelState.Closed) {
                isPanelOpen = false;
                Util.saveState(Constants.IS_MENTOR,false);
                chipNavigationBar.setVisibility(View.VISIBLE);
            }
            hideKeyboard();
        });
        binding.overlappingPanels.registerStartPanelStateListeners(newState -> {
            if (newState instanceof PanelState.Opening) {
            } else if (newState instanceof PanelState.Opened) {
                isPanelOpen = true;
            } else if (newState instanceof PanelState.Closing) {
            } else if (newState instanceof PanelState.Closed) {
                isPanelOpen = false;
                Util.saveState(Constants.IS_MENTOR,false);

                isMentor = Util.getState(Constants.IS_MENTOR, false);
                if (isMentor) {
                    UserViewModel userViewModel = new ViewModelProvider(requireActivity()).get(UserViewModel.class);
                    userViewModel.fetchingCurrentUserDataFromDB(Util.getState(Constants.CURRENT_USER_ID,
                            "")).observe(getViewLifecycleOwner(), currentUser -> {
                        binding.tvProfileName.setText(currentUser.getFirstName() + " " + currentUser.getLastName());
                        binding.tvProfileRole.setText(currentUser.getUserRole());
                        binding.tvAbout.setText(currentUser.getBio());
                        binding.email.setText(currentUser.getEmail());

                        Glide.with(requireContext())
                                .load(currentUser.getPhotoUrl())
                                .circleCrop()
                                .into(binding.tvProfileAvatar);

                        binding.joinedTittle.setText("JOINED");
                        binding.tvJoined.setText(joinedAT);
                    });
                }
            }
        });

        binding.icBack.setOnClickListener(v -> {
            binding.overlappingPanels.closePanels();
            Util.saveState(Constants.IS_MENTOR,false);
        });
    }


    public void hideKeyboard() {
        View view = getActivity().getCurrentFocus();
        if (view != null) {
            InputMethodManager inputManager = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (inputManager != null) {
                inputManager.hideSoftInputFromWindow(view.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
            }
        }
    }


}