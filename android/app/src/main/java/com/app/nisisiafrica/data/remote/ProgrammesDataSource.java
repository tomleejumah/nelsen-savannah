package com.app.nisisiafrica.data.remote;

import androidx.annotation.NonNull;

import com.app.nisisiafrica.data.Model.ProgrammeItem;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Loads `programmes` from RTDB; falls back to seed list so Home always has cards. */
public final class ProgrammesDataSource {
    private ProgrammesDataSource() {}

    public interface Callback {
        void onProgrammes(@NonNull List<ProgrammeItem> programmes);
    }

    public static void fetch(Callback cb) {
        FirebaseDatabase.getInstance().getReference("programmes")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<ProgrammeItem> list = new ArrayList<>();
                        if (snapshot.exists()) {
                            for (DataSnapshot child : snapshot.getChildren()) {
                                ProgrammeItem p = child.getValue(ProgrammeItem.class);
                                if (p == null) continue;
                                if (p.slug == null || p.slug.isEmpty()) p.slug = child.getKey();
                                list.add(p);
                            }
                            Collections.sort(list, (a, b) -> Integer.compare(a.order, b.order));
                        }
                        if (list.isEmpty()) list = ProgrammeItem.defaults();
                        cb.onProgrammes(list);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        cb.onProgrammes(ProgrammeItem.defaults());
                    }
                });
    }
}
