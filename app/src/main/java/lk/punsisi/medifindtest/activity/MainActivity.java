package lk.punsisi.medifindtest.activity;


import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import lk.punsisi.medifindtest.R;
import lk.punsisi.medifindtest.databinding.ActivityMainBinding;
import lk.punsisi.medifindtest.databinding.SideNavHeaderBinding;
import lk.punsisi.medifindtest.fragment.AddMedicineFragment;
import lk.punsisi.medifindtest.fragment.HomeFragment;
import lk.punsisi.medifindtest.fragment.MapFragment;
import lk.punsisi.medifindtest.fragment.CartFragment;
import lk.punsisi.medifindtest.fragment.ProfileFragment;
import lk.punsisi.medifindtest.model.User;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener, NavigationBarView.OnItemSelectedListener {

    private ActivityMainBinding binding;

    private DrawerLayout drawerLayout;
    private MaterialToolbar toolbar;
    private NavigationView sidenavigationView;
    private BottomNavigationView bottomNavigationView;

    private SideNavHeaderBinding sideNavHeaderBinding;

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firebaseFirestore;

    private android.app.Dialog syncDialog;


    private ListenerRegistration roleSyncListener;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());

        setContentView(binding.getRoot());

        firebaseAuth = FirebaseAuth.getInstance();
        firebaseFirestore = FirebaseFirestore.getInstance();

        drawerLayout = binding.drawer;
        toolbar = binding.toolbar;
        sidenavigationView = binding.sideNavigationView;
        bottomNavigationView = binding.bottomNavigationView;

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.drawer_open, R.string.drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.getDrawerArrowDrawable().setColor(getResources().getColor(android.R.color.white, getTheme()));
        toggle.syncState();

        //        get back button
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    finish();
                }

            }
        });

        sidenavigationView.setNavigationItemSelectedListener(this);
        bottomNavigationView.setOnItemSelectedListener(this);


        if (savedInstanceState == null) {
            loadFragment(new HomeFragment());
            sidenavigationView.getMenu().findItem(R.id.side_nav_home).setChecked(true);
            bottomNavigationView.getMenu().findItem(R.id.bottom_nav_home).setChecked(true);
        }


        View headerView = sidenavigationView.getHeaderView(0);
        sideNavHeaderBinding = SideNavHeaderBinding.bind(headerView);

        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser != null) {
            String userId = currentUser.getUid();

            firebaseFirestore.collection("users").document(userId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            User user = documentSnapshot.toObject(User.class);
                            sideNavHeaderBinding.headerUserName.setText(user.getName());
                            sideNavHeaderBinding.headerUserEmail.setText(user.getEmail());
                            sideNavHeaderBinding.headerUserRole.setText("Role : " + user.getRole());

                            if (user.getRole().equals("pharmacist")){
                                sideNavHeaderBinding.headerUserRole.
                                        setBackgroundTintList
                                                (ColorStateList.valueOf(getColor( R.color.md_theme_tertiaryFixed_mediumContrast)));
                            }

                            String profileImageUrl = documentSnapshot.getString("profileImage");
                            if (profileImageUrl != null && !profileImageUrl.isEmpty()) {
                                Glide.with(MainActivity.this)
                                        .load(profileImageUrl)
                                        .placeholder(R.drawable.baseline_person_24)
                                        .into(sideNavHeaderBinding.headerProfilePic);
                            }

                        } else {
                            Log.e("Firestore", "document does not exist");
                        }
                    }).addOnFailureListener(e -> {
                        Log.e("Firestore", "Error getting user data" + e.getMessage());
                    });

            sidenavigationView.getMenu().findItem(R.id.side_nav_login).setVisible(false);
            sidenavigationView.getMenu().findItem(R.id.side_nav_logout).setVisible(true);

            sideNavHeaderBinding.headerProfilePic.setOnClickListener(v -> {
                loadFragment(new ProfileFragment());
            });


        }

        // 1. Prepare the Global Loading Dialog
        // Notice we use 'this' instead of 'requireContext()' in an Activity!
        syncDialog = new android.app.Dialog(this);
        syncDialog.setContentView(R.layout.dialog_syncing);
        syncDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        syncDialog.setCancelable(false);

        // 2. Watch the Background Worker globally!
        // Notice we use 'this' instead of 'getViewLifecycleOwner()' in an Activity!
        androidx.work.WorkManager.getInstance(this)
                .getWorkInfosForUniqueWorkLiveData("CartSyncJob")
                .observe(this, workInfos -> {
                    if (workInfos == null || workInfos.isEmpty()) return;

                    androidx.work.WorkInfo workInfo = workInfos.get(0);

                    if (workInfo.getState() == androidx.work.WorkInfo.State.RUNNING) {
                        if (!syncDialog.isShowing()) syncDialog.show();

                    } else if (workInfo.getState() == androidx.work.WorkInfo.State.SUCCEEDED) {
                        if (syncDialog.isShowing()) {
                            syncDialog.dismiss();
                            android.widget.Toast.makeText(this, "Cart synced securely!", android.widget.Toast.LENGTH_SHORT).show();
                        }

                    } else if (workInfo.getState() == androidx.work.WorkInfo.State.FAILED || workInfo.getState() == androidx.work.WorkInfo.State.CANCELLED) {
                        if (syncDialog.isShowing()) syncDialog.dismiss();
                    }
                });

        unlockPharmacistToolsIfAuthorized();

        generateAndSaveFCMToken();


        startSilentRoleSync();

    }

    // Call this method inside your onViewCreated() or onCreate()
    private void generateAndSaveFCMToken() {
        com.google.firebase.auth.FirebaseUser currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        android.util.Log.w("FCM", "Fetching FCM registration token failed", task.getException());
                        return;
                    }

                    // Get new FCM registration token
                    String token = task.getResult();

                    // Save it to the users collection in Firestore
                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(currentUser.getUid())
                            .update("fcmToken", token)
                            .addOnSuccessListener(aVoid -> android.util.Log.d("FCM", "Token saved successfully!"))
                            .addOnFailureListener(e -> android.util.Log.e("FCM", "Failed to save token: " + e.getMessage()));
                });
    }



    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {

        int itemId = item.getItemId();

        if (itemId == R.id.side_nav_categories) {
            Intent intent = new Intent(MainActivity.this, AllCategoriesActivity.class);
            startActivity(intent);
            if (drawerLayout.isDrawerOpen(GravityCompat.START))
                drawerLayout.closeDrawer(GravityCompat.START);
            return false;

        } else if (itemId == R.id.side_nav_medicine) {
            Intent intent = new Intent(MainActivity.this, MedicinesListActivity.class);
            intent.putExtra("IS_ALL_MEDICINES", true);
            intent.putExtra("CATEGORY_NAME", "All Medicines");
            startActivity(intent);
            if (drawerLayout.isDrawerOpen(GravityCompat.START))
                drawerLayout.closeDrawer(GravityCompat.START);
            return false;

        } else if (itemId == R.id.side_nav_order_history) {
            Intent intent = new Intent(MainActivity.this, OrderHistoryActivity.class);
            startActivity(intent);
            if (drawerLayout.isDrawerOpen(GravityCompat.START))
                drawerLayout.closeDrawer(GravityCompat.START);
            return false;

        } else if (itemId == R.id.side_nav_login) {
            Intent intent = new Intent(MainActivity.this, RegistrationActivity.class);
            startActivity(intent);
            if (drawerLayout.isDrawerOpen(GravityCompat.START))
                drawerLayout.closeDrawer(GravityCompat.START);
            return false;

        } else if (itemId == R.id.side_nav_logout) {
            firebaseAuth.signOut();
            Intent intent = new Intent(MainActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            return false;
        }

        Menu bottomNavMenu = bottomNavigationView.getMenu();
        Menu sideNavMenu = sidenavigationView.getMenu();


        for (int i = 0; i < bottomNavMenu.size(); i++) {
            bottomNavMenu.getItem(i).setChecked(false);
        }
        for (int i = 0; i < sideNavMenu.size(); i++) {
            sideNavMenu.getItem(i).setChecked(false);
        }

        if (itemId == R.id.bottom_nav_home || itemId == R.id.side_nav_home) {
            loadFragment(new HomeFragment());
            bottomNavMenu.findItem(R.id.bottom_nav_home).setChecked(true);
            sideNavMenu.findItem(R.id.side_nav_home).setChecked(true);
            binding.title.setText("MediFind");

        } else if (itemId == R.id.bottom_nav_map || itemId == R.id.side_nav_map) {
            loadFragment(new MapFragment());
            bottomNavMenu.findItem(R.id.bottom_nav_map).setChecked(true);
            sideNavMenu.findItem(R.id.side_nav_map).setChecked(true);
            binding.title.setText("Google Map");

        } else if (itemId == R.id.bottom_nav_orders || itemId == R.id.side_nav_orders) {
            loadFragment(new CartFragment());
            bottomNavMenu.findItem(R.id.bottom_nav_orders).setChecked(true);
            sideNavMenu.findItem(R.id.side_nav_orders).setChecked(true);
            binding.title.setText("Cart");

        } else if (itemId == R.id.bottom_nav_profile || itemId == R.id.side_nav_profile_settings) {
            loadFragment(new ProfileFragment());
            bottomNavMenu.findItem(R.id.bottom_nav_profile).setChecked(true);
            sideNavMenu.findItem(R.id.side_nav_profile_settings).setChecked(true);
            binding.title.setText("Profile");

        } else if (itemId == R.id.side_nav_add_medicine) {
            loadFragment(new AddMedicineFragment());
            sideNavMenu.findItem(R.id.side_nav_add_medicine).setChecked(true);
        }

        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        }

        return true;
    }

    private void loadFragment(Fragment fragment) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transition = fragmentManager.beginTransaction();
        //thiyana fragment eka ain karala auth fragment eka load wenna oni nisa thamai repalce eka damme nathAM DANNE ADD
        transition.replace(R.id.fragment_container, fragment);
        transition.commit();

    }


    private void unlockPharmacistToolsIfAuthorized() {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) return;

        firebaseFirestore.collection("users")
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String role = documentSnapshot.getString("role");
                        if (role == null) role = "user";

                        if (role.equals("pharmacist") || role.equals("admin")) {
                            showPharmacistMenu();
                        }
                    }
                })
                .addOnFailureListener(e -> Log.e("MainActivity", "Failed to check user role", e));
    }

    private void showPharmacistMenu() {
        NavigationView navigationView = binding.sideNavigationView;
        Menu menu = navigationView.getMenu();

        menu.findItem(R.id.side_nav_manage_inventary).setVisible(true);
        menu.findItem(R.id.side_nav_add_medicine).setVisible(true);
        menu.findItem(R.id.side_nav_prescription).setVisible(true);
    }


    // ==========================================
    // --- BACKGROUND ROLE SYNC ---
    // ==========================================
    private void startSilentRoleSync() {
        com.google.firebase.auth.FirebaseUser currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        // Attach a real-time listener to the user's profile document
        roleSyncListener = FirebaseFirestore.getInstance()
                .collection("users")
                .document(currentUser.getUid())
                .addSnapshotListener((documentSnapshot, e) -> {
                    if (e != null || documentSnapshot == null || !documentSnapshot.exists()) return;

                    // 1. Grab the live role from Firebase
                    String liveRole = documentSnapshot.getString("role");
                    if (liveRole == null) liveRole = "user";

                    // 2. Check what is currently saved on the phone
                    android.content.SharedPreferences prefs = getSharedPreferences("MediFindPrefs", android.content.Context.MODE_PRIVATE);
                    String localRole = prefs.getString("USER_ROLE", "user");

                    // 3. IF IT CHANGED, UPDATE THE PHONE!
                    if (!liveRole.equals(localRole)) {
                        prefs.edit().putString("USER_ROLE", liveRole).apply();

                        // 4. Update the UI instantly!
                        if (liveRole.equals("pharmacist") || liveRole.equals("admin")) {
                            showPharmacistMenu(); // Unlocks the side drawer tools!
                            android.widget.Toast.makeText(this, "Pharmacist Access Granted! \uD83C\uDF89", android.widget.Toast.LENGTH_LONG).show();
                        } else {
                            // If they were demoted, you could hide the menu here too!
                            hidePharmacistMenu();
                        }
                    }
                });
    }

    // Helper to hide the menu if they get demoted back to a normal user
    private void hidePharmacistMenu() {
        NavigationView navigationView = findViewById(R.id.side_navigation_view);
        Menu menu = navigationView.getMenu();
        menu.findItem(R.id.side_nav_manage_inventary).setVisible(false);
        menu.findItem(R.id.side_nav_add_medicine).setVisible(false);
        menu.findItem(R.id.side_nav_prescription).setVisible(false);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up the dialog to prevent memory leaks if the app closes mid-sync!
        if (syncDialog != null && syncDialog.isShowing()) {
            syncDialog.dismiss();
        }

        if (roleSyncListener != null) {
            roleSyncListener.remove();
        }
    }
}