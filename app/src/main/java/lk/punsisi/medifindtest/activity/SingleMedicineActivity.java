package lk.punsisi.medifindtest.activity;

import android.content.Context;
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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lk.punsisi.medifindtest.R;
import lk.punsisi.medifindtest.helper.CartHelper;
import lk.punsisi.medifindtest.model.CartItem;
import lk.punsisi.medifindtest.model.Medicine;
import lk.punsisi.medifindtest.room.AppDatabase;

public class SingleMedicineActivity extends AppCompatActivity {

    // UI Variables
    private ImageView ivMedicine;
    private TextView tvBadge, tvCategoryName, tvTitle, tvDosage, tvPrice, tvDescription, tvPharmacyName;
    private TextView tvAvailableQty, tvSalesCount, tvSelectedQuantity;
    private ImageButton btnMinus, btnPlus;
    private MaterialButton btnAddToCart;
    private FloatingActionButton fabBack;
    private RecyclerView rvRelatedProducts;

    // Data Variables
    private String medicineId;
    private Medicine currentMedicine;
    private int selectedQuantity = 1; // Default to 1
    private int maxAvailableStock = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_single_medicine);

        // 1. Initialize UI Elements
        initViews();

        // 2. Setup Back Button
        fabBack.setOnClickListener(v -> finish());

        // 3. Get the ID passed from the Intent!
        medicineId = getIntent().getStringExtra("MEDICINE_ID");

        if (medicineId != null) {
            loadMedicineData();
        } else {
            Toast.makeText(this, "Error loading product", Toast.LENGTH_SHORT).show();
            finish();
        }

        // 4. Setup Quantity Buttons
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


        // Cart bar UI
        btnMinus = findViewById(R.id.btn_minus);
        tvSelectedQuantity = findViewById(R.id.tv_quantity);
        btnPlus = findViewById(R.id.btn_plus);
        btnAddToCart = findViewById(R.id.btn_add_to_cart_main);

        rvRelatedProducts = findViewById(R.id.rv_related_products);
        // Make it scroll horizontally!
        rvRelatedProducts.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
    }

    // ==========================================
    // --- LOAD DATA FROM DATABASE ---
    // ==========================================
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
        // Set basic text
        tvCategoryName.setText(currentMedicine.getCategoryName());
        tvTitle.setText(currentMedicine.getName());
        tvPrice.setText("Rs. " + String.format("%.2f", currentMedicine.getPrice()));

        // If your model has these, set them. Otherwise, comment them out!
        if (currentMedicine.getDosage() != null) tvDosage.setText(currentMedicine.getDosage());

        // Set Image
        Glide.with(this)
                .load(currentMedicine.getImageUrl())
                .placeholder(R.drawable.baseline_local_pharmacy_24)
                .into(ivMedicine);

        // Safely get stock for our logic
        maxAvailableStock = currentMedicine.getQuantity();
        tvAvailableQty.setText(String.valueOf(maxAvailableStock));
        tvDescription.setText(currentMedicine.getDescription());
        tvPharmacyName.setText(currentMedicine.getPharmacyName());


        // Simulated sales count (You can replace this with a real DB field later!)
        tvSalesCount.setText( String.valueOf(currentMedicine.getSalesCount()));

        // Dynamic Badge Color
        String status = currentMedicine.getStatus();
        tvBadge.setText(status != null ? status : "Unknown");
        if ("In Stock".equalsIgnoreCase(status)) {
            tvBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#4CAF50")));
        } else if("Out of Stock".equalsIgnoreCase(status)) {
            tvBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#F44336")));
        } else {
            tvBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FFC107")));
        }

        if (currentMedicine.getCategoryId() != null) {
            loadRelatedProducts(currentMedicine.getCategoryId(), currentMedicine.getId());
        }
    }

    // ==========================================
    // --- QUANTITY CART LOGIC ---
    // ==========================================
    private void setupQuantityLogic() {

        btnMinus.setOnClickListener(v -> {
            if (selectedQuantity > 1) {
                selectedQuantity--;
                tvSelectedQuantity.setText(String.valueOf(selectedQuantity));
            }
        });

        btnPlus.setOnClickListener(v -> {
            // Prevent user from adding more than what we actually have in stock!
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

            // Just use the helper! We pass the custom quantity they selected.
            CartHelper.addMedicineToCart(SingleMedicineActivity.this, currentMedicine, selectedQuantity);
            Toast.makeText(this, "item added to cart in single page successfully", Toast.LENGTH_SHORT).show();

        });
    }

    // ==========================================
    // --- LOAD RELATED PRODUCTS ---
    // ==========================================
    private void loadRelatedProducts(String categoryId, String currentMedicineId) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(this);

            // 1. Get ALL medicines in this category
            java.util.List<Medicine> allCategoryMedicines = db.medicineDao().getMedicinesByCategory(categoryId);

            // 2. Filter out the one we are currently looking at
            java.util.List<Medicine> finalRelatedList = new java.util.ArrayList<>();
            for (Medicine medicine : allCategoryMedicines) {
                if (!medicine.getId().equals(currentMedicineId)) {
                    finalRelatedList.add(medicine);
                }
            }

            // 3. Send them to the UI
            runOnUiThread(() -> {
                // Pass FALSE so it uses your beautiful small Home Screen cards!
                lk.punsisi.medifindtest.adapter.MedicineAdapter adapter =
                        new lk.punsisi.medifindtest.adapter.MedicineAdapter(SingleMedicineActivity.this, finalRelatedList, false);

                rvRelatedProducts.setAdapter(adapter);
            });
        });
    }


}