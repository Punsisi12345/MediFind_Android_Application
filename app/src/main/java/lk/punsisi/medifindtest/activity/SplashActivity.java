package lk.punsisi.medifindtest.activity;

import android.animation.Animator;
import android.content.Intent;
import android.content.SharedPreferences; // Make sure this is imported!
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.airbnb.lottie.LottieAnimationView;

import lk.punsisi.medifindtest.R;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        LottieAnimationView lottieAnimationView = findViewById(R.id.lottieAnimationView);

        lottieAnimationView.addAnimatorListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(@NonNull Animator animation) {
                // Animation started, do nothing
            }

            @Override
            public void onAnimationEnd(@NonNull Animator animation) {
                // THE NEW ROUTING LOGIC:
                // 1. Open the local SharedPreferences file
                SharedPreferences preferences = getSharedPreferences("MediFindPrefs", MODE_PRIVATE);

                // 2. Check the flag (Defaults to true if the app was just installed)
                boolean isFirstTime = preferences.getBoolean("isFirstTimeLaunch", true);

                Intent intent;
                if (isFirstTime) {
                    // First time opening the app -> Go to Onboarding
                    intent = new Intent(SplashActivity.this, OnBording.class);
                } else {
                    // Already saw onboarding -> Go straight to the App
                    // TODO: Change "HomeActivity.class" if your main screen is named something else (like MainActivity.class or LoginActivity.class)
                    intent = new Intent(SplashActivity.this, MainActivity.class);
                }

                startActivity(intent);

                // close splash screen (if not use when click back button then splash screen will show)
                finish();
            }

            @Override
            public void onAnimationCancel(@NonNull Animator animation) {
            }

            @Override
            public void onAnimationRepeat(@NonNull Animator animation) {
            }
        });
    }
}