package lk.punsisi.medifindtest.adapter;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Insert;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;

import java.util.List;

import lk.punsisi.medifindtest.R;
import lk.punsisi.medifindtest.activity.MedicinesListActivity;
import lk.punsisi.medifindtest.activity.SingleMedicineActivity;
import lk.punsisi.medifindtest.helper.CartHelper;
import lk.punsisi.medifindtest.model.Medicine;

public class MedicineAdapter extends RecyclerView.Adapter<MedicineAdapter.ViewHolder> {

    private Context context;
    private List<Medicine> medicineList;
    private boolean isLargeLayout;

    // Constructor to pass data
    public MedicineAdapter(Context context, List<Medicine> medicineList, boolean isLargeLayout) {
        this.context = context;
        this.medicineList = medicineList;
        this.isLargeLayout = isLargeLayout;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        if (isLargeLayout) {
            // Inflate your beautiful new premium layout!
            view = LayoutInflater.from(context).inflate(R.layout.item_medicine_large, parent, false);
        } else {
            // Inflate your small layout for the Home Screen
            view = LayoutInflater.from(context).inflate(R.layout.medicine_item, parent, false);
        }
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Medicine medicine = medicineList.get(position);

        // ==========================================
        // 1. BASIC INFO (Safe null checks for everything!)
        // ==========================================
        if (holder.medicineName != null) {
            holder.medicineName.setText(medicine.getName());
        }

        if (holder.medicinePrice != null) {
            // Format the price nicely (e.g., "Rs. 150.50")
            holder.medicinePrice.setText("Rs. " + String.format("%.2f", medicine.getPrice()));
        }

        if (holder.medicineDosage != null && medicine.getDosage() != null) {
            holder.medicineDosage.setText(medicine.getDosage());
        }

        // ==========================================
        // 2. DYNAMIC BADGE LOGIC (Only runs if badge exists in XML)
        // ==========================================
        if (holder.medicineStatusBadge != null) {
            String status = medicine.getStatus();

            // Fallback just in case status is completely missing from database
            if (status == null) status = "Unknown";

            holder.medicineStatusBadge.setText(status);

            if ("In Stock".equalsIgnoreCase(status)) {
                holder.medicineStatusBadge.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#4CAF50")));
            } else if ("Out of Stock".equalsIgnoreCase(status)) {
                holder.medicineStatusBadge.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#F44336")));
            } else if ("Low Stock".equalsIgnoreCase(status)) {
                holder.medicineStatusBadge.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FF9800")));
            } else {
                holder.medicineStatusBadge.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#9E9E9E")));
            }
        }

        // ==========================================
        // 3. ADD TO CART BUTTON LOGIC
        // ==========================================
        if (holder.btnAddToCart != null) {
            holder.btnAddToCart.setOnClickListener(v -> {

                //check quantity
                if (medicine.getQuantity() <= 0) {
                    Toast.makeText(context, "Out of stock!", Toast.LENGTH_SHORT).show();
                    return;
                }else{
                    CartHelper.addMedicineToCart(context, medicine, 1);
                    // We will add the actual Cart Database logic here later!
                }



            });
        }

        // ==========================================
        // 4. LOAD IMAGE
        // ==========================================
        if (holder.medicineImage != null) {
            Glide.with(context)
                    .load(medicine.getImageUrl())
                    .placeholder(R.drawable.baseline_search_24)
                    .into(holder.medicineImage);
        }

        // ==========================================
        // 5. ADD CLICK LISTENER
        // ==========================================
        holder.itemView.setOnClickListener(v -> {
            // Handle item click here
            Toast.makeText(context, "Clicked on " + medicine.getName(), Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(context, SingleMedicineActivity.class);

            intent.putExtra("MEDICINE_ID", medicine.getId());
            intent.putExtra("CATEGORY_ID", medicine.getCategoryId());
            intent.putExtra("CATEGORY_NAME", medicine.getCategoryName()); // Pass this if you have it!

            context.startActivity(intent);
        });

    }

    @Override
    public int getItemCount() {
        return medicineList != null ? medicineList.size() : 0;
    }

    // ==========================================
    // THE VIEWHOLDER
    // ==========================================
    public static class ViewHolder extends RecyclerView.ViewHolder {

        ImageView medicineImage;
        TextView medicineName, medicineDosage, medicinePrice, medicineStatusBadge;
        MaterialButton btnAddToCart; // Added the cart button!

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            // Matched these IDs exactly to the item_medicine_large.xml you sent!
            medicineImage = itemView.findViewById(R.id.medicine_image);
            medicineName = itemView.findViewById(R.id.tv_medicine_name);
            medicineDosage = itemView.findViewById(R.id.tv_medicine_dosage);
            medicinePrice = itemView.findViewById(R.id.tv_medicine_price);
            medicineStatusBadge = itemView.findViewById(R.id.medicine_status_badge);
            btnAddToCart = itemView.findViewById(R.id.btn_add_to_cart);
        }
    }
}