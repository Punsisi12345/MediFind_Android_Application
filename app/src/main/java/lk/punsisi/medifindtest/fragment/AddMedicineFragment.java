package lk.punsisi.medifindtest.fragment;

import android.net.Uri;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.collect.Maps;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lk.punsisi.medifindtest.R;
import lk.punsisi.medifindtest.databinding.FragmentAddMedicineBinding;
import lk.punsisi.medifindtest.model.Category;
import lk.punsisi.medifindtest.model.Medicine;
import lk.punsisi.medifindtest.room.AppDatabase;
import lk.punsisi.medifindtest.room.CategoryDao;
import lk.punsisi.medifindtest.room.MedicineDao;

public class AddMedicineFragment extends Fragment {

    private FragmentAddMedicineBinding binding;

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private FirebaseStorage storage;
    private MedicineDao medicineDao;
    private CategoryDao categoryDao;
    private ExecutorService executorService;

    private Uri selectedImageUri = null;
    private String currentPharmacyName = "Unknown Pharmacy";
    private String selectedCategoryId = null;
    private String selectedCategoryName = null;

    private Long selectedExpiryDateMillis = null;


    private HashMap<String, String> categoryMap = new HashMap<>();

    //image picker
    private ActivityResultLauncher<String> imagePickerLauncher;


    private AlertDialog loadingDialog;


    private boolean isEditMode = false;
    private String editingMedicineId = null;
    private String existingImageUrl = null;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentAddMedicineBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        storage = FirebaseStorage.getInstance();
        categoryDao = AppDatabase.getDatabase(requireContext()).categoryDao();
        medicineDao = AppDatabase.getDatabase(requireContext()).medicineDao();
        executorService = Executors.newSingleThreadExecutor();

