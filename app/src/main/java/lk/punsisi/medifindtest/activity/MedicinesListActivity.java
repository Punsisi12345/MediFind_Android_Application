package lk.punsisi.medifindtest.activity;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lk.punsisi.medifindtest.R;
import lk.punsisi.medifindtest.adapter.CategoryChipAdapter;
import lk.punsisi.medifindtest.adapter.MedicineAdapter;
import lk.punsisi.medifindtest.model.Category;
import lk.punsisi.medifindtest.model.Medicine;
import lk.punsisi.medifindtest.room.AppDatabase;

public class MedicinesListActivity extends AppCompatActivity {

    private boolean isAllMedicines = false;

    private boolean isPharmacyStore = false;
    private String targetPharmacyId;

    private RecyclerView rvMedicines;
    private RecyclerView rvCategoryChips;
    private MedicineAdapter adapter;
    private CategoryChipAdapter chipAdapter;

    private EditText etSearch;
    private ImageButton btnFilter;

    private MaterialButton btnPrev, btnNext;
    private TextView tvPageIndicator;

    private MaterialCardView cardPharmacyHeader;
    private ShapeableImageView ivPharmacyLogo;
    private TextView tvHeaderName, tvHeaderAddress, tvHeaderPhone;

    private List<Medicine> fullMedicineList = new ArrayList<>();
    private List<Medicine> filteredMedicineList = new ArrayList<>();
    private List<Category> categoryChipsList = new ArrayList<>();

    private int currentPage = 1;
    private final int ITEMS_PER_PAGE = 6;
    private String currentSort = "A-Z";
    private String currentSearchText = "";
    private String currentSelectedCategoryId = "ALL";

    private String categoryId;
    private String categoryName;

