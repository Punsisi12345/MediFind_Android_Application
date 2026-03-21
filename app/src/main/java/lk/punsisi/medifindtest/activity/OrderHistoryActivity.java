package lk.punsisi.medifindtest.activity;

import static java.util.Locale.getDefault;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import lk.punsisi.medifindtest.R;
import lk.punsisi.medifindtest.adapter.OrderAdapter;
import lk.punsisi.medifindtest.databinding.BottomSheetOrderDetailsBinding;
import lk.punsisi.medifindtest.model.CustomerFeedback;
import lk.punsisi.medifindtest.model.Order;

public class OrderHistoryActivity extends AppCompatActivity {

    private RecyclerView rvOrders;
    private OrderAdapter adapter;
    private List<Order> orderList;
    private ProgressBar progressBar;
    private TextView tvNoOrders;

    private BottomSheetOrderDetailsBinding sheetBinding;

    private ListenerRegistration orderListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_history);

        initViews();
        fetchOrdersFromFirebase();
    }

    private void initViews() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar_orders);
        toolbar.setNavigationOnClickListener(v -> finish());

        progressBar = findViewById(R.id.progress_bar_orders);
        tvNoOrders = findViewById(R.id.tv_no_orders);

        rvOrders = findViewById(R.id.rv_orders);
        rvOrders.setLayoutManager(new LinearLayoutManager(this));

        orderList = new ArrayList<>();

        // 👉 UPDATED: Implement both click listeners
        adapter = new OrderAdapter(this, orderList, new OrderAdapter.OnOrderClickListener() {
            @Override
            public void onOrderClick(String orderId) {
                showOrderDetailsBottomSheet(orderId);
            }

            @Override
            public void onPayClick(Order order) {
                startPayHerePayment(order);
            }

            @Override
            public void onReviewClick(Order order) {
                showReviewBottomSheet(order);
            }
        });

        rvOrders.setAdapter(adapter);
    }

    private void showReviewBottomSheet(Order order) {
        // Create the Bottom Sheet
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_add_review, null);
        dialog.setContentView(view);

        // Find the views
        android.widget.RatingBar ratingBar = view.findViewById(R.id.rating_bar);
        com.google.android.material.textfield.TextInputEditText etComment = view.findViewById(R.id.et_review_comment);
        com.google.android.material.button.MaterialButton btnSubmit = view.findViewById(R.id.btn_submit_review);

        btnSubmit.setOnClickListener(v -> {
            float rating = ratingBar.getRating();
            String comment = etComment.getText() != null ? etComment.getText().toString().trim() : "";

            if (rating == 0) {
                Toast.makeText(this, "Please select at least 1 star", Toast.LENGTH_SHORT).show();
                return;
            }

            // Show a loading state on the button
            btnSubmit.setEnabled(false);
            btnSubmit.setText("Submitting...");

            // 👉 Create the object using your beautiful new Builder!
            CustomerFeedback feedback = CustomerFeedback.builder()
                    .customerId(FirebaseAuth.getInstance().getUid())
                    .orderId(order.getOrderId())
                    .pharmacyId(order.getPharmacyId())
                    .rating(rating)
                    .comment(comment)
                    .build();

            // Save to the "customer_feedback" collection
            FirebaseFirestore.getInstance().collection("customer_feedback")
                    .add(feedback)
                    .addOnSuccessListener(documentReference -> {
                        Toast.makeText(this, "Review submitted successfully! 🌟", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();

                        // Optional: Update the order document to show a review was left
                        // so the user can't leave 10 reviews for the same order!
                        FirebaseFirestore.getInstance().collection("orders")
                                .document(order.getOrderId())
                                .update("reviewed", true);
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to submit review", Toast.LENGTH_SHORT).show();
                        btnSubmit.setEnabled(true);
                        btnSubmit.setText("Submit Review");
                    });
        });

        dialog.show();
    }

    private void startPayHerePayment(Order order) {
        if (order.getGrandTotal() <= 0) {
            Toast.makeText(this, "Invalid amount for payment.", Toast.LENGTH_SHORT).show();
            return;
        }

        android.content.Intent intent = new android.content.Intent(this, CheckoutActivity.class);
        // Pass the price
        intent.putExtra("CART_TOTAL", order.getGrandTotal());
        // Tell CheckoutActivity this is an EXISTING order payment!
        intent.putExtra("IS_EXISTING_ORDER", true);
        // Pass the Order ID so we know which document to update after payment
        intent.putExtra("EXISTING_ORDER_ID", order.getOrderId());

        startActivity(intent);
    }

    private void fetchOrdersFromFirebase() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Please log in to view orders.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // We use addSnapshotListener instead of get()
        orderListener = FirebaseFirestore.getInstance().collection("orders")
                .whereEqualTo("userId", user.getUid())
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(20)
                .addSnapshotListener((queryDocumentSnapshots, e) -> {
                    progressBar.setVisibility(View.GONE);

                    // Handle errors (like internet disconnects)
                    if (e != null) {
                        Log.e("OrderHistory", "Error loading orders live", e);
                        Toast.makeText(this, "Live updates paused. Check internet.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Process the live data
                    if (queryDocumentSnapshots != null) {
                        orderList.clear(); // Clear the old list

                        if (queryDocumentSnapshots.isEmpty()) {
                            tvNoOrders.setVisibility(View.VISIBLE);
                        } else {
                            tvNoOrders.setVisibility(View.GONE);
                            for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                                Order order = document.toObject(Order.class);
                                orderList.add(order);
                            }
                        }
                        adapter.notifyDataSetChanged(); // Tell the UI to refresh!
                    }
                });
    }


    private void showOrderDetailsBottomSheet(String orderId) {
        // 1. Inflate the Bottom Sheet layout using View Binding!
        sheetBinding = BottomSheetOrderDetailsBinding.inflate(getLayoutInflater());

        // 2. Create the Dialog and set the View
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(sheetBinding.getRoot());
        dialog.show();

        // 3. Fetch from Firebase and populate the binding!
        FirebaseFirestore.getInstance().collection("orders").document(orderId)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {

                        java.util.List<java.util.Map<String, Object>> itemsList = (java.util.List<java.util.Map<String, Object>>) document.get("items");
                        String prescriptionUrl = document.getString("prescriptionUrl");

                        // Create two simple booleans to act as our logic switches!
                        boolean hasItems = itemsList != null && !itemsList.isEmpty();
                        boolean hasPrescription = prescriptionUrl != null && !prescriptionUrl.isEmpty();

                        // ==========================================
                        // --- THE 3-WAY DISPLAY LOGIC ---
                        // ==========================================
                        if (!hasItems && hasPrescription) {
                            // TYPE 1: Five Step Upload (Only Image)
                            sheetBinding.layoutNormalOrderDetails.setVisibility(android.view.View.GONE);
                            sheetBinding.ivSheetPrescription.setVisibility(android.view.View.VISIBLE);

                        } else if (hasItems && !hasPrescription) {
                            // TYPE 2: Normal Order (Only Cart Details)
                            sheetBinding.layoutNormalOrderDetails.setVisibility(android.view.View.VISIBLE);
                            sheetBinding.ivSheetPrescription.setVisibility(android.view.View.GONE);

                        } else if (hasItems && hasPrescription) {
                            // TYPE 3: Restricted Checkout (Details + Image)
                            sheetBinding.layoutNormalOrderDetails.setVisibility(android.view.View.VISIBLE);
                            sheetBinding.ivSheetPrescription.setVisibility(android.view.View.VISIBLE);
                        }

                        // ==========================================
                        // --- DRAW THE UI BASED ON VISIBILITY ---
                        // ==========================================

                        // 1. Load Image (If it exists)
                        if (hasPrescription) {
                            com.bumptech.glide.Glide.with(this)
                                    .load(prescriptionUrl)
                                    .into(sheetBinding.ivSheetPrescription);
                        }

                        // 2. Draw Cart Details (If items exist)
                        if (hasItems) {
                            // Grand Total
                            Double total = document.getDouble("grandTotal");
                            if (total != null)
                                sheetBinding.tvSheetTotal.setText(String.format(java.util.Locale.getDefault(), "Rs. %.2f", total));

                            // Address Map
                            java.util.Map<String, String> addressMap = (java.util.Map<String, String>) document.get("deliveryAddress");
                            if (addressMap != null) {
                                String fullAddress = addressMap.get("addressLine1") + ", " +
                                        addressMap.get("homeTown") + "\n" +
                                        "Phone: " + addressMap.get("phoneNumber");
                                sheetBinding.tvSheetAddress.setText(fullAddress);
                            }

                            // Dynamically build the Items List
                            sheetBinding.layoutItemsContainer.removeAllViews();
                            for (java.util.Map<String, Object> item : itemsList) {
                                String name = (String) item.get("name");
                                Long qty = (Long) item.get("quantity");
                                Double price = (Double) item.get("price");

                                android.widget.LinearLayout row = new android.widget.LinearLayout(this);
                                row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                                row.setPadding(0, 8, 0, 8);

                                android.widget.TextView tvItemName = new android.widget.TextView(this);
                                tvItemName.setText(qty + "x  " + name);
                                tvItemName.setTextColor(android.graphics.Color.BLACK);
                                tvItemName.setTextSize(15f);
                                tvItemName.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                                android.widget.TextView tvItemPrice = new android.widget.TextView(this);
                                double rowTotal = (qty != null && price != null) ? (qty * price) : 0.0;
                                tvItemPrice.setText(String.format(java.util.Locale.getDefault(), "Rs. %.2f", rowTotal));
                                tvItemPrice.setTextColor(android.graphics.Color.DKGRAY);
                                tvItemPrice.setTextSize(15f);

                                row.addView(tvItemName);
                                row.addView(tvItemPrice);
                                sheetBinding.layoutItemsContainer.addView(row);
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load details", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Stop listening to Firebase when the user closes the screen
        if (orderListener != null) {
            orderListener.remove();
        }
    }


}