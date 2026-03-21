package lk.punsisi.medifindtest.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

import lk.punsisi.medifindtest.R;
import lk.punsisi.medifindtest.model.Order;

public class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.OrderViewHolder> {

    public interface OnOrderClickListener {
        void onOrderClick(String orderId);
        void onPayClick(Order order);
        void onReviewClick(Order order);
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

        holder.tvOrderId.setText("#" + order.getOrderId());

        if (order.getPrescriptionUrl() != null && !order.getPrescriptionUrl().isEmpty() && order.getGrandTotal() == 0) {
            holder.tvOrderTotal.setText("Awaiting Quote");
            holder.tvOrderTotal.setTextColor(Color.parseColor("#9C27B0")); // Purple text
        } else {
            holder.tvOrderTotal.setText(String.format(Locale.getDefault(), "Rs. %.2f", order.getGrandTotal()));
            holder.tvOrderTotal.setTextColor(ContextCompat.getColor(context, R.color.md_theme_primary));
        }

        if (order.getTimestamp() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
            holder.tvOrderDate.setText(sdf.format(order.getTimestamp()));
        } else {
            holder.tvOrderDate.setText("Just now");
        }

        String status = order.getStatus() != null ? order.getStatus() : "Pending";
        holder.tvOrderStatus.setText(status);

        int textColor;
        int bgColor;

        switch (status.toLowerCase()) {
            case "reviewing prescription":
                textColor = Color.parseColor("#E64A19");
                bgColor = Color.parseColor("#FBE9E7");
                break;
            case "processing":
                textColor = Color.parseColor("#1976D2");
                bgColor = Color.parseColor("#E3F2FD");
                break;
            case "on the way":
                textColor = Color.parseColor("#00838F"); // Teal
                bgColor = Color.parseColor("#E0F7FA");
                break;
            case "ready to pick":
            case "ready for delivery":
            case "completed":
            case "delivered":
                textColor = Color.parseColor("#388E3C"); // Green
                bgColor = Color.parseColor("#E8F5E9");
                break;
            case "cancelled":
                textColor = Color.parseColor("#D32F2F");
                bgColor = Color.parseColor("#FFEBEE");
                break;
            case "pending":
            default:
                textColor = Color.parseColor("#F57C00");
                bgColor = Color.parseColor("#FFF3E0");
                break;
        }

        holder.tvOrderStatus.setTextColor(textColor);
        holder.tvOrderStatus.setBackgroundTintList(ColorStateList.valueOf(bgColor));

        // ==========================================
        // 👉 NEW: PAID BADGE & PAY BUTTON LOGIC
        // ==========================================
        String deliveryMethod = order.getDeliveryMethod() != null ? order.getDeliveryMethod() : "";

        // Reset visibility first
        holder.btnPayNow.setVisibility(View.GONE);
        holder.btnAddReview.setVisibility(View.GONE);
        holder.tvOrderPaidBadge.setVisibility(View.GONE);
        holder.ivArrow.setVisibility(View.VISIBLE);

        if (status.equalsIgnoreCase("Completed") || status.equalsIgnoreCase("Delivered")) {
            if (order.isReviewed()) {
                // Already reviewed! Hide the button and bring back the arrow
                holder.btnAddReview.setVisibility(View.GONE);
                holder.ivArrow.setVisibility(View.VISIBLE);
            } else {
                // Not reviewed yet! Show the shiny Add Review button
                holder.btnAddReview.setVisibility(View.VISIBLE);
                holder.ivArrow.setVisibility(View.GONE);
            }

            // Still show the paid badge if it was paid
            if (order.isPaid()) {
                holder.tvOrderPaidBadge.setText("Paid");
                holder.tvOrderPaidBadge.setVisibility(View.VISIBLE);
            }
        } else if (order.isPaid()) {
            // SCENARIO 2: Order is Paid but not yet completed. Show badge.
            if (deliveryMethod.equals("COD")) {
                holder.tvOrderPaidBadge.setText("COD Paid");
            } else if (deliveryMethod.equals("Pickup")) {
                holder.tvOrderPaidBadge.setText("Pickup Paid");
            } else {
                holder.tvOrderPaidBadge.setText("Online Paid");
            }
            holder.tvOrderPaidBadge.setVisibility(View.VISIBLE);
        } else {
            // SCENARIO 3: Not paid, Not completed. Show Pay button if ready.
            if (deliveryMethod.equalsIgnoreCase("Online") &&
                    status.equalsIgnoreCase("Ready for Delivery") &&
                    order.getGrandTotal() > 0) {
                holder.btnPayNow.setVisibility(View.VISIBLE);
                holder.ivArrow.setVisibility(View.GONE);
            }
        }

        // Click Listeners
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onOrderClick(order.getOrderId());
        });

        holder.btnPayNow.setOnClickListener(v -> {
            if (listener != null) listener.onPayClick(order);
        });

        holder.btnAddReview.setOnClickListener(v -> {
            if (listener != null) listener.onReviewClick(order);
        });
    }

    @Override
    public int getItemCount() {
        return orderList.size();
    }

    public static class OrderViewHolder extends RecyclerView.ViewHolder {
        TextView tvOrderId, tvOrderDate, tvOrderTotal, tvOrderStatus;

        // 👉 NEW: Added the Paid Badge TextView here
        TextView tvOrderPaidBadge;

        MaterialButton btnPayNow, btnAddReview;
        ImageView ivArrow;

        public OrderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvOrderId = itemView.findViewById(R.id.tv_order_id);
            tvOrderDate = itemView.findViewById(R.id.tv_order_date);
            tvOrderTotal = itemView.findViewById(R.id.tv_order_total);
            tvOrderStatus = itemView.findViewById(R.id.tv_order_status);

            // 👉 NEW: Initialize the badge
            tvOrderPaidBadge = itemView.findViewById(R.id.tv_order_paid_badge);

            btnPayNow = itemView.findViewById(R.id.btn_pay_now);
            btnAddReview = itemView.findViewById(R.id.btn_add_review);
            ivArrow = itemView.findViewById(R.id.iv_arrow);
        }
    }
}