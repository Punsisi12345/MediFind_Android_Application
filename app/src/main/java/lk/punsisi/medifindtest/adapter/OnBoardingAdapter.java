package lk.punsisi.medifindtest.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import lk.punsisi.medifindtest.fragment.OnBordingFirstPage;
import lk.punsisi.medifindtest.fragment.OnBordingSecondPage;
import lk.punsisi.medifindtest.fragment.OnBordingThirdPage;

public class OnBoardingAdapter extends FragmentStateAdapter {

    public OnBoardingAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @Override
    public int getItemCount() {
        return 3;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0: return new OnBordingFirstPage();
            case 1: return new OnBordingSecondPage();
            case 2: return new OnBordingThirdPage();
            default: return new OnBordingFirstPage();
        }
    }
}