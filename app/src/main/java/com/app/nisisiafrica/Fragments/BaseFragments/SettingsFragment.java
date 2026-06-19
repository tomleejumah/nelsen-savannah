package com.app.nisisiafrica.Fragments.BaseFragments;

import static android.app.Activity.RESULT_OK;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;

import com.app.nisisiafrica.Constants;
import com.app.nisisiafrica.DiditVerificationHandler;
import com.app.nisisiafrica.Interfaces.KYCCallBack;
import com.app.nisisiafrica.LockScreenActivity;
import com.app.nisisiafrica.PinManager;
import com.app.nisisiafrica.R;
import com.app.nisisiafrica.SetpinActivity;
import com.app.nisisiafrica.Utils.Util;
import com.app.nisisiafrica.data.remote.ApiClient;
import com.google.firebase.auth.FirebaseAuth;

import kotlin.Unit;

public class SettingsFragment extends Fragment {
    private static final String URL_LINKEDIN = "https://www.linkedin.com/company/nisisi-africa-org/";
    private static final String URL_INSTAGRAM = "https://www.instagram.com/nisisiafrica_org?igsh=MWo0a3NlbGVlaWptNw==";
    private static final String URL_FACEBOOK = "https://www.facebook.com/nisisiafrica";
    private static final String URL_YOUTUBE = "https://youtube.com/@nisisiafrica_org?si=oV0mIGg3uSOGEMi7";
     private DiditVerificationHandler handler;
    private ProgressDialog loadingDialog;
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private Switch lockSwitch;
    private String uid;
    private CompoundButton.OnCheckedChangeListener lockListener;
    private ActivityResultLauncher<Intent> setPinLauncher;
    private KYCCallBack backendApi =
            ApiClient.getClient().create(KYCCallBack.class);
    public static void rateApp(Context context) {
        try {
            Intent rateIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + context.getPackageName()));
            context.startActivity(rateIntent);
        } catch (ActivityNotFoundException e) {
            Intent rateIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + context.getPackageName()));
            context.startActivity(rateIntent);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setPinLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK) {
                        lockSwitch.setOnCheckedChangeListener(null);
                        lockSwitch.setChecked(false);
                        lockSwitch.setOnCheckedChangeListener(lockListener);
                    }
                }
        );
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            requireActivity().finish();
            return new View(requireContext());
        }
        uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        lockSwitch = view.findViewById(R.id.lockApp);

        view.findViewById(R.id.tv_share_app).setOnClickListener(v -> shareApp());
        view.findViewById(R.id.tv_rate_app).setOnClickListener(v -> rateApp(getContext()));
        view.findViewById(R.id.btn_linkedin).setOnClickListener(v -> openUrlInBrowser(URL_LINKEDIN));
        view.findViewById(R.id.btn_instagram).setOnClickListener(v -> openUrlInBrowser(URL_INSTAGRAM));
        view.findViewById(R.id.btn_youtube).setOnClickListener(v -> openUrlInBrowser(URL_YOUTUBE));
        view.findViewById(R.id.btn_facebook).setOnClickListener(v -> openUrlInBrowser(URL_FACEBOOK));

        RelativeLayout cardVerifyProfile = view.findViewById(R.id.cardVerifyProfile);
        cardVerifyProfile.setVisibility(Util.getState(Constants.USER_ROLE,"Mentee")
                .equals("Mentee") ? View.GONE : View.VISIBLE);

        handler = new DiditVerificationHandler(
                requireActivity(),
                backendApi,
                s -> {
                    updateVerificationUI(s);
                    return Unit.INSTANCE;
                }
        );
        // Setup loading dialog
        loadingDialog = new ProgressDialog(requireContext());
        loadingDialog.setMessage("Preparing verification...");
        loadingDialog.setCancelable(false);

            handler.checkStatus(s -> {
                updateVerificationUI(s);
                return Unit.INSTANCE;
            });

        view.findViewById(R.id.cardVerifyProfile).setOnClickListener(v -> {
            loadingDialog.show();
            handler.startVerification(() -> {
                loadingDialog.dismiss();
                return Unit.INSTANCE;
            });
        });

        lockListener = (buttonView, isChecked) -> {
            if (isChecked) {
                try {
                    if (PinManager.hasPin(getContext(), uid)) {
                        Toast.makeText(getContext(), "Lock enabled", Toast.LENGTH_SHORT).show();
                    } else {
                        setPinLauncher.launch(new Intent(getContext(), SetpinActivity.class));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                try {
                    PinManager.clearPin(getContext(), uid);
                    LockScreenActivity.AppLockState.lock();
                    Toast.makeText(getContext(), "Lock disabled", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        lockSwitch.setOnCheckedChangeListener(null);
        try {
            lockSwitch.setChecked(PinManager.hasPin(getContext(), uid));
        } catch (Exception e) {
            e.printStackTrace();
        }
        lockSwitch.setOnCheckedChangeListener(lockListener);

        return view;
    }

    private void shareApp() {
        try {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Check out this awesome app!");
            String shareMessage = "Hey, I found this great app and wanted to share it with you!\n\n"
                    + "https://play.google.com/store/apps/details?id=" + getActivity().getPackageName();
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareMessage);
            startActivity(Intent.createChooser(shareIntent, "Share app via"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void openLinkedIn(View view) {
        openUrlInBrowser(URL_LINKEDIN);
    }

    public void openInstagram(View view) {
        openUrlInBrowser(URL_INSTAGRAM);
    }

    public void openYoutube(View view) {
        openUrlInBrowser(URL_YOUTUBE);
    }

    public void openFacebook(View view) {
        openUrlInBrowser(URL_FACEBOOK);
    }

    private void openUrlInBrowser(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        startActivity(intent);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (handler == null) {
            handler = new DiditVerificationHandler(
                    requireActivity(),
                    backendApi,
                    s -> {
                        updateVerificationUI(s);
                        return Unit.INSTANCE;
                    });
        }
        handler.listenToStatus();
        handler.checkStatus(s -> {
            updateVerificationUI(s);
            return Unit.INSTANCE;
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        if (handler != null) {
            handler.stopListening();
        }
    }

    private void updateVerificationUI(String status) {
        requireActivity().runOnUiThread(() -> {
            TextView statusText = getView().findViewById(R.id.txtVerificationStatus);
            View verifyCard = getView().findViewById(R.id.cardVerifyProfile);

            switch (status) {
                case "approved":
                    statusText.setText(" Verified");
                    statusText.setBackgroundResource(R.drawable.badge_background_green);
                    statusText.setTextColor(Color.parseColor("#00AA00"));
                    verifyCard.setEnabled(false);
                    break;
                case "pending":
                    statusText.setText("Pending review");
                    verifyCard.setEnabled(false);
                    break;
                case "rejected":
                    statusText.setText("Rejected - Retry");
                    verifyCard.setEnabled(true);
                    break;
                default:
                    statusText.setText("Not verified");
                    verifyCard.setEnabled(true);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (handler != null) {
            handler.cleanup();
        }
    }
}