    private LinearLayout layoutEmptyState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_medicines_list);

        categoryId = getIntent().getStringExtra("CATEGORY_ID");
        categoryName = getIntent().getStringExtra("CATEGORY_NAME");
        isAllMedicines = getIntent().getBooleanExtra("IS_ALL_MEDICINES", false);

        isPharmacyStore = getIntent().getBooleanExtra("IS_PHARMACY_STORE", false);
        targetPharmacyId = getIntent().getStringExtra("PHARMACY_ID");

        MaterialToolbar toolbar = findViewById(R.id.toolbar_medicines);
        setSupportActionBar(toolbar);
        toolbar.setTitle(categoryName != null ? categoryName : "Medicines");
        toolbar.setNavigationOnClickListener(v -> finish());

        rvMedicines = findViewById(R.id.rv_medicines);
        rvMedicines.setLayoutManager(new GridLayoutManager(this, 2));
        rvCategoryChips = findViewById(R.id.rv_search_categories_list);
        etSearch = findViewById(R.id.et_search);
        btnFilter = findViewById(R.id.btn_filter);

        btnPrev = findViewById(R.id.btn_page_prev);
        btnNext = findViewById(R.id.btn_page_next);
        tvPageIndicator = findViewById(R.id.tv_page_info);

        layoutEmptyState = findViewById(R.id.layout_empty_state);

        cardPharmacyHeader = findViewById(R.id.card_pharmacy_header);
        ivPharmacyLogo = findViewById(R.id.iv_pharmacy_logo);
        tvHeaderName = findViewById(R.id.tv_header_pharmacy_name);
        tvHeaderAddress = findViewById(R.id.tv_header_pharmacy_address);
        tvHeaderPhone = findViewById(R.id.tv_header_pharmacy_phone);

        setupSearchAndFilter();
        setupPaginationListeners();

        if (isPharmacyStore) {
            setupPharmacyStorefront();
            setupCategoryChips();
        } else if (isAllMedicines) {
            setupCategoryChips();
        } else {
            rvCategoryChips.setVisibility(View.GONE);
        }

        loadMedicinesFromRoom();
    }

    private void setupPharmacyStorefront() {
        if (targetPharmacyId == null) {
            Toast.makeText(this, "Pharmacy ID missing", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // hide toolbar and show pharmacy details
        AppBarLayout appBarLayout = findViewById(R.id.appBarLayout);
        appBarLayout.setVisibility(View.GONE);
        cardPharmacyHeader.setVisibility(View.VISIBLE);

        ImageButton btnPharmacyBack = findViewById(R.id.btn_pharmacy_back);
        btnPharmacyBack.setOnClickListener(v -> finish());

        FirebaseFirestore.getInstance().collection("pharmacist_requests")
                .whereEqualTo("uid", targetPharmacyId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        DocumentSnapshot doc = queryDocumentSnapshots.getDocuments().get(0);

                        String pName = doc.getString("pharmacyName");
                        String pAddress = doc.getString("pharmacyAddress");
                        String pPhone = doc.getString("phoneNumber");
                        String pLogoUrl = doc.getString("profileImage");

                        tvHeaderName.setText(pName != null ? pName : "Pharmacy Store");
                        tvHeaderAddress.setText(pAddress != null ? pAddress : "Address not available");

                        if (pPhone != null && !pPhone.isEmpty()) {
                            tvHeaderPhone.setText(pPhone);
                        } else {
                            String ownerName = doc.getString("fullName");
                            tvHeaderPhone.setText(ownerName != null ? "Proprietor: " + ownerName : "Contact not available");
                        }

                        if (pLogoUrl != null && !pLogoUrl.isEmpty()) {
                            Glide.with(this)
                                    .load(pLogoUrl)
                                    .circleCrop()
                                    .placeholder(R.drawable.baseline_local_pharmacy_24)
                                    .into(ivPharmacyLogo);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("Storefront", "Failed to load pharmacy details", e);
                });
    }

    private void setupCategoryChips() {
        rvCategoryChips.setVisibility(View.VISIBLE);
        rvCategoryChips.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        chipAdapter = new CategoryChipAdapter(this, categoryChipsList, category -> {
            currentSelectedCategoryId = category.getId();
            currentPage = 1;
            applyFiltersAndSort();
        });
        rvCategoryChips.setAdapter(chipAdapter);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(this);
            List<Category> dbCategories = db.categoryDao().getActiveCategories();
            runOnUiThread(() -> updateCategoryChips(dbCategories));
        });
    }

    private void updateCategoryChips(List<Category> dbCategories) {
        categoryChipsList.clear();
        Category allCategory = new Category();
        allCategory.setId("ALL");
        allCategory.setName("All");
        categoryChipsList.add(allCategory);
        categoryChipsList.addAll(dbCategories);
        chipAdapter.notifyDataSetChanged();
    }

    private void loadMedicinesFromRoom() {

        if (!isAllMedicines && categoryId == null && !isPharmacyStore) return;

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(this);
            List<Medicine> dbList;

            if (isPharmacyStore) {
                // Fetch ONLY medicines belonging to this specific pharmacy!
                dbList = db.medicineDao().getAllActiveMedicines();
            } else if (isAllMedicines) {
                dbList = db.medicineDao().getAllActiveMedicines();
            } else {
                dbList = db.medicineDao().getMedicinesByCategory(categoryId);
            }

            // If it's a pharmacy store, we filter the raw list immediately
            if (isPharmacyStore && targetPharmacyId != null) {
                List<Medicine> pharmacyOnlyList = new ArrayList<>();
                for (Medicine m : dbList) {
                    if (targetPharmacyId.equals(m.getPharmacistId())) {
                        pharmacyOnlyList.add(m);
                    }
                }
                dbList = pharmacyOnlyList;
            }

            final List<Medicine> finalDbList = dbList;
            runOnUiThread(() -> {
                fullMedicineList = finalDbList;
                applyFiltersAndSort();
            });
        });
    }

    private void setupSearchAndFilter() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchText = s.toString().trim().toLowerCase();
                currentPage = 1;
                applyFiltersAndSort();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnFilter.setOnClickListener(v -> {
            PopupMenu popupMenu = new PopupMenu(MedicinesListActivity.this, btnFilter);
            popupMenu.getMenu().add("Name: A-Z");
            popupMenu.getMenu().add("Price: Low to High");
            popupMenu.getMenu().add("Price: High to Low");

            popupMenu.setOnMenuItemClickListener(item -> {
                currentSort = item.getTitle().toString();
                currentPage = 1;
                applyFiltersAndSort();
                return true;
            });
            popupMenu.show();
        });
    }

    private void applyFiltersAndSort() {
        filteredMedicineList.clear();

        for (Medicine medicine : fullMedicineList) {
            boolean matchesCategory = true;
            if (isAllMedicines || isPharmacyStore) {
                matchesCategory = currentSelectedCategoryId.equals("ALL") ||
                        (medicine.getCategoryId() != null && medicine.getCategoryId().equals(currentSelectedCategoryId));
            }

            boolean matchesSearch = currentSearchText.isEmpty() ||
                    (medicine.getName() != null && medicine.getName().toLowerCase().contains(currentSearchText));

            if (matchesCategory && matchesSearch) {
                filteredMedicineList.add(medicine);
            }
        }

        Collections.sort(filteredMedicineList, (m1, m2) -> {
            if (currentSort.equals("Price: Low to High")) {
                return Double.compare(m1.getPrice(), m2.getPrice());
            } else if (currentSort.equals("Price: High to Low")) {
                return Double.compare(m2.getPrice(), m1.getPrice());
            } else {
                return m1.getName().compareToIgnoreCase(m2.getName());
            }
        });

        updatePaginationUI();
    }

    private void setupPaginationListeners() {
        btnPrev.setOnClickListener(v -> {
            if (currentPage > 1) {
                currentPage--;
                updatePaginationUI();
            }
        });

        btnNext.setOnClickListener(v -> {
            int totalPages = (int) Math.ceil((double) filteredMedicineList.size() / ITEMS_PER_PAGE);
            if (currentPage < totalPages) {
                currentPage++;
                updatePaginationUI();
            }
        });
    }

    private void updatePaginationUI() {
        if (filteredMedicineList == null || filteredMedicineList.isEmpty()) {
            rvMedicines.setVisibility(View.GONE);
            layoutEmptyState.setVisibility(View.VISIBLE);

            tvPageIndicator.setText("No medicines found");
            btnPrev.setEnabled(false);
            btnNext.setEnabled(false);

            adapter = new MedicineAdapter(this, new ArrayList<>(), true);
            rvMedicines.setAdapter(adapter);
            return;
        }

        layoutEmptyState.setVisibility(View.GONE);
        rvMedicines.setVisibility(View.VISIBLE);

        int totalItems = filteredMedicineList.size();
        int totalPages = (int) Math.ceil((double) totalItems / ITEMS_PER_PAGE);

        if (currentPage > totalPages) currentPage = totalPages;
        if (currentPage < 1) currentPage = 1;

        int startIndex = (currentPage - 1) * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, totalItems);

        List<Medicine> pagedItems = filteredMedicineList.subList(startIndex, endIndex);

        adapter = new MedicineAdapter(this, pagedItems, true);
        rvMedicines.setAdapter(adapter);

        tvPageIndicator.setText("Page " + currentPage + " of " + totalPages);
        btnPrev.setEnabled(currentPage > 1);
        btnNext.setEnabled(currentPage < totalPages);
    }
}