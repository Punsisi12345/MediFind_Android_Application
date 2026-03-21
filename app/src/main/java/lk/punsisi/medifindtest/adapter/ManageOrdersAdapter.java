package lk.punsisi.medifindtest.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import lk.punsisi.medifindtest.R;
import lk.punsisi.medifindtest.model.Order;

public class ManageOrdersAdapter extends RecyclerView.Adapter<ManageOrdersAdapter.OrderViewHolder> {

    private Context context;
    private List<Order> orderList = new ArrayList<>();
    private OnOrderClickListener listener;

    public interface OnOrderClickListener {
        void onOrderClick(Order order);
    }

    public ManageOrdersAdapter(Context context, OnOrderClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setOrderList(List<Order> orderList) {
        this.orderList = orderList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public OrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_manage_order, parent, false);
        return new OrderViewHolder(view);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull OrderViewHolder holder, int position) {
        Order order = orderList.get(position);

        holder.tvOrderId.setText(order.getOrderId());

        // Format Date
        if (order.getTimestamp() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy • h:mm a", Locale.getDefault());
            holder.tvOrderDate.setText(sdf.format(order.getTimestamp()));
        } else {
            holder.tvOrderDate.setText("Just now");
        }

        // Show the delivery method so the pharmacist knows what to expect!
        String method = order.getDeliveryMethod() != null ? order.getDeliveryMethod() : "Standard";
        holder.tvDeliveryMethod.setText(method);
        holder.tvOrderTotalPrice.setText("LKR : " + order.getGrandTotal());

        if (order.isPaid()) {
            holder.cardOrderPaid.setVisibility(View.VISIBLE);
        } else {
            holder.cardOrderPaid.setVisibility(View.GONE);
        }

        // Dynamic Status Badge Colors
        String status = order.getStatus();
        holder.tvOrderStatus.setText(status);

        if ("Pending".equalsIgnoreCase(status)) {
            // Orange for Pending
            holder.tvOrderStatus.setTextColor(Color.parseColor("#F57F17"));
            holder.cardOrderStatus.setCardBackgroundColor(Color.parseColor("#FFF8E1"));
        } else if ("Processing".equalsIgnoreCase(status)) {
            // Blue for Processing
            holder.tvOrderStatus.setTextColor(Color.parseColor("#1976D2"));
            holder.cardOrderStatus.setCardBackgroundColor(Color.parseColor("#E3F2FD"));
        } else {
            // Green for Ready/Completed
            holder.tvOrderStatus.setTextColor(Color.parseColor("#388E3C"));
            holder.cardOrderStatus.setCardBackgroundColor(Color.parseColor("#E8F5E9"));
        }

        // Handle Click
        holder.itemView.setOnClickListener(v -> listener.onOrderClick(order));
    }

    @Override
    public int getItemCount() {
        return orderList.size();
    }

    public static class OrderViewHolder extends RecyclerView.ViewHolder {
        TextView tvOrderId, tvOrderStatus, tvOrderDate, tvDeliveryMethod, tvOrderTotalPrice;
        MaterialCardView cardOrderStatus, cardOrderPaid;

        public OrderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvOrderId = itemView.findViewById(R.id.tv_order_id);
            tvOrderStatus = itemView.findViewById(R.id.tv_order_status);
            tvOrderDate = itemView.findViewById(R.id.tv_order_date);
            cardOrderStatus = itemView.findViewById(R.id.card_order_status);
            cardOrderPaid = itemView.findViewById(R.id.card_order_paid);

            tvDeliveryMethod = itemView.findViewById(R.id.tv_order_delivery_method);
            tvOrderTotalPrice = itemView.findViewById(R.id.tv_order_total_price);
        }
    }
}