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

    private List<Category> fullCategoryList = new ArrayList<>();
    private int currentPage = 1;
    private final int ITEMS_PER_PAGE = 6;

    private MaterialButton btnPrev, btnNext;
    private TextView tvPageIndicator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_all_categories);

        MaterialToolbar toolbar = findViewById(R.id.toolbar_all_categories);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        rvAllCategories = findViewById(R.id.rv_all_categories);
        rvAllCategories.setLayoutManager(new GridLayoutManager(this, 2));

        btnPrev = findViewById(R.id.btn_page_prev);
        btnNext = findViewById(R.id.btn_page_next);
        tvPageIndicator = findViewById(R.id.tv_page_info);

        //pagination previous button
        btnPrev.setOnClickListener(v -> {
            if (currentPage > 1) {
                currentPage--;
                updatePaginationUI();
            }
        });

        //pagination next button
        btnNext.setOnClickListener(v -> {
            int totalPages = (int) Math.ceil((double) fullCategoryList.size() / ITEMS_PER_PAGE);
            if (currentPage < totalPages) {
                currentPage++;
                updatePaginationUI();
            }
        });

        firebaseFirestore = FirebaseFirestore.getInstance();
        loadCategoriesFromRoom();
        syncCategoriesWithFirebase();
    }

    private void loadCategoriesFromRoom() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(this);
            List<Category> allCategoriesList = db.categoryDao().getAllCategories();

            runOnUiThread(() -> {
                fullCategoryList = allCategoriesList;

                if (fullCategoryList.size() > 0 && currentPage == 0) {
                    currentPage = 1;
                }
                updatePaginationUI();
            });
        });
    }

    private void updatePaginationUI() {
        if (fullCategoryList == null || fullCategoryList.isEmpty()) {
            tvPageIndicator.setText("Page 1 of 1");
            btnPrev.setEnabled(false);
            btnNext.setEnabled(false);
            return;
        }

        int totalItems = fullCategoryList.size();
        int totalPages = (int) Math.ceil((double) totalItems / ITEMS_PER_PAGE);

        if (currentPage > totalPages) currentPage = totalPages;
        if (currentPage < 1) currentPage = 1;

        int startIndex = (currentPage - 1) * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, totalItems);

        List<Category> pageItems = fullCategoryList.subList(startIndex, endIndex);

        adapter = new CategoryAdapter(AllCategoriesActivity.this, pageItems, true);
        rvAllCategories.setAdapter(adapter);

        tvPageIndicator.setText("Page " + currentPage + " of " + totalPages);
        btnPrev.setEnabled(currentPage > 1);
        btnNext.setEnabled(currentPage < totalPages);
    }

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
                                    loadCategoriesFromRoom();
                                });
                            }
                        });
            });
        });
    }
}