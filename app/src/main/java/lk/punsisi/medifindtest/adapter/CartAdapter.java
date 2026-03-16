package lk.punsisi.medifindtest.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import lk.punsisi.medifindtest.R;
import lk.punsisi.medifindtest.model.CartItem;

public class CartAdapter extends RecyclerView.Adapter<CartAdapter.CartViewHolder> {

    private Context context;
    private List<CartItem> cartList;
    private CartActionListener listener;

    // Interface to talk to the Fragment!
    public interface CartActionListener {
        void onQuantityChanged(CartItem item, int newQuantity, int position);
        void onItemDeleted(CartItem item, int position);
    }

    public CartAdapter(Context context, List<CartItem> cartList, CartActionListener listener) {
        this.context = context;
        this.cartList = cartList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public CartViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_cart, parent, false);
        return new CartViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CartViewHolder holder, int position) {
        CartItem item = cartList.get(position);

        // 1. Set Name and Quantity
        holder.tvName.setText(item.getName());
        holder.tvQuantity.setText(String.valueOf(item.getQuantity()));

        // 2. THE FIX: Calculate the total price for THIS specific item card!
        double totalItemPrice = item.getPrice() * item.getQuantity();
        holder.tvPrice.setText(String.format("Rs. %.2f", totalItemPrice));

        // 3. THE FIX: Load the real image using Glide!
        // We use a placeholder so it doesn't look blank while the internet is downloading the image.
        com.bumptech.glide.Glide.with(context)
                .load(item.getImageUrl())
                .placeholder(R.drawable.baseline_medication_24)
                .error(R.drawable.baseline_medication_24)
                .into(holder.ivImage);

        // --- CLICK LISTENERS ---

        // PLUS BUTTON
        holder.btnPlus.setOnClickListener(v -> {
            int currentQty = item.getQuantity();
            if (currentQty < item.getMaxStock()) {
                listener.onQuantityChanged(item, currentQty + 1, position);
            } else {
                Toast.makeText(context, "Maximum stock reached", Toast.LENGTH_SHORT).show();
            }
        });

        // MINUS BUTTON
        holder.btnMinus.setOnClickListener(v -> {
            int currentQty = item.getQuantity();
            if (currentQty > 1) {
                listener.onQuantityChanged(item, currentQty - 1, position);
            } else {
                Toast.makeText(context, "Minimum quantity is 1. Use the trash icon to remove.", Toast.LENGTH_SHORT).show();
            }
        });

        // DELETE BUTTON
        holder.btnDelete.setOnClickListener(v -> {
            listener.onItemDeleted(item, position);
        });
    }

    @Override
    public int getItemCount() {
        return cartList != null ? cartList.size() : 0;
    }

    public static class CartViewHolder extends RecyclerView.ViewHolder {
        ImageView ivImage;
        TextView tvName, tvPrice, tvQuantity;
        ImageButton btnPlus, btnMinus, btnDelete;

        public CartViewHolder(@NonNull View itemView) {
            super(itemView);
            ivImage = itemView.findViewById(R.id.iv_cart_image);
            tvName = itemView.findViewById(R.id.tv_cart_name);
            tvPrice = itemView.findViewById(R.id.tv_cart_price);
            tvQuantity = itemView.findViewById(R.id.tv_cart_quantity);
            btnPlus = itemView.findViewById(R.id.btn_cart_plus);
            btnMinus = itemView.findViewById(R.id.btn_cart_minus);
            btnDelete = itemView.findViewById(R.id.btn_cart_delete);
        }
    }
}