package com.app.nisisiafrica.Adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.app.nisisiafrica.R;
import com.app.nisisiafrica.data.Model.ProgrammeItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ProgrammesAdapter extends RecyclerView.Adapter<ProgrammesAdapter.VH> {

    public interface OnProgrammeClick {
        void onClick(ProgrammeItem item);
    }

    private final List<ProgrammeItem> items = new ArrayList<>();
    private final boolean fullWidth;
    private OnProgrammeClick listener;

    public ProgrammesAdapter(boolean fullWidth) {
        this.fullWidth = fullWidth;
    }

    public void setOnProgrammeClick(OnProgrammeClick listener) {
        this.listener = listener;
    }

    public void submit(List<ProgrammeItem> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = fullWidth ? R.layout.item_programme_full : R.layout.item_programme_card;
        return new VH(LayoutInflater.from(parent.getContext()).inflate(layout, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        h.bind(items.get(position), position);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class VH extends RecyclerView.ViewHolder {
        TextView tag, index, title, blurb, apply;
        LinearLayout checklist;

        VH(@NonNull View itemView) {
            super(itemView);
            tag = itemView.findViewById(R.id.tvAudienceTag);
            index = itemView.findViewById(R.id.tvProgramIndex);
            title = itemView.findViewById(R.id.tvProgramTitle);
            blurb = itemView.findViewById(R.id.tvProgramBlurb);
            apply = itemView.findViewById(R.id.tvApply);
            checklist = itemView.findViewById(R.id.checklistContainer);
        }

        void bind(ProgrammeItem p, int pos) {
            title.setText(p.title);
            blurb.setText(p.blurb);
            tag.setText(p.audience != null && !p.audience.isEmpty() ? p.audience : "Programme");
            boolean blue = "blue".equalsIgnoreCase(p.tone) || "brand".equalsIgnoreCase(p.tone);
            tag.setBackgroundResource(blue ? R.drawable.bg_tag_blue : R.drawable.bg_tag_maroon);
            tag.setTextColor(itemView.getContext().getColor(
                    blue ? R.color.tag_blue_text : R.color.maroon_600));
            index.setText(String.format(Locale.getDefault(), "%02d", pos + 1));

            if (checklist != null) {
                checklist.removeAllViews();
                if (p.checklist != null) {
                    for (String line : p.checklist) {
                        if (line == null || line.isEmpty()) continue;
                        TextView row = new TextView(itemView.getContext());
                        row.setText("•  " + line);
                        row.setTextColor(itemView.getContext().getColor(R.color.ink_soft));
                        row.setTextSize(13f);
                        row.setPadding(0, 4, 0, 0);
                        checklist.addView(row);
                    }
                }
            }

            View.OnClickListener click = v -> {
                if (listener != null) listener.onClick(p);
            };
            itemView.setOnClickListener(click);
            apply.setOnClickListener(click);
        }
    }
}
