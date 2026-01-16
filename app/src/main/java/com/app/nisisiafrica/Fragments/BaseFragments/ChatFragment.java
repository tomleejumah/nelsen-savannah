package com.app.nisisiafrica.Fragments.BaseFragments;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.app.nisisiafrica.Adapters.ChatAdapter;
import com.app.nisisiafrica.Constants;
import com.app.nisisiafrica.R;
import com.app.nisisiafrica.Utils.Util;
import com.app.nisisiafrica.ViewModel.ChatViewModel;
import com.app.nisisiafrica.ViewModel.ChatViewModelFactory;
import com.app.nisisiafrica.data.Repository.ChatRepository;
import com.app.nisisiafrica.data.remote.FirebaseRemoteDataSource;
import com.app.nisisiafrica.databinding.FragmentChatBinding;
import com.bumptech.glide.Glide;
import com.discord.panels.PanelState;

import java.util.Objects;

import kotlin.Unit;

public class ChatFragment extends Fragment {
    View chipNavigationBar;
    private FragmentChatBinding binding;
    private ChatAdapter adapter;
    private ChatViewModel viewModel;
    private String role;
    String chatroomId;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentChatBinding.inflate(inflater, container, false);

        ChatRepository repository = new ChatRepository();
        ChatViewModelFactory factory = new ChatViewModelFactory(repository);
        viewModel = new ViewModelProvider(this, factory).get(ChatViewModel.class);
        role = Util.getState(Constants.USER_ROLE,"Mentee");

        adapter = new ChatAdapter(chatroom -> {
             chatroomId = chatroom.getChatroomId().isEmpty() ? "announcements" : chatroom.getChatroomId();

            String currentUserId = Util.getState(Constants.CURRENT_USER_ID, "");
            binding.overlappingPanels.openEndPanel();

            if (chatroomId.equals("announcements")) {
                binding.tvChatName.setText("Announcements");

                Glide.with(getContext())
                        .load(R.drawable.nisisi_logo)
                        .circleCrop()
                        .into(binding.tvHeaderAvatar);

            }else {
                binding.tvChatName.setText(chatroom.getOtherUserName(currentUserId));
                FirebaseRemoteDataSource.INSTANCE.getRemoteUserData(Objects.requireNonNull(chatroom.
                        getOtherUserId(currentUserId)), userData -> {
                    Glide.with(requireContext())
                            .load(userData.getPhotoUrl())
                            .circleCrop()
                            .into(binding.tvHeaderAvatar);
                    return Unit.INSTANCE;
                }, e -> {
                    e.printStackTrace();
                    return Unit.INSTANCE;
                });
            }

            return Unit.INSTANCE;
        });


        binding.rvChats.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvChats.setAdapter(adapter);
        viewModel.getChatRooms().observe(getViewLifecycleOwner(), pagingData -> {
            adapter.submitData(getLifecycle(), pagingData);
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
            } else if (newState instanceof PanelState.Closing) {
                chipNavigationBar.setVisibility(View.VISIBLE);
            } else if (newState instanceof PanelState.Closed) {
                chipNavigationBar.setVisibility(View.VISIBLE);
            }
            hideKeyboard();
        });

        binding.overlappingPanels.registerStartPanelStateListeners(newState -> {
//            if (newState instanceof PanelState.Opening) {
//                chipNavigationBar.setVisibility(View.GONE);
//            } else if (newState instanceof PanelState.Opened) {
//                chipNavigationBar.setVisibility(View.GONE);
//            } else if (newState instanceof PanelState.Closing) {
//                chipNavigationBar.setVisibility(View.VISIBLE);
//            } else if (newState instanceof PanelState.Closed) {
//                chipNavigationBar.setVisibility(View.VISIBLE);
//            }
        });

        binding.icBack.setOnClickListener(v -> {
            binding.overlappingPanels.closePanels();
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