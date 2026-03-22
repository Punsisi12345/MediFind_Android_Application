package lk.punsisi.medifindtest.fragment;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Pair;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
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
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.AggregateSource;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lk.punsisi.medifindtest.R;
import lk.punsisi.medifindtest.activity.AllCategoriesActivity;
import lk.punsisi.medifindtest.activity.MedicinesListActivity;
import lk.punsisi.medifindtest.adapter.CategoryAdapter;
import lk.punsisi.medifindtest.adapter.CategoryChipAdapter;
import lk.punsisi.medifindtest.adapter.MedicineAdapter;
import lk.punsisi.medifindtest.databinding.BottomSheetPharmaciesBinding;
import lk.punsisi.medifindtest.databinding.FragmentHomeBinding;
import lk.punsisi.medifindtest.databinding.ItemPharmacySelectionBinding;
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

    private RecyclerView recyclerViewCategories;
    private CategoryAdapter categoryAdapter;
    private List<Category> categoryList;
    private RecyclerView recyclerViewMedicines;
    private MedicineAdapter medicineAdapter;
    private List<Medicine> topMedicineList;

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private CategoryDao categoryDao;
    private MedicineDao medicineDao;
    private ExecutorService executorService;
    private FusedLocationProviderClient fusedLocationClient;

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

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        //charts load
        barChart = binding.topSellingChart;
        lineChart = binding.salesTrendChart;
        expiryChart = binding.expiryPieChart;
        rxOtcChart = binding.rxOtcPieChart;

        SharedPreferences prefs = requireActivity().getSharedPreferences("MediFindPrefs", Context.MODE_PRIVATE);
        currentUserRole = prefs.getString("USER_ROLE", "user");

        //pharmacist or user check
        if (currentUserRole.equals("pharmacist") || currentUserRole.equals("admin")) {

            binding.layoutUserDashboard.setVisibility(View.GONE);
            binding.layoutPharmacistDashboard.setVisibility(View.VISIBLE);

            loadDashboardMetrics();
            loadLiveChartData();

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


        } else {

            binding.layoutUserDashboard.setVisibility(View.VISIBLE);
            binding.layoutPharmacistDashboard.setVisibility(View.GONE);

            binding.layoutSeeMoreCategories.setOnClickListener(v -> {
                startActivity(new Intent(requireContext(), AllCategoriesActivity.class));
            });

            binding.layoutSeeMoreMedicines.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), MedicinesListActivity.class);
                intent.putExtra("IS_ALL_MEDICINES", true);
                intent.putExtra("CATEGORY_NAME", "All Medicines");
                startActivity(intent);
            });
        }

        categoryDao = AppDatabase.getDatabase(getContext()).categoryDao();
        medicineDao = AppDatabase.getDatabase(getContext()).medicineDao();
        executorService = Executors.newSingleThreadExecutor();

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        //category add to adapter
        recyclerViewCategories = binding.recyclerViewQuickCategories;
        recyclerViewCategories.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        categoryList = new ArrayList<>();
        categoryAdapter = new CategoryAdapter(getContext(), categoryList, false);
        recyclerViewCategories.setAdapter(categoryAdapter);

        //medicine add to adapter
        recyclerViewMedicines = binding.recyclerViewTopMedicines;
        recyclerViewMedicines.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        topMedicineList = new ArrayList<>();
        medicineAdapter = new MedicineAdapter(getContext(), topMedicineList, false);
        recyclerViewMedicines.setAdapter(medicineAdapter);

        //search part
        setupInLineSearchMode();

        //load data from room
        loadCategoriesFromRoomAndSync();
        loadMedicinesFromRoomAndSync();

        //open gallery for prescription
        galleryLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {

                        //find pharmacies around 10 KM
                        findNearestPharmacies(uri);
                    }
                }
        );

        binding.myCardDiv.setOnClickListener(v -> {
            galleryLauncher.launch("image/*");
        });


        return binding.getRoot();
    }

    private void loadDashboardMetrics() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) return;

        String pharmacyId = currentUser.getUid();

        //Count Medicines
        db.collection("medicines")
                .whereEqualTo("pharmacistId", pharmacyId)
                .count()
                .get(com.google.firebase.firestore.AggregateSource.SERVER)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        binding.medicineData.setText(String.valueOf(task.getResult().getCount()));
                    } else {
                        binding.medicineData.setText("-");
                    }
                });

        //Count Orders
        db.collection("orders")
                .whereEqualTo("pharmacyId", pharmacyId)
                .whereEqualTo("status", "Completed")
                .count()
                .get(com.google.firebase.firestore.AggregateSource.SERVER)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        binding.salesData.setText(String.valueOf(task.getResult().getCount()));
                    } else {
                        binding.salesData.setText("-");
                    }
                });

        //Count Feedback
        db.collection("customer_feedback")
                .whereEqualTo("pharmacyId", pharmacyId)
                .count()
                .get(com.google.firebase.firestore.AggregateSource.SERVER)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        binding.feedbackData.setText(String.valueOf(task.getResult().getCount()));
                    } else {
                        binding.feedbackData.setText("-");
                    }
                });
    }

    private void loadLiveChartData() {
        FirebaseUser currentUser = auth.getCurrentUser();
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

                    //data bucket
                    float[] monthlyRevenue = new float[12];
                    HashMap<String, Long> itemSales = new HashMap<>();
                    int rxCount = 0;
                    int otcCount = 0;

                    int currentYear = Calendar.getInstance().get(Calendar.YEAR);

                    for (QueryDocumentSnapshot doc : value) {
                        Order order = doc.toObject(Order.class);

                        //revenue cal(current year)
                        if (order.getTimestamp() != null) {
                            Calendar cal = Calendar.getInstance();
                            cal.setTime(order.getTimestamp());

                            if (cal.get(java.util.Calendar.YEAR) == currentYear) {
                                int month = cal.get(Calendar.MONTH);
                                monthlyRevenue[month] += order.getGrandTotal();
                            }
                        }

                        //rx and otc cal
                        boolean hasPrescription = order.getPrescriptionUrl() != null && !order.getPrescriptionUrl().isEmpty();
                        boolean hasItems = order.getItems() != null && !order.getItems().isEmpty();

                        if (hasPrescription) rxCount++;
                        if (hasItems) otcCount++;

                        //top selling medicines cal
                        if (hasItems) {
                            for (Map<String, Object> item : order.getItems()) {
                                String name = (String) item.get("name");
                                Long qty = (Long) item.get("quantity");
                                if (name != null && qty != null) {
                                    itemSales.put(name, itemSales.getOrDefault(name, 0L) + qty);
                                }
                            }
                        }
                    }

                    // add data to charts
                    updateMonthlyRevenueChart(monthlyRevenue);
                    updateTopSellingChart(itemSales);
                    updateRxOtcLiveChart(rxCount, otcCount);
                });
    }

    private void updateMonthlyRevenueChart(float[] monthlyRevenue) {
        ArrayList<Entry> trendData = new ArrayList<>();

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

        //x-axis
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

    private void updateTopSellingChart(HashMap<String, Long> itemSales) {

        List<Map.Entry<String, Long>> sortedItems = new ArrayList<>(itemSales.entrySet());
        sortedItems.sort((o1, o2) -> o2.getValue().compareTo(o1.getValue())); // descending order

        ArrayList<BarEntry> salesData = new ArrayList<>();
        ArrayList<String> itemNames = new ArrayList<>();

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

        //X-Axis
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

    private void setupLoadingDialog() {

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setPadding(60, 60, 60, 60);
        layout.setGravity(Gravity.CENTER_VERTICAL);

        ProgressBar progressBar = new ProgressBar(requireContext());
        layout.addView(progressBar);

        TextView tvMessage = new TextView(requireContext());
        tvMessage.setText("Uploading Prescription...");
        tvMessage.setTextSize(16f);
        tvMessage.setTextColor(Color.BLACK);
        tvMessage.setPadding(40, 0, 0, 0);
        layout.addView(tvMessage);

        loadingDialog = new MaterialAlertDialogBuilder(requireContext())
                .setCancelable(false)
                .setView(layout)
                .create();
    }

    private void setupInLineSearchMode() {
        //category chip add
        binding.rvSearchCategories.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        chipAdapter = new CategoryChipAdapter(requireContext(), searchCategoryChips, category -> {
            currentSelectedCategoryId = category.getId();
            currentPage = 1;
            applySearchFiltersAndSort();
        });
        binding.rvSearchCategories.setAdapter(chipAdapter);

        binding.rvHomeSearchResults.setLayoutManager(new GridLayoutManager(requireContext(), 2));

        //click back button
        searchBackCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                exitSearchMode();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), searchBackCallback);


        binding.textInputSearch.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && !isSearchMode) {
                enterSearchMode();
            }
        });

        //back button login handle when open search bar
        binding.textInputSearch.setOnKeyListener((v, keyCode, event) -> {

            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                if (isSearchMode) {
                    exitSearchMode();
                    return true; //handle by keyListener
                }
            }
            return false;
        });

        //typing Listener
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

        //filter menu
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

        //pagination click
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

        // reset everything
        currentSearchText = "";
        currentSelectedCategoryId = "ALL";
        binding.textInputSearch.setText("");
        binding.textInputSearch.clearFocus();

        // reset chip and select all chip
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

            boolean matchesCategory = currentSelectedCategoryId.equals("ALL") ||
                    (m.getCategoryId() != null && m.getCategoryId().equals(currentSelectedCategoryId));

            boolean matchesSearch = currentSearchText.isEmpty() ||
                    (m.getName() != null && m.getName().toLowerCase().contains(currentSearchText));

            if (matchesCategory && matchesSearch) {
                filteredSearchList.add(m);
            }
        }

        //sort list
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

            binding.rvHomeSearchResults.setVisibility(View.GONE);
            binding.layoutHomeEmptyState.setVisibility(View.VISIBLE);

            binding.tvSearchPage.setText("No results");
            binding.btnSearchPrev.setEnabled(false);
            binding.btnSearchNext.setEnabled(false);
            searchResultsAdapter = new MedicineAdapter(requireContext(), new ArrayList<>(), true);
            binding.rvHomeSearchResults.setAdapter(searchResultsAdapter);
            return;
        }

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

    private void updateCategoryChips(List<Category> dbCategories) {
        searchCategoryChips.clear();

        Category allCategory = new Category();
        allCategory.setId("ALL");
        allCategory.setName("All");
        searchCategoryChips.add(allCategory);

        searchCategoryChips.addAll(dbCategories);

        if (chipAdapter != null) {
            chipAdapter.notifyDataSetChanged();
        }
    }

    private void loadCategoriesFromRoomAndSync() {
        executorService.execute(() -> {
            List<Category> localCategories = categoryDao.getActiveCategories();
            requireActivity().runOnUiThread(() -> {
                categoryList.clear();
                categoryList.addAll(localCategories);
                categoryAdapter.notifyDataSetChanged();

                //update category chips
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

                            //update chips
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


    @SuppressWarnings("MissingPermission")
    private void findNearestPharmacies(Uri imageUri) {
        if (loadingDialog == null) setupLoadingDialog();
        loadingDialog.show();

        //check gps permission
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            loadingDialog.dismiss();
            Toast.makeText(requireContext(), "Please enable Location Permissions to find nearby pharmacies.", Toast.LENGTH_LONG).show();
            return;
        }

        //get user live location
        fusedLocationClient.getLastLocation().addOnSuccessListener(requireActivity(), location -> {
            if (location != null) {
                calculateDistancesFromLiveLocation(imageUri, location);
            } else {
                loadingDialog.dismiss();
                android.widget.Toast.makeText(requireContext(), "Could not get current location. Please ensure your phone's GPS is turned on.", Toast.LENGTH_LONG).show();
            }
        }).addOnFailureListener(e -> {
            loadingDialog.dismiss();
           Toast.makeText(requireContext(), "Error getting location.", Toast.LENGTH_SHORT).show();
        });
    }

    //working in background thread to calculate distance to pharmacy
    private void calculateDistancesFromLiveLocation(Uri imageUri, Location userLocation) {

        db.collection("pharmacist_requests").whereEqualTo("status", "approved").get()
                .addOnSuccessListener(querySnapshot -> {
                    executorService.execute(() -> {
                        List<Pair<DocumentSnapshot, Float>> localPharmacies = new ArrayList<>();
                        Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());

                        for (DocumentSnapshot doc : querySnapshot) {
                            String address = doc.getString("pharmacyAddress");

                            if (address != null && !address.isEmpty()) {
                                try {
                                    //address convert to coordinates
                                    List<Address> pharmAddresses = geocoder.getFromLocationName(address + ", Sri Lanka", 1);

                                    if (pharmAddresses != null && !pharmAddresses.isEmpty()) {
                                        Address pharmLocation = pharmAddresses.get(0);

                                        //distance to pharmacy from user location
                                        float[] results = new float[1];
                                        android.location.Location.distanceBetween(
                                                userLocation.getLatitude(), userLocation.getLongitude(),
                                                pharmLocation.getLatitude(), pharmLocation.getLongitude(),
                                                results
                                        );

                                        float distanceInMeters = results[0];

                                        // 10KM filter
                                        if (distanceInMeters <= 10000) {
                                            localPharmacies.add(new Pair<>(doc, distanceInMeters));
                                        }
                                    }
                                } catch (IOException e) {
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

    private void showPharmacySelectionBottomSheet(Uri imageUri, List<Pair<DocumentSnapshot, Float>> pharmacies) {
        BottomSheetDialog bottomSheetDialog =
                new BottomSheetDialog(requireContext());

        BottomSheetPharmaciesBinding sheetBinding = BottomSheetPharmaciesBinding.inflate(LayoutInflater.from(requireContext()));
        bottomSheetDialog.setContentView(sheetBinding.getRoot());

        //closet pharmacies to top
        Collections.sort(pharmacies, (p1, p2) -> Float.compare(p1.second, p2.second));

        for (Pair<DocumentSnapshot, Float> item : pharmacies) {
            DocumentSnapshot doc = item.first;
            Float distanceInMeters = item.second;

            ItemPharmacySelectionBinding itemBinding = ItemPharmacySelectionBinding.inflate(
                    LayoutInflater.from(requireContext()),
                    sheetBinding.layoutPharmacyList,
                    false
            );

            String pharmacyName = doc.getString("pharmacyName");
            String profileUrl = doc.getString("profileImage");
            String pharmacyId = doc.getString("uid");

            itemBinding.tvPharmacyName.setText(pharmacyName);
            itemBinding.tvPharmacyAddress.setText(doc.getString("pharmacyAddress"));

            //M to KM
            float distanceInKm = distanceInMeters / 1000f;
            itemBinding.tvPharmacyDistance.setText(String.format(Locale.getDefault(), "📍 %.1f km away", distanceInKm));

            // Load the profile image
            if (profileUrl != null && !profileUrl.isEmpty()) {
                itemBinding.ivPharmacyProfile.setImageTintList(null);
                Glide.with(requireContext())
                        .load(profileUrl)
                        .centerCrop()
                        .into(itemBinding.ivPharmacyProfile);
            }

            itemBinding.getRoot().setOnClickListener(v -> {

                int selectedId = sheetBinding.rgDeliveryMethod.getCheckedRadioButtonId();
                String deliveryMethod = "Pickup";

                if (selectedId == R.id.rb_cod) deliveryMethod = "COD";
                else if (selectedId == R.id.rb_online) deliveryMethod = "Online";

                bottomSheetDialog.dismiss();

                uploadPrescriptionToFirebase(imageUri, pharmacyId, pharmacyName, deliveryMethod);
            });

            sheetBinding.layoutPharmacyList.addView(itemBinding.getRoot());
        }
        bottomSheetDialog.show();
    }

    private void uploadPrescriptionToFirebase(Uri imageUri, String pharmacyId, String pharmacyName, String deliveryMethod) {
        if (loadingDialog == null) setupLoadingDialog();
        loadingDialog.show();

        String userId = auth.getCurrentUser().getUid();
        String orderId = "PRES-" + System.currentTimeMillis();

        StorageReference storageRef = FirebaseStorage.getInstance().getReference()
                .child("prescriptions/" + userId + "/" + orderId + ".jpg");

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

        db.collection("orders").document(orderId)
                .set(newOrder)
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