package lk.punsisi.medifindtest.fragment;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.fragment.app.Fragment;

import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.concurrent.Executors;

import lk.punsisi.medifindtest.R;
import lk.punsisi.medifindtest.activity.MainActivity;
import lk.punsisi.medifindtest.activity.RegistrationActivity;
import lk.punsisi.medifindtest.databinding.FragmentSignInBinding;
import lk.punsisi.medifindtest.model.User;

public class SignInFragment extends Fragment {

    private FragmentSignInBinding binding;
    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firebaseFirestore;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSignInBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        firebaseAuth = FirebaseAuth.getInstance();
        firebaseFirestore = FirebaseFirestore.getInstance();

        // 1. Initialize the new Credential Manager
        CredentialManager credentialManager = CredentialManager.create(requireContext());

        // 2. Configure the Google ID request
        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(getString(R.string.default_web_client_id))
                .setAutoSelectEnabled(true)
                .build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build();

        // 3. Google Button Click Listener
        binding.btnGoogleSignIn.setOnClickListener(v -> {
            credentialManager.getCredentialAsync(
                    requireContext(),
                    request,
                    new CancellationSignal(),
                    Executors.newSingleThreadExecutor(),
                    new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                        @Override
                        public void onResult(GetCredentialResponse result) {
                            Credential credential = result.getCredential();
                            if (credential instanceof CustomCredential &&
                                    credential.getType().equals(GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)) {

                                GoogleIdTokenCredential googleId = GoogleIdTokenCredential.createFrom(credential.getData());
                                requireActivity().runOnUiThread(() -> firebaseAuthWithGoogle(googleId.getIdToken()));
                            }
                        }

                        @Override
                        public void onError(GetCredentialException e) {
                            requireActivity().runOnUiThread(() ->
                                    Toast.makeText(requireContext(), "Google sign in cancelled", Toast.LENGTH_SHORT).show()
                            );
                            Log.e("Auth", "Sign in failed", e);
                        }
                    }
            );
        });

        binding.textSignUp.setOnClickListener(v -> {
            if (getActivity() instanceof RegistrationActivity) {
                ((RegistrationActivity) getActivity()).switchToTab(1);
            }
        });

        // --- Standard Email/Password Routing ---
        binding.signInButton.setOnClickListener(v -> {
            String email = binding.signInEmail.getText().toString().trim();
            String password = binding.signInPassword.getText().toString().trim();

            if (email.isEmpty()){
                binding.signInEmail.setError("Email is required");
                binding.signInEmail.requestFocus();
                return;
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()){
                binding.signInEmail.setError("Please provide a valid email");
                binding.signInEmail.requestFocus();
                return;
            }

            if (password.isEmpty()){
                binding.signInPassword.setError("Password is required");
                binding.signInPassword.requestFocus();
                return;
            }

            firebaseAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            // 👉 NEW: Don't jump to MainActivity instantly! Fetch the role first.
                            String uid = firebaseAuth.getCurrentUser().getUid();
                            fetchRoleAndNavigate(uid);
                        } else {
                            Toast.makeText(requireContext(), "Login Failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
        });
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);

        firebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener(requireActivity(), task -> {
                    if (task.isSuccessful()) {

                        boolean isNewUser = task.getResult().getAdditionalUserInfo().isNewUser();
                        String uid = firebaseAuth.getCurrentUser().getUid();

                        if (isNewUser) {
                            String name = firebaseAuth.getCurrentUser().getDisplayName();
                            String email = firebaseAuth.getCurrentUser().getEmail();

                            User user = User.builder().uid(uid).name(name).email(email).build();
                            firebaseFirestore.collection("users").document(uid).set(user);

                            // 👉 NEW: It's a brand new user, so default to "user" role instantly!
                            saveRoleLocally("user");
                            navigateToMain();
                        } else {
                            // 👉 NEW: Returning Google user. Check their role!
                            fetchRoleAndNavigate(uid);
                        }
                    } else {
                        Toast.makeText(requireContext(), "Authentication Failed.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ==========================================
    // --- OFFLINE-FIRST ROLE ARCHITECTURE ---
    // ==========================================

    private void fetchRoleAndNavigate(String uid) {
        // Fetch the profile ONE time during login
        firebaseFirestore.collection("users").document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    String role = "user"; // Default safety fallback
                    if (documentSnapshot.exists() && documentSnapshot.getString("role") != null) {
                        role = documentSnapshot.getString("role");
                    }

                    saveRoleLocally(role);
                    navigateToMain();
                })
                .addOnFailureListener(e -> {
                    // If internet drops right after auth, default to user so they aren't stuck
                    saveRoleLocally("user");
                    navigateToMain();
                });
    }

    private void saveRoleLocally(String role) {
        // SharedPreferences is a tiny, lightning-fast local XML file
        SharedPreferences sharedPreferences = requireActivity().getSharedPreferences("MediFindPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("USER_ROLE", role);
        editor.apply(); // apply() saves in the background perfectly!
    }

    private void navigateToMain() {
        Toast.makeText(requireContext(), "Login Successful!", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(requireActivity(), MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}