package lk.punsisi.medifindtest.activity;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import lk.payhere.androidsdk.PHConfigs;
import lk.payhere.androidsdk.PHConstants;
import lk.payhere.androidsdk.PHMainActivity;
import lk.payhere.androidsdk.PHResponse;
import lk.payhere.androidsdk.model.InitRequest;
import lk.payhere.androidsdk.model.StatusResponse;
import lk.punsisi.medifindtest.R;
import lk.punsisi.medifindtest.model.CartItem;
import lk.punsisi.medifindtest.model.DeliveryAddress;
import lk.punsisi.medifindtest.model.Order;
import lk.punsisi.medifindtest.room.AppDatabase;

public class CheckoutActivity extends AppCompatActivity {

    private TextInputEditText etPhone, etAddress1, etAddress2, etCity, etPostal;
    private MaterialSwitch switchDefaultAddress;
    private TextView tvSubtotal, tvDelivery, tvTotal;
    private MaterialButton btnPayNow;

    private String singlePharmacyAddress = null;
    private double subtotal = 0.0;
    private double deliveryFee = 300.0; // Default to outside 5km

    private String defPhone = "", defAdd1 = "", defAdd2 = "", defCity = "", defPostal = "";

    private Dialog loadingDialog;
    private ExecutorService executorService;
    private String currentOrderId;

    private String checkoutPrescriptionUrl = null;
    private ActivityResultLauncher<Void> cameraLauncher;


    private boolean isExistingOrder = false;
    private String existingOrderId = null;


