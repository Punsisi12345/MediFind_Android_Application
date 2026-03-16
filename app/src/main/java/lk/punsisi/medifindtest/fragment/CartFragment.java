package lk.punsisi.medifindtest.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lk.punsisi.medifindtest.R;
import lk.punsisi.medifindtest.activity.CheckoutActivity;
import lk.punsisi.medifindtest.adapter.CartAdapter;
import lk.punsisi.medifindtest.model.CartItem;
import lk.punsisi.medifindtest.room.AppDatabase;

public class CartFragment extends Fragment implements CartAdapter.CartActionListener {

    private RecyclerView rvCartItems;
    private CartAdapter cartAdapter;
    private List<CartItem> cartList = new ArrayList<>();

    private LinearLayout layoutEmptyState, layoutCheckoutBar;
    private TextView tvTotalPrice;
    private MaterialButton btnCheckout;

    private AppDatabase db;
    private ExecutorService executorService;

    private boolean isNetworkAvailable() {
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager) requireContext().getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
        android.net.NetworkInfo netInfo = cm != null ? cm.getActiveNetworkInfo() : null;
        return netInfo != null && netInfo.isConnected();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_cart, container, false); // Make sure your layout is named fragment_cart.xml!

        // 1. Initialize Views
        rvCartItems = view.findViewById(R.id.rv_cart_items);
        layoutEmptyState = view.findViewById(R.id.layout_cart_empty_state);
        layoutCheckoutBar = view.findViewById(R.id.layout_cart_checkout_bar);
        tvTotalPrice = view.findViewById(R.id.tv_cart_total_price);
        btnCheckout = view.findViewById(R.id.btn_checkout);

        // 2. Setup RecyclerView
        rvCartItems.setLayoutManager(new LinearLayoutManager(requireContext()));
        cartAdapter = new CartAdapter(requireContext(), cartList, this);
        rvCartItems.setAdapter(cartAdapter);

        // 3. Initialize Database Tools
        db = AppDatabase.getDatabase(requireContext());
        executorService = Executors.newSingleThreadExecutor();

        // 4. Load Data
        loadCartItems();

        // 5. Checkout Click
        btnCheckout.setOnClickListener(v -> {

            // 1. Check for internet first!
            if (!isNetworkAvailable()) {
                Toast.makeText(requireContext(), "Please connect to the internet to proceed to checkout.", Toast.LENGTH_LONG).show();
                return; // Stop right here, don't open the next screen!
            }

            // 2. If online, calculate the subtotal and proceed exactly as before
            double currentSubtotal = 0;
            for (CartItem item : cartList) {
                currentSubtotal += (item.getPrice() * item.getQuantity());
            }

            Intent intent = new Intent(requireContext(), CheckoutActivity.class);
            intent.putExtra("CART_TOTAL", currentSubtotal);
            startActivity(intent);
        });

        return view;
    }

    // ==========================================
    // --- LOAD & CALCULATE LOGIC ---
    // ==========================================
    private void loadCartItems() {
        executorService.execute(() -> {
            List<CartItem> itemsFromDb = db.cartDao().getActiveCartItems();

            requireActivity().runOnUiThread(() -> {
                cartList.clear();
                cartList.addAll(itemsFromDb);
                cartAdapter.notifyDataSetChanged();
                updateUIAndCalculateTotal();
            });
        });
    }

    private void updateUIAndCalculateTotal() {
        if (cartList.isEmpty()) {
            layoutEmptyState.setVisibility(View.VISIBLE);
            rvCartItems.setVisibility(View.GONE);
            layoutCheckoutBar.setVisibility(View.GONE); // Hide checkout bar if cart is empty!
        } else {
            layoutEmptyState.setVisibility(View.GONE);
            rvCartItems.setVisibility(View.VISIBLE);
            layoutCheckoutBar.setVisibility(View.VISIBLE);

            double total = 0;
            for (CartItem item : cartList) {
                total += (item.getPrice() * item.getQuantity());
            }
            tvTotalPrice.setText(String.format("Rs. %.2f", total));
        }
    }

    // ==========================================
    // --- INTERFACE CALLBACKS (FROM ADAPTER) ---
    // ==========================================
    @Override
    public void onQuantityChanged(CartItem item, int newQuantity, int position) {
        item.setQuantity(newQuantity);
        cartAdapter.notifyItemChanged(position);
        updateUIAndCalculateTotal();

        boolean isOnline = isNetworkAvailable();

        executorService.execute(() -> {
            item.setSynced(isOnline);
            db.cartDao().update(item);

            if (isOnline) updateFirebaseCartItem(item);
            else lk.punsisi.medifindtest.helper.CartHelper.scheduleSync(requireContext()); // Schedule background sync!
        });
    }

    @Override
    public void onItemDeleted(CartItem item, int position) {
        cartList.remove(position);
        cartAdapter.notifyItemRemoved(position);
        new Handler(Looper.getMainLooper()).postDelayed(this::updateUIAndCalculateTotal, 300);

        boolean isOnline = isNetworkAvailable();

        executorService.execute(() -> {
            // SOFT DELETE: Mark as deleted and unsynced!
            item.setDeleted(true);
            item.setSynced(isOnline);
            db.cartDao().update(item); // Update, don't delete yet!

            if (isOnline) {
                deleteFromFirebase(item.getMedicineId());
                db.cartDao().delete(item); // If online, safe to hard delete locally now
            } else {
                lk.punsisi.medifindtest.helper.CartHelper.scheduleSync(requireContext()); // Schedule background sync!
            }
        });
    }

    // ==========================================
    // --- FIREBASE ONLINE SYNC ---
    // ==========================================
    private void updateFirebaseCartItem(CartItem item) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        FirebaseFirestore.getInstance()
                .collection("users").document(user.getUid())
                .collection("cart").document(item.getMedicineId())
                .set(item)
                .addOnFailureListener(e -> Log.e("Cart", "Firebase update failed", e));
    }

    private void deleteFromFirebase(String medicineId) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        FirebaseFirestore.getInstance()
                .collection("users").document(user.getUid())
                .collection("cart").document(medicineId)
                .delete()
                .addOnFailureListener(e -> Log.e("Cart", "Firebase delete failed", e));
    }

    @Override
    public void onResume() {
        super.onResume();
        // This runs EVERY time the user comes back to the Cart screen!
        // Because Checkout emptied the database, this will now load an empty list
        // and trigger your cute empty state animation automatically!
        loadCartItems();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}