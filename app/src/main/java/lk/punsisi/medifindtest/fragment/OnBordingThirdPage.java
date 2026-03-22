package lk.punsisi.medifindtest.fragment;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import lk.punsisi.medifindtest.R;
import lk.punsisi.medifindtest.activity.OnBording;
import lk.punsisi.medifindtest.databinding.FragmentOnBordingSecondPageBinding;
import lk.punsisi.medifindtest.databinding.FragmentOnBordingThirdPageBinding;

public class OnBordingThirdPage extends Fragment {

    private FragmentOnBordingThirdPageBinding binding;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentOnBordingThirdPageBinding.inflate(inflater,container,false);

        binding.finishButton.setOnClickListener(v -> {
            if (getActivity() instanceof OnBording){
                ((OnBording) getActivity()).openRegistrationPage();
            }
        });

        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}