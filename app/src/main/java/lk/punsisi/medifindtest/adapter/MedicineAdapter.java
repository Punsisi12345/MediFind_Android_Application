package lk.punsisi.medifindtest.adapter;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.button.MaterialButton;

import java.util.List;

import lk.punsisi.medifindtest.R;
import lk.punsisi.medifindtest.activity.SingleMedicineActivity;
import lk.punsisi.medifindtest.helper.CartHelper;
import lk.punsisi.medifindtest.model.Medicine;

public class MedicineAdapter extends RecyclerView.Adapter<MedicineAdapter.ViewHolder> {

    private Context context;
    private List<Medicine> medicineList;
    private boolean isLargeLayout;

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
            view = LayoutInflater.from(context).inflate(R.layout.item_medicine_large, parent, false);
        } else {
            view = LayoutInflater.from(context).inflate(R.layout.medicine_item, parent, false);
        }
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Medicine medicine = medicineList.get(position);

        if (holder.medicineName != null) {
            holder.medicineName.setText(medicine.getName());
        }

        if (holder.medicinePrice != null) {
            holder.medicinePrice.setText("Rs. " + String.format("%.2f", medicine.getPrice()));
        }

        if (holder.medicineDosage != null && medicine.getDosage() != null) {
            holder.medicineDosage.setText(medicine.getDosage());
        }

        if (holder.medicineStatusBadge != null) {
            String status = medicine.getStatus();

            if (status == null) status = "Unknown";

            holder.medicineStatusBadge.setText(status);

            if ("In Stock".equalsIgnoreCase(status)) {
                holder.medicineStatusBadge.setBackgroundTintList(
                        ColorStateList.valueOf(Color.parseColor("#4CAF50")));
            } else if ("Out of Stock".equalsIgnoreCase(status)) {
                holder.medicineStatusBadge.setBackgroundTintList(
                        ColorStateList.valueOf(Color.parseColor("#F44336")));
            } else if ("Low Stock".equalsIgnoreCase(status)) {
                holder.medicineStatusBadge.setBackgroundTintList(
                        ColorStateList.valueOf(Color.parseColor("#FF9800")));
            } else {
                holder.medicineStatusBadge.setBackgroundTintList(
                        ColorStateList.valueOf(Color.parseColor("#9E9E9E")));
            }
        }

        if (holder.btnAddToCart != null) {
            holder.btnAddToCart.setOnClickListener(v -> {
                //check quantity
                if (medicine.getQuantity() <= 0) {
                    Toast.makeText(context, "Out of stock!", Toast.LENGTH_SHORT).show();
                    return;
                } else {
                    CartHelper.addMedicineToCart(context, medicine, 1);
                }
            });
        }

        if (holder.medicineImage != null) {
            Glide.with(context)
                    .load(medicine.getImageUrl())
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.baseline_medication_24)
                    .error(R.drawable.baseline_medication_24)
                    .into(holder.medicineImage);
        }

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, SingleMedicineActivity.class);
            intent.putExtra("MEDICINE_ID", medicine.getId());
            intent.putExtra("CATEGORY_ID", medicine.getCategoryId());
            intent.putExtra("CATEGORY_NAME", medicine.getCategoryName());
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return medicineList != null ? medicineList.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        ImageView medicineImage;
        TextView medicineName, medicineDosage, medicinePrice, medicineStatusBadge;
        MaterialButton btnAddToCart;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            medicineImage = itemView.findViewById(R.id.medicine_image);
            medicineName = itemView.findViewById(R.id.tv_medicine_name);
            medicineDosage = itemView.findViewById(R.id.tv_medicine_dosage);
            medicinePrice = itemView.findViewById(R.id.tv_medicine_price);
            medicineStatusBadge = itemView.findViewById(R.id.medicine_status_badge);
            btnAddToCart = itemView.findViewById(R.id.btn_add_to_cart);
        }
    }
}