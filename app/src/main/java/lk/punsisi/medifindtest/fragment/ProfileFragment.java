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
import com.google.android.material.imageview.ShapeableImageView;
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
import lk.punsisi.medifindtest.databinding.BottomSheetDeliveryAddressBinding;
import lk.punsisi.medifindtest.databinding.BottomSheetPharmacistRequestBinding;
import lk.punsisi.medifindtest.databinding.BottomSheetSupportBinding;
import lk.punsisi.medifindtest.databinding.FragmentProfileBinding;
import lk.punsisi.medifindtest.model.DeliveryAddress;
import lk.punsisi.medifindtest.model.User;

public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firebaseFirestore;
    private FirebaseStorage firebaseStorage;
    private String currentUserId;

    private Uri selectedLogoUri = null;
    private ShapeableImageView activeLogoImageView = null;

    private final ActivityResultLauncher<PickVisualMediaRequest> pickMedia =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {

                    Glide.with(requireContext())
                            .load(uri)
                            .into(binding.imgProfilePicture);

                    // profile image upload to firebase
                    uploadImageToFirebaseStorage(uri);
                } else {
                    Log.d("PhotoPicker", "No media selected");
                }
            });

    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(),
                    uri -> {
                        if (uri != null) {
                            selectedLogoUri = uri;
                            if (activeLogoImageView != null) {
                                activeLogoImageView.setImageURI(uri); // Show preview instantly
                            }
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


        //edit name
        binding.btnEditName.setOnClickListener(v -> showEditNameDialog());

        //become pharmacist
        binding.cardUpgradePharmacist.setOnClickListener(v -> showPharmacistRequestBottomSheet());

        //change profile image
        binding.cardEditPicture.setOnClickListener(v -> {
            pickMedia.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build());
        });

        //add delivery address
        binding.menuDelivaryAddress.setOnClickListener(v -> showDeliveryAddressBottomSheet());

        //support menu load
        binding.menuSupport.setOnClickListener(v -> showSupportBottomSheet());


    }

    private void loadUserProfile() {
        firebaseFirestore.collection("users").document(currentUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        User user = documentSnapshot.toObject(User.class);
                        if (user != null) {
                            binding.tvProfileName.setText(user.getName());
                            binding.tvProfileEmail.setText(user.getEmail());

                            if (user.getRole() != null) {
                                binding.tvProfileRole.setText(user.getRole());
                            }

                            binding.menuDelivaryAddress.setCompoundDrawablePadding(32);

                            if (documentSnapshot.contains("deliveryAddress") && documentSnapshot.get("deliveryAddress") != null) {

                                binding.menuDelivaryAddress.setCompoundDrawablesWithIntrinsicBounds(
                                        R.drawable.ic_status_complete, 0, R.drawable.baseline_arrow_forward_ios_24, 0);
                            } else {

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

    private void uploadImageToFirebaseStorage(Uri imageUri) {
        Toast.makeText(requireContext(), "Uploading Profile Picture...", Toast.LENGTH_SHORT).show();

        StorageReference fileReference = firebaseStorage.getReference()
                .child("profile_images/" + currentUserId + ".jpg");

        fileReference.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> {

                    fileReference.getDownloadUrl().addOnSuccessListener(uri -> {
                        String downloadUrl = uri.toString();

                        //upload profile image to firebase storage
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

    private void showPharmacistRequestBottomSheet() {

        selectedLogoUri = null;

        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(requireContext());
        BottomSheetPharmacistRequestBinding sheetBinding = BottomSheetPharmacistRequestBinding.inflate(getLayoutInflater());
        bottomSheetDialog.setContentView(sheetBinding.getRoot());

        activeLogoImageView = sheetBinding.ivPharmacyLogoPicker;

        activeLogoImageView.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));

        sheetBinding.btnSubmitRequest.setOnClickListener(v -> {

            String fullName = sheetBinding.etFullName.getText().toString().trim();
            String slmc = sheetBinding.etSlmcNumber.getText().toString().trim();
            String pharmacyName = sheetBinding.etPharmacyName.getText().toString().trim();
            String nmra = sheetBinding.etNmraLicense.getText().toString().trim();
            String address = sheetBinding.etPharmacyAddress.getText().toString().trim();
            String telephone = sheetBinding.etTelephoneNumber.getText().toString().trim();

            if (selectedLogoUri == null) {
                Toast.makeText(requireContext(), "Please upload a pharmacy logo", Toast.LENGTH_SHORT).show();
                return;
            }
            if (fullName.isEmpty() || slmc.isEmpty() || pharmacyName.isEmpty() || nmra.isEmpty() || address.isEmpty()) {
                Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            sheetBinding.btnSubmitRequest.setText("Uploading Logo...");
            sheetBinding.btnSubmitRequest.setEnabled(false);

            com.google.firebase.storage.StorageReference logoRef = com.google.firebase.storage.FirebaseStorage.getInstance()
                    .getReference().child("pharmacy_logos/" + currentUserId + ".jpg");

            logoRef.putFile(selectedLogoUri)
                    .addOnSuccessListener(taskSnapshot -> {

                        logoRef.getDownloadUrl().addOnSuccessListener(uri -> {

                            savePharmacistRequestToDatabase(
                                    fullName, slmc, pharmacyName, telephone, nmra, address, uri.toString(),
                                    bottomSheetDialog, sheetBinding.btnSubmitRequest
                            );
                        });
                    })
                    .addOnFailureListener(e -> {
                        sheetBinding.btnSubmitRequest.setText("Submit Request");
                        sheetBinding.btnSubmitRequest.setEnabled(true);
                        Toast.makeText(requireContext(), "Image Upload Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        });

        bottomSheetDialog.show();
    }

    private void savePharmacistRequestToDatabase(String fullName, String slmc, String pharmacyName, String telephone, String nmra,
                                                 String address, String profileImageUrl, BottomSheetDialog dialog, MaterialButton btnSubmit) {

        btnSubmit.setText("Saving Data...");

        Map<String, Object> requestData = new HashMap<>();
        requestData.put("uid", currentUserId);
        requestData.put("fullName", fullName);
        requestData.put("slmcRegNumber", slmc);
        requestData.put("pharmacyName", pharmacyName);
        requestData.put("nmraLicenseNumber", nmra);
        requestData.put("pharmacyAddress", address);
        requestData.put("profileImage", profileImageUrl);
        requestData.put("status", "pending");
        requestData.put("telephone", telephone);
        requestData.put("timestamp", System.currentTimeMillis());

        WriteBatch batch = firebaseFirestore.batch();

        DocumentReference requestRef = firebaseFirestore.collection("pharmacist_requests").document(currentUserId);
        batch.set(requestRef, requestData);

        DocumentReference userRef = firebaseFirestore.collection("users").document(currentUserId);
        batch.update(userRef, "pharmacistRequestStatus", "pending");

        batch.commit()
                .addOnSuccessListener(unused -> {
                    dialog.dismiss();
                    showSuccessDialog();
                })
                .addOnFailureListener(e -> {
                    btnSubmit.setText("Submit Request");
                    btnSubmit.setEnabled(true);
                    Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void showSuccessDialog() {
        new MaterialAlertDialogBuilder(requireContext(), com.google.android.material.R.style.ThemeOverlay_MaterialComponents_MaterialAlertDialog_Centered)
                .setTitle("Request Submitted!")
                .setMessage("Your profile is now under Admin review. We will cross-check your SLMC and NMRA credentials and notify you quickly.")
                .setPositiveButton("Awesome", (dialog, which) -> dialog.dismiss())
                .setCancelable(false)
                .show();
    }

    private void checkPharmacistRequestStatus() {
        firebaseFirestore.collection("pharmacist_requests").document(currentUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String status = documentSnapshot.getString("status");

                        if ("pending".equals(status)) {

                            binding.cardUpgradePharmacist.setCardBackgroundColor(Color.parseColor("#FFF3E0"));

                            binding.tvBecomePharmacistText.setText("Pharmacist Request Under Review");
                            binding.tvBecomePharmacistText.setTextColor(Color.parseColor("#E65100"));
                            binding.tvBecomePharmacistDesText.setText(status);
                            binding.tvBecomePharmacistDesText.setTextColor(Color.parseColor("#8B8000"));


                            binding.cardUpgradePharmacist.setOnClickListener(v -> {
                                Toast.makeText(requireContext(), "Your details are currently being reviewed by an Admin.", Toast.LENGTH_SHORT).show();
                            });

                        } else if ("approved".equals(status)) {
                            binding.cardUpgradePharmacist.setVisibility(View.GONE);
                        }
                    }
                })
                .addOnFailureListener(e -> Log.e("Profile", "Error checking request status", e));
    }

    private void showDeliveryAddressBottomSheet() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(requireContext());

        BottomSheetDeliveryAddressBinding sheetBinding = BottomSheetDeliveryAddressBinding.inflate(getLayoutInflater());
        bottomSheetDialog.setContentView(sheetBinding.getRoot());

        firebaseFirestore.collection("users").document(currentUserId).get().addOnSuccessListener(doc -> {
            if (doc.exists() && doc.contains("deliveryAddress")) {
                DeliveryAddress savedAddress = doc.get("deliveryAddress", DeliveryAddress.class);
                if (savedAddress != null) {

                    sheetBinding.etPhoneNumber.setText(savedAddress.getPhoneNumber());
                    sheetBinding.etAddressLine1.setText(savedAddress.getAddressLine1());
                    sheetBinding.etAddressLine2.setText(savedAddress.getAddressLine2());
                    sheetBinding.etHomeTown.setText(savedAddress.getHomeTown());
                    sheetBinding.etPostalCode.setText(savedAddress.getPostalCode());

                    // update button ui
                    sheetBinding.btnSaveAddress.setText("Update Address");
                    sheetBinding.btnSaveAddress.setBackgroundColor(Color.parseColor("#FFC000"));
                    sheetBinding.btnSaveAddress.setTextColor(Color.parseColor("#000000"));
                }
            }
        });

        sheetBinding.btnSaveAddress.setOnClickListener(v -> {

            String phone = sheetBinding.etPhoneNumber.getText().toString().trim();
            String line1 = sheetBinding.etAddressLine1.getText().toString().trim();
            String line2 = sheetBinding.etAddressLine2.getText().toString().trim();
            String town = sheetBinding.etHomeTown.getText().toString().trim();
            String postal = sheetBinding.etPostalCode.getText().toString().trim();

            if (phone.isEmpty() || line1.isEmpty() || town.isEmpty() || postal.isEmpty()) {
                Toast.makeText(requireContext(), "Please fill all required fields", Toast.LENGTH_SHORT).show();
                return;
            }

            sheetBinding.btnSaveAddress.setText("Saving...");
            sheetBinding.btnSaveAddress.setEnabled(false);

            DeliveryAddress newAddress = new DeliveryAddress(phone, line1, line2, town, postal);

            firebaseFirestore.collection("users").document(currentUserId)
                    .update("deliveryAddress", newAddress)
                    .addOnSuccessListener(unused -> {
                        bottomSheetDialog.dismiss();
                        Toast.makeText(requireContext(), "Delivery Address Saved!", Toast.LENGTH_SHORT).show();

                        binding.menuDelivaryAddress.setCompoundDrawablesWithIntrinsicBounds(
                                R.drawable.ic_status_complete, 0, R.drawable.baseline_arrow_forward_ios_24, 0);
                    })
                    .addOnFailureListener(e -> {

                        sheetBinding.btnSaveAddress.setText("Save Address");
                        sheetBinding.btnSaveAddress.setEnabled(true);
                        Toast.makeText(requireContext(), "Failed to save: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        });

        bottomSheetDialog.show();
    }

    private void showSupportBottomSheet() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(requireContext());

        BottomSheetSupportBinding sheetBinding = BottomSheetSupportBinding.inflate(getLayoutInflater());
        bottomSheetDialog.setContentView(sheetBinding.getRoot());


        sheetBinding.btnCloseSupport.setOnClickListener(v -> bottomSheetDialog.dismiss());

        // phone dialer open
        sheetBinding.callIcon.setOnClickListener(v -> {

            String phone = sheetBinding.phoneNumber.getText().toString().trim();

            if (!phone.isEmpty()) {
                Intent intent = new Intent(Intent.ACTION_DIAL);
                intent.setData(Uri.parse("tel:" + phone));
                startActivity(intent);
            } else {
                Toast.makeText(requireContext(), "Phone number not available", Toast.LENGTH_SHORT).show();
            }
        });

        // email app open
        sheetBinding.emailIcon.setOnClickListener(v -> {

            String emailAddress = sheetBinding.email.getText().toString().trim();

            if (!emailAddress.isEmpty()) {

                Intent intent = new Intent(Intent.ACTION_SENDTO);
                intent.setData(Uri.parse("mailto:" + emailAddress));

                //automatically set subject and text for draft mail
                intent.putExtra(Intent.EXTRA_SUBJECT, "Support Request: MediFind App");
                intent.putExtra(Intent.EXTRA_TEXT,
                        "Hello MediFind Support,\n\nI am having an issue with...\n\n[App Version 1.0 | User ID: " + currentUserId + "]");
                startActivity(intent);
            } else {
                Toast.makeText(requireContext(), "Email address not available", Toast.LENGTH_SHORT).show();
            }
        });

        bottomSheetDialog.show();
    }


    @Override
    public void onResume() {
        super.onResume();

    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}