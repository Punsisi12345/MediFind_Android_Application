package lk.punsisi.medifindtest.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import lk.punsisi.medifindtest.R;
import lk.punsisi.medifindtest.activity.RegistrationActivity;
import lk.punsisi.medifindtest.databinding.FragmentSignUpBinding;
import lk.punsisi.medifindtest.model.User;

public class SignUpFragment extends Fragment {

    private FragmentSignUpBinding binding;
    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firebaseFirestore;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentSignUpBinding.inflate(inflater, container, false);

        binding.textSignUp.setOnClickListener(v -> {
            if (getActivity() instanceof RegistrationActivity) {
                ((RegistrationActivity) getActivity()).switchToTab(0);
            }
        });

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        firebaseAuth = FirebaseAuth.getInstance();
        firebaseFirestore = FirebaseFirestore.getInstance();

        binding.signUpButton.setOnClickListener(v -> {
            String name = binding.signUpName.getText().toString().trim();
            String email = binding.signUpEmail.getText().toString().trim();
            String password = binding.signUpPassword.getText().toString().trim();
            String retypePassword = binding.signUpRetypePassword.getText().toString().trim();

            if (name.isEmpty()) {
                binding.signUpName.setError("Name is required");
                binding.signUpName.requestFocus();
                return;
            }

            if (email.isEmpty()) {
                binding.signUpEmail.setError("Email is required");
                binding.signUpEmail.requestFocus();
                return;
            }

            if (password.isEmpty()) {
                binding.signUpPassword.setError("Password is required");
                binding.signUpPassword.requestFocus();
                return;
            }

            if (password.length() < 8) {
                binding.signUpPassword.setError("Password must be at least 8 characters");
                binding.signUpPassword.requestFocus();
                return;
            }

            if (retypePassword.isEmpty()) {
                binding.signUpRetypePassword.setError("Retype Password is required");
                binding.signUpRetypePassword.requestFocus();
                return;
            }

            if (!password.equals(retypePassword)) {
                binding.signUpRetypePassword.setError("Passwords do not match");
                binding.signUpRetypePassword.requestFocus();
                return;
            }

            // Firebase Registration
            firebaseAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            String uid = task.getResult().getUser().getUid();

                            User user = User.builder()
                                    .uid(uid)
                                    .name(name)
                                    .email(email)
                                    .build();

                            firebaseFirestore.collection("users")
                                    .document(uid)
                                    .set(user)
                                    .addOnSuccessListener(unused -> {

                                        // 👉 NEW: Save the role locally so the device knows who they are!
                                        saveRoleLocally("user");

                                        Toast.makeText(requireContext(), "Saved Successfully", Toast.LENGTH_SHORT).show();

                                        // Tell the hosting Activity to switch to the Sign In tab
                                        ((RegistrationActivity) requireActivity()).switchToTab(0);
                                    })
                                    .addOnFailureListener(e -> {
                                        Toast.makeText(requireContext(), "Try again: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                    });
                        } else {
                            Toast.makeText(requireContext(), "Error: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
        });
    }

    // 👉 NEW: The local storage helper method
    private void saveRoleLocally(String role) {
        SharedPreferences sharedPreferences = requireActivity().getSharedPreferences("MediFindPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("USER_ROLE", role);
        editor.apply();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}