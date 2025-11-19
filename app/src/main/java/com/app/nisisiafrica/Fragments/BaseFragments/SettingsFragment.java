package com.app.nisisiafrica.Fragments.BaseFragments;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.app.nisisiafrica.R;

public class SettingsFragment extends Fragment {
    private static final String URL_LINKEDIN = "https://www.linkedin.com/company/nisisi-africa-org/";
    private static final String URL_INSTAGRAM = "https://www.instagram.com/nisisiafrica_org?igsh=MWo0a3NlbGVlaWptNw==";
    private static final String URL_FACEBOOK = "https://www.facebook.com/nisisiafrica";
    private static final String URL_YOUTUBE = "https://youtube.com/@nisisiafrica_org?si=oV0mIGg3uSOGEMi7";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view =  inflater.inflate(R.layout.fragment_settings, container, false);

        view.findViewById(R.id.tv_share_app).setOnClickListener(v -> shareApp());

        view.findViewById(R.id.tv_rate_app).setOnClickListener(v -> rateApp(getContext()));

        view.findViewById(R.id.btn_linkedin).setOnClickListener(v -> openUrlInBrowser(URL_LINKEDIN));
        view.findViewById(R.id.btn_instagram).setOnClickListener(v -> openUrlInBrowser(URL_INSTAGRAM));
        view.findViewById(R.id.btn_youtube).setOnClickListener(v -> openUrlInBrowser(URL_YOUTUBE));
        view.findViewById(R.id.btn_facebook).setOnClickListener(v -> openUrlInBrowser(URL_FACEBOOK));


        return  view;
    }

    private void shareApp() {
        try {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Check out this awesome app!");

            String shareMessage = "Hey, I found this great app and wanted to share it with you!\n\n";
            shareMessage = shareMessage + "https://play.google.com/store/apps/details?id=" + getActivity().getPackageName();

            shareIntent.putExtra(Intent.EXTRA_TEXT, shareMessage);
            startActivity(Intent.createChooser(shareIntent, "Share app via")); // Title for the share chooser dialog
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void rateApp(Context context) {
        try {
            Intent rateIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + context.getPackageName()));
            context.startActivity(rateIntent);
        } catch (ActivityNotFoundException e) {
            Intent rateIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + context.getPackageName()));
            context.startActivity(rateIntent);
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
}