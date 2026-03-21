package lk.punsisi.medifindtest.fragment;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lk.punsisi.medifindtest.R;
import lk.punsisi.medifindtest.databinding.FragmentMapBinding;
import lk.punsisi.medifindtest.helper.MapHelper;

public class MapFragment extends Fragment implements OnMapReadyCallback {

    private FragmentMapBinding binding;
    private GoogleMap mMap;
    private FirebaseFirestore db;
    private ExecutorService executorService;
    private FusedLocationProviderClient fusedLocationClient;

    // The Permission Launcher MUST be declared here at the top
    private ActivityResultLauncher<String> requestPermissionLauncher;

    private android.location.Location currentUserLocation;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize the permission launcher in onCreate
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        enableUserLocationAndZoom();
                    } else {
                        Toast.makeText(requireContext(), "Location permission denied. Showing default map.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentMapBinding.inflate(inflater, container, false);
        db = FirebaseFirestore.getInstance();
        executorService = Executors.newSingleThreadExecutor();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.fabBackMap.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());

        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.google_map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        try {
            // Apply the custom MediFind theme!
            boolean success = mMap.setMapStyle(
                    com.google.android.gms.maps.model.MapStyleOptions.loadRawResourceStyle(
                            requireContext(), R.raw.map_style));
            if (!success) android.util.Log.e("Maps", "Style parsing failed.");
        } catch (android.content.res.Resources.NotFoundException e) {
            android.util.Log.e("Maps", "Can't find style. Error: ", e);
        }

        // 1. Map UI Settings
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setMapToolbarEnabled(false);
        // Turns on live traffic lines across the whole map!
        mMap.setTrafficEnabled(true);

        // 2. Restrict to Sri Lanka bounds
        LatLng southWest = new LatLng(5.8, 79.5);
        LatLng northEast = new LatLng(9.9, 82.0);
        LatLngBounds sriLankaBounds = new LatLngBounds(southWest, northEast);
        mMap.setLatLngBoundsForCameraTarget(sriLankaBounds);
        mMap.setMinZoomPreference(7.0f);

        // Set a default center just in case location is denied
        LatLng centerSriLanka = new LatLng(7.8731, 80.7718);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(centerSriLanka, 7.5f));

        mMap.setOnMarkerClickListener(marker -> {
            showPharmacyBottomSheet(marker);
            return true;
        });

        loadPharmaciesOntoMap();

        // 👉 3. Check for Location Permissions!
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // Already have permission! Turn on the blue dot.
            enableUserLocationAndZoom();
        } else {
            // Ask the user for permission
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    // This method suppresses the Android Studio red error because we already checked for permission above!
    @SuppressWarnings("MissingPermission")
    private void enableUserLocationAndZoom() {
        if (mMap == null) return;

        // Turn on the Blue Dot and the "Jump to me" target button
        mMap.setMyLocationEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);

        // Grab the user's exact GPS location and smoothly animate the camera to them
        fusedLocationClient.getLastLocation().addOnSuccessListener(requireActivity(), location -> {
            if (location != null) {

                currentUserLocation = location;

                LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 13f), 1500, null);
            }
        });
    }

    private void loadPharmaciesOntoMap() {
        db.collection("pharmacist_requests")
                .whereEqualTo("status", "approved")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        String address = doc.getString("pharmacyAddress");
                        String name = doc.getString("pharmacyName");

                        if (address != null && !address.isEmpty()) {
                            executorService.execute(() -> {
                                LatLng coordinates = getCoordinatesFromAddress(address);
                                if (coordinates != null) {
                                    requireActivity().runOnUiThread(() -> {
                                        Marker marker = mMap.addMarker(new MarkerOptions()
                                                .position(coordinates)
                                                .title(name)
                                                .icon(MapHelper.getPngMarkerIcon(requireContext(), R.drawable.map_marker)));

                                        mMap.addCircle(new com.google.android.gms.maps.model.CircleOptions()
                                                .center(coordinates)
                                                .radius(5000) // 5000 meters = 5km delivery radius
                                                .strokeWidth(3f)
                                                .strokeColor(android.graphics.Color.parseColor("#00796B")) // Solid Teal border
                                                .fillColor(android.graphics.Color.parseColor("#2200796B"))); // Very transparent Teal fill
                                        if (marker != null) marker.setTag(doc);
                                    });
                                }
                            });
                        }
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(requireContext(), "Failed to load pharmacies", Toast.LENGTH_SHORT).show());
    }

    private LatLng getCoordinatesFromAddress(String addressString) {
        Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocationName(addressString + ", Sri Lanka", 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address location = addresses.get(0);
                return new LatLng(location.getLatitude(), location.getLongitude());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void showPharmacyBottomSheet(Marker marker) {
        QueryDocumentSnapshot doc = (QueryDocumentSnapshot) marker.getTag();
        if (doc == null) return;

        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View sheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_map_pharmacy, null);
        dialog.setContentView(sheetView);

        TextView tvName = sheetView.findViewById(R.id.tv_sheet_pharmacy_name);
        TextView tvAddress = sheetView.findViewById(R.id.tv_sheet_pharmacy_address);
        TextView tvDistance = sheetView.findViewById(R.id.tv_sheet_pharmacy_distance);
        TextView tvReviews = sheetView.findViewById(R.id.tv_sheet_pharmacy_reviews);
        com.google.android.material.imageview.ShapeableImageView ivProfile = sheetView.findViewById(R.id.iv_sheet_pharmacy_image);
        com.google.android.material.button.MaterialButton btnDirections = sheetView.findViewById(R.id.btn_sheet_get_directions);
        com.google.android.material.button.MaterialButton btnShop = sheetView.findViewById(R.id.btn_sheet_shop);
        com.google.android.material.button.MaterialButton btnCall = sheetView.findViewById(R.id.btn_sheet_call);

        tvName.setText(doc.getString("pharmacyName"));
        tvAddress.setText(doc.getString("pharmacyAddress"));

        // 👉 THE DISTANCE CALCULATOR
        LatLng destination = marker.getPosition();
        if (currentUserLocation != null) {
            float[] results = new float[1];
            android.location.Location.distanceBetween(
                    currentUserLocation.getLatitude(), currentUserLocation.getLongitude(),
                    destination.latitude, destination.longitude,
                    results
            );

            // Convert meters to Kilometers (e.g. 5200 meters -> 5.2 km)
            float distanceInKm = results[0] / 1000f;
            tvDistance.setText(String.format(Locale.getDefault(), "📍 %.1f km away", distanceInKm));
        } else {
            // If GPS is still loading or denied
            tvDistance.setVisibility(View.GONE);
        }

        String profileUrl = doc.getString("profileImage");
        if (profileUrl != null && !profileUrl.isEmpty()) {
            Glide.with(this).load(profileUrl).into(ivProfile);
        }

        btnDirections.setOnClickListener(v -> {
            dialog.dismiss();
//            LatLng destination = marker.getPosition();

            Uri gmmIntentUri = Uri.parse("google.navigation:q=" + destination.latitude + "," + destination.longitude);
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
            mapIntent.setPackage("com.google.android.apps.maps");

            if (mapIntent.resolveActivity(requireActivity().getPackageManager()) != null) {
                startActivity(mapIntent);
            } else {
                Toast.makeText(requireContext(), "Google Maps app is not installed!", Toast.LENGTH_SHORT).show();
            }
        });

        // 👉 THE TELEPHONE DIALER LAUNCHER
        btnCall.setOnClickListener(v -> {
            // Check your exact Firebase field name! Change "pharmacyPhone" if needed.
            String phoneNumber = doc.getString("telephone");

            if (phoneNumber != null && !phoneNumber.trim().isEmpty()) {
                // This Intent opens the phone dialer and pre-types the number
                Intent intent = new Intent(Intent.ACTION_DIAL);
                intent.setData(Uri.parse("tel:" + phoneNumber));
                startActivity(intent);
            } else {
                Toast.makeText(requireContext(), "Phone number not available for this pharmacy.", Toast.LENGTH_SHORT).show();
            }
        });

// 👉 THE "VISIT SHOP" LAUNCHER
        btnShop.setOnClickListener(v -> {
            // 1. Dismiss the bottom sheet so it's gone when they press back
            dialog.dismiss();

            // 2. Grab the specific Pharmacy's Data from the marker
            String pharmacyId = doc.getString("uid");
            if (pharmacyId == null) pharmacyId = doc.getId(); // Failsafe
            String pharmacyName = doc.getString("pharmacyName");

            // 3. Launch the Storefront Activity!
            Intent intent = new Intent(requireContext(), lk.punsisi.medifindtest.activity.MedicinesListActivity.class);
            intent.putExtra("IS_PHARMACY_STORE", true); // Tell the activity it's a storefront!
            intent.putExtra("PHARMACY_ID", pharmacyId);
            intent.putExtra("CATEGORY_NAME", pharmacyName);
            startActivity(intent);
        });


        // 👉 FETCH REVIEWS SAFELY
        // Grab the pharmacy ID (assuming it is saved as 'uid' or you can use doc.getId())
        String pharmacyId = doc.getString("uid");
        if (pharmacyId == null) pharmacyId = doc.getId(); // Fallback just in case!

        // Start listening for reviews
        com.google.firebase.firestore.ListenerRegistration reviewListener = db.collection("customer_feedback")
                .whereEqualTo("pharmacyId", pharmacyId)
                .addSnapshotListener((value, error) -> {
                    // Safe check if the dialog is still open
                    if (error != null) {
                        tvReviews.setText("Reviews unavailable");
                        return;
                    }
                    if (value != null) {
                        int count = value.size();
                        if (count == 0) {
                            tvReviews.setText("No reviews yet");
                        } else {
                            tvReviews.setText(count + " Reviews");
                        }
                    }
                });

        // 👉 THE MEMORY SAVER
        // When the user swipes the bottom sheet down to close it, turn off the Firebase listener!
        dialog.setOnDismissListener(d -> {
            if (reviewListener != null) {
                reviewListener.remove();
            }
        });

        dialog.show();
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