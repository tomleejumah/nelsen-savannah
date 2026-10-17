package com.app.nisisiafrica.Utils;

import com.app.nisisiafrica.data.Model.LmsModels;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Merge live polling snapshots with older history pages without resetting the
 * scroll position or losing messages once they leave the latest-50 window.
 * This model is shared by the viewer and host studios.
 */
public final class LiveChatTimeline {
    private static final int MAX_RETAINED = 1000;
    private final Map<String, LmsModels.NativeLiveChatMessage> messages = new LinkedHashMap<>();
    private String nextCursor;
    private boolean hasMore;
    private boolean loadedFirstSnapshot;

    public boolean appendLatest(LmsModels.LiveStateData state) {
        if (state == null) return false;
        if (!loadedFirstSnapshot) {
            loadedFirstSnapshot = true;
            hasMore = state.chatHasMore;
            nextCursor = state.chatNextCursor;
        }
        return merge(state.chat);
    }

    public boolean prependHistory(LmsModels.LiveChatHistoryData history) {
        if (history == null) return false;
        nextCursor = history.nextCursor;
        hasMore = history.hasMore && nextCursor != null && !nextCursor.isEmpty();
        return merge(history.messages);
    }

    private boolean merge(List<LmsModels.NativeLiveChatMessage> incoming) {
        if (incoming == null || incoming.isEmpty()) return false;
        boolean changed = false;
        for (LmsModels.NativeLiveChatMessage item : incoming) {
            if (item == null || item.id == null || item.id.isEmpty()) continue;
            LmsModels.NativeLiveChatMessage previous = messages.put(item.id, item);
            if (previous == null || previous.createdAt != item.createdAt
                    || !safe(previous.message).equals(safe(item.message))
                    || !safe(previous.author).equals(safe(item.author))) changed = true;
        }
        if (messages.size() > MAX_RETAINED) {
            List<LmsModels.NativeLiveChatMessage> sorted = sorted();
            for (int i = 0; i < sorted.size() - MAX_RETAINED; i++) {
                messages.remove(sorted.get(i).id);
            }
        }
        return changed;
    }

    private List<LmsModels.NativeLiveChatMessage> sorted() {
        List<LmsModels.NativeLiveChatMessage> items = new ArrayList<>(messages.values());
        items.sort(Comparator.comparingLong((LmsModels.NativeLiveChatMessage m) -> m.createdAt)
                .thenComparing(m -> safe(m.id)));
        return items;
    }

    public boolean hasOlder() {
        return hasMore && nextCursor != null && !nextCursor.isEmpty();
    }

    public String nextCursor() {
        return nextCursor;
    }

    public String displayText() {
        List<LmsModels.NativeLiveChatMessage> items = sorted();
        if (items.isEmpty()) return "Be the first to say something.";
        StringBuilder text = new StringBuilder();
        for (LmsModels.NativeLiveChatMessage message : items) {
            if (text.length() > 0) text.append("\n\n");
            if (!safe(message.author).isEmpty()) text.append(message.author).append(": ");
            text.append(safe(message.message));
        }
        return text.toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
