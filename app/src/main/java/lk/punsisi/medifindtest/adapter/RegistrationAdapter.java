package lk.punsisi.medifindtest.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import lk.punsisi.medifindtest.fragment.SignInFragment;
import lk.punsisi.medifindtest.fragment.SignUpFragment;

public class RegistrationAdapter extends FragmentStateAdapter {
    public RegistrationAdapter(@NonNull FragmentManager fragmentManager, @NonNull Lifecycle lifecycle) {
        super(fragmentManager, lifecycle);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == 1){
            return new SignUpFragment();
        }

        return new SignInFragment();
    }

    @Override
    public int getItemCount() {
        return 2;
    }
}
