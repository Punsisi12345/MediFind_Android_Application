package lk.punsisi.medifindtest.activity;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lk.punsisi.medifindtest.R;
import lk.punsisi.medifindtest.adapter.CategoryAdapter;
import lk.punsisi.medifindtest.model.Category;
import lk.punsisi.medifindtest.room.AppDatabase;

public class AllCategoriesActivity extends AppCompatActivity {

    private RecyclerView rvAllCategories;
    private FirebaseFirestore firebaseFirestore;
    private CategoryAdapter adapter;

    // --- PAGINATION VARIABLES ---
    private List<Category> fullCategoryList = new ArrayList<>();
    private int currentPage = 1;
    private final int ITEMS_PER_PAGE = 6;

    private MaterialButton btnPrev, btnNext;
    private TextView tvPageIndicator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_all_categories);

        // 1. Setup Toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar_all_categories);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // 2. Setup RecyclerView
        rvAllCategories = findViewById(R.id.rv_all_categories);
        rvAllCategories.setLayoutManager(new GridLayoutManager(this, 2));

        // 3. Setup Pagination UI Components
        btnPrev = findViewById(R.id.btn_page_prev);
        btnNext = findViewById(R.id.btn_page_next);
        tvPageIndicator = findViewById(R.id.tv_page_info);

        // 4. Handle Previous Button Click
        btnPrev.setOnClickListener(v -> {
            if (currentPage > 1) {
                currentPage--;
                updatePaginationUI();
            }
        });

        // 5. Handle Next Button Click
        btnNext.setOnClickListener(v -> {
            int totalPages = (int) Math.ceil((double) fullCategoryList.size() / ITEMS_PER_PAGE);
            if (currentPage < totalPages) {
                currentPage++;
                updatePaginationUI();
            }
        });

        // 6. Initialize Firebase & Data
        firebaseFirestore = FirebaseFirestore.getInstance();
        loadCategoriesFromRoom();
        syncCategoriesWithFirebase();
    }

    // ==========================================
    // --- 1. FAST LOCAL LOAD ---
    // ==========================================
    private void loadCategoriesFromRoom() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(this);
            List<Category> allCategoriesList = db.categoryDao().getAllCategories();

            runOnUiThread(() -> {
                // Save the full list of data globally
                fullCategoryList = allCategoriesList;

                // If the user just opened the screen, reset to page 1
                if (fullCategoryList.size() > 0 && currentPage == 0) {
                    currentPage = 1;
                }

                // Slice the data and update the screen!
                updatePaginationUI();
            });
        });
    }

    // ==========================================
    // --- 2. PAGINATION SLICER MAGIC ---
    // ==========================================
    private void updatePaginationUI() {
        if (fullCategoryList == null || fullCategoryList.isEmpty()) {
            tvPageIndicator.setText("Page 1 of 1");
            btnPrev.setEnabled(false);
            btnNext.setEnabled(false);
            return;
        }

        // Calculate total pages
        int totalItems = fullCategoryList.size();
        int totalPages = (int) Math.ceil((double) totalItems / ITEMS_PER_PAGE);

        // Safety check to ensure we don't go out of bounds
        if (currentPage > totalPages) currentPage = totalPages;
        if (currentPage < 1) currentPage = 1;

        // Figure out exactly which 6 items to grab from the main list
        int startIndex = (currentPage - 1) * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, totalItems);

        // Slice the list!
        List<Category> pageItems = fullCategoryList.subList(startIndex, endIndex);

        // Give ONLY those 6 items to the adapter
        adapter = new CategoryAdapter(AllCategoriesActivity.this, pageItems, true);
        rvAllCategories.setAdapter(adapter);

        // Update the Bottom Bar Text and Buttons
        tvPageIndicator.setText("Page " + currentPage + " of " + totalPages);
        btnPrev.setEnabled(currentPage > 1);  // Disable "Prev" if on Page 1
        btnNext.setEnabled(currentPage < totalPages); // Disable "Next" if on Last Page
    }

    // ==========================================
    // --- 3. FIREBASE BACKGROUND SYNC ---
    // ==========================================
    private void syncCategoriesWithFirebase() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(this);
            long latestTimestamp = db.categoryDao().getLatestTimestamp();

            runOnUiThread(() -> {
                firebaseFirestore.collection("categories")
                        .whereGreaterThan("lastUpdated", latestTimestamp)
                        .get()
                        .addOnSuccessListener(queryDocumentSnapshots -> {
                            if (!queryDocumentSnapshots.isEmpty()) {
                                List<Category> newCategories = queryDocumentSnapshots.toObjects(Category.class);
                                executor.execute(() -> {
                                    db.categoryDao().insertCategories(newCategories);
                                    loadCategoriesFromRoom(); // This will auto-refresh the current page!
                                });
                            }
                        });
            });
        });
    }
}