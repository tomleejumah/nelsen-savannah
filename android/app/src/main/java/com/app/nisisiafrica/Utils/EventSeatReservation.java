package com.app.nisisiafrica.Utils;

import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.app.nisisiafrica.R;
import com.app.nisisiafrica.Worker.EventReminderWorker;
import com.app.nisisiafrica.data.Model.Event;
import com.app.nisisiafrica.data.Model.LmsModels;
import com.app.nisisiafrica.data.Model.UserData;
import com.app.nisisiafrica.data.remote.LmsEventsDataSource;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.concurrent.Executors;

/** Shared Reserve-seat bottom sheet used on Home and See more schedules. */
public final class EventSeatReservation {

    public interface OnReserved {
        void onReserved(Event event);
    }

    private EventSeatReservation() {}

    public static void show(
            Context context,
            Event event,
            @Nullable UserData profile,
            @Nullable OnReserved onReserved) {
        if (context == null || event == null) return;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(context, "Sign in to reserve a seat", Toast.LENGTH_SHORT).show();
            return;
        }

        int seatsLeft = Math.max(0, event.getSeats() - event.getSeatsTaken());
        if (event.getSeats() > 0 && seatsLeft <= 0) {
            Toast.makeText(context, "No seats left", Toast.LENGTH_SHORT).show();
            return;
        }

        BottomSheetDialog dialog = new BottomSheetDialog(context);
        View sheet = LayoutInflater.from(context)
                .inflate(R.layout.dialog_reserve_seat, null, false);
        dialog.setContentView(sheet);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dialog.setOnShowListener(d -> {
            View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior<?> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });

        TextView tvTitle = sheet.findViewById(R.id.tvReserveTitle);
        TextView tvSeats = sheet.findViewById(R.id.tvSeatsRemaining);
        EditText etFullName = sheet.findViewById(R.id.etFullName);
        EditText etEmail = sheet.findViewById(R.id.etEmail);
        EditText etPhone = sheet.findViewById(R.id.etPhone);
        EditText etProgram = sheet.findViewById(R.id.etProgram);
        MaterialButton btnCancel = sheet.findViewById(R.id.btnCancel);
        MaterialButton btnConfirm = sheet.findViewById(R.id.btnConfirm);

        String eventTitle = event.getTitle();
        if (eventTitle == null || eventTitle.trim().isEmpty()) {
            eventTitle = "this event";
        }
        tvTitle.setText(context.getString(R.string.reserve_title_named, eventTitle));
        if (event.getSeats() > 0) {
            tvSeats.setText(seatsLeft + " seat" + (seatsLeft == 1 ? "" : "s") + " remaining");
        } else {
            tvSeats.setText(R.string.reserve_confirm_details);
        }

        etFullName.setText(resolveName(profile, user));
        etEmail.setText(resolveEmail(profile, user));
        if (user.getPhoneNumber() != null) {
            etPhone.setText(user.getPhoneNumber());
        }
        if (event.getProgram() != null) {
            etProgram.setText(event.getProgram());
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            String fullName = text(etFullName);
            String mail = text(etEmail);
            String phone = text(etPhone);
            String program = text(etProgram);
            if (fullName.length() < 2) {
                Toast.makeText(context, "Enter your full name", Toast.LENGTH_SHORT).show();
                return;
            }
            if (mail.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(mail).matches()) {
                Toast.makeText(context, "Enter a valid email", Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            submit(context, event, fullName, mail, phone, program, onReserved);
        });
        dialog.show();
    }

    private static String text(EditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }

    private static String resolveName(@Nullable UserData profile, FirebaseUser user) {
        if (profile != null) {
            if (!TextUtils.isEmpty(profile.getDisplayName())) {
                return profile.getDisplayName().trim();
            }
            String fn = profile.getFirstName() != null ? profile.getFirstName().trim() : "";
            String ln = profile.getLastName() != null ? profile.getLastName().trim() : "";
            String combined = (fn + " " + ln).trim();
            if (!combined.isEmpty()) return combined;
        }
        return user.getDisplayName() != null ? user.getDisplayName().trim() : "";
    }

    private static String resolveEmail(@Nullable UserData profile, FirebaseUser user) {
        if (profile != null && !TextUtils.isEmpty(profile.getEmail())) {
            return profile.getEmail().trim();
        }
        return user.getEmail() != null ? user.getEmail().trim() : "";
    }

    private static void submit(
            Context context,
            Event event,
            String fullName,
            String email,
            String phone,
            String program,
            @Nullable OnReserved onReserved) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(context, "Sign in to reserve a seat", Toast.LENGTH_SHORT).show();
            return;
        }
        LmsModels.ReserveEventBody body = new LmsModels.ReserveEventBody();
        body.fullName = fullName;
        body.email = email;
        body.phone = phone.isEmpty() ? null : phone;
        body.program = program.isEmpty() ? event.getProgram() : program;

        user.getIdToken(false).addOnSuccessListener(tokenResult -> {
            String bearer = "Bearer " + tokenResult.getToken();
            Executors.newSingleThreadExecutor().execute(() -> {
                kotlin.Pair<Boolean, String> result =
                        LmsEventsDataSource.reserveSeatBlocking(bearer, event.getEventId(), body);
                runOnUi(context, () -> {
                    if (result.getFirst()) {
                        Toast.makeText(context, "Seat reserved", Toast.LENGTH_SHORT).show();
                        scheduleReminder(context, event);
                        EventActions.addToCalendar(context, event);
                        if (onReserved != null) onReserved.onReserved(event);
                    } else {
                        String msg = result.getSecond() != null ? result.getSecond() : "Could not reserve";
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
                    }
                });
            });
        });
    }

    private static void runOnUi(Context context, Runnable r) {
        if (context instanceof Activity) {
            ((Activity) context).runOnUiThread(r);
        } else if (context instanceof FragmentActivity) {
            ((FragmentActivity) context).runOnUiThread(r);
        } else {
            r.run();
        }
    }

    private static void scheduleReminder(Context context, Event event) {
        long startMs = EventActions.startMillis(event);
        long now = System.currentTimeMillis();
        if (startMs <= now) return;
        long triggerAt = startMs - EventReminderWorker.REMINDER_LEAD_MS;
        if (triggerAt < now) triggerAt = startMs;
        String title = event.getTitle();
        if (title == null || title.trim().isEmpty()) {
            title = event.getMentorName() != null && !event.getMentorName().isEmpty()
                    ? "Session with " + event.getMentorName() : "Upcoming event";
        }
        String timeSuffix = event.getStartTime() != null && !event.getStartTime().isEmpty()
                ? " at " + event.getStartTime() : "";
        String idKey = event.getEventId() != null ? event.getEventId() : title;
        EventReminderWorker.scheduleAlarm(
                context.getApplicationContext(),
                idKey.hashCode(),
                triggerAt,
                title,
                "Reminder: " + title + timeSuffix);
    }
}
