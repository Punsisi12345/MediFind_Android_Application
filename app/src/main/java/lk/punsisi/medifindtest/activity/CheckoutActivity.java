package lk.punsisi.medifindtest.activity;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
import lk.punsisi.medifindtest.room.AppDatabase;

public class CheckoutActivity extends AppCompatActivity {

    private TextInputEditText etPhone, etAddress1, etAddress2, etCity, etPostal;
    private MaterialSwitch switchDefaultAddress;
    private TextView tvSubtotal, tvDelivery, tvTotal;
    private MaterialButton btnPayNow;

    private double subtotal = 0.0;
    private double deliveryFee = 300.0; // Default to outside 5km

    // Variables to store their default Firebase address
    private String defPhone = "", defAdd1 = "", defAdd2 = "", defCity = "", defPostal = "";

    private Dialog loadingDialog;
    private ExecutorService executorService;
    private String currentOrderId;

    // Phase 2: Prescription Variables
    private String checkoutPrescriptionUrl = null;
    private ActivityResultLauncher<Void> cameraLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkout);

        // 1. Get the Subtotal passed from the Cart Fragment!
        subtotal = getIntent().getDoubleExtra("CART_TOTAL", 0.0);

        initViews();
        setupListeners();
        setupLoadingDialog();
        checkFirebaseForDefaultAddress();
        updatePricingUI();

        // 2. Initialize Camera Launcher
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
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String cityTyped = s.toString().trim().toLowerCase();
                if (cityTyped.equals("ragama")) {
                    deliveryFee = 100.0; // Within 5km!
                } else {
                    deliveryFee = 300.0; // Outside 5km
                }
                updatePricingUI();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        switchDefaultAddress.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                etPhone.setText(defPhone);
                etAddress1.setText(defAdd1);
                etAddress2.setText(defAdd2);
                etCity.setText(defCity);
                etPostal.setText(defPostal);
            } else {
                etPhone.setText("");
                etAddress1.setText("");
                etAddress2.setText("");
                etCity.setText("");
                etPostal.setText("");
            }
        });

        btnPayNow.setOnClickListener(v -> {
            if (etPhone.getText().toString().isEmpty() || etAddress1.getText().toString().isEmpty() || etCity.getText().toString().isEmpty()) {
                Toast.makeText(this, "Please fill all required address fields", Toast.LENGTH_SHORT).show();
                return;
            }

            // Start Phase 1 Validation ONLY!
            loadingDialog.show();
            verifyCartInventory();
        });
    }

    private void updatePricingUI() {
        double grandTotal = subtotal + deliveryFee;
        tvSubtotal.setText(String.format("Rs. %.2f", subtotal));
        tvDelivery.setText(String.format("Rs. %.2f", deliveryFee));
        tvTotal.setText(String.format("Rs. %.2f", grandTotal));
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

                            etPhone.setEnabled(false);
                            etAddress1.setEnabled(false);
                            etAddress2.setEnabled(false);
                            etCity.setEnabled(false);
                            etPostal.setEnabled(false);
                        } else {
                            switchDefaultAddress.setEnabled(false);
                            etPhone.setEnabled(true);
                            etAddress1.setEnabled(true);
                            etAddress2.setEnabled(true);
                            etCity.setEnabled(true);
                            etPostal.setEnabled(true);
                        }
                    } else {
                        switchDefaultAddress.setEnabled(false);
                        etPhone.setEnabled(true);
                        etAddress1.setEnabled(true);
                        etAddress2.setEnabled(true);
                        etCity.setEnabled(true);
                        etPostal.setEnabled(true);
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
        if (tvLoading != null) tvLoading.setText("Verifying Inventory...");

        executorService = java.util.concurrent.Executors.newSingleThreadExecutor();
    }

    // ==========================================
    // --- PHASE 1: INVENTORY VERIFICATION ---
    // ==========================================
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
                            errorMessages.add("❌ '" + item.getName() + "' is no longer available. Please remove it from your cart.");
                        } else {
                            Double livePrice = doc.getDouble("price");
                            Long liveStockLong = doc.getLong("quantity");

                            double currentLivePrice = livePrice != null ? livePrice : 0.0;
                            int currentLiveStock = liveStockLong != null ? liveStockLong.intValue() : 0;

                            if (item.getQuantity() > currentLiveStock) {
                                errorMessages.add("⚠️ '" + item.getName() + "': Only " + currentLiveStock + " left in stock. Please lower your quantity.");
                            }
                            if (Math.abs(item.getPrice() - currentLivePrice) > 0.01) {
                                errorMessages.add("💰 '" + item.getName() + "': Price has changed to Rs. " + currentLivePrice + ". Please Remove the current Item and add again to cart.");
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
                // SUCCESS! Inventory is good. Chain directly to the Prescription Check!
                verifyCartAndCheckout();
            } else {
                // FAIL! Issues found.
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
                .setPositiveButton("Go to Cart", (dialog, which) -> {
                    finish();
                })
                .setCancelable(false)
                .show();
    }

    // ==========================================
    // --- PHASE 1.5: PRESCRIPTION VERIFICATION ---
    // ==========================================
    private void verifyCartAndCheckout() {
        executorService.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(this);
            // 👉 NEW: Get the actual list of names!
            List<String> restrictedItems = db.cartDao().getRestrictedItemNames();

            runOnUiThread(() -> {
                loadingDialog.dismiss();

                // If the list is not empty, we have restricted items!
                if (!restrictedItems.isEmpty() && checkoutPrescriptionUrl == null) {
                    // Pass the list of names into your dialog!
                    showPrescriptionRequiredDialog(restrictedItems);
                } else {
                    loadPayHereSandbox();
                }
            });
        });
    }

    private void showPrescriptionRequiredDialog(List<String> restrictedItems) {
        // Combine the names neatly: "Panadol, Amoxicillin"
        String itemNames = android.text.TextUtils.join(", ", restrictedItems);

        // Inject the names directly into the message!
        String dynamicMessage = "Your cart contains restricted medicines: " + itemNames + ".\n\n require a valid doctor's prescription to dispense these items.\n\nPlease upload a photo of your prescription to continue.";

        new MaterialAlertDialogBuilder(this)
                .setTitle("Prescription Required \u26A0\uFE0F")
                .setMessage(dynamicMessage)
                .setPositiveButton("Take Photo", (dialog, which) -> {
                    cameraLauncher.launch(null);
                })
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

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, baos);
        byte[] data = baos.toByteArray();

        com.google.firebase.storage.StorageReference storageRef =
                com.google.firebase.storage.FirebaseStorage.getInstance().getReference()
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

    // ==========================================
    // --- PHASE 2: PAYHERE INTEGRATION ---
    // ==========================================
    private void loadPayHereSandbox() {
        InitRequest req = new InitRequest();

        req.setMerchantId("1222135");
        req.setMerchantSecret("MTM0NjY5MTI3NzE5NzQ2NzEyNDgxMDM2MjU4NzMyMjE4NjMzMDgw");
        req.setCurrency("LKR");

        double grandTotal = subtotal + deliveryFee;
        req.setAmount(grandTotal);

        currentOrderId = "ORDER-" + System.currentTimeMillis();
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

    // ==========================================
    // --- PAYHERE RESULT LISTENER ---
    // ==========================================
    private final ActivityResultLauncher<Intent> payHereLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Intent data = result.getData();
                    if (data.hasExtra(PHConstants.INTENT_EXTRA_RESULT)) {
                        PHResponse<StatusResponse> response =
                                (PHResponse<StatusResponse>) data.getSerializableExtra(PHConstants.INTENT_EXTRA_RESULT);

                        if (response.isSuccess()) {
                            Toast.makeText(this, "Payment Successful!", Toast.LENGTH_LONG).show();
                            processSuccessfulOrder(currentOrderId);
                        } else {
                            Toast.makeText(this, "Payment Failed: " + response.toString(), Toast.LENGTH_LONG).show();
                        }
                    }
                } else if (result.getResultCode() == RESULT_CANCELED) {
                    Toast.makeText(this, "Payment Canceled by User", Toast.LENGTH_SHORT).show();
                }
            });

    // ==========================================
    // --- PHASE 3: PROCESS SUCCESSFUL ORDER ---
    // ==========================================
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

            Map<String, Object> orderData = new HashMap<>();
            orderData.put("orderId", orderId);
            orderData.put("userId", userId);
            orderData.put("status", "Pending");
            orderData.put("timestamp", FieldValue.serverTimestamp());

            orderData.put("subtotal", subtotal);
            orderData.put("deliveryFee", deliveryFee);
            orderData.put("grandTotal", subtotal + deliveryFee);
            orderData.put("paymentMethod", "PayHere");

            // 👉 SAVES THE UPLOADED URL! (Will be null if no prescription was needed)
            orderData.put("prescriptionUrl", checkoutPrescriptionUrl);

            Map<String, String> addressMap = new HashMap<>();
            addressMap.put("phone", etPhone.getText().toString());
            addressMap.put("address1", etAddress1.getText().toString());
            addressMap.put("address2", etAddress2.getText().toString());
            addressMap.put("city", etCity.getText().toString());
            addressMap.put("postal", etPostal.getText().toString());
            orderData.put("shippingAddress", addressMap);

            java.util.List<Map<String, Object>> itemsList = new ArrayList<>();
            for (CartItem item : finalItems) {
                Map<String, Object> itemMap = new HashMap<>();
                itemMap.put("medicineId", item.getMedicineId());
                itemMap.put("name", item.getName());
                itemMap.put("price", item.getPrice());
                itemMap.put("quantity", item.getQuantity());
                itemMap.put("imageUrl", item.getImageUrl());
                itemsList.add(itemMap);
            }
            orderData.put("items", itemsList);

            FirebaseFirestore.getInstance().collection("orders").document(orderId)
                    .set(orderData)
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

    // ==========================================
    // --- PHASE 4: UPDATE INVENTORY & CLEANUP ---
    // ==========================================
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