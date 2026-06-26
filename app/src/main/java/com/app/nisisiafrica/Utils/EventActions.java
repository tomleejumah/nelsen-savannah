package com.app.nisisiafrica.Utils;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.CalendarContract;
import android.widget.Toast;

import com.app.nisisiafrica.data.Model.Event;

import java.util.Calendar;

/**
 * Contextual actions for an {@link Event}: add to Google Calendar, join the
 * online meeting, or open the physical location on a maps app.
 */
public final class EventActions {

    /** Window (ms) before start within which an online event is considered "joinable". */
    private static final long JOIN_WINDOW_MS = 30L * 60L * 1000L;

    private EventActions() {}

    public static boolean isOnline(Event e) {
        return "online".equalsIgnoreCase(e.getMode());
    }

    /** Start time in epoch millis, combining the event date with its startTime (HH:mm). */
    public static long startMillis(Event e) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(e.getDate());
        String st = e.getStartTime();
        if (st != null && st.contains(":")) {
            try {
                String[] parts = st.split(":");
                c.set(Calendar.HOUR_OF_DAY, Integer.parseInt(parts[0].trim()));
                c.set(Calendar.MINUTE, Integer.parseInt(parts[1].trim()));
                c.set(Calendar.SECOND, 0);
            } catch (NumberFormatException ignored) {
            }
        }
        return c.getTimeInMillis();
    }

    public static long endMillis(Event e) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(startMillis(e));
        String et = e.getEndTime();
        if (et != null && et.contains(":")) {
            try {
                String[] parts = et.split(":");
                c.set(Calendar.HOUR_OF_DAY, Integer.parseInt(parts[0].trim()));
                c.set(Calendar.MINUTE, Integer.parseInt(parts[1].trim()));
                return c.getTimeInMillis();
            } catch (NumberFormatException ignored) {
            }
        }
        return startMillis(e) + 60L * 60L * 1000L; // default 1h
    }

    /** True when we're close enough to (or inside) the event to "join now". */
    public static boolean isJoinTime(Event e) {
        long now = System.currentTimeMillis();
        return now >= startMillis(e) - JOIN_WINDOW_MS && now <= endMillis(e);
    }

    public static void addToCalendar(Context ctx, Event e) {
        String title = e.getTitle();
        if (title == null || title.trim().isEmpty()) {
            title = e.getMentorName() != null && !e.getMentorName().isEmpty()
                    ? "Session with " + e.getMentorName() : "Event";
        }
        String place = isOnline(e) ? e.getMeetingLink() : e.getLocation();
        Intent intent = new Intent(Intent.ACTION_INSERT)
                .setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE, title)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis(e))
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis(e))
                .putExtra(CalendarContract.Events.EVENT_LOCATION, place == null ? "" : place);
        if (e.getDescription() != null) {
            intent.putExtra(CalendarContract.Events.DESCRIPTION, e.getDescription());
        }
        try {
            ctx.startActivity(intent);
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(ctx, "No calendar app found", Toast.LENGTH_SHORT).show();
        }
    }

    public static void joinMeeting(Context ctx, Event e) {
        String link = e.getMeetingLink();
        if (link == null || link.trim().isEmpty()) {
            Toast.makeText(ctx, "No meeting link set", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!link.startsWith("http://") && !link.startsWith("https://")) {
            link = "https://" + link;
        }
        try {
            ctx.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link)));
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(ctx, "Can't open meeting link", Toast.LENGTH_SHORT).show();
        }
    }

    public static void openLocation(Context ctx, Event e) {
        String loc = e.getLocation();
        if (loc == null || loc.trim().isEmpty()) {
            Toast.makeText(ctx, "No location set", Toast.LENGTH_SHORT).show();
            return;
        }
        Uri geo = Uri.parse("geo:0,0?q=" + Uri.encode(loc));
        try {
            ctx.startActivity(new Intent(Intent.ACTION_VIEW, geo));
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(ctx, "No maps app found", Toast.LENGTH_SHORT).show();
        }
    }
}
