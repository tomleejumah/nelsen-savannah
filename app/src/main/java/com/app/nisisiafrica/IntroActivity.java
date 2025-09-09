package com.app.nisisiafrica;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.app.nisisiafrica.Auth.LoginSignUpActivity;
import com.app.nisisiafrica.Utils.Util;
import com.tbuonomo.viewpagerdotsindicator.DotsIndicator;

public class IntroActivity extends AppCompatActivity{
    private static final String TAG = "IntroActivity";

    private ViewPager2 viewPager2;
    private TextView btn_next,btn_prev;
    private Button startNow;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_intro);

     viewPager2 = findViewById(R.id.viewPager2);
        DotsIndicator dots_indicator = findViewById(R.id.dots_indicator);
        btn_prev= findViewById(R.id.btn_prev);
        btn_next = findViewById(R.id.btn_next);
        startNow = findViewById(R.id.startNow);
//        check_box = findViewById(R.id.check_box);

        int[] layouts = new int[]{
                R.layout.screen_one,
                R.layout.screen_two,
                R.layout.screen_three};

        MyViewPagerAdapter myViewPagerAdapter = new MyViewPagerAdapter(this, layouts);
        viewPager2.setAdapter(myViewPagerAdapter);
        final int[] currentItem = {viewPager2.getCurrentItem()};
        final int[] totalPages = {viewPager2.getAdapter().getItemCount()};

        viewPager2.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                int total = viewPager2.getAdapter().getItemCount();

                btn_prev.setVisibility(position == 0 ? View.GONE : View.VISIBLE);
                btn_next.setVisibility(position == total - 1 ? View.GONE : View.VISIBLE);
                startNow.setVisibility(position == total - 1 ? View.VISIBLE : View.GONE);
            }
        });


        btn_next.setOnClickListener(v -> {
            int current = viewPager2.getCurrentItem();
            if (current < viewPager2.getAdapter().getItemCount() - 1) {
                viewPager2.setCurrentItem(current + 1, true);
            }
        });

        btn_prev.setOnClickListener(v -> {
            int current = viewPager2.getCurrentItem();
            if (current > 0) {
                viewPager2.setCurrentItem(current - 1, true);
            }
        });

        startNow.setOnClickListener(v -> {
            Util.setClickAnimation(v, () -> {
                Util.saveState("is-FirstTime", false);
                startActivity(new Intent(IntroActivity.this, LoginSignUpActivity.class));
                finish();
            });

        });
        dots_indicator.attachTo(viewPager2);
    }
    private void openWebBrowser(String url) {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(browserIntent);
    }
    private static class MyViewPagerAdapter extends RecyclerView.Adapter<MyViewPagerAdapter.ViewHolder> {

        private LayoutInflater layoutInflater;
        private int[] layouts;

        public MyViewPagerAdapter(Context context, int[] layouts) {
            this.layoutInflater = LayoutInflater.from(context);
            this.layouts = layouts;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = layoutInflater.inflate(layouts[viewType], parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            // No need to bind data as layouts are static
        }

        @Override
        public int getItemCount() {
            return layouts.length;
        }

        @Override
        public int getItemViewType(int position) {
            return position;
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            public ViewHolder(View itemView) {
                super(itemView);
            }
        }
    }
}