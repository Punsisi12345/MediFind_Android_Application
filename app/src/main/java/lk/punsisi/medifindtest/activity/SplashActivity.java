package lk.punsisi.medifindtest.activity;

import android.animation.Animator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.airbnb.lottie.LottieAnimationView;

import lk.punsisi.medifindtest.R;

public class SplashActivity extends AppCompatActivity {

    //prevent app opening twice
    private boolean isRoutingComplete = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        if (!isTaskRoot()
                && getIntent().hasCategory(android.content.Intent.CATEGORY_LAUNCHER)
                && getIntent().getAction() != null
                && getIntent().getAction().equals(android.content.Intent.ACTION_MAIN)) {
            finish();
            return;
        }

        setContentView(R.layout.activity_splash);

        //check notification data
        if (getIntent() != null && getIntent().getExtras() != null) {

            Log.d("FCM_DEBUG", "-----------------------------------------");
            Log.d("FCM_DEBUG", "SplashActivity woke up from a notification!");


            for (String key : getIntent().getExtras().keySet()) {
                Log.d("FCM_DEBUG", "Found Key -> " + key + " : " + getIntent().getExtras().get(key));
            }
            Log.d("FCM_DEBUG", "-----------------------------------------");


            String orderId = getIntent().getStringExtra("orderId");

            if (orderId != null) {
                Log.d("FCM_DEBUG", "Valid orderId found! Skipping animation.");
                routeToNextScreen();
                return;
            } else {
                Log.e("FCM_DEBUG", "WARNING: Extras exist, but 'orderId' is missing!");
            }
        } else {
            Log.d("FCM_DEBUG", "Normal app launch. No notification data found.");
        }

        // animation play
        LottieAnimationView lottieAnimationView = findViewById(R.id.lottieAnimationView);
        lottieAnimationView.addAnimatorListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(@NonNull Animator animation) {}

            @Override
            public void onAnimationEnd(@NonNull Animator animation) {
                routeToNextScreen();
            }

            @Override
            public void onAnimationCancel(@NonNull Animator animation) {}

            @Override
            public void onAnimationRepeat(@NonNull Animator animation) {}
        });


        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            routeToNextScreen();
        }, 3000);
    }

    private void routeToNextScreen() {
        if (isRoutingComplete) return;
        isRoutingComplete = true;

        SharedPreferences preferences = getSharedPreferences("MediFindPrefs", MODE_PRIVATE);
        boolean isFirstTime = preferences.getBoolean("isFirstTimeLaunch", true);

        Intent intent;
        if (isFirstTime) {
            intent = new Intent(SplashActivity.this, OnBording.class);
        } else {
            intent = new Intent(SplashActivity.this, MainActivity.class);
        }

        if (getIntent() != null && getIntent().getExtras() != null) {
            intent.putExtras(getIntent().getExtras());
        }

        startActivity(intent);
        finish();
    }
}