        //back button press
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {

                int backStackCount = requireActivity().getSupportFragmentManager().getBackStackEntryCount();

                if (backStackCount > 0) {

                    requireActivity().getSupportFragmentManager().popBackStack();
                } else {

                    BottomNavigationView bottomNav = requireActivity().findViewById(R.id.bottom_navigation_view);
                    if (bottomNav != null) {

                        bottomNav.setSelectedItemId(R.id.bottom_nav_home);
                    } else {

                        setEnabled(false);
                        requireActivity().getOnBackPressedDispatcher().onBackPressed();
                    }
                }
            }
        });

        setupImagePicker();
        fetchPharmacyDetails();
        loadCategoriesForDropdown();
        setupDatePicker();

        //check if edit mode
        if (getArguments() != null && getArguments().containsKey("MEDICINE_ID_TO_EDIT")) {
            isEditMode = true;
            editingMedicineId = getArguments().getString("MEDICINE_ID_TO_EDIT");

            binding.tvHeaderTitle.setText("Edit Medicine");
            binding.btnAddMedicine.setText("Update Medicine");

            loadMedicineDataForEditing(editingMedicineId);
        }

        binding.cardUploadImage.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));

        binding.btnAddMedicine.setOnClickListener(v -> {
            if (validateInputs()) {
                processAndSaveMedicine();
            }
        });

    }

    private void setupDatePicker() {

        CalendarConstraints constraints = new CalendarConstraints.Builder()
                .setValidator(DateValidatorPointForward.now())
                .build();

        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select Expiry Date")
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .setCalendarConstraints(constraints)
                .build();

        datePicker.addOnPositiveButtonClickListener(selection -> {
            selectedExpiryDateMillis = selection;

            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            binding.etExpiryDate.setText(sdf.format(new Date(selection)));
            binding.etExpiryDate.setError(null);
        });


        binding.etExpiryDate.setOnClickListener(v -> {
            if (!datePicker.isAdded()) {
                datePicker.show(getParentFragmentManager(), "EXPIRY_DATE_PICKER");
            }
        });
    }

    //open device image gallery
    private void setupImagePicker() {
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        selectedImageUri = uri;
                        binding.ivMedicineImage.setImageURI(uri);


                        binding.ivMedicineImage.setImageTintList(null);
                        binding.tvImagePlaceholder.setVisibility(View.GONE);
                        binding.ivMedicineImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    }
                }
        );
    }

    private void fetchPharmacyDetails(){
        if (auth.getCurrentUser() == null) return;

        String uid = auth.getCurrentUser().getUid();

        db.collection("pharmacist_requests").document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists() && documentSnapshot.getString("pharmacyName") != null){
                        currentPharmacyName = documentSnapshot.getString("pharmacyName");
                    }
                });
    }

    private void loadCategoriesForDropdown(){
        executorService.execute(() -> {
            List<Category> localCategories = categoryDao.getActiveCategories();
            List<String> categoryNames = new ArrayList<>();

            for (Category cat : localCategories){
                categoryNames.add(cat.getName());
                categoryMap.put(cat.getName(), cat.getId());
            }

            requireActivity().runOnUiThread(() -> {
                ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), R.layout.item_category_dropdown, categoryNames);
                binding.autoCompleteCategory.setAdapter(adapter);

                binding.autoCompleteCategory.setOnItemClickListener((parent, view, position, id) -> {
                    selectedCategoryName = adapter.getItem(position);
                    selectedCategoryId = categoryMap.get(selectedCategoryName);
                });
            });
        });
    }


    private boolean validateInputs(){
        if (!isEditMode && selectedImageUri == null) {
            Toast.makeText(requireContext(), "Please select an image", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (binding.etMedicineName.getText().toString().trim().isEmpty()) {
            binding.etMedicineName.setError("Required");
            return false;
        }
        if (selectedCategoryId == null) {
            binding.autoCompleteCategory.setError("Please select a category");
            return false;
        }
        if (binding.etPrice.getText().toString().trim().isEmpty()) {
            binding.etPrice.setError("Required");
            return false;
        }
        if (binding.etQuantity.getText().toString().trim().isEmpty()) {
            binding.etQuantity.setError("Required");
            return false;
        }
        if (selectedExpiryDateMillis == null) {
            binding.etExpiryDate.setError("Please select an expiry date");
            return false;
        }
        return true;
    }

    private void processAndSaveMedicine() {
        showLoadingDialog();

        String finalMedicineId = isEditMode ? editingMedicineId : java.util.UUID.randomUUID().toString();

        int enteredQuantity = Integer.parseInt(binding.etQuantity.getText().toString().trim());
        String calculatedStatus;
        if (enteredQuantity <= 0) {
            calculatedStatus = "Out of Stock";
        } else if (enteredQuantity <= 10) {
            calculatedStatus = "Low Stock";
        } else {
            calculatedStatus = "In Stock";
        }

        if (selectedImageUri != null) {
            // User selected a NEW image (works for both Add and Edit)
            StorageReference storageRef = storage.getReference().child("medicine_images/" + finalMedicineId + ".jpg");
            storageRef.putFile(selectedImageUri)
                    .addOnSuccessListener(taskSnapshot -> storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                        saveToFirestore(finalMedicineId, uri.toString(), enteredQuantity, calculatedStatus);
                    }))
                    .addOnFailureListener(e -> {
                        loadingDialog.dismiss();
                        Toast.makeText(requireContext(), "Image Upload Failed", Toast.LENGTH_SHORT).show();
                    });
        } else {
            saveToFirestore(finalMedicineId, existingImageUrl, enteredQuantity, calculatedStatus);
        }
    }

    private void saveToFirestore(String medicineId, String imageUrl, int quantity, String status) {
        String uid = auth.getCurrentUser().getUid();

        Medicine updatedMedicine = Medicine.builder()
                .id(medicineId)
                .name(binding.etMedicineName.getText().toString().trim())
                .description(binding.etDescription.getText().toString().trim())
                .categoryId(selectedCategoryId)
                .categoryName(selectedCategoryName)
                .dosage(binding.etDosage.getText().toString().trim())
                .price(Double.parseDouble(binding.etPrice.getText().toString().trim()))
                .quantity(quantity)
                .expiryDate(selectedExpiryDateMillis)
                .requiresPrescription(binding.switchPrescription.isChecked())
                .imageUrl(imageUrl)
                .pharmacistId(uid)
                .pharmacyName(currentPharmacyName)
                .status(status)
                .salesCount(0)
                .lastUpdated(System.currentTimeMillis())
                .deleted(false)
                .build();

        db.collection("medicines").document(medicineId).set(updatedMedicine)
                .addOnSuccessListener(aVoid -> {

                    //save to local db
                    executorService.execute(() -> {
                        if (isEditMode) {
                            medicineDao.updateMedicine(updatedMedicine);
                        } else {
                            medicineDao.insertMedicine(updatedMedicine);
                        }

                        requireActivity().runOnUiThread(() -> {
                            loadingDialog.dismiss();
                            String successMessage = isEditMode ? "Medicine Updated!" : "Medicine Added!";
                            Toast.makeText(requireContext(), successMessage, Toast.LENGTH_SHORT).show();


                            requireActivity().getSupportFragmentManager().popBackStack();
                        });
                    });
                })
                .addOnFailureListener(e -> {
                    loadingDialog.dismiss();
                    Toast.makeText(requireContext(), "Database Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void showLoadingDialog() {
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setPadding(60, 60, 60, 60);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        layout.addView(new ProgressBar(requireContext()));

        TextView tvMessage = new TextView(requireContext());
        tvMessage.setText("Adding Medicine...");
        tvMessage.setTextSize(16f);
        tvMessage.setPadding(40, 0, 0, 0);
        layout.addView(tvMessage);

        loadingDialog = new MaterialAlertDialogBuilder(requireContext())
                .setCancelable(false)
                .setView(layout)
                .create();
        loadingDialog.show();
    }


    private void loadMedicineDataForEditing(String medicineId) {
        executorService.execute(() -> {
            Medicine medicine = medicineDao.getMedicineById(medicineId);

            if (medicine != null) {
                requireActivity().runOnUiThread(() -> {

                    binding.etMedicineName.setText(medicine.getName());
                    binding.etDescription.setText(medicine.getDescription());
                    binding.etDosage.setText(medicine.getDosage());
                    binding.etPrice.setText(String.valueOf(medicine.getPrice()));
                    binding.etQuantity.setText(String.valueOf(medicine.getQuantity()));


                    binding.switchPrescription.setChecked(medicine.isRequiresPrescription());

                    selectedCategoryId = medicine.getCategoryId();
                    selectedCategoryName = medicine.getCategoryName();
                    binding.autoCompleteCategory.setText(medicine.getCategoryName(), false);

                    if (medicine.getExpiryDate() > 0) {
                        selectedExpiryDateMillis = medicine.getExpiryDate();
                        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                        binding.etExpiryDate.setText(sdf.format(new Date(medicine.getExpiryDate())));
                    }

                    existingImageUrl = medicine.getImageUrl();
                    binding.tvImagePlaceholder.setVisibility(View.GONE);
                    binding.ivMedicineImage.setImageTintList(null);
                    binding.ivMedicineImage.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);

                    com.bumptech.glide.Glide.with(requireContext())
                            .load(existingImageUrl)
                            .into(binding.ivMedicineImage);
                });
            }
        });
    }


    @Override
    public void onResume() {
        super.onResume();
        BottomNavigationView bottomNav =
                requireActivity().findViewById(R.id.bottom_navigation_view);
        if (bottomNav != null) {
            bottomNav.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}