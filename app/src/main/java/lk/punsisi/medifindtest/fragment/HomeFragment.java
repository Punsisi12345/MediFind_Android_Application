package lk.punsisi.medifindtest.fragment;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.PopupMenu;
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
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
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
import lk.punsisi.medifindtest.model.Medicine;
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
    private ActivityResultLauncher<Void> cameraLauncher;
    private AlertDialog loadingDialog;

    private String currentUserRole = "user";

    private BarChart barChart;
    private LineChart lineChart;
    private PieChart expiryChart,rxOtcChart;


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


        // 1. Setup the Camera Launcher
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicturePreview(),
                bitmap -> {
                    if (bitmap != null) {
                        uploadPrescriptionToFirebase(bitmap);
                    }
                }
        );

        binding.myCardDiv.setOnClickListener(v -> {
            cameraLauncher.launch(null);
        });

        barChart = binding.topSellingChart;
        barChartLoad();

        lineChart = binding.salesTrendChart;
        lineChartLoad();

        expiryChart = binding.expiryPieChart;
        rxOtcChart = binding.rxOtcPieChart;

        setupExpiryChart(expiryChart);
        setupRxOtcChart(rxOtcChart);


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




        return binding.getRoot();
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

    private void lineChartLoad(){

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


    // 3. Upload to Firebase Storage and Save to Firestore
    private void uploadPrescriptionToFirebase(android.graphics.Bitmap bitmap) {
        if (loadingDialog == null) setupLoadingDialog();
        loadingDialog.show(); // Show the modern dialog!

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        String orderId = "PRES-" + System.currentTimeMillis();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos); // Compressed to 80% to save speed
        byte[] data = baos.toByteArray();

        StorageReference storageRef = FirebaseStorage.getInstance().getReference()
                .child("prescriptions/" + userId + "/" + orderId + ".jpg");

        // Upload the image byte array
        storageRef.putBytes(data)
                .addOnSuccessListener(taskSnapshot -> {
                    // Get the live download URL
                    storageRef.getDownloadUrl()
                            .addOnSuccessListener(uri -> {
                                savePrescriptionOrderToDatabase(orderId, userId, uri.toString());
                            })
                            .addOnFailureListener(e -> {
                                loadingDialog.dismiss(); // FAILSAFE
                                Toast.makeText(getContext(), "Failed to generate URL", Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    loadingDialog.dismiss(); // FAILSAFE
                    Toast.makeText(getContext(), "Upload Blocked: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void savePrescriptionOrderToDatabase(String orderId, String userId, String imageUrl) {
        Map<String, Object> orderData = new HashMap<>();
        orderData.put("orderId", orderId);
        orderData.put("status", "Reviewing Prescription");
        orderData.put("grandTotal", 0.0);
        orderData.put("prescriptionUrl", imageUrl);
        orderData.put("userId", userId);
        orderData.put("timestamp", com.google.firebase.firestore.FieldValue.serverTimestamp());
        orderData.put("items", new java.util.ArrayList<>());

        FirebaseFirestore.getInstance().collection("orders").document(orderId)
                .set(orderData)
                .addOnSuccessListener(aVoid -> {
                    loadingDialog.dismiss(); // SUCCESS -> CLOSE DIALOG
                    Toast.makeText(getContext(), "Prescription Sent to Pharmacist!", Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(e -> {
                    loadingDialog.dismiss(); // FAILSAFE
                    Toast.makeText(getContext(), "Database Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }


    @Override
    public void onResume() {
        super.onResume();

        BottomNavigationView bottomNavigationView = requireActivity().findViewById(R.id.bottom_navigation_view);

        if (bottomNavigationView != null){
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