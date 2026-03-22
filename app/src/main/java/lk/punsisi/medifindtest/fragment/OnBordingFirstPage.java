package lk.punsisi.medifindtest.fragment;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import lk.punsisi.medifindtest.R;
import lk.punsisi.medifindtest.activity.OnBording;
import lk.punsisi.medifindtest.databinding.FragmentOnBordingFirstPageBinding;


public class OnBordingFirstPage extends Fragment {

    private FragmentOnBordingFirstPageBinding binding;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentOnBordingFirstPageBinding.inflate(inflater, container, false);

        binding.getstarted.setOnClickListener(v -> {

            if (getActivity() instanceof OnBording){
                ((OnBording) getActivity()).moveToNextPage();
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