    private final Map<String, String[]> cartPharmacyDetails = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkout);

        isExistingOrder = getIntent().getBooleanExtra("IS_EXISTING_ORDER", false);

        if (isExistingOrder) {
            subtotal = getIntent().getDoubleExtra("CART_TOTAL", 0.0);
            existingOrderId = getIntent().getStringExtra("EXISTING_ORDER_ID");
        } else {
            subtotal = getIntent().getDoubleExtra("CART_TOTAL", 0.0);
        }

        initViews();
        setupListeners();
        setupLoadingDialog();
        checkFirebaseForDefaultAddress();
        updatePricingUI();
        loadDeliveryFeeData();

        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicturePreview(),
                bitmap -> {
                    if (bitmap != null) {
                        uploadPrescriptionForCheckout(bitmap);
                    }
                }
        );
    }

    private void initViews() {
        etPhone = findViewById(R.id.et_phone);
        etAddress1 = findViewById(R.id.et_address1);
        etAddress2 = findViewById(R.id.et_address2);
        etCity = findViewById(R.id.et_city);
        etPostal = findViewById(R.id.et_postal);

        switchDefaultAddress = findViewById(R.id.switch_default_address);

        tvSubtotal = findViewById(R.id.tv_checkout_subtotal);
        tvDelivery = findViewById(R.id.tv_checkout_delivery);
        tvTotal = findViewById(R.id.tv_checkout_total);
        btnPayNow = findViewById(R.id.btn_pay_now);

        findViewById(R.id.toolbar_checkout).setOnClickListener(v -> finish());
    }

    private void setupListeners() {

        etCity.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // start calculation dynamically  they type their city
                calculateDynamicFee();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });


        switchDefaultAddress.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                etPhone.setText(defPhone);
                etAddress1.setText(defAdd1);
                etAddress2.setText(defAdd2);
                etCity.setText(defCity);
                etPostal.setText(defPostal);

                etPhone.setEnabled(false);
                etAddress1.setEnabled(false);
                etAddress2.setEnabled(false);
                etCity.setEnabled(false);
                etPostal.setEnabled(false);

                calculateDynamicFee();

            } else {

                etPhone.setText("");
                etAddress1.setText("");
                etAddress2.setText("");
                etCity.setText("");
                etPostal.setText("");

                etPhone.setEnabled(true);
                etAddress1.setEnabled(true);
                etAddress2.setEnabled(true);
                etCity.setEnabled(true);
                etPostal.setEnabled(true);

                calculateDynamicFee();
            }
        });


        btnPayNow.setOnClickListener(v -> {

            if (etPhone.getText().toString().isEmpty() || etAddress1.getText().toString().isEmpty() || etCity.getText().toString().isEmpty()) {
                Toast.makeText(this, "Please fill all required billing address fields", Toast.LENGTH_SHORT).show();
                return;
            }

            if (isExistingOrder) {
                loadPayHereSandbox();
            } else {
                loadingDialog.show();
                verifyCartInventory();
            }
        });
    }

    private void updatePricingUI() {

        if (isExistingOrder) {
            tvDelivery.setVisibility(View.GONE);
            ((View) tvDelivery.getParent()).setVisibility(View.GONE);
            tvTotal.setText(String.format("Rs. %.2f", subtotal));
        } else {
            double grandTotal = subtotal + deliveryFee;
            tvSubtotal.setText(String.format("Rs. %.2f", subtotal));
            tvDelivery.setText(String.format("Rs. %.2f", deliveryFee));
            tvTotal.setText(String.format("Rs. %.2f", grandTotal));
        }
    }

    private void loadDeliveryFeeData() {
        if (isExistingOrder) return;

        executorService.execute(() -> {
            List<CartItem> cartItems = AppDatabase.getDatabase(CheckoutActivity.this).cartDao().getActiveCartItems();
            if (cartItems.isEmpty()) return;

            FirebaseFirestore db = FirebaseFirestore.getInstance();
            List<Task<DocumentSnapshot>> tasks = new ArrayList<>();

            for (CartItem item : cartItems) {
                tasks.add(db.collection("medicines").document(item.getMedicineId()).get());
            }

            Tasks.whenAllSuccess(tasks).addOnSuccessListener(results -> {
                String commonPharmacyId = null;
                boolean isSinglePharmacy = true;

                for (Object obj : results) {
                    DocumentSnapshot doc = (DocumentSnapshot) obj;
                    String pId = doc.getString("pharmacistId");

                    if (commonPharmacyId == null) {
                        commonPharmacyId = pId;
                    } else if (!commonPharmacyId.equals(pId)) {
                        isSinglePharmacy = false;
                        break;
                    }
                }


                if (isSinglePharmacy && commonPharmacyId != null) {
                    db.collection("pharmacist_requests").document(commonPharmacyId).get().addOnSuccessListener(pDoc -> {
                        singlePharmacyAddress = pDoc.getString("pharmacyAddress");
                        calculateDynamicFee();
                    });
                } else {
                    //its mixed
                    singlePharmacyAddress = null;
                }
            });
        });
    }

    private void calculateDynamicFee() {
        if (isExistingOrder) return;

        if (singlePharmacyAddress == null) {
            deliveryFee = 300.0;
            updatePricingUI();
            return;
        }

        String userAddressStr = etAddress1.getText().toString().trim() + ", " + etCity.getText().toString().trim();

        if (etAddress1.getText().toString().isEmpty() || etCity.getText().toString().isEmpty()) {
            deliveryFee = 300.0;
            updatePricingUI();
            return;
        }

        //running background for distance cal
        executorService.execute(() -> {
            Geocoder geocoder = new Geocoder(CheckoutActivity.this, Locale.getDefault());
            try {
                List<Address> pAddrs = geocoder.getFromLocationName(singlePharmacyAddress + ", Sri Lanka", 1);
                List<Address> uAddrs = geocoder.getFromLocationName(userAddressStr + ", Sri Lanka", 1);

                if (pAddrs != null && !pAddrs.isEmpty() && uAddrs != null && !uAddrs.isEmpty()) {
                    Address pLoc = pAddrs.get(0);
                    Address uLoc = uAddrs.get(0);

                    // Calculate distance
                    float[] results = new float[1];
                    Location.distanceBetween(
                            pLoc.getLatitude(), pLoc.getLongitude(),
                            uLoc.getLatitude(), uLoc.getLongitude(),
                            results
                    );

                    float distanceInMeters = results[0];

                    //back to main thread
                    runOnUiThread(() -> {
                        if (distanceInMeters <= 5000) { // 5000 meters = 5km
                            deliveryFee = 100.0;
                        } else {
                            deliveryFee = 300.0;
                        }
                        updatePricingUI();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();

                runOnUiThread(() -> {
                    deliveryFee = 300.0;
                    updatePricingUI();
                });
            }
        });
    }

    private void checkFirebaseForDefaultAddress() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        FirebaseFirestore.getInstance().collection("users").document(user.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists() && documentSnapshot.contains("deliveryAddress")) {
                        Map<String, Object> addressMap = (Map<String, Object>) documentSnapshot.get("deliveryAddress");

                        if (addressMap != null && addressMap.containsKey("addressLine1")) {
                            switchDefaultAddress.setEnabled(true);
                            defPhone = addressMap.containsKey("phoneNumber") ? (String) addressMap.get("phoneNumber") : "";
                            defAdd1 = addressMap.containsKey("addressLine1") ? (String) addressMap.get("addressLine1") : "";
                            defAdd2 = addressMap.containsKey("addressLine2") ? (String) addressMap.get("addressLine2") : "";
                            defCity = addressMap.containsKey("homeTown") ? (String) addressMap.get("homeTown") : "";
                            defPostal = addressMap.containsKey("postalCode") ? (String) addressMap.get("postalCode") : "";


                        } else {
                            switchDefaultAddress.setEnabled(false);
                        }
                    } else {
                        switchDefaultAddress.setEnabled(false);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("Checkout", "Failed to fetch address", e);
                    switchDefaultAddress.setEnabled(false);
                });
    }

    private void setupLoadingDialog() {
        loadingDialog = new Dialog(this);
        loadingDialog.setContentView(R.layout.dialog_syncing);
        loadingDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        loadingDialog.setCancelable(false);

        TextView tvLoading = loadingDialog.findViewById(R.id.tv_dialog_text);
        if (tvLoading != null) tvLoading.setText("Processing...");

        executorService = java.util.concurrent.Executors.newSingleThreadExecutor();
    }

    private void verifyCartInventory() {
        executorService.execute(() -> {
            List<CartItem> cartItems = AppDatabase.getDatabase(this).cartDao().getActiveCartItems();

            runOnUiThread(() -> {
                if (cartItems.isEmpty()) {
                    loadingDialog.dismiss();
                    Toast.makeText(this, "Your cart is empty!", Toast.LENGTH_SHORT).show();
                    return;
                }
                checkLiveFirebaseData(cartItems);
            });
        });
    }

    private void checkLiveFirebaseData(List<CartItem> cartItems) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        List<String> errorMessages = new ArrayList<>();
        final int[] completedChecks = {0};
        final int totalItems = cartItems.size();

        for (CartItem item : cartItems) {
            db.collection("medicines").document(item.getMedicineId()).get()
                    .addOnSuccessListener(doc -> {
                        Boolean isDeleted = doc.getBoolean("deleted");
                        boolean productIsSoftDeleted = isDeleted != null && isDeleted;

                        if (!doc.exists() || productIsSoftDeleted) {
                            errorMessages.add("❌ '" + item.getName() + "' is no longer available.");
                        } else {
                            Double livePrice = doc.getDouble("price");
                            Long liveStockLong = doc.getLong("quantity");

                            double currentLivePrice = livePrice != null ? livePrice : 0.0;
                            int currentLiveStock = liveStockLong != null ? liveStockLong.intValue() : 0;

                            String pId = doc.getString("pharmacistId");
                            String pName = doc.getString("pharmacyName");
                            cartPharmacyDetails.put(item.getMedicineId(), new String[]{pId, pName});

                            if (item.getQuantity() > currentLiveStock) {
                                errorMessages.add("⚠️ '" + item.getName() + "': Only " + currentLiveStock + " left in stock.");
                            }
                            if (Math.abs(item.getPrice() - currentLivePrice) > 0.01) {
                                errorMessages.add("💰 '" + item.getName() + "': Price has changed to Rs. " + currentLivePrice + ".");
                            }
                        }

                        completedChecks[0]++;
                        evaluateVerificationResults(completedChecks[0], totalItems, errorMessages);
                    })
                    .addOnFailureListener(e -> {
                        errorMessages.add("🔌 Failed to verify '" + item.getName() + "'. Check connection.");
                        completedChecks[0]++;
                        evaluateVerificationResults(completedChecks[0], totalItems, errorMessages);
                    });
        }
    }

    private void evaluateVerificationResults(int current, int total, List<String> errorMessages) {
        if (current == total) {
            if (errorMessages.isEmpty()) {
                verifyCartAndCheckout();
            } else {
                loadingDialog.dismiss();
                showCartIssuesDialog(errorMessages);
            }
        }
    }

    private void showCartIssuesDialog(List<String> errorMessages) {
        StringBuilder errorsText = new StringBuilder("We found some issues with your cart:\n\n");
        for (String error : errorMessages) {
            errorsText.append(error).append("\n\n");
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Action Required")
                .setMessage(errorsText.toString().trim())
                .setIcon(R.drawable.baseline_medication_24)
                .setPositiveButton("Go to Cart", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    private void verifyCartAndCheckout() {
        executorService.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(this);
            List<String> restrictedItems = db.cartDao().getRestrictedItemNames();

            runOnUiThread(() -> {
                loadingDialog.dismiss();
                if (!restrictedItems.isEmpty() && checkoutPrescriptionUrl == null) {
                    showPrescriptionRequiredDialog(restrictedItems);
                } else {
                    loadPayHereSandbox();
                }
            });
        });
    }

    private void showPrescriptionRequiredDialog(List<String> restrictedItems) {
        String itemNames = android.text.TextUtils.join(", ", restrictedItems);
        String dynamicMessage = "Your cart contains restricted medicines: " + itemNames + ".\n\nA valid doctor's prescription is required. Please upload a photo to continue.";

        new MaterialAlertDialogBuilder(this)
                .setTitle("Prescription Required \u26A0\uFE0F")
                .setMessage(dynamicMessage)
                .setPositiveButton("Take Photo", (dialog, which) -> cameraLauncher.launch(null))
                .setNegativeButton("Cancel", null)
                .setCancelable(false)
                .show();
    }

    private void uploadPrescriptionForCheckout(android.graphics.Bitmap bitmap) {
        loadingDialog.show();
        TextView tvLoading = loadingDialog.findViewById(R.id.tv_dialog_text);
        if (tvLoading != null) tvLoading.setText("Uploading Prescription...");

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        String tempImageId = "CHECKOUT-" + System.currentTimeMillis();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
        byte[] data = baos.toByteArray();

        StorageReference storageRef = FirebaseStorage.getInstance().getReference()
                        .child("prescriptions/" + userId + "/" + tempImageId + ".jpg");

        storageRef.putBytes(data)
                .addOnSuccessListener(taskSnapshot -> storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                    loadingDialog.dismiss();
                    checkoutPrescriptionUrl = uri.toString();
                    Toast.makeText(this, "Prescription attached! Resuming checkout...", Toast.LENGTH_SHORT).show();
                    loadPayHereSandbox();
                }))
                .addOnFailureListener(e -> {
                    loadingDialog.dismiss();
                    Toast.makeText(this, "Upload failed. Please try again.", Toast.LENGTH_SHORT).show();
                });
    }

    private void loadPayHereSandbox() {
        InitRequest req = new InitRequest();

        req.setMerchantId("1222135");
        req.setMerchantSecret("MTM0NjY5MTI3NzE5NzQ2NzEyNDgxMDM2MjU4NzMyMjE4NjMzMDgw");
        req.setCurrency("LKR");

        double finalAmount = isExistingOrder ? subtotal : (subtotal + deliveryFee);
        req.setAmount(finalAmount);

        currentOrderId = isExistingOrder ? existingOrderId : ("ORDER-" + System.currentTimeMillis());
        req.setOrderId(currentOrderId);
        req.setItemsDescription("MediFind Pharmacy Order");

        req.getCustomer().setFirstName("Valued");
        req.getCustomer().setLastName("Customer");
        req.getCustomer().setEmail("customer@medifind.lk");
        req.getCustomer().setPhone(etPhone.getText().toString());
        req.getCustomer().getAddress().setAddress(etAddress1.getText().toString() + " " + etAddress2.getText().toString());
        req.getCustomer().getAddress().setCity(etCity.getText().toString());
        req.getCustomer().getAddress().setCountry("Sri Lanka");

        PHConfigs.setBaseUrl(PHConfigs.SANDBOX_URL);

        Intent intent = new Intent(this, PHMainActivity.class);
        intent.putExtra(PHConstants.INTENT_EXTRA_DATA, req);
        payHereLauncher.launch(intent);
    }

    private final ActivityResultLauncher<Intent> payHereLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Intent data = result.getData();
                    if (data.hasExtra(PHConstants.INTENT_EXTRA_RESULT)) {
                        PHResponse<StatusResponse> response =
                                (PHResponse<StatusResponse>) data.getSerializableExtra(PHConstants.INTENT_EXTRA_RESULT);

                        if (response.isSuccess()) {
                            Toast.makeText(this, "Payment Successful!", Toast.LENGTH_LONG).show();

                            if (isExistingOrder) {
                                processExistingOrderPayment(currentOrderId);
                            } else {
                                processSuccessfulOrder(currentOrderId);
                            }
                        } else {
                            Toast.makeText(this, "Payment Failed: " + response.toString(), Toast.LENGTH_LONG).show();
                        }
                    }
                } else if (result.getResultCode() == RESULT_CANCELED) {
                    Toast.makeText(this, "Payment Canceled", Toast.LENGTH_SHORT).show();
                }
            });


    private void processExistingOrderPayment(String orderId) {
        if (loadingDialog != null) {
            loadingDialog.show();
            TextView tvLoading = loadingDialog.findViewById(R.id.tv_dialog_text);
            if (tvLoading != null) tvLoading.setText("Updating Order...");
        }

        DeliveryAddress deliveryAddress = DeliveryAddress.builder()
                .phoneNumber(etPhone.getText().toString())
                .addressLine1(etAddress1.getText().toString())
                .addressLine2(etAddress2.getText().toString())
                .homeTown(etCity.getText().toString())
                .postalCode(etPostal.getText().toString())
                .build();

        FirebaseFirestore.getInstance().collection("orders").document(orderId)
                .update(
                        "paid", true,
                        "deliveryAddress", deliveryAddress
                )
                .addOnSuccessListener(aVoid -> {
                    if (loadingDialog != null) loadingDialog.dismiss();
                    Intent successIntent = new Intent(CheckoutActivity.this, OrderSuccessActivity.class);
                    successIntent.putExtra("FINAL_ORDER_ID", orderId);
                    startActivity(successIntent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    if (loadingDialog != null) loadingDialog.dismiss();
                    Toast.makeText(this, "Payment succeeded, but update failed. Contact support.", Toast.LENGTH_LONG).show();
                });
    }

    private void processSuccessfulOrder(String orderId) {
        loadingDialog.show();
        TextView tvLoading = loadingDialog.findViewById(R.id.tv_dialog_text);
        if (tvLoading != null) tvLoading.setText("Finalizing Order...");

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        String userId = user.getUid();

        executorService.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(this);
            List<CartItem> finalItems = db.cartDao().getActiveCartItems();

            String commonPharmacyId = null;
            String commonPharmacyName = null;
            boolean isSinglePharmacy = true;

            if (!finalItems.isEmpty()) {
                String firstMedId = finalItems.get(0).getMedicineId();
                if (cartPharmacyDetails.containsKey(firstMedId)) {
                    commonPharmacyId = cartPharmacyDetails.get(firstMedId)[0];
                    commonPharmacyName = cartPharmacyDetails.get(firstMedId)[1];
                }

                for (CartItem item : finalItems) {
                    String[] details = cartPharmacyDetails.get(item.getMedicineId());
                    String currentPId = details != null ? details[0] : null;

                    if (currentPId == null || !currentPId.equals(commonPharmacyId)) {
                        isSinglePharmacy = false;
                        break;
                    }
                }
            }

            if (!isSinglePharmacy) {
                commonPharmacyId = null;
                commonPharmacyName = null;
            }

            DeliveryAddress deliveryAddress = DeliveryAddress.builder()
                    .phoneNumber(etPhone.getText().toString())
                    .addressLine1(etAddress1.getText().toString())
                    .addressLine2(etAddress2.getText().toString())
                    .homeTown(etCity.getText().toString())
                    .postalCode(etPostal.getText().toString())
                    .build();

            List<Map<String, Object>> itemsList = new ArrayList<>();
            for (CartItem item : finalItems) {
                Map<String, Object> itemMap = new HashMap<>();
                itemMap.put("medicineId", item.getMedicineId());
                itemMap.put("name", item.getName());
                itemMap.put("price", item.getPrice());
                itemMap.put("quantity", item.getQuantity());
                itemMap.put("imageUrl", item.getImageUrl());
                itemsList.add(itemMap);
            }

            Order newOrder = Order.builder()
                    .orderId(orderId)
                    .userId(userId)
                    .status("Pending")
                    .paid(true)
                    .grandTotal(subtotal + deliveryFee)
                    .deliveryMethod("Online")
                    .prescriptionUrl(checkoutPrescriptionUrl)
                    .deliveryAddress(deliveryAddress)
                    .items(itemsList)
                    .pharmacyId(commonPharmacyId)
                    .pharmacyName(commonPharmacyName)
                    .build();

            FirebaseFirestore.getInstance().collection("orders").document(orderId)
                    .set(newOrder)
                    .addOnSuccessListener(aVoid -> {
                        executorService.execute(() -> {
                            db.cartDao().clearCart();
                            updateInventoryAndClearCart(userId, finalItems);

                            runOnUiThread(() -> {
                                loadingDialog.dismiss();
                                Intent successIntent = new Intent(CheckoutActivity.this, OrderSuccessActivity.class);
                                successIntent.putExtra("FINAL_ORDER_ID", orderId);
                                startActivity(successIntent);
                                finish();
                            });
                        });
                    })
                    .addOnFailureListener(e -> {
                        runOnUiThread(() -> {
                            loadingDialog.dismiss();
                            Toast.makeText(CheckoutActivity.this, "Payment succeeded, but order saving failed. Please contact support.", Toast.LENGTH_LONG).show();
                        });
                    });
        });
    }

    private void updateInventoryAndClearCart(String userId, List<CartItem> items) {
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        WriteBatch batch = firestore.batch();

        for (CartItem item : items) {
            DocumentReference cartRef = firestore.collection("users")
                    .document(userId)
                    .collection("cart")
                    .document(item.getMedicineId());
            batch.delete(cartRef);

            DocumentReference medicineRef = firestore.collection("medicines")
                    .document(item.getMedicineId());

            batch.update(medicineRef,
                    "quantity", FieldValue.increment(-item.getQuantity()),
                    "salesCount", FieldValue.increment(item.getQuantity()),
                    "lastUpdated", System.currentTimeMillis()
            );
        }

        batch.commit()
                .addOnSuccessListener(aVoid -> Log.d("Checkout", "Inventory updated and cart cleared!"))
                .addOnFailureListener(e -> Log.e("Checkout", "Failed to update inventory", e));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) executorService.shutdown();
    }
}