package lk.punsisi.medifindtest.activity;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lk.punsisi.medifindtest.R;
import lk.punsisi.medifindtest.adapter.MedicineAdapter;
import lk.punsisi.medifindtest.helper.CartHelper;
import lk.punsisi.medifindtest.model.CartItem;
import lk.punsisi.medifindtest.model.Medicine;
import lk.punsisi.medifindtest.room.AppDatabase;

public class SingleMedicineActivity extends AppCompatActivity {

    private ImageView ivMedicine;
    private TextView tvBadge, tvCategoryName, tvTitle, tvDosage, tvPrice, tvDescription, tvPharmacyName;
    private TextView tvAvailableQty, tvSalesCount, tvSelectedQuantity;
    private ImageButton btnMinus, btnPlus;
    private MaterialButton btnAddToCart;
    private FloatingActionButton fabBack;
    private RecyclerView rvRelatedProducts;


    private String medicineId;
    private Medicine currentMedicine;
    private int selectedQuantity = 1;
    private int maxAvailableStock = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_single_medicine);

        initViews();

        fabBack.setOnClickListener(v -> finish());

        medicineId = getIntent().getStringExtra("MEDICINE_ID");

        if (medicineId != null) {
            loadMedicineData();
        } else {
            Toast.makeText(this, "Error loading product", Toast.LENGTH_SHORT).show();
            finish();
        }

        setupQuantityLogic();

    }

    private void initViews() {
        ivMedicine = findViewById(R.id.iv_medicine_large);
        fabBack = findViewById(R.id.fab_back);
        tvBadge = findViewById(R.id.tv_status_badge);
        tvCategoryName = findViewById(R.id.tv_category_name);
        tvTitle = findViewById(R.id.tv_medicine_title);
        tvDosage = findViewById(R.id.tv_medicine_dosage);
        tvPrice = findViewById(R.id.tv_medicine_price);
        tvAvailableQty = findViewById(R.id.tv_available_quantity);
        tvSalesCount = findViewById(R.id.tv_sales_count);
        tvDescription = findViewById(R.id.tv_medicine_description);
        tvPharmacyName = findViewById(R.id.tv_pharmacy_name);

        btnMinus = findViewById(R.id.btn_minus);
        tvSelectedQuantity = findViewById(R.id.tv_quantity);
        btnPlus = findViewById(R.id.btn_plus);
        btnAddToCart = findViewById(R.id.btn_add_to_cart_main);

        rvRelatedProducts = findViewById(R.id.rv_related_products);

        rvRelatedProducts.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        tvPharmacyName.setOnClickListener(v -> {
            if (currentMedicine != null && currentMedicine.getPharmacistId() != null) {
                Intent intent = new Intent(this, MedicinesListActivity.class);
                intent.putExtra("IS_PHARMACY_STORE", true);
                intent.putExtra("PHARMACY_ID", currentMedicine.getPharmacistId());
                intent.putExtra("CATEGORY_NAME", currentMedicine.getPharmacyName());
                startActivity(intent);
            }
        });
    }

    private void loadMedicineData() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(this);
            currentMedicine = db.medicineDao().getMedicineById(medicineId);

            runOnUiThread(() -> {
                if (currentMedicine != null) {
                    populateUI();
                }
            });
        });
    }

    private void populateUI() {

        tvCategoryName.setText(currentMedicine.getCategoryName());
        tvTitle.setText(currentMedicine.getName());
        tvPrice.setText("Rs. " + String.format("%.2f", currentMedicine.getPrice()));

        if (currentMedicine.getDosage() != null) tvDosage.setText(currentMedicine.getDosage());


        Glide.with(this)
                .load(currentMedicine.getImageUrl())
                .placeholder(R.drawable.baseline_local_pharmacy_24)
                .into(ivMedicine);

        maxAvailableStock = currentMedicine.getQuantity();
        tvAvailableQty.setText(String.valueOf(maxAvailableStock));
        tvDescription.setText(currentMedicine.getDescription());
        tvPharmacyName.setText(currentMedicine.getPharmacyName());

        tvSalesCount.setText( String.valueOf(currentMedicine.getSalesCount()));

        String status = currentMedicine.getStatus();
        tvBadge.setText(status != null ? status : "Unknown");
        if ("In Stock".equalsIgnoreCase(status)) {
            tvBadge.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4CAF50")));
        } else if("Out of Stock".equalsIgnoreCase(status)) {
            tvBadge.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#F44336")));
        } else {
            tvBadge.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FFC107")));
        }

        if (currentMedicine.getCategoryId() != null) {
            loadRelatedProducts(currentMedicine.getCategoryId(), currentMedicine.getId());
        }
    }

    private void setupQuantityLogic() {

        btnMinus.setOnClickListener(v -> {
            if (selectedQuantity > 1) {
                selectedQuantity--;
                tvSelectedQuantity.setText(String.valueOf(selectedQuantity));
            }
        });

        btnPlus.setOnClickListener(v -> {

            if (selectedQuantity < maxAvailableStock) {
                selectedQuantity++;
                tvSelectedQuantity.setText(String.valueOf(selectedQuantity));
            } else {
                Toast.makeText(this, "Maximum stock reached!", Toast.LENGTH_SHORT).show();
            }
        });

        btnAddToCart.setOnClickListener(v -> {
            if (maxAvailableStock <= 0) {
                Toast.makeText(this, "Sorry, this item is out of stock", Toast.LENGTH_SHORT).show();
                return;
            }


            CartHelper.addMedicineToCart(SingleMedicineActivity.this, currentMedicine, selectedQuantity);
            Toast.makeText(this, "item added to cart in single page successfully", Toast.LENGTH_SHORT).show();

        });
    }

    private void loadRelatedProducts(String categoryId, String currentMedicineId) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(this);


            List<Medicine> allCategoryMedicines = db.medicineDao().getMedicinesByCategory(categoryId);


            List<Medicine> finalRelatedList = new ArrayList<>();
            for (Medicine medicine : allCategoryMedicines) {
                if (!medicine.getId().equals(currentMedicineId)) {
                    finalRelatedList.add(medicine);
                }
            }


            runOnUiThread(() -> {

                MedicineAdapter adapter =
                        new MedicineAdapter(SingleMedicineActivity.this, finalRelatedList, false);

                rvRelatedProducts.setAdapter(adapter);
            });
        });
    }


}