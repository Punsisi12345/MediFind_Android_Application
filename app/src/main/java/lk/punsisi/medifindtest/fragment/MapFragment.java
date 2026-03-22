package lk.punsisi.medifindtest.fragment;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
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
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lk.punsisi.medifindtest.R;
import lk.punsisi.medifindtest.activity.MedicinesListActivity;
import lk.punsisi.medifindtest.databinding.BottomSheetMapPharmacyBinding;
import lk.punsisi.medifindtest.databinding.FragmentMapBinding;
import lk.punsisi.medifindtest.helper.MapHelper;

public class MapFragment extends Fragment implements OnMapReadyCallback {

    private FragmentMapBinding binding;
    private GoogleMap mMap;
    private FirebaseFirestore db;
    private ExecutorService executorService;
    private FusedLocationProviderClient fusedLocationClient;

    private ActivityResultLauncher<String> requestPermissionLauncher;

    private android.location.Location currentUserLocation;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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

            //custom theme add
            boolean success = mMap.setMapStyle(
                    MapStyleOptions.loadRawResourceStyle(
                            requireContext(), R.raw.map_style));
            if (!success) Log.e("Maps", "Style parsing failed.");
        } catch (Resources.NotFoundException e) {
            Log.e("Maps", "Can't find style. Error: ", e);
        }

        //map features
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setMapToolbarEnabled(false);
        mMap.setTrafficEnabled(true);

        //only view sri lanka
        LatLng southWest = new LatLng(5.8, 79.5);
        LatLng northEast = new LatLng(9.9, 82.0);
        LatLngBounds sriLankaBounds = new LatLngBounds(southWest, northEast);
        mMap.setLatLngBoundsForCameraTarget(sriLankaBounds);
        mMap.setMinZoomPreference(7.0f);

        //no location set default
        LatLng centerSriLanka = new LatLng(7.8731, 80.7718);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(centerSriLanka, 7.5f));

        mMap.setOnMarkerClickListener(marker -> {
            showPharmacyBottomSheet(marker);
            return true;
        });

        loadPharmaciesOntoMap();

        //check location permission
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            //enable user location
            enableUserLocationAndZoom();
        } else {
            //ask for permission
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    @SuppressWarnings("MissingPermission")
    private void enableUserLocationAndZoom() {
        if (mMap == null) return;

        mMap.setMyLocationEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);

        //focus to user location
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

                                        //add cycle around the marker
                                        mMap.addCircle(new CircleOptions()
                                                .center(coordinates)
                                                .radius(5000) //5km delivery radius
                                                .strokeWidth(3f)
                                                .strokeColor(Color.parseColor("#00796B"))
                                                .fillColor(Color.parseColor("#2200796B")));
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

        BottomSheetMapPharmacyBinding sheetBinding = BottomSheetMapPharmacyBinding.inflate(getLayoutInflater());
        dialog.setContentView(sheetBinding.getRoot());

        sheetBinding.tvSheetPharmacyName.setText(doc.getString("pharmacyName"));
        sheetBinding.tvSheetPharmacyAddress.setText(doc.getString("pharmacyAddress"));

        //calculate distance
        LatLng destination = marker.getPosition();
        if (currentUserLocation != null) {
            float[] results = new float[1];
            Location.distanceBetween(
                    currentUserLocation.getLatitude(), currentUserLocation.getLongitude(),
                    destination.latitude, destination.longitude,
                    results
            );

            //M to KM
            float distanceInKm = results[0] / 1000f;
            sheetBinding.tvSheetPharmacyDistance.setText(String.format(Locale.getDefault(), "📍 %.1f km away", distanceInKm));
        } else {
            sheetBinding.tvSheetPharmacyDistance.setVisibility(View.GONE);
        }

        String profileUrl = doc.getString("profileImage");
        if (profileUrl != null && !profileUrl.isEmpty()) {
            Glide.with(this).load(profileUrl).into(sheetBinding.ivSheetPharmacyImage);
        }

        //phone map load for the direction
        sheetBinding.btnSheetGetDirections.setOnClickListener(v -> {
            dialog.dismiss();
            Uri gmmIntentUri = Uri.parse("google.navigation:q=" + destination.latitude + "," + destination.longitude);
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
            mapIntent.setPackage("com.google.android.apps.maps");

            if (mapIntent.resolveActivity(requireActivity().getPackageManager()) != null) {
                startActivity(mapIntent);
            } else {
                Toast.makeText(requireContext(), "Google Maps app is not installed!", Toast.LENGTH_SHORT).show();
            }
        });

        //open phone dialer
        sheetBinding.btnSheetCall.setOnClickListener(v -> {
            String phoneNumber = doc.getString("telephone");

            if (phoneNumber != null && !phoneNumber.trim().isEmpty()) {
                Intent intent = new Intent(Intent.ACTION_DIAL);
                intent.setData(Uri.parse("tel:" + phoneNumber));
                startActivity(intent);
            } else {
                Toast.makeText(requireContext(), "Phone number not available for this pharmacy.", Toast.LENGTH_SHORT).show();
            }
        });

        //visit shop
        sheetBinding.btnSheetShop.setOnClickListener(v -> {
            dialog.dismiss();

            String pharmacyId = doc.getString("uid");
            if (pharmacyId == null) pharmacyId = doc.getId(); // Failsafe
            String pharmacyName = doc.getString("pharmacyName");

            Intent intent = new Intent(requireContext(), MedicinesListActivity.class);
            intent.putExtra("IS_PHARMACY_STORE", true);
            intent.putExtra("PHARMACY_ID", pharmacyId);
            intent.putExtra("CATEGORY_NAME", pharmacyName);
            startActivity(intent);
        });

        //get reviews according to pharmacy
        String pharmacyIdForReview = doc.getString("uid");
        if (pharmacyIdForReview == null) pharmacyIdForReview = doc.getId();

        ListenerRegistration reviewListener = db.collection("customer_feedback")
                .whereEqualTo("pharmacyId", pharmacyIdForReview)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        sheetBinding.tvSheetPharmacyReviews.setText("Reviews unavailable");
                        return;
                    }
                    if (value != null) {
                        int count = value.size();
                        if (count == 0) {
                            sheetBinding.tvSheetPharmacyReviews.setText("No reviews yet");
                        } else {
                            sheetBinding.tvSheetPharmacyReviews.setText(count + " Reviews");
                        }
                    }
                });

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