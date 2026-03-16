package lk.punsisi.medifindtest.fragment;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.Firebase;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;

import lk.punsisi.medifindtest.R;
import lk.punsisi.medifindtest.activity.MainActivity;
import lk.punsisi.medifindtest.activity.RegistrationActivity;
import lk.punsisi.medifindtest.databinding.FragmentProfileBinding;
import lk.punsisi.medifindtest.model.User;

public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firebaseFirestore;
    private FirebaseStorage firebaseStorage;
    private String currentUserId;

    private final ActivityResultLauncher<PickVisualMediaRequest> pickMedia =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {

                    // FIX: Never use setImageURI with the modern PhotoPicker! Use Glide.
                    Glide.with(requireContext())
                            .load(uri)
                            .into(binding.imgProfilePicture);

                    // Start uploading it to Firebase behind the scenes
                    uploadImageToFirebaseStorage(uri);
                } else {
                    Log.d("PhotoPicker", "No media selected");
                }
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        firebaseAuth = FirebaseAuth.getInstance();
        firebaseFirestore = FirebaseFirestore.getInstance();

        // Check if user is logged in
        if (firebaseAuth.getCurrentUser() != null) {
            currentUserId = firebaseAuth.getCurrentUser().getUid();
            loadUserProfile();


            binding.menuLogout.setOnClickListener(v -> {

                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Log Out")
                        .setMessage("Are you sure you want to log out?")
                        .setPositiveButton("Yes", (dialog, which) -> {
                            firebaseAuth.signOut();
                            Intent intent = new Intent(requireActivity(), MainActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                        })
                        .setNegativeButton("No", (dialog, which) -> dialog.dismiss())
                        .show();
            });


        } else {

            binding.tvProfileName.setText("Guest User");
            binding.tvProfileEmail.setText("Please log in");
            binding.menuLogout.setText("Log In");
            binding.menuLogout.setTextColor(Color.parseColor("#006B60"));
            binding.tvProfileRole.setVisibility(View.GONE);
            binding.cardUpgradePharmacist.setVisibility(View.GONE);
            binding.menuDelivaryAddress.setVisibility(View.GONE);

            //Log Out Button
            binding.menuLogout.setOnClickListener(v -> {

                Intent intent = new Intent(requireActivity(), RegistrationActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            });

        }

        // --- BUTTON CLICK LISTENERS ---

        // 1. Edit Name Button
        binding.btnEditName.setOnClickListener(v -> showEditNameDialog());

        // 2. Upgrade to Pharmacist Button
        binding.cardUpgradePharmacist.setOnClickListener(v -> showPharmacistRequestBottomSheet());

        binding.cardEditPicture.setOnClickListener(v -> {
            pickMedia.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build());
        });

        binding.menuDelivaryAddress.setOnClickListener(v -> showDeliveryAddressBottomSheet());

        binding.menuSupport.setOnClickListener(v -> showSupportBottomSheet());


    }

    // ==========================================
    // --- LOAD DATA LOGIC ---
    // ==========================================
    private void loadUserProfile() {
        firebaseFirestore.collection("users").document(currentUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        User user = documentSnapshot.toObject(User.class);
                        if (user != null) {
                            binding.tvProfileName.setText(user.getName());
                            binding.tvProfileEmail.setText(user.getEmail());
                            binding.tvProfileRole.setText("Registered User");

                            binding.menuDelivaryAddress.setCompoundDrawablePadding(32);

                            if (documentSnapshot.contains("deliveryAddress") && documentSnapshot.get("deliveryAddress") != null) {
                                // If it exists: Show Green Check on the left, Arrow on the right
                                binding.menuDelivaryAddress.setCompoundDrawablesWithIntrinsicBounds(
                                        R.drawable.ic_status_complete, 0, R.drawable.baseline_arrow_forward_ios_24, 0);
                            } else {
                                // If missing: Show Yellow Warning on the left, Arrow on the right
                                binding.menuDelivaryAddress.setCompoundDrawablesWithIntrinsicBounds(
                                        R.drawable.ic_status_missing, 0, R.drawable.baseline_arrow_forward_ios_24, 0);
                            }

                            String profileImageUrl = documentSnapshot.getString("profileImage");
                            if (profileImageUrl != null && !profileImageUrl.isEmpty()) {
                                Glide.with(requireContext())
                                        .load(profileImageUrl)
                                        .placeholder(R.drawable.baseline_profile_24)
                                        .into(binding.imgProfilePicture);
                            }
                        }

                        checkPharmacistRequestStatus();
                    }
                })
                .addOnFailureListener(e -> Log.e("Profile", "Error loading profile", e));
    }


    // ==========================================
    // --- FIREBASE STORAGE UPLOAD LOGIC ---
    // ==========================================
    private void uploadImageToFirebaseStorage(Uri imageUri) {
        Toast.makeText(requireContext(), "Uploading Profile Picture...", Toast.LENGTH_SHORT).show();

        // Create a folder called 'profile_images' and name the file with their specific User ID
        StorageReference fileReference = firebaseStorage.getInstance().getReference()
                .child("profile_images/" + currentUserId + ".jpg");

        fileReference.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> {
                    // Once uploaded, we need to ask Firebase for the public URL to view the image
                    fileReference.getDownloadUrl().addOnSuccessListener(uri -> {
                        String downloadUrl = uri.toString();

                        // Save that public URL to their Firestore profile!
                        saveImageUrlToFirestore(downloadUrl);
                    });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(requireContext(), "Failed to upload image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void saveImageUrlToFirestore(String imageUrl) {
        firebaseFirestore.collection("users").document(currentUserId)
                .update("profileImage", imageUrl)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(requireContext(), "Profile Picture Saved!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> Log.e("Profile", "Error saving image URL", e));
    }


    // ==========================================
    // --- EDIT NAME LOGIC ---
    // ==========================================
    private void showEditNameDialog() {
        EditText input = new EditText(requireContext());
        input.setHint("Enter new name");
        input.setText(binding.tvProfileName.getText().toString());
        input.setPadding(50, 50, 50, 50);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Update Name")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        updateNameInFirestore(newName);
                    }
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void updateNameInFirestore(String newName) {
        firebaseFirestore.collection("users").document(currentUserId)
                .update("name", newName)
                .addOnSuccessListener(unused -> {
                    binding.tvProfileName.setText(newName);
                    Toast.makeText(requireContext(), "Name updated successfully", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> Toast.makeText(requireContext(), "Failed to update name", Toast.LENGTH_SHORT).show());
    }

    // ==========================================
    // --- PHARMACIST REQUEST LOGIC ---
    // ==========================================
    private void showPharmacistRequestBottomSheet() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(requireContext());
        View bottomSheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_pharmacist_request, null);
        bottomSheetDialog.setContentView(bottomSheetView);

        TextInputEditText etFullName = bottomSheetView.findViewById(R.id.et_full_name);
        TextInputEditText etSlmcNumber = bottomSheetView.findViewById(R.id.et_slmc_number);
        TextInputEditText etPharmacyName = bottomSheetView.findViewById(R.id.et_pharmacy_name);
        TextInputEditText etNmraLicense = bottomSheetView.findViewById(R.id.et_nmra_license);
        TextInputEditText etPharmacyAddress = bottomSheetView.findViewById(R.id.et_pharmacy_address);
        MaterialButton btnSubmit = bottomSheetView.findViewById(R.id.btn_submit_request);

        btnSubmit.setOnClickListener(v -> {
            String fullName = etFullName.getText().toString().trim();
            String slmc = etSlmcNumber.getText().toString().trim();
            String pharmacyName = etPharmacyName.getText().toString().trim();
            String nmra = etNmraLicense.getText().toString().trim();
            String address = etPharmacyAddress.getText().toString().trim();

            if (fullName.isEmpty() || slmc.isEmpty() || pharmacyName.isEmpty() || nmra.isEmpty() || address.isEmpty()) {
                Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            btnSubmit.setText("Submitting...");
            btnSubmit.setEnabled(false);

            Map<String, Object> requestData = new HashMap<>();
            requestData.put("uid", currentUserId);
            requestData.put("fullName", fullName);
            requestData.put("slmcRegNumber", slmc);
            requestData.put("pharmacyName", pharmacyName);
            requestData.put("nmraLicenseNumber", nmra);
            requestData.put("pharmacyAddress", address);
            requestData.put("status", "pending");
            requestData.put("timestamp", System.currentTimeMillis());

            //we pass data to two different location if one fail other also fail in this check all success or not
            WriteBatch batch = firebaseFirestore.batch();

            DocumentReference requestRef = firebaseFirestore.collection("pharmacist_requests").document(currentUserId);
            batch.set(requestRef, requestData);

            DocumentReference userRef = firebaseFirestore.collection("users").document(currentUserId);
            batch.update(userRef, "pharmacistRequestStatus", "pending");

            batch.commit()
                            .addOnSuccessListener(unused -> {
                                bottomSheetDialog.dismiss();
                                showSuccessDialog();
                            })
                            .addOnFailureListener(e -> {
                                btnSubmit.setText("Submit Request");
                                btnSubmit.setEnabled(true);
                                Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
        });

        bottomSheetDialog.show();
    }

    private void showSuccessDialog() {
        new MaterialAlertDialogBuilder(requireContext(), com.google.android.material.R.style.ThemeOverlay_MaterialComponents_MaterialAlertDialog_Centered)
                .setTitle("Request Submitted!")
                .setMessage("Your profile is now under Admin review. We will cross-check your SLMC and NMRA credentials and notify you quickly.")
                .setPositiveButton("Awesome", (dialog, which) -> dialog.dismiss())
                .setCancelable(false)
                .show();
    }


    // ==========================================
    // --- CHECK REQUEST STATUS LOGIC ---
    // ==========================================
    private void checkPharmacistRequestStatus() {
        firebaseFirestore.collection("pharmacist_requests").document(currentUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String status = documentSnapshot.getString("status");

                        if ("pending".equals(status)) {
                            // 1. Change the Card Background to a soft warning color (Light Orange/Yellow)
                            binding.cardUpgradePharmacist.setCardBackgroundColor(android.graphics.Color.parseColor("#FFF3E0"));

                            // 2. Change the text to let the user know it is processing
                            binding.tvBecomePharmacistText.setText("Pharmacist Request Under Review");
                            binding.tvBecomePharmacistText.setTextColor(android.graphics.Color.parseColor("#E65100")); // Dark Orange text
                            binding.tvBecomePharmacistDesText.setText(status);
                            binding.tvBecomePharmacistDesText.setTextColor(Color.parseColor("#8B8000"));

                            // 3. Overwrite the click listener to show a Toast instead of opening the form!
                            binding.cardUpgradePharmacist.setOnClickListener(v -> {
                                Toast.makeText(requireContext(), "Your details are currently being reviewed by an Admin.", Toast.LENGTH_SHORT).show();
                            });

                        } else if ("APPROVED".equals(status)) {
                            // If they are already an approved Pharmacist, hide the upgrade card completely!
                            binding.cardUpgradePharmacist.setVisibility(View.GONE);
                        }
                    }
                })
                .addOnFailureListener(e -> Log.e("Profile", "Error checking request status", e));
    }



    // ==========================================
    // --- DELIVERY ADDRESS LOGIC ---
    // ==========================================
    private void showDeliveryAddressBottomSheet() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(requireContext());
        View bottomSheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_delivery_address, null);
        bottomSheetDialog.setContentView(bottomSheetView);

        TextInputEditText etPhone = bottomSheetView.findViewById(R.id.et_phone_number);
        TextInputEditText etLine1 = bottomSheetView.findViewById(R.id.et_address_line_1);
        TextInputEditText etLine2 = bottomSheetView.findViewById(R.id.et_address_line_2);
        TextInputEditText etTown = bottomSheetView.findViewById(R.id.et_home_town);
        TextInputEditText etPostal = bottomSheetView.findViewById(R.id.et_postal_code);
        MaterialButton btnSave = bottomSheetView.findViewById(R.id.btn_save_address);

        // PRE-FILL EXISTING DATA: Check if they already have an address saved!
        firebaseFirestore.collection("users").document(currentUserId).get().addOnSuccessListener(doc -> {
            if (doc.exists() && doc.contains("deliveryAddress")) {
                Map<String, Object> existingAddress = (Map<String, Object>) doc.get("deliveryAddress");
                if (existingAddress != null) {
                    etPhone.setText((String) existingAddress.get("phoneNumber"));
                    etLine1.setText((String) existingAddress.get("addressLine1"));
                    etLine2.setText((String) existingAddress.get("addressLine2"));
                    etTown.setText((String) existingAddress.get("homeTown"));
                    etPostal.setText((String) existingAddress.get("postalCode"));

                    btnSave.setText("Update Address");
                    btnSave.setBackgroundColor(Color.parseColor("#FFC000"));
                    btnSave.setTextColor(Color.parseColor("#000000"));
                }
            }
        });

        // HANDLE SAVE CLICK
        btnSave.setOnClickListener(v -> {
            String phone = etPhone.getText().toString().trim();
            String line1 = etLine1.getText().toString().trim();
            String line2 = etLine2.getText().toString().trim();
            String town = etTown.getText().toString().trim();
            String postal = etPostal.getText().toString().trim();

            if (phone.isEmpty() || line1.isEmpty() || town.isEmpty() || postal.isEmpty()) {
                Toast.makeText(requireContext(), "Please fill all required fields", Toast.LENGTH_SHORT).show();
                return;
            }

            btnSave.setText("Saving...");
            btnSave.setEnabled(false);

            // Create the Address Object (Map)
            Map<String, Object> addressData = new HashMap<>();
            addressData.put("phoneNumber", phone);
            addressData.put("addressLine1", line1);
            addressData.put("addressLine2", line2);
            addressData.put("homeTown", town);
            addressData.put("postalCode", postal);

            // Update the user's document by adding the deliveryAddress map
            firebaseFirestore.collection("users").document(currentUserId)
                    .update("deliveryAddress", addressData)
                    .addOnSuccessListener(unused -> {
                        bottomSheetDialog.dismiss();
                        Toast.makeText(requireContext(), "Delivery Address Saved!", Toast.LENGTH_SHORT).show();

                        // INSTANTLY UPDATE THE ICON TO GREEN!
                        binding.menuDelivaryAddress.setCompoundDrawablesWithIntrinsicBounds(
                                R.drawable.ic_status_complete, 0, R.drawable.baseline_arrow_forward_ios_24, 0);


                    })

                    .addOnFailureListener(e -> {
                        btnSave.setText("Save Address");
                        btnSave.setEnabled(true);
                        Toast.makeText(requireContext(), "Failed to save: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        });

        bottomSheetDialog.show();

    }




    // ==========================================
    // --- HELP & SUPPORT LOGIC ---
    // ==========================================
    private void showSupportBottomSheet() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(requireContext());
        View bottomSheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_support, null);
        bottomSheetDialog.setContentView(bottomSheetView);

        // Just one button to close the sheet!
        MaterialButton btnClose = bottomSheetView.findViewById(R.id.btn_close_support);
        btnClose.setOnClickListener(v -> bottomSheetDialog.dismiss());

        bottomSheetDialog.show();
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}