package lk.punsisi.medifindtest.activity;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.tbuonomo.viewpagerdotsindicator.DotsIndicator;

import lk.punsisi.medifindtest.adapter.OnBoardingAdapter;
import lk.punsisi.medifindtest.databinding.ActivityOnBordingBinding;

public class OnBording extends AppCompatActivity {

    private ActivityOnBordingBinding binding;
    private ViewPager2 viewPager2;
    private DotsIndicator dotsIndicator;

    // 1. Setup the modern Permission Launcher
    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                // This block runs AFTER the user clicks "Allow" or "Deny" on the popups.
                // We don't strictly block them if they say no, we just proceed to the app.
                completeOnboarding();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityOnBordingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewPager2 = binding.viewPager2;
        dotsIndicator = binding.dotsIndicator;

        //set adapter to viewpager
        OnBoardingAdapter adapter = new OnBoardingAdapter(this);
        viewPager2.setAdapter(adapter);

        dotsIndicator.attachTo(viewPager2);
    }

    public void moveToNextPage() {
        if (viewPager2 != null) {
            viewPager2.setCurrentItem(viewPager2.getCurrentItem() + 1, true);
        }
    }

    public void moveToPreviousPage() {
        if (viewPager2 != null && viewPager2.getCurrentItem() > 0) {
            viewPager2.setCurrentItem(viewPager2.getCurrentItem() - 1, true);
        }
    }

    // 2. Intercept the button click to ask for permissions first!
    public void openRegistrationPage() {
        permissionLauncher.launch(new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        });
    }

    // 3. Handle the final routing and SharedPreferences
    private void completeOnboarding() {
        // Save the flag as FALSE so they never see the ViewPager again
        SharedPreferences preferences = getSharedPreferences("MediFindPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("isFirstTimeLaunch", false);
        editor.apply(); // MUST use apply() to save it securely in the background

        // Finally, move them to the Registration screen
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish(); // Destroy the Onboarding activity so they can't click 'Back' to return to it
    }
}