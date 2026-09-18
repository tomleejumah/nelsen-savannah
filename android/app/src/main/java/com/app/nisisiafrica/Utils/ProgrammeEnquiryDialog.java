package com.app.nisisiafrica.Utils;

import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.app.nisisiafrica.Interfaces.InquiryApiService;
import com.app.nisisiafrica.R;
import com.app.nisisiafrica.data.remote.ApiClient;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Contact-style programme enquiry → POST /inquiries (same desk mail as web). */
public final class ProgrammeEnquiryDialog {

    public static final List<String> PROGRAMMES = Arrays.asList(
            "Future Safari",
            "Savannah Robotics & Automation Lab",
            "Savannah Data & AI Academy",
            "Savannah Creative Lab",
            "Savannah Software Engineering Lab",
            "Savannah Sauti Academy",
            "Kijiji Hub",
            "Custom / multi-programme"
    );

    private ProgrammeEnquiryDialog() {}

    public static void show(@NonNull Context context, @Nullable String programmeTitle) {
        View root = LayoutInflater.from(context).inflate(R.layout.dialog_programme_enquiry, null, false);

        TextView roleLearner = root.findViewById(R.id.roleLearner);
        TextView roleCampus = root.findViewById(R.id.roleCampus);
        TextView rolePartner = root.findViewById(R.id.rolePartner);
        TextView roleCareers = root.findViewById(R.id.roleCareers);
        TextView roleFacilitator = root.findViewById(R.id.roleFacilitator);
        EditText name = root.findViewById(R.id.enquiryName);
        EditText email = root.findViewById(R.id.enquiryEmail);
        EditText phone = root.findViewById(R.id.enquiryPhone);
        EditText message = root.findViewById(R.id.enquiryMessage);
        Spinner programme = root.findViewById(R.id.enquiryProgramme);
        TextView send = root.findViewById(R.id.enquirySend);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                context, android.R.layout.simple_spinner_dropdown_item, PROGRAMMES);
        programme.setAdapter(adapter);
        if (!TextUtils.isEmpty(programmeTitle)) {
            int idx = PROGRAMMES.indexOf(programmeTitle);
            if (idx < 0) {
                for (int i = 0; i < PROGRAMMES.size(); i++) {
                    if (PROGRAMMES.get(i).equalsIgnoreCase(programmeTitle)
                            || programmeTitle.toLowerCase().contains(
                            PROGRAMMES.get(i).toLowerCase().split(" ")[0])) {
                        idx = i;
                        break;
                    }
                }
            }
            // Also match home programme card titles (Sela, Trailblazers, …)
            if (idx < 0 && programmeTitle.toLowerCase().contains("sela")) {
                // keep Future Safari / first as default for non-catalog cards
                idx = 0;
            }
            if (idx >= 0) programme.setSelection(idx);
        }

        final String[] roleId = {"learner"};
        final TextView[] chips = {
                roleLearner, roleCampus, rolePartner, roleCareers, roleFacilitator
        };
        final String[] roleIds = {"learner", "campus", "partner", "careers", "facilitator"};

        View.OnClickListener pickRole = v -> {
            for (int i = 0; i < chips.length; i++) {
                boolean selected = chips[i] == v;
                chips[i].setSelected(selected);
                chips[i].setTextColor(ContextCompat.getColor(context,
                        selected ? R.color.ns_cream : R.color.ns_ink));
                if (selected) roleId[0] = roleIds[i];
            }
        };
        for (TextView chip : chips) chip.setOnClickListener(pickRole);
        pickRole.onClick(roleLearner);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(root)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.shape_polaroid_bg);
        }

        send.setOnClickListener(v -> {
            String fullName = name.getText() != null ? name.getText().toString().trim() : "";
            String mail = email.getText() != null ? email.getText().toString().trim() : "";
            String tel = phone.getText() != null ? phone.getText().toString().trim() : "";
            String body = message.getText() != null ? message.getText().toString().trim() : "";
            String prog = programme.getSelectedItem() != null
                    ? String.valueOf(programme.getSelectedItem()) : "";

            if (fullName.isEmpty() || mail.isEmpty()) {
                Toast.makeText(context, R.string.enquiry_name_email_required, Toast.LENGTH_SHORT).show();
                return;
            }

            String desk = "careers".equals(roleId[0]) ? "careers" : "contact";
            String subject = "careers".equals(roleId[0])
                    ? "Nelsen Savannah — role application"
                    : "Nelsen Savannah — " + roleId[0];

            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("Role", roleId[0]);
            fields.put("Full name", fullName);
            fields.put("Email", mail);
            if (!tel.isEmpty()) fields.put("Phone", tel);
            fields.put("Programme of interest", prog);
            if (!body.isEmpty()) fields.put("Message", body);

            send.setEnabled(false);
            send.setText(R.string.enquiry_sending);

            ApiClient.getInquiryService()
                    .postInquiry(new InquiryApiService.InquiryBody(desk, subject, mail, fields))
                    .enqueue(new Callback<>() {
                        @Override
                        public void onResponse(@NonNull Call<InquiryApiService.InquiryResponse> call,
                                               @NonNull Response<InquiryApiService.InquiryResponse> response) {
                            send.setEnabled(true);
                            send.setText(R.string.enquiry_send);
                            InquiryApiService.InquiryResponse body = response.body();
                            if (response.isSuccessful() && body != null && body.ok) {
                                Toast.makeText(context, R.string.enquiry_sent, Toast.LENGTH_LONG).show();
                                dialog.dismiss();
                            } else {
                                String err = body != null && body.error != null ? body.error
                                        : context.getString(R.string.enquiry_send_failed, response.code());
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show();
                            }
                        }

                        @Override
                        public void onFailure(@NonNull Call<InquiryApiService.InquiryResponse> call,
                                              @NonNull Throwable t) {
                            send.setEnabled(true);
                            send.setText(R.string.enquiry_send);
                            Toast.makeText(context, R.string.enquiry_network_error, Toast.LENGTH_LONG).show();
                        }
                    });
        });

        if (context instanceof Activity && ((Activity) context).isFinishing()) return;
        dialog.show();
    }
}
