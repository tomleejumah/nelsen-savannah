package com.app.nisisiafrica;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager.widget.ViewPager;

import com.LiquidPager.liquid_swipe.LiquidPager;
import com.app.nisisiafrica.Adapter.LiquidPagerAdapter;
import com.app.nisisiafrica.Auth.LoginSignUpActivity;
import com.app.nisisiafrica.Fragment.LiquidPagerFragment;

public class IntroActivity extends AppCompatActivity implements LiquidPagerFragment.OnTermsAndConditionsListener {
    private static final String TAG = "IntroActivity";
    private View termsView;
    private ViewGroup rootLayout;
    private LiquidPager liquidPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_intro);

        rootLayout = findViewById(R.id.main1);
        liquidPager = findViewById(R.id.liquidPager);
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        setupFullscreenUI();
        setupLiquidPager();
    }


    private void setupFullscreenUI() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
    }

    private void setupLiquidPager() {
        if (liquidPager != null) {
            LiquidPagerAdapter adapter = new LiquidPagerAdapter(getSupportFragmentManager());
            liquidPager.setAdapter(adapter);

            liquidPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
                @Override
                public void onPageSelected(int position) {
                    onTermsAndConditionsShown(position == 3);
                }

                @Override
                public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                }

                @Override
                public void onPageScrollStateChanged(int state) {
                }
            });
        }
    }

    @Override
    public void onTermsAndConditionsShown(boolean isShown) {
        try {
            // Access the termsView from the fragment directly
            LiquidPagerFragment fragment = (LiquidPagerFragment) getSupportFragmentManager()
                    .findFragmentByTag("android:switcher:" + liquidPager.getId() + ":" + liquidPager.getCurrentItem());
            if (fragment != null && fragment.termsView != null) {

                if (isShown) {
                    termsView = fragment.getView();
                    assert termsView != null;
                    setupTermsViewListeners(termsView);

                }

            }
        } catch (Exception e) {
            Log.e("IntroActivity", "Error managing terms view visibility", e);
        }
    }

    private void setupTermsViewListeners(View termsView) {
        termsView.findViewById(R.id.radio_btn_male).setOnClickListener(this::setClickAnimation);
        termsView.findViewById(R.id.radio_btn_female).setOnClickListener(this::setClickAnimation);

        Button button = termsView.findViewById(R.id.btn_proceed);
        button.setOnClickListener(v -> {
            setClickAnimation(v);

            // Retrieve the required views from the termsView
            RadioGroup radioGroup = termsView.findViewById(R.id.sex_radio_group);
            CheckBox checkBox = termsView.findViewById(R.id.check_box);

            // Validate input fields and the checkbox
            boolean inputsValid = validateInputs(termsView);
            boolean checkBoxChecked = checkBox.isChecked();

            if (inputsValid && checkBoxChecked) {
                // Make sure a radio button is selected before proceeding
                int selectedId = radioGroup.getCheckedRadioButtonId();
                if (selectedId == -1) {
                    shakeView(radioGroup);
                    return;
                }

                String role = ((RadioButton) termsView.findViewById(selectedId)).getText().toString();

                // Example logic: if role isn't "Mentor", then assign "Mentee"
                if (!role.equals("Mentor")) {
                    role = "Mentee";
                }

                // Save the role in SharedPreferences
                getSharedPreferences("ROLE", MODE_PRIVATE)
                        .edit()
                        .putString("UserRole", role)
                        .apply();

                // Start the next activity
                Intent intent = new Intent(IntroActivity.this, LoginSignUpActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
            } else {
                // If inputs are not valid, shake the corresponding view(s)

                // Shake the checkbox if it is not checked
                if (!checkBoxChecked) {
                    shakeView(checkBox);
                }
                // Shake the radio group if the inputs are not valid
                if (!inputsValid) {
                    shakeView(radioGroup);
                }
            }
        });

    }

    private boolean validateInputs(View termsView) {
        RadioGroup radioGroup = termsView.findViewById(R.id.sex_radio_group);
        final int selectedGenderLayoutButtonId = radioGroup.getCheckedRadioButtonId();
        return selectedGenderLayoutButtonId != -1;
    }

    public void shakeView(final View view) {
        final ObjectAnimator shakeAnimator = ObjectAnimator.ofFloat(view, "translationX",
                0f, 10f, -10f, 10f, -10f, 5f, -5f, 0f);
        shakeAnimator.setDuration(500);
        shakeAnimator.start();
    }

    public void setClickAnimation(View v) {
        v.animate()
                .scaleX(0.8f)
                .scaleY(0.8f)
                .setDuration(25)
                .withEndAction(() -> {
                    v.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(25)
                            .start();
                })
                .start();
    }
}