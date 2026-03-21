package lk.punsisi.medifindtest.fragment;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.PopupMenu;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.AggregateSource;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lk.punsisi.medifindtest.R;
import lk.punsisi.medifindtest.activity.AllCategoriesActivity;
import lk.punsisi.medifindtest.activity.MedicinesListActivity;
import lk.punsisi.medifindtest.adapter.CategoryAdapter;
import lk.punsisi.medifindtest.adapter.CategoryChipAdapter;
import lk.punsisi.medifindtest.adapter.MedicineAdapter;
import lk.punsisi.medifindtest.databinding.FragmentHomeBinding;
import lk.punsisi.medifindtest.model.Category;
import lk.punsisi.medifindtest.model.DeliveryAddress;
import lk.punsisi.medifindtest.model.Medicine;
import lk.punsisi.medifindtest.model.Order;
import lk.punsisi.medifindtest.model.User;
import lk.punsisi.medifindtest.room.AppDatabase;
import lk.punsisi.medifindtest.room.CategoryDao;
import lk.punsisi.medifindtest.room.MedicineDao;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;

    // Existing Variables
    private RecyclerView recyclerViewCategories;
    private CategoryAdapter categoryAdapter;
    private List<Category> categoryList;
    private RecyclerView recyclerViewMedicines;
    private MedicineAdapter medicineAdapter;
    private List<Medicine> topMedicineList;

    private FirebaseFirestore db;
    private CategoryDao categoryDao;
    private MedicineDao medicineDao;
    private ExecutorService executorService;
    private FusedLocationProviderClient fusedLocationClient;

    // ==========================================
    // --- IN-LINE SEARCH VARIABLES ---
    // ==========================================
    private boolean isSearchMode = false;
    private OnBackPressedCallback searchBackCallback;

    // Category Chips
    private CategoryChipAdapter chipAdapter;
    private List<Category> searchCategoryChips = new ArrayList<>();
    private String currentSelectedCategoryId = "ALL"; // Defaults to "All"

    // Search Results
    private MedicineAdapter searchResultsAdapter;
    private List<Medicine> fullSearchList = new ArrayList<>();
    private List<Medicine> filteredSearchList = new ArrayList<>();

    private int currentPage = 1;
    private final int ITEMS_PER_PAGE = 8;
    private String currentSort = "A-Z";
    private String currentSearchText = "";


    //to prescription camera
    private ActivityResultLauncher<String> galleryLauncher;
    private AlertDialog loadingDialog;

    private String currentUserRole = "user";

    private BarChart barChart;
    private LineChart lineChart;
    private PieChart expiryChart, rxOtcChart;

    private User user;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);

        // 1. Initialize Databases and Threads
        db = FirebaseFirestore.getInstance();


        SharedPreferences prefs = requireActivity().getSharedPreferences("MediFindPrefs", Context.MODE_PRIVATE);
        currentUserRole = prefs.getString("USER_ROLE", "user");

        if (currentUserRole.equals("pharmacist") || currentUserRole.equals("admin")) {
            // PHARMACIST LOGGED IN! Instantly show the dashboard.
            binding.layoutUserDashboard.setVisibility(View.GONE);
            binding.layoutPharmacistDashboard.setVisibility(View.VISIBLE);

        } else {
            // NORMAL USER. Instantly show shopping view.
            binding.layoutUserDashboard.setVisibility(View.VISIBLE);
            binding.layoutPharmacistDashboard.setVisibility(View.GONE);
        }

        categoryDao = AppDatabase.getDatabase(getContext()).categoryDao();
        medicineDao = AppDatabase.getDatabase(getContext()).medicineDao();
        executorService = Executors.newSingleThreadExecutor();

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        // 2. Setup Existing Category List (Home Screen)
        recyclerViewCategories = binding.recyclerViewQuickCategories;
        recyclerViewCategories.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        categoryList = new ArrayList<>();
        categoryAdapter = new CategoryAdapter(getContext(), categoryList, false);
        recyclerViewCategories.setAdapter(categoryAdapter);

        // 3. Setup Existing Medicine List (Home Screen)
        recyclerViewMedicines = binding.recyclerViewTopMedicines;
        recyclerViewMedicines.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        topMedicineList = new ArrayList<>();
        medicineAdapter = new MedicineAdapter(getContext(), topMedicineList, false);
        recyclerViewMedicines.setAdapter(medicineAdapter);

        // 4. Setup the new In-Line Search Mode!
        setupInLineSearchMode();

        // 5. Load the Data Offline-First!
        loadCategoriesFromRoomAndSync();
        loadMedicinesFromRoomAndSync();

        // 6. Clicks
        binding.layoutSeeMoreCategories.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), AllCategoriesActivity.class));
        });

        binding.layoutSeeMoreMedicines.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), MedicinesListActivity.class);
            intent.putExtra("IS_ALL_MEDICINES", true);
            intent.putExtra("CATEGORY_NAME", "All Medicines");
            startActivity(intent);
        });


        // 👉 NEW: Gallery Picker Launcher
        galleryLauncher = registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        // Pass the high-quality URI instead of a compressed Bitmap!
                        findNearestPharmacies(uri);
                    }
                }
        );

        binding.myCardDiv.setOnClickListener(v -> {
            galleryLauncher.launch("image/*");
        });

        barChart = binding.topSellingChart;
//        barChartLoad();

        lineChart = binding.salesTrendChart;
//        lineChartLoad();

        expiryChart = binding.expiryPieChart;
        rxOtcChart = binding.rxOtcPieChart;

