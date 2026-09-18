package com.app.nisisiafrica.data.Model;

import java.util.ArrayList;
import java.util.List;

/** One home-rail cell: all active stories from the same owner, WhatsApp/IG style. */
public class StoryBucket {
    public final String ownerKey;
    public String label;
    public final ArrayList<Story> stories = new ArrayList<>();

    public StoryBucket(String ownerKey, String label) {
        this.ownerKey = ownerKey;
        this.label = label;
    }

    public String coverUrl() {
        if (stories.isEmpty()) return null;
        Story latest = stories.get(stories.size() - 1);
        if (latest.logoUrl != null && !latest.logoUrl.isEmpty()) return latest.logoUrl;
        return latest.mediaUrl;
    }

    public int firstUnseenIndex(java.util.Set<String> seenIds) {
        for (int i = 0; i < stories.size(); i++) {
            String id = stories.get(i).storyId;
            if (id == null || seenIds == null || !seenIds.contains(id)) return i;
        }
        return 0;
    }

    public boolean hasUnseen(java.util.Set<String> seenIds) {
        if (seenIds == null || seenIds.isEmpty()) return !stories.isEmpty();
        for (Story s : stories) {
            if (s.storyId == null || !seenIds.contains(s.storyId)) return true;
        }
        return false;
    }

    public int unseenCount(java.util.Set<String> seenIds) {
        int n = 0;
        for (Story s : stories) {
            if (s.storyId == null || seenIds == null || !seenIds.contains(s.storyId)) n++;
        }
        return n;
    }

    /** Group flat stories by ownerId (fallback companyName / storyId). Oldest → newest in each bucket. */
    public static ArrayList<StoryBucket> fromStories(List<Story> flat) {
        ArrayList<StoryBucket> out = new ArrayList<>();
        java.util.LinkedHashMap<String, StoryBucket> map = new java.util.LinkedHashMap<>();
        ArrayList<Story> sorted = new ArrayList<>(flat);
        sorted.sort((a, b) -> Long.compare(a.timestamp, b.timestamp));
        for (Story s : sorted) {
            String key = s.ownerId;
            if (key == null || key.isEmpty()) {
                key = (s.companyName != null && !s.companyName.isEmpty())
                        ? "name:" + s.companyName
                        : "id:" + s.storyId;
            }
            StoryBucket bucket = map.get(key);
            if (bucket == null) {
                bucket = new StoryBucket(key, s.displayLabel());
                map.put(key, bucket);
                out.add(bucket);
            }
            bucket.stories.add(s);
            String nextLabel = s.displayLabel();
            if (nextLabel != null && !nextLabel.isEmpty()) {
                bucket.label = nextLabel;
            }
        }
        // Newest activity first on the rail (like WhatsApp).
        out.sort((a, b) -> {
            long ta = a.stories.isEmpty() ? 0 : a.stories.get(a.stories.size() - 1).timestamp;
            long tb = b.stories.isEmpty() ? 0 : b.stories.get(b.stories.size() - 1).timestamp;
            return Long.compare(tb, ta);
        });
        return out;
    }
}
