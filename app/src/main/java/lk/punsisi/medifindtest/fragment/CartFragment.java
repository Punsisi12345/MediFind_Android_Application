package lk.punsisi.medifindtest.fragment;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
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
import lk.punsisi.medifindtest.databinding.FragmentCartBinding;
import lk.punsisi.medifindtest.helper.CartHelper;
import lk.punsisi.medifindtest.model.CartItem;
import lk.punsisi.medifindtest.room.AppDatabase;

public class CartFragment extends Fragment implements CartAdapter.CartActionListener {

    private FragmentCartBinding binding;

    private RecyclerView rvCartItems;
    private CartAdapter cartAdapter;
    private List<CartItem> cartList = new ArrayList<>();

    private LinearLayout layoutEmptyState, layoutCheckoutBar;
    private TextView tvTotalPrice;
    private MaterialButton btnCheckout;

    private FirebaseAuth auth;

    private AppDatabase db;
    private ExecutorService executorService;

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm != null ? cm.getActiveNetworkInfo() : null;
        return netInfo != null && netInfo.isConnected();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        binding = FragmentCartBinding.inflate(inflater, container,false);

        auth = FirebaseAuth.getInstance();

        rvCartItems = binding.rvCartItems;
        layoutEmptyState = binding.layoutCartEmptyState;
        layoutCheckoutBar = binding.layoutCartCheckoutBar;
        tvTotalPrice = binding.tvCartTotalPrice;
        btnCheckout = binding.btnCheckout;

        rvCartItems.setLayoutManager(new LinearLayoutManager(requireContext()));
        cartAdapter = new CartAdapter(requireContext(), cartList, this);
        rvCartItems.setAdapter(cartAdapter);

        db = AppDatabase.getDatabase(requireContext());
        executorService = Executors.newSingleThreadExecutor();

        loadCartItems();

        //checkout button click
        btnCheckout.setOnClickListener(v -> {

            //check internet connection
            if (!isNetworkAvailable()) {
                Toast.makeText(requireContext(), "Please connect to the internet to proceed to checkout.", Toast.LENGTH_LONG).show();
                return;
            }

            FirebaseUser currentUser = auth.getCurrentUser();

            if (currentUser == null) {
                Toast.makeText(requireContext(), "Please log in or register to place an order.", Toast.LENGTH_LONG).show();
                return;
            }

            double currentSubtotal = 0;
            for (CartItem item : cartList) {
                currentSubtotal += (item.getPrice() * item.getQuantity());
            }

            Intent intent = new Intent(requireContext(), CheckoutActivity.class);
            intent.putExtra("CART_TOTAL", currentSubtotal);
            startActivity(intent);
        });

        return  binding.getRoot();
    }

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
            layoutCheckoutBar.setVisibility(View.GONE);
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
            else CartHelper.scheduleSync(requireContext());
        });
    }

    @Override
    public void onItemDeleted(CartItem item, int position) {
        cartList.remove(position);
        cartAdapter.notifyItemRemoved(position);
        new Handler(Looper.getMainLooper()).postDelayed(this::updateUIAndCalculateTotal, 300);

        boolean isOnline = isNetworkAvailable();

        executorService.execute(() -> {

            item.setDeleted(true);
            item.setSynced(isOnline);
            db.cartDao().update(item);

            if (isOnline) {
                deleteFromFirebase(item.getMedicineId());
                db.cartDao().delete(item);
            } else {
                CartHelper.scheduleSync(requireContext());
            }
        });
    }

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

        loadCartItems();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding =null;
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}