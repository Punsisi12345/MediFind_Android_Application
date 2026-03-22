package lk.punsisi.medifindtest.activity;

import static java.util.Locale.getDefault;

import android.content.Intent;
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

        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_add_review, null);
        dialog.setContentView(view);


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

            btnSubmit.setEnabled(false);
            btnSubmit.setText("Submitting...");

            CustomerFeedback feedback = CustomerFeedback.builder()
                    .customerId(FirebaseAuth.getInstance().getUid())
                    .orderId(order.getOrderId())
                    .pharmacyId(order.getPharmacyId())
                    .rating(rating)
                    .comment(comment)
                    .build();

            FirebaseFirestore.getInstance().collection("customer_feedback")
                    .add(feedback)
                    .addOnSuccessListener(documentReference -> {
                        Toast.makeText(this, "Review submitted successfully! 🌟", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();

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

        Intent intent = new Intent(this, CheckoutActivity.class);

        intent.putExtra("CART_TOTAL", order.getGrandTotal());
        intent.putExtra("IS_EXISTING_ORDER", true);
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

        orderListener = FirebaseFirestore.getInstance().collection("orders")
                .whereEqualTo("userId", user.getUid())
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(20)
                .addSnapshotListener((queryDocumentSnapshots, e) -> {
                    progressBar.setVisibility(View.GONE);

                    if (e != null) {
                        Log.e("OrderHistory", "Error loading orders live", e);
                        Toast.makeText(this, "Live updates paused. Check internet.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (queryDocumentSnapshots != null) {
                        orderList.clear();

                        if (queryDocumentSnapshots.isEmpty()) {
                            tvNoOrders.setVisibility(View.VISIBLE);
                        } else {
                            tvNoOrders.setVisibility(View.GONE);
                            for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                                Order order = document.toObject(Order.class);
                                orderList.add(order);
                            }
                        }
                        adapter.notifyDataSetChanged();
                    }
                });
    }


    private void showOrderDetailsBottomSheet(String orderId) {

        sheetBinding = BottomSheetOrderDetailsBinding.inflate(getLayoutInflater());

        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(sheetBinding.getRoot());
        dialog.show();

        FirebaseFirestore.getInstance().collection("orders").document(orderId)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {

                        List<java.util.Map<String, Object>> itemsList = (List<Map<String, Object>>) document.get("items");
                        String prescriptionUrl = document.getString("prescriptionUrl");

                        boolean hasItems = itemsList != null && !itemsList.isEmpty();
                        boolean hasPrescription = prescriptionUrl != null && !prescriptionUrl.isEmpty();

                        if (!hasItems && hasPrescription) {
                            sheetBinding.layoutNormalOrderDetails.setVisibility(View.GONE);
                            sheetBinding.ivSheetPrescription.setVisibility(View.VISIBLE);

                        } else if (hasItems && !hasPrescription) {
                            sheetBinding.layoutNormalOrderDetails.setVisibility(View.VISIBLE);
                            sheetBinding.ivSheetPrescription.setVisibility(View.GONE);

                        } else if (hasItems && hasPrescription) {
                            sheetBinding.layoutNormalOrderDetails.setVisibility(View.VISIBLE);
                            sheetBinding.ivSheetPrescription.setVisibility(View.VISIBLE);
                        }

                        if (hasPrescription) {
                            Glide.with(this)
                                    .load(prescriptionUrl)
                                    .into(sheetBinding.ivSheetPrescription);
                        }

                        if (hasItems) {
                            Double total = document.getDouble("grandTotal");
                            if (total != null)
                                sheetBinding.tvSheetTotal.setText(String.format(Locale.getDefault(), "Rs. %.2f", total));

                            Map<String, String> addressMap = (Map<String, String>) document.get("deliveryAddress");
                            if (addressMap != null) {
                                String fullAddress = addressMap.get("addressLine1") + ", " +
                                        addressMap.get("homeTown") + "\n" +
                                        "Phone: " + addressMap.get("phoneNumber");
                                sheetBinding.tvSheetAddress.setText(fullAddress);
                            }

                            sheetBinding.layoutItemsContainer.removeAllViews();
                            for (Map<String, Object> item : itemsList) {
                                String name = (String) item.get("name");
                                Long qty = (Long) item.get("quantity");
                                Double price = (Double) item.get("price");

                                LinearLayout row = new LinearLayout(this);
                                row.setOrientation(LinearLayout.HORIZONTAL);
                                row.setPadding(0, 8, 0, 8);

                                TextView tvItemName = new TextView(this);
                                tvItemName.setText(qty + "x  " + name);
                                tvItemName.setTextColor(Color.BLACK);
                                tvItemName.setTextSize(15f);
                                tvItemName.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                                TextView tvItemPrice = new TextView(this);
                                double rowTotal = (qty != null && price != null) ? (qty * price) : 0.0;
                                tvItemPrice.setText(String.format(Locale.getDefault(), "Rs. %.2f", rowTotal));
                                tvItemPrice.setTextColor(Color.DKGRAY);
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
        if (orderListener != null) {
            orderListener.remove();
        }
    }


}