//        setupExpiryChart(expiryChart);
//        setupRxOtcChart(rxOtcChart);


        binding.addItemBtn.setOnClickListener(v -> {
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                    .replace(R.id.fragment_container, new AddMedicineFragment())
                    .addToBackStack(null)
                    .commit();
        });

        binding.manageOrdersBtn.setOnClickListener(v -> {
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                    .replace(R.id.fragment_container, new ManageOrdersFragment())
                    .addToBackStack(null)
                    .commit();
        });

        binding.inventoryManageBtn.setOnClickListener(v -> {
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                    .replace(R.id.fragment_container, new ManageInventoryFragment())
                    .addToBackStack(null)
                    .commit();
        });


        loadDashboardMetrics();
        loadLiveChartData();

        return binding.getRoot();
    }

    private void loadDashboardMetrics() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            // If nobody is logged in, just silently stop and do nothing!
            return;
        }

        // 1. Link your layout IDs
        TextView tvMedicineData = binding.medicineData;
        TextView tvSalesData = binding.salesData;
        TextView tvFeedbackData = binding.feedbackData;

        // 2. Get the current logged-in Pharmacist's ID

        String pharmacyId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        // ==========================================
        // 📊 METRIC 1: Total Medicines in Inventory
        // ==========================================
        db.collection("medicines")
                .whereEqualTo("pharmacistId", pharmacyId)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e("Dashboard", "Error loading medicines", error);
                        tvMedicineData.setText("-"); // Show dash on error instead of blank
                        return;
                    }
                    if (value != null) {
                        // .size() safely counts the documents even on a bad network!
                        tvMedicineData.setText(String.valueOf(value.size()));
                    }
                });

        // ==========================================
        // 📊 METRIC 2: Total Completed Sales (Orders)
        // ==========================================
        db.collection("orders")
                .whereEqualTo("pharmacyId", pharmacyId)
                .whereEqualTo("status", "Completed")
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e("Dashboard", "Error loading orders", error);
                        tvSalesData.setText("-");
                        return;
                    }
                    if (value != null) {
                        tvSalesData.setText(String.valueOf(value.size()));
                    }
                });

        // ==========================================
        // 📊 METRIC 3: Total Customer Feedback
        // ==========================================
        db.collection("customer_feedback")
                .whereEqualTo("pharmacyId", pharmacyId)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e("Dashboard", "Error loading feedback", error);
                        tvFeedbackData.setText("-");
                        return;
                    }
                    if (value != null) {
                        tvFeedbackData.setText(String.valueOf(value.size()));
                    }
                });
    }

    private void loadLiveChartData() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        String pharmacyId = currentUser.getUid();

        db.collection("orders")
                .whereEqualTo("pharmacyId", pharmacyId)
                .whereEqualTo("status", "Completed")
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null || binding == null) {
                        Log.e("Charts", "Error loading chart data", error);
                        return;
                    }

                    // 1. Data Buckets
                    float[] monthlyRevenue = new float[12]; // 12 months
                    java.util.HashMap<String, Long> itemSales = new java.util.HashMap<>();
                    int rxCount = 0;
                    int otcCount = 0;

                    int currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);

                    // 2. Loop through all completed orders
                    for (QueryDocumentSnapshot doc : value) {
                        Order order = doc.toObject(Order.class);

                        // --- REVENUE CALCULATION (Current Year Only) ---
                        if (order.getTimestamp() != null) {
                            java.util.Calendar cal = java.util.Calendar.getInstance();
                            cal.setTime(order.getTimestamp());

                            if (cal.get(java.util.Calendar.YEAR) == currentYear) {
                                int month = cal.get(java.util.Calendar.MONTH); // 0 = Jan, 11 = Dec
                                monthlyRevenue[month] += order.getGrandTotal();
                            }
                        }

                        // --- RX vs OTC CALCULATION ---
                        boolean hasPrescription = order.getPrescriptionUrl() != null && !order.getPrescriptionUrl().isEmpty();
                        boolean hasItems = order.getItems() != null && !order.getItems().isEmpty();

                        if (hasPrescription) rxCount++;
                        if (hasItems) otcCount++;

                        // --- TOP SELLING ITEMS CALCULATION ---
                        if (hasItems) {
                            for (java.util.Map<String, Object> item : order.getItems()) {
                                String name = (String) item.get("name");
                                Long qty = (Long) item.get("quantity");
                                if (name != null && qty != null) {
                                    itemSales.put(name, itemSales.getOrDefault(name, 0L) + qty);
                                }
                            }
                        }
                    }

                    // 3. Feed the data to the charts!
                    updateMonthlyRevenueChart(monthlyRevenue);
                    updateTopSellingChart(itemSales);
                    updateRxOtcLiveChart(rxCount, otcCount);
                });
    }

    private void updateMonthlyRevenueChart(float[] monthlyRevenue) {
        ArrayList<Entry> trendData = new ArrayList<>();

        // Populate the 12 months
        for (int i = 0; i < 12; i++) {
            trendData.add(new Entry(i, monthlyRevenue[i]));
        }

        LineDataSet dataSet = new LineDataSet(trendData, "Monthly Revenue");
        dataSet.setColor(Color.parseColor("#00796B"));
        dataSet.setCircleColor(Color.parseColor("#00796B"));
        dataSet.setLineWidth(3f);
        dataSet.setCircleRadius(4f);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(Color.parseColor("#BF00796B"));
        dataSet.setDrawValues(false);

        LineData lineData = new LineData(dataSet);
        lineChart.setData(lineData);

        // Update X-Axis to show Months instead of Days!
        String[] months = new String[]{"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
        XAxis xAxis = lineChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(months));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);

        lineChart.getDescription().setEnabled(false);
        lineChart.getAxisRight().setEnabled(false);
        lineChart.animateX(1000);
        lineChart.invalidate();
    }

    private void updateTopSellingChart(java.util.HashMap<String, Long> itemSales) {
        // 1. Sort the items to find the top sellers
        List<java.util.Map.Entry<String, Long>> sortedItems = new ArrayList<>(itemSales.entrySet());
        sortedItems.sort((o1, o2) -> o2.getValue().compareTo(o1.getValue())); // Descending order

        ArrayList<BarEntry> salesData = new ArrayList<>();
        ArrayList<String> itemNames = new ArrayList<>();

        // 2. Get the Top 4 items (or fewer if they haven't sold 4 different items yet)
        int limit = Math.min(sortedItems.size(), 4);
        for (int i = 0; i < limit; i++) {
            salesData.add(new BarEntry(i, sortedItems.get(i).getValue()));
            itemNames.add(sortedItems.get(i).getKey());
        }

        BarDataSet dataSet = new BarDataSet(salesData, "Units Sold");
        dataSet.setColors(ColorTemplate.MATERIAL_COLORS);
        dataSet.setValueTextSize(10f);
        dataSet.setValueTextColor(Color.parseColor("#757575"));

        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.5f);
        barChart.setData(barData);

        // 3. Dynamically set the X-Axis labels based on actual medicine names
        XAxis xAxis = barChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(itemNames));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);

        barChart.getDescription().setEnabled(false);
        barChart.getAxisRight().setEnabled(false);
        barChart.getLegend().setEnabled(false);
        barChart.animateY(1000);
        barChart.invalidate();
    }

    private void updateRxOtcLiveChart(int rxCount, int otcCount) {
        ArrayList<PieEntry> entries = new ArrayList<>();

        // Prevent crashing if the pharmacy has exactly 0 orders
        if (rxCount == 0 && otcCount == 0) {
            entries.add(new PieEntry(100f, "No Sales"));
        } else {
            if (rxCount > 0) entries.add(new PieEntry(rxCount, "Rx"));
            if (otcCount > 0) entries.add(new PieEntry(otcCount, "OTC"));
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        int colorRx = Color.parseColor("#00796B");
        int colorOtc = Color.parseColor("#8000796B");
        dataSet.setColors(colorRx, colorOtc);

        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(12f);

        PieData data = new PieData(dataSet);
        data.setValueFormatter(new PercentFormatter(rxOtcChart));

        rxOtcChart.setData(data);
        rxOtcChart.getDescription().setEnabled(false);
        rxOtcChart.getLegend().setEnabled(false);
        rxOtcChart.setUsePercentValues(true);
        rxOtcChart.setEntryLabelColor(Color.WHITE);
        rxOtcChart.setEntryLabelTextSize(10f);
        rxOtcChart.setCenterText("Sales");
        rxOtcChart.setCenterTextSize(14f);
        rxOtcChart.setHoleRadius(40f);
        rxOtcChart.setTransparentCircleRadius(45f);
        rxOtcChart.animateY(1000);
        rxOtcChart.invalidate();
    }

    private void setupExpiryChart(PieChart chart) {
        ArrayList<PieEntry> entries = new ArrayList<>();
        entries.add(new PieEntry(85f, "Safe"));
        entries.add(new PieEntry(10f, "Soon")); // Expiring soon
        entries.add(new PieEntry(5f, "Expired"));

        PieDataSet dataSet = new PieDataSet(entries, "");

        // Custom Traffic Light Colors: Green, Yellow, Red
        int colorSafe = Color.parseColor("#4CAF50");
        int colorSoon = Color.parseColor("#FFC107");
        int colorExpired = Color.parseColor("#F44336");
        dataSet.setColors(colorSafe, colorSoon, colorExpired);

        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(12f);

        PieData data = new PieData(dataSet);
        data.setValueFormatter(new PercentFormatter(chart)); // Shows the '%' symbol
        chart.setData(data);

        // UI Styling
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false); // Hide legend to save space in small cards
        chart.setUsePercentValues(true);
        chart.setEntryLabelColor(Color.WHITE);
        chart.setEntryLabelTextSize(10f);
        chart.setCenterText("Stock"); // Text in the middle hole
        chart.setCenterTextSize(14f);
        chart.setHoleRadius(40f); // Creates the "Donut" hole look
        chart.setTransparentCircleRadius(45f);
        chart.animateY(1400); // Cool spin animation
        chart.invalidate();
    }

    private void setupRxOtcChart(PieChart chart) {
        ArrayList<PieEntry> entries = new ArrayList<>();
        entries.add(new PieEntry(60f, "Rx"));  // Prescriptions
        entries.add(new PieEntry(40f, "OTC")); // Over The Counter

        PieDataSet dataSet = new PieDataSet(entries, "");

        // Using your app's theme colors: Solid Teal and 50% Transparent Teal
        int colorRx = Color.parseColor("#00796B");
        int colorOtc = Color.parseColor("#8000796B");
        dataSet.setColors(colorRx, colorOtc);

        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(12f);

        PieData data = new PieData(dataSet);
        data.setValueFormatter(new PercentFormatter(chart));
        chart.setData(data);

        // UI Styling
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setUsePercentValues(true);
        chart.setEntryLabelColor(Color.WHITE);
        chart.setEntryLabelTextSize(10f);
        chart.setCenterText("Sales");
        chart.setCenterTextSize(14f);
        chart.setHoleRadius(40f);
        chart.setTransparentCircleRadius(45f);
        chart.animateY(1400);
        chart.invalidate();
    }

    private void barChartLoad() {

        // 1. Create your dummy data (The numbers)
        ArrayList<BarEntry> salesData = new ArrayList<>();
        salesData.add(new BarEntry(0, 120)); // X=0, 120 sales
        salesData.add(new BarEntry(1, 85));  // X=1, 85 sales
        salesData.add(new BarEntry(2, 60));  // X=2, 60 sales
        salesData.add(new BarEntry(3, 45));  // X=3, 45 sales

        // 2. Put data into a DataSet and style it
        BarDataSet dataSet = new BarDataSet(salesData, "Units Sold");
        dataSet.setColors(ColorTemplate.MATERIAL_COLORS); // Uses nice, colorful defaults
        dataSet.setValueTextSize(12f);
        dataSet.setValueTextColor(getResources().getColor(android.R.color.black));

        // 3. Attach the data to the chart
        BarData barData = new BarData(dataSet);
        barChart.setData(barData);

        // 4. Customize the X-Axis to show Medicine Names instead of numbers
        String[] itemNames = new String[]{"Paracetamol", "Amoxicillin", "Vitamin C", "Ibuprofen"};
        XAxis xAxis = barChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(itemNames));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM); // Move labels to the bottom
        xAxis.setDrawGridLines(false); // Make it look cleaner by hiding vertical lines
        xAxis.setGranularity(1f); // Ensure every label is shown

        // 5. Final UI touches
        barChart.getDescription().setEnabled(false); // Hide the default "Description Label"
        barChart.getAxisRight().setEnabled(false); // Hide the duplicate Y-axis on the right
        barChart.animateY(1000); // Add a cool 1-second animation when it loads!
        barChart.invalidate(); // Refresh the chart

    }

    private void lineChartLoad() {

        // 1. Create the data points (X = Day, Y = Sales Amount)
        ArrayList<Entry> trendData = new ArrayList<>();
        trendData.add(new Entry(0, 500)); // Monday
        trendData.add(new Entry(1, 620)); // Tuesday
        trendData.add(new Entry(2, 480)); // Wednesday
        trendData.add(new Entry(3, 750)); // Thursday
        trendData.add(new Entry(4, 810)); // Friday
        trendData.add(new Entry(5, 950)); // Saturday
        trendData.add(new Entry(6, 400)); // Sunday

        // 2. Put data into a DataSet and style the line
        LineDataSet dataSet = new LineDataSet(trendData, "Daily Revenue");
        dataSet.setColor(Color.parseColor("#00796B")); // Your solid theme color
        dataSet.setCircleColor(Color.parseColor("#00796B")); // Color of the dots
        dataSet.setLineWidth(3f); // Make the line slightly thicker
        dataSet.setCircleRadius(5f); // Make the dots bigger

        // 💎 PRO TIP: This fills the area under the line with your transparent color!
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(Color.parseColor("#BF00796B"));
        dataSet.setDrawValues(false); // Hides the numbers floating over the dots to keep it clean

        // 3. Attach data to the chart
        LineData lineData = new LineData(dataSet);
        lineChart.setData(lineData);

        // 4. Customize the X-Axis to show Days of the Week
        String[] days = new String[]{"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        XAxis xAxis = lineChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(days));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM); // Move days to the bottom
        xAxis.setDrawGridLines(false); // Hide vertical grid lines
        xAxis.setGranularity(1f); // Force it to show every single day

        // 5. Final UI touches
        lineChart.getDescription().setEnabled(false); // Hide default description
        lineChart.getAxisRight().setEnabled(false); // Hide right-side Y-axis
        lineChart.animateX(1000); // 🚀 Animate the line drawing from left to right!
        lineChart.invalidate(); // Refresh

    }

    private void setupLoadingDialog() {
        // 1. Create a horizontal layout container
        android.widget.LinearLayout layout = new android.widget.LinearLayout(requireContext());
        layout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        layout.setPadding(60, 60, 60, 60);
        layout.setGravity(android.view.Gravity.CENTER_VERTICAL);

        // 2. Add a modern spinning ProgressBar
        android.widget.ProgressBar progressBar = new android.widget.ProgressBar(requireContext());
        layout.addView(progressBar);

        // 3. Add the text message
        android.widget.TextView tvMessage = new android.widget.TextView(requireContext());
        tvMessage.setText("Uploading Prescription...");
        tvMessage.setTextSize(16f);
        tvMessage.setTextColor(android.graphics.Color.BLACK);
        tvMessage.setPadding(40, 0, 0, 0);
        layout.addView(tvMessage);

        // 4. Build the modern dialog!
        loadingDialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setCancelable(false) // Stops users from clicking outside to close it
                .setView(layout)
                .create();
    }

    // ==========================================
    // --- IN-LINE SEARCH LOGIC ---
    // ==========================================
    private void setupInLineSearchMode() {
        // 1. Setup Category Chips RecyclerView
        binding.rvSearchCategories.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        chipAdapter = new CategoryChipAdapter(requireContext(), searchCategoryChips, category -> {
            currentSelectedCategoryId = category.getId(); // Update the filter!
            currentPage = 1; // Reset to page 1
            applySearchFiltersAndSort(); // Trigger the filter math
        });
        binding.rvSearchCategories.setAdapter(chipAdapter);

        // 2. Setup the Search Results RecyclerView
        binding.rvHomeSearchResults.setLayoutManager(new GridLayoutManager(requireContext(), 2));

        // 3. The Back Button Trap
        searchBackCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                exitSearchMode();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), searchBackCallback);

        // 4. Search Bar Focus Listener
        binding.textInputSearch.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && !isSearchMode) {
                enterSearchMode();
            }
        });

        // 5. Typing Listener
        binding.textInputSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchText = s.toString().trim().toLowerCase();
                currentPage = 1;
                applySearchFiltersAndSort();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        // 6. Filter Menu
        binding.btnHomeFilter.setOnClickListener(v -> {
            PopupMenu popupMenu = new PopupMenu(requireContext(), binding.btnHomeFilter);
            popupMenu.getMenu().add("Name: A-Z");
            popupMenu.getMenu().add("Price: Low to High");
            popupMenu.getMenu().add("Price: High to Low");
            popupMenu.setOnMenuItemClickListener(item -> {
                currentSort = item.getTitle().toString();
                currentPage = 1;
                applySearchFiltersAndSort();
                return true;
            });
            popupMenu.show();
        });

        // 7. Pagination Clicks
        binding.btnSearchPrev.setOnClickListener(v -> {
            if (currentPage > 1) {
                currentPage--;
                updateSearchPaginationUI();
            }
        });

        binding.btnSearchNext.setOnClickListener(v -> {
            int totalPages = (int) Math.ceil((double) filteredSearchList.size() / ITEMS_PER_PAGE);
            if (currentPage < totalPages) {
                currentPage++;
                updateSearchPaginationUI();
            }
        });
    }

    private void enterSearchMode() {
        isSearchMode = true;
        searchBackCallback.setEnabled(true);

        binding.nestedScrollHome.animate().alpha(0f).setDuration(250).withEndAction(() -> {
            binding.nestedScrollHome.setVisibility(View.GONE);
            binding.layoutSearchResults.setVisibility(View.VISIBLE);
            binding.layoutSearchResults.setAlpha(0f);
            binding.layoutSearchResults.animate().alpha(1f).setDuration(250);
        });

        executorService.execute(() -> {
            fullSearchList = medicineDao.getAllActiveMedicines();
            requireActivity().runOnUiThread(this::applySearchFiltersAndSort);
        });
    }

    private void exitSearchMode() {
        isSearchMode = false;
        searchBackCallback.setEnabled(false);

        // Reset everything
        currentSearchText = "";
        currentSelectedCategoryId = "ALL";
        binding.textInputSearch.setText("");
        binding.textInputSearch.clearFocus();

        // Reset Chip Adapter back to "All" (Index 0)
        if (chipAdapter != null) {
            chipAdapter.notifyDataSetChanged();
            binding.rvSearchCategories.scrollToPosition(0);
        }

        InputMethodManager imm = (InputMethodManager) requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(binding.textInputSearch.getWindowToken(), 0);

        binding.layoutSearchResults.animate().alpha(0f).setDuration(250).withEndAction(() -> {
            binding.layoutSearchResults.setVisibility(View.GONE);
            binding.nestedScrollHome.setVisibility(View.VISIBLE);
            binding.nestedScrollHome.animate().alpha(1f).setDuration(250);
        });
    }

    private void applySearchFiltersAndSort() {
        filteredSearchList.clear();

        for (Medicine m : fullSearchList) {

            // 1. Check if it matches the Category Chip
            boolean matchesCategory = currentSelectedCategoryId.equals("ALL") ||
                    (m.getCategoryId() != null && m.getCategoryId().equals(currentSelectedCategoryId));

            // 2. Check if it matches the Search Text
            boolean matchesSearch = currentSearchText.isEmpty() ||
                    (m.getName() != null && m.getName().toLowerCase().contains(currentSearchText));

            // If it matches BOTH, add it to the final list!
            if (matchesCategory && matchesSearch) {
                filteredSearchList.add(m);
            }
        }

        // 3. Sort the final list
        Collections.sort(filteredSearchList, (m1, m2) -> {
            if (currentSort.equals("Price: Low to High")) {
                return Double.compare(m1.getPrice(), m2.getPrice());
            } else if (currentSort.equals("Price: High to Low")) {
                return Double.compare(m2.getPrice(), m1.getPrice());
            } else {
                return m1.getName().compareToIgnoreCase(m2.getName());
            }
        });

        updateSearchPaginationUI();
    }

    private void updateSearchPaginationUI() {
        if (filteredSearchList == null || filteredSearchList.isEmpty()) {

            // SHOW ANIMATION, HIDE LIST
            binding.rvHomeSearchResults.setVisibility(View.GONE);
            binding.layoutHomeEmptyState.setVisibility(View.VISIBLE);


            binding.tvSearchPage.setText("No results");
            binding.btnSearchPrev.setEnabled(false);
            binding.btnSearchNext.setEnabled(false);
            searchResultsAdapter = new MedicineAdapter(requireContext(), new ArrayList<>(), true);
            binding.rvHomeSearchResults.setAdapter(searchResultsAdapter);
            return;
        }

        // HIDE ANIMATION, SHOW LIST
        binding.layoutHomeEmptyState.setVisibility(View.GONE);
        binding.rvHomeSearchResults.setVisibility(View.VISIBLE);

        int totalItems = filteredSearchList.size();
        int totalPages = (int) Math.ceil((double) totalItems / ITEMS_PER_PAGE);

        if (currentPage > totalPages) currentPage = totalPages;
        if (currentPage < 1) currentPage = 1;

        int startIndex = (currentPage - 1) * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, totalItems);

        List<Medicine> pagedItems = filteredSearchList.subList(startIndex, endIndex);

        searchResultsAdapter = new MedicineAdapter(requireContext(), pagedItems, true);
        binding.rvHomeSearchResults.setAdapter(searchResultsAdapter);

        binding.tvSearchPage.setText("Page " + currentPage + " of " + totalPages);
        binding.btnSearchPrev.setEnabled(currentPage > 1);
        binding.btnSearchNext.setEnabled(currentPage < totalPages);
    }

    // ==========================================
    // --- CREATE THE FAKE "ALL" CATEGORY ---
    // ==========================================
    private void updateCategoryChips(List<Category> dbCategories) {
        searchCategoryChips.clear();

        // Inject Fake "All" Category
        Category allCategory = new Category();
        allCategory.setId("ALL");
        allCategory.setName("All");
        searchCategoryChips.add(allCategory);

        // Add real categories
        searchCategoryChips.addAll(dbCategories);

        if (chipAdapter != null) {
            chipAdapter.notifyDataSetChanged();
        }
    }

    // ==========================================
    // --- EXISTING FIREBASE LOGIC ---
    // ==========================================
    private void loadCategoriesFromRoomAndSync() {
        executorService.execute(() -> {
            List<Category> localCategories = categoryDao.getActiveCategories();
            requireActivity().runOnUiThread(() -> {
                categoryList.clear();
                categoryList.addAll(localCategories);
                categoryAdapter.notifyDataSetChanged();

                // UPDATE THE CHIPS TOO!
                updateCategoryChips(localCategories);
            });
            long latestLocalTimestamp = categoryDao.getLatestTimestamp();
            requireActivity().runOnUiThread(() -> syncCategoriesWithFirebase(latestLocalTimestamp));
        });
    }

    private void syncCategoriesWithFirebase(long latestLocalTimestamp) {
        db.collection("categories").whereGreaterThan("lastUpdated", latestLocalTimestamp).get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) return;
                    List<Category> newOrUpdatedCategories = new ArrayList<>();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        Category category = document.toObject(Category.class);
                        category.setId(document.getId());
                        newOrUpdatedCategories.add(category);
                    }
                    executorService.execute(() -> {
                        categoryDao.insertCategories(newOrUpdatedCategories);
                        List<Category> updatedLocalCategories = categoryDao.getActiveCategories();
                        requireActivity().runOnUiThread(() -> {
                            categoryList.clear();
                            categoryList.addAll(updatedLocalCategories);
                            categoryAdapter.notifyDataSetChanged();

                            // UPDATE THE CHIPS TOO!
                            updateCategoryChips(updatedLocalCategories);
                        });
                    });
                });
    }

    private void loadMedicinesFromRoomAndSync() {
        executorService.execute(() -> {
            List<Medicine> localTopMedicines = medicineDao.getTopSellingMedicines();
            requireActivity().runOnUiThread(() -> {
                topMedicineList.clear();
                topMedicineList.addAll(localTopMedicines);
                medicineAdapter.notifyDataSetChanged();
            });
            long latestLocalTimestamp = medicineDao.getLatestTimestamp();
            requireActivity().runOnUiThread(() -> syncMedicinesWithFirebase(latestLocalTimestamp));
        });
    }

    private void syncMedicinesWithFirebase(long latestLocalTimestamp) {
        db.collection("medicines").whereGreaterThan("lastUpdated", latestLocalTimestamp).get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) return;
                    List<Medicine> newOrUpdatedMedicines = new ArrayList<>();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        Medicine medicine = document.toObject(Medicine.class);
                        medicine.setId(document.getId());
                        newOrUpdatedMedicines.add(medicine);
                    }
                    executorService.execute(() -> {
                        medicineDao.insertMedicines(newOrUpdatedMedicines);
                        List<Medicine> updatedLocalMedicines = medicineDao.getTopSellingMedicines();
                        requireActivity().runOnUiThread(() -> {
                            topMedicineList.clear();
                            topMedicineList.addAll(updatedLocalMedicines);
                            medicineAdapter.notifyDataSetChanged();
                        });
                    });
                });
    }


    // ==========================================
    // 2. THE HOMETOWN MATCHING ENGINE
    // ==========================================
