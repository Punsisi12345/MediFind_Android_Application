package lk.punsisi.medifindtest.activity;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

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

    // UI Variables
    private RecyclerView rvMedicines;
    private RecyclerView rvCategoryChips; // New Category Chips RV
    private MedicineAdapter adapter;
    private CategoryChipAdapter chipAdapter; // New Category Adapter

    // Search & Filter UI
    private EditText etSearch;
    private ImageButton btnFilter;

    // Pagination UI
    private MaterialButton btnPrev, btnNext;
    private TextView tvPageIndicator;

    // Data Lists
    private List<Medicine> fullMedicineList = new ArrayList<>();
    private List<Medicine> filteredMedicineList = new ArrayList<>();
    private List<Category> categoryChipsList = new ArrayList<>(); // Chip Data

    // State Variables
    private int currentPage = 1;
    private final int ITEMS_PER_PAGE = 6;
    private String currentSort = "A-Z";
    private String currentSearchText = "";
    private String currentSelectedCategoryId = "ALL"; // Defaults to "All"

    private String categoryId;
    private String categoryName;

    private android.widget.LinearLayout layoutEmptyState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_medicines_list);

        // 1. Get Data from Intent
        categoryId = getIntent().getStringExtra("CATEGORY_ID");
        categoryName = getIntent().getStringExtra("CATEGORY_NAME");
        isAllMedicines = getIntent().getBooleanExtra("IS_ALL_MEDICINES", false);

        // 2. Setup Toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar_medicines);
        setSupportActionBar(toolbar);
        toolbar.setTitle(categoryName != null ? categoryName : "Medicines");
        toolbar.setNavigationOnClickListener(v -> finish());

        // 3. Setup RecyclerViews
        rvMedicines = findViewById(R.id.rv_medicines);
        rvMedicines.setLayoutManager(new GridLayoutManager(this, 2));

        rvCategoryChips = findViewById(R.id.rv_search_categories_list);

        // 4. Initialize UI Elements
        etSearch = findViewById(R.id.et_search);
        btnFilter = findViewById(R.id.btn_filter);
        btnPrev = findViewById(R.id.btn_prev_page);
        btnNext = findViewById(R.id.btn_next_page);
        tvPageIndicator = findViewById(R.id.tv_page_indicator);

        // 5. Setup Listeners
        setupSearchAndFilter();
        setupPaginationListeners();

        // 6. Setup Category Chips (ONLY if isAllMedicines is true)
        setupCategoryChipsIfRequired();

        // 7. Load Data
        loadMedicinesFromRoom();

        layoutEmptyState = findViewById(R.id.layout_empty_state);
    }

    // ==========================================
    // --- CATEGORY CHIP LOGIC ---
    // ==========================================
    private void setupCategoryChipsIfRequired() {
        if (!isAllMedicines) {
            rvCategoryChips.setVisibility(View.GONE);
            return;
        }

        // Show the recycler view and set it up
        rvCategoryChips.setVisibility(View.VISIBLE);
        rvCategoryChips.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        chipAdapter = new CategoryChipAdapter(this, categoryChipsList, category -> {
            currentSelectedCategoryId = category.getId();
            currentPage = 1; // Reset pagination
            applyFiltersAndSort(); // Re-filter medicines
        });
        rvCategoryChips.setAdapter(chipAdapter);

        // Fetch categories from Room
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(this);
            List<Category> dbCategories = db.categoryDao().getActiveCategories();

            runOnUiThread(() -> {
                updateCategoryChips(dbCategories);
            });
        });
    }

    private void updateCategoryChips(List<Category> dbCategories) {
        categoryChipsList.clear();

        // Inject "All"
        Category allCategory = new Category();
        allCategory.setId("ALL");
        allCategory.setName("All");
        categoryChipsList.add(allCategory);

        // Add actual categories
        categoryChipsList.addAll(dbCategories);
        chipAdapter.notifyDataSetChanged();
    }

    // ==========================================
    // --- DATA LOADING ---
    // ==========================================
    private void loadMedicinesFromRoom() {
        if (!isAllMedicines && categoryId == null) return;

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(this);
            List<Medicine> dbList;

            if (isAllMedicines) {
                dbList = db.medicineDao().getAllActiveMedicines();
            } else {
                dbList = db.medicineDao().getMedicinesByCategory(categoryId);
            }

            runOnUiThread(() -> {
                fullMedicineList = dbList;
                applyFiltersAndSort();
            });
        });
    }

    // ==========================================
    // --- SEARCH & SORT LISTENERS ---
    // ==========================================
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

    // ==========================================
    // --- FILTER & SORT LOGIC ---
    // ==========================================
    private void applyFiltersAndSort() {
        filteredMedicineList.clear();

        for (Medicine medicine : fullMedicineList) {

            // 1. Check Category Match (If we are on "All Medicines", verify the chip. Otherwise, always true)
            boolean matchesCategory = true;
            if (isAllMedicines) {
                matchesCategory = currentSelectedCategoryId.equals("ALL") ||
                        (medicine.getCategoryId() != null && medicine.getCategoryId().equals(currentSelectedCategoryId));
            }

            // 2. Check Search Text Match
            boolean matchesSearch = currentSearchText.isEmpty() ||
                    (medicine.getName() != null && medicine.getName().toLowerCase().contains(currentSearchText));

            // Only add if it passes BOTH filters
            if (matchesCategory && matchesSearch) {
                filteredMedicineList.add(medicine);
            }
        }

        // Sorting Logic
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

    // ==========================================
    // --- PAGINATION LOGIC ---
    // ==========================================
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

            // SHOW ANIMATION, HIDE LIST
            rvMedicines.setVisibility(View.GONE);
            layoutEmptyState.setVisibility(View.VISIBLE);

            tvPageIndicator.setText("No medicines found");
            btnPrev.setEnabled(false);
            btnNext.setEnabled(false);

            adapter = new MedicineAdapter(this, new ArrayList<>(), true);
            rvMedicines.setAdapter(adapter);
            return;
        }

        // HIDE ANIMATION, SHOW LIST
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