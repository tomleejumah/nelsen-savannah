package com.app.nisisiafrica.Fragments.BaseFragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.nisisiafrica.Adapters.CommunityAdapter;
import com.app.nisisiafrica.CommunityDetailActivity;
import com.app.nisisiafrica.CreateCommunityActivity;
import com.app.nisisiafrica.R;
import com.app.nisisiafrica.ViewModel.UserViewModel;
import com.app.nisisiafrica.data.Model.Community;
import com.app.nisisiafrica.data.Repository.CommunityRepository;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class CommunitiesFragment extends Fragment {

    private final CommunityRepository repository = new CommunityRepository();
    private CommunityAdapter adapter;
    private ListenerRegistration registration;
    private View emptyState;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_communities, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        emptyState = view.findViewById(R.id.emptyState);
        RecyclerView rv = view.findViewById(R.id.rvCommunities);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new CommunityAdapter(community -> {
            Intent intent = new Intent(requireContext(), CommunityDetailActivity.class);
            intent.putExtra(CommunityDetailActivity.EXTRA_COMMUNITY_ID, community.getId());
            intent.putExtra(CommunityDetailActivity.EXTRA_COMMUNITY_NAME, community.getName());
            startActivity(intent);
        });
        rv.setAdapter(adapter);

        FloatingActionButton fab = view.findViewById(R.id.fabCreateCommunity);
        fab.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), CreateCommunityActivity.class)));

        UserViewModel userViewModel = new ViewModelProvider(requireActivity()).get(UserViewModel.class);
        userViewModel.getUserData().observe(getViewLifecycleOwner(), user -> {
            boolean isMentor = user != null && "Mentor".equals(user.getUserRole());
            fab.setVisibility(isMentor ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        registration = repository.communitiesQuery().addSnapshotListener((snapshot, e) -> {
            if (e != null || snapshot == null) return;
            List<Community> list = new ArrayList<>();
            snapshot.forEach(doc -> list.add(doc.toObject(Community.class)));
            adapter.submit(list);
            if (emptyState != null) {
                emptyState.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
            }
        });
    }

    @Override
    public void onStop() {
        super.onStop();
        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }
}