//    private void findNearestPharmacies(Uri imageUri) {
//        if (loadingDialog == null) setupLoadingDialog();
//        // Update dialog text to let the user know what's happening
//        // (Assuming you made your TextView accessible, or just use a generic message)
//        loadingDialog.show();
//
//        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
//        FirebaseFirestore db = FirebaseFirestore.getInstance();
//
//        // Step 1: Get the user's Hometown
//        db.collection("users").document(userId).get()
//                .addOnSuccessListener(userDoc -> {
//                    if (userDoc.exists() && userDoc.contains("deliveryAddress.homeTown")) {
//                        String homeTown = userDoc.getString("deliveryAddress.homeTown");
//
//                        user = userDoc.toObject(User.class);
//
//                        // Step 2: Get all Approved Pharmacies
//                        db.collection("pharmacist_requests").whereEqualTo("status", "approved").get()
//                                .addOnSuccessListener(querySnapshot -> {
//
//                                    List<com.google.firebase.firestore.DocumentSnapshot> localPharmacies = new java.util.ArrayList<>();
//
//                                    // Step 3: The Smart Filter
//                                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot) {
//                                        String address = doc.getString("pharmacyAddress");
//                                        if (address != null && homeTown != null && address.toLowerCase().contains(homeTown.toLowerCase())) {
//                                            localPharmacies.add(doc);
//                                        }
//                                    }
//
//                                    loadingDialog.dismiss();
//
//                                    // Step 4: Handle the result
//                                    if (localPharmacies.isEmpty()) {
//                                        Toast.makeText(requireContext(), "No pharmacies found in " + homeTown, Toast.LENGTH_LONG).show();
//                                    } else {
//                                        showPharmacySelectionBottomSheet(imageUri, localPharmacies);
//                                    }
//
//                                })
//                                .addOnFailureListener(e -> {
//                                    loadingDialog.dismiss();
//                                    Toast.makeText(requireContext(), "Failed to load pharmacies.", Toast.LENGTH_SHORT).show();
//                                });
//
//                    } else {
//                        loadingDialog.dismiss();
//                        Toast.makeText(requireContext(), "Please update your delivery address in your profile first!", Toast.LENGTH_LONG).show();
//                    }
//                })
//                .addOnFailureListener(e -> {
//                    loadingDialog.dismiss();
//                    Toast.makeText(requireContext(), "Failed to get user data.", Toast.LENGTH_SHORT).show();
//                });
//    }

    @SuppressWarnings("MissingPermission")
    private void findNearestPharmacies(Uri imageUri) {
        if (loadingDialog == null) setupLoadingDialog();
        loadingDialog.show();

        // 1. Check if the user has actually granted GPS permissions!
        if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            loadingDialog.dismiss();
            android.widget.Toast.makeText(requireContext(), "Please enable Location Permissions to find nearby pharmacies.", android.widget.Toast.LENGTH_LONG).show();
            return;
        }

        // 2. Grab the user's exact live GPS location
        fusedLocationClient.getLastLocation().addOnSuccessListener(requireActivity(), location -> {
            if (location != null) {
                // We have the exact location! Now run the pharmacy math.
                calculateDistancesFromLiveLocation(imageUri, location);
            } else {
                loadingDialog.dismiss();
                android.widget.Toast.makeText(requireContext(), "Could not get current location. Please ensure your phone's GPS is turned on.", android.widget.Toast.LENGTH_LONG).show();
            }
        }).addOnFailureListener(e -> {
            loadingDialog.dismiss();
            android.widget.Toast.makeText(requireContext(), "Error getting location.", android.widget.Toast.LENGTH_SHORT).show();
        });
    }

    // 3. The Math Engine (Runs in the background)
    private void calculateDistancesFromLiveLocation(Uri imageUri, android.location.Location userLocation) {
        com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();

        db.collection("pharmacist_requests").whereEqualTo("status", "approved").get()
                .addOnSuccessListener(querySnapshot -> {

                    executorService.execute(() -> {
                        java.util.List<android.util.Pair<com.google.firebase.firestore.DocumentSnapshot, Float>> localPharmacies = new java.util.ArrayList<>();
                        android.location.Geocoder geocoder = new android.location.Geocoder(requireContext(), java.util.Locale.getDefault());

                        for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot) {
                            String address = doc.getString("pharmacyAddress");

                            if (address != null && !address.isEmpty()) {
                                try {
                                    // Convert Pharmacy address to Coordinates
                                    java.util.List<android.location.Address> pharmAddresses = geocoder.getFromLocationName(address + ", Sri Lanka", 1);

                                    if (pharmAddresses != null && !pharmAddresses.isEmpty()) {
                                        android.location.Address pharmLocation = pharmAddresses.get(0);

                                        // Calculate exact distance from the User's live GPS to the Pharmacy!
                                        float[] results = new float[1];
                                        android.location.Location.distanceBetween(
                                                userLocation.getLatitude(), userLocation.getLongitude(),
                                                pharmLocation.getLatitude(), pharmLocation.getLongitude(),
                                                results
                                        );

                                        float distanceInMeters = results[0];

                                        // The 15 KM Filter (15,000 meters)
                                        if (distanceInMeters <= 10000) {
                                            localPharmacies.add(new android.util.Pair<>(doc, distanceInMeters));
                                        }
                                    }
                                } catch (java.io.IOException e) {
                                    // Skip this specific pharmacy if the network drops momentarily
                                    e.printStackTrace();
                                }
                            }
                        }

                        // Go back to the UI thread to show the results
                        requireActivity().runOnUiThread(() -> {
                            loadingDialog.dismiss();
                            if (localPharmacies.isEmpty()) {
                                android.widget.Toast.makeText(requireContext(), "No pharmacies found within 15 km of your current location.", android.widget.Toast.LENGTH_LONG).show();
                            } else {
                                // This method already has the sorting logic we added earlier!
                                showPharmacySelectionBottomSheet(imageUri, localPharmacies);
                            }
                        });
                    });
                })
                .addOnFailureListener(e -> {
                    loadingDialog.dismiss();
                    android.widget.Toast.makeText(requireContext(), "Failed to load pharmacies.", android.widget.Toast.LENGTH_SHORT).show();
                });
    }

    // ==========================================
    // 3. SHOW BOTTOM SHEET UI
    // ==========================================
