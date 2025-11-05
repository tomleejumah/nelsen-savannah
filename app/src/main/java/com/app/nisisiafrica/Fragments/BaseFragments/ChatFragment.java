package com.app.nisisiafrica.Fragments.BaseFragments;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;

import com.app.nisisiafrica.Adapters.ChatAdapter;
import com.app.nisisiafrica.R;
import com.app.nisisiafrica.ViewModel.ChatViewModel;
import com.app.nisisiafrica.ViewModel.ChatViewModelFactory;
import com.app.nisisiafrica.data.Repository.ChatRepository;
import com.app.nisisiafrica.databinding.FragmentChatBinding;
import com.discord.panels.OverlappingPanelsLayout;
import com.discord.panels.PanelState;

public class ChatFragment extends Fragment {
    private FragmentChatBinding binding;
    View chipNavigationBar;
    private ChatAdapter adapter;
    private ChatViewModel viewModel;
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
           binding = FragmentChatBinding.inflate(inflater, container, false);

        ChatRepository repository = new ChatRepository();
        ChatViewModelFactory factory = new ChatViewModelFactory(repository);
        viewModel = new ViewModelProvider(this, factory).get(ChatViewModel.class);

        adapter = new ChatAdapter();
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

        binding.overlappingPanels.registerEndPanelStateListeners(newState ->{
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

        binding.icBack.setOnClickListener(v->{
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