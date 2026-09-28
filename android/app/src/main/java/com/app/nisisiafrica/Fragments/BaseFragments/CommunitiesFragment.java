package com.app.nisisiafrica.Fragments.BaseFragments;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.nisisiafrica.Adapters.CommunityAdapter;
import com.app.nisisiafrica.CommunityDetailActivity;
import com.app.nisisiafrica.CreateCommunityActivity;
import com.app.nisisiafrica.R;
import com.app.nisisiafrica.Utils.Roles;
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
    private TextView btnEdit;

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
        View content = view.findViewById(R.id.communitiesContent);
        btnEdit = view.findViewById(R.id.btnEditCommunities);
        EditText search = view.findViewById(R.id.etSearchCommunities);
        RecyclerView rv = view.findViewById(R.id.rvCommunities);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));

        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(v.getPaddingLeft(),
                    bars.top + Math.round(8 * getResources().getDisplayMetrics().density),
                    v.getPaddingRight(),
                    v.getPaddingBottom());
            return insets;
        });
        ViewCompat.requestApplyInsets(content);

        adapter = new CommunityAdapter(community -> {
            Intent intent = new Intent(requireContext(), CommunityDetailActivity.class);
            intent.putExtra(CommunityDetailActivity.EXTRA_COMMUNITY_ID, community.getId());
            intent.putExtra(CommunityDetailActivity.EXTRA_COMMUNITY_NAME, community.getName());
            startActivity(intent);
        });
        rv.setAdapter(adapter);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s != null ? s.toString() : "");
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        btnEdit.setOnClickListener(v -> {
            if (Roles.canCreate()) {
                startActivity(new Intent(requireContext(), CreateCommunityActivity.class));
            }
        });

        FloatingActionButton fab = view.findViewById(R.id.fabCreateCommunity);
        fab.setVisibility(View.GONE);
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