//    private void showPharmacySelectionBottomSheet(Uri imageUri, List<com.google.firebase.firestore.DocumentSnapshot> pharmacies) {
//        com.google.android.material.bottomsheet.BottomSheetDialog bottomSheetDialog =
//                new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext());
//
//        View sheetView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_pharmacies, null);
//        bottomSheetDialog.setContentView(sheetView);
//
//        RadioGroup rgDelivery = sheetView.findViewById(R.id.rg_delivery_method);
//        android.widget.LinearLayout layoutPharmacyList = sheetView.findViewById(R.id.layout_pharmacy_list);
//
//        // Dynamically add a card for each matching pharmacy
//        for (com.google.firebase.firestore.DocumentSnapshot doc : pharmacies) {
//            View cardView = LayoutInflater.from(requireContext()).inflate(R.layout.item_pharmacy_selection, layoutPharmacyList, false);
//
//            android.widget.TextView tvName = cardView.findViewById(R.id.tv_pharmacy_name);
//            android.widget.TextView tvAddress = cardView.findViewById(R.id.tv_pharmacy_address);
//            android.widget.ImageView ivProfile = cardView.findViewById(R.id.iv_pharmacy_profile);
//
//            String pharmacyName = doc.getString("pharmacyName");
//            String profileUrl = doc.getString("profileImage");
//            String pharmacyId = doc.getString("uid"); // The pharmacist's user ID
//
//            tvName.setText(pharmacyName);
//            tvAddress.setText(doc.getString("pharmacyAddress"));
//
//            if (profileUrl != null && !profileUrl.isEmpty()) {
//
//                ivProfile.setImageTintList(null);
//
//                com.bumptech.glide.Glide.with(requireContext())
//                        .load(profileUrl)
//                        .centerCrop() // Perfect circular crop
//                        .into(ivProfile);
//            }
//
//            // When user clicks a pharmacy, START THE UPLOAD
//            cardView.setOnClickListener(v -> {
//                // Find out what the user selected
//                int selectedId = rgDelivery.getCheckedRadioButtonId();
//                String deliveryMethod = "Pickup"; // Default
//
//                if (selectedId == R.id.rb_cod) deliveryMethod = "COD";
//                else if (selectedId == R.id.rb_online) deliveryMethod = "Online";
//
//                bottomSheetDialog.dismiss();
//
//                // 👉 Pass this new deliveryMethod string to your upload method!
//                uploadPrescriptionToFirebase(imageUri, pharmacyId, pharmacyName, deliveryMethod);
//            });
//
//            layoutPharmacyList.addView(cardView);
//        }
//
//        bottomSheetDialog.show();
//    }

    // 👉 Notice the updated List parameter!
    private void showPharmacySelectionBottomSheet(Uri imageUri, List<android.util.Pair<com.google.firebase.firestore.DocumentSnapshot, Float>> pharmacies) {
        com.google.android.material.bottomsheet.BottomSheetDialog bottomSheetDialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext());

        View sheetView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_pharmacies, null);
        bottomSheetDialog.setContentView(sheetView);

        android.widget.RadioGroup rgDelivery = sheetView.findViewById(R.id.rg_delivery_method);
        android.widget.LinearLayout layoutPharmacyList = sheetView.findViewById(R.id.layout_pharmacy_list);

        // 👉 Sort the list so the CLOSEST pharmacies show up at the top!
        java.util.Collections.sort(pharmacies, (p1, p2) -> Float.compare(p1.second, p2.second));

        // Dynamically add a card for each matching pharmacy
        for (android.util.Pair<com.google.firebase.firestore.DocumentSnapshot, Float> item : pharmacies) {
            com.google.firebase.firestore.DocumentSnapshot doc = item.first;
            Float distanceInMeters = item.second;

            View cardView = LayoutInflater.from(requireContext()).inflate(R.layout.item_pharmacy_selection, layoutPharmacyList, false);

            android.widget.TextView tvName = cardView.findViewById(R.id.tv_pharmacy_name);
            android.widget.TextView tvAddress = cardView.findViewById(R.id.tv_pharmacy_address);
            android.widget.TextView tvDistance = cardView.findViewById(R.id.tv_pharmacy_distance); // The new Distance Text!
            android.widget.ImageView ivProfile = cardView.findViewById(R.id.iv_pharmacy_profile);

            String pharmacyName = doc.getString("pharmacyName");
            String profileUrl = doc.getString("profileImage");
            String pharmacyId = doc.getString("uid");

            tvName.setText(pharmacyName);
            tvAddress.setText(doc.getString("pharmacyAddress"));

            // 👉 Convert meters to km and set the text
            float distanceInKm = distanceInMeters / 1000f;
            tvDistance.setText(String.format(java.util.Locale.getDefault(), "📍 %.1f km away", distanceInKm));

            if (profileUrl != null && !profileUrl.isEmpty()) {
                ivProfile.setImageTintList(null);
                com.bumptech.glide.Glide.with(requireContext())
                        .load(profileUrl)
                        .centerCrop()
                        .into(ivProfile);
            }

            cardView.setOnClickListener(v -> {
                int selectedId = rgDelivery.getCheckedRadioButtonId();
                String deliveryMethod = "Pickup";

                if (selectedId == R.id.rb_cod) deliveryMethod = "COD";
                else if (selectedId == R.id.rb_online) deliveryMethod = "Online";

                bottomSheetDialog.dismiss();
                uploadPrescriptionToFirebase(imageUri, pharmacyId, pharmacyName, deliveryMethod);
            });

            layoutPharmacyList.addView(cardView);
        }

        bottomSheetDialog.show();
    }

    // 3. Upload to Firebase Storage and Save to Firestore
    private void uploadPrescriptionToFirebase(android.net.Uri imageUri, String pharmacyId, String pharmacyName, String deliveryMethod) {
        if (loadingDialog == null) setupLoadingDialog();
        loadingDialog.show();

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        String orderId = "PRES-" + System.currentTimeMillis();

        com.google.firebase.storage.StorageReference storageRef = com.google.firebase.storage.FirebaseStorage.getInstance().getReference()
                .child("prescriptions/" + userId + "/" + orderId + ".jpg");

        // 👉 THE MAGIC: putFile() uploads the 100% original, uncompressed image!
        storageRef.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> {
                    storageRef.getDownloadUrl()
                            .addOnSuccessListener(uri -> {
                                savePrescriptionOrderToDatabase(orderId, userId, uri.toString(), pharmacyId, pharmacyName, deliveryMethod);
                            })
                            .addOnFailureListener(e -> {
                                loadingDialog.dismiss();
                                Toast.makeText(getContext(), "Failed to generate URL", Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    loadingDialog.dismiss();
                    Toast.makeText(getContext(), "Upload Blocked: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void savePrescriptionOrderToDatabase(String orderId, String userId, String imageUrl, String pharmacyId, String pharmacyName, String deliveryMethod) {

        DeliveryAddress deliveryAddressData = new DeliveryAddress();

        if (deliveryMethod.equals("COD")) {

            if (user.getDeliveryAddress() != null) {
                deliveryAddressData = user.getDeliveryAddress();
            } else {
                Toast.makeText(getContext(), "Please update your delivery address in your profile first!", Toast.LENGTH_LONG).show();
                return;
            }

        } else {
            deliveryAddressData = null;
        }

        // 👉 THE UPGRADE: Using your elegant Lombok Builder!
        Order newOrder = Order.builder()
                .orderId(orderId)
                .status("Pending")
                .grandTotal(0.0)
                .deliveryMethod(deliveryMethod)
                .prescriptionUrl(imageUrl)
                .userId(userId)
                .pharmacyId(pharmacyId)
                .pharmacyName(pharmacyName)
                .deliveryAddress(deliveryAddressData)
                .items(new java.util.ArrayList<>())
                .build();

        FirebaseFirestore.getInstance().collection("orders").document(orderId)
                .set(newOrder) // Pass the whole object directly!
                .addOnSuccessListener(aVoid -> {
                    loadingDialog.dismiss();
                    Toast.makeText(getContext(), "Prescription Sent to " + pharmacyName + "!", Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(e -> {
                    loadingDialog.dismiss();
                    Toast.makeText(getContext(), "Database Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }


    @Override
    public void onResume() {
        super.onResume();

        BottomNavigationView bottomNavigationView = requireActivity().findViewById(R.id.bottom_navigation_view);

        if (bottomNavigationView != null) {
            bottomNavigationView.setVisibility(View.VISIBLE);
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