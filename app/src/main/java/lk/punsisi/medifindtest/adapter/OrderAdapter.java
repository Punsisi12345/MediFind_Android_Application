package lk.punsisi.medifindtest.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

import lk.punsisi.medifindtest.R;
import lk.punsisi.medifindtest.model.Order;

public class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.OrderViewHolder> {

    public interface OnOrderClickListener {
        void onOrderClick(String orderId);
    }

    private final Context context;
    private final List<Order> orderList;
    private final OnOrderClickListener listener;

    public OrderAdapter(Context context, List<Order> orderList, OnOrderClickListener listener) {
        this.context = context;
        this.orderList = orderList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public OrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_order, parent, false);
        return new OrderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OrderViewHolder holder, int position) {
        Order order = orderList.get(position);

        // 1. Set Order ID
        holder.tvOrderId.setText("#" + order.getOrderId());

        // 2. NEW: Check if this is a Prescription Order or a Normal Order
        if (order.getPrescriptionUrl() != null && !order.getPrescriptionUrl().isEmpty()) {
            // It's a prescription! Show custom text and color.
            holder.tvOrderTotal.setText("Awaiting Quote");
            holder.tvOrderTotal.setTextColor(Color.parseColor("#9C27B0")); // Purple text
        } else {
            // Normal order! Show the price.
            holder.tvOrderTotal.setText(String.format(Locale.getDefault(), "Rs. %.2f", order.getGrandTotal()));
            // Reset to your primary theme color (so recycled views don't stay purple)
            holder.tvOrderTotal.setTextColor(ContextCompat.getColor(context, R.color.md_theme_primary));
        }

        // 3. Format the Firebase Date beautifully
        if (order.getTimestamp() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
            holder.tvOrderDate.setText(sdf.format(order.getTimestamp()));
        } else {
            // Sometimes it takes a millisecond for Firebase to stamp the time locally
            holder.tvOrderDate.setText("Just now");
        }

        // 4. Dynamic Status Badge Colors
        String status = order.getStatus() != null ? order.getStatus() : "Pending";
        holder.tvOrderStatus.setText(status);

        int textColor;
        int bgColor;

        switch (status.toLowerCase()) {
            case "reviewing prescription": // <-- NEW PRESCRIPTION CASE!
                textColor = Color.parseColor("#E64A19"); // Deep Orange
                bgColor = Color.parseColor("#FBE9E7"); // Light Deep Orange Background
                break;
            case "processing":
                textColor = Color.parseColor("#1976D2"); // Blue
                bgColor = Color.parseColor("#E3F2FD");
                break;
            case "shipped":
                textColor = Color.parseColor("#7B1FA2"); // Purple
                bgColor = Color.parseColor("#F3E5F5");
                break;
            case "delivered":
                textColor = Color.parseColor("#388E3C"); // Green
                bgColor = Color.parseColor("#E8F5E9");
                break;
            case "cancelled":
                textColor = Color.parseColor("#D32F2F"); // Red
                bgColor = Color.parseColor("#FFEBEE");
                break;
            case "pending":
            default:
                textColor = Color.parseColor("#F57C00"); // Orange
                bgColor = Color.parseColor("#FFF3E0");
                break;
        }

        holder.tvOrderStatus.setTextColor(textColor);
        holder.tvOrderStatus.setBackgroundTintList(ColorStateList.valueOf(bgColor));

        // 5. Click Listener
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onOrderClick(order.getOrderId());
            }
        });
    }

    @Override
    public int getItemCount() {
        return orderList.size();
    }

    public static class OrderViewHolder extends RecyclerView.ViewHolder {
        TextView tvOrderId, tvOrderDate, tvOrderTotal, tvOrderStatus;

        public OrderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvOrderId = itemView.findViewById(R.id.tv_order_id);
            tvOrderDate = itemView.findViewById(R.id.tv_order_date);
            tvOrderTotal = itemView.findViewById(R.id.tv_order_total);
            tvOrderStatus = itemView.findViewById(R.id.tv_order_status);
        }
    }
}