package lk.punsisi.medifindtest.fragment;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lk.punsisi.medifindtest.R;
import lk.punsisi.medifindtest.adapter.InventoryAdapter;
import lk.punsisi.medifindtest.databinding.FragmentManageInventoryBinding;
import lk.punsisi.medifindtest.model.Medicine;
import lk.punsisi.medifindtest.room.AppDatabase;
import lk.punsisi.medifindtest.room.MedicineDao;

public class ManageInventoryFragment extends Fragment implements InventoryAdapter.OnInventoryItemClickListener {

    private FragmentManageInventoryBinding binding;

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private MedicineDao medicineDao;
    private ExecutorService executorService;

    private InventoryAdapter adapter;

    private List<Medicine> fullInventoryList = new ArrayList<>();
    private List<Medicine> filteredList = new ArrayList<>();

    private String currentCategorySelection = "All Categories";
    private String currentSearchQuery = "";
    private int currentFilterSelection = 0;
    private final String[] filterOptions = {"All Items", "In Stock", "Low Stock", "Out of Stock"};

    private int currentPage = 1;
    private final int itemsPerPage = 15;
    private int totalPages = 1;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentManageInventoryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        medicineDao = AppDatabase.getDatabase(requireContext()).medicineDao();
        executorService = Executors.newSingleThreadExecutor();

        setupRecyclerView();

        setupListeners();

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
    }

    private void setupRecyclerView() {
        adapter = new InventoryAdapter(requireContext(), this);
        binding.rvManageInventory.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvManageInventory.setAdapter(adapter);
    }


    private void loadInventoryData() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        executorService.execute(() -> {
            List<Medicine> inventory = medicineDao.getMyInventory(uid);

            requireActivity().runOnUiThread(() -> {
                fullInventoryList = inventory;
                applyFiltersAndPagination();
            });
        });
    }

    private void setupListeners() {
        // Floating Action Button -> Go to Add Medicine
        binding.fabAddMedicine.setOnClickListener(v -> {
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                    .replace(R.id.fragment_container, new AddMedicineFragment())
                    .addToBackStack(null)
                    .commit();
        });

        binding.etInventorySearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s.toString().toLowerCase().trim();
                currentPage = 1; // Reset to page 1 when searching
                applyFiltersAndPagination();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        binding.btnInventoryFilter.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Filter by Status")
                    .setSingleChoiceItems(filterOptions, currentFilterSelection, (dialog, which) -> {
                        currentFilterSelection = which;
                        currentPage = 1;
                        applyFiltersAndPagination();
                        dialog.dismiss();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });


        binding.btnCategoryFilter.setOnClickListener(v -> {

            List<String> dynamicCategories = new ArrayList<>();
            dynamicCategories.add("All Categories");

            for (Medicine m : fullInventoryList) {
                if (!dynamicCategories.contains(m.getCategoryName())) {
                    dynamicCategories.add(m.getCategoryName());
                }
            }

            String[] catArray = dynamicCategories.toArray(new String[0]);

            int checkedItem = dynamicCategories.indexOf(currentCategorySelection);
            if (checkedItem == -1) checkedItem = 0;

            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Filter by Category")
                    .setSingleChoiceItems(catArray, checkedItem, (dialog, which) -> {
                        currentCategorySelection = catArray[which];
                        currentPage = 1;
                        applyFiltersAndPagination();
                        dialog.dismiss();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });


        binding.btnPageNext.setOnClickListener(v -> {
            if (currentPage < totalPages) {
                currentPage++;
                applyFiltersAndPagination();
                binding.rvManageInventory.scrollToPosition(0);
            }
        });

        binding.btnPagePrev.setOnClickListener(v -> {
            if (currentPage > 1) {
                currentPage--;
                applyFiltersAndPagination();
                binding.rvManageInventory.scrollToPosition(0);
            }
        });


    }


    private void applyFiltersAndPagination() {
        filteredList.clear();


        for (Medicine medicine : fullInventoryList) {

            boolean matchesSearch = medicine.getName().toLowerCase().contains(currentSearchQuery) ||
                    medicine.getCategoryName().toLowerCase().contains(currentSearchQuery);

            boolean matchesStatus = false;
            int qty = medicine.getQuantity();
            if (currentFilterSelection == 0) matchesStatus = true;
            else if (currentFilterSelection == 1 && qty > 10) matchesStatus = true;
            else if (currentFilterSelection == 2 && qty > 0 && qty <= 10) matchesStatus = true;
            else if (currentFilterSelection == 3 && qty <= 0) matchesStatus = true;


            boolean matchesCategory = currentCategorySelection.equals("All Categories") ||
                    medicine.getCategoryName().equalsIgnoreCase(currentCategorySelection);

            if (matchesSearch && matchesStatus && matchesCategory) {
                filteredList.add(medicine);
            }
        }

        if (filteredList.isEmpty()) {
            binding.rvManageInventory.setVisibility(View.GONE);
            binding.layoutInventoryEmpty.setVisibility(View.VISIBLE);
            binding.cardPagination.setVisibility(View.GONE);
            binding.tvInventoryCount.setText("Showing 0 items");
            return;
        } else {
            binding.rvManageInventory.setVisibility(View.VISIBLE);
            binding.layoutInventoryEmpty.setVisibility(View.GONE);
            binding.cardPagination.setVisibility(View.VISIBLE);
        }

        totalPages = (int) Math.ceil((double) filteredList.size() / itemsPerPage);

        if (currentPage > totalPages) currentPage = totalPages;


        int startIndex = (currentPage - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, filteredList.size());

        List<Medicine> pageList = new ArrayList<>(filteredList.subList(startIndex, endIndex));

        adapter.setMedicineList(pageList);
        binding.tvInventoryCount.setText("Total Matches: " + filteredList.size());
        binding.tvPageInfo.setText("Page " + currentPage + " of " + totalPages);

        binding.btnPagePrev.setEnabled(currentPage > 1);
        binding.btnPageNext.setEnabled(currentPage < totalPages);

        binding.btnPagePrev.setAlpha(currentPage > 1 ? 1.0f : 0.3f);
        binding.btnPageNext.setAlpha(currentPage < totalPages ? 1.0f : 0.3f);
    }


    @Override
    public void onEditClick(Medicine medicine) {
        Bundle bundle = new Bundle();
        bundle.putString("MEDICINE_ID_TO_EDIT", medicine.getId());

        AddMedicineFragment editFragment = new AddMedicineFragment();
        editFragment.setArguments(bundle);

        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.fragment_container, editFragment)
                .addToBackStack(null)
                .commit();
    }

    @Override
    public void onDeleteClick(Medicine medicine) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete " + medicine.getName() + "?")
                .setMessage("This will remove the medicine from your inventory and the public store. This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> performSoftDelete(medicine))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performSoftDelete(Medicine medicine) {
        long timestamp = System.currentTimeMillis();

        db.collection("medicines").document(medicine.getId())
                .update("deleted", true, "lastUpdated", timestamp)
                .addOnSuccessListener(aVoid -> {

                    executorService.execute(() -> {
                        medicine.setDeleted(true);
                        medicine.setLastUpdated(timestamp);
                        medicineDao.updateMedicine(medicine);

                        loadInventoryData();
                    });

                    Toast.makeText(requireContext(), medicine.getName() + " deleted.", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> Toast.makeText(requireContext(), "Error deleting item.", Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onResume() {
        super.onResume();

        loadInventoryData();

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