package lk.punsisi.medifindtest.fragment;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import lk.punsisi.medifindtest.R;
import lk.punsisi.medifindtest.adapter.ManageOrdersAdapter;
import lk.punsisi.medifindtest.databinding.FragmentManageOrdersBinding;
import lk.punsisi.medifindtest.model.Order;

public class ManageOrdersFragment extends Fragment implements ManageOrdersAdapter.OnOrderClickListener {

    private FragmentManageOrdersBinding binding;
    private FirebaseFirestore db;
    ListenerRegistration ordersListener;
    private String currentPharmacyId;

    private ManageOrdersAdapter adapter;
    private List<Order> fullOrderList = new ArrayList<>();
    private List<Order> filteredList = new ArrayList<>();

    // Master Engine Variables
    private String currentSearchQuery = "";
    // Filter Indexes
    private int filterStatusIndex = 0;   // All, Pending, Processing, Ready..., Completed, Cancelled
    private int filterDateIndex = 0;     // All Time, Today, This Week, This Month
    private int filterTypeIndex = 0;     // All, Prescription, Cart
    private int filterDeliveryIndex = 0; // All, Pickup, COD, Online

    // Sort Index
    private int sortOptionIndex = 0;

    private int currentPage = 1;
    private final int itemsPerPage = 10;
    private int totalPages = 1;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentManageOrdersBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            currentPharmacyId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }

        setupRecyclerView();
        setupListeners();
        listenForLiveOrders();

        // 4. Back Button Press Override
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {

                int backStackCount = requireActivity().getSupportFragmentManager().getBackStackEntryCount();

                if (backStackCount > 0) {
                    // 1. It was opened from the Home Dashboard Card -> Slide back normally
                    requireActivity().getSupportFragmentManager().popBackStack();
                } else {
                    // 2. It was opened from the Side Nav -> Force the Bottom Nav to go Home!
                    BottomNavigationView bottomNav = requireActivity().findViewById(R.id.bottom_navigation_view);
                    if (bottomNav != null) {
                        bottomNav.setSelectedItemId(R.id.bottom_nav_home);
                    } else {
                        // Failsafe: Just close it
                        setEnabled(false);
                        requireActivity().getOnBackPressedDispatcher().onBackPressed();
                    }
                }
            }
        });
    }

    private void setupRecyclerView() {
        adapter = new ManageOrdersAdapter(requireContext(), this);
        binding.rvManageOrders.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvManageOrders.setAdapter(adapter);
    }

    private void listenForLiveOrders() {
        if (currentPharmacyId == null) return;

        ordersListener = db.collection("orders")
                .whereEqualTo("pharmacyId", currentPharmacyId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (binding == null) return;
                    if (error != null) {
                        Toast.makeText(requireContext(), "Error loading orders", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (value != null) {
                        fullOrderList.clear();
                        for (QueryDocumentSnapshot doc : value) {
                            Order order = doc.toObject(Order.class);
                            fullOrderList.add(order);
                        }
                        applyFiltersAndPagination();
                    }
                });
    }

    private void setupListeners() {
        binding.etOrderSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s.toString().toLowerCase().trim();
                currentPage = 1;
                applyFiltersAndPagination();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        // 👉 OPEN THE NEW ADVANCED FILTER DIALOG
        binding.btnFilterStatus.setOnClickListener(v -> showAdvancedFilterDialog());

        // 👉 OPEN THE NEW SORT DIALOG
        binding.btnSortDate.setOnClickListener(v -> showSortDialog());

        binding.btnPageNext.setOnClickListener(v -> {
            if (currentPage < totalPages) {
                currentPage++;
                applyFiltersAndPagination();
                binding.rvManageOrders.scrollToPosition(0);
            }
        });

        binding.btnPagePrev.setOnClickListener(v -> {
            if (currentPage > 1) {
                currentPage--;
                applyFiltersAndPagination();
                binding.rvManageOrders.scrollToPosition(0);
            }
        });
    }

    private void showSortDialog() {
        String[] sortOptions = {
                "Date: Newest First",
                "Date: Oldest First",
                "Price: Highest to Lowest",
                "Price: Lowest to Highest"
        };

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Sort Orders By")
                .setSingleChoiceItems(sortOptions, sortOptionIndex, (dialog, which) -> {
                    sortOptionIndex = which;
                    currentPage = 1;
                    applyFiltersAndPagination();
                    dialog.dismiss();
                })
                .show();
    }

    private void showAdvancedFilterDialog() {
        // Build the UI programmatically so we don't need a new XML file
        android.widget.LinearLayout layout = new android.widget.LinearLayout(requireContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(64, 32, 64, 0);

        // 1. Status Spinner
        TextView tvStatus = new TextView(requireContext());
        tvStatus.setText("Order Status");
        tvStatus.setTypeface(null, android.graphics.Typeface.BOLD);
        android.widget.Spinner spStatus = new android.widget.Spinner(requireContext());
        String[] arrStatus = {"All Statuses", "Pending", "Processing", "Ready to Pick", "Ready for Delivery", "On the Way", "Completed", "Cancelled"};
        spStatus.setAdapter(new android.widget.ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, arrStatus));
        spStatus.setSelection(filterStatusIndex);

        // 2. Date Spinner
        TextView tvDate = new TextView(requireContext());
        tvDate.setText("Timeframe");
        tvDate.setPadding(0, 32, 0, 0);
        tvDate.setTypeface(null, android.graphics.Typeface.BOLD);
        android.widget.Spinner spDate = new android.widget.Spinner(requireContext());
        String[] arrDate = {"All Time", "Today", "This Week", "This Month"};
        spDate.setAdapter(new android.widget.ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, arrDate));
        spDate.setSelection(filterDateIndex);

        // 3. Type Spinner
        TextView tvType = new TextView(requireContext());
        tvType.setText("Order Type");
        tvType.setPadding(0, 32, 0, 0);
        tvType.setTypeface(null, android.graphics.Typeface.BOLD);
        android.widget.Spinner spType = new android.widget.Spinner(requireContext());
        String[] arrType = {"All Types", "Prescription Uploads", "Cart Orders"};
        spType.setAdapter(new android.widget.ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, arrType));
        spType.setSelection(filterTypeIndex);

        // 4. Delivery Method Spinner
        TextView tvDelivery = new TextView(requireContext());
        tvDelivery.setText("Delivery Method");
        tvDelivery.setPadding(0, 32, 0, 0);
        tvDelivery.setTypeface(null, android.graphics.Typeface.BOLD);
        android.widget.Spinner spDelivery = new android.widget.Spinner(requireContext());
        String[] arrDelivery = {"All Methods", "Pickup", "Cash on Delivery", "Online Payment"};
        spDelivery.setAdapter(new android.widget.ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, arrDelivery));
        spDelivery.setSelection(filterDeliveryIndex);

        layout.addView(tvStatus);
        layout.addView(spStatus);
        layout.addView(tvDate);
        layout.addView(spDate);
        layout.addView(tvType);
        layout.addView(spType);
        layout.addView(tvDelivery);
        layout.addView(spDelivery);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Advanced Filters")
                .setView(layout)
                .setPositiveButton("Apply Filters", (dialog, which) -> {
                    filterStatusIndex = spStatus.getSelectedItemPosition();
                    filterDateIndex = spDate.getSelectedItemPosition();
                    filterTypeIndex = spType.getSelectedItemPosition();
                    filterDeliveryIndex = spDelivery.getSelectedItemPosition();
                    currentPage = 1;
                    applyFiltersAndPagination();
                })
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Clear All", (dialog, which) -> {
                    filterStatusIndex = 0;
                    filterDateIndex = 0;
                    filterTypeIndex = 0;
                    filterDeliveryIndex = 0;
                    currentPage = 1;
                    applyFiltersAndPagination();
                })
                .show();
    }

    private void applyFiltersAndPagination() {
        filteredList.clear();

        for (Order order : fullOrderList) {
            boolean matchesSearch = order.getOrderId().toLowerCase().contains(currentSearchQuery);

            // 1. Status Check
            boolean matchesStatus = false;
            String stat = order.getStatus() != null ? order.getStatus() : "";
            if (filterStatusIndex == 0) matchesStatus = true;
            else if (filterStatusIndex == 1 && stat.equalsIgnoreCase("Pending")) matchesStatus = true;
            else if (filterStatusIndex == 2 && stat.equalsIgnoreCase("Processing")) matchesStatus = true;
            else if (filterStatusIndex == 3 && stat.equalsIgnoreCase("Ready to Pick")) matchesStatus = true;
            else if (filterStatusIndex == 4 && stat.equalsIgnoreCase("Ready for Delivery")) matchesStatus = true;
            else if (filterStatusIndex == 5 && stat.equalsIgnoreCase("On the Way")) matchesStatus = true;
            else if (filterStatusIndex == 6 && stat.equalsIgnoreCase("Completed")) matchesStatus = true;
            else if (filterStatusIndex == 7 && stat.equalsIgnoreCase("Cancelled")) matchesStatus = true;

            // 2. Date Check
            boolean matchesDate = isDateInRange(order.getTimestamp(), filterDateIndex);

            // 3. Type Check
            boolean hasItems = order.getItems() != null && !order.getItems().isEmpty();
            boolean matchesType = false;
            if (filterTypeIndex == 0) matchesType = true;
            else if (filterTypeIndex == 1 && !hasItems) matchesType = true; // Image Only
            else if (filterTypeIndex == 2 && hasItems) matchesType = true;  // Cart Items

            // 4. Delivery Method Check
            boolean matchesDelivery = false;
            String delMethod = order.getDeliveryMethod() != null ? order.getDeliveryMethod() : "";
            if (filterDeliveryIndex == 0) matchesDelivery = true;
            else if (filterDeliveryIndex == 1 && delMethod.equalsIgnoreCase("Pickup")) matchesDelivery = true;
            else if (filterDeliveryIndex == 2 && delMethod.equalsIgnoreCase("COD")) matchesDelivery = true;
            else if (filterDeliveryIndex == 3 && delMethod.equalsIgnoreCase("Online")) matchesDelivery = true;

            // Only add if it passes ALL filters
            if (matchesSearch && matchesStatus && matchesDate && matchesType && matchesDelivery) {
                filteredList.add(order);
            }
        }

        // 👉 THE SORTING ENGINE
        java.util.Collections.sort(filteredList, (o1, o2) -> {
            if (sortOptionIndex == 0 || sortOptionIndex == 1) { // Sort by Date
                java.util.Date d1 = o1.getTimestamp() != null ? o1.getTimestamp() : new java.util.Date(0);
                java.util.Date d2 = o2.getTimestamp() != null ? o2.getTimestamp() : new java.util.Date(0);
                return sortOptionIndex == 0 ? d2.compareTo(d1) : d1.compareTo(d2);
            } else { // Sort by Price
                double p1 = o1.getGrandTotal();
                double p2 = o2.getGrandTotal();
                return sortOptionIndex == 2 ? Double.compare(p2, p1) : Double.compare(p1, p2);
            }
        });

        // Update UI
        if (filteredList.isEmpty()) {
            binding.rvManageOrders.setVisibility(View.GONE);
            binding.layoutOrdersEmpty.setVisibility(View.VISIBLE);
            binding.cardPagination.setVisibility(View.GONE);
            binding.tvOrderCount.setText("0 Orders Found");
            return;
        } else {
            binding.rvManageOrders.setVisibility(View.VISIBLE);
            binding.layoutOrdersEmpty.setVisibility(View.GONE);
            binding.cardPagination.setVisibility(View.VISIBLE);
        }

        totalPages = (int) Math.ceil((double) filteredList.size() / itemsPerPage);
        if (currentPage > totalPages) currentPage = totalPages;

        int startIndex = (currentPage - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, filteredList.size());
        List<Order> pageList = new ArrayList<>(filteredList.subList(startIndex, endIndex));

        adapter.setOrderList(pageList);
        binding.tvOrderCount.setText("Total Orders: " + filteredList.size());
        binding.tvPageInfo.setText("Page " + currentPage + " of " + totalPages);

        binding.btnPagePrev.setEnabled(currentPage > 1);
        binding.btnPageNext.setEnabled(currentPage < totalPages);
        binding.btnPagePrev.setAlpha(currentPage > 1 ? 1.0f : 0.3f);
        binding.btnPageNext.setAlpha(currentPage < totalPages ? 1.0f : 0.3f);
    }

    // Helper Method for Date Filtering
    private boolean isDateInRange(java.util.Date orderDate, int dateFilterType) {
        if (orderDate == null) return false;
        if (dateFilterType == 0) return true; // All Time

        Calendar currentCal = Calendar.getInstance();
        Calendar orderCal = Calendar.getInstance();
        orderCal.setTime(orderDate);

        if (dateFilterType == 1) { // Today
            return currentCal.get(Calendar.YEAR) == orderCal.get(Calendar.YEAR) &&
                    currentCal.get(Calendar.DAY_OF_YEAR) == orderCal.get(Calendar.DAY_OF_YEAR);
        } else if (dateFilterType == 2) { // This Week
            return currentCal.get(Calendar.YEAR) == orderCal.get(Calendar.YEAR) &&
                    currentCal.get(Calendar.WEEK_OF_YEAR) == orderCal.get(Calendar.WEEK_OF_YEAR);
        } else if (dateFilterType == 3) { // This Month
            return currentCal.get(Calendar.YEAR) == orderCal.get(Calendar.YEAR) &&
                    currentCal.get(Calendar.MONTH) == orderCal.get(Calendar.MONTH);
        }
        return true;
    }

    // ==========================================
    // 4. BOTTOM SHEET & ACTIONS
    // ==========================================
    @Override
    public void onOrderClick(Order order) {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(requireContext());
        View sheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_manage_order, null);
        bottomSheetDialog.setContentView(sheetView);

        TextView tvTitle = sheetView.findViewById(R.id.tv_sheet_order_id);
        tvTitle.setText("Manage: " + order.getOrderId());

        MaterialButtonToggleGroup toggleGroup = sheetView.findViewById(R.id.toggle_status_group);
        MaterialButton btnViewPrescription = sheetView.findViewById(R.id.btn_view_prescription);
        MaterialButton btnSaveStatus = sheetView.findViewById(R.id.btn_save_status);
        MaterialButton btnCancelOrder = sheetView.findViewById(R.id.btn_cancel_order);

        View layoutPriceInput = sheetView.findViewById(R.id.layout_price_input);
        com.google.android.material.textfield.TextInputEditText etPrice = sheetView.findViewById(R.id.et_order_total_price);
        android.widget.LinearLayout layoutItemsContainer = sheetView.findViewById(R.id.layout_order_items_container);

        // ==========================================
        // 👉 1. PRESCRIPTION BUTTON LOGIC
        // ==========================================
        if (order.getPrescriptionUrl() != null && !order.getPrescriptionUrl().isEmpty()) {
            btnViewPrescription.setVisibility(View.VISIBLE);
            btnViewPrescription.setOnClickListener(v -> showPrescriptionImageDialog(order.getPrescriptionUrl()));
        } else {
            btnViewPrescription.setVisibility(View.GONE);
        }

        // ==========================================
        // 👉 2. ITEMS LIST & PRICE INPUT LOGIC
        // ==========================================
        boolean hasItems = order.getItems() != null && !order.getItems().isEmpty();

        if (hasItems) {
            // SCENARIO A: Standard Cart Order (Hide price input, show items)
            layoutPriceInput.setVisibility(View.GONE);
            layoutItemsContainer.setVisibility(View.VISIBLE);
            layoutItemsContainer.removeAllViews();

            TextView title = new TextView(requireContext());
            title.setText("Items to Pack:");
            title.setTextColor(Color.DKGRAY);
            title.setTextSize(14f);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setPadding(0, 0, 0, 8);
            layoutItemsContainer.addView(title);

            for (java.util.Map<String, Object> item : order.getItems()) {
                String name = (String) item.get("name");
                Long qtyLong = (Long) item.get("quantity");
                String qty = qtyLong != null ? String.valueOf(qtyLong) : "1";

                TextView tvItem = new TextView(requireContext());
                tvItem.setText("• " + qty + "x  " + name);
                tvItem.setTextColor(Color.BLACK);
                tvItem.setTextSize(16f);
                tvItem.setPadding(0, 4, 0, 4);
                layoutItemsContainer.addView(tvItem);
            }
        } else {
            // SCENARIO B: Pure Prescription Upload (Show price input, hide items)
            layoutItemsContainer.setVisibility(View.GONE);
            layoutPriceInput.setVisibility(View.VISIBLE);
            if (order.getGrandTotal() > 0) {
                etPrice.setText(String.valueOf(order.getGrandTotal()));
            }
        }

        // ==========================================
        // 👉 3. STATUS BUTTON VISIBILITY
        // ==========================================
        String deliveryMethod = order.getDeliveryMethod() != null ? order.getDeliveryMethod() : "Pickup";
        if (deliveryMethod.equalsIgnoreCase("Pickup")) {
            sheetView.findViewById(R.id.btn_status_ready_delivery).setVisibility(View.GONE);
            sheetView.findViewById(R.id.btn_status_on_way).setVisibility(View.GONE);
        } else {
            sheetView.findViewById(R.id.btn_status_ready_pickup).setVisibility(View.GONE);
        }

        String currentStatus = order.getStatus() != null ? order.getStatus() : "Pending";

        // Only enable price input if it's "Processing" AND there are no cart items
        etPrice.setEnabled(currentStatus.equalsIgnoreCase("Processing") && !hasItems);

        if (currentStatus.equalsIgnoreCase("Processing")) toggleGroup.check(R.id.btn_status_processing);
        else if (currentStatus.equalsIgnoreCase("Ready to Pick")) toggleGroup.check(R.id.btn_status_ready_pickup);
        else if (currentStatus.equalsIgnoreCase("Ready for Delivery")) toggleGroup.check(R.id.btn_status_ready_delivery);
        else if (currentStatus.equalsIgnoreCase("On the Way")) toggleGroup.check(R.id.btn_status_on_way);
        else if (currentStatus.equalsIgnoreCase("Completed")) toggleGroup.check(R.id.btn_status_completed);
        else toggleGroup.check(R.id.btn_status_pending);

        // Lock/Unlock price field dynamically
        toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btn_status_processing && !hasItems) {
                    etPrice.setEnabled(true);
                    etPrice.requestFocus();
                } else {
                    etPrice.setEnabled(false);
                }
            }
        });

        // ==========================================
        // 👉 4. SAVE & BLOCKER LOGIC
        // ==========================================
        btnSaveStatus.setOnClickListener(v -> {
            int checkedId = toggleGroup.getCheckedButtonId();
            String newStatus = "Pending";

            if (checkedId == R.id.btn_status_processing) newStatus = "Processing";
            else if (checkedId == R.id.btn_status_ready_pickup) newStatus = "Ready to Pick";
            else if (checkedId == R.id.btn_status_ready_delivery) newStatus = "Ready for Delivery";
            else if (checkedId == R.id.btn_status_on_way) newStatus = "On the Way";
            else if (checkedId == R.id.btn_status_completed) newStatus = "Completed";

            // Prevent shipping unpaid online orders
            if (deliveryMethod.equalsIgnoreCase("Online") &&
                    (newStatus.equalsIgnoreCase("On the Way") || newStatus.equalsIgnoreCase("Completed")) &&
                    !order.isPaid()) {
                Toast.makeText(requireContext(), "Cannot change! User has not paid yet.", Toast.LENGTH_LONG).show();
                return;
            }

            boolean finalIsPaidStatus = order.isPaid();
            if (newStatus.equalsIgnoreCase("Completed") &&
                    (deliveryMethod.equalsIgnoreCase("COD") || deliveryMethod.equalsIgnoreCase("Pickup"))) {
                finalIsPaidStatus = true;
            }

            // Grab new price (only if it's a pure prescription upload)
            double newPrice = order.getGrandTotal();
            if (!hasItems) {
                String priceStr = etPrice.getText().toString().trim();
                if (!priceStr.isEmpty()) {
                    try {
                        newPrice = Double.parseDouble(priceStr);
                    } catch (NumberFormatException e) {
                        Toast.makeText(requireContext(), "Please enter a valid price.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
            }

            updateOrderStatusAndPrice(order.getOrderId(), newStatus, newPrice, finalIsPaidStatus, order.getUserId());
            bottomSheetDialog.dismiss();
        });

        btnCancelOrder.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Cancel Order?")
                    .setMessage("This will permanently delete this order. Ensure the customer has been refunded or informed.")
                    .setPositiveButton("Yes, Cancel & Delete", (dialog, which) -> cancelAndDeleteOrder(order.getOrderId(), order.getUserId()))
                    .setNegativeButton("Go Back", null)
                    .show();
        });

        bottomSheetDialog.show();
    }

    private void showPrescriptionImageDialog(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            Toast.makeText(requireContext(), "No prescription image found.", Toast.LENGTH_SHORT).show();
            return;
        }

        ImageView imageView = new ImageView(requireContext());
        imageView.setAdjustViewBounds(true);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setPadding(32, 32, 32, 32);
        imageView.setMinimumHeight(800);

        Glide.with(this)
                .load(imageUrl)
                .placeholder(R.drawable.baseline_image_24)
                .error(R.drawable.baseline_receipt_long_24)
                .into(imageView);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Prescription Details")
                .setView(imageView)
                .setPositiveButton("Close", null)
                .show();
    }

    // 👉 UPDATED: Now saves BOTH status and grandTotal simultaneously
    private void updateOrderStatusAndPrice(String orderId, String newStatus, double newPrice, boolean isPaid, String customerId) {
        if (orderId == null || orderId.isEmpty()) {
            Toast.makeText(getContext(), "Error: This order has corrupted or missing data.", Toast.LENGTH_LONG).show();
            return;
        }

        db.collection("orders").document(orderId)
                .update(
                        "status", newStatus,
                        "grandTotal", newPrice,
                        "paid", isPaid // Push the payment status!
                )
                .addOnSuccessListener(aVoid -> {
                    if (getContext() != null) {
                        Toast.makeText(getContext(), "Updated to " + newStatus, Toast.LENGTH_SHORT).show();
                    }
                    sendPushNotificationToUser(customerId, "Order Update", "Your order is now: " + newStatus);
                })
                .addOnFailureListener(e -> {
                    if (getContext() != null) {
                        Toast.makeText(getContext(), "Failed to update: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void cancelAndDeleteOrder(String orderId, String customerId) {
        db.collection("orders").document(orderId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(requireContext(), "Order Cancelled and Deleted", Toast.LENGTH_SHORT).show();
                    sendPushNotificationToUser(customerId, "Order Cancelled", "Your prescription order was cancelled by the pharmacy.");
                });
    }

    private void sendPushNotificationToUser(String userId, String title, String message) {
        // TODO: FCM Push Notification integration
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

        if (ordersListener != null) {
            ordersListener.remove();
        }
        binding = null;
    }
}